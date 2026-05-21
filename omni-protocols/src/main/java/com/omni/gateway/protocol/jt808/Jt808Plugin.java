package com.omni.gateway.protocol.jt808;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.omni.gateway.core.auth.AuthResult;
import com.omni.gateway.core.logging.ProtocolTrafficLog;
import com.omni.gateway.core.model.CommandEnvelope;
import com.omni.gateway.core.model.ThingModel;
import com.omni.gateway.core.plugin.ProtocolPlugin;
import com.omni.gateway.core.session.DeviceSession;
import com.omni.gateway.network.downlink.PendingAckRegistry;
import com.omni.gateway.network.metrics.OmniMetrics;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.util.AttributeKey;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * JT808 协议插件骨架（2013 常见消息：注册、心跳、平台/终端通用应答）。
 */
public class Jt808Plugin implements ProtocolPlugin {

    public static final AttributeKey<Integer> LAST_PLATFORM_SERIAL =
            AttributeKey.valueOf("omni.jt808.lastPlatformSerial");
    public static final AttributeKey<Integer> LAST_PLATFORM_MSG_ID =
            AttributeKey.valueOf("omni.jt808.lastPlatformMsgId");
    public static final AttributeKey<String> LAST_DOWNLINK_MESSAGE_ID =
            AttributeKey.valueOf("omni.jt808.lastDownlinkMessageId");
    public static final AttributeKey<Integer> REGISTER_SERIAL =
            AttributeKey.valueOf("omni.jt808.registerSerial");

    private final OmniMetrics metrics;
    private final ProtocolTrafficLog trafficLog;

    public Jt808Plugin(OmniMetrics metrics, ProtocolTrafficLog trafficLog) {
        this.metrics = metrics;
        this.trafficLog = trafficLog;
    }

    @Override
    public String pluginId() {
        return Jt808Constants.PLUGIN_ID;
    }

    @Override
    public int minProbeLength() {
        return 1;
    }

    @Override
    public boolean detect(ByteBuf buffer) {
        return Jt808Codec.detect(buffer);
    }

    @Override
    public List<ChannelHandler> createHandlers(DeviceSession session) {
        return List.of(new Jt808Decoder(metrics, trafficLog));
    }

    @Override
    public AuthResult authenticate(DeviceSession session, Object firstMessage) {
        if (!(firstMessage instanceof Jt808Message msg)) {
            return AuthResult.FAIL;
        }
        if (msg.getMessageId() != Jt808Constants.MSG_TERMINAL_REGISTER
                && msg.getMessageId() != Jt808Constants.MSG_TERMINAL_AUTH) {
            return AuthResult.FAIL;
        }
        if (msg.getTerminalPhone() == null || msg.getTerminalPhone().isBlank()) {
            return AuthResult.FAIL;
        }
        session.setDeviceId(msg.getTerminalPhone());
        session.getChannel().attr(REGISTER_SERIAL).set(msg.getSerialNo());
        return AuthResult.OK;
    }

    @Override
    public String describeInboundMessage(Object protocolMessage) {
        if (!(protocolMessage instanceof Jt808Message msg)) {
            return protocolMessage == null ? "null" : protocolMessage.getClass().getSimpleName();
        }
        String bodyHex = msg.getBody() != null && msg.getBody().length > 0
                ? bytesToHex(msg.getBody()) : "";
        return String.format("msgId=0x%04X phone=%s serial=%d bodyLen=%d bodyHex=%s",
                msg.getMessageId(), msg.getTerminalPhone(), msg.getSerialNo(),
                msg.getBodyLength(), bodyHex);
    }

    @Override
    public Optional<ThingModel> toThingModel(DeviceSession session, Object protocolMessage) {
        if (!(protocolMessage instanceof Jt808Message msg)) {
            return Optional.empty();
        }
        if (msg.getMessageId() == Jt808Constants.MSG_TERMINAL_REGISTER
                || msg.getMessageId() == Jt808Constants.MSG_TERMINAL_AUTH) {
            return Optional.empty();
        }
        if (msg.getMessageId() == Jt808Constants.MSG_TERMINAL_COMMON_ACK) {
            handleTerminalAck(session, msg);
            return Optional.empty();
        }
        ObjectNode payload = JsonNodeFactory.instance.objectNode();
        payload.put("serialNo", msg.getSerialNo());
        payload.put("bodyLength", msg.getBodyLength());
        if (msg.getBody() != null && msg.getBody().length > 0) {
            payload.put("bodyHex", bytesToHex(msg.getBody()));
        }
        ThingModel thing = new ThingModel();
        thing.setDeviceId(session.getDeviceId());
        thing.setProtocol(pluginId());
        thing.setMessageType(String.format("0x%04X", msg.getMessageId()));
        thing.setTimestamp(Instant.now());
        thing.setPayload(payload);
        return Optional.of(thing);
    }

