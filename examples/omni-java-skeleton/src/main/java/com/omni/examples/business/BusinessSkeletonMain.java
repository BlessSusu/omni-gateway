package com.omni.examples.business;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.omni.examples.ProtocolPrinter;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 最小业务端：消费上行 / 生命周期 / 下行结果；可选向在线设备发一条下行。
 *
 * <pre>
 * mvn -q package -f examples/omni-java-skeleton/pom.xml
 * java -cp examples/omni-java-skeleton/target/omni-java-skeleton-1.0.0-SNAPSHOT-jar-with-dependencies.jar \
 *   com.omni.examples.business.BusinessSkeletonMain \
 *   --bootstrap 127.0.0.1:19092 \
 *   --send-downlink device-001
 * </pre>
 */
public class BusinessSkeletonMain {

    private static final String TOPIC_UPLINK = "omni.device.uplink";
    private static final String TOPIC_LIFECYCLE = "omni.device.lifecycle";
    private static final String TOPIC_DOWNLINK = "omni.command.downlink";
    private static final String TOPIC_DOWNLINK_RESULT = "omni.command.downlink.result";

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new JavaTimeModule());
    private static final Set<String> ONLINE = ConcurrentHashMap.newKeySet();

    public static void main(String[] args) throws Exception {
        String bootstrap = "127.0.0.1:9092";
        String groupId = "omni-business-skeleton-" + UUID.randomUUID();
        String sendDownlinkTo = null;
        boolean verbose = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--bootstrap" -> bootstrap = args[++i];
                case "--group" -> groupId = args[++i];
                case "--send-downlink" -> sendDownlinkTo = args[++i];
                case "--verbose", "-v" -> verbose = true;
                default -> throw new IllegalArgumentException("unknown arg: " + args[i]);
            }
        }

        final String kafkaBootstrap = bootstrap;
        final String consumerGroup = groupId;
        final String downlinkDeviceId = sendDownlinkTo;
        final boolean logVerbose = verbose;

        System.out.println("Kafka bootstrap: " + kafkaBootstrap);
        System.out.println("Consumer group:  " + consumerGroup);

        AtomicBoolean running = new AtomicBoolean(true);
        ExecutorService pool = Executors.newFixedThreadPool(3);

        pool.submit(() -> consumeLoop(kafkaBootstrap, consumerGroup + "-uplink", TOPIC_UPLINK, running, logVerbose, BusinessSkeletonMain::onUplink));
        pool.submit(() -> consumeLoop(kafkaBootstrap, consumerGroup + "-lifecycle", TOPIC_LIFECYCLE, running, logVerbose, BusinessSkeletonMain::onLifecycle));
        pool.submit(() -> consumeLoop(kafkaBootstrap, consumerGroup + "-result", TOPIC_DOWNLINK_RESULT, running, logVerbose, BusinessSkeletonMain::onDownlinkResult));

        if (downlinkDeviceId != null) {
            Thread sender = new Thread(() -> {
                try {
                    waitUntilOnline(downlinkDeviceId, 60);
                    Thread.sleep(2000);
                    sendDownlink(kafkaBootstrap, downlinkDeviceId, logVerbose);
                } catch (Exception e) {
                    System.err.println("send downlink failed: " + e.getMessage());
                }
            }, "downlink-sender");
            sender.setDaemon(true);
            sender.start();
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            running.set(false);
            pool.shutdownNow();
        }));

        System.out.println("Business skeleton running (Ctrl+C to exit).");
        pool.awaitTermination(Long.MAX_VALUE, TimeUnit.DAYS);
    }

    private static void waitUntilOnline(String deviceId, int timeoutSec) throws InterruptedException {
        for (int i = 0; i < timeoutSec; i++) {
            if (ONLINE.contains(deviceId)) {
                System.out.println("[business] device online: " + deviceId);
                return;
            }
            Thread.sleep(1000);
        }
        System.out.println("[business] warn: " + deviceId + " not seen online in " + timeoutSec + "s, sending anyway");
    }

    private static void sendDownlink(String bootstrap, String deviceId, boolean verbose) throws Exception {
        String messageId = "biz-cmd-" + UUID.randomUUID();
        DownlinkCommand cmd = new DownlinkCommand();
        cmd.setMessageId(messageId);
        cmd.setDeviceId(deviceId);
        cmd.setProtocol("simple-frame");
        cmd.setCommandType("setParam");
        cmd.setPayload(MAPPER.createObjectNode()
                .put("key", "interval")
                .put("value", 60));
        cmd.setTimeoutMs(5000L);

        String json = MAPPER.writeValueAsString(cmd);
        ProtocolPrinter.printDownlinkSend(json, verbose);
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            producer.send(new ProducerRecord<>(TOPIC_DOWNLINK, deviceId, json)).get();
            System.out.println("[business] >> downlink topic=" + TOPIC_DOWNLINK + " messageId=" + messageId);
        }
    }

    @FunctionalInterface
    private interface RecordHandler {
        void accept(ConsumerRecord<String, String> record, boolean verbose) throws Exception;
    }

    private static void consumeLoop(String bootstrap,
                                    String groupId,
                                    String topic,
                                    AtomicBoolean running,
                                    boolean verbose,
                                    RecordHandler handler) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(Collections.singletonList(topic));
            while (running.get()) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, String> r : records) {
                    try {
                        handler.accept(r, verbose);
                    } catch (Exception e) {
                        System.err.println("[" + topic + "] handler error: " + e.getMessage());
                    }
                }
            }
        }
    }

    private static void onUplink(ConsumerRecord<String, String> r, boolean verbose) {
        ProtocolPrinter.printKafka("RECV uplink", r, verbose);
    }

    private static void onLifecycle(ConsumerRecord<String, String> r, boolean verbose) throws Exception {
        JsonNode node = MAPPER.readTree(r.value());
        String event = node.path("event").asText();
        String deviceId = node.path("deviceId").asText();
        if ("online".equals(event)) {
            ONLINE.add(deviceId);
        } else if ("offline".equals(event)) {
            ONLINE.remove(deviceId);
        }
        ProtocolPrinter.printKafka("RECV lifecycle", r, verbose);
    }

    private static void onDownlinkResult(ConsumerRecord<String, String> r, boolean verbose) {
        ProtocolPrinter.printKafka("RECV downlink.result", r, verbose);
    }
}
