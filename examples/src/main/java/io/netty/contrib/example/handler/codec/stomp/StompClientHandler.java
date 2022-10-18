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
package io.netty.contrib.example.handler.codec.stomp;

import io.netty5.channel.ChannelHandlerContext;
import io.netty5.channel.SimpleChannelInboundHandler;
import io.netty.contrib.handler.codec.stomp.DefaultFullStompFrame;
import io.netty.contrib.handler.codec.stomp.StompCommand;
import io.netty.contrib.handler.codec.stomp.FullStompFrame;
import io.netty.contrib.handler.codec.stomp.StompHeaders;

/**
 * STOMP client inbound handler implementation, which just passes received messages to listener
 */
public class StompClientHandler extends SimpleChannelInboundHandler<FullStompFrame> {

    private enum ClientState {
        AUTHENTICATING,
        AUTHENTICATED,
        SUBSCRIBED,
        DISCONNECTING
    }

    private ClientState state;

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        state = ClientState.AUTHENTICATING;
        FullStompFrame connFrame = new DefaultFullStompFrame(StompCommand.CONNECT);
        connFrame.headers().set(StompHeaders.ACCEPT_VERSION, "1.2")
                .set(StompHeaders.HOST, StompClient.HOST)
                .set(StompHeaders.LOGIN, StompClient.LOGIN)
                .set(StompHeaders.PASSCODE, StompClient.PASSCODE);

        ctx.writeAndFlush(connFrame);
    }

    @Override
    protected void messageReceived(ChannelHandlerContext ctx, FullStompFrame frame) throws Exception {
        String subscrReceiptId = "001";
        String disconReceiptId = "002";
        switch (frame.command()) {
            case CONNECTED:
                FullStompFrame subscribeFrame = new DefaultFullStompFrame(StompCommand.SUBSCRIBE);
                subscribeFrame.headers()
                        .set(StompHeaders.DESTINATION, StompClient.TOPIC)
                        .set(StompHeaders.RECEIPT, subscrReceiptId)
                        .set(StompHeaders.ID, "1");
                System.out.println("connected, sending subscribe frame: " + subscribeFrame);
                state = ClientState.AUTHENTICATED;
                ctx.writeAndFlush(subscribeFrame);
                break;
            case RECEIPT:
                String receiptHeader = frame.headers().getAsString(StompHeaders.RECEIPT_ID);
                if (state == ClientState.AUTHENTICATED && receiptHeader.equals(subscrReceiptId)) {
                    FullStompFrame msgFrame = new DefaultFullStompFrame(StompCommand.SEND,
                            ctx.bufferAllocator().allocate(128));
                    msgFrame.headers().set(StompHeaders.DESTINATION, StompClient.TOPIC);
                    msgFrame.payload().writeBytes("some payload".getBytes());
                    System.out.println("subscribed, sending message frame: " + msgFrame);
                    state = ClientState.SUBSCRIBED;
                    ctx.writeAndFlush(msgFrame);
                } else if (state == ClientState.DISCONNECTING && receiptHeader.equals(disconReceiptId)) {
                    System.out.println("disconnected");
                    ctx.close();
                } else {
                    throw new IllegalStateException("received: " + frame + ", while internal state is " + state);
                }
                break;
            case MESSAGE:
                if (state == ClientState.SUBSCRIBED) {
                    System.out.println("received frame: " + frame);
                    FullStompFrame disconnectFrame = new DefaultFullStompFrame(StompCommand.DISCONNECT);
                    disconnectFrame.headers().set(StompHeaders.RECEIPT, disconReceiptId);
                    System.out.println("sending disconnect frame: " + disconnectFrame);
                    state = ClientState.DISCONNECTING;
                    ctx.writeAndFlush(disconnectFrame);
                }
                break;
            default:
                break;
        }
    }

    @Override
    public void channelExceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}
