package com.omni.gateway.bootstrap.config;

import com.omni.gateway.bootstrap.OmniGatewayProperties;
import com.omni.gateway.core.backpressure.BackpressureController;
import com.omni.gateway.core.config.GatewayConfigSnapshot;
import com.omni.gateway.core.lifecycle.DeviceLifecyclePublisher;
import com.omni.gateway.core.plugin.PluginRegistry;
import com.omni.gateway.core.plugin.ProtocolPlugin;
import com.omni.gateway.core.session.SessionRegistry;
import com.omni.gateway.network.backpressure.DefaultBackpressureController;
import com.omni.gateway.network.downlink.DownlinkDispatcher;
import com.omni.gateway.network.downlink.DeviceSerialExecutor;
import com.omni.gateway.network.metrics.OmniMetrics;
import com.omni.gateway.network.server.PortListenerManager;
import com.omni.gateway.network.session.InMemorySessionRegistry;
import com.omni.gateway.protocol.jt808.Jt808Plugin;
import com.omni.gateway.protocol.simpleframe.SimpleFramePlugin;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Configuration
public class GatewayBeanConfig {

    @Bean
    public AtomicReference<GatewayConfigSnapshot> gatewayConfigRef(OmniGatewayProperties properties) {
        return new AtomicReference<>(properties.toSnapshot());
    }

    @Bean
    public OmniMetrics omniMetrics(MeterRegistry meterRegistry) {
        return new OmniMetrics(meterRegistry);
    }

    @Bean
    public BackpressureController backpressureController(AtomicReference<GatewayConfigSnapshot> configRef,
                                                       OmniMetrics metrics) {
        return new DefaultBackpressureController(configRef::get, metrics);
    }

    @Bean
    public SimpleFramePlugin simpleFramePlugin(OmniMetrics metrics) {
        return new SimpleFramePlugin(metrics);
    }

    @Bean
    public Jt808Plugin jt808Plugin(OmniMetrics metrics) {
        return new Jt808Plugin(metrics);
    }

    @Bean
    public PluginRegistry pluginRegistry(List<ProtocolPlugin> plugins) {
        return new DefaultPluginRegistry(plugins);
    }

    @Bean
    public SessionRegistry sessionRegistry(OmniGatewayProperties properties) {
        return new InMemorySessionRegistry(properties.getGateway().isKickOldOnReauth());
    }

    @Bean
    public DeviceSerialExecutor deviceSerialExecutor() {
        return new DeviceSerialExecutor();
    }

    @Bean
    public DownlinkDispatcher downlinkDispatcher(PluginRegistry pluginRegistry,
                                                 com.omni.gateway.bootstrap.kafka.KafkaDownlinkResultPublisher resultPublisher,
                                                 OmniMetrics metrics) {
        return new DownlinkDispatcher(pluginRegistry, resultPublisher, metrics);
    }

    @Bean
    public PortListenerManager portListenerManager(AtomicReference<GatewayConfigSnapshot> configRef,
                                                   PluginRegistry pluginRegistry,
                                                   SessionRegistry sessionRegistry,
                                                   com.omni.gateway.bootstrap.kafka.KafkaUplinkPublisher uplinkPublisher,
                                                   OmniMetrics metrics,
                                                   OmniGatewayProperties properties,
                                                   BackpressureController backpressure,
                                                   DeviceLifecyclePublisher lifecyclePublisher) {
        return new PortListenerManager(
                configRef, pluginRegistry, sessionRegistry,
                uplinkPublisher, metrics, properties.getNodeId(),
                backpressure, lifecyclePublisher);
    }
}
