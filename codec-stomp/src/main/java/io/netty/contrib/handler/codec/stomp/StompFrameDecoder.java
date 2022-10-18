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
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.handler.codec.ByteToMessageDecoder;
import io.netty5.handler.codec.DecoderException;
import io.netty5.handler.codec.DecoderResult;
import io.netty5.handler.codec.TooLongFrameException;
import io.netty5.util.ByteProcessor;
import io.netty5.util.internal.AppendableCharSequence;
import io.netty5.util.internal.ObjectUtil;
import io.netty5.util.internal.StringUtil;

import java.util.Objects;

/**
 * Decodes {@link Buffer}s into {@link HeadersStompFrame}s and {@link ContentStompFrame}s.
 *
 * <h3>Parameters to control memory consumption: </h3>
 * {@code maxLineLength} the maximum length of line - restricts length of command and header lines If the length of the
 * initial line exceeds this value, a {@link TooLongFrameException} will be raised.
 * <br>
 * {@code maxChunkSize} The maximum length of the content or each chunk.  If the content length (or the length of each
 * chunk) exceeds this value, the content or chunk ill be split into multiple {@link ContentStompFrame}s whose length
 * is {@code maxChunkSize} at maximum.
 *
 * <h3>Chunked Content</h3>
 * <p>
 * If the content of a stomp message is greater than {@code maxChunkSize} the transfer encoding of the HTTP message is
 * 'chunked', this decoder generates multiple {@link ContentStompFrame} instances to avoid excessive memory
 * consumption. Note, that every message, even with no content decodes with {@link LastContentStompFrame} at the end
 * to simplify upstream message parsing.
 */
public class StompFrameDecoder extends ByteToMessageDecoder {

    private static final int DEFAULT_CHUNK_SIZE = 8132;
    private static final int DEFAULT_MAX_LINE_LENGTH = 1024;

    private enum State {
        SKIP_CONTROL_CHARACTERS,
        READ_HEADERS,
        READ_CONTENT,
        FINALIZE_FRAME_READ,
        BAD_FRAME,
        INVALID_CHUNK
    }

    private State state = State.SKIP_CONTROL_CHARACTERS;
    private final Utf8LineParser commandParser;
    private final HeaderParser headerParser;
    private final int maxChunkSize;
    private HeadersStompFrame startHeadersFrame;
    private int alreadyReadChunkSize;
    private LastContentStompFrame<?> lastContentFrame;
    private long contentLength = -1;

    public StompFrameDecoder() {
        this(DEFAULT_MAX_LINE_LENGTH, DEFAULT_CHUNK_SIZE);
    }

    public StompFrameDecoder(boolean validateHeaders) {
        this(DEFAULT_MAX_LINE_LENGTH, DEFAULT_CHUNK_SIZE, validateHeaders);
    }

    public StompFrameDecoder(int maxLineLength, int maxChunkSize) {
        this(maxLineLength, maxChunkSize, false);
    }

