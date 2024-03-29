/*
 * Copyright 2021 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.contrib.example.handler.codec.stomp.websocket;

import io.netty5.bootstrap.ServerBootstrap;
import io.netty5.channel.Channel;
import io.netty5.channel.MultithreadEventLoopGroup;
import io.netty5.channel.nio.NioHandler;
import io.netty5.channel.socket.nio.NioServerSocketChannel;

public class StompWebSocketChatServer {

    static final int PORT = Integer.parseInt(System.getProperty("port", "8080"));

    public void start(final int port) throws Exception {
        MultithreadEventLoopGroup boosGroup = new MultithreadEventLoopGroup(1, NioHandler.newFactory());
        MultithreadEventLoopGroup workerGroup = new MultithreadEventLoopGroup(NioHandler.newFactory());
        try {
            ServerBootstrap bootstrap = new ServerBootstrap()
                    .group(boosGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .childHandler(new StompWebSocketChatServerInitializer("/chat"));
            Channel channel = bootstrap.bind(port).addListener(future -> {
                if (future.isSuccess()) {
                    System.out.println("Open your web browser and navigate to http://127.0.0.1:" + PORT + '/');
                } else {
                    System.out.println("Cannot start server, follows exception " + future.cause());
                }
            }).asStage().get();
            channel.closeFuture().asStage().sync();
        } finally {
            boosGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }
    }

    public static void main(String[] args) throws Exception {
        new StompWebSocketChatServer().start(PORT);
    }
}
