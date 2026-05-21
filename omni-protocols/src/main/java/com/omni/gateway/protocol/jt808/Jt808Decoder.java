package com.omni.gateway.protocol.jt808;

import com.omni.gateway.core.ChannelAttributes;
import com.omni.gateway.core.logging.ProtocolTrafficLog;
import com.omni.gateway.core.session.DeviceSession;
import com.omni.gateway.network.metrics.OmniMetrics;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

public class Jt808Decoder extends ByteToMessageDecoder {

    private final OmniMetrics metrics;
    private final ProtocolTrafficLog trafficLog;

    public Jt808Decoder(OmniMetrics metrics, ProtocolTrafficLog trafficLog) {
        this.metrics = metrics;
        this.trafficLog = trafficLog;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        while (in.isReadable()) {
            if (!Jt808Codec.detect(in)) {
                in.skipBytes(1);
                continue;
            }
            int readerBefore = in.readerIndex();
            Jt808Message msg = Jt808Codec.decodeFrame(in);
            if (msg == null) {
                in.readerIndex(readerBefore);
                return;
            }
            if (trafficLog != null && trafficLog.isEnabled() && msg.getRawFrame() != null) {
                DeviceSession session = ctx.channel().attr(ChannelAttributes.SESSION).get();
                String sn = msg.getTerminalPhone() != null ? msg.getTerminalPhone()
                        : (session != null ? session.getDeviceId() : null);
                trafficLog.logRecv(ctx.channel(), sn, msg.getRawFrame());
            }
            out.add(msg);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        metrics.parseError(Jt808Constants.PLUGIN_ID);
        ctx.close();
    }
}
