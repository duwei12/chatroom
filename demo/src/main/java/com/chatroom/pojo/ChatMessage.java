package com.chatroom.pojo;

import lombok.Data;

@Data
public class ChatMessage {
    private String type;       // CONNECT, MESSAGE, PING, PONG, DISCONNECT
    private String senderId;
    private String receiverId;
    private String roomId;     // 房间ID
    private String content;
    private Long timestamp;
    private String token;
}
