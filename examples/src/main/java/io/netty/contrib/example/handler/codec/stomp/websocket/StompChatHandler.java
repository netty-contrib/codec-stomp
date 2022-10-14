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
package io.netty.contrib.example.handler.codec.stomp.websocket;

import io.netty.contrib.handler.codec.stomp.DefaultFullStompFrame;
import io.netty.contrib.handler.codec.stomp.FullStompFrame;
import io.netty.contrib.handler.codec.stomp.StompCommand;
import io.netty5.buffer.Buffer;
import io.netty5.channel.ChannelFutureListeners;
import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.SimpleChannelInboundHandler;
import io.netty5.handler.codec.DecoderResult;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static io.netty.contrib.handler.codec.stomp.StompHeaders.*;
import static java.nio.charset.StandardCharsets.UTF_8;

public class StompChatHandler extends SimpleChannelInboundHandler<FullStompFrame> {

    private final ConcurrentMap<String, Set<StompSubscription>> chatDestinations = new ConcurrentHashMap<String, Set<StompSubscription>>();

    @Override
    public boolean isSharable() {
        return true;
    }

    @Override
    protected void messageReceived(ChannelHandlerContext ctx, FullStompFrame inboundFrame) throws Exception {
        DecoderResult decoderResult = inboundFrame.decoderResult();
        if (decoderResult.isFailure()) {
            sendErrorFrame("rejected frame", decoderResult.toString(), ctx);
            return;
        }

        switch (inboundFrame.command()) {
            case STOMP:
            case CONNECT:
                onConnect(ctx, inboundFrame);
                break;
            case SUBSCRIBE:
                onSubscribe(ctx, inboundFrame);
                break;
            case SEND:
                onSend(ctx, inboundFrame);
                break;
            case UNSUBSCRIBE:
                onUnsubscribe(ctx, inboundFrame);
                break;
            case DISCONNECT:
                onDisconnect(ctx, inboundFrame);
                break;
            default:
                sendErrorFrame("unsupported command",
                        "Received unsupported command " + inboundFrame.command(), ctx);
        }
    }

    private void onSubscribe(ChannelHandlerContext ctx, FullStompFrame inboundFrame) {
        String destination = inboundFrame.headers().getAsString(DESTINATION);
        String subscriptionId = inboundFrame.headers().getAsString(ID);

        if (destination == null || subscriptionId == null) {
            sendErrorFrame("missed header", "Required 'destination' or 'id' header missed", ctx);
            return;
        }

        Set<StompSubscription> subscriptions = chatDestinations.get(destination);
        if (subscriptions == null) {
            subscriptions = new HashSet<>();
            Set<StompSubscription> previousSubscriptions = chatDestinations.putIfAbsent(destination, subscriptions);
            if (previousSubscriptions != null) {
                subscriptions = previousSubscriptions;
            }
        }

        final StompSubscription subscription = new StompSubscription(subscriptionId, destination, ctx.channel());
        if (subscriptions.contains(subscription)) {
            sendErrorFrame("duplicate subscription",
                    "Received duplicate subscription id=" + subscriptionId, ctx);
            return;
        }

        subscriptions.add(subscription);
        ctx.channel().closeFuture()
                .addListener(future -> chatDestinations.get(subscription.destination()).remove(subscription));

        String receiptId = inboundFrame.headers().getAsString(RECEIPT);
        if (receiptId != null) {
            FullStompFrame receiptFrame = new DefaultFullStompFrame(StompCommand.RECEIPT);
            receiptFrame.headers().set(RECEIPT_ID, receiptId);
            ctx.writeAndFlush(receiptFrame);
        }
    }

    private void onSend(ChannelHandlerContext ctx, FullStompFrame inboundFrame) {
        String destination = inboundFrame.headers().getAsString(DESTINATION);
        if (destination == null) {
            sendErrorFrame("missed header", "required 'destination' header missed", ctx);
            return;
        }

        Set<StompSubscription> subscriptions = chatDestinations.get(destination);
        for (StompSubscription subscription : subscriptions) {
            subscription.channel()
                    .writeAndFlush(transformToMessage(inboundFrame, subscription));
        }
    }

