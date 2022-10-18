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

import io.netty5.channel.ChannelHandler;
import io.netty5.handler.codec.TooLongFrameException;
import io.netty5.buffer.BufferAllocator;
import io.netty5.buffer.CompositeBuffer;
import io.netty5.channel.ChannelPipeline;
import io.netty5.handler.codec.MessageAggregator;

/**
 * A {@link ChannelHandler} that aggregates an {@link HeadersStompFrame}
 * and its following {@link ContentStompFrame}s into a single {@link FullStompFrame}.
 * It is useful when you don't want to take care of STOMP frames whose content is 'chunked'.  Insert this
 * handler after {@link StompFrameDecoder} in the {@link ChannelPipeline}:
 */
public class StompFrameAggregator<C extends ContentStompFrame<C>>
    extends MessageAggregator<StompFrame, HeadersStompFrame, ContentStompFrame<C>, FullStompFrame> {

    /**
     * Creates a new instance.
     *
     * @param maxContentLength the maximum length of the aggregated content.
     *                         If the length of the aggregated content exceeds this value,
     *                         a {@link TooLongFrameException} will be raised.
     */
    public StompFrameAggregator(int maxContentLength) {
        super(maxContentLength);
    }

    @Override
    protected HeadersStompFrame tryStartMessage(Object obj) {
        return obj instanceof HeadersStompFrame ? (HeadersStompFrame) obj : null;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected ContentStompFrame<C> tryContentMessage(Object obj) {
        return obj instanceof ContentStompFrame ? (ContentStompFrame<C>) obj : null;
    }

    @Override
    protected boolean isLastContentMessage(ContentStompFrame<C> contentFrame) {
        return contentFrame instanceof LastContentStompFrame;
    }

    @Override
    protected boolean isAggregated(Object obj) {
        return obj instanceof FullStompFrame;
    }

    @Override
    protected int lengthForContent(ContentStompFrame<C> contentFrame) {
        return contentFrame.payload().readableBytes();
    }

    @Override
    protected int lengthForAggregation(FullStompFrame fullStompFrame) {
        return fullStompFrame.payload().readableBytes();
    }

    @Override
    protected boolean isContentLengthInvalid(HeadersStompFrame headersFrame, int maxContentLength) {
        return headersFrame.headers().getLong(StompHeaders.CONTENT_LENGTH, -1L) > maxContentLength;
    }

    @Override
    protected Object newContinueResponse(HeadersStompFrame headersFrame, int i, ChannelPipeline channelPipeline) {
        return null;
    }

    @Override
    protected boolean closeAfterContinueResponse(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected boolean ignoreContentAfterContinueResponse(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    protected FullStompFrame beginAggregation(BufferAllocator allocator, HeadersStompFrame headersFrame) {
        assert !(headersFrame instanceof FullStompFrame);
        FullStompFrame fullFrame = new DefaultFullStompFrame(headersFrame.command(), allocator.compose(), headersFrame.headers());
        if (headersFrame.decoderResult().isFailure()) {
            fullFrame.setDecoderResult(headersFrame.decoderResult());
        }

        return fullFrame;
    }

    @Override
    protected void aggregate(BufferAllocator bufferAllocator, FullStompFrame fullStompFrame, ContentStompFrame<C> contentFrame) {
        final CompositeBuffer payload = (CompositeBuffer) fullStompFrame.payload();
        payload.extendWith(contentFrame.payload().send());
    }
}
