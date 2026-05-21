package com.omni.gateway.network.server;

import com.omni.gateway.core.config.GatewayConfigSnapshot;
import com.omni.gateway.core.config.PortListenerConfig;
import com.omni.gateway.core.plugin.PluginRegistry;
import com.omni.gateway.core.session.SessionRegistry;
import com.omni.gateway.core.backpressure.BackpressureController;
import com.omni.gateway.core.lifecycle.DeviceLifecyclePublisher;
import com.omni.gateway.core.uplink.UplinkPublisher;
import com.omni.gateway.network.handler.GatewayChannelInitializer;
import com.omni.gateway.network.logging.ConfigurableProtocolTrafficLog;
import com.omni.gateway.network.metrics.OmniMetrics;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public class PortListenerManager {

    private static final Logger log = LoggerFactory.getLogger(PortListenerManager.class);

    private final AtomicReference<GatewayConfigSnapshot> configRef;
    private final PluginRegistry pluginRegistry;
    private final SessionRegistry sessionRegistry;
    private final UplinkPublisher uplinkPublisher;
    private final OmniMetrics metrics;
    private final String gatewayNodeId;
    private final BackpressureController backpressure;
    private final DeviceLifecyclePublisher lifecyclePublisher;
    private final ConfigurableProtocolTrafficLog protocolTrafficLog;

    private final EventLoopGroup bossGroup = new NioEventLoopGroup(1);
    private final EventLoopGroup workerGroup = new NioEventLoopGroup();
    private final Map<Integer, Channel> serverChannels = new ConcurrentHashMap<>();

    public PortListenerManager(AtomicReference<GatewayConfigSnapshot> configRef,
                               PluginRegistry pluginRegistry,
                               SessionRegistry sessionRegistry,
                               UplinkPublisher uplinkPublisher,
                               OmniMetrics metrics,
                               String gatewayNodeId,
                               BackpressureController backpressure,
                               DeviceLifecyclePublisher lifecyclePublisher,
                               ConfigurableProtocolTrafficLog protocolTrafficLog) {
        this.configRef = configRef;
        this.pluginRegistry = pluginRegistry;
        this.sessionRegistry = sessionRegistry;
        this.uplinkPublisher = uplinkPublisher;
        this.metrics = metrics;
        this.gatewayNodeId = gatewayNodeId;
        this.backpressure = backpressure;
        this.lifecyclePublisher = lifecyclePublisher;
        this.protocolTrafficLog = protocolTrafficLog;
    }

    public void startAll() throws InterruptedException {
        GatewayConfigSnapshot config = configRef.get();
        for (PortListenerConfig listener : config.getListeners()) {
            startPort(listener.getPort());
        }
    }

    public synchronized void startPort(int port) throws InterruptedException {
        if (serverChannels.containsKey(port)) {
            return;
        }
        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(bossGroup, workerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new GatewayChannelInitializer(
                        port, configRef, pluginRegistry, sessionRegistry,
                        uplinkPublisher, metrics, gatewayNodeId,
                        backpressure, lifecyclePublisher, protocolTrafficLog));

        ChannelFuture future = bootstrap.bind(port).sync();
        serverChannels.put(port, future.channel());
        log.info("TCP listener started on port {}", port);
    }

    public void stopAll() {
        for (Channel ch : serverChannels.values()) {
            ch.close();
        }
        serverChannels.clear();
        bossGroup.shutdownGracefully();
        workerGroup.shutdownGracefully();
        log.info("All TCP listeners stopped");
    }
}
