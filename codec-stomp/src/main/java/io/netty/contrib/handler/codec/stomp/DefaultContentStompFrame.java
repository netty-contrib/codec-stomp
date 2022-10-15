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
import io.netty5.util.Send;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

/**
 * Default implementation of the {@link ContentStompFrame}.
 */
public class DefaultContentStompFrame extends DefaultStompFrame implements ContentStompFrame<DefaultContentStompFrame> {

    private final Buffer payload;

    public DefaultContentStompFrame(Buffer payload) {
        this.payload = requireNonNull(payload, "payload");
    }

    @Override
    public Buffer payload() {
        return payload;
    }

    @Override
    public DefaultContentStompFrame copy() {
        return new DefaultContentStompFrame(payload.copy());
    }

    @Override
    public Send<DefaultContentStompFrame> send() {
        return payload.send().map(DefaultContentStompFrame.class, DefaultContentStompFrame::new);
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
    public DefaultContentStompFrame touch(Object hint) {
        payload.touch(hint);
        return this;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        DefaultContentStompFrame that = (DefaultContentStompFrame) obj;
        return super.equals(obj) && payload.equals(that.payload);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + payload.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "DefaultContentStompFrame(decoderResult=" + decoderResult() +
                ", payload=" + payload.toString(UTF_8) +
                ')';
    }
}
