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
package io.netty.contrib.microbenchmarks.stomp;

import io.netty.contrib.handler.codec.stomp.DefaultFullStompFrame;
import io.netty.contrib.handler.codec.stomp.HeadersStompFrame;
import io.netty.contrib.handler.codec.stomp.StompFrameEncoder;
import io.netty5.buffer.Buffer;
import io.netty5.buffer.BufferAllocator;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.microbench.channel.EmbeddedChannelWriteReleaseHandlerContext;
import io.netty5.microbench.util.AbstractMicrobenchmark;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.profile.GCProfiler;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import static io.netty5.buffer.DefaultBufferAllocators.offHeapAllocator;
import static io.netty5.buffer.DefaultBufferAllocators.onHeapAllocator;

@State(Scope.Benchmark)
@Fork(value = 1)
@Threads(1)
@Warmup(iterations = 2)
@Measurement(iterations = 3)
public class StompEncoderBenchmark extends AbstractMicrobenchmark {

    private StompFrameEncoder stompEncoder;
    private Supplier<Buffer> contentSupplier;
    private HeadersStompFrame headersFrame;
    private ChannelHandlerContext context;

    @Param({"true", "false"})
    public boolean offHeapAllocator;

    @Param
    public ExampleStompHeadersSubframe.HeadersType headersType;

    @Param({"0", "10", "100", "1000", "3000"})
    public int contentLength;

    @Setup(Level.Trial)
    public void setup() {
        byte[] bytes = new byte[contentLength];
        ThreadLocalRandom.current().nextBytes(bytes);
        BufferAllocator allocator = offHeapAllocator ? offHeapAllocator() : onHeapAllocator();
        contentSupplier = allocator.constBufferSupplier(bytes);

        headersFrame = ExampleStompHeadersSubframe.EXAMPLES.get(headersType);

        stompEncoder = new StompFrameEncoder();
        context = new EmbeddedChannelWriteReleaseHandlerContext(allocator, stompEncoder) {
            @Override
            protected void handleException(Throwable t) {
                handleUnexpectedException(t);
            }
        };
    }

    @TearDown(Level.Trial)
    public void teardown() {
        contentSupplier = null;
        context.close();
    }

    @Benchmark
    public void writeStompFrame() {
        context.executor().execute(() ->
                stompEncoder.write(context, new DefaultFullStompFrame(headersFrame.command(),
                        contentSupplier.get(), headersFrame.headers())
                ).addListener(future -> handleUnexpectedException(future.cause())));
    }

    @Override
    protected ChainedOptionsBuilder newOptionsBuilder() throws Exception {
        return super.newOptionsBuilder().addProfiler(GCProfiler.class);
    }
}
