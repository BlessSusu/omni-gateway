package com.omni.gateway.bootstrap.config;

import com.omni.gateway.bootstrap.OmniGatewayProperties;
import com.omni.gateway.core.backpressure.BackpressureController;
import com.omni.gateway.core.config.GatewayConfigSnapshot;
import com.omni.gateway.core.config.TlsConfig;
import com.omni.gateway.core.lifecycle.DeviceLifecyclePublisher;
import com.omni.gateway.core.plugin.PluginRegistry;
import com.omni.gateway.core.plugin.ProtocolPlugin;
import com.omni.gateway.core.session.DistributedSessionIndex;
import com.omni.gateway.core.session.SessionRegistry;
import com.omni.gateway.network.backpressure.DefaultBackpressureController;
import com.omni.gateway.core.logging.ProtocolTrafficLog;
import com.omni.gateway.network.downlink.DownlinkDispatcher;
import com.omni.gateway.network.downlink.DeviceSerialExecutor;
import com.omni.gateway.network.drain.NodeDrainService;
import com.omni.gateway.network.logging.ConfigurableProtocolTrafficLog;
import com.omni.gateway.network.metrics.OmniMetrics;
import com.omni.gateway.network.server.PortListenerManager;
import com.omni.gateway.network.session.InMemorySessionRegistry;
import com.omni.gateway.network.ssl.SslContextFactory;
import com.omni.gateway.protocol.gb28181.Gb28181Plugin;
import com.omni.gateway.protocol.jt808.Jt808Plugin;
import com.omni.gateway.protocol.simpleframe.SimpleFramePlugin;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Tracer;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Configuration
public class GatewayBeanConfig {

    private static final Logger log = LoggerFactory.getLogger(GatewayBeanConfig.class);

    @Bean
    public AtomicReference<GatewayConfigSnapshot> gatewayConfigRef(OmniGatewayProperties properties) {
        return new AtomicReference<>(properties.toSnapshot());
    }

    @Bean
    public OmniMetrics omniMetrics(MeterRegistry meterRegistry) {
        return new OmniMetrics(meterRegistry);
    }

    @Bean
    public ConfigurableProtocolTrafficLog protocolTrafficLog(OmniGatewayProperties properties) {
        return new ConfigurableProtocolTrafficLog(() -> properties.getLogging().isProtocolHexEnabled());
    }

    @Bean
    public BackpressureController backpressureController(AtomicReference<GatewayConfigSnapshot> configRef,
                                                       OmniMetrics metrics) {
        return new DefaultBackpressureController(configRef::get, metrics);
    }

    @Bean
    public SimpleFramePlugin simpleFramePlugin(OmniMetrics metrics, ProtocolTrafficLog protocolTrafficLog) {
        return new SimpleFramePlugin(metrics, protocolTrafficLog);
    }

    @Bean
    public Jt808Plugin jt808Plugin(OmniMetrics metrics, ProtocolTrafficLog protocolTrafficLog) {
        return new Jt808Plugin(metrics, protocolTrafficLog);
    }

    @Bean
    public Gb28181Plugin gb28181Plugin(OmniMetrics metrics, ProtocolTrafficLog protocolTrafficLog) {
        return new Gb28181Plugin(metrics, protocolTrafficLog);
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
                                                 OmniMetrics metrics,
                                                 ConfigurableProtocolTrafficLog protocolTrafficLog,
                                                 Tracer tracer) {
        return new DownlinkDispatcher(pluginRegistry, resultPublisher, metrics, protocolTrafficLog, tracer);
    }

    @Bean
    public SslContextFactory sslContextFactory() {
        return new SslContextFactory();
    }

    @Bean
    public PortListenerManager portListenerManager(AtomicReference<GatewayConfigSnapshot> configRef,
                                                   PluginRegistry pluginRegistry,
                                                   SessionRegistry sessionRegistry,
                                                   DistributedSessionIndex distributedSessionIndex,
                                                   OmniGatewayProperties properties,
                                                   com.omni.gateway.bootstrap.kafka.KafkaUplinkPublisher uplinkPublisher,
                                                   OmniMetrics metrics,
                                                   BackpressureController backpressure,
                                                   DeviceLifecyclePublisher lifecyclePublisher,
                                                   ConfigurableProtocolTrafficLog protocolTrafficLog,
                                                   SslContextFactory sslContextFactory,
                                                   @Lazy NodeDrainService nodeDrainService,
                                                   Tracer tracer) {
        return new PortListenerManager(
                configRef, pluginRegistry, sessionRegistry, distributedSessionIndex,
                properties.resolveSessionRedisTtlSeconds(),
                uplinkPublisher, metrics, properties.getNodeId(),
                backpressure, lifecyclePublisher, protocolTrafficLog,
                sslContextFactory, nodeDrainService, tracer);
    }

    @Bean
    public NodeDrainService nodeDrainService(@Lazy PortListenerManager portListenerManager,
                                             SessionRegistry sessionRegistry) {
        return new NodeDrainService(portListenerManager, sessionRegistry);
    }

    @Bean
    public TlsBootstrap tlsBootstrap(OmniGatewayProperties properties,
                                       SslContextFactory sslContextFactory,
                                       AtomicReference<GatewayConfigSnapshot> configRef) {
        return new TlsBootstrap(properties, sslContextFactory, configRef);
    }

    @Bean
    public NodeDrainBootstrap nodeDrainBootstrap(NodeDrainService nodeDrainService,
                                                 KafkaListenerEndpointRegistry kafkaRegistry,
                                                 OmniGatewayProperties properties) {
        return new NodeDrainBootstrap(nodeDrainService, kafkaRegistry, properties);
    }

    public static class TlsBootstrap {
        private final OmniGatewayProperties properties;
        private final SslContextFactory sslContextFactory;
        private final AtomicReference<GatewayConfigSnapshot> configRef;

        public TlsBootstrap(OmniGatewayProperties properties,
                              SslContextFactory sslContextFactory,
                              AtomicReference<GatewayConfigSnapshot> configRef) {
            this.properties = properties;
            this.sslContextFactory = sslContextFactory;
            this.configRef = configRef;
        }

        @PostConstruct
        public void init() throws Exception {
            TlsConfig tls = configRef.get().getTls();
            if (tls != null && tls.isEnabled()) {
                sslContextFactory.reload(tls);
                log.info("TLS context loaded");
            }
        }
    }

    public static class NodeDrainBootstrap {
        private final NodeDrainService nodeDrainService;
        private final KafkaListenerEndpointRegistry kafkaRegistry;
        private final OmniGatewayProperties properties;

        public NodeDrainBootstrap(NodeDrainService nodeDrainService,
                                  KafkaListenerEndpointRegistry kafkaRegistry,
                                  OmniGatewayProperties properties) {
            this.nodeDrainService = nodeDrainService;
            this.kafkaRegistry = kafkaRegistry;
            this.properties = properties;
        }

        @PostConstruct
        public void wire() {
            nodeDrainService.setDownlinkStopHook(() -> {
                String listenerId = "downlinkConsumer";
                kafkaRegistry.getListenerContainers().forEach(c -> {
                    if (c.getListenerId() != null && c.getListenerId().contains("DownlinkConsumer")) {
                        c.stop();
                    }
                });
            });
        }
    }
}
