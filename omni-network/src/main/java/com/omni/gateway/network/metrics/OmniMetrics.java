package com.omni.gateway.network.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

public class OmniMetrics {

    private final MeterRegistry registry;
    private final AtomicInteger activeConnections = new AtomicInteger();

    public MeterRegistry registry() {
        return registry;
    }

    public OmniMetrics(MeterRegistry registry) {
        this.registry = registry;
        Gauge.builder("omni_connections_active", activeConnections, AtomicInteger::get)
                .description("Active TCP connections")
                .register(registry);
    }

    public void connectionOpened() {
        activeConnections.incrementAndGet();
        counter("omni_connections_total", "status", "accepted").increment();
    }

    public void connectionClosed() {
        activeConnections.decrementAndGet();
        counter("omni_connections_total", "status", "closed").increment();
    }

    public void sniffResult(int port, String protocol, String result, long durationMs) {
        Timer.builder("omni_sniff_duration_seconds")
                .tag("port", String.valueOf(port))
                .tag("protocol", protocol == null ? "none" : protocol)
                .tag("result", result)
                .register(registry)
                .record(java.time.Duration.ofMillis(durationMs));
        if (!"ok".equals(result)) {
            counter("omni_sniff_failures_total", "port", String.valueOf(port), "reason", result).increment();
        }
    }

    public void authFailure(String protocol, String reason) {
        counter("omni_auth_failures_total", "protocol", protocol, "reason", reason).increment();
    }

    public void uplink(String protocol, String status) {
        counter("omni_messages_uplink_total", "protocol", protocol, "status", status).increment();
    }

    public void parseError(String protocol) {
        counter("omni_message_parse_errors_total", "protocol", protocol).increment();
    }

    public void downlink(String protocol, String status) {
        counter("omni_downlink_total", "protocol", protocol, "status", status).increment();
    }

    public void downlinkSkipNotLocal() {
        counter("omni_downlink_skip_not_local_total").increment();
    }

    public void connectionRejected(String reason) {
        counter("omni_connection_rejected_total", "reason", reason).increment();
    }

    public void registerBackpressure(Supplier<Number> valueSupplier) {
        Gauge.builder("omni_kafka_publish_backpressure", valueSupplier)
                .register(registry);
    }

    private Counter counter(String name, String... tags) {
        Counter.Builder b = Counter.builder(name);
        for (int i = 0; i < tags.length; i += 2) {
            b.tag(tags[i], tags[i + 1]);
        }
        return b.register(registry);
    }
}
