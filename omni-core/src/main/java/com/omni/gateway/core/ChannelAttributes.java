package com.omni.gateway.core;

import com.omni.gateway.core.session.DeviceSession;
import io.netty.util.AttributeKey;

public final class ChannelAttributes {

    public static final AttributeKey<DeviceSession> SESSION =
            AttributeKey.valueOf("omni.session");
    public static final AttributeKey<Integer> LOCAL_PORT =
            AttributeKey.valueOf("omni.localPort");
    public static final AttributeKey<String> BOUND_PROTOCOL =
            AttributeKey.valueOf("omni.boundProtocol");
    public static final AttributeKey<Boolean> AUTHENTICATED =
            AttributeKey.valueOf("omni.authenticated");
    public static final AttributeKey<Integer> UPLINK_PENDING =
            AttributeKey.valueOf("omni.uplinkPending");

    private ChannelAttributes() {
    }
}
