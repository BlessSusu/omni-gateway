package com.omni.gateway.protocol.jt808;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;

import java.nio.charset.StandardCharsets;

/**
 * JT/T 808-2013 帧界、转义、校验（骨架实现，覆盖注册/心跳/通用应答）。
 */
public final class Jt808Codec {

    private Jt808Codec() {
    }

    public static boolean detect(ByteBuf buffer) {
        return buffer.readableBytes() >= 1 && buffer.getByte(buffer.readerIndex()) == Jt808Constants.FLAG;
    }

    /**
     * 从累积缓冲中尝试解析一帧；数据不足返回 null。
     */
    public static Jt808Message decodeFrame(ByteBuf in) {
        int start = indexOf(in, in.readerIndex(), Jt808Constants.FLAG);
        if (start < 0) {
            return null;
        }
        int end = indexOf(in, start + 1, Jt808Constants.FLAG);
        if (end < 0) {
            return null;
        }
        int escapedLen = end - start - 1;
        if (escapedLen < 12) {
            in.readerIndex(end + 1);
            return null;
        }
        byte[] escaped = new byte[escapedLen];
        in.getBytes(start + 1, escaped);
        byte[] raw = unescape(escaped);
        if (raw.length < 13) {
            in.readerIndex(end + 1);
            return null;
        }
        byte cs = raw[raw.length - 1];
        byte calc = xor(raw, 0, raw.length - 1);
        if (cs != calc) {
            in.readerIndex(end + 1);
            return null;
        }
        Jt808Message msg = new Jt808Message();
        msg.setMessageId(((raw[0] & 0xFF) << 8) | (raw[1] & 0xFF));
        int props = ((raw[2] & 0xFF) << 8) | (raw[3] & 0xFF);
        msg.setBodyLength(props & 0x03FF);
        msg.setTerminalPhone(bcdPhone(raw, 4, 6));
        msg.setSerialNo(((raw[10] & 0xFF) << 8) | (raw[11] & 0xFF));
        int bodyLen = msg.getBodyLength();
        if (raw.length < 12 + bodyLen + 1) {
            return null;
        }
        if (bodyLen > 0) {
            byte[] body = new byte[bodyLen];
            System.arraycopy(raw, 12, body, 0, bodyLen);
            msg.setBody(body);
        }
        in.readerIndex(end + 1);
        return msg;
    }

    public static ByteBuf encode(ByteBufAllocator alloc, int messageId, String phone, int serialNo, byte[] body) {
        int bodyLen = body == null ? 0 : body.length;
        byte[] raw = new byte[12 + bodyLen + 1];
        raw[0] = (byte) (messageId >> 8);
        raw[1] = (byte) messageId;
        int props = bodyLen & 0x03FF;
        raw[2] = (byte) (props >> 8);
        raw[3] = (byte) props;
        writeBcdPhone(raw, 4, phone);
        raw[10] = (byte) (serialNo >> 8);
        raw[11] = (byte) serialNo;
        if (bodyLen > 0) {
            System.arraycopy(body, 0, raw, 12, bodyLen);
        }
        raw[raw.length - 1] = xor(raw, 0, raw.length - 1);
        byte[] escaped = escape(raw);
        ByteBuf out = alloc.buffer(escaped.length + 2);
        out.writeByte(Jt808Constants.FLAG);
        out.writeBytes(escaped);
        out.writeByte(Jt808Constants.FLAG);
        return out;
    }

    public static ByteBuf encodeRegisterAck(ByteBufAllocator alloc, String phone, int serialNo, int answerSerial, byte result) {
        byte[] body = new byte[5];
        body[0] = (byte) (answerSerial >> 8);
        body[1] = (byte) answerSerial;
        body[2] = (byte) (Jt808Constants.MSG_TERMINAL_REGISTER >> 8);
        body[3] = (byte) Jt808Constants.MSG_TERMINAL_REGISTER;
        body[4] = result;
        return encode(alloc, Jt808Constants.MSG_PLATFORM_REGISTER_ACK, phone, serialNo, body);
    }

    public static ByteBuf encodePlatformCommonAck(ByteBufAllocator alloc, String phone, int serialNo,
                                                  int answerSerial, int answerMsgId, byte result) {
        byte[] body = new byte[5];
        body[0] = (byte) (answerSerial >> 8);
        body[1] = (byte) answerSerial;
        body[2] = (byte) (answerMsgId >> 8);
        body[3] = (byte) answerMsgId;
        body[4] = result;
        return encode(alloc, Jt808Constants.MSG_PLATFORM_COMMON_ACK, phone, serialNo, body);
    }

    private static int indexOf(ByteBuf buf, int from, byte b) {
        for (int i = from; i < buf.writerIndex(); i++) {
            if (buf.getByte(i) == b) {
                return i;
            }
        }
        return -1;
    }

    private static byte[] unescape(byte[] data) {
        int len = 0;
        byte[] tmp = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            if (data[i] == Jt808Constants.ESCAPE && i + 1 < data.length) {
                if (data[i + 1] == Jt808Constants.ESCAPE_7E) {
                    tmp[len++] = Jt808Constants.FLAG;
                } else if (data[i + 1] == Jt808Constants.ESCAPE_7D) {
                    tmp[len++] = Jt808Constants.ESCAPE;
                }
                i++;
            } else {
                tmp[len++] = data[i];
            }
        }
        byte[] out = new byte[len];
        System.arraycopy(tmp, 0, out, 0, len);
        return out;
    }

    private static byte[] escape(byte[] data) {
        int extra = 0;
        for (byte b : data) {
            if (b == Jt808Constants.FLAG || b == Jt808Constants.ESCAPE) {
                extra++;
            }
        }
        byte[] out = new byte[data.length + extra];
        int j = 0;
        for (byte b : data) {
            if (b == Jt808Constants.FLAG) {
                out[j++] = Jt808Constants.ESCAPE;
                out[j++] = Jt808Constants.ESCAPE_7E;
            } else if (b == Jt808Constants.ESCAPE) {
                out[j++] = Jt808Constants.ESCAPE;
                out[j++] = Jt808Constants.ESCAPE_7D;
            } else {
                out[j++] = b;
            }
        }
        return out;
    }

    private static byte xor(byte[] data, int off, int len) {
        byte x = 0;
        for (int i = off; i < len; i++) {
            x ^= data[i];
        }
        return x;
    }

    static String bcdPhone(byte[] raw, int off, int len) {
        StringBuilder sb = new StringBuilder(len * 2);
        for (int i = off; i < off + len; i++) {
            sb.append((raw[i] >> 4) & 0x0F);
            sb.append(raw[i] & 0x0F);
        }
        return sb.toString().replaceFirst("^0+", "");
    }

    static void writeBcdPhone(byte[] raw, int off, String phone) {
        String digits = phone == null ? "" : phone.replaceAll("\\D", "");
        if (digits.length() % 2 != 0) {
            digits = "0" + digits;
        }
        while (digits.length() < 12) {
            digits = "0" + digits;
        }
        digits = digits.substring(0, 12);
        for (int i = 0; i < 6; i++) {
            int hi = Character.digit(digits.charAt(i * 2), 10);
            int lo = Character.digit(digits.charAt(i * 2 + 1), 10);
            raw[off + i] = (byte) ((hi << 4) | lo);
        }
    }
}
