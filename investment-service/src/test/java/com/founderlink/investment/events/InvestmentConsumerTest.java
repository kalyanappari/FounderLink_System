package com.founderlink.investment.events;

import com.founderlink.investment.entity.Investment;
import com.founderlink.investment.entity.InvestmentStatus;
import com.founderlink.investment.repository.InvestmentRepository;
import com.founderlink.investment.service.InvestmentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvestmentConsumerTest {

    // ── PaymentResultEventConsumer ────────────────────────────────────────────

    @Mock
    private InvestmentService investmentService;

    @InjectMocks
    private PaymentResultEventConsumer paymentConsumer;

    @Mock
    private InvestmentRepository investmentRepository;

    @InjectMocks
    private StartupDeletedEventConsumer startupConsumer;

    // ── PaymentCompletedEvent / PaymentFailedEvent POJOs ─────────────────────

    @Test
    void paymentEvent_POJOs_ShouldWork() {
        PaymentCompletedEvent completed = new PaymentCompletedEvent(1L, 200L);
        assertThat(completed.getInvestmentId()).isEqualTo(1L);
        assertThat(new PaymentCompletedEvent()).isNotNull();

        PaymentFailedEvent failed = new PaymentFailedEvent(1L, 200L, "Failed");
        assertThat(failed.getInvestmentId()).isEqualTo(1L);
        assertThat(new PaymentFailedEvent()).isNotNull();

        StartupDeletedEvent deleted = new StartupDeletedEvent(100L, 5L);
        assertThat(deleted.getStartupId()).isEqualTo(100L);
        assertThat(new StartupDeletedEvent()).isNotNull();
    }

    // ── PaymentResultEventConsumer tests ─────────────────────────────────────

    @Test
    void handlePaymentCompleted_ShouldDelegate() {
        PaymentCompletedEvent event = new PaymentCompletedEvent(1L, 200L);
        paymentConsumer.handlePaymentCompleted(event);
        verify(investmentService).markCompletedFromPayment(1L);
    }

    @Test
    void handlePaymentFailed_ShouldDelegate() {
        PaymentFailedEvent event = new PaymentFailedEvent(1L, 200L, "reason");
        paymentConsumer.handlePaymentFailed(event);
        verify(investmentService).markPaymentFailedFromPayment(1L);
    }

    // ── StartupDeletedEventConsumer tests ─────────────────────────────────────

    @Test
    void handleStartupDeletedEvent_WithPendingAndApproved_ShouldMarkClosed() {
        Investment pending = new Investment();
        pending.setId(1L);
        pending.setStatus(InvestmentStatus.PENDING);

        Investment approved = new Investment();
        approved.setId(2L);
        approved.setStatus(InvestmentStatus.APPROVED);

        Investment completed = new Investment();
        completed.setId(3L);
        completed.setStatus(InvestmentStatus.COMPLETED);

        when(investmentRepository.findByStartupId(100L)).thenReturn(List.of(pending, approved, completed));

        startupConsumer.handleStartupDeletedEvent(new StartupDeletedEvent(100L, 5L));

        verify(investmentRepository, times(2)).save(any());
        assertThat(pending.getStatus()).isEqualTo(InvestmentStatus.STARTUP_CLOSED);
        assertThat(approved.getStatus()).isEqualTo(InvestmentStatus.STARTUP_CLOSED);
        assertThat(completed.getStatus()).isEqualTo(InvestmentStatus.COMPLETED); // unchanged
    }

    @Test
    void handleStartupDeletedEvent_Empty_ShouldReturn() {
        when(investmentRepository.findByStartupId(100L)).thenReturn(List.of());
        startupConsumer.handleStartupDeletedEvent(new StartupDeletedEvent(100L, 5L));
        verify(investmentRepository, never()).save(any());
    }

    @Test
    void handleStartupDeletedEvent_Exception_ShouldCatch() {
        when(investmentRepository.findByStartupId(anyLong())).thenThrow(new RuntimeException("DB Error"));
        // Should not propagate
        startupConsumer.handleStartupDeletedEvent(new StartupDeletedEvent(100L, 5L));
        verify(investmentRepository).findByStartupId(100L);
    }
}
