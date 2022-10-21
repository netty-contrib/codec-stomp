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

import static io.netty.contrib.handler.codec.stomp.StompTestConstants.*;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Map.entry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

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
                        entry("destination", "/some-destination"),
                        entry("content-type", "text/plain"));

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
                "Header value or name contains prohibited character ':', current-time:2000-01-01T00:00:00");
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
        assertThat(headersFrame.decoderResult().cause()).hasMessage("Received an invalid header line ':header-value'");
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
                        entry("destination", "/queue/№11±♛нетти♕"),
                        entry("content-type", "text/plain"));

        try (LastContentStompFrame<?> content = channel.readInbound()) {
            assertThat(content.payload().toString(UTF_8)).isEqualTo("body");
        }
    }

    @Test
    void shouldSetFailureWhenNullEndingIsMissing() {
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

    @Test
    void shouldUnescapeFrameWhenEscaped() {
        byte[] source = StompTestConstants.ESCAPED_MESSAGE_FRAME.getBytes(UTF_8);
        Buffer incoming = channel.bufferAllocator().copyOf(source);
        assertTrue(channel.writeInbound(incoming));

        HeadersStompFrame headersFrame = channel.readInbound();
        assertThat(headersFrame).isNotNull();
        assertThat(headersFrame.decoderResult().isFailure()).isFalse();
        assertThat(headersFrame.headers()).hasSize(6)
                .containsExactlyInAnyOrder(
                        entry("message-id", "100"),
                        entry("subscription", "1"),
                        entry("destination", "/queue/a:"),
                        entry("header\\\r\n:Name", "header\\\r\n:Value"),
                        entry("header_\\_\r_\n_:_Name", "header_\\_\r_\n_:_Value"),
                        entry("headerName:", ":headerValue"));

        try (LastContentStompFrame<?> contentFrame = channel.readInbound();
             var expectedLastFrame = new EmptyLastContentStompFrame(channel.bufferAllocator())) {
            assertThat(contentFrame).isEqualTo(expectedLastFrame);
        }

        assertThat((Object) channel.readInbound()).isNull();
    }

    @Test
    void shouldNotUnescapeFrameWhenConnectCommand() {
        String sourceConnectFrame = "CONNECT\n"
                + "headerName-\\\\:headerValue-\\\\\n"
                + "\n\0";

        Buffer incoming = channel.bufferAllocator().copyOf(sourceConnectFrame.getBytes(UTF_8));
        assertTrue(channel.writeInbound(incoming));

        HeadersStompFrame headersFrame = channel.readInbound();
        assertThat(headersFrame).isNotNull();
        assertThat(headersFrame.decoderResult().isFailure()).isFalse();
        assertThat(headersFrame.headers()).hasSize(1)
                .containsExactlyInAnyOrder(entry("headerName-\\\\", "headerValue-\\\\"));

        try (LastContentStompFrame<?> contentFrame = channel.readInbound();
             var expectedLastFrame = new EmptyLastContentStompFrame(channel.bufferAllocator())) {
            assertThat(contentFrame).isEqualTo(expectedLastFrame);
        }

        assertThat((Object) channel.readInbound()).isNull();
    }

    @Test
    void shouldNotUnescapeFrameWhenConnectedCommand() {
        String sourceConnectedFrame = "CONNECTED\n"
                + "headerName-\\\\:headerValue-\\\\\n"
                + "\n\0";

        Buffer incoming = channel.bufferAllocator().copyOf(sourceConnectedFrame.getBytes(UTF_8));
        assertTrue(channel.writeInbound(incoming));

        HeadersStompFrame headersFrame = channel.readInbound();
        assertThat(headersFrame).isNotNull();
        assertThat(headersFrame.decoderResult().isFailure()).isFalse();
        assertThat(headersFrame.headers()).hasSize(1)
                .containsExactlyInAnyOrder(entry("headerName-\\\\", "headerValue-\\\\"));

        try (LastContentStompFrame<?> contentFrame = channel.readInbound();
             var expectedLastFrame = new EmptyLastContentStompFrame(channel.bufferAllocator())) {
            assertThat(contentFrame).isEqualTo(expectedLastFrame);
        }

        assertThat((Object) channel.readInbound()).isNull();
    }

    @Test
    void shouldSetFailureWhenInvalidEscapedFrame() {
        assertThat(channel.pipeline().removeIfExists(StompFrameDecoder.class)).isNotNull();
        channel.pipeline().addLast(new StompFrameDecoder(true));

        byte[] source = INVALID_ESCAPED_MESSAGE_FRAME.getBytes(UTF_8);
        Buffer incoming = channel.bufferAllocator().copyOf(source);
        assertTrue(channel.writeInbound(incoming));


        HeadersStompFrame headersFrame = channel.readInbound();
        assertThat(headersFrame).isNotNull();
        assertThat(headersFrame.decoderResult().isFailure()).isTrue();
        assertThat(headersFrame.decoderResult().cause())
                .hasMessage("received an invalid escape header sequence 'custom_invalid\\t'");

        assertThat((Object) channel.readInbound()).isNull();
    }
}
