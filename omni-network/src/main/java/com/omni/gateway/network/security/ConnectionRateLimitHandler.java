package com.omni.gateway.network.security;

import com.omni.gateway.core.config.GatewayConfigSnapshot;
import com.omni.gateway.network.metrics.OmniMetrics;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

@ChannelHandler.Sharable
public class ConnectionRateLimitHandler extends ChannelInboundHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(ConnectionRateLimitHandler.class);

    private final Supplier<GatewayConfigSnapshot> configSupplier;
    private final OmniMetrics metrics;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public ConnectionRateLimitHandler(Supplier<GatewayConfigSnapshot> configSupplier, OmniMetrics metrics) {
        this.configSupplier = configSupplier;
        this.metrics = metrics;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        String ip = IpAccessHandler.remoteIp(ctx);
        int limit = configSupplier.get().getSecurity().getConnectionRatePerIp();
        if (limit <= 0) {
            ctx.fireChannelActive();
            return;
        }
        long now = System.currentTimeMillis();
        Window w = windows.compute(ip, (k, v) -> {
            if (v == null || now - v.epochMs >= 1000) {
                return new Window(now, new AtomicInteger(0));
            }
            return v;
        });
        if (w.count.incrementAndGet() > limit) {
            log.warn("Connection rate limited ip={}", ip);
            metrics.connectionRejected("rate_limit");
            ctx.close();
            return;
        }
        ctx.fireChannelActive();
    }

    private static final class Window {
        final long epochMs;
        final AtomicInteger count;

        Window(long epochMs, AtomicInteger count) {
            this.epochMs = epochMs;
            this.count = count;
        }
    }
}
