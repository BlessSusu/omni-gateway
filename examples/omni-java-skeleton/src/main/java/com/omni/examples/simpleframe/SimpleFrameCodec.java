package com.omni.examples.simpleframe;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * simple-frame：OMNI(4) + bodyLen(2 BE) + JSON UTF-8 + XOR checksum(1)。
 */
public final class SimpleFrameCodec {

    private static final byte[] MAGIC = {'O', 'M', 'N', 'I'};
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SimpleFrameCodec() {
    }

    public static byte[] encode(SimpleFrameMessage msg) throws IOException {
        byte[] body = MAPPER.writeValueAsBytes(msg);
        ByteArrayOutputStream out = new ByteArrayOutputStream(7 + body.length);
        out.write(MAGIC);
        out.write((body.length >> 8) & 0xFF);
        out.write(body.length & 0xFF);
        out.write(body);
        out.write(xorChecksum(MAGIC, body));
        return out.toByteArray();
    }

    public static SimpleFrameMessage decode(InputStream in) throws IOException {
        return decodePacket(in).message();
    }

    public static FramePacket decodePacket(InputStream in) throws IOException {
        byte[] header = readExact(in, 6);
        if (header[0] != 'O' || header[1] != 'M' || header[2] != 'N' || header[3] != 'I') {
            throw new IOException("bad magic");
        }
        int bodyLen = ((header[4] & 0xFF) << 8) | (header[5] & 0xFF);
        byte[] rest = readExact(in, bodyLen + 1);
        byte[] body = new byte[bodyLen];
        System.arraycopy(rest, 0, body, 0, bodyLen);
        byte cs = rest[bodyLen];
        if (xorChecksum(MAGIC, body) != cs) {
            throw new IOException("checksum mismatch");
        }
        byte[] raw = new byte[6 + bodyLen + 1];
        System.arraycopy(header, 0, raw, 0, 6);
        System.arraycopy(body, 0, raw, 6, bodyLen);
        raw[6 + bodyLen] = cs;
        String bodyJson = new String(body, StandardCharsets.UTF_8);
        SimpleFrameMessage msg = MAPPER.readValue(body, SimpleFrameMessage.class);
        return new FramePacket(raw, msg, bodyJson);
    }

    public static FramePacket encodePacket(SimpleFrameMessage msg) throws IOException {
        byte[] raw = encode(msg);
        byte[] body = MAPPER.writeValueAsBytes(msg);
        return new FramePacket(raw, msg, new String(body, StandardCharsets.UTF_8));
    }

    public static void writeFrame(OutputStream out, SimpleFrameMessage msg) throws IOException {
        out.write(encode(msg));
        out.flush();
    }

    private static byte[] readExact(InputStream in, int n) throws IOException {
        byte[] buf = new byte[n];
        int off = 0;
        while (off < n) {
            int r = in.read(buf, off, n - off);
            if (r < 0) {
                throw new IOException("stream closed");
            }
            off += r;
        }
        return buf;
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
}
