package com.chatroom.controller;

import com.chatroom.utils.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AuthController {

    private final JwtUtil jwtUtil;

    @GetMapping("/token/{userId}")
    public Map<String, String> getToken(@PathVariable String userId) {
        Map<String, String> result = new HashMap<>();
        result.put("token", jwtUtil.generateToken(userId));
        return result;
    }
}
