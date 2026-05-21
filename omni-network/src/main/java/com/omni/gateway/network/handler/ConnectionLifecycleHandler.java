package com.omni.gateway.network.handler;

import com.omni.gateway.core.ChannelAttributes;
import com.omni.gateway.core.backpressure.BackpressureController;
import com.omni.gateway.core.lifecycle.DeviceLifecyclePublisher;
import com.omni.gateway.core.session.DeviceSession;
import com.omni.gateway.core.session.SessionRegistry;
import com.omni.gateway.network.metrics.OmniMetrics;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ChannelHandler.Sharable
public class ConnectionLifecycleHandler extends ChannelInboundHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(ConnectionLifecycleHandler.class);

    private final SessionRegistry sessionRegistry;
    private final OmniMetrics metrics;
    private final BackpressureController backpressure;
    private final DeviceLifecyclePublisher lifecyclePublisher;

    public ConnectionLifecycleHandler(SessionRegistry sessionRegistry,
                                      OmniMetrics metrics,
                                      BackpressureController backpressure,
                                      DeviceLifecyclePublisher lifecyclePublisher) {
        this.sessionRegistry = sessionRegistry;
        this.metrics = metrics;
        this.backpressure = backpressure;
        this.lifecyclePublisher = lifecyclePublisher;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        metrics.connectionOpened();
        ctx.fireChannelActive();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        metrics.connectionClosed();
        DeviceSession session = ctx.channel().attr(ChannelAttributes.SESSION).get();
        if (session != null) {
            backpressure.unregisterChannel(ctx.channel());
            if (session.getDeviceId() != null) {
                sessionRegistry.unbindIfSame(session.getDeviceId(), session);
                Boolean auth = ctx.channel().attr(ChannelAttributes.AUTHENTICATED).get();
                if (Boolean.TRUE.equals(auth)) {
                    lifecyclePublisher.publishOffline(session);
                }
                log.debug("Session closed deviceId={} channel={}", session.getDeviceId(), session.getChannelId());
            }
        }
        ctx.fireChannelInactive();
    }
}
