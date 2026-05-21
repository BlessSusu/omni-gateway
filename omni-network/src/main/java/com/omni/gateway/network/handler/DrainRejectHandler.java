package com.omni.gateway.network.handler;

import com.omni.gateway.network.drain.NodeDrainService;
import com.omni.gateway.network.server.PortListenerManager;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

@ChannelHandler.Sharable
public class DrainRejectHandler extends ChannelInboundHandlerAdapter {

    private final NodeDrainService nodeDrainService;
    private final PortListenerManager portListenerManager;

    public DrainRejectHandler(NodeDrainService nodeDrainService, PortListenerManager portListenerManager) {
        this.nodeDrainService = nodeDrainService;
        this.portListenerManager = portListenerManager;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        if (nodeDrainService.isDraining() || !portListenerManager.isAcceptingConnections()) {
            ctx.close();
            return;
        }
        ctx.fireChannelActive();
    }
}
