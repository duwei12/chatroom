package com.chatroom.utils;

import com.chatroom.handle.ChatMessageHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.codec.json.JsonObjectDecoder;
import io.netty.handler.timeout.IdleStateHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.util.concurrent.TimeUnit;

/**
 * Netty WebSocket服务启动器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NettyWebSocketUtil {

    @Value("${netty.websocket.port}")
    private int port;

    @Value("${netty.websocket.boss-thread-count}")
    private int bossThreadCount;

    @Value("${netty.websocket.worker-thread-count}")
    private int workerThreadCount;

    @Value("${netty.websocket.idle-time}")
    private int idleTime;

    @Resource
    private ChatMessageHandler chatMessageHandler;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    /**
     * 启动Netty服务（Spring初始化后执行）
     */
    @PostConstruct
    public void start() {
        bossGroup = new NioEventLoopGroup(bossThreadCount);
        workerGroup = new NioEventLoopGroup(workerThreadCount);

        try {
            ServerBootstrap bootstrap = new ServerBootstrap();
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    // 开启TCP底层心跳保活
                    .option(ChannelOption.SO_KEEPALIVE, true)
                    // 连接队列大小
                    .option(ChannelOption.SO_BACKLOG, 1024)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            // 1. 心跳检测：读空闲超时触发IdleStateEvent
                            ch.pipeline().addLast(new IdleStateHandler(idleTime, 0, 0, TimeUnit.SECONDS));
                            // 2. HTTP编解码器（WebSocket基于HTTP握手）
                            ch.pipeline().addLast(new HttpServerCodec());
                            // 3. HTTP消息聚合（处理大消息）
                            ch.pipeline().addLast(new HttpObjectAggregator(65536));
                            // 4. JSON解码器（处理JSON消息）
                            ch.pipeline().addLast(new JsonObjectDecoder());
                            // 5. WebSocket协议处理器（指定WebSocket路径）
                            ch.pipeline().addLast(new WebSocketServerProtocolHandler("/ws"));
                            // 6. 自定义消息处理器
                            ch.pipeline().addLast(chatMessageHandler);
                        }
                    });

            // 绑定端口并启动
            ChannelFuture future = bootstrap.bind(port).sync();
            log.info("Netty WebSocket服务启动成功，端口：{}", port);
            // 阻塞直到服务关闭
            future.channel().closeFuture().sync();
        } catch (InterruptedException e) {
            log.error("Netty服务启动失败", e);
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 停止Netty服务（Spring销毁前执行）
     */
    @PreDestroy
    public void stop() {
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        log.info("Netty WebSocket服务已停止");
    }
}
