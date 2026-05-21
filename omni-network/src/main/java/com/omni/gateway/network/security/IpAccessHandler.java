package com.omni.gateway.network.security;

import com.omni.gateway.core.config.GatewayConfigSnapshot;
import com.omni.gateway.core.config.SecurityConfig;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.function.Supplier;

@ChannelHandler.Sharable
public class IpAccessHandler extends ChannelInboundHandlerAdapter {

    private static final Logger log = LoggerFactory.getLogger(IpAccessHandler.class);

    private final Supplier<GatewayConfigSnapshot> configSupplier;

    public IpAccessHandler(Supplier<GatewayConfigSnapshot> configSupplier) {
        this.configSupplier = configSupplier;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        String ip = remoteIp(ctx);
        SecurityConfig sec = configSupplier.get().getSecurity();
        List<String> deny = sec.getIpDenyList();
        if (deny != null && !deny.isEmpty() && deny.contains(ip)) {
            log.warn("Connection denied (deny list) ip={}", ip);
            ctx.close();
            return;
        }
        List<String> allow = sec.getIpAllowList();
        if (allow != null && !allow.isEmpty() && !allow.contains(ip)) {
            log.warn("Connection denied (not in allow list) ip={}", ip);
            ctx.close();
            return;
        }
        ctx.fireChannelActive();
    }

    static String remoteIp(ChannelHandlerContext ctx) {
        if (ctx.channel().remoteAddress() instanceof InetSocketAddress isa) {
            return isa.getAddress().getHostAddress();
        }
        return "unknown";
    }
}
