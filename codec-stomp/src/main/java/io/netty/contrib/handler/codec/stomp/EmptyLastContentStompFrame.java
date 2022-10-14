package io.netty.contrib.handler.codec.stomp;

import io.netty5.buffer.Buffer;
import io.netty5.buffer.BufferAllocator;
import io.netty5.handler.codec.DecoderResult;
import io.netty5.util.Send;

import static java.util.Objects.requireNonNull;

/**
 * Empty implementation of the {@link LastContentStompFrame}.
 */
public final class EmptyLastContentStompFrame implements LastContentStompFrame<EmptyLastContentStompFrame> {

    private final BufferAllocator allocator;
    private final Buffer payload;

    public EmptyLastContentStompFrame(BufferAllocator allocator) {
        this.allocator = requireNonNull(allocator, "allocator");
        payload = allocator.allocate(0).makeReadOnly();
    }

    @Override
    public Buffer payload() {
        return payload;
    }

    @Override
    public EmptyLastContentStompFrame copy() {
        return new EmptyLastContentStompFrame(allocator);
    }

    @Override
    public Send<EmptyLastContentStompFrame> send() {
        return Send.sending(EmptyLastContentStompFrame.class, () -> new EmptyLastContentStompFrame(allocator));
    }

    @Override
    public void close() {
        payload.close();
    }

    @Override
    public boolean isAccessible() {
        return payload.isAccessible();
    }

    @Override
    public EmptyLastContentStompFrame touch(Object hint) {
        payload.touch(hint);
        return this;
    }

    @Override
    public DecoderResult decoderResult() {
        return DecoderResult.success();
    }

    @Override
    public void setDecoderResult(DecoderResult decoderResult) {
        throw new UnsupportedOperationException("EmptyLastContentStompFrame is read-only");
    }

    @Override
    public String toString() {
        return "EmptyLastContentStompFrame(decoderResult=success)";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        EmptyLastContentStompFrame that = (EmptyLastContentStompFrame) obj;
        return allocator.equals(that.allocator)
                && payload.equals(that.payload);
    }

    @Override
    public int hashCode() {
        int result = allocator.hashCode();
        result = 31 * result + payload.hashCode();
        return result;
    }
}
