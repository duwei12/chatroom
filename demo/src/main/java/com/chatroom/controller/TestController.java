package com.chatroom.controller;

import com.chatroom.utils.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 测试接口（生成Token）
 */
@RestController
@RequestMapping("/test")
@RequiredArgsConstructor
public class TestController {

    private final JwtUtil jwtUtil;

    /**
     * 生成测试Token（生产环境需从用户登录接口生成）
     */
    @GetMapping("/token/{userId}")
    public String generateTestToken(@PathVariable String userId) {
        return jwtUtil.generateToken(userId);
    }
}
