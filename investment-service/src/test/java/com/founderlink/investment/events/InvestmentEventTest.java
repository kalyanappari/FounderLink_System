package com.founderlink.investment.events;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvestmentEventTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private InvestmentEventPublisher publisher;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(publisher, "exchange", "ex");
        ReflectionTestUtils.setField(publisher, "investmentRoutingKey", "r1");
        ReflectionTestUtils.setField(publisher, "investmentApprovedRoutingKey", "r2");
        ReflectionTestUtils.setField(publisher, "investmentRejectedRoutingKey", "r3");
    }

    @Test
    void event_POJOs_ShouldWork() {
        InvestmentCreatedEvent created = new InvestmentCreatedEvent(1L, 100L, 200L, 5L, new BigDecimal("1000.00"));
        assertThat(created.getInvestmentId()).isEqualTo(1L);

        InvestmentApprovedEvent approved = new InvestmentApprovedEvent(1L, 200L, 5L, 100L, new BigDecimal("1000.00"));
        assertThat(approved.getInvestmentId()).isEqualTo(1L);

        InvestmentRejectedEvent rejected = new InvestmentRejectedEvent(1L, 200L, 5L, 100L, new BigDecimal("1000.00"), "msg");
        assertThat(rejected.getRejectionReason()).isEqualTo("msg");
        
        // No-args for data binding
        assertThat(new InvestmentCreatedEvent()).isNotNull();
        assertThat(new InvestmentApprovedEvent()).isNotNull();
        assertThat(new InvestmentRejectedEvent()).isNotNull();
    }

    @Test
    void publishInvestmentCreatedEvent_Success() {
        InvestmentCreatedEvent event = new InvestmentCreatedEvent(1L, 100L, 200L, 5L, new BigDecimal("1000.00"));
        publisher.publishInvestmentCreatedEvent(event);
        verify(rabbitTemplate).convertAndSend(eq("ex"), eq("r1"), eq(event));
    }

    @Test
    void publishInvestmentCreatedEvent_Failure() {
        doThrow(new RuntimeException("Rabbit error")).when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));
        assertThatThrownBy(() -> publisher.publishInvestmentCreatedEvent(new InvestmentCreatedEvent()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to publish");
    }

    @Test
    void publishInvestmentApprovedEvent_Success() {
        InvestmentApprovedEvent event = new InvestmentApprovedEvent(1L, 200L, 5L, 100L, new BigDecimal("1000.00"));
        publisher.publishInvestmentApprovedEvent(event);
        verify(rabbitTemplate).convertAndSend(eq("ex"), eq("r2"), eq(event));
    }

    @Test
    void publishInvestmentApprovedEvent_Failure() {
        doThrow(new RuntimeException("Rabbit error")).when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));
        assertThatThrownBy(() -> publisher.publishInvestmentApprovedEvent(new InvestmentApprovedEvent()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void publishInvestmentRejectedEvent_Success() {
        InvestmentRejectedEvent event = new InvestmentRejectedEvent(1L, 200L, 5L, 100L, new BigDecimal("1000.00"), "msg");
        publisher.publishInvestmentRejectedEvent(event);
        verify(rabbitTemplate).convertAndSend(eq("ex"), eq("r3"), eq(event));
    }

    @Test
    void publishInvestmentRejectedEvent_Failure() {
        doThrow(new RuntimeException("Rabbit error")).when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Object.class));
        assertThatThrownBy(() -> publisher.publishInvestmentRejectedEvent(new InvestmentRejectedEvent()))
                .isInstanceOf(IllegalStateException.class);
    }
}
