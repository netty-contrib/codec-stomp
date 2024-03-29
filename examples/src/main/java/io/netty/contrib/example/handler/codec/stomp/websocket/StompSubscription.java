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

import io.netty5.channel.Channel;

import static java.util.Objects.requireNonNull;

public final class StompSubscription {

    private final String id;
    private final String destination;
    private final Channel channel;

    public StompSubscription(String id, String destination, Channel channel) {
        this.id = requireNonNull(id, "id");
        this.destination = requireNonNull(destination, "destination");
        this.channel = requireNonNull(channel, "channel");
    }

    public String id() {
        return id;
    }

    public String destination() {
        return destination;
    }

    public Channel channel() {
        return channel;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        StompSubscription that = (StompSubscription) obj;

        if (!id.equals(that.id)) {
            return false;
        }

        if (!destination.equals(that.destination)) {
            return false;
        }

        return channel.equals(that.channel);
    }

    @Override
    public int hashCode() {
        int result = id.hashCode();
        result = 31 * result + destination.hashCode();
        result = 31 * result + channel.hashCode();
        return result;
    }
}
