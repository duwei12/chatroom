package com.chatroom.handle;

import com.alibaba.fastjson.JSON;
import com.chatroom.pojo.ChatMessage;
import com.chatroom.service.RoomService;
import com.chatroom.utils.JwtUtil;
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

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@ChannelHandler.Sharable
@RequiredArgsConstructor
public class ChatMessageHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    private static final AttributeKey<String> USER_ID_KEY = AttributeKey.valueOf("userId");
    private static final AttributeKey<String> ROOM_ID_KEY = AttributeKey.valueOf("roomId");
    /** roomId -> Set<Channel> */
    private static final ConcurrentHashMap<String, Set<Channel>> ROOM_CHANNELS_MAP = new ConcurrentHashMap<>();

    private final JwtUtil jwtUtil;
    private final RedisTemplate<String, String> redisTemplate;
    private final RoomService roomService;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
        String msgJson = frame.text();
        log.debug("收到消息: {}", msgJson);

        try {
            ChatMessage message = JSON.parseObject(msgJson, ChatMessage.class);
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
                    sendError(ctx, "未知消息类型");
            }
        } catch (Exception e) {
            log.error("消息处理失败", e);
            sendError(ctx, "消息格式错误");
        }
    }

    private void handleConnect(ChannelHandlerContext ctx, ChatMessage message) {
        String token = message.getToken();
        if (token == null || !jwtUtil.validateToken(token)) {
            sendError(ctx, "认证失败");
            ctx.close();
            return;
        }

        String userId = jwtUtil.extractUserId(token);
        if (userId == null) {
            sendError(ctx, "认证失败");
            ctx.close();
            return;
        }

        String roomId = message.getRoomId();
        if (roomId == null || roomId.trim().isEmpty()) {
            sendError(ctx, "房间ID不能为空");
            ctx.close();
            return;
        }

        if (!roomService.exists(roomId)) {
            sendError(ctx, "房间不存在");
            ctx.close();
            return;
        }

        Channel channel = ctx.channel();
        channel.attr(USER_ID_KEY).set(userId);
        channel.attr(ROOM_ID_KEY).set(roomId);

        ROOM_CHANNELS_MAP.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet()).add(channel);

        ChatMessage response = new ChatMessage();
        response.setType("CONNECT_SUCCESS");
        response.setContent("连接成功");
        response.setRoomId(roomId);
        channel.writeAndFlush(new TextWebSocketFrame(JSON.toJSONString(response)));
        log.info("用户 {} 加入房间 {}", userId, roomId);
    }

    private void handleMessage(ChannelHandlerContext ctx, ChatMessage message) {
        String senderId = ctx.channel().attr(USER_ID_KEY).get();
        String roomId = ctx.channel().attr(ROOM_ID_KEY).get();
        if (senderId == null || roomId == null) {
            sendError(ctx, "未认证");
            return;
        }

        message.setSenderId(senderId);
        message.setRoomId(roomId);
        message.setTimestamp(System.currentTimeMillis());

        if (message.getReceiverId() != null && !message.getReceiverId().isEmpty()) {
            // 私聊暂简化：在同一房间内找接收者
            Set<Channel> channels = ROOM_CHANNELS_MAP.get(roomId);
            if (channels != null) {
                for (Channel ch : channels) {
                    if (message.getReceiverId().equals(ch.attr(USER_ID_KEY).get()) && ch.isActive()) {
                        ch.writeAndFlush(new TextWebSocketFrame(JSON.toJSONString(message)));
                        return;
                    }
                }
            }
            redisTemplate.opsForList().leftPush("chat:offline:" + message.getReceiverId(), JSON.toJSONString(message));
            sendError(ctx, "对方离线，消息已缓存");
        } else {
            redisTemplate.convertAndSend("chat:broadcast", JSON.toJSONString(message));
        }
    }

    private void handlePing(ChannelHandlerContext ctx) {
        ChatMessage pong = new ChatMessage();
        pong.setType("PONG");
        ctx.channel().writeAndFlush(new TextWebSocketFrame(JSON.toJSONString(pong)));
    }

    private void handleDisconnect(ChannelHandlerContext ctx) {
        removeFromRoom(ctx.channel());
        ctx.close();
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state() == IdleState.READER_IDLE) {
                ctx.close();
            }
        }
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        removeFromRoom(ctx.channel());
    }

    private void removeFromRoom(Channel channel) {
        String roomId = channel.attr(ROOM_ID_KEY).get();
        String userId = channel.attr(USER_ID_KEY).get();
        if (roomId != null) {
            Set<Channel> set = ROOM_CHANNELS_MAP.get(roomId);
            if (set != null) {
                set.remove(channel);
                if (set.isEmpty()) {
                    ROOM_CHANNELS_MAP.remove(roomId);
                }
            }
        }
        if (userId != null) {
            log.info("用户 {} 离开房间 {}", userId, roomId);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("通道异常", cause);
        ctx.close();
    }

    private void sendError(ChannelHandlerContext ctx, String content) {
        ChatMessage error = new ChatMessage();
        error.setType("ERROR");
        error.setContent(content);
        ctx.channel().writeAndFlush(new TextWebSocketFrame(JSON.toJSONString(error)));
    }

    public void handleRedisBroadcast(String msgJson) {
        ChatMessage message = JSON.parseObject(msgJson, ChatMessage.class);
        String roomId = message.getRoomId();
        if (roomId == null) return;

        Set<Channel> channels = ROOM_CHANNELS_MAP.get(roomId);
        if (channels != null) {
            for (Channel ch : channels) {
                if (ch.isActive()) {
                    ch.writeAndFlush(new TextWebSocketFrame(msgJson));
                }
            }
        }
    }
}
