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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultStompHeadersTest {
    @Test
    void shouldHaveCaseSensitiveHeaders() {
        var headers = new DefaultStompHeaders();
        String name = "header-name";
        String value = "header-value";
        headers.add(name, value);

        assertThat(headers.get("Header-Name")).isNull();
        assertThat(headers.get(name)).isEqualTo(value);
    }
}
