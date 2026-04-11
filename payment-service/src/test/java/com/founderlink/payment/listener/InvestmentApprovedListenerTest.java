package com.founderlink.payment.listener;

import com.founderlink.payment.entity.Payment;
import com.founderlink.payment.event.InvestmentApprovedEvent;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InvestmentApprovedListenerTest {

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private InvestmentApprovedListener investmentApprovedListener;

    private InvestmentApprovedEvent event;

    @BeforeEach
    void setUp() {
        event = new InvestmentApprovedEvent(
                101L, 201L, 301L, 401L, new BigDecimal("1000.00")
        );
    }

    @Test
    @DisplayName("handleInvestmentApproved - success")
    void handleInvestmentApproved_Success() {
        // Arrange
        when(paymentRepository.findByInvestmentId(event.getInvestmentId())).thenReturn(Optional.empty());

        // Act
        investmentApprovedListener.handleInvestmentApproved(event);

        // Assert
        verify(paymentRepository, times(1)).save(any(Payment.class));
    }

    @Test
    @DisplayName("handleInvestmentApproved - skips if already exists")
    void handleInvestmentApproved_Duplicate() {
        // Arrange
        when(paymentRepository.findByInvestmentId(event.getInvestmentId())).thenReturn(Optional.of(new Payment()));

        // Act
        investmentApprovedListener.handleInvestmentApproved(event);

        // Assert
        verify(paymentRepository, never()).save(any(Payment.class));
    }
}
