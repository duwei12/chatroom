# 聊天室 ChatRoom

基于 **Netty WebSocket** + **Spring Boot** + **Redis** 的实时聊天室。

## 技术栈

- Spring Boot 2.6
- Netty 4.1 (WebSocket)
- Redis (Pub/Sub 广播)
- JWT 认证

## 启动

1. 启动 Redis: `redis-server`
2. 启动应用: `cd demo && mvn spring-boot:run`
3. 浏览器访问: http://localhost:8088/

## 端口

- 8088: HTTP (静态资源 + API)
- 8888: WebSocket

## API

- `GET /api/token/{userId}` — 获取 JWT Token
