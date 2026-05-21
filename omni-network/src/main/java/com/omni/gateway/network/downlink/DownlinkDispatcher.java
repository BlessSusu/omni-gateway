package com.omni.gateway.network.downlink;

import com.omni.gateway.core.model.CommandEnvelope;
import com.omni.gateway.core.model.DownlinkResult;
import com.omni.gateway.core.model.DownlinkStatus;
import com.omni.gateway.core.plugin.ProtocolPlugin;
import com.omni.gateway.core.plugin.PluginRegistry;
import com.omni.gateway.core.session.DeviceSession;
import com.omni.gateway.core.downlink.DownlinkResultPublisher;
import com.omni.gateway.network.logging.ConfigurableProtocolTrafficLog;
import com.omni.gateway.network.metrics.OmniMetrics;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class DownlinkDispatcher {

    private static final Logger log = LoggerFactory.getLogger(DownlinkDispatcher.class);

    private final PluginRegistry pluginRegistry;
    private final DownlinkResultPublisher resultPublisher;
    private final OmniMetrics metrics;
    private final ConcurrentHashMap<String, Long> processedMessages = new ConcurrentHashMap<>();
    private final ConfigurableProtocolTrafficLog protocolTrafficLog;

    public DownlinkDispatcher(PluginRegistry pluginRegistry,
                              DownlinkResultPublisher resultPublisher,
                              OmniMetrics metrics,
                              ConfigurableProtocolTrafficLog protocolTrafficLog) {
        this.pluginRegistry = pluginRegistry;
        this.resultPublisher = resultPublisher;
        this.metrics = metrics;
        this.protocolTrafficLog = protocolTrafficLog;
    }

    public void dispatch(DeviceSession session, CommandEnvelope cmd) {
        String messageId = cmd.getMessageId();
        if (messageId != null && processedMessages.putIfAbsent(messageId, System.currentTimeMillis()) != null) {
            resultPublisher.publish(DownlinkResult.of(messageId, cmd.getDeviceId(), DownlinkStatus.SUCCESS, "duplicate"));
            return;
        }

        String protocolId = session.getProtocolId();
        if (!cmd.getProtocol().equals(protocolId)) {
            publish(cmd, DownlinkStatus.REJECTED, "protocol_mismatch");
            return;
        }

        if (!session.isActive()) {
            publish(cmd, DownlinkStatus.OFFLINE, "channel_inactive");
            return;
        }

        ProtocolPlugin plugin = pluginRegistry.get(protocolId).orElse(null);
        if (plugin == null) {
            publish(cmd, DownlinkStatus.REJECTED, "unknown_protocol");
            return;
        }

        Optional<ByteBuf> encoded = plugin.encodeDownlink(session, cmd);
        if (encoded.isEmpty()) {
            publish(cmd, DownlinkStatus.ENCODE_ERROR, "encode_failed");
            metrics.downlink(protocolId, "ENCODE_ERROR");
            return;
        }

        Channel channel = session.getChannel();
        ByteBuf buf = encoded.get();
        channel.eventLoop().execute(() -> {
            try {
                if (protocolTrafficLog != null && protocolTrafficLog.isEnabled()) {
                    protocolTrafficLog.logSend(session, buf);
                }
                channel.writeAndFlush(buf);
                if (plugin.downlinkAckMode() == ProtocolPlugin.DownlinkAckMode.FIRE_AND_FORGET) {
                    publish(cmd, DownlinkStatus.SUCCESS, "sent");
                    metrics.downlink(protocolId, "SUCCESS");
                } else {
                    PendingAckRegistry.register(messageId, cmd, protocolId, resultPublisher, metrics);
                }
            } catch (Exception e) {
                buf.release();
                publish(cmd, DownlinkStatus.REJECTED, e.getMessage());
                metrics.downlink(protocolId, "REJECTED");
                log.error("Downlink write failed deviceId={}", cmd.getDeviceId(), e);
            }
        });
    }

    private void publish(CommandEnvelope cmd, DownlinkStatus status, String detail) {
        resultPublisher.publish(DownlinkResult.of(cmd.getMessageId(), cmd.getDeviceId(), status, detail));
        metrics.downlink(cmd.getProtocol(), status.name());
    }
}
