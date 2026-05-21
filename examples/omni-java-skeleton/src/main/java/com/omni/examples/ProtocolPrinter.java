package com.omni.examples;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.omni.examples.jt808.Jt808Codec;
import com.omni.examples.jt808.Jt808Message;
import com.omni.examples.simpleframe.FramePacket;
import com.omni.examples.simpleframe.SimpleFrameMessage;
import org.apache.kafka.clients.consumer.ConsumerRecord;

/**
 * 统一打印接收/发送的协议内容。
 */
public final class ProtocolPrinter {

    private static final ObjectMapper PRETTY = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private ProtocolPrinter() {
    }

    /** 设备 TCP：simple-frame 收发 */
    public static void printSimpleFrame(String direction, FramePacket packet, boolean verbose) {
        SimpleFrameMessage m = packet.message();
        System.out.println("========== simple-frame " + direction + " ==========");
        System.out.println("  frame     : OMNI + total=" + packet.raw().length + " bytes (body UTF-8 len="
                + packet.bodyJson().length() + ")");
        System.out.println("  type      : " + m.getType());
        System.out.println("  deviceId  : " + m.getDeviceId());
        System.out.println("  messageId : " + m.getMessageId());
        if (m.getPayload() != null && !m.getPayload().isNull()) {
            try {
                System.out.println("  payload   :");
                System.out.println(PRETTY.writeValueAsString(m.getPayload()));
            } catch (Exception e) {
                System.out.println("  payload   : " + m.getPayload());
            }
        }
        System.out.println("  body(json): " + packet.bodyJson());
        if (verbose) {
            System.out.println("  hex       : " + toHex(packet.raw()));
        }
        System.out.println();
    }

    /** 业务 Kafka：网关解析后的 JSON */
    public static void printKafka(String channel, ConsumerRecord<String, String> record, boolean verbose) {
        System.out.println("========== Kafka " + channel + " ==========");
        System.out.println("  topic     : " + record.topic());
        System.out.println("  partition : " + record.partition());
        System.out.println("  offset    : " + record.offset());
        System.out.println("  key       : " + record.key());
        System.out.println("  timestamp : " + record.timestamp());
        try {
            JsonNode node = PRETTY.readTree(record.value());
            System.out.println("  value     :");
            System.out.println(PRETTY.writeValueAsString(node));
            if ("omni.device.uplink".equals(record.topic())) {
                System.out.println("  --- parsed uplink (ThingModel) ---");
                System.out.println("  deviceId    : " + node.path("deviceId").asText(null));
                System.out.println("  protocol    : " + node.path("protocol").asText(null));
                System.out.println("  messageType : " + node.path("messageType").asText(null));
                System.out.println("  gatewayNode : " + node.path("gatewayNodeId").asText(null));
            }
            if ("omni.device.lifecycle".equals(record.topic())) {
                System.out.println("  --- parsed lifecycle ---");
                System.out.println("  event       : " + node.path("event").asText(null));
                System.out.println("  deviceId    : " + node.path("deviceId").asText(null));
                System.out.println("  protocol    : " + node.path("protocol").asText(null));
            }
            if ("omni.command.downlink.result".equals(record.topic())) {
                System.out.println("  --- parsed downlink result ---");
                System.out.println("  messageId   : " + node.path("messageId").asText(null));
                System.out.println("  status      : " + node.path("status").asText(null));
                System.out.println("  detail      : " + node.path("detail").asText(null));
            }
        } catch (Exception e) {
            System.out.println("  value(raw): " + record.value());
        }
        if (verbose) {
            System.out.println("  headers   : " + record.headers());
        }
        System.out.println();
    }

    /** JT808 TCP 帧 */
    public static void printJt808(String direction, byte[] raw, Jt808Message msg, boolean verbose) {
        if (msg == null && raw != null) {
            Jt808Codec.DrainResult drained = Jt808Codec.drainFrames(raw);
            if (!drained.messages().isEmpty()) {
                msg = drained.messages().get(0);
            }
        }
        System.out.println("========== JT808 " + direction + " ==========");
        if (raw != null) {
            System.out.println("  frame     : 0x7E ... total=" + raw.length + " bytes");
        }
        if (msg != null) {
            System.out.println("  msgId     : 0x" + String.format("%04X", msg.getMessageId()));
            System.out.println("  phone     : " + msg.getTerminalPhone());
            System.out.println("  serialNo  : " + msg.getSerialNo());
            System.out.println("  bodyLen   : " + msg.getBodyLength());
            if (msg.getBody() != null && msg.getBody().length > 0) {
                System.out.println("  bodyHex   : " + toHex(msg.getBody()));
            }
        }
        if (verbose && raw != null) {
            System.out.println("  hex       : " + toHex(raw));
        }
        System.out.println();
    }

    public static void printDownlinkSend(String json, boolean verbose) {
        System.out.println("========== Kafka SEND downlink ==========");
        try {
            System.out.println(PRETTY.writeValueAsString(PRETTY.readTree(json)));
        } catch (Exception e) {
            System.out.println(json);
        }
        if (verbose) {
            System.out.println("  topic: omni.command.downlink");
        }
        System.out.println();
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 3);
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0 && i % 16 == 0) {
                sb.append('\n');
            }
            sb.append(String.format("%02X ", bytes[i]));
        }
        return sb.toString().trim();
    }
}
