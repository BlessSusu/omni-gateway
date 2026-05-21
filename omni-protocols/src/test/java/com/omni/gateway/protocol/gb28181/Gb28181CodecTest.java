package com.omni.gateway.protocol.gb28181;

import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Gb28181CodecTest {

    private static final String REGISTER = """
            REGISTER sip:34020000002000000001@3402000000 SIP/2.0\r
            Via: SIP/2.0/TCP 192.168.1.10:5060;branch=z9hG4bK-1\r
            From: <sip:34020000002000000001@3402000000>;tag=abc\r
            To: <sip:34020000002000000001@3402000000>\r
            Call-ID: call-1\r
            CSeq: 1 REGISTER\r
            Contact: <sip:34020000002000000001@192.168.1.10:5060>\r
            Content-Length: 0\r
            \r
            """;

    private static String keepaliveMessage() {
        String xml = "<?xml version=\"1.0\"?><Notify><CmdType>Keepalive</CmdType><SN>1</SN>"
                + "<DeviceID>34020000002000000001</DeviceID><Status>OK</Status></Notify>";
        return "MESSAGE sip:34020000001320000001@3402000000 SIP/2.0\r\n"
                + "Via: SIP/2.0/TCP 192.168.1.10:5060;branch=z9hG4bK-2\r\n"
                + "From: <sip:34020000002000000001@3402000000>;tag=def\r\n"
                + "To: <sip:34020000001320000001@3402000000>\r\n"
                + "Call-ID: call-2\r\n"
                + "CSeq: 2 MESSAGE\r\n"
                + "Content-Type: Application/MANSCDP+xml\r\n"
                + "Content-Length: " + xml.length() + "\r\n\r\n"
                + xml;
    }

    @Test
    void detectAndDecodeRegister() {
        var buf = Unpooled.wrappedBuffer(REGISTER.getBytes(StandardCharsets.UTF_8));
        assertTrue(Gb28181Codec.detect(buf));
        Gb28181Message msg = Gb28181Codec.decodeFrame(buf);
        assertNotNull(msg);
        assertTrue(msg.isRegister());
        assertEquals("34020000002000000001", Gb28181Sip.resolveDeviceId(msg));
        assertEquals(0, buf.readableBytes());
    }

    @Test
    void decodeKeepaliveXml() {
        var buf = Unpooled.wrappedBuffer(keepaliveMessage().getBytes(StandardCharsets.UTF_8));
        Gb28181Message msg = Gb28181Codec.decodeFrame(buf);
        assertNotNull(msg);
        assertEquals("MESSAGE", msg.getMethod());
        assertTrue(msg.hasManscdpBody());
        assertEquals("Keepalive", Gb28181Xml.cmdType(msg.getBody()).orElseThrow());
        assertEquals("34020000002000000001", Gb28181Xml.deviceId(msg.getBody()).orElseThrow());
    }

    @Test
    void buildRegisterOk() {
        var buf = Unpooled.wrappedBuffer(REGISTER.getBytes(StandardCharsets.UTF_8));
        Gb28181Message req = Gb28181Codec.decodeFrame(buf);
        byte[] ok = Gb28181Sip.buildRegisterOk(req);
        String text = new String(ok, StandardCharsets.UTF_8);
        assertTrue(text.startsWith("SIP/2.0 200 OK"));
        assertTrue(text.contains("Call-ID: call-1"));
    }
}
