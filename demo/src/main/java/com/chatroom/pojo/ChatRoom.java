package com.chatroom.pojo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatRoom {
    private String id;
    private String name;
    private String creatorId;
    private Long createTime;
}
