package com.omni.gateway.network.sniff;

import com.omni.gateway.core.plugin.ProtocolPlugin;
import com.omni.gateway.core.session.DeviceSession;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;

import java.util.List;

public class PipelineBinder {

    public void bind(ChannelHandlerContext ctx, ProtocolPlugin plugin, DeviceSession session) {
        ChannelPipeline pipeline = ctx.pipeline();
        List<ChannelHandler> handlers = plugin.createHandlers(session);
        String base = "proto-" + plugin.pluginId() + "-";
        for (int i = handlers.size() - 1; i >= 0; i--) {
            pipeline.addBefore("uplink", base + i, handlers.get(i));
        }
    }
}
