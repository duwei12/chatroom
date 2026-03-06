package com.chatroom.utils;

import com.chatroom.handle.ChatMessageHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;

@Slf4j
@Service
@RequiredArgsConstructor
public class RedisPubSubUtil implements MessageListener{

    private final RedisTemplate<String, String> redisTemplate;
    private final RedisMessageListenerContainer redisMessageListenerContainer;
    @Lazy
    private final ChatMessageHandler chatMessageHandler;

    /**
     * 订阅广播通道
     */
    @PostConstruct
    public void subscribe() {
        MessageListenerAdapter adapter = new MessageListenerAdapter(this, "onMessage");
        redisMessageListenerContainer.addMessageListener(adapter, new ChannelTopic("chat:broadcast"));
        log.info("Redis订阅通道：chat:broadcast");
    }

    /**
     * 发布消息到广播通道
     */
    public void publish(String channel, String message) {
        redisTemplate.convertAndSend(channel, message);
        log.info("发布消息到Redis通道{}：{}", channel, message);
    }

    /**
     * 处理Redis订阅消息
     */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        String msgJson = new String(message.getBody());
        log.info("收到Redis广播消息：{}", msgJson);
        // 交给Netty处理器广播给当前节点用户
        chatMessageHandler.handleRedisBroadcast(msgJson);
    }
}