    private void onUnsubscribe(ChannelHandlerContext ctx, FullStompFrame inboundFrame) {
        String subscriptionId = inboundFrame.headers().getAsString(SUBSCRIPTION);
        for (Entry<String, Set<StompSubscription>> entry : chatDestinations.entrySet()) {
            Iterator<StompSubscription> iterator = entry.getValue().iterator();
            while (iterator.hasNext()) {
                StompSubscription subscription = iterator.next();
                if (subscription.id().equals(subscriptionId) && subscription.channel().equals(ctx.channel())) {
                    iterator.remove();
                    return;
                }
            }
        }
    }

    private static void onConnect(ChannelHandlerContext ctx, FullStompFrame inboundFrame) {
        String acceptVersions = inboundFrame.headers().getAsString(ACCEPT_VERSION);
        StompVersion handshakeAcceptVersion = ctx.channel().attr(StompVersion.CHANNEL_ATTRIBUTE_KEY).get();
        if (acceptVersions == null || !acceptVersions.contains(handshakeAcceptVersion.version())) {
            sendErrorFrame("invalid version",
                    "Received invalid version, expected " + handshakeAcceptVersion.version(), ctx);
            return;
        }

        FullStompFrame connectedFrame = new DefaultFullStompFrame(StompCommand.CONNECTED);
        connectedFrame.headers()
                .set(VERSION, handshakeAcceptVersion.version())
                .set(SERVER, "Netty-Server")
                .set(HEART_BEAT, "0,0");
        ctx.writeAndFlush(connectedFrame);
    }

    private static void onDisconnect(ChannelHandlerContext ctx, FullStompFrame inboundFrame) {
        String receiptId = inboundFrame.headers().getAsString(RECEIPT);
        if (receiptId == null) {
            ctx.close();
            return;
        }

        FullStompFrame receiptFrame = new DefaultFullStompFrame(StompCommand.RECEIPT);
        receiptFrame.headers().set(RECEIPT_ID, receiptId);
        ctx.writeAndFlush(receiptFrame).addListener(ctx, ChannelFutureListeners.CLOSE);
    }

    private static void sendErrorFrame(String message, String description, ChannelHandlerContext ctx) {
        final FullStompFrame errorFrame;
        if (description != null) {
            Buffer payload = ctx.bufferAllocator().copyOf(description.getBytes(UTF_8));
            errorFrame = new DefaultFullStompFrame(StompCommand.ERROR, payload);
        } else {
            errorFrame = new DefaultFullStompFrame(StompCommand.ERROR);
        }

        errorFrame.headers().set(MESSAGE, message);
        ctx.writeAndFlush(errorFrame).addListener(ctx, ChannelFutureListeners.CLOSE);
    }

    private static FullStompFrame transformToMessage(FullStompFrame sendFrame, StompSubscription subscription) {
        FullStompFrame messageFrame = new DefaultFullStompFrame(StompCommand.MESSAGE, sendFrame.payload().copy(true));
        String id = UUID.randomUUID().toString();
        messageFrame.headers()
                .set(MESSAGE_ID, id)
                .set(SUBSCRIPTION, subscription.id())
                .set(CONTENT_LENGTH, Integer.toString(messageFrame.payload().readableBytes()));

        CharSequence contentType = sendFrame.headers().get(CONTENT_TYPE);
        if (contentType != null) {
            messageFrame.headers().set(CONTENT_TYPE, contentType);
        }

        return messageFrame;
    }

    @Override
    public void channelExceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        try {
            sendErrorFrame(cause.getMessage(), "Exception occurs, connection will be closed", ctx);
        } finally {
            cause.printStackTrace();
        }
    }
}
