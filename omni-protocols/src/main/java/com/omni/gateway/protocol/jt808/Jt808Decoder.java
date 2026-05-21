package com.omni.gateway.protocol.jt808;

import com.omni.gateway.network.metrics.OmniMetrics;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

public class Jt808Decoder extends ByteToMessageDecoder {

    private final OmniMetrics metrics;

    public Jt808Decoder(OmniMetrics metrics) {
        this.metrics = metrics;
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
            out.add(msg);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        metrics.parseError(Jt808Constants.PLUGIN_ID);
        ctx.close();
    }
}
