package com.omni.gateway.bootstrap.session;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.omni.gateway.core.session.SessionRoute;
import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class RedisSessionIndexTest {

    @Container
    static RedisContainer redis = new RedisContainer(RedisContainer.DEFAULT_IMAGE_NAME);

    private StringRedisTemplate template;
    private RedisSessionIndex index;

    @BeforeEach
    void setUp() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(redis.getHost(), redis.getFirstMappedPort());
        factory.afterPropertiesSet();
        template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();
        index = new RedisSessionIndex(template, new ObjectMapper(), "node-a");
    }

    @AfterEach
    void tearDown() {
        if (template != null && template.getConnectionFactory() instanceof LettuceConnectionFactory lcf) {
            lcf.destroy();
        }
    }

    @Test
    void registerLookupUnregisterCas() {
        index.register("dev-1", "node-a", "simple-frame", 60);
        SessionRoute route = index.lookup("dev-1").orElseThrow();
        assertThat(route.nodeId()).isEqualTo("node-a");
        assertThat(route.protocol()).isEqualTo("simple-frame");

        index.unregister("dev-1", "node-b");
        assertThat(index.lookup("dev-1")).isPresent();

        index.unregister("dev-1", "node-a");
        assertThat(index.lookup("dev-1")).isEmpty();
    }
}
