package com.omni.gateway.protocol.simpleframe;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;

public final class SimpleFrameCodec {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SimpleFrameCodec() {
    }

    public record FramePacket(SimpleFrameMessage message, byte[] rawFrame) {
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
        FramePacket packet = decodeFramePacket(in);
        return packet != null ? packet.message() : null;
    }

    public static FramePacket decodeFramePacket(ByteBuf in) throws Exception {
        if (in.readableBytes() < 7) {
            return null;
        }
        int frameStart = in.readerIndex();
        for (int i = 0; i < 4; i++) {
            if (in.readByte() != SimpleFrameConstants.MAGIC[i]) {
                throw new IllegalArgumentException("bad magic");
            }
        }
        int bodyLen = in.readUnsignedShort();
        if (in.readableBytes() < bodyLen + 1) {
            in.readerIndex(frameStart);
            return null;
        }
        byte[] body = new byte[bodyLen];
        in.readBytes(body);
        byte checksum = in.readByte();
        byte expected = xorChecksum(SimpleFrameConstants.MAGIC, body);
        if (checksum != expected) {
            throw new IllegalArgumentException("checksum mismatch");
        }
        int frameEnd = in.readerIndex();
        byte[] raw = new byte[frameEnd - frameStart];
        in.getBytes(frameStart, raw);
        SimpleFrameMessage msg = MAPPER.readValue(body, SimpleFrameMessage.class);
        return new FramePacket(msg, raw);
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

    public static byte[] encodeFrameBytes(SimpleFrameMessage msg) throws Exception {
        byte[] body = MAPPER.writeValueAsBytes(msg);
        byte[] raw = new byte[7 + body.length];
        System.arraycopy(SimpleFrameConstants.MAGIC, 0, raw, 0, 4);
        raw[4] = (byte) (body.length >> 8);
        raw[5] = (byte) body.length;
        System.arraycopy(body, 0, raw, 6, body.length);
        raw[raw.length - 1] = xorChecksum(SimpleFrameConstants.MAGIC, body);
        return raw;
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
