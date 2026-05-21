package com.omni.gateway.protocol.simpleframe;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
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
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class SimpleFramePlugin implements ProtocolPlugin {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final OmniMetrics metrics;
    private final ProtocolTrafficLog trafficLog;

    public SimpleFramePlugin(OmniMetrics metrics, ProtocolTrafficLog trafficLog) {
        this.metrics = metrics;
        this.trafficLog = trafficLog;
    }

    @Override
    public String pluginId() {
        return SimpleFrameConstants.PLUGIN_ID;
    }

    @Override
    public int minProbeLength() {
        return 4;
    }

    @Override
    public boolean detect(ByteBuf buffer) {
        return SimpleFrameCodec.detect(buffer);
    }

    @Override
    public List<ChannelHandler> createHandlers(DeviceSession session) {
        return List.of(new SimpleFrameDecoder(metrics, trafficLog));
    }

    @Override
    public AuthResult authenticate(DeviceSession session, Object firstMessage) {
        if (!(firstMessage instanceof SimpleFrameMessage msg)) {
            return AuthResult.FAIL;
        }
        if (!"auth".equalsIgnoreCase(msg.getType())) {
            return AuthResult.FAIL;
        }
        if (msg.getDeviceId() == null || msg.getDeviceId().isBlank()) {
            return AuthResult.FAIL;
        }
        session.setDeviceId(msg.getDeviceId().trim());
        return AuthResult.OK;
    }

    @Override
    public String describeInboundMessage(Object protocolMessage) {
        if (!(protocolMessage instanceof SimpleFrameMessage msg)) {
            return protocolMessage == null ? "null" : protocolMessage.getClass().getSimpleName();
        }
        return "type=" + msg.getType()
                + " deviceId=" + msg.getDeviceId()
                + " messageId=" + msg.getMessageId()
                + " payload=" + (msg.getPayload() != null ? msg.getPayload().toString() : "null");
    }

    @Override
    public Optional<ThingModel> toThingModel(DeviceSession session, Object protocolMessage) {
        if (!(protocolMessage instanceof SimpleFrameMessage msg)) {
            return Optional.empty();
        }
        if ("auth".equalsIgnoreCase(msg.getType())) {
            return Optional.empty();
        }
        if ("ack".equalsIgnoreCase(msg.getType()) && msg.getMessageId() != null) {
            PendingAckRegistry.complete(msg.getMessageId(), "device_ack");
            return Optional.empty();
        }
        ThingModel thing = new ThingModel();
        thing.setDeviceId(session.getDeviceId());
        thing.setProtocol(pluginId());
        thing.setMessageType(msg.getType());
        thing.setTimestamp(Instant.now());
        thing.setPayload(msg.getPayload() != null ? msg.getPayload() : JsonNodeFactory.instance.objectNode());
        return Optional.of(thing);
    }

    @Override
    public Optional<ByteBuf> encodeDownlink(DeviceSession session, CommandEnvelope command) {
        try {
            SimpleFrameMessage msg = new SimpleFrameMessage();
            msg.setType(command.getCommandType());
            msg.setMessageId(command.getMessageId());
            msg.setPayload(command.getPayload());
            return Optional.of(SimpleFrameCodec.encodeFrame(session.getChannel().alloc(), msg));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    @Override
    public boolean matchDownlinkAck(DeviceSession session, Object protocolMessage) {
        if (protocolMessage instanceof SimpleFrameMessage msg) {
            return "ack".equalsIgnoreCase(msg.getType()) && msg.getMessageId() != null;
        }
        return false;
    }

    @Override
    public DownlinkAckMode downlinkAckMode() {
        return DownlinkAckMode.WAIT_DEVICE_ACK;
    }

    @Override
    public Optional<ByteBuf> buildAuthSuccessResponse(DeviceSession session) {
        try {
            SimpleFrameMessage msg = new SimpleFrameMessage();
            msg.setType("auth_ok");
            msg.setDeviceId(session.getDeviceId());
            return Optional.of(SimpleFrameCodec.encodeFrame(session.getChannel().alloc(), msg));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
