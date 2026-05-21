package com.omni.gateway.bootstrap;

import com.omni.gateway.bootstrap.test.TcpTestClient;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EmbeddedKafka(
        partitions = 1,
        topics = {
                "omni.device.uplink",
                "omni.device.lifecycle",
                "omni.command.downlink",
                "omni.command.downlink.result"
        })
@ActiveProfiles("test")
class SimpleFrameIntegrationTest {

    private static final int TCP_PORT = 19000;
    private static final String DEVICE_ID = "integration-device-001";

    @Autowired
    private EmbeddedKafkaBroker embeddedKafka;

    @DynamicPropertySource
    static void registerKafka(DynamicPropertyRegistry registry) {
        registry.add("omni.kafka.bootstrap-servers",
                () -> System.getProperty("spring.embedded.kafka.brokers"));
    }

    @Test
    void simpleFrameUplinkAndDownlink() throws Exception {
        await().atMost(30, TimeUnit.SECONDS).until(() -> portOpen("127.0.0.1", TCP_PORT));

        try (TcpTestClient client = new TcpTestClient("127.0.0.1", TCP_PORT)) {
            client.sendAuth(DEVICE_ID);
            var authResp = client.readFrame();
            assertThat(authResp.getType()).isEqualToIgnoringCase("auth_ok");

            client.sendTelemetry("telemetry", "{\"temp\":26}");

            String uplinkGroup = "test-uplink-" + UUID.randomUUID();
            Map<String, Object> consumerProps = new HashMap<>(KafkaTestUtils.consumerProps(
                    embeddedKafka.getBrokersAsString(), uplinkGroup, "true"));
            consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
            Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(
                    consumerProps, new StringDeserializer(), new StringDeserializer()).createConsumer();
            consumer.subscribe(java.util.List.of("omni.device.uplink"));

            await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
                var records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(3));
                boolean found = false;
                for (ConsumerRecord<String, String> r : records) {
                    if (r.value().contains(DEVICE_ID) && r.value().contains("telemetry")) {
                        found = true;
                        break;
                    }
                }
                assertThat(found).as("uplink kafka record").isTrue();
            });
            consumer.close();

            String messageId = "it-cmd-" + UUID.randomUUID();
            String cmd = "{\"messageId\":\"" + messageId + "\",\"deviceId\":\"" + DEVICE_ID
                    + "\",\"protocol\":\"simple-frame\",\"commandType\":\"setParam\","
                    + "\"payload\":{\"k\":1},\"timeoutMs\":5000}";

            String resultGroup = "test-result-" + UUID.randomUUID();
            Map<String, Object> resultConsumerProps = new HashMap<>(KafkaTestUtils.consumerProps(
                    embeddedKafka.getBrokersAsString(), resultGroup, "true"));
            resultConsumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
            Consumer<String, String> resultConsumer = new DefaultKafkaConsumerFactory<>(
                    resultConsumerProps, new StringDeserializer(), new StringDeserializer()).createConsumer();
            resultConsumer.subscribe(java.util.List.of("omni.command.downlink.result"));

            Map<String, Object> producerProps = KafkaTestUtils.producerProps(embeddedKafka.getBrokersAsString());
            ProducerFactory<String, String> pf = new DefaultKafkaProducerFactory<>(
                    producerProps, new StringSerializer(), new StringSerializer());
            KafkaTemplate<String, String> template = new KafkaTemplate<>(pf);
            template.send(new ProducerRecord<>("omni.command.downlink", DEVICE_ID, cmd)).get(5, TimeUnit.SECONDS);

            var downlink = client.readFrame();
            assertThat(downlink.getType()).isEqualTo("setParam");
            client.sendAck(messageId);

            await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
                var records = KafkaTestUtils.getRecords(resultConsumer, Duration.ofSeconds(3));
                boolean success = false;
                for (ConsumerRecord<String, String> r : records) {
                    if (r.value().contains(messageId) && r.value().contains("SUCCESS")) {
                        success = true;
                        break;
                    }
                }
                assertThat(success).as("downlink result").isTrue();
            });
            resultConsumer.close();
        }
    }

    private static boolean portOpen(String host, int port) {
        try (var s = new java.net.Socket(host, port)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
