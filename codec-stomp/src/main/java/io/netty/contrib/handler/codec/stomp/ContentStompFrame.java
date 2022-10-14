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

import io.netty5.buffer.Buffer;
import io.netty5.channel.ChannelPipeline;
import io.netty5.util.Resource;

/**
 * An STOMP chunk which is used for STOMP chunked transfer-encoding. {@link StompFrameDecoder} generates
 * {@link ContentStompFrame} after {@link HeadersStompFrame} when the content is large or the encoding of
 * the content is 'chunked.  If you prefer not to receive multiple {@link StompFrame}s for a single
 * {@link FullStompFrame}, place {@link StompFrameAggregator} after {@link StompFrameDecoder} in the
 * {@link ChannelPipeline}.
 */
public interface ContentStompFrame<R extends ContentStompFrame<R>> extends StompFrame, Resource<R> {

    /**
     * Returns the {@link Buffer} representing the payload of the STOMP frame.
     */
    Buffer payload();

    /**
     * Create a copy of this STOMP content frame, and return it.
     */
    R copy();

}
