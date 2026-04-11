package com.founderlink.payment.query;

import com.founderlink.payment.dto.response.PaymentResponseDto;
import com.founderlink.payment.entity.Payment;
import com.founderlink.payment.entity.PaymentStatus;
import com.founderlink.payment.exception.PaymentNotFoundException;
import com.founderlink.payment.mapper.PaymentMapper;
import com.founderlink.payment.repository.PaymentRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentQueryServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentMapper paymentMapper;

    @InjectMocks
    private PaymentQueryService paymentQueryService;

    @Test
    @DisplayName("getPaymentById - success")
    void getPaymentById_Success() {
        // Arrange
        Long paymentId = 1L;
        Payment payment = new Payment();
        PaymentResponseDto responseDto = new PaymentResponseDto();
        
        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
        when(paymentMapper.toResponseDto(payment)).thenReturn(responseDto);

        // Act
        PaymentResponseDto result = paymentQueryService.getPaymentById(paymentId);

        // Assert
        assertEquals(responseDto, result);
    }

    @Test
    @DisplayName("getPaymentById - not found")
    void getPaymentById_NotFound() {
        // Arrange
        when(paymentRepository.findById(1L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(PaymentNotFoundException.class, () -> paymentQueryService.getPaymentById(1L));
    }

    @Test
    @DisplayName("getPaymentByInvestmentId - success")
    void getPaymentByInvestmentId_Success() {
        // Arrange
        Long investmentId = 101L;
        Payment payment = new Payment();
        PaymentResponseDto responseDto = new PaymentResponseDto();

        when(paymentRepository.findByInvestmentId(investmentId)).thenReturn(Optional.of(payment));
        when(paymentMapper.toResponseDto(payment)).thenReturn(responseDto);

        // Act
        PaymentResponseDto result = paymentQueryService.getPaymentByInvestmentId(investmentId);

        // Assert
        assertEquals(responseDto, result);
    }

    @Test
    @DisplayName("getPaymentByInvestmentId - not found")
    void getPaymentByInvestmentId_NotFound() {
        // Arrange
        when(paymentRepository.findByInvestmentId(101L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(PaymentNotFoundException.class, () -> paymentQueryService.getPaymentByInvestmentId(101L));
    }

    @Test
    @DisplayName("getPaymentStatus - success")
    void getPaymentStatus_Success() {
        // Arrange
        Long paymentId = 1L;
        Payment payment = new Payment();
        payment.setStatus(PaymentStatus.SUCCESS);

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        // Act
        PaymentStatus result = paymentQueryService.getPaymentStatus(paymentId);

        // Assert
        assertEquals(PaymentStatus.SUCCESS, result);
    }

    @Test
    @DisplayName("getPaymentStatus - not found")
    void getPaymentStatus_NotFound() {
        // Arrange
        when(paymentRepository.findById(1L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(PaymentNotFoundException.class, () -> paymentQueryService.getPaymentStatus(1L));
    }
}
