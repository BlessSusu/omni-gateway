package com.omni.gateway.bootstrap.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.omni.gateway.bootstrap.OmniGatewayProperties;
import com.omni.gateway.core.lifecycle.DeviceLifecyclePublisher;
import com.omni.gateway.core.session.DeviceSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class KafkaDeviceLifecyclePublisher implements DeviceLifecyclePublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaDeviceLifecyclePublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OmniGatewayProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public KafkaDeviceLifecyclePublisher(KafkaTemplate<String, String> kafkaTemplate,
                                         OmniGatewayProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
    }

    @Override
    public void publishOnline(DeviceSession session) {
        publish("online", session);
    }

    @Override
    public void publishOffline(DeviceSession session) {
        publish("offline", session);
    }

    private void publish(String event, DeviceSession session) {
        if (!properties.getKafka().isEnabled() || !properties.getKafka().isLifecycleEnabled()) {
            return;
        }
        try {
            ObjectNode node = JsonNodeFactory.instance.objectNode();
            node.put("event", event);
            node.put("deviceId", session.getDeviceId());
            node.put("protocol", session.getProtocolId());
            node.put("channelId", session.getChannelId());
            node.put("port", session.getLocalPort());
            node.put("timestamp", System.currentTimeMillis());
            kafkaTemplate.send(
                    properties.getKafka().getLifecycleTopic(),
                    session.getDeviceId(),
                    objectMapper.writeValueAsString(node));
        } catch (Exception e) {
            log.error("Lifecycle publish failed event={} deviceId={}", event, session.getDeviceId(), e);
        }
    }
}
