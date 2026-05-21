package com.omni.gateway.core.config;

public class SniffConfig {

    private int maxBytes = 256;
    private int timeoutMs = 5000;
    private int minProbeLength = 2;

    public int getMaxBytes() {
        return maxBytes;
    }

    public void setMaxBytes(int maxBytes) {
        this.maxBytes = maxBytes;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public int getMinProbeLength() {
        return minProbeLength;
    }

    public void setMinProbeLength(int minProbeLength) {
        this.minProbeLength = minProbeLength;
    }
}
