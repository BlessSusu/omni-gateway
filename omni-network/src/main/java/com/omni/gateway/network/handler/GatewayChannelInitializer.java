package com.omni.gateway.network.handler;

import com.omni.gateway.core.ChannelAttributes;
import com.omni.gateway.core.backpressure.BackpressureController;
import com.omni.gateway.core.config.GatewayConfigSnapshot;
import com.omni.gateway.core.config.PortListenerConfig;
import com.omni.gateway.core.lifecycle.DeviceLifecyclePublisher;
import com.omni.gateway.core.plugin.PluginRegistry;
import com.omni.gateway.core.session.DeviceSession;
import com.omni.gateway.core.session.DistributedSessionIndex;
import com.omni.gateway.core.session.SessionRegistry;
import com.omni.gateway.core.uplink.UplinkPublisher;
import com.omni.gateway.network.drain.NodeDrainService;
import com.omni.gateway.network.metrics.OmniMetrics;
import com.omni.gateway.network.security.ConnectionRateLimitHandler;
import com.omni.gateway.network.security.IpAccessHandler;
import com.omni.gateway.network.logging.ConfigurableProtocolTrafficLog;
import com.omni.gateway.network.server.PortListenerManager;
import com.omni.gateway.network.sniff.PipelineBinder;
import com.omni.gateway.network.sniff.SniffHandler;
import com.omni.gateway.network.ssl.SslContextFactory;
import io.micrometer.tracing.Tracer;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.handler.timeout.IdleStateHandler;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

public class GatewayChannelInitializer extends ChannelInitializer<SocketChannel> {

    private final int port;
    private final boolean tls;
    private final AtomicReference<GatewayConfigSnapshot> configRef;
    private final PluginRegistry pluginRegistry;
    private final SessionRegistry sessionRegistry;
    private final DistributedSessionIndex distributedSessionIndex;
    private final long sessionIndexTtlSec;
    private final UplinkPublisher uplinkPublisher;
    private final OmniMetrics metrics;
    private final String gatewayNodeId;
    private final BackpressureController backpressure;
    private final DeviceLifecyclePublisher lifecyclePublisher;
    private final IpAccessHandler ipAccessHandler;
    private final ConnectionRateLimitHandler rateLimitHandler;
    private final ConnectionLifecycleHandler lifecycleHandler;
    private final UplinkDispatchHandler uplinkDispatchHandler;
    private final DrainRejectHandler drainRejectHandler;
    private final SslContextFactory sslContextFactory;
    private final Tracer tracer;
    private final PipelineBinder pipelineBinder = new PipelineBinder();

    public GatewayChannelInitializer(int port,
                                     boolean tls,
                                     AtomicReference<GatewayConfigSnapshot> configRef,
                                     PluginRegistry pluginRegistry,
                                     SessionRegistry sessionRegistry,
                                     DistributedSessionIndex distributedSessionIndex,
                                     long sessionIndexTtlSec,
                                     UplinkPublisher uplinkPublisher,
                                     OmniMetrics metrics,
                                     String gatewayNodeId,
                                     BackpressureController backpressure,
                                     DeviceLifecyclePublisher lifecyclePublisher,
                                     ConfigurableProtocolTrafficLog protocolTrafficLog,
                                     SslContextFactory sslContextFactory,
                                     NodeDrainService nodeDrainService,
                                     PortListenerManager portListenerManager,
                                     Tracer tracer) {
        this.port = port;
        this.tls = tls;
        this.configRef = configRef;
        this.pluginRegistry = pluginRegistry;
        this.sessionRegistry = sessionRegistry;
        this.distributedSessionIndex = distributedSessionIndex;
        this.sessionIndexTtlSec = sessionIndexTtlSec;
        this.uplinkPublisher = uplinkPublisher;
        this.metrics = metrics;
        this.gatewayNodeId = gatewayNodeId;
        this.backpressure = backpressure;
        this.lifecyclePublisher = lifecyclePublisher;
        this.sslContextFactory = sslContextFactory;
        this.tracer = tracer;
        Supplier<GatewayConfigSnapshot> configSupplier = configRef::get;
        this.ipAccessHandler = new IpAccessHandler(configSupplier);
        this.rateLimitHandler = new ConnectionRateLimitHandler(configSupplier, metrics);
        this.lifecycleHandler = new ConnectionLifecycleHandler(
                sessionRegistry, distributedSessionIndex, gatewayNodeId,
                metrics, backpressure, lifecyclePublisher);
        this.uplinkDispatchHandler = new UplinkDispatchHandler(
                pluginRegistry, sessionRegistry, distributedSessionIndex, sessionIndexTtlSec,
                uplinkPublisher, metrics, gatewayNodeId, backpressure, lifecyclePublisher, protocolTrafficLog, tracer);
        this.drainRejectHandler = new DrainRejectHandler(nodeDrainService, portListenerManager);
    }

    @Override
    protected void initChannel(SocketChannel ch) {
        GatewayConfigSnapshot config = configRef.get();
        config.listenerForPort(port);
        Supplier<PortListenerConfig> listenerSupplier = () -> configRef.get().listenerForPort(port);

        DeviceSession session = new DeviceSession(ch, port);
        ch.attr(ChannelAttributes.SESSION).set(session);
        ch.attr(ChannelAttributes.LOCAL_PORT).set(port);
        ch.attr(ChannelAttributes.AUTHENTICATED).set(false);
        ch.attr(ChannelAttributes.TRACE_SESSION_ID).set(UUID.randomUUID().toString().replace("-", ""));

        var pipeline = ch.pipeline();
        pipeline.addLast("drain-reject", drainRejectHandler);
        if (tls && sslContextFactory != null && sslContextFactory.isReady()) {
            pipeline.addLast("ssl", sslContextFactory.contextForNewConnection().newHandler(ch.alloc()));
        }
        pipeline.addLast("ip-access", ipAccessHandler);
        pipeline.addLast("rate-limit", rateLimitHandler);
        pipeline.addLast("idle", new IdleStateHandler(config.getReaderIdleSeconds(), 0, 0));
        pipeline.addLast("idle-handler", new IdleConnectionHandler());
        pipeline.addLast("lifecycle", lifecycleHandler);
        pipeline.addLast("sniff", new SniffHandler(port, listenerSupplier, pluginRegistry, pipelineBinder, metrics, tracer));
        pipeline.addLast("uplink", uplinkDispatchHandler);
    }

    private class IdleConnectionHandler extends io.netty.channel.ChannelInboundHandlerAdapter {
        @Override
        public void userEventTriggered(ChannelHandlerContext ctx, Object evt) {
            if (evt instanceof IdleStateEvent) {
                ctx.close();
            } else {
                ctx.fireUserEventTriggered(evt);
            }
        }
    }
}
