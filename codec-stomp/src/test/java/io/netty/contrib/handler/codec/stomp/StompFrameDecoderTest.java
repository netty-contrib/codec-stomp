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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.netty.contrib.handler.codec.stomp.StompTestConstants.*;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

public class StompFrameDecoderTest {

    private EmbeddedChannel channel;

    @BeforeEach
    void setup() {
        channel = new EmbeddedChannel(new StompFrameDecoder());
    }

    @AfterEach
    void teardown() {
        assertThat(channel.finish()).isFalse();
    }

    @Test
    void shouldDecodeToHeadersAndEmptyContentFrames() {
        byte[] source = CONNECT_FRAME.getBytes(UTF_8);
        Buffer incoming = channel.bufferAllocator().copyOf(source);
        channel.writeInbound(incoming);

        HeadersStompFrame headersFrame = channel.readInbound();
        assertThat(headersFrame).isNotNull()
                .extracting(HeadersStompFrame::command)
                .isEqualTo(StompCommand.CONNECT);

        try (ContentStompFrame<?> contentFrame = channel.readInbound();
             var expectedLastFrame = new EmptyLastContentStompFrame(channel.bufferAllocator())) {
            assertThat(contentFrame).isEqualTo(expectedLastFrame);
        }

        assertThat((Object) channel.readInbound()).isNull();
    }

    @Test
    void shouldDecodeFrameWithContentLengthHeader() {
        byte[] source = SEND_FRAME_WITH_CONTENT_LENGTH.getBytes(UTF_8);
        Buffer incoming = channel.bufferAllocator().copyOf(source);
        channel.writeInbound(incoming);

        HeadersStompFrame headersFrame = channel.readInbound();
        assertThat(headersFrame).isNotNull()
                .extracting(HeadersStompFrame::command)
                .isEqualTo(StompCommand.SEND);

        try (ContentStompFrame<?> contentFrame = channel.readInbound()) {
            assertThat(contentFrame).isExactlyInstanceOf(DefaultLastContentStompFrame.class)
                    .extracting(frame -> frame.payload().toString(UTF_8))
                    .isEqualTo("hello, queue a!!!");
        }

        assertThat((Object) channel.readInbound()).isNull();
    }

    @Test
    void shouldDecodeFrameWithoutContentLengthHeader() {
        byte[] source = SEND_FRAME_WITHOUT_CONTENT_LENGTH.getBytes(UTF_8);
        Buffer incoming = channel.bufferAllocator().copyOf(source);
        channel.writeInbound(incoming);

        HeadersStompFrame headersFrame = channel.readInbound();
        assertThat(headersFrame).isNotNull()
                .extracting(HeadersStompFrame::command)
                .isEqualTo(StompCommand.SEND);

        try (ContentStompFrame<?> contentFrame = channel.readInbound()) {
            assertThat(contentFrame).isExactlyInstanceOf(DefaultLastContentStompFrame.class)
                    .extracting(frame -> frame.payload().toString(UTF_8))
                    .isEqualTo("hello, queue a!");
        }

        assertThat((Object) channel.readInbound()).isNull();
    }

    @Test
    void shouldDecodeFrameByChunks() {
        EmbeddedChannel channel = new EmbeddedChannel(new StompFrameDecoder(10000, 5));
        byte[] source = SEND_FRAME_WITH_CONTENT_LENGTH.getBytes(UTF_8);
        Buffer incoming = channel.bufferAllocator().copyOf(source);
        channel.writeInbound(incoming);

        HeadersStompFrame headersFrame = channel.readInbound();
        assertThat(headersFrame).isNotNull()
                .extracting(HeadersStompFrame::command)
                .isEqualTo(StompCommand.SEND);

        ContentStompFrame<?> chunkContent = channel.readInbound();
        assertThat(chunkContent).isExactlyInstanceOf(DefaultContentStompFrame.class)
                .extracting(content -> content.payload().toString(UTF_8))
                .isEqualTo("hello");
        chunkContent.close();

        chunkContent = channel.readInbound();
        assertThat(chunkContent).isExactlyInstanceOf(DefaultContentStompFrame.class)
                .extracting(content -> content.payload().toString(UTF_8))
                .isEqualTo(", que");
        chunkContent.close();

        chunkContent = channel.readInbound();
        assertThat(chunkContent).isExactlyInstanceOf(DefaultContentStompFrame.class)
                .extracting(content -> content.payload().toString(UTF_8))
                .isEqualTo("ue a!");
        chunkContent.close();

        chunkContent = channel.readInbound();
        assertThat(chunkContent).isExactlyInstanceOf(DefaultLastContentStompFrame.class)
                .extracting(content -> content.payload().toString(UTF_8))
                .isEqualTo("!!");
        chunkContent.close();

        assertThat((Object) channel.readInbound()).isNull();
    }

