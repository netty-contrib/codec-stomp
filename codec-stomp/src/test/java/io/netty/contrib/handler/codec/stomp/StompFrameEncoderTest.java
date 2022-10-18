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
import static org.assertj.core.api.Assertions.assertThat;

public class StompFrameEncoderTest {

    private EmbeddedChannel channel;

    @BeforeEach
    void setup() {
        channel = new EmbeddedChannel(new StompFrameEncoder());
    }

    @AfterEach
    void teardown() {
        assertThat(channel.finish()).isFalse();
    }

    @Test
    void shouldEncodeHeadersAndLastContentFrames() {
        HeadersStompFrame headersFrame = new DefaultHeadersStompFrame(StompCommand.CONNECT);
        StompHeaders headers = headersFrame.headers();
        headers.set(StompHeaders.HOST, "stomp.github.io")
                .set(StompHeaders.ACCEPT_VERSION, "1.1,1.2");
        channel.writeOutbound(headersFrame);
        channel.writeOutbound(new EmptyLastContentStompFrame(channel.bufferAllocator()));

        Buffer aggregatedBuffer = channel.bufferAllocator().allocate(1024);
        Buffer buffer = channel.readOutbound();
        assertThat(buffer).isNotNull();
        aggregatedBuffer.writeBytes(buffer);
        buffer.close();

        buffer = channel.readOutbound();
        assertThat(buffer).isNotNull();
        aggregatedBuffer.writeBytes(buffer);
        buffer.close();

        String content = aggregatedBuffer.toString(UTF_8);
        assertThat(content).isEqualTo(CONNECT_FRAME);
        aggregatedBuffer.close();
    }

    @Test
    void shouldEncodeHeadersInUtf8Charset() {
        FullStompFrame frame = new DefaultFullStompFrame(StompCommand.SEND, channel.bufferAllocator().copyOf("body".getBytes(UTF_8)));
        StompHeaders headers = frame.headers();
        headers.set(StompHeaders.DESTINATION, "/queue/№11±♛нетти♕")
                .set(StompHeaders.CONTENT_TYPE, "text/plain");

        channel.writeOutbound(frame);

        Buffer fullFrame = channel.readOutbound();
        assertThat(fullFrame.toString(UTF_8)).isEqualTo(SEND_FRAME_UTF8);
        fullFrame.close();
    }

    @Test
    void shouldEncodeFullFrameWithEmptyPayloadToOneBuffer() {
        FullStompFrame connectedFrame = new DefaultFullStompFrame(StompCommand.CONNECTED);
        connectedFrame.headers().set(StompHeaders.VERSION, "1.2");

        assertThat(channel.writeOutbound(connectedFrame)).isTrue();

        try (Buffer stompBuffer = channel.readOutbound()) {
            assertThat(stompBuffer).isNotNull();
            assertThat((Object) channel.readOutbound()).isNull();
            assertThat(stompBuffer.toString(UTF_8)).isEqualTo("CONNECTED\nversion:1.2\n\n\0");
        }
    }

    @Test
    void shouldEscapeStompHeaders() {
        FullStompFrame messageFrame = new DefaultFullStompFrame(StompCommand.MESSAGE);
        messageFrame.headers()
                .add(StompHeaders.MESSAGE_ID, "100")
                .add(StompHeaders.SUBSCRIPTION, "1")
                .add(StompHeaders.DESTINATION, "/queue/a:")
                .add("header\\\r\n:Name", "header\\\r\n:Value")
                .add("header_\\_\r_\n_:_Name", "header_\\_\r_\n_:_Value")
                .add("headerName:", ":headerValue");

        assertThat(channel.writeOutbound(messageFrame)).isTrue();

        try (Buffer stompBuffer = channel.readOutbound()) {
            assertThat(stompBuffer).isNotNull();
            assertThat(stompBuffer.toString(UTF_8)).isEqualTo(ESCAPED_MESSAGE_FRAME);
        }

        assertThat((Object) channel.readOutbound()).isNull();
    }

    @Test
    void shouldNotEscapeStompHeadersWhenConnectCommand() {
        String expectedConnectFrame = "CONNECT\n" +
                "colonHeaderName-::colonHeaderValue-:\n" +
                "\n\0";
        FullStompFrame connectFrame = new DefaultFullStompFrame(StompCommand.CONNECT);
        connectFrame.headers()
                .add("colonHeaderName-:", "colonHeaderValue-:");

        assertThat(channel.writeOutbound(connectFrame)).isTrue();

        try (Buffer stompBuffer = channel.readOutbound()) {
            assertThat(stompBuffer).isNotNull();
            assertThat(stompBuffer.toString(UTF_8)).isEqualTo(expectedConnectFrame);
        }

        assertThat((Object) channel.readOutbound()).isNull();
    }

    @Test
    void shouldNotEscapeStompHeadersWhenConnectedCommand() {
        String expectedConnectedFrame = "CONNECTED\n" +
                "colonHeaderName-::colonHeaderValue-:\n" +
                "\n\0";
        FullStompFrame connectedFrame = new DefaultFullStompFrame(StompCommand.CONNECTED);
        connectedFrame.headers()
                .add("colonHeaderName-:", "colonHeaderValue-:");

        assertThat(channel.writeOutbound(connectedFrame)).isTrue();

        try (Buffer stompBuffer = channel.readOutbound()) {
            assertThat(stompBuffer).isNotNull();
            assertThat(stompBuffer.toString(UTF_8)).isEqualTo(expectedConnectedFrame);
        }

        assertThat((Object) channel.readOutbound()).isNull();
    }
}
