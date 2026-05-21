package com.omni.gateway.bootstrap.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.gateway.bootstrap.OmniGatewayProperties;
import com.omni.gateway.core.model.CommandEnvelope;
import com.omni.gateway.core.session.DeviceSession;
import com.omni.gateway.core.session.SessionRegistry;
import com.omni.gateway.network.downlink.DeviceSerialExecutor;
import com.omni.gateway.network.downlink.DownlinkDispatcher;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "omni.downlink.broadcast-enabled", havingValue = "true")
public class BroadcastDownlinkConsumer {

    private static final Logger log = LoggerFactory.getLogger(BroadcastDownlinkConsumer.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SessionRegistry sessionRegistry;
    private final DownlinkDispatcher dispatcher;
    private final DeviceSerialExecutor serialExecutor;
    private final OmniGatewayProperties properties;

    public BroadcastDownlinkConsumer(SessionRegistry sessionRegistry,
                                     DownlinkDispatcher dispatcher,
                                     DeviceSerialExecutor serialExecutor,
                                     OmniGatewayProperties properties) {
        this.sessionRegistry = sessionRegistry;
        this.dispatcher = dispatcher;
        this.serialExecutor = serialExecutor;
        this.properties = properties;
    }

    @KafkaListener(
            topics = "${omni.downlink.broadcast-topic:omni.command.downlink.broadcast}",
            groupId = "${omni.downlink.broadcast-consumer-group:omni-gateway-broadcast}-${omni.node-id:local}",
            containerFactory = "downlinkKafkaListenerContainerFactory")
    public void onMessage(ConsumerRecord<String, String> record, Acknowledgment ack) {
        if (!properties.getDownlink().isEnabled()) {
            if (ack != null) {
                ack.acknowledge();
            }
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(record.value());
            CommandEnvelope template = objectMapper.treeToValue(root, CommandEnvelope.class);
            if (template.getProtocol() == null || template.getCommandType() == null) {
                ack.acknowledge();
                return;
            }
            String filterProtocol = root.has("filterProtocol") ? root.get("filterProtocol").asText(null) : null;
            JsonNode deviceIds = root.get("deviceIds");

            int dispatched = 0;
            if (deviceIds != null && deviceIds.isArray()) {
                for (JsonNode id : deviceIds) {
                    if (dispatchToDevice(id.asText(), template, filterProtocol)) {
                        dispatched++;
                    }
                }
            } else {
                for (DeviceSession session : sessionRegistry.localSessions()) {
                    if (matchesFilter(session, filterProtocol, template.getProtocol())) {
                        dispatchOne(session, template);
                        dispatched++;
                    }
                }
            }
            log.debug("Broadcast dispatched {} local session(s)", dispatched);
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Broadcast consume error offset={}", record.offset(), e);
        }
    }

    private boolean dispatchToDevice(String deviceId, CommandEnvelope template, String filterProtocol) {
        return sessionRegistry.get(deviceId)
                .filter(s -> matchesFilter(s, filterProtocol, template.getProtocol()))
                .map(s -> {
                    dispatchOne(s, template);
                    return true;
                })
                .orElse(false);
    }

    private void dispatchOne(DeviceSession session, CommandEnvelope template) {
        CommandEnvelope cmd = new CommandEnvelope();
        cmd.setMessageId(template.getMessageId() + "-" + session.getDeviceId());
        cmd.setDeviceId(session.getDeviceId());
        cmd.setProtocol(template.getProtocol());
        cmd.setCommandType(template.getCommandType());
        cmd.setPayload(template.getPayload());
        cmd.setTimeoutMs(template.getTimeoutMs());
        cmd.setTraceId(template.getTraceId());
        serialExecutor.execute(session.getDeviceId(), () -> dispatcher.dispatch(session, cmd));
    }

    private static boolean matchesFilter(DeviceSession session, String filterProtocol, String cmdProtocol) {
        if (session.getProtocolId() == null || !session.getProtocolId().equals(cmdProtocol)) {
            return false;
        }
        if (filterProtocol != null && !filterProtocol.isBlank() && !filterProtocol.equals(session.getProtocolId())) {
            return false;
        }
        return session.isActive();
    }
}
