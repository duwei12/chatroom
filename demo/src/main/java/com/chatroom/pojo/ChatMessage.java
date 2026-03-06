package com.chatroom.pojo;

import lombok.Data;

/**
 * 统一消息体（前后端/服务端间通用）
 */
@Data
public class ChatMessage {
    /**
     * 消息类型：CONNECT(连接), MESSAGE(普通消息), PING(心跳), PONG(心跳响应), DISCONNECT(断开)
     */
    private String type;
    /**
     * 发送者ID
     */
    private String senderId;
    /**
     * 接收者ID（空表示广播）
     */
    private String receiverId;
    /**
     * 消息内容
     */
    private String content;
    /**
     * 消息时间戳
     */
    private Long timestamp;
    /**
     * JWT令牌（连接时携带）
     */
    private String token;
}
