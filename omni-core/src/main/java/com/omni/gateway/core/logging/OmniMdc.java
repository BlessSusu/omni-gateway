package com.omni.gateway.core.logging;

import org.slf4j.MDC;

public final class OmniMdc {

    public static final String DEVICE_ID = "deviceId";
    public static final String PROTOCOL = "protocol";
    public static final String CHANNEL_ID = "channelId";
    public static final String EVENT = "event";

    private OmniMdc() {
    }

    public static void bindDevice(String deviceId, String protocol, String channelId) {
        if (deviceId != null) {
            MDC.put(DEVICE_ID, deviceId);
        }
        if (protocol != null) {
            MDC.put(PROTOCOL, protocol);
        }
        if (channelId != null) {
            MDC.put(CHANNEL_ID, channelId);
        }
    }

    public static void event(String name) {
        MDC.put(EVENT, name);
    }

    public static void clear() {
        MDC.remove(DEVICE_ID);
        MDC.remove(PROTOCOL);
        MDC.remove(CHANNEL_ID);
        MDC.remove(EVENT);
    }
}
