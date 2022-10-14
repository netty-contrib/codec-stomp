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

import io.netty.contrib.handler.codec.stomp.*;
import io.netty5.buffer.Buffer;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty5.handler.codec.http.websocketx.ContinuationWebSocketFrame;
import io.netty5.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty5.handler.codec.http.websocketx.WebSocketFrame;

import java.util.List;

public class StompWebSocketFrameEncoder extends StompFrameEncoder {

    @Override
    public void encode(ChannelHandlerContext ctx, StompFrame msg, List<Object> out) throws Exception {
        super.encode(ctx, msg, out);
    }

    @Override
    protected WebSocketFrame convertFullFrame(FullStompFrame original, Buffer encoded) {
        if (isTextFrame(original)) {
            return new TextWebSocketFrame(encoded);
        }

        return new BinaryWebSocketFrame(encoded);
    }

    @Override
    protected WebSocketFrame convertHeadersFrame(HeadersStompFrame original, Buffer encoded) {
        if (isTextFrame(original)) {
            return new TextWebSocketFrame(false, 0, encoded);
        }

        return new BinaryWebSocketFrame(false, 0, encoded);
    }

    @Override
    protected WebSocketFrame convertContentFrame(ContentStompFrame<?> original, Buffer encoded) {
        if (original instanceof LastContentStompFrame) {
            return new ContinuationWebSocketFrame(true, 0, encoded);
        }

        return new ContinuationWebSocketFrame(false, 0, encoded);
    }

    private static boolean isTextFrame(HeadersStompFrame headersFrame) {
        String contentType = headersFrame.headers().getAsString(StompHeaders.CONTENT_TYPE);
        return contentType != null && (contentType.startsWith("text") || contentType.startsWith("application/json"));
    }
}