    public StompFrameDecoder(int maxLineLength, int maxChunkSize, boolean validateHeaders) {
        ObjectUtil.checkPositive(maxLineLength, "maxLineLength");
        ObjectUtil.checkPositive(maxChunkSize, "maxChunkSize");
        this.maxChunkSize = maxChunkSize;
        commandParser = new Utf8LineParser(new AppendableCharSequence(16), maxLineLength);
        headerParser = new HeaderParser(new AppendableCharSequence(128), maxLineLength, validateHeaders);
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, Buffer in) throws Exception {
        switch (state) {
            case SKIP_CONTROL_CHARACTERS:
                if (!skipControlCharacters(in)) {
                    return;
                }

                state = State.READ_HEADERS;
            case READ_HEADERS:
                StompCommand command = StompCommand.UNKNOWN;
                try {
                    if (startHeadersFrame == null) {
                        command = readCommand(in);
                        if (command == null) {
                            return;
                        }

                        startHeadersFrame = new DefaultHeadersStompFrame(command);
                    }

                    State nextState = readHeaders(in, startHeadersFrame);
                    if (nextState == null) {
                        return;
                    }

                    state = nextState;
                    HeadersStompFrame headersFrame = startHeadersFrame;
                    startHeadersFrame = null;
                    ctx.fireChannelRead(headersFrame);
                } catch (Exception e) {
                    if (startHeadersFrame == null) {
                        startHeadersFrame = new DefaultHeadersStompFrame(command);
                    }

                    startHeadersFrame.setDecoderResult(DecoderResult.failure(e));
                    HeadersStompFrame headersFrame = startHeadersFrame;
                    startHeadersFrame = null;
                    ctx.fireChannelRead(headersFrame);
                    state = State.BAD_FRAME;
                    return;
                }
                break;
            case BAD_FRAME:
                in.skipReadableBytes(actualReadableBytes());
                return;
        }

        try {
            switch (state) {
                case READ_CONTENT:
                    int toRead = in.readableBytes();
                    if (toRead == 0) {
                        return;
                    }

                    if (toRead > maxChunkSize) {
                        toRead = maxChunkSize;
                    }

                    if (contentLength >= 0) {
                        int remainingLength = (int) (contentLength - alreadyReadChunkSize);
                        if (toRead > remainingLength) {
                            toRead = remainingLength;
                        }

                        Buffer chunkBuffer = in.readSplit(toRead);
                        if ((alreadyReadChunkSize += toRead) >= contentLength) {
                            lastContentFrame = new DefaultLastContentStompFrame(chunkBuffer);
                            state = State.FINALIZE_FRAME_READ;
                        } else {
                            ctx.fireChannelRead(new DefaultContentStompFrame(chunkBuffer));
                            return;
                        }
                    } else {
                        int beforeNull = in.bytesBefore(StompConstants.NUL);
                        if (beforeNull != 0) {
                            if (beforeNull > 0) {
                                toRead = beforeNull;
                            } else {
                                toRead = in.readableBytes();
                            }

                            Buffer chunkBuffer = in.readSplit(toRead);
                            alreadyReadChunkSize += toRead;
                            if (beforeNull < 0) {
                                ctx.fireChannelRead(new DefaultContentStompFrame(chunkBuffer));
                                return;
                            }

                            lastContentFrame = new DefaultLastContentStompFrame(chunkBuffer);
                        }
                        state = State.FINALIZE_FRAME_READ;
                    }
                    // Fall through.
                case FINALIZE_FRAME_READ:
                    if (!skipNullCharacter(in)) {
                        return;
                    }
                    if (lastContentFrame == null) {
                        lastContentFrame = new EmptyLastContentStompFrame(ctx.bufferAllocator());
                    }
                    ctx.fireChannelRead(lastContentFrame);
                    resetDecoder();
            }
        } catch (Exception e) {
            if (lastContentFrame != null) {
                lastContentFrame.close();
                lastContentFrame = null;
            }

            DefaultLastContentStompFrame errorContent = new DefaultLastContentStompFrame(ctx.bufferAllocator().allocate(0));
            errorContent.setDecoderResult(DecoderResult.failure(e));
            ctx.fireChannelRead(errorContent);
            state = State.BAD_FRAME;
        }
    }

    private StompCommand readCommand(Buffer in) {
        CharSequence commandSequence = commandParser.parse(in);
        if (commandSequence == null) {
            return null;
        }

        try {
            StompCommand command = StompCommand.valueOf(commandSequence.toString());
            if (command == StompCommand.UNKNOWN) {
                throw new DecoderException("Cannot to parse UNKNOWN command");
            }

            return command;
        } catch (IllegalArgumentException e) {
            throw new DecoderException("Cannot to parse command: " + commandSequence);
        }
    }

    private State readHeaders(Buffer buffer, HeadersStompFrame headersFrame) {
        if (headerParser.parseHeaders(headersFrame, buffer)) {
            StompHeaders headers = headersFrame.headers();
            if (headers.contains(StompHeaders.CONTENT_LENGTH)) {
                contentLength = getContentLength(headers);
                if (contentLength == 0) {
                    return State.FINALIZE_FRAME_READ;
                }
            }
            return State.READ_CONTENT;
        }
        return null;
    }

    private static long getContentLength(StompHeaders headers) {
        long contentLength = headers.getLong(StompHeaders.CONTENT_LENGTH, 0L);
        if (contentLength < 0) {
            throw new DecoderException(StompHeaders.CONTENT_LENGTH + " must be non-negative");
        }
        return contentLength;
    }

    private static boolean skipNullCharacter(Buffer buffer) {
        if (buffer.readableBytes() < 1) {
            return false;
        }

        byte b = buffer.readByte();
        if (b != StompConstants.NUL) {
            throw new IllegalStateException("Unexpected byte in buffer " + b + " while expecting NULL byte");
        }

        return true;
    }

    private static boolean skipControlCharacters(Buffer buffer) {
        while (buffer.readableBytes() > 0) {
            byte b = buffer.readByte();
            if (b != StompConstants.CR && b != StompConstants.LF) {
                buffer.readerOffset(buffer.readerOffset() - 1);
                return true;
            }
        }

        return false;
    }

    private void resetDecoder() {
        state = State.SKIP_CONTROL_CHARACTERS;
        startHeadersFrame = null;
        contentLength = -1;
        alreadyReadChunkSize = 0;
        lastContentFrame = null;
    }

    private static class Utf8LineParser implements ByteProcessor {

        private final AppendableCharSequence charSeq;
        private final int maxLineLength;

        private int lineLength;
        private char interim;
        private boolean nextRead;

        Utf8LineParser(AppendableCharSequence charSeq, int maxLineLength) {
            this.charSeq = Objects.requireNonNull(charSeq, "charSeq");
            this.maxLineLength = maxLineLength;
        }

