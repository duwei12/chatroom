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

    @PostConstruct
    public void start() {
        Thread nettyThread = new Thread(() -> {
            bossGroup = new NioEventLoopGroup(bossThreadCount);
            workerGroup = new NioEventLoopGroup(workerThreadCount);

            try {
                ServerBootstrap bootstrap = new ServerBootstrap();
                bootstrap.group(bossGroup, workerGroup)
                        .channel(NioServerSocketChannel.class)
                        .option(ChannelOption.SO_KEEPALIVE, true)
                        .option(ChannelOption.SO_BACKLOG, 1024)
                        .childHandler(new ChannelInitializer<SocketChannel>() {
                            @Override
                            protected void initChannel(SocketChannel ch) {
                                ch.pipeline().addLast(new IdleStateHandler(idleTime, 0, 0, TimeUnit.SECONDS));
                                ch.pipeline().addLast(new HttpServerCodec());
                                ch.pipeline().addLast(new HttpObjectAggregator(65536));
                                ch.pipeline().addLast(new JsonObjectDecoder());
                                ch.pipeline().addLast(new WebSocketServerProtocolHandler("/ws"));
                                ch.pipeline().addLast(chatMessageHandler);
                            }
                        });

                ChannelFuture future = bootstrap.bind(port).sync();
                log.info("Netty WebSocket 启动成功, 端口: {}", port);
                future.channel().closeFuture().sync();
            } catch (InterruptedException e) {
                log.error("Netty 启动失败", e);
                Thread.currentThread().interrupt();
            }
        }, "netty-websocket");
        nettyThread.setDaemon(false);
        nettyThread.start();
    }

    @PreDestroy
    public void stop() {
        if (bossGroup != null) bossGroup.shutdownGracefully();
        if (workerGroup != null) workerGroup.shutdownGracefully();
        log.info("Netty WebSocket 已停止");
    }
}
