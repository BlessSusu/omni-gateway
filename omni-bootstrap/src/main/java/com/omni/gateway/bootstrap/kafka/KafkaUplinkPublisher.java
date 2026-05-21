package com.omni.gateway.bootstrap.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.omni.gateway.bootstrap.OmniGatewayProperties;
import com.omni.gateway.core.backpressure.BackpressureController;
import com.omni.gateway.core.model.ThingModel;
import com.omni.gateway.core.uplink.UplinkPublisher;
import com.omni.gateway.network.observability.GatewayTracing;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Tracer;
import io.micrometer.core.instrument.Timer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class KafkaUplinkPublisher implements UplinkPublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaUplinkPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OmniGatewayProperties properties;
    private final ObjectMapper objectMapper;
    private final Timer publishTimer;
    private final BackpressureController backpressure;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final Tracer tracer;

    public KafkaUplinkPublisher(KafkaTemplate<String, String> kafkaTemplate,
                                OmniGatewayProperties properties,
                                MeterRegistry meterRegistry,
                                BackpressureController backpressure,
                                Tracer tracer) {
        this.kafkaTemplate = kafkaTemplate;
        this.properties = properties;
        this.backpressure = backpressure;
        this.tracer = tracer;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        this.publishTimer = Timer.builder("omni_kafka_publish_seconds")
                .tag("topic", properties.getKafka().getUplinkTopic())
                .register(meterRegistry);
    }

    @Override
    public CompletableFuture<Void> publish(ThingModel model) {
        return GatewayTracing.inSpan(tracer, "kafka.uplink.publish", () -> publishInternal(model));
    }

    private CompletableFuture<Void> publishInternal(ThingModel model) {
        if (!properties.getKafka().isEnabled()) {
            log.debug("Kafka disabled, skip uplink deviceId={}", model.getDeviceId());
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<Void> result = new CompletableFuture<>();
        try {
            String traceId = GatewayTracing.currentTraceId(tracer);
            if (traceId != null && model.getTraceId() == null) {
                model.setTraceId(traceId);
            }
            String json = objectMapper.writeValueAsString(model);
            ProducerRecord<String, String> record = new ProducerRecord<>(
                    properties.getKafka().getUplinkTopic(),
                    model.getDeviceId(),
                    json);
            Timer.Sample sample = Timer.start();
            kafkaTemplate.send(record).whenComplete((SendResult<String, String> sr, Throwable ex) -> {
                sample.stop(publishTimer);
                if (ex != null) {
                    if (consecutiveFailures.incrementAndGet() >= properties.getKafka().getFailureThreshold()) {
                        backpressure.onKafkaDegraded();
                    }
                    result.completeExceptionally(ex);
                } else {
                    consecutiveFailures.set(0);
                    if (backpressure.isUnderPressure()) {
                        backpressure.onKafkaRecovered();
                    }
                    result.complete(null);
                }
            });
        } catch (Exception e) {
            result.completeExceptionally(e);
        }
        return result;
    }
}
