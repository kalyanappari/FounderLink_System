package com.founderlink.auth.publisher;

import com.founderlink.auth.config.RabbitMQConfig;
import com.founderlink.auth.dto.PasswordResetEmailEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PasswordResetEventPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private PasswordResetEventPublisher publisher;

    @Test
    void publishPasswordResetEventShouldSendToRabbitMQ() {
        PasswordResetEmailEvent event = PasswordResetEmailEvent.builder()
                .email("user@founderlink.com")
                .pin("123456")
                .userName("Test User")
                .build();

        doNothing().when(rabbitTemplate).convertAndSend(any(String.class), any(String.class), any(Object.class));

        assertDoesNotThrow(() -> publisher.publishPasswordResetEvent(event));

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.FOUNDERLINK_EXCHANGE),
                eq(RabbitMQConfig.PASSWORD_RESET_ROUTING_KEY),
                eq(event)
        );
    }

    @Test
    void publishPasswordResetEventShouldThrowIllegalStateWhenRabbitFails() {
        PasswordResetEmailEvent event = PasswordResetEmailEvent.builder()
                .email("user@founderlink.com")
                .pin("654321")
                .userName("Fail User")
                .build();

        doThrow(new RuntimeException("RabbitMQ connection refused"))
                .when(rabbitTemplate).convertAndSend(any(String.class), any(String.class), any(Object.class));

        assertThatThrownBy(() -> publisher.publishPasswordResetEvent(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to publish password reset event");
    }
}
