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
