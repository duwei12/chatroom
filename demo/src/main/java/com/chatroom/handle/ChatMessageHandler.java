package com.chatroom.handle;

import com.alibaba.fastjson.JSON;
import com.chatroom.pojo.ChatMessage;
import com.chatroom.utils.JwtUtil;
import com.chatroom.utils.RedisPubSubUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.AttributeKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 聊天室消息处理器（核心）
 */
@Slf4j
@Component
@ChannelHandler.Sharable // 标记为可共享（单例）
@RequiredArgsConstructor
public class ChatMessageHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    // 存储用户ID与Channel的映射（单节点）
    private static final ConcurrentHashMap<String, Channel> USER_CHANNEL_MAP = new ConcurrentHashMap<>();
    // Channel属性：存储用户ID
    private static final AttributeKey<String> USER_ID_KEY = AttributeKey.valueOf("userId");

    private final JwtUtil jwtUtil;
    private final RedisPubSubUtil redisPubSubUtil;
    private final RedisTemplate<String, String> redisTemplate;

    /**
     * 处理客户端发送的文本消息
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
        String msgJson = frame.text();
        log.info("收到客户端消息：{}", msgJson);

        try {
            // 1. 解析消息
            ChatMessage message = JSON.parseObject(msgJson, ChatMessage.class);

            // 2. 处理不同消息类型
            switch (message.getType()) {
                case "CONNECT":
                    handleConnect(ctx, message);
                    break;
                case "MESSAGE":
                    handleMessage(ctx, message);
                    break;
                case "PING":
                    handlePing(ctx);
                    break;
                case "DISCONNECT":
                    handleDisconnect(ctx);
                    break;
                default:
                    log.warn("未知消息类型：{}", message.getType());
                    sendError(ctx, "未知消息类型");
            }
        } catch (Exception e) {
            log.error("消息处理失败", e);
            sendError(ctx, "消息格式错误");
        }
    }

    /**
     * 处理客户端连接（认证）
     */
    private void handleConnect(ChannelHandlerContext ctx, ChatMessage message) {
        // 1. 验证JWT令牌
        String token = message.getToken();
        if (token == null || !jwtUtil.validateToken(token)) {
            sendError(ctx, "认证失败，无效的token");
            ctx.close();
            return;
        }

        // 2. 解析用户ID
        String userId = jwtUtil.extractUserId(token);
        if (userId == null) {
            sendError(ctx, "认证失败，无用户信息");
            ctx.close();
            return;
        }

        // 3. 存储用户与Channel的映射
        Channel channel = ctx.channel();
        channel.attr(USER_ID_KEY).set(userId);
        USER_CHANNEL_MAP.put(userId, channel);

        // 4. 发送连接成功响应
        ChatMessage response = new ChatMessage();
        response.setType("CONNECT_SUCCESS");
        response.setContent("连接成功");
        channel.writeAndFlush(new TextWebSocketFrame(JSON.toJSONString(response)));

        log.info("用户{}连接成功", userId);
    }

    /**
     * 处理普通消息（广播/私聊）
     */
    private void handleMessage(ChannelHandlerContext ctx, ChatMessage message) {
        String senderId = ctx.channel().attr(USER_ID_KEY).get();
        if (senderId == null) {
            sendError(ctx, "未认证，无法发送消息");
            return;
        }
        message.setSenderId(senderId);
        message.setTimestamp(System.currentTimeMillis());

        // 1. 私聊：指定接收者
        if (message.getReceiverId() != null && !message.getReceiverId().isEmpty()) {
            Channel receiverChannel = USER_CHANNEL_MAP.get(message.getReceiverId());
            if (receiverChannel != null && receiverChannel.isActive()) {
                receiverChannel.writeAndFlush(new TextWebSocketFrame(JSON.toJSONString(message)));
            } else {
                // 离线消息：存入Redis（可选）
                redisTemplate.opsForList().leftPush("chat:offline:" + message.getReceiverId(), JSON.toJSONString(message));
                sendError(ctx, "接收者离线，消息已缓存");
            }
        }
        // 2. 广播：发布到Redis（集群同步）
        else {
            redisPubSubUtil.publish("chat:broadcast", JSON.toJSONString(message));
        }
    }

    /**
     * 处理心跳请求
     */
    private void handlePing(ChannelHandlerContext ctx) {
        ChatMessage pong = new ChatMessage();
        pong.setType("PONG");
        ctx.channel().writeAndFlush(new TextWebSocketFrame(JSON.toJSONString(pong)));
    }

    /**
     * 处理客户端断开连接
     */
    private void handleDisconnect(ChannelHandlerContext ctx) {
        String userId = ctx.channel().attr(USER_ID_KEY).get();
        if (userId != null) {
            USER_CHANNEL_MAP.remove(userId);
            log.info("用户{}主动断开连接", userId);
        }
        ctx.close();
    }

    /**
     * 处理空闲超时（心跳检测）
     */
    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state() == IdleState.READER_IDLE) {
                log.warn("用户{}读空闲超时，断开连接", ctx.channel().attr(USER_ID_KEY).get());
                ctx.close();
            }
        }
    }

    /**
     * 客户端连接断开（被动）
     */
    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        String userId = ctx.channel().attr(USER_ID_KEY).get();
        if (userId != null) {
            USER_CHANNEL_MAP.remove(userId);
            log.info("用户{}连接断开", userId);
        }
    }

    /**
     * 处理异常
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("通道异常", cause);
        ctx.close();
    }

    /**
     * 发送错误消息
     */
    private void sendError(ChannelHandlerContext ctx, String message) {
        ChatMessage error = new ChatMessage();
        error.setType("ERROR");
        error.setContent(message);
        ctx.channel().writeAndFlush(new TextWebSocketFrame(JSON.toJSONString(error)));
    }

    /**
     * 处理Redis广播消息（集群同步）
     */
    public void handleRedisBroadcast(String msgJson) {
        ChatMessage message = JSON.parseObject(msgJson, ChatMessage.class);
        // 广播给当前节点所有在线用户
        for (Channel channel : USER_CHANNEL_MAP.values()) {
            if (channel.isActive()) {
                channel.writeAndFlush(new TextWebSocketFrame(msgJson));
            }
        }
    }
}
