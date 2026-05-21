package com.omni.gateway.bootstrap.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.gateway.bootstrap.OmniGatewayProperties;
import com.omni.gateway.core.downlink.DownlinkResultPublisher;
import com.omni.gateway.core.model.DownlinkResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class KafkaDownlinkResultPublisher implements DownlinkResultPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaDownlinkResultPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OmniGatewayProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public KafkaDownlinkResultPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                        OmniGatewayProperties properties) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
    }

    @Override
    public void publish(DownlinkResult result) {
        if (!properties.getDownlink().isResultEnabled()) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(result);
            kafkaTemplate.send(properties.getDownlink().getResultTopic(), result.getDeviceId(), json);
        } catch (Exception e) {
            log.error("Failed to publish downlink result messageId={}", result.getMessageId(), e);
        }
    }
}
