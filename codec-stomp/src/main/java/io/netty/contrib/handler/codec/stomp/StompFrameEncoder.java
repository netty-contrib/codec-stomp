/*
 * Copyright 2022 The Netty Project
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
package io.netty.contrib.handler.codec.stomp;

import io.netty5.buffer.Buffer;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.handler.codec.MessageToMessageEncoder;

import java.util.List;
import java.util.Map.Entry;

import static io.netty.contrib.handler.codec.stomp.StompConstants.*;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Encodes a {@link FullStompFrame} or a {@link StompFrame} into a {@link Buffer}.
 */
public class StompFrameEncoder extends MessageToMessageEncoder<StompFrame> {

    @Override
    protected void encode(ChannelHandlerContext ctx, StompFrame msg, List<Object> out) throws Exception {
        if (msg instanceof FullStompFrame) {
            FullStompFrame fullStompFrame = (FullStompFrame) msg;
            Buffer buffer = encodeFullFrame(fullStompFrame, ctx);

            Object convertedFull = convertFullFrame(fullStompFrame, buffer);
            out.add(convertedFull);
        } else if (msg instanceof HeadersStompFrame) {
            HeadersStompFrame headersFrame = (HeadersStompFrame) msg;
            Buffer buffer = ctx.bufferAllocator().allocate(headersSubFrameSize(headersFrame));
            encodeHeaders(headersFrame, buffer);

            Object convertedHeaders = convertHeadersFrame(headersFrame, buffer);
            out.add(convertedHeaders);
        } else if (msg instanceof ContentStompFrame) {
            ContentStompFrame<?> contentFrame = (ContentStompFrame<?>) msg;
            Buffer buffer = encodeContent(contentFrame, ctx);

            Object convertedContent = convertContentFrame(contentFrame, buffer);
            out.add(convertedContent);
        }
    }

    /**
     * An extension method to convert a STOMP encoded buffer to a different message type
     * based on an original {@link FullStompFrame} full frame.
     *
     * <p>By default an encoded buffer is returned as is.
     */
    protected Object convertFullFrame(FullStompFrame original, Buffer encoded) {
        return encoded;
    }

    /**
     * An extension method to convert a STOMP encoded buffer to a different message type
     * based on an original {@link HeadersStompFrame} headers sub frame.
     *
     * <p>By default an encoded buffer is returned as is.
     */
    protected Object convertHeadersFrame(HeadersStompFrame original, Buffer encoded) {
        return encoded;
    }

    /**
     * An extension method to convert a STOMP encoded buffer to a different message type
     * based on an original {@link HeadersStompFrame} content sub frame.
     *
     * <p>By default an encoded buffer is returned as is.
     */
    protected Object convertContentFrame(ContentStompFrame<?> original, Buffer encoded) {
        return encoded;
    }

    /**
     * Returns a heuristic size for headers (32 bytes per header line) + (2 bytes for colon and eol) + (additional
     * command buffer).
     */
    protected int headersSubFrameSize(HeadersStompFrame headersFrame) {
        int estimatedSize = headersFrame.headers().size() * 34 + 48;
        if (estimatedSize < 128) {
            return 128;
        }

        return Math.max(estimatedSize, 256);

    }

    private Buffer encodeFullFrame(FullStompFrame fullFrame, ChannelHandlerContext ctx) {
        int contentReadableBytes = fullFrame.payload().readableBytes();
        Buffer buf = ctx.bufferAllocator().allocate(headersSubFrameSize(fullFrame) + contentReadableBytes);
        encodeHeaders(fullFrame, buf);

        if (contentReadableBytes > 0) {
            buf.writeBytes(fullFrame.payload());
        }

        return buf.writeByte(NUL);
    }

    private static void encodeHeaders(HeadersStompFrame headersFrame, Buffer buf) {
        buf.writeCharSequence(headersFrame.command().toString(), UTF_8);
        buf.writeByte(LF);

        for (Entry<CharSequence, CharSequence> entry : headersFrame.headers()) {
            buf.writeCharSequence(entry.getKey(), UTF_8)
                .writeByte(COLON)
                .writeCharSequence(entry.getValue(), UTF_8)
                .writeByte(LF);
        }

        buf.writeByte(LF);
    }

    private static Buffer encodeContent(ContentStompFrame<?> contentFrame, ChannelHandlerContext ctx) {
        if (contentFrame instanceof LastContentStompFrame) {
            Buffer buf = ctx.bufferAllocator().allocate(contentFrame.payload().readableBytes() + 1);
            buf.writeBytes(contentFrame.payload())
                .writeByte(NUL);
            return buf;
        }

        return contentFrame.payload();
    }
}
