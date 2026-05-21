package com.omni.gateway.network.sniff;

import com.omni.gateway.core.ChannelAttributes;
import com.omni.gateway.core.config.PortListenerConfig;
import com.omni.gateway.core.config.SniffConfig;
import com.omni.gateway.core.plugin.PluginRegistry;
import com.omni.gateway.core.plugin.ProtocolPlugin;
import com.omni.gateway.core.session.DeviceSession;
import com.omni.gateway.network.metrics.OmniMetrics;
import com.omni.gateway.network.observability.GatewayTracing;
import io.micrometer.tracing.Tracer;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class SniffHandler extends ChannelInboundHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(SniffHandler.class);

    private final int port;
    private final Supplier<PortListenerConfig> listenerConfigSupplier;
    private final PluginRegistry pluginRegistry;
    private final PipelineBinder pipelineBinder;
    private final OmniMetrics metrics;
    private final Tracer tracer;

    private ByteBuf cumulation;
    private ScheduledFuture<?> timeoutTask;
    private long sniffStartMs;

    public SniffHandler(int port,
                        Supplier<PortListenerConfig> listenerConfigSupplier,
                        PluginRegistry pluginRegistry,
                        PipelineBinder pipelineBinder,
                        OmniMetrics metrics,
                        Tracer tracer) {
        this.port = port;
        this.listenerConfigSupplier = listenerConfigSupplier;
        this.pluginRegistry = pluginRegistry;
        this.pipelineBinder = pipelineBinder;
        this.metrics = metrics;
        this.tracer = tracer;
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        sniffStartMs = System.currentTimeMillis();
        SniffConfig sniff = listenerConfigSupplier.get().getSniff();
        timeoutTask = ctx.executor().schedule(
                () -> failAndClose(ctx, "timeout"),
                sniff.getTimeoutMs(),
                TimeUnit.MILLISECONDS);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        ByteBuf in = (ByteBuf) msg;
        try {
            if (cumulation == null) {
                cumulation = ctx.alloc().buffer();
            }
            cumulation.writeBytes(in);

            PortListenerConfig listener = listenerConfigSupplier.get();
            SniffConfig sniff = listener.getSniff();
            if (cumulation.readableBytes() > sniff.getMaxBytes()) {
                failAndClose(ctx, "max_bytes");
                return;
            }

            List<ProtocolPlugin> candidates = pluginRegistry.resolveForPort(
                    port, listener.getPluginPriority());

            for (ProtocolPlugin plugin : candidates) {
                if (cumulation.readableBytes() < plugin.minProbeLength()) {
                    return;
                }
                ByteBuf dup = cumulation.duplicate();
                if (plugin.detect(dup)) {
                    cancelTimeout();
                    long duration = System.currentTimeMillis() - sniffStartMs;
                    metrics.sniffResult(port, plugin.pluginId(), "ok", duration);
                    bindProtocol(ctx, plugin);
                    return;
                }
            }

            int minRequired = candidates.stream().mapToInt(ProtocolPlugin::minProbeLength).min().orElse(2);
            if (cumulation.readableBytes() >= sniff.getMaxBytes()
                    && cumulation.readableBytes() >= minRequired) {
                failAndClose(ctx, "no_match");
            }
        } finally {
            in.release();
        }
    }

    private void bindProtocol(ChannelHandlerContext ctx, ProtocolPlugin plugin) {
        GatewayTracing.run(tracer, "protocol.sniff", () -> {
            DeviceSession session = ctx.channel().attr(ChannelAttributes.SESSION).get();
            session.setProtocolId(plugin.pluginId());
            ctx.channel().attr(ChannelAttributes.BOUND_PROTOCOL).set(plugin.pluginId());

            ByteBuf remaining = cumulation;
            cumulation = null;

            pipelineBinder.bind(ctx, plugin, session);
            ctx.pipeline().remove(this);

            if (remaining != null && remaining.isReadable()) {
                ctx.pipeline().fireChannelRead(remaining.retain());
                remaining.release();
            } else if (remaining != null) {
                remaining.release();
            }
        });
    }

    private void failAndClose(ChannelHandlerContext ctx, String reason) {
        cancelTimeout();
        long duration = System.currentTimeMillis() - sniffStartMs;
        metrics.sniffResult(port, "none", reason, duration);
        if (cumulation != null && cumulation.readableBytes() > 0) {
            log.warn("Sniff failed port={} reason={} remote={} head={}",
                    port, reason, ctx.channel().remoteAddress(),
                    ByteBufUtil.hexDump(cumulation, 0, Math.min(16, cumulation.readableBytes())));
        } else {
            log.warn("Sniff failed port={} reason={} remote={}", port, reason, ctx.channel().remoteAddress());
        }
        releaseCumulation();
        ctx.close();
    }

    private void cancelTimeout() {
        if (timeoutTask != null) {
            timeoutTask.cancel(false);
            timeoutTask = null;
        }
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {
        cancelTimeout();
        releaseCumulation();
    }

    private void releaseCumulation() {
        if (cumulation != null) {
            cumulation.release();
            cumulation = null;
        }
    }
}
