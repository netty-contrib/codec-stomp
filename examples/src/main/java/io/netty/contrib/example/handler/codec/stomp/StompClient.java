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
package io.netty.contrib.example.handler.codec.stomp;

import io.netty.contrib.handler.codec.stomp.StompFrameAggregator;
import io.netty.contrib.handler.codec.stomp.StompFrameDecoder;
import io.netty.contrib.handler.codec.stomp.StompFrameEncoder;
import io.netty5.bootstrap.Bootstrap;
import io.netty5.channel.ChannelInitializer;
import io.netty5.channel.ChannelPipeline;
import io.netty5.channel.EventLoopGroup;
import io.netty5.channel.MultithreadEventLoopGroup;
import io.netty5.channel.nio.NioHandler;
import io.netty5.channel.socket.SocketChannel;
import io.netty5.channel.socket.nio.NioSocketChannel;

/**
 * Very simple stomp client implementation example, requires running stomp server to actually work
 * uses default username/password and destination values from hornetq message broker.
 */
public final class StompClient {

    static final String HOST = System.getProperty("host", "127.0.0.1");
    static final int PORT = Integer.parseInt(System.getProperty("port", "61613"));
    static final String LOGIN = System.getProperty("login", "guest");
    static final String PASSCODE = System.getProperty("passcode", "guest");
    static final String TOPIC = System.getProperty("topic", "jms.topic.exampleTopic");

    public static void main(String[] args) throws Exception {
        EventLoopGroup group = new MultithreadEventLoopGroup(NioHandler.newFactory());
        try {
            Bootstrap b = new Bootstrap();
            b.group(group).channel(NioSocketChannel.class);
            b.handler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) throws Exception {
                    ChannelPipeline pipeline = ch.pipeline();
                    pipeline.addLast("decoder", new StompFrameDecoder());
                    pipeline.addLast("encoder", new StompFrameEncoder());
                    pipeline.addLast("aggregator", new StompFrameAggregator<>(1048576));
                    pipeline.addLast("handler", new StompClientHandler());
                }
            });

            b.connect(HOST, PORT).asStage().get().closeFuture().asStage().sync();
        } finally {
            group.shutdownGracefully();
        }
    }
}
