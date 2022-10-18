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
import io.netty5.util.concurrent.FastThreadLocal;
import io.netty5.util.internal.AppendableCharSequence;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map.Entry;

import static io.netty.contrib.handler.codec.stomp.StompConstants.*;
import static io.netty.contrib.handler.codec.stomp.StompHeaders.*;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Encodes a {@link FullStompFrame} or a {@link StompFrame} into a {@link Buffer}.
 */
public class StompFrameEncoder extends MessageToMessageEncoder<StompFrame> {

    private static final int ESCAPE_HEADER_KEY_CACHE_LIMIT = 32;
    private static final float DEFAULT_LOAD_FACTOR = 0.75f;
    private static final FastThreadLocal<LinkedHashMap<CharSequence, CharSequence>> ESCAPE_HEADER_KEY_CACHE = new FastThreadLocal<>() {
        @Override
        protected LinkedHashMap<CharSequence, CharSequence> initialValue() {
            LinkedHashMap<CharSequence, CharSequence> cache = new LinkedHashMap<>(
                    ESCAPE_HEADER_KEY_CACHE_LIMIT, DEFAULT_LOAD_FACTOR, true) {

                @Override
                protected boolean removeEldestEntry(Entry eldest) {
                    return size() > ESCAPE_HEADER_KEY_CACHE_LIMIT;
                }
            };

            cache.put(ACCEPT_VERSION, ACCEPT_VERSION);
            cache.put(HOST, HOST);
            cache.put(LOGIN, LOGIN);
            cache.put(PASSCODE, PASSCODE);
            cache.put(HEART_BEAT, HEART_BEAT);
            cache.put(VERSION, VERSION);
            cache.put(SESSION, SESSION);
            cache.put(SERVER, SERVER);
            cache.put(DESTINATION, DESTINATION);
            cache.put(ID, ID);
            cache.put(ACK, ACK);
            cache.put(TRANSACTION, TRANSACTION);
            cache.put(RECEIPT, RECEIPT);
            cache.put(MESSAGE_ID, MESSAGE_ID);
            cache.put(SUBSCRIPTION, SUBSCRIPTION);
            cache.put(RECEIPT_ID, RECEIPT_ID);
            cache.put(MESSAGE, MESSAGE);
            cache.put(CONTENT_LENGTH, CONTENT_LENGTH);
            cache.put(CONTENT_TYPE, CONTENT_TYPE);

            return cache;
        }
    };

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
        StompCommand command = headersFrame.command();
        buf.writeCharSequence(command.toString(), UTF_8);
        buf.writeByte(LF);

        boolean shouldEscape = shouldEscape(command);
        LinkedHashMap<CharSequence, CharSequence> cache = ESCAPE_HEADER_KEY_CACHE.get();
        for (Entry<CharSequence, CharSequence> entry : headersFrame.headers()) {
            CharSequence headerKey = entry.getKey();
            if (shouldEscape) {
                headerKey = cache.computeIfAbsent(headerKey, StompFrameEncoder::escape);
            }

            buf.writeCharSequence(headerKey, UTF_8)
                    .writeByte(COLON);

            CharSequence headerValue = shouldEscape ? escape(entry.getValue()) : entry.getValue();
            buf.writeCharSequence(headerValue, UTF_8)
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

    private static boolean shouldEscape(StompCommand command) {
        return command != StompCommand.CONNECT && command != StompCommand.CONNECTED;
    }

    private static CharSequence escape(CharSequence input) {
        AppendableCharSequence builder = null;
        for (int i = 0; i < input.length(); i++) {
            char chr = input.charAt(i);
            if (chr == '\\') {
                builder = escapeBuilder(builder, input, i);
                builder.append("\\\\");
            } else if (chr == ':') {
                builder = escapeBuilder(builder, input, i);
                builder.append("\\c");
            } else if (chr == '\n') {
                builder = escapeBuilder(builder, input, i);
                builder.append("\\n");
            } else if (chr == '\r') {
                builder = escapeBuilder(builder, input, i);
                builder.append("\\r");
            } else if (builder != null) {
                builder.append(chr);
            }
        }

        return builder != null ? builder : input;
    }

    private static AppendableCharSequence escapeBuilder(AppendableCharSequence builder, CharSequence input,
                                                        int offset) {
        if (builder != null) {
            return builder;
        }

        // Add extra overhead to the input char sequence to avoid resizing during escaping.
        return new AppendableCharSequence(input.length() + 8).append(input, 0, offset);
    }
}