        AppendableCharSequence parse(Buffer buffer) {
            reset();
            int count = buffer.openCursor().process(this);
            if (count == -1) {
                return null;
            }

            int readerOffset = buffer.readerOffset() + count + 1;
            buffer.readerOffset(readerOffset);
            return charSeq;
        }

        AppendableCharSequence charSequence() {
            return charSeq;
        }

        @Override
        public boolean process(byte nextByte) {
            if (nextByte == StompConstants.CR) {
                ++lineLength;
                return true;
            }

            if (nextByte == StompConstants.LF) {
                return false;
            }

            if (++lineLength > maxLineLength) {
                throw new TooLongFrameException("An STOMP line is larger than " + maxLineLength + " bytes.");
            }

            // 1 byte   -   0xxxxxxx                    -  7 bits
            // 2 byte   -   110xxxxx 10xxxxxx           -  11 bits
            // 3 byte   -   1110xxxx 10xxxxxx 10xxxxxx  -  16 bits
            if (nextRead) {
                interim |= (nextByte & 0x3F) << 6;
                nextRead = false;
            } else if (interim != 0) { // flush 2 or 3 byte
                appendTo(charSeq, (char) (interim | nextByte & 0x3F));
                interim = 0;
            } else if (nextByte >= 0) { // INITIAL BRANCH
                // The first 128 characters (US-ASCII) need one byte.
                appendTo(charSeq, (char) nextByte);
            } else if ((nextByte & 0xE0) == 0xC0) {
                // The next 1920 characters need two bytes and we can define
                // a first byte by mask 110xxxxx.
                interim = (char) ((nextByte & 0x1F) << 6);
            } else {
                // The rest of characters need three bytes.
                interim = (char) ((nextByte & 0x0F) << 12);
                nextRead = true;
            }

            return true;
        }

        protected void appendTo(AppendableCharSequence charSeq, char chr) {
            charSeq.append(chr);
        }

        protected void reset() {
            charSeq.reset();
            lineLength = 0;
            interim = 0;
            nextRead = false;
        }
    }

    private static final class HeaderParser extends Utf8LineParser {

        private final boolean validateHeaders;

        private String name;
        private boolean valid;
        private boolean shouldUnescape;
        private boolean unescapeInProgress;

        HeaderParser(AppendableCharSequence charSeq, int maxLineLength, boolean validateHeaders) {
            super(charSeq, maxLineLength);
            this.validateHeaders = validateHeaders;
        }

        boolean parseHeaders(HeadersStompFrame headersFrame, Buffer buffer) {
            shouldUnescape = shouldUnescape(headersFrame.command());
            for (;;) {
                AppendableCharSequence value = parse(buffer);
                if (value == null) {
                    return false;
                }
                if (name == null && value.length() == 0) {
                    return true;
                }
                if (valid) {
                    headersFrame.headers().add(name, value.toString());
                } else if (validateHeaders) {
                    if (StringUtil.isNullOrEmpty(name)) {
                        throw new IllegalArgumentException("received an invalid header line '" + value + '\'');
                    }
                    String line = name + ':' + value;
                    throw new IllegalArgumentException("a header value or name contains a prohibited character ':'"
                            + ", " + line);
                }
            }
        }

        @Override
        public boolean process(byte nextByte) {
            if (nextByte == StompConstants.COLON) {
                if (name == null) {
                    AppendableCharSequence charSeq = charSequence();
                    if (charSeq.length() != 0) {
                        name = charSeq.substring(0, charSeq.length());
                        charSeq.reset();
                        valid = true;
                        return true;
                    } else {
                        name = StringUtil.EMPTY_STRING;
                    }
                } else {
                    valid = false;
                }
            }

            return super.process(nextByte);
        }

        @Override
        protected void appendTo(AppendableCharSequence charSeq, char chr) {
            if (!shouldUnescape) {
                super.appendTo(charSeq, chr);
                return;
            }

            if (chr == '\\') {
                if (unescapeInProgress) {
                    super.appendTo(charSeq, chr);
                    unescapeInProgress = false;
                } else {
                    unescapeInProgress = true;
                }
                return;
            }

            if (unescapeInProgress) {
                if (chr == 'c') {
                    charSeq.append(':');
                } else if (chr == 'r') {
                    charSeq.append('\r');
                } else if (chr == 'n') {
                    charSeq.append('\n');
                } else {
                    charSeq.append('\\').append(chr);
                    throw new IllegalArgumentException("received an invalid escape header sequence '" + charSeq + '\'');
                }

                unescapeInProgress = false;
                return;
            }

            super.appendTo(charSeq, chr);
        }

        @Override
        protected void reset() {
            name = null;
            valid = false;
            unescapeInProgress = false;
            super.reset();
        }
    }

    private static boolean shouldUnescape(StompCommand command) {
        return command != StompCommand.CONNECT && command != StompCommand.CONNECTED;
    }
}
