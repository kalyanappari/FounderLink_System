package com.founderlink.startup.events;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StartupEventPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private StartupEventPublisher publisher;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(publisher, "exchange", "test.exchange");
        ReflectionTestUtils.setField(publisher, "startupRoutingKey", "test.created.key");
        ReflectionTestUtils.setField(publisher, "startupDeletedRoutingKey", "test.deleted.key");
    }

    @Test
    void publishStartupCreatedEvent_Success() {
        StartupCreatedEvent event = new StartupCreatedEvent(1L, "Test", 5L, "Tech", java.math.BigDecimal.TEN);
        publisher.publishStartupCreatedEvent(event);

        verify(rabbitTemplate).convertAndSend(eq("test.exchange"), eq("test.created.key"), eq(event));
    }

    @Test
    void publishStartupCreatedEvent_Failure() {
        StartupCreatedEvent event = new StartupCreatedEvent(1L, "Test", 5L, "Tech", java.math.BigDecimal.TEN);
        doThrow(new RuntimeException("Rabbit error")).when(rabbitTemplate)
                .convertAndSend(anyString(), anyString(), any(Object.class));

        assertThatThrownBy(() -> publisher.publishStartupCreatedEvent(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to publish STARTUP_CREATED");
    }

    @Test
    void publishStartupDeletedEvent_Success() {
        StartupDeletedEvent event = new StartupDeletedEvent(1L, 5L);
        publisher.publishStartupDeletedEvent(event);

        verify(rabbitTemplate).convertAndSend(eq("test.exchange"), eq("test.deleted.key"), eq(event));
    }

    @Test
    void publishStartupDeletedEvent_Failure() {
        StartupDeletedEvent event = new StartupDeletedEvent(1L, 5L);
        doThrow(new RuntimeException("Rabbit error")).when(rabbitTemplate)
                .convertAndSend(anyString(), anyString(), any(Object.class));

        assertThatThrownBy(() -> publisher.publishStartupDeletedEvent(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to publish STARTUP_DELETED");
    }
}
