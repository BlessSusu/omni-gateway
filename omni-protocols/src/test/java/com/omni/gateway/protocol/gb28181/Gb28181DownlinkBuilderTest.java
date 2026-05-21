package com.omni.gateway.protocol.gb28181;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class Gb28181DownlinkBuilderTest {

    @Test
    void buildInviteContainsSdp() {
        var payload = JsonNodeFactory.instance.objectNode();
        payload.put("streamUrl", "10.0.0.8");
        payload.put("mediaPort", 10002);
        Gb28181DownlinkBuilder.DeviceSessionLike ctx = new Gb28181DownlinkBuilder.DeviceSessionLike() {
            @Override
            public String deviceId() {
                return "34020000002000000001";
            }

            @Override
            public String localAddress() {
                return "127.0.0.1:5060";
            }
        };
        var buf = Gb28181DownlinkBuilder.build(ctx, "InviteStream", payload, "call-invite-1");
        String text = new String(buf.array(), buf.arrayOffset(), buf.readableBytes(), StandardCharsets.UTF_8);
        assertTrue(text.startsWith("INVITE "));
        assertTrue(text.contains("application/sdp"));
        assertTrue(text.contains("10.0.0.8"));
        buf.release();
    }

    @Test
    void buildBroadcastXml() {
        var payload = JsonNodeFactory.instance.objectNode();
        payload.put("streamUrl", "rtsp://10.0.0.1/live/ch1");
        String xml = Gb28181DownlinkBuilder.buildBroadcastXml("34020000002000000001", payload);
        assertTrue(xml.contains("Broadcast"));
        assertTrue(xml.contains("rtsp://10.0.0.1/live/ch1"));
    }
}
