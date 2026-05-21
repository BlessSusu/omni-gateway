package com.omni.gateway.protocol.simpleframe;

import com.omni.gateway.core.ChannelAttributes;
import com.omni.gateway.core.logging.ProtocolTrafficLog;
import com.omni.gateway.core.session.DeviceSession;
import com.omni.gateway.network.metrics.OmniMetrics;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

public class SimpleFrameDecoder extends ByteToMessageDecoder {

    private final OmniMetrics metrics;
    private final ProtocolTrafficLog trafficLog;

    public SimpleFrameDecoder(OmniMetrics metrics, ProtocolTrafficLog trafficLog) {
        this.metrics = metrics;
        this.trafficLog = trafficLog;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        while (in.readableBytes() >= 7) {
            in.markReaderIndex();
            try {
                if (!SimpleFrameCodec.detect(in)) {
                    in.resetReaderIndex();
                    return;
                }
                boolean hexLog = trafficLog != null && trafficLog.isEnabled();
                SimpleFrameCodec.FramePacket packet = hexLog ? SimpleFrameCodec.decodeFramePacket(in) : null;
                SimpleFrameMessage msg = packet != null
                        ? packet.message()
                        : SimpleFrameCodec.decodeFrame(in);
                if (msg == null) {
                    in.resetReaderIndex();
                    return;
                }
                if (packet != null) {
                    DeviceSession session = ctx.channel().attr(ChannelAttributes.SESSION).get();
                    String sn = msg.getDeviceId() != null ? msg.getDeviceId()
                            : (session != null ? session.getDeviceId() : null);
                    trafficLog.logRecv(ctx.channel(), sn, packet.rawFrame());
                }
                out.add(msg);
            } catch (Exception e) {
                in.resetReaderIndex();
                metrics.parseError(SimpleFrameConstants.PLUGIN_ID);
                ctx.close();
                return;
            }
        }
    }
}
