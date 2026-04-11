package com.founderlink.payment.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentResultEventPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private PaymentResultEventPublisher eventPublisher;

    private final String exchange = "test-exchange";
    private final String completedKey = "completed-key";
    private final String failedKey = "failed-key";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(eventPublisher, "exchange", exchange);
        ReflectionTestUtils.setField(eventPublisher, "paymentCompletedRoutingKey", completedKey);
        ReflectionTestUtils.setField(eventPublisher, "paymentFailedRoutingKey", failedKey);
    }

    @Test
    @DisplayName("publishPaymentCompleted - success")
    void publishPaymentCompleted_Success() {
        PaymentCompletedEvent event = new PaymentCompletedEvent(1L, 100L, 201L, 301L, 401L, BigDecimal.valueOf(500));
        
        eventPublisher.publishPaymentCompleted(event);

        verify(rabbitTemplate, times(1)).convertAndSend(eq(exchange), eq(completedKey), eq(event));
    }

    @Test
    @DisplayName("publishPaymentCompleted - exception")
    void publishPaymentCompleted_Exception() {
        PaymentCompletedEvent event = new PaymentCompletedEvent(1L, 100L, 201L, 301L, 401L, BigDecimal.valueOf(500));
        doThrow(new RuntimeException("Rabbit down")).when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> eventPublisher.publishPaymentCompleted(event));
        assertEquals("Failed to publish PAYMENT_COMPLETED event", ex.getMessage());
    }

    @Test
    @DisplayName("publishPaymentFailed - success")
    void publishPaymentFailed_Success() {
        PaymentFailedEvent event = new PaymentFailedEvent(1L, 100L, 201L, 301L, 401L, BigDecimal.valueOf(500), "REASON");

        eventPublisher.publishPaymentFailed(event);

        verify(rabbitTemplate, times(1)).convertAndSend(eq(exchange), eq(failedKey), eq(event));
    }

    @Test
    @DisplayName("publishPaymentFailed - exception")
    void publishPaymentFailed_Exception() {
        PaymentFailedEvent event = new PaymentFailedEvent(1L, 100L, 201L, 301L, 401L, BigDecimal.valueOf(500), "REASON");
        doThrow(new RuntimeException("Rabbit down")).when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> eventPublisher.publishPaymentFailed(event));
        assertEquals("Failed to publish PAYMENT_FAILED event", ex.getMessage());
    }
}
