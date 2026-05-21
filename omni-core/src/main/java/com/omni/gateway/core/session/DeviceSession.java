package com.omni.gateway.core.session;

import io.netty.channel.Channel;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

public class DeviceSession {

    private final Channel channel;
    private final int localPort;
    private volatile String deviceId;
    private volatile String protocolId;
    private volatile Instant connectedAt = Instant.now();
    private volatile Instant lastActiveAt = Instant.now();
    private final AtomicInteger downlinkSerial = new AtomicInteger(0);

    public DeviceSession(Channel channel, int localPort) {
        this.channel = channel;
        this.localPort = localPort;
    }

    public Channel getChannel() {
        return channel;
    }

    public String getChannelId() {
        return channel.id().asShortText();
    }

    public int getLocalPort() {
        return localPort;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getProtocolId() {
        return protocolId;
    }

    public void setProtocolId(String protocolId) {
        this.protocolId = protocolId;
    }

    public Instant getConnectedAt() {
        return connectedAt;
    }

    public Instant getLastActiveAt() {
        return lastActiveAt;
    }

    public void touch() {
        lastActiveAt = Instant.now();
    }

    public int nextDownlinkSerial() {
        return downlinkSerial.incrementAndGet();
    }

    public boolean isActive() {
        return channel.isActive();
    }
}
