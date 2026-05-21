package com.omni.gateway.bootstrap.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.gateway.bootstrap.OmniGatewayProperties;
import com.omni.gateway.bootstrap.downlink.PendingDownlinkReplayService;
import com.omni.gateway.core.model.CommandEnvelope;
import com.omni.gateway.core.model.DownlinkResult;
import com.omni.gateway.core.model.DownlinkStatus;
import com.omni.gateway.core.session.DeviceSession;
import com.omni.gateway.core.session.DistributedSessionIndex;
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
    private final DistributedSessionIndex sessionIndex;
    private final DownlinkDispatcher dispatcher;
    private final DeviceSerialExecutor serialExecutor;
    private final OmniMetrics metrics;
    private final KafkaDownlinkResultPublisher resultPublisher;
    private final OmniGatewayProperties properties;
    private final PendingDownlinkReplayService pendingReplayService;

    public DownlinkConsumer(SessionRegistry sessionRegistry,
                            DistributedSessionIndex sessionIndex,
                            DownlinkDispatcher dispatcher,
                            DeviceSerialExecutor serialExecutor,
                            OmniMetrics metrics,
                            KafkaDownlinkResultPublisher resultPublisher,
                            OmniGatewayProperties properties,
                            PendingDownlinkReplayService pendingReplayService) {
        this.sessionRegistry = sessionRegistry;
        this.sessionIndex = sessionIndex;
        this.dispatcher = dispatcher;
        this.serialExecutor = serialExecutor;
        this.metrics = metrics;
        this.resultPublisher = resultPublisher;
        this.properties = properties;
        this.pendingReplayService = pendingReplayService;
    }

    @KafkaListener(
            topics = "#{@downlinkTopicProvider.nodeTopic}",
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
                var route = sessionIndex.lookup(cmd.getDeviceId());
                if (route.isPresent() && !properties.getNodeId().equals(route.get().nodeId())) {
                    resultPublisher.publish(DownlinkResult.of(
                            cmd.getMessageId(), cmd.getDeviceId(), DownlinkStatus.OFFLINE, "not_local"));
                } else if (properties.getDownlink().isPendingEnabled()) {
                    pendingReplayService.enqueueIfOffline(cmd);
                    resultPublisher.publish(DownlinkResult.of(
                            cmd.getMessageId(), cmd.getDeviceId(), DownlinkStatus.OFFLINE, "queued_pending"));
                } else {
                    metrics.downlinkSkipNotLocal();
                    resultPublisher.publish(DownlinkResult.of(
                            cmd.getMessageId(), cmd.getDeviceId(), DownlinkStatus.OFFLINE, "offline"));
                }
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
