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
package io.netty.contrib.handler.codec.stomp;

import io.netty5.buffer.Buffer;
import io.netty5.channel.embedded.EmbeddedChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

class StompCommandDecodeTest {

    private EmbeddedChannel channel;

    @BeforeEach
    void setUp() {
        channel = new EmbeddedChannel(new StompFrameDecoder(true));
    }

    @AfterEach
    void tearDown() {
        assertThat(channel.finish()).isFalse();
    }

    @ParameterizedTest(name = "{index}: testDecodeCommand({0}) = {1}")
    @MethodSource("stompCommands")
    void testDecodeCommand(String rawCommand, StompCommand expectedCommand, Boolean valid) {
        byte[] frameSource = String.format("%s\n\n\0", rawCommand).getBytes(UTF_8);
        Buffer incoming = channel.bufferAllocator().copyOf(frameSource);
        assertThat(channel.writeInbound(incoming)).isTrue();

        HeadersStompFrame headersFrame = channel.readInbound();
        assertThat(headersFrame).isNotNull()
                .extracting(HeadersStompFrame::command)
                .isEqualTo(expectedCommand);

        if (valid) {
            assertThat(headersFrame.decoderResult().isSuccess()).isTrue();
            try (ContentStompFrame<?> lastContent = channel.readInbound()) {
                assertThat(lastContent).isEqualTo(new EmptyLastContentStompFrame(channel.bufferAllocator()));
            }
        } else {
            assertThat(headersFrame.decoderResult().isFailure()).isTrue();
            assertThat((Object) channel.readInbound()).isNull();
        }
    }

    static Collection<Object[]> stompCommands() {
        Stream<Object[]> valid = Arrays.stream(StompCommand.values())
                .filter(command -> command != StompCommand.UNKNOWN)
                .map(command -> new Object[]{command.name(), command, true});

        Stream<Object[]> invalid = Stream.of(
                new Object[]{"INVALID", StompCommand.UNKNOWN, false},
                new Object[]{"UNKNOWN", StompCommand.UNKNOWN, false},
                new Object[]{"connect", StompCommand.UNKNOWN, false}
        );

        return Stream.concat(valid, invalid).collect(toList());
    }
}
