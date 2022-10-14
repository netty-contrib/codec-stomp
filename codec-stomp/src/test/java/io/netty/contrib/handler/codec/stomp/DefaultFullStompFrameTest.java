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
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.netty5.buffer.DefaultBufferAllocators.preferredAllocator;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

class DefaultFullStompFrameTest {

    @Test
    void shouldReturnNewInstanceWhenCopyInvoked() {
        Buffer payload = preferredAllocator().copyOf("test".getBytes(UTF_8));
        FullStompFrame originFrame = new DefaultFullStompFrame(StompCommand.CONNECT, payload);
        assertThat(originFrame.headers()).isEmpty();

        originFrame.headers().set(StompHeaders.HOST, "stomp.github.io");
        FullStompFrame copyFrame = originFrame.copy();

        assertThat(originFrame).isEqualTo(copyFrame).isNotSameAs(copyFrame);
        assertThat(originFrame.payload()).isEqualTo(copyFrame.payload()).isNotSameAs(copyFrame.payload());

        copyFrame.payload().setByte(0, (byte) 'T');
        assertThat(originFrame.payload()).isNotEqualTo(copyFrame.payload());

        String copyHeaderName = "foo";
        String copyHeaderValue = "bar";
        copyFrame.headers().set(copyHeaderName, copyHeaderValue);

        var copyHeaderEntry = Map.<CharSequence, CharSequence>entry(copyHeaderName, copyHeaderValue);
        assertThat(originFrame.headers()).doesNotContain(copyHeaderEntry);
        assertThat(copyFrame.headers()).contains(copyHeaderEntry);
        assertThat(originFrame).isNotEqualTo(copyFrame);

        originFrame.close();
        copyFrame.close();
    }

    @Test
    void shouldComplyToStringContract() {
        byte[] payloadSource = "STOMP full frame body !!!".getBytes(UTF_8);
        try (var fullFrame = new DefaultFullStompFrame(StompCommand.CONNECT, preferredAllocator().copyOf(payloadSource))) {
            fullFrame.headers()
                    .set(StompHeaders.HOST, "stomp.github.io")
                    .set(StompHeaders.ACCEPT_VERSION, "1.1,1.2");

            assertThat(fullFrame)
                    .hasToString("DefaultFullStompFrame(decoderResult=success" +
                            ", command=CONNECT" +
                            ", headers=DefaultStompHeaders[host: stomp.github.io, accept-version: 1.1,1.2]" +
                            ", payload=STOMP full frame body !!!)");
        }
    }

    @Test
    void shouldComplyEqualsAndHashCodeContract() {
        EqualsVerifier.forClass(DefaultFullStompFrame.class)
                .withNonnullFields("command", "headers", "payload")
                .suppress(Warning.NONFINAL_FIELDS)
                .usingGetClass()
                .verify();
    }
}
