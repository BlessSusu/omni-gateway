package com.omni.examples.simpleframe;

/**
 * 解码后的一帧：原始字节 + 解析结果。
 */
public record FramePacket(byte[] raw, SimpleFrameMessage message, String bodyJson) {
}
