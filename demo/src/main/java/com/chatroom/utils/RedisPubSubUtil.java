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
public class RedisPubSubUtil implements MessageListener {

    private final RedisTemplate<String, String> redisTemplate;
    private final RedisMessageListenerContainer redisMessageListenerContainer;
    @Lazy
    private final ChatMessageHandler chatMessageHandler;

    @PostConstruct
    public void subscribe() {
        MessageListenerAdapter adapter = new MessageListenerAdapter(this, "onMessage");
        redisMessageListenerContainer.addMessageListener(adapter, new ChannelTopic("chat:broadcast"));
        log.info("Redis 订阅通道: chat:broadcast");
    }

    public void publish(String channel, String message) {
        redisTemplate.convertAndSend(channel, message);
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String msgJson = new String(message.getBody());
        log.info("收到 Redis 广播: {}", msgJson);
        chatMessageHandler.handleRedisBroadcast(msgJson);
    }
}
