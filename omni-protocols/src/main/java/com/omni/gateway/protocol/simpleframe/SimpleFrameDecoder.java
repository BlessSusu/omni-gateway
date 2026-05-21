package com.omni.gateway.protocol.simpleframe;

import com.omni.gateway.network.metrics.OmniMetrics;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

public class SimpleFrameDecoder extends ByteToMessageDecoder {

    private final OmniMetrics metrics;

    public SimpleFrameDecoder(OmniMetrics metrics) {
        this.metrics = metrics;
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
                SimpleFrameMessage msg = SimpleFrameCodec.decodeFrame(in);
                if (msg == null) {
                    in.resetReaderIndex();
                    return;
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
