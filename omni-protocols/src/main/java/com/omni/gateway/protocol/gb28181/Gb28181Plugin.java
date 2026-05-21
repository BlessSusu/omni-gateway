package com.omni.gateway.protocol.gb28181;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.omni.gateway.core.auth.AuthResult;
import com.omni.gateway.core.logging.ProtocolTrafficLog;
import com.omni.gateway.core.model.CommandEnvelope;
import com.omni.gateway.core.model.ThingModel;
import com.omni.gateway.core.plugin.ProtocolPlugin;
import com.omni.gateway.core.session.DeviceSession;
import com.omni.gateway.network.metrics.OmniMetrics;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandler;
import io.netty.util.AttributeKey;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class Gb28181Plugin implements ProtocolPlugin {

    public static final AttributeKey<Gb28181Message> PENDING_REGISTER =
            AttributeKey.valueOf("omni.gb28181.pendingRegister");

    private final OmniMetrics metrics;
    private final ProtocolTrafficLog trafficLog;

    public Gb28181Plugin(OmniMetrics metrics, ProtocolTrafficLog trafficLog) {
        this.metrics = metrics;
        this.trafficLog = trafficLog;
    }

    @Override
    public String pluginId() {
        return Gb28181Constants.PLUGIN_ID;
    }

    @Override
    public int minProbeLength() {
        return 7;
    }

    @Override
    public boolean detect(ByteBuf buffer) {
        return Gb28181Codec.detect(buffer);
    }

    @Override
    public List<ChannelHandler> createHandlers(DeviceSession session) {
        return List.of(new Gb28181Decoder(metrics, trafficLog));
    }

    @Override
    public AuthResult authenticate(DeviceSession session, Object firstMessage) {
        if (!(firstMessage instanceof Gb28181Message msg)) {
            return AuthResult.FAIL;
        }
        if (!msg.isRegister()) {
            return AuthResult.FAIL;
        }
        String deviceId = Gb28181Sip.resolveDeviceId(msg);
        if (deviceId == null || deviceId.isBlank()) {
            return AuthResult.FAIL;
        }
        session.setDeviceId(deviceId.trim());
        session.getChannel().attr(PENDING_REGISTER).set(msg);
        return AuthResult.OK;
    }

    @Override
    public String describeInboundMessage(Object protocolMessage) {
        if (!(protocolMessage instanceof Gb28181Message msg)) {
            return protocolMessage == null ? "null" : protocolMessage.getClass().getSimpleName();
        }
        String cmd = msg.getBody() != null ? Gb28181Xml.cmdType(msg.getBody()).orElse("-") : "-";
        return String.format("sip=%s deviceHint=%s cmdType=%s contentLength=%d",
                msg.isResponse() ? ("SIP/" + msg.getStatusCode()) : msg.getMethod(),
                Gb28181Sip.resolveDeviceId(msg),
                cmd,
                msg.getBody() != null ? msg.getBody().length() : 0);
    }

    @Override
    public Optional<ThingModel> toThingModel(DeviceSession session, Object protocolMessage) {
        if (!(protocolMessage instanceof Gb28181Message msg)) {
            return Optional.empty();
        }
        if (msg.isResponse()) {
            return Optional.empty();
        }
        if (msg.isRegister()) {
            return Optional.empty();
        }

        ObjectNode payload = JsonNodeFactory.instance.objectNode();
        payload.put("sipMethod", msg.getMethod() != null ? msg.getMethod() : "");
        payload.put("requestUri", msg.getRequestUri() != null ? msg.getRequestUri() : "");
        if (msg.getBody() != null) {
            payload.put("xml", msg.getBody());
            Gb28181Xml.cmdType(msg.getBody()).ifPresent(v -> payload.put("cmdType", v));
            Gb28181Xml.sn(msg.getBody()).ifPresent(v -> payload.put("sn", v));
            Gb28181Xml.rootElement(msg.getBody()).ifPresent(v -> payload.put("root", v));
        }
        String messageType = resolveMessageType(msg);
        String deviceId = session.getDeviceId();
        if (deviceId == null && msg.getBody() != null) {
            deviceId = Gb28181Xml.deviceId(msg.getBody()).orElse(null);
        }
        if (deviceId == null) {
            deviceId = Gb28181Sip.resolveDeviceId(msg);
        }
        if (deviceId == null) {
            return Optional.empty();
        }

        ThingModel thing = new ThingModel();
        thing.setDeviceId(deviceId);
        thing.setProtocol(pluginId());
        thing.setMessageType(messageType);
        thing.setTimestamp(Instant.now());
        thing.setPayload(payload);
        return Optional.of(thing);
    }

    private static String resolveMessageType(Gb28181Message msg) {
        if (msg.getBody() != null) {
            Optional<String> cmd = Gb28181Xml.cmdType(msg.getBody());
            if (cmd.isPresent()) {
                return cmd.get().toLowerCase(Locale.ROOT);
            }
        }
        if (msg.getMethod() != null) {
            return msg.getMethod().toLowerCase(Locale.ROOT);
        }
        return "unknown";
    }

    @Override
    public Optional<ByteBuf> encodeDownlink(DeviceSession session, CommandEnvelope command) {
        if (command.getPayload() == null) {
            return Optional.empty();
        }
        String xml = command.getPayload().has("xml")
                ? command.getPayload().get("xml").asText()
                : command.getPayload().toString();
        if (xml == null || xml.isBlank()) {
            return Optional.empty();
        }
        String deviceId = session.getDeviceId() != null ? session.getDeviceId() : command.getDeviceId();
        String toUri = "sip:" + deviceId + "@3402000000";
        String fromUri = "sip:34020000001320000001@3402000000";
        String callId = command.getMessageId() != null ? command.getMessageId() : String.valueOf(System.nanoTime());
        StringBuilder sb = new StringBuilder(1024);
        sb.append("MESSAGE ").append(toUri).append(" SIP/2.0\r\n");
        sb.append("Via: SIP/2.0/TCP ").append(session.getChannel().localAddress()).append(";branch=z9hG4bK-omni\r\n");
        sb.append("From: <").append(fromUri).append(">;tag=omni\r\n");
        sb.append("To: <").append(toUri).append(">\r\n");
        sb.append("Call-ID: ").append(callId).append("\r\n");
        sb.append("CSeq: 1 MESSAGE\r\n");
        sb.append("Content-Type: Application/MANSCDP+xml\r\n");
        byte[] bodyBytes = xml.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        sb.append("Content-Length: ").append(bodyBytes.length).append("\r\n\r\n");
        sb.append(xml);
        return Optional.of(Unpooled.wrappedBuffer(sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }

    @Override
    public DownlinkAckMode downlinkAckMode() {
        return DownlinkAckMode.FIRE_AND_FORGET;
    }

    @Override
    public Optional<ByteBuf> buildAuthSuccessResponse(DeviceSession session) {
        Gb28181Message req = session.getChannel().attr(PENDING_REGISTER).getAndSet(null);
        if (req == null || !req.isRegister()) {
            return Optional.empty();
        }
        return Optional.of(Unpooled.wrappedBuffer(Gb28181Sip.buildRegisterOk(req)));
    }
}
