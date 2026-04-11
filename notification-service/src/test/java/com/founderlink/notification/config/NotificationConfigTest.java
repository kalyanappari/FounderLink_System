package com.founderlink.notification.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import io.swagger.v3.oas.models.OpenAPI;
import org.springframework.test.util.ReflectionTestUtils;
import io.micrometer.observation.ObservationRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class NotificationConfigTest {

    @Test
    @DisplayName("RabbitMQConfig - beans are created correctly")
    void rabbitMQConfigBeans() {
        RabbitMQConfig config = new RabbitMQConfig();
        ReflectionTestUtils.setField(config, "exchange", "test.exchange");
        ReflectionTestUtils.setField(config, "userRegisteredQueue", "q.user");
        ReflectionTestUtils.setField(config, "teamAcceptedQueue", "q.team.acc");
        ReflectionTestUtils.setField(config, "teamRejectedQueue", "q.team.rej");
        ReflectionTestUtils.setField(config, "investmentApprovedQueue", "q.inv.acc");
        ReflectionTestUtils.setField(config, "investmentRejectedQueue", "q.inv.rej");
        ReflectionTestUtils.setField(config, "paymentCompletedQueue", "q.pay.acc");
        ReflectionTestUtils.setField(config, "paymentFailedQueue", "q.pay.fail");
        ReflectionTestUtils.setField(config, "startupQueue", "q.startup");
        ReflectionTestUtils.setField(config, "investmentQueue", "q.investment");
        ReflectionTestUtils.setField(config, "teamQueue", "q.team");
        ReflectionTestUtils.setField(config, "messagingQueue", "q.messaging");
        
        assertThat(config.exchange()).isInstanceOf(DirectExchange.class);
        assertThat(config.userRegisteredQueue()).isInstanceOf(Queue.class);
        assertThat(config.teamAcceptedQueue()).isInstanceOf(Queue.class);
        assertThat(config.teamRejectedQueue()).isInstanceOf(Queue.class);
        assertThat(config.investmentApprovedQueue()).isInstanceOf(Queue.class);
        assertThat(config.investmentRejectedQueue()).isInstanceOf(Queue.class);
        assertThat(config.paymentCompletedQueue()).isInstanceOf(Queue.class);
        assertThat(config.paymentFailedQueue()).isInstanceOf(Queue.class);
        assertThat(config.startupQueue()).isInstanceOf(Queue.class);
        assertThat(config.investmentQueue()).isInstanceOf(Queue.class);
        assertThat(config.teamQueue()).isInstanceOf(Queue.class);
        assertThat(config.messagingQueue()).isInstanceOf(Queue.class);
        assertThat(config.passwordResetQueue()).isInstanceOf(Queue.class);

        DirectExchange exchange = config.exchange();

        assertThat(config.userRegisteredBinding(config.userRegisteredQueue(), exchange)).isInstanceOf(Binding.class);
        assertThat(config.teamAcceptedBinding(config.teamAcceptedQueue(), exchange)).isInstanceOf(Binding.class);
        assertThat(config.teamRejectedBinding(config.teamRejectedQueue(), exchange)).isInstanceOf(Binding.class);
        assertThat(config.investmentApprovedBinding(config.investmentApprovedQueue(), exchange)).isInstanceOf(Binding.class);
        assertThat(config.investmentRejectedBinding(config.investmentRejectedQueue(), exchange)).isInstanceOf(Binding.class);
        assertThat(config.paymentCompletedBinding(config.paymentCompletedQueue(), exchange)).isInstanceOf(Binding.class);
        assertThat(config.paymentFailedBinding(config.paymentFailedQueue(), exchange)).isInstanceOf(Binding.class);
        
        assertThat(config.startupBinding(config.startupQueue(), exchange)).isInstanceOf(Binding.class);
        assertThat(config.investmentBinding(config.investmentQueue(), exchange)).isInstanceOf(Binding.class);
        assertThat(config.teamBinding(config.teamQueue(), exchange)).isInstanceOf(Binding.class);
        assertThat(config.messagingBinding(config.messagingQueue(), exchange)).isInstanceOf(Binding.class);
        assertThat(config.passwordResetBinding(config.passwordResetQueue(), exchange)).isInstanceOf(Binding.class);

        assertThat(config.messageConverter()).isInstanceOf(MessageConverter.class);
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
        assertThat(openAPI.getInfo().getTitle()).contains("Notification Service");
    }
}
