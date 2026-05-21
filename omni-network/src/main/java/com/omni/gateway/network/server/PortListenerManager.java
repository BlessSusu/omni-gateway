package com.omni.gateway.network.server;

import com.omni.gateway.core.config.GatewayConfigSnapshot;
import com.omni.gateway.core.config.PortListenerConfig;
import com.omni.gateway.core.plugin.PluginRegistry;
import com.omni.gateway.core.session.DistributedSessionIndex;
import com.omni.gateway.core.session.SessionRegistry;
import com.omni.gateway.core.backpressure.BackpressureController;
import com.omni.gateway.core.lifecycle.DeviceLifecyclePublisher;
import com.omni.gateway.core.lifecycle.DeviceOnlineListener;
import com.omni.gateway.core.uplink.UplinkPublisher;
import com.omni.gateway.network.drain.NodeDrainService;
import com.omni.gateway.network.handler.GatewayChannelInitializer;
import com.omni.gateway.network.logging.ConfigurableProtocolTrafficLog;
import com.omni.gateway.network.metrics.OmniMetrics;
import com.omni.gateway.network.ssl.SslContextFactory;
import io.micrometer.tracing.Tracer;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.micrometer.core.instrument.Gauge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class PortListenerManager {

    private static final Logger log = LoggerFactory.getLogger(PortListenerManager.class);

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
    private final DeviceOnlineListener deviceOnlineListener;
    private final ConfigurableProtocolTrafficLog protocolTrafficLog;
    private final SslContextFactory sslContextFactory;
    private final NodeDrainService nodeDrainService;
    private final Tracer tracer;

    private final EventLoopGroup bossGroup = new NioEventLoopGroup(1);
    private final EventLoopGroup workerGroup = new NioEventLoopGroup();
    private final Map<Integer, Channel> serverChannels = new ConcurrentHashMap<>();
    private final Set<Integer> drainingPorts = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean acceptNewConnections = new AtomicBoolean(true);

    public PortListenerManager(AtomicReference<GatewayConfigSnapshot> configRef,
                               PluginRegistry pluginRegistry,
                               SessionRegistry sessionRegistry,
                               DistributedSessionIndex distributedSessionIndex,
                               long sessionIndexTtlSec,
                               UplinkPublisher uplinkPublisher,
                               OmniMetrics metrics,
                               String gatewayNodeId,
                               BackpressureController backpressure,
                               DeviceLifecyclePublisher lifecyclePublisher,
                               DeviceOnlineListener deviceOnlineListener,
                               ConfigurableProtocolTrafficLog protocolTrafficLog,
                               SslContextFactory sslContextFactory,
                               NodeDrainService nodeDrainService,
                               Tracer tracer) {
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
        this.deviceOnlineListener = deviceOnlineListener;
        this.protocolTrafficLog = protocolTrafficLog;
        this.sslContextFactory = sslContextFactory;
        this.nodeDrainService = nodeDrainService;
        this.tracer = tracer;
    }

    public void startAll() throws InterruptedException {
        GatewayConfigSnapshot config = configRef.get();
        for (PortListenerConfig listener : config.getListeners()) {
            startPort(listener.getPort());
        }
    }

    public synchronized void startPort(int port) throws InterruptedException {
        if (serverChannels.containsKey(port) || drainingPorts.contains(port)) {
            return;
        }
        PortListenerConfig listener = configRef.get().listenerForPort(port);
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new GatewayChannelInitializer(
                        port, listener.isTls(), configRef, pluginRegistry, sessionRegistry,
                        distributedSessionIndex, sessionIndexTtlSec,
                        uplinkPublisher, metrics, gatewayNodeId,
                        backpressure, lifecyclePublisher, deviceOnlineListener, protocolTrafficLog,
                        sslContextFactory, nodeDrainService, this, tracer));

        ChannelFuture future = bootstrap.bind(port).sync();
        serverChannels.put(port, future.channel());
        registerDrainGauge(port);
        log.info("TCP listener started on port {} tls={}", port, listener.isTls());
    }

    public synchronized void stopPort(int port) {
        Channel ch = serverChannels.remove(port);
        drainingPorts.remove(port);
        if (ch != null) {
            ch.close();
            log.info("TCP listener stopped on port {}", port);
        }
    }

    public synchronized boolean drainPort(int port, int timeoutSec) throws InterruptedException {
        if (!serverChannels.containsKey(port)) {
            return true;
        }
        drainingPorts.add(port);
        Channel server = serverChannels.get(port);
        if (server != null) {
            server.config().setAutoRead(false);
            server.close().sync();
        }
        serverChannels.remove(port);
        long deadline = System.currentTimeMillis() + timeoutSec * 1000L;
        while (sessionRegistry.localSessionCountOnPort(port) > 0 && System.currentTimeMillis() < deadline) {
            TimeUnit.MILLISECONDS.sleep(200);
        }
        int remaining = sessionRegistry.localSessionCountOnPort(port);
        drainingPorts.remove(port);
        log.info("Port drain finished port={} remainingSessions={}", port, remaining);
        return remaining == 0;
    }

    public boolean isPortDraining(int port) {
        return drainingPorts.contains(port);
    }

    public Set<Integer> activePorts() {
        return Set.copyOf(serverChannels.keySet());
    }

    public boolean isAcceptingConnections() {
        return acceptNewConnections.get() && !nodeDrainService.isDraining();
    }

    public void setAcceptNewConnections(boolean accept) {
        acceptNewConnections.set(accept);
    }

    public void stopAll() {
        for (Integer port : Set.copyOf(serverChannels.keySet())) {
            stopPort(port);
        }
        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
        log.info("All TCP listeners stopped");
    }

    private void registerDrainGauge(int port) {
        Gauge.builder("omni_listener_draining", () -> drainingPorts.contains(port) ? 1.0 : 0.0)
                .tag("port", String.valueOf(port))
                .register(metrics.registry());
    }
}
