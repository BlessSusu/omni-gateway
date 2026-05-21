package com.omni.gateway.network.downlink;

import com.omni.gateway.core.model.CommandEnvelope;
import com.omni.gateway.core.model.DownlinkResult;
import com.omni.gateway.core.model.DownlinkStatus;
import com.omni.gateway.core.downlink.DownlinkResultPublisher;
import com.omni.gateway.network.metrics.OmniMetrics;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class PendingAckRegistry {

    private static final Map<String, PendingAck> PENDING = new ConcurrentHashMap<>();
    private static final ScheduledExecutorService SCHEDULER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "omni-downlink-ack-timeout");
                t.setDaemon(true);
                return t;
            });

    private PendingAckRegistry() {
    }

    public static void register(String messageId,
                                CommandEnvelope cmd,
                                String protocolId,
                                DownlinkResultPublisher publisher,
                                OmniMetrics metrics) {
        long timeoutMs = cmd.getTimeoutMs() != null ? cmd.getTimeoutMs() : 5000L;
        PendingAck ack = new PendingAck(cmd, protocolId, publisher, metrics);
        PENDING.put(messageId, ack);
        SCHEDULER.schedule(() -> {
            PendingAck removed = PENDING.remove(messageId);
            if (removed != null) {
                removed.timeout();
            }
        }, timeoutMs, TimeUnit.MILLISECONDS);
    }

    public static boolean complete(String messageId, String detail) {
        PendingAck ack = PENDING.remove(messageId);
        if (ack == null) {
            return false;
        }
        ack.success(detail);
        return true;
    }

    private static final class PendingAck {
        private final CommandEnvelope cmd;
        private final String protocolId;
        private final DownlinkResultPublisher publisher;
        private final OmniMetrics metrics;

        PendingAck(CommandEnvelope cmd, String protocolId,
                   DownlinkResultPublisher publisher, OmniMetrics metrics) {
            this.cmd = cmd;
            this.protocolId = protocolId;
            this.publisher = publisher;
            this.metrics = metrics;
        }

        void success(String detail) {
            publisher.publish(DownlinkResult.of(cmd.getMessageId(), cmd.getDeviceId(), DownlinkStatus.SUCCESS, detail));
            metrics.downlink(protocolId, "SUCCESS");
        }

        void timeout() {
            publisher.publish(DownlinkResult.of(cmd.getMessageId(), cmd.getDeviceId(), DownlinkStatus.TIMEOUT, "device_ack_timeout"));
            metrics.downlink(protocolId, "TIMEOUT");
        }
    }
}
