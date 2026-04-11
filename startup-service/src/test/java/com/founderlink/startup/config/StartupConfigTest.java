package com.founderlink.startup.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class StartupConfigTest {

    @Test
    void testOpenApiConfig() {
        OpenApiConfig config = new OpenApiConfig();
        OpenAPI api = config.customOpenAPI();
        assertThat(api).isNotNull();
        assertThat(api.getInfo().getTitle()).contains("Startup Service");
    }

    @Test
    void testRedisConfig() {
        RedisConfig config = new RedisConfig();
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        RedisCacheManager manager = config.cacheManager(factory);
        assertThat(manager).isNotNull();
    }

    @Test
    void testRabbitMQConfig() {
        RabbitMQConfig config = new RabbitMQConfig();
        
        // Inject values
        ReflectionTestUtils.setField(config, "exchange", "test.exchange");
        ReflectionTestUtils.setField(config, "startupQueue", "test.queue");
        ReflectionTestUtils.setField(config, "startupRoutingKey", "test.routing");
        ReflectionTestUtils.setField(config, "startupDeletedRoutingKey", "test.deleted.routing");
        ReflectionTestUtils.setField(config, "investmentDeletedQueue", "test.inv.deleted.queue");
        ReflectionTestUtils.setField(config, "teamDeletedQueue", "test.team.deleted.queue");

        DirectExchange exchange = config.founderLinkExchange();
        assertThat(exchange.getName()).isEqualTo("test.exchange");

        Queue queue = config.startupQueue();
        assertThat(queue.getName()).isEqualTo("test.queue");

        assertThat(config.investmentDeletedQueue().getName()).isEqualTo("test.inv.deleted.queue");
        assertThat(config.teamDeletedQueue().getName()).isEqualTo("test.team.deleted.queue");

        Binding binding = config.startupCreatedBinding();
        assertThat(binding.getExchange()).isEqualTo("test.exchange");
        assertThat(binding.getRoutingKey()).isEqualTo("test.routing");

        assertThat(config.investmentDeletedBinding().getRoutingKey()).isEqualTo("test.deleted.routing");
        assertThat(config.teamDeletedBinding().getRoutingKey()).isEqualTo("test.deleted.routing");

        assertThat(config.messageConverter()).isNotNull();

        ConnectionFactory connectionFactory = mock(ConnectionFactory.class);
        ObservationRegistry observationRegistry = mock(ObservationRegistry.class);
        RabbitTemplate template = config.rabbitTemplate(connectionFactory, observationRegistry);
        assertThat(template).isNotNull();
        
        // Use ReflectionTestUtils to verify the private field if the getter is missing in this version
        Boolean observationEnabled = (Boolean) ReflectionTestUtils.getField(template, "observationEnabled");
        assertThat(observationEnabled).isTrue();
    }
}
