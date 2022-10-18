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
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.jupiter.api.Test;

import static io.netty5.buffer.DefaultBufferAllocators.preferredAllocator;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

class DefaultLastContentStompFrameTest {

    @Test
    void shouldReturnNewInstanceWhenCopyInvoked() {
        Buffer buffer = preferredAllocator().allocate(4).writeCharSequence("test", US_ASCII);
        try (var originContentFrame = new DefaultLastContentStompFrame(buffer);
             var copyContentFrame = originContentFrame.copy()) {
            assertThat(originContentFrame)
                    .isEqualTo(copyContentFrame)
                    .isNotSameAs(copyContentFrame);

            copyContentFrame.payload().setByte(0, (byte) 'T');
            assertThat(originContentFrame).isNotEqualTo(copyContentFrame);
        }

        assertThat(buffer.isAccessible()).isFalse();
    }

    @Test
    void shouldComplyToStringContract() {
        byte[] payloadSource = "STOMP last content frame body !!!".getBytes(UTF_8);
        try (var contentFrame = new DefaultLastContentStompFrame(preferredAllocator().copyOf(payloadSource))) {
            assertThat(contentFrame).hasToString("DefaultLastContentStompFrame(decoderResult=success" +
                    ", payload=STOMP last content frame body !!!)");
        }
    }

    @Test
    void shouldComplyEqualsAndHashCodeContract() {
        EqualsVerifier.forClass(DefaultLastContentStompFrame.class)
                .withNonnullFields("payload")
                .suppress(Warning.NONFINAL_FIELDS)
                .usingGetClass()
                .verify();
    }
}
