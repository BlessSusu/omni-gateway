package com.omni.gateway.bootstrap.test;

import com.omni.gateway.protocol.simpleframe.SimpleFrameCodec;
import com.omni.gateway.protocol.simpleframe.SimpleFrameMessage;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/**
 * 测试用 simple-frame TCP 客户端。
 */
public class TcpTestClient implements AutoCloseable {

    private final Socket socket;
    private final InputStream in;
    private final OutputStream out;

    public TcpTestClient(String host, int port) throws IOException {
        socket = new Socket(host, port);
        socket.setSoTimeout(10_000);
        in = socket.getInputStream();
        out = socket.getOutputStream();
    }

    public void sendAuth(String deviceId) throws Exception {
        SimpleFrameMessage msg = new SimpleFrameMessage();
        msg.setType("auth");
        msg.setDeviceId(deviceId);
        writeFrame(msg);
    }

    public void sendTelemetry(String type, String jsonPayload) throws Exception {
        SimpleFrameMessage msg = new SimpleFrameMessage();
        msg.setType(type);
        if (jsonPayload != null) {
            msg.setPayload(new com.fasterxml.jackson.databind.ObjectMapper().readTree(jsonPayload));
        }
        writeFrame(msg);
    }

    public void sendAck(String messageId) throws Exception {
        SimpleFrameMessage msg = new SimpleFrameMessage();
        msg.setType("ack");
        msg.setMessageId(messageId);
        writeFrame(msg);
    }

    public SimpleFrameMessage readFrame() throws Exception {
        byte[] frame = readOneFrame();
        var buf = io.netty.buffer.Unpooled.wrappedBuffer(frame);
        try {
            return SimpleFrameCodec.decodeFrame(buf);
        } finally {
            buf.release();
        }
    }

    private void writeFrame(SimpleFrameMessage msg) throws Exception {
        var buf = SimpleFrameCodec.encodeFrame(
                io.netty.buffer.UnpooledByteBufAllocator.DEFAULT, msg);
        try {
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            out.write(bytes);
            out.flush();
        } finally {
            buf.release();
        }
    }

    private byte[] readOneFrame() throws IOException {
        ByteArrayOutputStream acc = new ByteArrayOutputStream();
        while (true) {
            int b = in.read();
            if (b < 0) {
                throw new IOException("connection closed before full frame");
            }
            acc.write(b);
            byte[] arr = acc.toByteArray();
            if (arr.length >= 7
                    && arr[0] == 'O' && arr[1] == 'M' && arr[2] == 'N' && arr[3] == 'I') {
                int bodyLen = ((arr[4] & 0xFF) << 8) | (arr[5] & 0xFF);
                int total = 4 + 2 + bodyLen + 1;
                if (arr.length >= total) {
                    byte[] frame = new byte[total];
                    System.arraycopy(arr, 0, frame, 0, total);
                    return frame;
                }
            }
        }
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
