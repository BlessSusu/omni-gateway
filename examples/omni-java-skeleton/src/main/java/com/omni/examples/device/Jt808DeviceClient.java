package com.omni.examples.device;

import com.omni.examples.ProtocolPrinter;
import com.omni.examples.jt808.Jt808Codec;
import com.omni.examples.jt808.Jt808Message;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * JT808 终端模拟：注册 0x0100、心跳 0x0002、自动终端通用应答 0x0001。
 *
 * <pre>
 * java -cp ...-jar-with-dependencies.jar com.omni.examples.device.Jt808DeviceClient \
 *   --host 127.0.0.1 --port 9001 --phone 13800138000
 * </pre>
 */
public class Jt808DeviceClient {

    public static void main(String[] args) throws Exception {
        String host = "127.0.0.1";
        int port = 9001;
        String phone = "13800138000";
        int heartbeatIntervalSec = 30;
        boolean verbose = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--host" -> host = args[++i];
                case "--port" -> port = Integer.parseInt(args[++i]);
                case "--phone" -> phone = args[++i];
                case "--interval" -> heartbeatIntervalSec = Integer.parseInt(args[++i]);
                case "--verbose", "-v" -> verbose = true;
                default -> throw new IllegalArgumentException("unknown arg: " + args[i]);
            }
        }

        final boolean logVerbose = verbose;
        final String terminalPhone = phone;
        final AtomicInteger serial = new AtomicInteger(1);

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 10_000);
            socket.setSoTimeout(0);
            final InputStream in = socket.getInputStream();
            final OutputStream out = socket.getOutputStream();

            System.out.println("connected " + host + ":" + port + " phone=" + terminalPhone);

            send(out, Jt808Codec.encode(Jt808Codec.MSG_TERMINAL_REGISTER, terminalPhone, serial.getAndIncrement(),
                    new byte[]{0x00, 0x00}), null, logVerbose);

            AtomicBoolean running = new AtomicBoolean(true);
            Thread reader = new Thread(() -> readLoop(in, out, terminalPhone, serial, logVerbose, running), "jt808-reader");
            reader.setDaemon(true);
            reader.start();

            Thread.sleep(2000);

            while (!Thread.currentThread().isInterrupted()) {
                Thread.sleep(heartbeatIntervalSec * 1000L);
                send(out, Jt808Codec.encode(Jt808Codec.MSG_TERMINAL_HEARTBEAT, terminalPhone,
                        serial.getAndIncrement(), new byte[0]), null, logVerbose);
            }
        }
    }

    private static void send(OutputStream out, byte[] frame, Jt808Message meta, boolean verbose) throws Exception {
        ProtocolPrinter.printJt808(">> SEND", frame, meta, verbose);
        synchronized (out) {
            out.write(frame);
            out.flush();
        }
    }

    private static void readLoop(InputStream in,
                                 OutputStream out,
                                 String phone,
                                 AtomicInteger serial,
                                 boolean verbose,
                                 AtomicBoolean running) {
        byte[] acc = new byte[0];
        byte[] chunk = new byte[4096];
        try {
            while (running.get()) {
                int n = in.read(chunk);
                if (n < 0) {
                    break;
                }
                byte[] merged = new byte[acc.length + n];
                System.arraycopy(acc, 0, merged, 0, acc.length);
                System.arraycopy(chunk, 0, merged, acc.length, n);
                acc = merged;

                Jt808Codec.DrainResult drained = Jt808Codec.drainFrames(acc);
                acc = drained.remainder();

                for (Jt808Message msg : drained.messages()) {
                    ProtocolPrinter.printJt808("<< RECV", msg.getRawFrame(), msg, verbose);
                    if (msg.getMessageId() == Jt808Codec.MSG_PLATFORM_REGISTER_ACK) {
                        System.out.println("register ack received");
                        continue;
                    }
                    if (msg.getMessageId() != Jt808Codec.MSG_TERMINAL_COMMON_ACK) {
                        byte[] ack = Jt808Codec.encodeTerminalCommonAck(
                                phone, serial.getAndIncrement(), msg.getSerialNo(), msg.getMessageId(), (byte) 0);
                        send(out, ack, null, verbose);
                    }
                }
            }
        } catch (Exception e) {
            if (running.get()) {
                System.out.println("reader stopped: " + e.getMessage());
            }
        }
    }
}
