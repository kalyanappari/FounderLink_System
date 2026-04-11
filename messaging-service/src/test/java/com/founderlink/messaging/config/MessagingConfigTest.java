package com.founderlink.messaging.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import io.micrometer.observation.ObservationRegistry;
import io.swagger.v3.oas.models.OpenAPI;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MessagingConfigTest {

    @Test
    @DisplayName("RabbitMQConfig - beans are created correctly")
    void rabbitMQConfigBeans() {
        RabbitMQConfig config = new RabbitMQConfig();
        ReflectionTestUtils.setField(config, "exchange", "test.exchange");

        assertThat(config.exchange()).isInstanceOf(DirectExchange.class);
        assertThat(config.jsonMessageConverter()).isInstanceOf(MessageConverter.class);

        ConnectionFactory factory = mock(ConnectionFactory.class);
        ObservationRegistry registry = mock(ObservationRegistry.class);
        RabbitTemplate template = config.rabbitTemplate(factory, registry);

        assertThat(template).isNotNull();
        assertThat(template.getMessageConverter()).isInstanceOf(Jackson2JsonMessageConverter.class);
    }

    @Test
    @DisplayName("RedisConfig - beans are created correctly")
    void redisConfigBeans() {
        RedisConfig config = new RedisConfig();
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);

        CacheManager cacheManager = config.cacheManager(factory);
        assertThat(cacheManager).isNotNull();
    }

    @Test
    @DisplayName("OpenApiConfig - bean is created correctly")
    void openApiConfigBean() {
        OpenApiConfig config = new OpenApiConfig();
        OpenAPI openAPI = config.customOpenAPI();

        assertThat(openAPI).isNotNull();
        assertThat(openAPI.getInfo().getTitle()).contains("Messaging Service");
    }
}
