package com.omni.gateway.bootstrap.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.gateway.bootstrap.OmniGatewayProperties;
import com.omni.gateway.core.model.CommandEnvelope;
import com.omni.gateway.core.model.DownlinkResult;
import com.omni.gateway.core.model.DownlinkStatus;
import com.omni.gateway.core.session.DistributedSessionIndex;
import com.omni.gateway.core.session.SessionRoute;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Optional bridge: unified downlink topic → per-node topic via Redis routing index.
 */
@Component
@ConditionalOnProperty(name = "omni.downlink.router-enabled", havingValue = "true")
public class DownlinkRouterConsumer {

    private static final Logger log = LoggerFactory.getLogger(DownlinkRouterConsumer.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DistributedSessionIndex sessionIndex;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final KafkaDownlinkResultPublisher resultPublisher;
    private final OmniGatewayProperties properties;

    public DownlinkRouterConsumer(DistributedSessionIndex sessionIndex,
                                  KafkaTemplate<String, String> kafkaTemplate,
                                  KafkaDownlinkResultPublisher resultPublisher,
                                  OmniGatewayProperties properties) {
        this.sessionIndex = sessionIndex;
        this.kafkaTemplate = kafkaTemplate;
        this.resultPublisher = resultPublisher;
        this.properties = properties;
    }

    @KafkaListener(
            topics = "${omni.downlink.topic:omni.command.downlink}",
            groupId = "${omni.downlink.router-consumer-group:omni-gateway-downlink-router}",
            containerFactory = "downlinkKafkaListenerContainerFactory")
    public void onMessage(ConsumerRecord<String, String> record, Acknowledgment ack) {
        if (!properties.getDownlink().isEnabled()) {
            if (ack != null) {
                ack.acknowledge();
            }
            return;
        }
        try {
            CommandEnvelope cmd = objectMapper.readValue(record.value(), CommandEnvelope.class);
            if (cmd.getDeviceId() == null || cmd.getMessageId() == null) {
                ack.acknowledge();
                return;
            }
            Optional<SessionRoute> route = sessionIndex.lookup(cmd.getDeviceId());
            if (route.isEmpty()) {
                resultPublisher.publish(DownlinkResult.of(
                        cmd.getMessageId(), cmd.getDeviceId(), DownlinkStatus.OFFLINE, "no_route"));
                ack.acknowledge();
                return;
            }
            String nodeTopic = properties.getDownlink().resolveNodeTopic(route.get().nodeId());
            kafkaTemplate.send(nodeTopic, cmd.getDeviceId(), record.value());
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Downlink router error offset={}", record.offset(), e);
        }
    }
}
