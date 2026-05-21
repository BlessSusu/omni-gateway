package com.omni.gateway.bootstrap.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.gateway.bootstrap.OmniGatewayProperties;
import com.omni.gateway.core.model.CommandEnvelope;
import com.omni.gateway.core.model.DownlinkResult;
import com.omni.gateway.core.model.DownlinkStatus;
import com.omni.gateway.core.session.DeviceSession;
import com.omni.gateway.core.session.SessionRegistry;
import com.omni.gateway.network.downlink.DeviceSerialExecutor;
import com.omni.gateway.network.downlink.DownlinkDispatcher;
import com.omni.gateway.network.metrics.OmniMetrics;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class DownlinkConsumer {

    private static final Logger log = LoggerFactory.getLogger(DownlinkConsumer.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SessionRegistry sessionRegistry;
    private final DownlinkDispatcher dispatcher;
    private final DeviceSerialExecutor serialExecutor;
    private final OmniMetrics metrics;
    private final KafkaDownlinkResultPublisher resultPublisher;
    private final OmniGatewayProperties properties;

    public DownlinkConsumer(SessionRegistry sessionRegistry,
                            DownlinkDispatcher dispatcher,
                            DeviceSerialExecutor serialExecutor,
                            OmniMetrics metrics,
                            KafkaDownlinkResultPublisher resultPublisher,
                            OmniGatewayProperties properties) {
        this.sessionRegistry = sessionRegistry;
        this.dispatcher = dispatcher;
        this.serialExecutor = serialExecutor;
        this.metrics = metrics;
        this.resultPublisher = resultPublisher;
        this.properties = properties;
    }

    @KafkaListener(
            topics = "${omni.downlink.topic:omni.command.downlink}",
            groupId = "${omni.downlink.consumer-group:omni-gateway-downlink}-${omni.node-id:local}",
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
            if (cmd.getDeviceId() == null || cmd.getMessageId() == null || cmd.getProtocol() == null) {
                log.warn("Invalid downlink command, skip");
                ack.acknowledge();
                return;
            }
            var sessionOpt = sessionRegistry.get(cmd.getDeviceId());
            if (sessionOpt.isEmpty()) {
                metrics.downlinkSkipNotLocal();
                resultPublisher.publish(DownlinkResult.of(
                        cmd.getMessageId(), cmd.getDeviceId(), DownlinkStatus.OFFLINE, "not_local"));
                ack.acknowledge();
                return;
            }
            DeviceSession session = sessionOpt.get();
            serialExecutor.execute(cmd.getDeviceId(), () -> dispatcher.dispatch(session, cmd));
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Downlink consume error offset={}", record.offset(), e);
        }
    }
}
