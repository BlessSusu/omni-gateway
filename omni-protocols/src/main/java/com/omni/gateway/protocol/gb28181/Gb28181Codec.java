package com.omni.gateway.protocol.gb28181;

import io.netty.buffer.ByteBuf;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class Gb28181Codec {

    private static final String[] SIP_PREFIXES = {
            "REGISTER ", "MESSAGE ", "NOTIFY ", "INVITE ", "ACK ", "BYE ", "CANCEL ", "OPTIONS ", "SIP/2.0"
    };

    private Gb28181Codec() {
    }

    public static boolean detect(ByteBuf buffer) {
        if (buffer.readableBytes() < 7) {
            return false;
        }
        int len = Math.min(buffer.readableBytes(), 12);
        String head = buffer.toString(buffer.readerIndex(), len, StandardCharsets.US_ASCII).toUpperCase(Locale.ROOT);
        for (String p : SIP_PREFIXES) {
            if (head.startsWith(p)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Decode one complete SIP message; returns null if frame incomplete.
     */
    public static Gb28181Message decodeFrame(ByteBuf in) {
        int readable = in.readableBytes();
        if (readable < 16) {
            return null;
        }
        int headerEnd = findHeaderEnd(in, in.readerIndex(), readable);
        if (headerEnd < 0) {
            return null;
        }
        int headerLen = headerEnd - in.readerIndex() - 4;
        String headerBlock = in.toString(in.readerIndex(), headerLen, StandardCharsets.UTF_8);
        int contentLength = parseContentLength(headerBlock);
        int totalLen = (headerEnd - in.readerIndex()) + contentLength;
        if (readable < totalLen) {
            return null;
        }
        byte[] raw = new byte[totalLen];
        in.readBytes(raw);
        return parse(raw);
    }

    private static int findHeaderEnd(ByteBuf buf, int start, int length) {
        for (int i = start; i < start + length - 3; i++) {
            if (buf.getByte(i) == '\r' && buf.getByte(i + 1) == '\n'
                    && buf.getByte(i + 2) == '\r' && buf.getByte(i + 3) == '\n') {
                return i + 4;
            }
        }
        return -1;
    }

    private static int parseContentLength(String headerBlock) {
        for (String line : headerBlock.split("\r\n")) {
            if (line.regionMatches(true, 0, "Content-Length:", 0, 15)) {
                try {
                    return Integer.parseInt(line.substring(15).trim());
                } catch (NumberFormatException ignored) {
                    return 0;
                }
            }
        }
        return 0;
    }

    static Gb28181Message parse(byte[] raw) {
        String text = new String(raw, StandardCharsets.UTF_8);
        int sep = text.indexOf("\r\n\r\n");
        if (sep < 0) {
            return null;
        }
        String headerBlock = text.substring(0, sep);
        String body = text.length() > sep + 4 ? text.substring(sep + 4) : "";

        Gb28181Message msg = new Gb28181Message();
        msg.setRawFrame(raw);

        String[] lines = headerBlock.split("\r\n");
        if (lines.length == 0) {
            return null;
        }
        String start = lines[0];
        msg.setStartLine(start);
        if (start.startsWith("SIP/2.0")) {
            msg.setResponse(true);
            String[] parts = start.split("\\s+", 3);
            if (parts.length >= 2) {
                try {
                    msg.setStatusCode(Integer.parseInt(parts[1]));
                } catch (NumberFormatException ignored) {
                    msg.setStatusCode(0);
                }
            }
        } else {
            int sp = start.indexOf(' ');
            if (sp > 0) {
                msg.setMethod(start.substring(0, sp).trim());
                msg.setRequestUri(start.substring(sp + 1).trim());
            } else {
                msg.setMethod(start.trim());
            }
        }

        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line.startsWith(" ") || line.startsWith("\t")) {
                continue;
            }
            int colon = line.indexOf(':');
            if (colon > 0) {
                msg.addHeader(line.substring(0, colon).trim(), line.substring(colon + 1).trim());
            }
        }
        msg.setBody(body.isEmpty() ? null : body);
        return msg;
    }
}
