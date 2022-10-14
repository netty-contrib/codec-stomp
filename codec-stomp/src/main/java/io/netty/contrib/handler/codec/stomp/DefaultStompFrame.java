package io.netty.contrib.handler.codec.stomp;

import io.netty5.handler.codec.DecoderResult;

import java.util.Objects;

import static java.util.Objects.requireNonNull;

public abstract class DefaultStompFrame implements StompFrame {

    private DecoderResult decoderResult = DecoderResult.success();

    @Override
    public DecoderResult decoderResult() {
        return decoderResult;
    }

    @Override
    public void setDecoderResult(DecoderResult decoderResult) {
        this.decoderResult = requireNonNull(decoderResult, "decoderResult");
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        DefaultStompFrame that = (DefaultStompFrame) obj;
        return Objects.equals(decoderResult, that.decoderResult);
    }

    @Override
    public int hashCode() {
        return decoderResult != null ? decoderResult.hashCode() : 0;
    }
}
