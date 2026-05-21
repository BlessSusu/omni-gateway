package com.omni.gateway.protocol.simpleframe;

public final class SimpleFrameConstants {

    public static final String PLUGIN_ID = "simple-frame";
    public static final byte[] MAGIC = {'O', 'M', 'N', 'I'};
    public static final int HEADER_LEN = 7; // magic4 + length2 + checksum1 at end -> actually frame: magic4 len2 body checksum1

    private SimpleFrameConstants() {
    }
}
