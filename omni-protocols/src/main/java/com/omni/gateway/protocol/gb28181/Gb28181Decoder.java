package com.omni.gateway.protocol.gb28181;

import com.omni.gateway.core.ChannelAttributes;
import com.omni.gateway.core.logging.ProtocolTrafficLog;
import com.omni.gateway.core.session.DeviceSession;
import com.omni.gateway.network.metrics.OmniMetrics;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

public class Gb28181Decoder extends ByteToMessageDecoder {

    private final OmniMetrics metrics;
    private final ProtocolTrafficLog trafficLog;

    public Gb28181Decoder(OmniMetrics metrics, ProtocolTrafficLog trafficLog) {
        this.metrics = metrics;
        this.trafficLog = trafficLog;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        while (in.isReadable()) {
            if (!Gb28181Codec.detect(in)) {
                in.skipBytes(1);
                continue;
            }
            int readerBefore = in.readerIndex();
            Gb28181Message msg = Gb28181Codec.decodeFrame(in);
            if (msg == null) {
                in.readerIndex(readerBefore);
                return;
            }
            if (trafficLog != null && trafficLog.isEnabled() && msg.getRawFrame() != null) {
                DeviceSession session = ctx.channel().attr(ChannelAttributes.SESSION).get();
                String sn = Gb28181Sip.resolveDeviceId(msg);
                if (sn == null && session != null) {
                    sn = session.getDeviceId();
                }
                trafficLog.logRecv(ctx.channel(), sn, msg.getRawFrame());
            }
            out.add(msg);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        metrics.parseError(Gb28181Constants.PLUGIN_ID);
        ctx.close();
    }
}
