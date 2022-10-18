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

import static java.util.Objects.requireNonNull;

/**
 * Default implementation of the {@link HeadersStompFrame}.
 */
public class DefaultHeadersStompFrame extends DefaultStompFrame implements HeadersStompFrame {

    protected final StompCommand command;
    protected final StompHeaders headers;

    public DefaultHeadersStompFrame(StompCommand command) {
        this(command, null);
    }

    public DefaultHeadersStompFrame(StompCommand command, StompHeaders headers) {
        this.command = requireNonNull(command, "command");
        this.headers = headers == null ? new DefaultStompHeaders() : headers;
    }

    @Override
    public StompCommand command() {
        return command;
    }

    @Override
    public StompHeaders headers() {
        return headers;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        DefaultHeadersStompFrame that = (DefaultHeadersStompFrame) obj;
        if (command != that.command) {
            return false;
        }

        return super.equals(obj) && headers.equals(that.headers);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + command.hashCode();
        result = 31 * result + headers.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "DefaultHeadersStompFrame(decoderResult=" + decoderResult() +
                ", command=" + command +
                ", headers=" + headers +
                ')';
    }
}
