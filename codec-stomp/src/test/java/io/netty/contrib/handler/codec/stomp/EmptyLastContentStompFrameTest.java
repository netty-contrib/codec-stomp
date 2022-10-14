package io.netty.contrib.handler.codec.stomp;

import io.netty5.handler.codec.DecoderResult;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;
import org.junit.jupiter.api.Test;

import static io.netty5.buffer.DefaultBufferAllocators.preferredAllocator;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class EmptyLastContentStompFrameTest {

    @Test
    void shouldReturnReadOnlyPayload() {
        try (var emptyLastContentFrame = new EmptyLastContentStompFrame(preferredAllocator())) {
            assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> {
                emptyLastContentFrame.payload().writeCharSequence("test", UTF_8);
            }).withMessage("This buffer is read-only: Buffer[roff:0, woff:0, cap:0]");
        }
    }

    @Test
    void shouldThrowExceptionWhenSetDecoderResult() {
        try (var emptyLastContentFrame = new EmptyLastContentStompFrame(preferredAllocator())) {
            assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> {
                emptyLastContentFrame.setDecoderResult(DecoderResult.success());
            }).withMessage("EmptyLastContentStompFrame is read-only");
        }
    }

    @Test
    void shouldReturnSuccessDecoderResult() {
        try (var emptyLastContentFrame = new EmptyLastContentStompFrame(preferredAllocator())) {
            assertThat(emptyLastContentFrame.decoderResult()).isEqualTo(DecoderResult.success());
        }
    }

    @Test
    void shouldComplyToStringContract() {
        try (var emptyLastContentFrame = new EmptyLastContentStompFrame(preferredAllocator())) {
            assertThat(emptyLastContentFrame).hasToString("EmptyLastContentStompFrame(decoderResult=success)");
        }
    }

    @Test
    void shouldComplyEqualsAndHashCodeContract() {
        EqualsVerifier.forClass(EmptyLastContentStompFrame.class)
                .withNonnullFields("payload", "allocator")
                .suppress(Warning.NONFINAL_FIELDS)
                .usingGetClass()
                .verify();
    }
}
