package com.omni.gateway.protocol.simpleframe;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;

import java.nio.charset.StandardCharsets;

public final class SimpleFrameCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SimpleFrameCodec() {
    }

    public static boolean detect(ByteBuf buffer) {
        if (buffer.readableBytes() < 4) {
            return false;
        }
        for (int i = 0; i < 4; i++) {
            if (buffer.getByte(buffer.readerIndex() + i) != SimpleFrameConstants.MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

    public static SimpleFrameMessage decodeFrame(ByteBuf in) throws Exception {
        if (in.readableBytes() < 7) {
            return null;
        }
        for (int i = 0; i < 4; i++) {
            if (in.readByte() != SimpleFrameConstants.MAGIC[i]) {
                throw new IllegalArgumentException("bad magic");
            }
        }
        int bodyLen = in.readUnsignedShort();
        if (in.readableBytes() < bodyLen + 1) {
            in.readerIndex(in.readerIndex() - 6);
            return null;
        }
        byte[] body = new byte[bodyLen];
        in.readBytes(body);
        byte checksum = in.readByte();
        byte expected = xorChecksum(SimpleFrameConstants.MAGIC, body);
        if (checksum != expected) {
            throw new IllegalArgumentException("checksum mismatch");
        }
        return MAPPER.readValue(body, SimpleFrameMessage.class);
    }

    public static ByteBuf encodeFrame(io.netty.buffer.ByteBufAllocator alloc, SimpleFrameMessage msg) throws Exception {
        byte[] body = MAPPER.writeValueAsBytes(msg);
        ByteBuf out = alloc.buffer(7 + body.length);
        out.writeBytes(SimpleFrameConstants.MAGIC);
        out.writeShort(body.length);
        out.writeBytes(body);
        out.writeByte(xorChecksum(SimpleFrameConstants.MAGIC, body));
        return out;
    }

    public static ByteBuf encodeCommand(io.netty.buffer.ByteBufAllocator alloc,
                                        String commandType,
                                        com.fasterxml.jackson.databind.JsonNode payload) throws Exception {
        SimpleFrameMessage msg = new SimpleFrameMessage();
        msg.setType(commandType);
        msg.setPayload(payload);
        return encodeFrame(alloc, msg);
    }

    private static byte xorChecksum(byte[] magic, byte[] body) {
        byte x = 0;
        for (byte b : magic) {
            x ^= b;
        }
        for (byte b : body) {
            x ^= b;
        }
        return x;
    }

    public static String hexHead(ByteBuf buf, int max) {
        return ByteBufUtil.hexDump(buf, buf.readerIndex(), Math.min(max, buf.readableBytes()));
    }
}
