package com.founderlink.auth.publisher;

import com.founderlink.auth.config.RabbitMQConfig;
import com.founderlink.auth.dto.UserRegisteredEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UserRegisteredEventPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private UserRegisteredEventPublisher publisher;

    @Test
    void publishUserRegisteredEventShouldSendToRabbitMQ() {
        UserRegisteredEvent event = UserRegisteredEvent.builder()
                .userId(101L)
                .email("alice@founderlink.com")
                .name("Alice Founder")
                .role("FOUNDER")
                .build();

        doNothing().when(rabbitTemplate).convertAndSend(any(String.class), any(String.class), any(Object.class));

        assertDoesNotThrow(() -> publisher.publishUserRegisteredEvent(event));

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.FOUNDERLINK_EXCHANGE),
                eq(RabbitMQConfig.USER_REGISTERED_ROUTING_KEY),
                eq(event)
        );
    }

    @Test
    void publishUserRegisteredEventShouldNotThrowWhenRabbitFails() {
        UserRegisteredEvent event = UserRegisteredEvent.builder()
                .userId(202L)
                .email("fail@founderlink.com")
                .name("Fail User")
                .role("INVESTOR")
                .build();

        doThrow(new RuntimeException("RabbitMQ connection refused"))
                .when(rabbitTemplate).convertAndSend(any(String.class), any(String.class), any(Object.class));

        // UserRegisteredEventPublisher swallows the exception (unlike PasswordResetEventPublisher)
        assertDoesNotThrow(() -> publisher.publishUserRegisteredEvent(event));
    }
}
