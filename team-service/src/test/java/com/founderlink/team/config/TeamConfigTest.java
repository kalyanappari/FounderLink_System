package com.founderlink.team.config;

import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.cache.RedisCacheManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TeamConfigTest {

    @Test
    void rabbitMQConfig_Beans_ShouldInitializeCorrectly() {
        RabbitMQConfig config = new RabbitMQConfig();
        ReflectionTestUtils.setField(config, "exchange", "test.exchange");
        ReflectionTestUtils.setField(config, "teamQueue", "test.queue");
        ReflectionTestUtils.setField(config, "teamRoutingKey", "test.rk");
        ReflectionTestUtils.setField(config, "teamAcceptedQueue", "test.acc.queue");
        ReflectionTestUtils.setField(config, "teamAcceptedRoutingKey", "test.acc.rk");
        ReflectionTestUtils.setField(config, "teamRejectedQueue", "test.rej.queue");
        ReflectionTestUtils.setField(config, "teamRejectedRoutingKey", "test.rej.rk");
        ReflectionTestUtils.setField(config, "startupDeletedQueue", "test.del.queue");
        ReflectionTestUtils.setField(config, "startupDeletedRoutingKey", "test.del.rk");

        assertThat(config.founderLinkExchange()).isInstanceOf(DirectExchange.class);
        assertThat(config.teamQueue()).isInstanceOf(Queue.class);
        assertThat(config.teamAcceptedQueue()).isInstanceOf(Queue.class);
        assertThat(config.teamRejectedQueue()).isInstanceOf(Queue.class);
        assertThat(config.startupDeletedQueue()).isInstanceOf(Queue.class);
        
        assertThat(config.teamInviteBinding()).isInstanceOf(Binding.class);
        assertThat(config.teamAcceptedBinding()).isInstanceOf(Binding.class);
        assertThat(config.teamRejectedBinding()).isInstanceOf(Binding.class);
        assertThat(config.startupDeletedBinding()).isInstanceOf(Binding.class);
        
        assertThat(config.messageConverter()).isNotNull();

        ConnectionFactory cf = mock(ConnectionFactory.class);
        ObservationRegistry or = mock(ObservationRegistry.class);
        RabbitTemplate template = config.rabbitTemplate(cf, or);
        assertThat(template).isNotNull();
        // Reflection check for observationEnabled if needed, similar to Startup-Service
        Object observationEnabled = ReflectionTestUtils.getField(template, "observationEnabled");
        if (observationEnabled != null) {
            assertThat(observationEnabled).isEqualTo(true);
        }
    }

    @Test
    void redisConfig_CacheManager_ShouldInitialize() {
        RedisConfig config = new RedisConfig();
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        RedisCacheManager manager = config.cacheManager(factory);
        assertThat(manager).isNotNull();
    }

    @Test
    void openApiConfig_Bean_ShouldInitialize() {
        OpenApiConfig config = new OpenApiConfig();
        assertThat(config.customOpenAPI()).isNotNull();
    }
}
