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

import io.netty.contrib.handler.codec.stomp.StompFrame;
import io.netty.contrib.handler.codec.stomp.StompFrameAggregator;
import io.netty.contrib.handler.codec.stomp.StompFrameDecoder;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.handler.codec.MessageToMessageCodec;
import io.netty5.handler.codec.http.websocketx.*;

import java.util.List;

public class StompWebSocketProtocolCodec extends MessageToMessageCodec<WebSocketFrame, StompFrame> {

    private final StompChatHandler stompChatHandler = new StompChatHandler();
    private final StompWebSocketFrameEncoder stompWebSocketFrameEncoder = new StompWebSocketFrameEncoder();

    @Override
    public boolean isSharable() {
        return true;
    }

    @Override
    public void channelInboundEvent(ChannelHandlerContext ctx, Object event) throws Exception {
        if (event instanceof WebSocketServerHandshakeCompletionEvent) {
            StompVersion stompVersion = StompVersion.findBySubProtocol(((WebSocketServerHandshakeCompletionEvent) event).selectedSubprotocol());
            ctx.channel().attr(StompVersion.CHANNEL_ATTRIBUTE_KEY).set(stompVersion);
            ctx.pipeline()
                .addLast(new WebSocketFrameAggregator(65536))
                .addLast(new StompFrameDecoder())
                .addLast(new StompFrameAggregator<>(65536))
                .addLast(stompChatHandler)
                .remove(StompWebSocketClientPageHandler.INSTANCE);
        } else {
            super.channelInboundEvent(ctx, event);
        }
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, StompFrame stompFrame, List<Object> out) throws Exception {
        stompWebSocketFrameEncoder.encode(ctx, stompFrame, out);
    }

    @Override
    protected void decodeAndClose(ChannelHandlerContext ctx, WebSocketFrame webSocketFrame) {
        if (webSocketFrame instanceof TextWebSocketFrame || webSocketFrame instanceof BinaryWebSocketFrame) {
            ctx.fireChannelRead(webSocketFrame.binaryData());
        } else {
            try (webSocketFrame) {
                ctx.close();
            }
        }
    }
}