    @Override
    public Optional<ByteBuf> encodeDownlink(DeviceSession session, CommandEnvelope command) {
        try {
            String phone = session.getDeviceId();
            int serial = session.nextDownlinkSerial() & 0xFFFF;
            String type = command.getCommandType();
            if ("REGISTER_ACK".equalsIgnoreCase(type) || "0x8100".equalsIgnoreCase(type)) {
                int answerSerial = command.getPayload() != null && command.getPayload().has("answerSerial")
                        ? command.getPayload().get("answerSerial").asInt()
                        : 1;
                byte result = command.getPayload() != null && command.getPayload().has("result")
                        ? (byte) command.getPayload().get("result").asInt()
                        : 0;
                ByteBuf buf = Jt808Codec.encodeRegisterAck(
                        session.getChannel().alloc(), phone, serial, answerSerial, result);
                if (command.getMessageId() != null) {
                    session.getChannel().attr(LAST_DOWNLINK_MESSAGE_ID).set(command.getMessageId());
                    session.getChannel().attr(LAST_PLATFORM_SERIAL).set(serial);
                }
                return Optional.of(buf);
            }
            int msgId = parseMsgId(type);
            byte[] body = new byte[0];
            if (command.getPayload() != null && command.getPayload().has("bodyHex")) {
                body = hexToBytes(command.getPayload().get("bodyHex").asText());
            }
            ByteBuf buf = Jt808Codec.encode(session.getChannel().alloc(), msgId, phone, serial, body);
            session.getChannel().attr(LAST_PLATFORM_SERIAL).set(serial);
            session.getChannel().attr(LAST_PLATFORM_MSG_ID).set(msgId);
            if (command.getMessageId() != null) {
                session.getChannel().attr(LAST_DOWNLINK_MESSAGE_ID).set(command.getMessageId());
            }
            return Optional.of(buf);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public boolean matchDownlinkAck(DeviceSession session, Object protocolMessage) {
        return protocolMessage instanceof Jt808Message msg
                && msg.getMessageId() == Jt808Constants.MSG_TERMINAL_COMMON_ACK;
    }

    @Override
    public DownlinkAckMode downlinkAckMode() {
        return DownlinkAckMode.WAIT_DEVICE_ACK;
    }

    @Override
    public Optional<ByteBuf> buildAuthSuccessResponse(DeviceSession session) {
        Integer regSerial = session.getChannel().attr(REGISTER_SERIAL).get();
        int answerSerial = regSerial != null ? regSerial : 1;
        return Optional.of(Jt808Codec.encodeRegisterAck(
                session.getChannel().alloc(),
                session.getDeviceId(),
                session.nextDownlinkSerial() & 0xFFFF,
                answerSerial,
                (byte) 0));
    }

    private void handleTerminalAck(DeviceSession session, Jt808Message msg) {
        byte[] body = msg.getBody();
        if (body == null || body.length < 5) {
            return;
        }
        int answerSerial = ((body[0] & 0xFF) << 8) | (body[1] & 0xFF);
        Integer expectedSerial = session.getChannel().attr(LAST_PLATFORM_SERIAL).get();
        String messageId = session.getChannel().attr(LAST_DOWNLINK_MESSAGE_ID).get();
        if (expectedSerial != null && expectedSerial == answerSerial && messageId != null) {
            PendingAckRegistry.complete(messageId, "terminal_ack");
            session.getChannel().attr(LAST_DOWNLINK_MESSAGE_ID).set(null);
        }
    }

    private static int parseMsgId(String type) {
        if (type == null) {
            return 0x8900;
        }
        if (type.startsWith("0x") || type.startsWith("0X")) {
            return Integer.parseInt(type.substring(2), 16);
        }
        return Integer.parseInt(type, 16);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    private static byte[] hexToBytes(String hex) {
        String h = hex.replaceAll("\\s", "");
        byte[] out = new byte[h.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(h.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }
}
