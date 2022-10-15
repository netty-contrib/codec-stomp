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

import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultHeadersStompFrameTest {

    @Test
    void shouldCreateNewHeadersWhenNotPassed() {
        var headersFrame = new DefaultHeadersStompFrame(StompCommand.CONNECT);
        assertThat(headersFrame.command()).isSameAs(StompCommand.CONNECT);
        assertThat(headersFrame.headers).isEmpty();
    }

    @Test
    void shouldAcceptHeadersWhenPassed() {
        StompHeaders headers = new DefaultStompHeaders();
        headers.set(StompHeaders.HOST, "stomp.github.io");

        var headersFrame = new DefaultHeadersStompFrame(StompCommand.CONNECT, headers);
        assertThat(headersFrame.command()).isSameAs(StompCommand.CONNECT);
        assertThat(headersFrame.headers()).isSameAs(headers).isEqualTo(headers);
    }

    @Test
    void shouldComplyToStringContract() {
        var headersFrame = new DefaultHeadersStompFrame(StompCommand.CONNECT);
        headersFrame.headers()
                .set(StompHeaders.HOST, "stomp.github.io")
                .set(StompHeaders.ACCEPT_VERSION, "1.1,1.2");
        assertThat(headersFrame).hasToString("DefaultHeadersStompFrame(decoderResult=success" +
                ", command=CONNECT" +
                ", headers=DefaultStompHeaders[host: stomp.github.io, accept-version: 1.1,1.2])");
    }

    @Test
    void shouldComplyEqualsAndHashCodeContract() {
        EqualsVerifier.forClass(DefaultHeadersStompFrame.class)
                .withNonnullFields("command", "headers")
                .suppress(Warning.NONFINAL_FIELDS)
                .usingGetClass()
                .verify();
    }
}
