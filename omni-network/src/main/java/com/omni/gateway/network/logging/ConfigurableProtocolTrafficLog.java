package com.omni.gateway.network.logging;

import com.omni.gateway.core.ChannelAttributes;
import com.omni.gateway.core.logging.ProtocolTrafficLog;
import com.omni.gateway.core.session.DeviceSession;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.function.BooleanSupplier;

/**
 * 格式：{@code 2026-05-20 17:55:02.482 --- SESSION: <32hex> SN: <deviceId> recv|send F9 0A ...}
 */
public class ConfigurableProtocolTrafficLog implements ProtocolTrafficLog {

    private static final Logger log = LoggerFactory.getLogger("com.omni.gateway.protocol.traffic");
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final BooleanSupplier enabled;

    public ConfigurableProtocolTrafficLog(BooleanSupplier enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean isEnabled() {
        return enabled.getAsBoolean();
    }

    @Override
    public void logRecv(Channel channel, String serialNo, byte[] frame) {
        log(channel, serialNo, "recv", frame);
    }

    @Override
    public void logSend(Channel channel, String serialNo, byte[] frame) {
        log(channel, serialNo, "send", frame);
    }

    @Override
    public void logRecv(String traceSessionId, String serialNo, byte[] frame) {
        write(traceSessionId, serialNo, "recv", frame);
    }

    @Override
    public void logSend(String traceSessionId, String serialNo, byte[] frame) {
        write(traceSessionId, serialNo, "send", frame);
    }

    public void logRecv(DeviceSession session, byte[] frame) {
        if (!isEnabled() || frame == null || frame.length == 0) {
            return;
        }
        logRecv(session.getChannel(), resolveSerialNo(session), frame);
    }

    public void logSend(DeviceSession session, byte[] frame) {
        if (!isEnabled() || frame == null || frame.length == 0) {
            return;
        }
        logSend(session.getChannel(), resolveSerialNo(session), frame);
    }

    public void logSend(DeviceSession session, ByteBuf buf) {
        if (!isEnabled() || buf == null || !buf.isReadable()) {
            return;
        }
        byte[] copy = new byte[buf.readableBytes()];
        buf.getBytes(buf.readerIndex(), copy);
        logSend(session, copy);
    }

    private void log(Channel channel, String serialNo, String direction, byte[] frame) {
        if (!isEnabled() || frame == null || frame.length == 0) {
            return;
        }
        String sessionId = channel != null
                ? channel.attr(ChannelAttributes.TRACE_SESSION_ID).get()
                : null;
        write(sessionId != null ? sessionId : "-", serialNo, direction, frame);
    }

    private static String resolveSerialNo(DeviceSession session) {
        String deviceId = session.getDeviceId();
        return deviceId != null && !deviceId.isBlank() ? deviceId : "-";
    }

    private static void write(String traceSessionId, String serialNo, String direction, byte[] frame) {
        String line = TS.format(LocalDateTime.now())
                + " --- SESSION: " + traceSessionId
                + " SN: " + (serialNo != null ? serialNo : "-")
                + " " + direction + " "
                + toSpacedHex(frame);
        log.info(line);
    }

    static String toSpacedHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 3);
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(String.format("%02X", bytes[i]));
        }
        return sb.toString();
    }
}
