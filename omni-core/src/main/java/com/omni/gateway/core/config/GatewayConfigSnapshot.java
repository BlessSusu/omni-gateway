package com.omni.gateway.core.config;

import java.util.ArrayList;
import java.util.List;

public class GatewayConfigSnapshot {

    private long configVersion = 1;
    private List<PortListenerConfig> listeners = new ArrayList<>();
    private int readerIdleSeconds = 120;
    private boolean kickOldOnReauth = true;
    private SecurityConfig security = new SecurityConfig();
    private int maxGlobalUplinkPending = 5000;
    private int maxPerChannelUplinkPending = 32;

    public long getConfigVersion() {
        return configVersion;
    }

    public void setConfigVersion(long configVersion) {
        this.configVersion = configVersion;
    }

    public List<PortListenerConfig> getListeners() {
        return listeners;
    }

    public void setListeners(List<PortListenerConfig> listeners) {
        this.listeners = listeners;
    }

    public int getReaderIdleSeconds() {
        return readerIdleSeconds;
    }

    public void setReaderIdleSeconds(int readerIdleSeconds) {
        this.readerIdleSeconds = readerIdleSeconds;
    }

    public boolean isKickOldOnReauth() {
        return kickOldOnReauth;
    }

    public void setKickOldOnReauth(boolean kickOldOnReauth) {
        this.kickOldOnReauth = kickOldOnReauth;
    }

    public SecurityConfig getSecurity() {
        return security;
    }

    public void setSecurity(SecurityConfig security) {
        this.security = security;
    }

    public int getMaxGlobalUplinkPending() {
        return maxGlobalUplinkPending;
    }

    public void setMaxGlobalUplinkPending(int maxGlobalUplinkPending) {
        this.maxGlobalUplinkPending = maxGlobalUplinkPending;
    }

    public int getMaxPerChannelUplinkPending() {
        return maxPerChannelUplinkPending;
    }

    public void setMaxPerChannelUplinkPending(int maxPerChannelUplinkPending) {
        this.maxPerChannelUplinkPending = maxPerChannelUplinkPending;
    }

    public PortListenerConfig listenerForPort(int port) {
        return listeners.stream()
                .filter(l -> l.getPort() == port)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No listener for port " + port));
    }
}
