package com.omni.examples.device;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.omni.examples.ProtocolPrinter;
import com.omni.examples.simpleframe.FramePacket;
import com.omni.examples.simpleframe.SimpleFrameCodec;
import com.omni.examples.simpleframe.SimpleFrameMessage;

import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 最小设备端：TCP 连接网关 → auth → 周期 telemetry → 收到下行自动 ack。
 *
 * <pre>
 * mvn -q package -f examples/omni-java-skeleton/pom.xml
 * java -cp examples/omni-java-skeleton/target/omni-java-skeleton-1.0.0-SNAPSHOT-jar-with-dependencies.jar \
 *   com.omni.examples.device.SimpleFrameDeviceClient --host 127.0.0.1 --port 9000 --device-id device-001
 * </pre>
 */
public class SimpleFrameDeviceClient {

    public static void main(String[] args) throws Exception {
        String host = "127.0.0.1";
        int port = 9000;
        String deviceId = "device-001";
        int telemetryIntervalSec = 30;
        boolean verbose = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--host" -> host = args[++i];
                case "--port" -> port = Integer.parseInt(args[++i]);
                case "--device-id" -> deviceId = args[++i];
                case "--interval" -> telemetryIntervalSec = Integer.parseInt(args[++i]);
                case "--verbose", "-v" -> verbose = true;
                default -> throw new IllegalArgumentException("unknown arg: " + args[i]);
            }
        }
        final boolean logVerbose = verbose;

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 10_000);
            socket.setSoTimeout(0);
            InputStream in = socket.getInputStream();
            var out = socket.getOutputStream();

            SimpleFrameMessage auth = new SimpleFrameMessage();
            auth.setType("auth");
            auth.setDeviceId(deviceId);
            ProtocolPrinter.printSimpleFrame(">> SEND", SimpleFrameCodec.encodePacket(auth), logVerbose);
            SimpleFrameCodec.writeFrame(out, auth);

            FramePacket authOkPkt = SimpleFrameCodec.decodePacket(in);
            ProtocolPrinter.printSimpleFrame("<< RECV", authOkPkt, logVerbose);

            AtomicBoolean running = new AtomicBoolean(true);
            Thread reader = new Thread(() -> {
                while (running.get()) {
                    try {
                        FramePacket pkt = SimpleFrameCodec.decodePacket(in);
                        ProtocolPrinter.printSimpleFrame("<< RECV", pkt, logVerbose);
                        SimpleFrameMessage msg = pkt.message();
                        if (msg.getMessageId() != null && !msg.getMessageId().isBlank()) {
                            SimpleFrameMessage ack = new SimpleFrameMessage();
                            ack.setType("ack");
                            ack.setMessageId(msg.getMessageId());
                            ProtocolPrinter.printSimpleFrame(">> SEND", SimpleFrameCodec.encodePacket(ack), logVerbose);
                            SimpleFrameCodec.writeFrame(out, ack);
                        }
                    } catch (Exception e) {
                        if (running.get()) {
                            System.out.println("reader stopped: " + e.getMessage());
                        }
                        break;
                    }
                }
            }, "device-reader");
            reader.setDaemon(true);
            reader.start();

            int seq = 0;
            while (!Thread.currentThread().isInterrupted()) {
                Thread.sleep(telemetryIntervalSec * 1000L);
                SimpleFrameMessage telemetry = new SimpleFrameMessage();
                telemetry.setType("telemetry");
                telemetry.setPayload(JsonNodeFactory.instance.objectNode()
                        .put("seq", seq++)
                        .put("temp", 20 + (seq % 10)));
                ProtocolPrinter.printSimpleFrame(">> SEND", SimpleFrameCodec.encodePacket(telemetry), logVerbose);
                SimpleFrameCodec.writeFrame(out, telemetry);
            }
        }
    }
}
