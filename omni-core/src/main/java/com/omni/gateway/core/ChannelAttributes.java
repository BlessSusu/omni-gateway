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
    /** 协议流量日志用会话 ID（32 位十六进制，无连字符） */
    public static final AttributeKey<String> TRACE_SESSION_ID =
            AttributeKey.valueOf("omni.traceSessionId");

    private ChannelAttributes() {
    }
}
