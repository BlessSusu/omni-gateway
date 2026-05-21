package com.omni.gateway.network.backpressure;

import com.omni.gateway.core.ChannelAttributes;
import com.omni.gateway.core.backpressure.BackpressureController;
import com.omni.gateway.core.config.GatewayConfigSnapshot;
import com.omni.gateway.network.metrics.OmniMetrics;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class DefaultBackpressureController implements BackpressureController {

    private static final Logger log = LoggerFactory.getLogger(DefaultBackpressureController.class);

    private final Supplier<GatewayConfigSnapshot> configSupplier;
    private final Set<Channel> channels = ConcurrentHashMap.newKeySet();
    private final AtomicInteger globalPending = new AtomicInteger();
    private final AtomicBoolean kafkaDegraded = new AtomicBoolean();
    private final AtomicBoolean underPressure = new AtomicBoolean();
    private final AtomicInteger backpressureGauge = new AtomicInteger(0);

    public DefaultBackpressureController(Supplier<GatewayConfigSnapshot> configSupplier, OmniMetrics metrics) {
        this.configSupplier = configSupplier;
        metrics.registerBackpressure(backpressureGauge::get);
    }

    @Override
    public void registerChannel(Channel channel) {
        channels.add(channel);
        channel.attr(ChannelAttributes.UPLINK_PENDING).set(0);
    }

    @Override
    public void unregisterChannel(Channel channel) {
        channels.remove(channel);
        int ch = channelPending(channel);
        if (ch > 0) {
            globalPending.addAndGet(-ch);
        }
        channel.attr(ChannelAttributes.UPLINK_PENDING).set(0);
        resumeRead(channel);
        reevaluateGlobal();
    }

    @Override
    public void beforeUplinkPublish(Channel channel) {
        int ch = incrementChannelPending(channel);
        int global = globalPending.incrementAndGet();
        GatewayConfigSnapshot cfg = configSupplier.get();
        if (kafkaDegraded.get()
                || global > cfg.getMaxGlobalUplinkPending()
                || ch > cfg.getMaxPerChannelUplinkPending()) {
            pauseRead(channel);
            setPressure(true);
        }
    }

    @Override
    public void afterUplinkPublish(Channel channel, boolean success) {
        decrementChannelPending(channel);
        globalPending.decrementAndGet();
        if (!success) {
            onKafkaDegraded();
        } else if (!kafkaDegraded.get()) {
            maybeResumeChannel(channel);
            reevaluateGlobal();
        }
    }

    @Override
    public void onKafkaDegraded() {
        kafkaDegraded.set(true);
        setPressure(true);
        channels.forEach(this::pauseRead);
        log.warn("Kafka degraded, backpressure applied to {} channels", channels.size());
    }

    @Override
    public void onKafkaRecovered() {
        kafkaDegraded.set(false);
        reevaluateGlobal();
        log.info("Kafka recovered, reevaluating backpressure");
    }

    @Override
    public boolean isUnderPressure() {
        return underPressure.get();
    }

    private void reevaluateGlobal() {
        GatewayConfigSnapshot cfg = configSupplier.get();
        if (kafkaDegraded.get() || globalPending.get() > cfg.getMaxGlobalUplinkPending()) {
            setPressure(true);
            return;
        }
        setPressure(false);
        channels.forEach(ch -> {
            if (channelPending(ch) <= cfg.getMaxPerChannelUplinkPending()) {
                resumeRead(ch);
            }
        });
    }

    private void maybeResumeChannel(Channel channel) {
        GatewayConfigSnapshot cfg = configSupplier.get();
        if (!kafkaDegraded.get()
                && channelPending(channel) <= cfg.getMaxPerChannelUplinkPending()
                && globalPending.get() <= cfg.getMaxGlobalUplinkPending()) {
            resumeRead(channel);
        }
    }

    private void setPressure(boolean on) {
        underPressure.set(on);
        backpressureGauge.set(on ? 1 : 0);
    }

    private void pauseRead(Channel channel) {
        if (channel.isActive() && channel.config().isAutoRead()) {
            channel.config().setAutoRead(false);
        }
    }

    private void resumeRead(Channel channel) {
        if (channel.isActive() && !channel.config().isAutoRead()) {
            channel.config().setAutoRead(true);
        }
    }

    private int incrementChannelPending(Channel channel) {
        int n = channelPending(channel) + 1;
        channel.attr(ChannelAttributes.UPLINK_PENDING).set(n);
        return n;
    }

    private void decrementChannelPending(Channel channel) {
        int n = Math.max(0, channelPending(channel) - 1);
        channel.attr(ChannelAttributes.UPLINK_PENDING).set(n);
    }

    private int channelPending(Channel channel) {
        Integer v = channel.attr(ChannelAttributes.UPLINK_PENDING).get();
        return v == null ? 0 : v;
    }
}
