package com.omni.gateway.network.handler;

import com.omni.gateway.core.ChannelAttributes;
import com.omni.gateway.core.auth.AuthResult;
import com.omni.gateway.core.logging.OmniMdc;
import com.omni.gateway.core.backpressure.BackpressureController;
import com.omni.gateway.core.lifecycle.DeviceLifecyclePublisher;
import com.omni.gateway.core.model.ThingModel;
import com.omni.gateway.core.plugin.ProtocolPlugin;
import com.omni.gateway.core.plugin.PluginRegistry;
import com.omni.gateway.core.session.DeviceSession;
import com.omni.gateway.core.session.SessionRegistry;
import com.omni.gateway.core.uplink.UplinkPublisher;
import com.omni.gateway.network.metrics.OmniMetrics;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

@ChannelHandler.Sharable
public class UplinkDispatchHandler extends SimpleChannelInboundHandler<Object> {

    private static final Logger log = LoggerFactory.getLogger(UplinkDispatchHandler.class);

    private final PluginRegistry pluginRegistry;
    private final SessionRegistry sessionRegistry;
    private final UplinkPublisher uplinkPublisher;
    private final OmniMetrics metrics;
    private final String gatewayNodeId;
    private final BackpressureController backpressure;
    private final DeviceLifecyclePublisher lifecyclePublisher;

    public UplinkDispatchHandler(PluginRegistry pluginRegistry,
                                 SessionRegistry sessionRegistry,
                                 UplinkPublisher uplinkPublisher,
                                 OmniMetrics metrics,
                                 String gatewayNodeId,
                                 BackpressureController backpressure,
                                 DeviceLifecyclePublisher lifecyclePublisher) {
        this.pluginRegistry = pluginRegistry;
        this.sessionRegistry = sessionRegistry;
        this.uplinkPublisher = uplinkPublisher;
        this.metrics = metrics;
        this.gatewayNodeId = gatewayNodeId;
        this.backpressure = backpressure;
        this.lifecyclePublisher = lifecyclePublisher;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) {
        DeviceSession session = ctx.channel().attr(ChannelAttributes.SESSION).get();
        String protocolId = ctx.channel().attr(ChannelAttributes.BOUND_PROTOCOL).get();
        if (session == null || protocolId == null) {
            return;
        }

        ProtocolPlugin plugin = pluginRegistry.get(protocolId).orElse(null);
        if (plugin == null) {
            return;
        }

        session.touch();
        Boolean authenticated = ctx.channel().attr(ChannelAttributes.AUTHENTICATED).get();
        if (authenticated == null || !authenticated) {
            AuthResult auth = plugin.authenticate(session, msg);
            if (auth == AuthResult.PENDING) {
                return;
            }
            if (auth == AuthResult.FAIL) {
                metrics.authFailure(protocolId, "rejected");
                log.warn("Auth failed device channel={}", session.getChannelId());
                ctx.close();
                return;
            }
            if (session.getDeviceId() == null) {
                metrics.authFailure(protocolId, "no_device_id");
                ctx.close();
                return;
            }
            ctx.channel().attr(ChannelAttributes.AUTHENTICATED).set(true);
            sessionRegistry.bind(session.getDeviceId(), session);
            backpressure.registerChannel(ctx.channel());
            lifecyclePublisher.publishOnline(session);
            OmniMdc.bindDevice(session.getDeviceId(), protocolId, session.getChannelId());
            OmniMdc.event("device_auth");
            log.info("Device authenticated");
            OmniMdc.clear();

            plugin.buildAuthSuccessResponse(session).ifPresent(ctx::writeAndFlush);
            return;
        }

        if (plugin.matchDownlinkAck(session, msg)) {
            // Process device ACK (e.g. PendingAckRegistry) without uplink publish.
            plugin.toThingModel(session, msg);
            return;
        }

        Optional<ThingModel> thingOpt = plugin.toThingModel(session, msg);
        if (thingOpt.isEmpty()) {
            return;
        }

        ThingModel thing = thingOpt.get();
        thing.setGatewayNodeId(gatewayNodeId);
        OmniMdc.bindDevice(session.getDeviceId(), protocolId, session.getChannelId());
        OmniMdc.event("uplink_publish");
        backpressure.beforeUplinkPublish(ctx.channel());
        uplinkPublisher.publish(thing)
                .whenComplete((v, ex) -> {
                    boolean ok = ex == null;
                    backpressure.afterUplinkPublish(ctx.channel(), ok);
                    if (!ok) {
                        metrics.uplink(protocolId, "error");
                        log.error("Uplink publish failed", ex);
                    } else {
                        metrics.uplink(protocolId, "ok");
                    }
                    OmniMdc.clear();
                });
    }
}
