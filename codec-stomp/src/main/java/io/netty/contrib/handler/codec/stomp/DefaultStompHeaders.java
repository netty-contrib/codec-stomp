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

import io.netty5.handler.codec.CharSequenceValueConverter;
import io.netty5.handler.codec.DefaultHeaders;
import io.netty5.handler.codec.HeadersUtils;

import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import static io.netty5.util.AsciiString.CASE_INSENSITIVE_HASHER;
import static io.netty5.util.AsciiString.CASE_SENSITIVE_HASHER;

/**
 * Default implementation of the {@link StompHeaders}.
 */
public class DefaultStompHeaders extends DefaultHeaders<CharSequence, CharSequence, StompHeaders> implements StompHeaders {
    public DefaultStompHeaders() {
        super(CASE_SENSITIVE_HASHER, CharSequenceValueConverter.INSTANCE);
    }

    @Override
    public String getAsString(CharSequence name) {
        return HeadersUtils.getAsString(this, name);
    }

    @Override
    public List<String> getAllAsString(CharSequence name) {
        return HeadersUtils.getAllAsString(this, name);
    }

    @Override
    public Iterator<Entry<String, String>> iteratorAsString() {
        return HeadersUtils.iteratorAsString(this);
    }

    @Override
    public boolean contains(CharSequence name, CharSequence value) {
        return contains(name, value, false);
    }

    @Override
    public boolean contains(CharSequence name, CharSequence value, boolean ignoreCase) {
        return contains(name, value,
                ignoreCase ? CASE_INSENSITIVE_HASHER : CASE_SENSITIVE_HASHER);
    }

    @Override
    public DefaultStompHeaders copy() {
        DefaultStompHeaders copy = new DefaultStompHeaders();
        copy.addImpl(this);
        return copy;
    }
}
