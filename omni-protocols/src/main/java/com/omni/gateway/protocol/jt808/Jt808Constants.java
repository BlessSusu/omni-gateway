package com.omni.gateway.protocol.jt808;

public final class Jt808Constants {

    public static final String PLUGIN_ID = "jt808";
    public static final byte FLAG = 0x7E;
    public static final byte ESCAPE = 0x7D;
    public static final byte ESCAPE_7E = 0x02;
    public static final byte ESCAPE_7D = 0x01;

    public static final int MSG_TERMINAL_REGISTER = 0x0100;
    public static final int MSG_TERMINAL_AUTH = 0x0102;
    public static final int MSG_TERMINAL_HEARTBEAT = 0x0002;
    public static final int MSG_TERMINAL_COMMON_ACK = 0x0001;
    public static final int MSG_PLATFORM_REGISTER_ACK = 0x8100;
    public static final int MSG_PLATFORM_COMMON_ACK = 0x8001;

    private Jt808Constants() {
    }
}
