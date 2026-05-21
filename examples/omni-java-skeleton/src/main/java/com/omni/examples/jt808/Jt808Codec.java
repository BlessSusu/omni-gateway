package com.omni.examples.jt808;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * JT/T 808-2013 最小编解码（与网关 omni-protocols 行为对齐）。
 */
public final class Jt808Codec {

    public static final byte FLAG = 0x7E;
    public static final byte ESCAPE = 0x7D;
    public static final byte ESCAPE_7E = 0x02;
    public static final byte ESCAPE_7D = 0x01;

    public static final int MSG_TERMINAL_REGISTER = 0x0100;
    public static final int MSG_TERMINAL_HEARTBEAT = 0x0002;
    public static final int MSG_TERMINAL_COMMON_ACK = 0x0001;
    public static final int MSG_PLATFORM_REGISTER_ACK = 0x8100;

    private Jt808Codec() {
    }

    public static byte[] encode(int messageId, String phone, int serialNo, byte[] body) {
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
        ByteArrayOutputStream out = new ByteArrayOutputStream(escaped.length + 2);
        out.write(FLAG);
        out.writeBytes(escaped);
        out.write(FLAG);
        return out.toByteArray();
    }

    public static byte[] encodeTerminalCommonAck(String phone, int serialNo,
                                                 int answerSerial, int answerMsgId, byte result) {
        byte[] body = new byte[5];
        body[0] = (byte) (answerSerial >> 8);
        body[1] = (byte) answerSerial;
        body[2] = (byte) (answerMsgId >> 8);
        body[3] = (byte) answerMsgId;
        body[4] = result;
        return encode(MSG_TERMINAL_COMMON_ACK, phone, serialNo, body);
    }

    public static DrainResult drainFrames(byte[] buffer) {
        List<Jt808Message> list = new ArrayList<>();
        int pos = 0;
        while (pos < buffer.length) {
            int start = indexOf(buffer, pos, FLAG);
            if (start < 0) {
                break;
            }
            int end = indexOf(buffer, start + 1, FLAG);
            if (end < 0) {
                break;
            }
            int escapedLen = end - start - 1;
            if (escapedLen >= 12) {
                byte[] escaped = new byte[escapedLen];
                System.arraycopy(buffer, start + 1, escaped, 0, escapedLen);
                Jt808Message msg = parseEscaped(escaped);
                if (msg != null) {
                    byte[] raw = new byte[end - start + 1];
                    System.arraycopy(buffer, start, raw, 0, raw.length);
                    msg.setRawFrame(raw);
                    list.add(msg);
                }
            }
            pos = end + 1;
        }
        byte[] remainder = pos < buffer.length
                ? java.util.Arrays.copyOfRange(buffer, pos, buffer.length)
                : new byte[0];
        return new DrainResult(list, remainder);
    }

    public record DrainResult(List<Jt808Message> messages, byte[] remainder) {
    }

    private static Jt808Message parseEscaped(byte[] escaped) {
        byte[] raw = unescape(escaped);
        if (raw.length < 13) {
            return null;
        }
        byte cs = raw[raw.length - 1];
        if (xor(raw, 0, raw.length - 1) != cs) {
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
        return msg;
    }

    private static int indexOf(byte[] buf, int from, byte b) {
        for (int i = from; i < buf.length; i++) {
            if (buf[i] == b) {
                return i;
            }
        }
        return -1;
    }

    private static byte[] unescape(byte[] data) {
        int len = 0;
        byte[] tmp = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            if (data[i] == ESCAPE && i + 1 < data.length) {
                if (data[i + 1] == ESCAPE_7E) {
                    tmp[len++] = FLAG;
                } else if (data[i + 1] == ESCAPE_7D) {
                    tmp[len++] = ESCAPE;
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
            if (b == FLAG || b == ESCAPE) {
                extra++;
            }
        }
        byte[] out = new byte[data.length + extra];
        int j = 0;
        for (byte b : data) {
            if (b == FLAG) {
                out[j++] = ESCAPE;
                out[j++] = ESCAPE_7E;
            } else if (b == ESCAPE) {
                out[j++] = ESCAPE;
                out[j++] = ESCAPE_7D;
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

    private static String bcdPhone(byte[] raw, int off, int len) {
        StringBuilder sb = new StringBuilder(len * 2);
        for (int i = off; i < off + len; i++) {
            sb.append((raw[i] >> 4) & 0x0F);
            sb.append(raw[i] & 0x0F);
        }
        return sb.toString().replaceFirst("^0+", "");
    }

    private static void writeBcdPhone(byte[] raw, int off, String phone) {
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
