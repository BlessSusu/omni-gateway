package com.omni.gateway.core.logging;

/**
 * 协议原始帧十六进制流量日志（可配置开关）。
 */
public interface ProtocolTrafficLog {

    boolean isEnabled();

    void logRecv(String traceSessionId, String serialNo, byte[] frame);

    void logSend(String traceSessionId, String serialNo, byte[] frame);

    void logRecv(io.netty.channel.Channel channel, String serialNo, byte[] frame);

    void logSend(io.netty.channel.Channel channel, String serialNo, byte[] frame);
}
