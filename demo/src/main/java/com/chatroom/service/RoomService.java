package com.chatroom.service;

import com.chatroom.pojo.ChatRoom;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class RoomService {

    private static final String ROOMS_KEY = "chat:rooms";
    private static final String ROOM_PREFIX = "chat:room:";

    private final RedisTemplate<String, String> redisTemplate;

    public ChatRoom createRoom(String name, String creatorId) {
        String id = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        long now = System.currentTimeMillis();
        ChatRoom room = new ChatRoom(id, name, creatorId, now);

        redisTemplate.opsForSet().add(ROOMS_KEY, id);
        redisTemplate.opsForHash().put(ROOM_PREFIX + id, "name", name);
        redisTemplate.opsForHash().put(ROOM_PREFIX + id, "creatorId", creatorId);
        redisTemplate.opsForHash().put(ROOM_PREFIX + id, "createTime", String.valueOf(now));

        return room;
    }

    public List<ChatRoom> listRooms() {
        Set<String> ids = redisTemplate.opsForSet().members(ROOMS_KEY);
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }

        List<ChatRoom> rooms = new ArrayList<>();
        for (String id : ids) {
            Map<Object, Object> map = redisTemplate.opsForHash().entries(ROOM_PREFIX + id);
            if (!map.isEmpty()) {
                String name = (String) map.get("name");
                String creatorId = (String) map.get("creatorId");
                String createTimeStr = (String) map.get("createTime");
                Long createTime = createTimeStr != null ? Long.parseLong(createTimeStr) : 0L;
                rooms.add(new ChatRoom(id, name, creatorId, createTime));
            }
        }
        rooms.sort(Comparator.comparing(ChatRoom::getCreateTime).reversed());
        return rooms;
    }

    public boolean exists(String roomId) {
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(ROOMS_KEY, roomId));
    }
}
