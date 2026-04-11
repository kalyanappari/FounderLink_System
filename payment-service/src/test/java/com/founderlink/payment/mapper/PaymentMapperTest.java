package com.founderlink.payment.mapper;

import com.founderlink.payment.dto.response.PaymentResponseDto;
import com.founderlink.payment.entity.Payment;
import com.founderlink.payment.entity.PaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class PaymentMapperTest {

    private final PaymentMapper mapper = new PaymentMapper();

    @Test
    @DisplayName("toResponseDto - should return null when input is null")
    void toResponseDto_Null() {
        assertNull(mapper.toResponseDto(null));
    }

    @Test
    @DisplayName("toResponseDto - should map all fields")
    void toResponseDto_Success() {
        Payment payment = new Payment();
        payment.setId(1L);
        payment.setInvestmentId(101L);
        payment.setInvestorId(201L);
        payment.setStartupId(301L);
        payment.setFounderId(401L);
        payment.setAmount(BigDecimal.valueOf(1000));
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setRazorpayPaymentId("pay_123");
        payment.setFailureReason("none");
        payment.setCreatedAt(LocalDateTime.now());
        payment.setUpdatedAt(LocalDateTime.now());

        PaymentResponseDto result = mapper.toResponseDto(payment);

        assertNotNull(result);
        assertEquals(payment.getId(), result.getId());
        assertEquals(payment.getAmount(), result.getAmount());
        assertEquals("pay_123", result.getExternalPaymentId());
    }
}
