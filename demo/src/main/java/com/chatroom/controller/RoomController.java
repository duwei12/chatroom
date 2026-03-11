package com.chatroom.controller;

import com.chatroom.pojo.ChatRoom;
import com.chatroom.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    @PostMapping("/rooms")
    public ChatRoom createRoom(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        String creatorId = body.get("creatorId");
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("房间名称不能为空");
        }
        if (creatorId == null || creatorId.trim().isEmpty()) {
            throw new IllegalArgumentException("创建者不能为空");
        }
        return roomService.createRoom(name.trim(), creatorId.trim());
    }

    @GetMapping("/rooms")
    public List<ChatRoom> listRooms() {
        return roomService.listRooms();
    }
}
