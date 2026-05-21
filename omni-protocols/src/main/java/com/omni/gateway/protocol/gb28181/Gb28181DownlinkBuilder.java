package com.omni.gateway.protocol.gb28181;

import com.fasterxml.jackson.databind.JsonNode;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Builds GB28181 SIP downlink frames (MESSAGE with MANSCDP+xml, INVITE with SDP).
 */
public final class Gb28181DownlinkBuilder {

    private static final AtomicInteger SN = new AtomicInteger(1);

    private Gb28181DownlinkBuilder() {
    }

    public static ByteBuf build(DeviceSessionLike ctx, String commandType, JsonNode payload, String messageId) {
        String deviceId = ctx.deviceId();
        String platformId = payload != null && payload.has("platformId")
                ? payload.get("platformId").asText("34020000001320000001")
                : "34020000001320000001";
        String domain = payload != null && payload.has("domain")
                ? payload.get("domain").asText("3402000000")
                : "3402000000";

        return switch (commandType != null ? commandType : "") {
            case "InviteStream", "inviteStream", "INVITE" -> buildInvite(ctx, deviceId, platformId, domain, payload, messageId);
            case "Catalog", "catalog" -> buildMessage(deviceId, platformId, domain, ctx.localAddress(),
                    buildCatalogQueryXml(nextSn()), messageId);
            case "DeviceControl", "deviceControl", "Broadcast", "broadcast" -> {
                String xml = payload != null && payload.has("xml")
                        ? payload.get("xml").asText()
                        : buildBroadcastXml(deviceId, payload);
                yield buildMessage(deviceId, platformId, domain, ctx.localAddress(), xml, messageId);
            }
            default -> {
                String xml = payload != null && payload.has("xml")
                        ? payload.get("xml").asText()
                        : buildBroadcastXml(deviceId, payload);
                yield buildMessage(deviceId, platformId, domain, ctx.localAddress(), xml, messageId);
            }
        };
    }

    public interface DeviceSessionLike {
        String deviceId();

        String localAddress();
    }

    public static String buildBroadcastXml(String deviceId, JsonNode payload) {
        int sn = nextSn();
        String cmd = "Broadcast";
        String streamUrl = "";
        if (payload != null) {
            if (payload.has("streamUrl")) {
                streamUrl = payload.get("streamUrl").asText();
            } else if (payload.has("rtspUrl")) {
                streamUrl = payload.get("rtspUrl").asText();
            }
            if (payload.has("cmdType")) {
                cmd = payload.get("cmdType").asText();
            }
        }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n"
                + "<Control>\r\n"
                + "<CmdType>" + cmd + "</CmdType>\r\n"
                + "<SN>" + sn + "</SN>\r\n"
                + "<DeviceID>" + deviceId + "</DeviceID>\r\n"
                + "<BroadcastUrl>" + escapeXml(streamUrl) + "</BroadcastUrl>\r\n"
                + "</Control>\r\n";
    }

    public static String buildCatalogQueryXml(int sn) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n"
                + "<Query>\r\n"
                + "<CmdType>Catalog</CmdType>\r\n"
                + "<SN>" + sn + "</SN>\r\n"
                + "<DeviceID>34020000001320000001</DeviceID>\r\n"
                + "</Query>\r\n";
    }

    private static ByteBuf buildInvite(DeviceSessionLike ctx, String deviceId, String platformId,
                                       String domain, JsonNode payload, String messageId) {
        String streamUrl = payload != null && payload.has("streamUrl")
                ? payload.get("streamUrl").asText("0.0.0.0")
                : "0.0.0.0";
        int port = payload != null && payload.has("mediaPort") ? payload.get("mediaPort").asInt(10000) : 10000;
        String sdp = "v=0\r\n"
                + "o=" + platformId + " 0 0 IN IP4 " + streamUrl + "\r\n"
                + "s=Play\r\n"
                + "c=IN IP4 " + streamUrl + "\r\n"
                + "t=0 0\r\n"
                + "m=video " + port + " RTP/AVP 96\r\n"
                + "a=recvonly\r\n"
                + "a=rtpmap:96 PS/90000\r\n";
        String toUri = "sip:" + deviceId + "@" + domain;
        String fromUri = "sip:" + platformId + "@" + domain;
        String callId = messageId != null ? messageId : String.valueOf(System.nanoTime());
        String branch = "z9hG4bK-omni-invite";
        StringBuilder sb = new StringBuilder(2048);
        sb.append("INVITE ").append(toUri).append(" SIP/2.0\r\n");
        sb.append("Via: SIP/2.0/TCP ").append(ctx.localAddress()).append(";branch=").append(branch).append("\r\n");
        sb.append("From: <").append(fromUri).append(">;tag=omni-inv\r\n");
        sb.append("To: <").append(toUri).append(">\r\n");
        sb.append("Call-ID: ").append(callId).append("\r\n");
        sb.append("CSeq: 1 INVITE\r\n");
        sb.append("Contact: <").append(fromUri).append(">\r\n");
        sb.append("Content-Type: application/sdp\r\n");
        byte[] body = sdp.getBytes(StandardCharsets.UTF_8);
        sb.append("Content-Length: ").append(body.length).append("\r\n\r\n");
        sb.append(sdp);
        return Unpooled.wrappedBuffer(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    static ByteBuf buildMessage(String deviceId, String platformId, String domain,
                               String localAddr, String xml, String messageId) {
        String toUri = "sip:" + deviceId + "@" + domain;
        String fromUri = "sip:" + platformId + "@" + domain;
        String callId = messageId != null ? messageId : String.valueOf(System.nanoTime());
        StringBuilder sb = new StringBuilder(1024);
        sb.append("MESSAGE ").append(toUri).append(" SIP/2.0\r\n");
        sb.append("Via: SIP/2.0/TCP ").append(localAddr).append(";branch=z9hG4bK-omni-msg\r\n");
        sb.append("From: <").append(fromUri).append(">;tag=omni\r\n");
        sb.append("To: <").append(toUri).append(">\r\n");
        sb.append("Call-ID: ").append(callId).append("\r\n");
        sb.append("CSeq: 1 MESSAGE\r\n");
        sb.append("Content-Type: Application/MANSCDP+xml\r\n");
        byte[] bodyBytes = xml.getBytes(StandardCharsets.UTF_8);
        sb.append("Content-Length: ").append(bodyBytes.length).append("\r\n\r\n");
        sb.append(xml);
        return Unpooled.wrappedBuffer(sb.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static int nextSn() {
        return SN.getAndIncrement();
    }

    private static String escapeXml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
