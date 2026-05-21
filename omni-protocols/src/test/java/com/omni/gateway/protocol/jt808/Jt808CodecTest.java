package com.omni.gateway.protocol.jt808;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.UnpooledByteBufAllocator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class Jt808CodecTest {

    @Test
    void roundTripRegisterFrame() {
        String phone = "13800138000";
        ByteBuf encoded = Jt808Codec.encode(UnpooledByteBufAllocator.DEFAULT, 0x0100, phone, 1, new byte[]{0, 0});
        assertEquals(0x7E, encoded.getByte(0));
        Jt808Message decoded = Jt808Codec.decodeFrame(encoded);
        assertNotNull(decoded);
        assertEquals(0x0100, decoded.getMessageId());
        assertEquals(phone, decoded.getTerminalPhone());
        assertEquals(1, decoded.getSerialNo());
        encoded.release();
    }
}
