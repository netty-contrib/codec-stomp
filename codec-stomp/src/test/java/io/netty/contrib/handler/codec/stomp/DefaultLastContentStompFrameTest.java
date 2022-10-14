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
