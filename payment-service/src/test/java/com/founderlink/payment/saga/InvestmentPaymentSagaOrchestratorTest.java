package com.founderlink.payment.saga;

import com.founderlink.payment.entity.Payment;
import com.founderlink.payment.entity.PaymentStatus;
import com.founderlink.payment.event.InvestmentRejectedEvent;
import com.founderlink.payment.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvestmentPaymentSagaOrchestratorTest {

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private InvestmentPaymentSagaOrchestrator orchestrator;

    private InvestmentRejectedEvent event;

    @BeforeEach
    void setUp() {
        event = new InvestmentRejectedEvent(
                101L, 201L, 301L, 401L, new BigDecimal("1000.00"), "User cancelled"
        );
    }

    @Test
    @DisplayName("handleInvestmentRejected - no payment found")
    void handleInvestmentRejected_NoPaymentFound() {
        // Arrange
        when(paymentRepository.findByInvestmentId(event.getInvestmentId())).thenReturn(Optional.empty());

        // Act
        orchestrator.handleInvestmentRejected(event);

        // Assert
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    @DisplayName("handleInvestmentRejected - mark payment as failed")
    void handleInvestmentRejected_MarkAsFailed() {
        // Arrange
        Payment payment = new Payment();
        payment.setId(1L);
        payment.setStatus(PaymentStatus.PENDING);
        
        when(paymentRepository.findByInvestmentId(event.getInvestmentId())).thenReturn(Optional.of(payment));

        // Act
        orchestrator.handleInvestmentRejected(event);

        // Assert
        assertEquals(PaymentStatus.FAILED, payment.getStatus());
        verify(paymentRepository, times(1)).save(payment);
    }

    @Test
    @DisplayName("handleInvestmentRejected - already successful, do nothing")
    void handleInvestmentRejected_AlreadySuccessful() {
        // Arrange
        Payment payment = new Payment();
        payment.setId(1L);
        payment.setStatus(PaymentStatus.SUCCESS);
        
        when(paymentRepository.findByInvestmentId(event.getInvestmentId())).thenReturn(Optional.of(payment));

        // Act
        orchestrator.handleInvestmentRejected(event);

        // Assert
        assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
        verify(paymentRepository, never()).save(payment);
    }
}
