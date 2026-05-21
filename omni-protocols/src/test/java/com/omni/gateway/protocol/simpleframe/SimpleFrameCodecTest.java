package com.omni.gateway.protocol.simpleframe;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.UnpooledByteBufAllocator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimpleFrameCodecTest {

    @Test
    void detectMagic() {
        ByteBuf buf = UnpooledByteBufAllocator.DEFAULT.buffer();
        buf.writeBytes(SimpleFrameConstants.MAGIC);
        assertTrue(SimpleFrameCodec.detect(buf));
        buf.release();
    }

    @Test
    void roundTripAuthFrame() throws Exception {
        SimpleFrameMessage msg = new SimpleFrameMessage();
        msg.setType("auth");
        msg.setDeviceId("device-test-001");
        ByteBuf encoded = SimpleFrameCodec.encodeFrame(UnpooledByteBufAllocator.DEFAULT, msg);
        SimpleFrameMessage decoded = SimpleFrameCodec.decodeFrame(encoded);
        assertNotNull(decoded);
        assertEquals("auth", decoded.getType());
        assertEquals("device-test-001", decoded.getDeviceId());
        encoded.release();
    }

    @Test
    void roundTripTelemetry() throws Exception {
        SimpleFrameMessage msg = new SimpleFrameMessage();
        msg.setType("telemetry");
        msg.setPayload(JsonNodeFactory.instance.objectNode().put("temp", 25));
        ByteBuf encoded = SimpleFrameCodec.encodeFrame(UnpooledByteBufAllocator.DEFAULT, msg);
        SimpleFrameMessage decoded = SimpleFrameCodec.decodeFrame(encoded);
        assertNotNull(decoded);
        assertEquals("telemetry", decoded.getType());
        assertEquals(25, decoded.getPayload().get("temp").asInt());
        encoded.release();
    }
}
