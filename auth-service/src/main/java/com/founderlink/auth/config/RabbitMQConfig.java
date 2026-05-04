package com.founderlink.auth.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String PASSWORD_RESET_QUEUE       = "password-reset-queue";
    public static final String FOUNDERLINK_EXCHANGE        = "founderlink.exchange";
    public static final String PASSWORD_RESET_ROUTING_KEY  = "password.reset";

    public static final String USER_REGISTERED_ROUTING_KEY = "user.registered";
    public static final String USER_REGISTERED_QUEUE       = "user-registered-queue";

    // ── Email Verification ────────────────────────────────────────────────────
    public static final String EMAIL_VERIFICATION_QUEUE       = "email-verification-queue";
    public static final String EMAIL_VERIFICATION_ROUTING_KEY = "email.verification";

    @Bean
    public Queue passwordResetQueue() {
        return new Queue(PASSWORD_RESET_QUEUE, true);
    }

    @Bean
    public Queue userRegisteredQueue() {
        return new Queue(USER_REGISTERED_QUEUE, true);
    }

    @Bean
    public DirectExchange founderLinkExchange() {
        return new DirectExchange(FOUNDERLINK_EXCHANGE);
    }

    @Bean
    public Binding userRegisteredBinding(Queue userRegisteredQueue, DirectExchange founderLinkExchange) {
        return BindingBuilder.bind(userRegisteredQueue)
            .to(founderLinkExchange)
            .with(USER_REGISTERED_ROUTING_KEY);
    }

    @Bean
    public Binding passwordResetBinding(Queue passwordResetQueue, DirectExchange founderLinkExchange) {
        return BindingBuilder.bind(passwordResetQueue)
            .to(founderLinkExchange)
            .with(PASSWORD_RESET_ROUTING_KEY);
    }

    @Bean
    public Queue emailVerificationQueue() {
        return new Queue(EMAIL_VERIFICATION_QUEUE, true);
    }

    @Bean
    public Binding emailVerificationBinding(Queue emailVerificationQueue, DirectExchange founderLinkExchange) {
        return BindingBuilder.bind(emailVerificationQueue)
            .to(founderLinkExchange)
            .with(EMAIL_VERIFICATION_ROUTING_KEY);
    }

    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter());
        return rabbitTemplate;
    }
}