    @Test
    void shouldDecodeMultipleFrames() {
        Buffer incoming = channel.bufferAllocator().allocate(256);
        incoming.writeBytes(CONNECT_FRAME.getBytes());
        incoming.writeBytes(CONNECTED_FRAME.getBytes());
        channel.writeInbound(incoming);

        HeadersStompFrame headersFrame = channel.readInbound();
        assertThat(headersFrame).isNotNull()
                .extracting(HeadersStompFrame::command)
                .isEqualTo(StompCommand.CONNECT);

        try (ContentStompFrame<?> content = channel.readInbound();
             var expectedLastFrame = new EmptyLastContentStompFrame(channel.bufferAllocator())) {
            assertThat(content).isEqualTo(expectedLastFrame);
        }

        HeadersStompFrame headersFrame2 = channel.readInbound();
        assertThat(headersFrame2).isNotNull()
                .extracting(HeadersStompFrame::command)
                .isEqualTo(StompCommand.CONNECTED);

        try (ContentStompFrame<?> content2 = channel.readInbound();
             var expectedLastFrame = new EmptyLastContentStompFrame(channel.bufferAllocator())) {
            assertThat(content2).isEqualTo(expectedLastFrame);
        }

        assertThat((Object) channel.readInbound()).isNull();
    }

    @Test
    void shouldSkipInvalidHeaderWhenHeaderValidationDisabled() {
        Buffer invalidIncoming = channel.bufferAllocator().copyOf(FRAME_WITH_INVALID_HEADER.getBytes(UTF_8));
        assertThat(channel.writeInbound(invalidIncoming)).isTrue();

        HeadersStompFrame headersFrame = channel.readInbound();
        assertThat(headersFrame).isNotNull()
                .extracting(HeadersStompFrame::command)
                .isEqualTo(StompCommand.SEND);
        assertThat(headersFrame.headers())
                .containsExactly(
                        Map.entry("destination", "/some-destination"),
                        Map.entry("content-type", "text/plain"));

        try (ContentStompFrame<?> content = channel.readInbound()) {
            assertThat(content.payload().toString(UTF_8)).isEqualTo("some body");
        }
    }

    @Test
    void shouldSetFailureWhenInvalidHeaderAndValidationEnabled() {
        assertThat(channel.pipeline().removeIfExists(StompFrameDecoder.class)).isNotNull();
        channel.pipeline().addLast(new StompFrameDecoder(true));

        Buffer invalidIncoming = channel.bufferAllocator().copyOf(FRAME_WITH_INVALID_HEADER.getBytes(UTF_8));
        assertThat(channel.writeInbound(invalidIncoming)).isTrue();

        HeadersStompFrame headersFrame = channel.readInbound();
        assertThat(headersFrame).isNotNull()
                .extracting(HeadersStompFrame::command)
                .isEqualTo(StompCommand.SEND);

        assertThat(headersFrame.decoderResult().isFailure()).isTrue();
        assertThat(headersFrame.decoderResult().cause()).hasMessage(
                "a header value or name contains a prohibited character ':', current-time:2000-01-01T00:00:00");
    }

    @Test
    void shouldSetFailureWhenEmptyHeaderNameAndValidationEnabled() {
        assertThat(channel.pipeline().removeIfExists(StompFrameDecoder.class)).isNotNull();
        channel.pipeline().addLast(new StompFrameDecoder(true));

        Buffer invalidIncoming = channel.bufferAllocator().copyOf(FRAME_WITH_EMPTY_HEADER_NAME.getBytes(UTF_8));
        assertThat(channel.writeInbound(invalidIncoming)).isTrue();

        HeadersStompFrame headersFrame = channel.readInbound();
        assertThat(headersFrame).isNotNull()
                .extracting(HeadersStompFrame::command)
                .isEqualTo(StompCommand.SEND);

        assertThat(headersFrame.decoderResult().isFailure()).isTrue();
        assertThat(headersFrame.decoderResult().cause()).hasMessage("received an invalid header line ':header-value'");
    }

    @Test
    void shouldDecodeHeadersInUtf8Charset() {
        assertThat(channel.pipeline().removeIfExists(StompFrameDecoder.class)).isNotNull();
        channel.pipeline().addLast(new StompFrameDecoder(true));

        Buffer incoming = channel.bufferAllocator().copyOf(SEND_FRAME_UTF8.getBytes(UTF_8));
        assertThat(channel.writeInbound(incoming)).isTrue();

        HeadersStompFrame headersFrame = channel.readInbound();
        assertThat(headersFrame).isNotNull()
                .extracting(HeadersStompFrame::command)
                .isEqualTo(StompCommand.SEND);

        assertThat(headersFrame.decoderResult().isFailure()).isFalse();
        assertThat(headersFrame.headers())
                .containsExactly(
                        Map.entry("destination", "/queue/№11±♛нетти♕"),
                        Map.entry("content-type", "text/plain"));

        try (LastContentStompFrame<?> content = channel.readInbound()) {
            assertThat(content.payload().toString(UTF_8)).isEqualTo("body");
        }
    }

    @Test
    void shouldSetFailureWhenContentLengthOrNullEndingIsMissing() {
        Buffer incoming = channel.bufferAllocator().copyOf(FRAME_WITHOUT_NULL_ENDING.getBytes(UTF_8));
        assertThat(channel.writeInbound(incoming)).isTrue();

        HeadersStompFrame headersFrame = channel.readInbound();
        assertThat(headersFrame).isNotNull()
                .extracting(HeadersStompFrame::command)
                .isEqualTo(StompCommand.SEND);
        assertThat(headersFrame.decoderResult().isFailure()).isFalse();

        try (LastContentStompFrame<?> lastContent = channel.readInbound()) {
            assertThat(lastContent.decoderResult().isFailure()).isTrue();
            assertThat(lastContent.decoderResult().cause())
                    .hasMessage("Unexpected byte in buffer 1 while expecting NULL byte");
        }
    }
}
