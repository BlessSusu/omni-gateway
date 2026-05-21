package com.omni.gateway.protocol.gb28181;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SIP URI / header helpers for GB28181.
 */
public final class Gb28181Sip {

    private static final Pattern SIP_USER = Pattern.compile("sip:([^@;>\\s]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DEVICE_ID_20 = Pattern.compile("(\\d{20})");

    private Gb28181Sip() {
    }

    /**
     * Extract 20-digit GB device ID from From/Contact/To header value.
     */
    public static String extractDeviceId(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return null;
        }
        Matcher sip = SIP_USER.matcher(headerValue);
        if (sip.find()) {
            String user = sip.group(1);
            Matcher id = DEVICE_ID_20.matcher(user);
            if (id.find()) {
                return id.group(1);
            }
            return user;
        }
        Matcher id = DEVICE_ID_20.matcher(headerValue);
        if (id.find()) {
            return id.group(1);
        }
        return null;
    }

    public static String resolveDeviceId(Gb28181Message msg) {
        if (msg.getBody() != null) {
            var fromXml = Gb28181Xml.deviceId(msg.getBody());
            if (fromXml.isPresent()) {
                return fromXml.get();
            }
        }
        String from = Gb28181Sip.extractDeviceId(msg.header("From"));
        if (from != null) {
            return from;
        }
        return Gb28181Sip.extractDeviceId(msg.header("Contact"));
    }

    public static byte[] buildRegisterOk(Gb28181Message request) {
        String via = request.header("Via");
        String from = request.header("From");
        String to = request.header("To");
        String callId = request.header("Call-ID");
        String cseq = request.header("CSeq");
        StringBuilder sb = new StringBuilder(512);
        sb.append("SIP/2.0 200 OK\r\n");
        if (via != null) {
            sb.append("Via: ").append(via).append("\r\n");
        }
        if (from != null) {
            sb.append("From: ").append(from).append("\r\n");
        }
        if (to != null) {
            sb.append("To: ").append(to).append("\r\n");
        }
        if (callId != null) {
            sb.append("Call-ID: ").append(callId).append("\r\n");
        }
        if (cseq != null) {
            sb.append("CSeq: ").append(cseq).append("\r\n");
        }
        sb.append("User-Agent: OmniGateway\r\n");
        sb.append("Content-Length: 0\r\n\r\n");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }
}
