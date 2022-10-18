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
import io.netty5.channel.embedded.EmbeddedChannel;
import io.netty5.handler.codec.TooLongFrameException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.netty.contrib.handler.codec.stomp.StompTestConstants.*;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class StompFrameAggregatorTest {

    private EmbeddedChannel channel;

    @BeforeEach
    void setup() {
        channel = new EmbeddedChannel(new StompFrameDecoder(true), new StompFrameAggregator<>(1024 * 1024));
    }

    @AfterEach
    void teardown() {
        assertThat(channel.finish()).isFalse();
    }

    @Test
    void shouldAggregateFrameWithoutAnyBody() {
        byte[] source = CONNECT_FRAME.getBytes(UTF_8);
        Buffer incoming = channel.bufferAllocator().copyOf(source);
        assertThat(channel.writeInbound(incoming)).isTrue();

        try (DefaultFullStompFrame fullFrame = channel.readInbound()) {
            assertThat(fullFrame).isNotNull();
            assertThat(fullFrame.payload().readableBytes()).isEqualTo(0);
        }

        assertThat((Object) channel.readInbound()).isNull();
    }

    @Test
    void shouldAggregateFrameWithBodyAndContentLength() {
        byte[] connectFrame = SEND_FRAME_WITH_CONTENT_LENGTH.getBytes(UTF_8);
        Buffer incoming = channel.bufferAllocator().copyOf(connectFrame);
        assertThat(channel.writeInbound(incoming)).isTrue();

        try (FullStompFrame fullFrame = channel.readInbound()) {
            assertThat(fullFrame).isNotNull()
                    .extracting(FullStompFrame::command)
                    .isEqualTo(StompCommand.SEND);
            assertThat(fullFrame.payload().toString(UTF_8)).isEqualTo("hello, queue a!!!");
        }

        assertThat((Object) channel.readInbound()).isNull();
    }

    @Test
    void shouldAggregateFrameWithBodyAndNoContentLength() {
        byte[] connectFrame = SEND_FRAME_WITHOUT_CONTENT_LENGTH.getBytes(UTF_8);
        Buffer incoming = channel.bufferAllocator().copyOf(connectFrame);
        assertThat(channel.writeInbound(incoming)).isTrue();

        try (FullStompFrame frame = channel.readInbound()) {
            assertThat(frame).isNotNull()
                    .extracting(FullStompFrame::command)
                    .isEqualTo(StompCommand.SEND);
            assertThat(frame.payload().toString(UTF_8)).isEqualTo("hello, queue a!");
        }

        assertThat((Object) channel.readInbound()).isNull();
    }

    @Test
    void shouldAggregateFrameWithSplitBodyAndNoContentLength() {
        for (String framePart : SEND_FRAME_WITHOUT_CONTENT_LENGTH_PARTS) {
            Buffer incoming = channel.bufferAllocator().copyOf(framePart.getBytes(UTF_8));
            channel.writeInbound(incoming);
        }

        try (FullStompFrame fullFrame = channel.readInbound()) {
            assertThat(fullFrame).isNotNull()
                    .extracting(FullStompFrame::command)
                    .isEqualTo(StompCommand.SEND);
            assertThat(fullFrame.payload().toString(UTF_8)).isEqualTo("first part of body\nsecond part of body");
        }

        assertThat((Object) channel.readInbound()).isNull();
    }

    @Test
    void shouldAggregateFrameWhenSplitByChunks() {
        assertThat(channel.pipeline().removeIfExists(StompFrameDecoder.class)).isNotNull();
        channel.pipeline().addFirst(new StompFrameDecoder(1000, 5));

        byte[] source = SEND_FRAME_WITH_CONTENT_LENGTH.getBytes(UTF_8);
        Buffer incoming = channel.bufferAllocator().copyOf(source);
        assertThat(channel.writeInbound(incoming)).isTrue();

        try (FullStompFrame frame = channel.readInbound()) {
            assertThat(frame).isNotNull()
                    .extracting(FullStompFrame::command)
                    .isEqualTo(StompCommand.SEND);
            assertThat(frame.payload().toString(UTF_8)).isEqualTo("hello, queue a!!!");
        }

        assertThat((Object) channel.readInbound()).isNull();
    }

    @Test
    void shouldAggregateWhenMultiplesFrameSend() {
        Buffer incoming = channel.bufferAllocator().allocate(256);
        incoming.writeBytes(CONNECT_FRAME.getBytes());
        incoming.writeBytes(StompTestConstants.CONNECTED_FRAME.getBytes());
        channel.writeInbound(incoming);
        channel.writeInbound(channel.bufferAllocator().copyOf(SEND_FRAME_WITHOUT_CONTENT_LENGTH.getBytes(UTF_8)));

        FullStompFrame frame = channel.readInbound();
        assertThat(frame.command()).isEqualTo(StompCommand.CONNECT);
        frame.close();

        frame = channel.readInbound();
        assertThat(frame.command()).isEqualTo(StompCommand.CONNECTED);
        frame.close();

        frame = channel.readInbound();
        assertThat(frame.command()).isEqualTo(StompCommand.SEND);
        frame.close();

        assertThat((Object) channel.readInbound()).isNull();
    }

    @Test
    void shouldAggregateWhenFailureHeadersFrameSend() {
        byte[] source = FRAME_WITH_EMPTY_HEADER_NAME.getBytes(UTF_8);
        Buffer invalidIncoming = channel.bufferAllocator().copyOf(source);
        assertThat(channel.writeInbound(invalidIncoming)).isTrue();

        try (FullStompFrame fullFrame = channel.readInbound()) {
            assertThat(fullFrame).isNotNull()
                    .extracting(HeadersStompFrame::command)
                    .isEqualTo(StompCommand.SEND);

            assertThat(fullFrame.decoderResult().isFailure()).isTrue();
            assertThat(fullFrame.decoderResult().cause()).hasMessage("received an invalid header line ':header-value'");
        }
    }

    @Test
    void shouldAggregateWhenFailureLastContentFrameSend() {
        byte[] source = FRAME_WITHOUT_NULL_ENDING.getBytes(UTF_8);
        Buffer invalidIncoming = channel.bufferAllocator().copyOf(source);
        assertThat(channel.writeInbound(invalidIncoming)).isTrue();

        try (FullStompFrame fullFrame = channel.readInbound()) {
            assertThat(fullFrame).isNotNull()
                    .extracting(HeadersStompFrame::command)
                    .isEqualTo(StompCommand.SEND);

            assertThat(fullFrame.decoderResult().isFailure()).isTrue();
            assertThat(fullFrame.decoderResult().cause())
                    .hasMessage("Unexpected byte in buffer 1 while expecting NULL byte");
        }
    }

    @Test
    void shouldThrowExceptionWhenMaxAggregatedLengthReached() {
        EmbeddedChannel channel = new EmbeddedChannel(new StompFrameDecoder(), new StompFrameAggregator<>(10));
        assertThatExceptionOfType(TooLongFrameException.class)
                .isThrownBy(() -> channel.writeInbound(
                        channel.bufferAllocator().copyOf(SEND_FRAME_WITHOUT_CONTENT_LENGTH.getBytes(UTF_8))));
    }
}
