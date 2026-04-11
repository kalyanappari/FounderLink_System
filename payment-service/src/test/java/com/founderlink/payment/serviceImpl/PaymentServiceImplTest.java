package com.founderlink.payment.serviceImpl;

import com.founderlink.payment.command.PaymentCommandService;
import com.founderlink.payment.dto.response.ConfirmPaymentResponse;
import com.founderlink.payment.dto.response.CreateOrderResponse;
import com.founderlink.payment.dto.response.PaymentResponseDto;
import com.founderlink.payment.entity.PaymentStatus;
import com.founderlink.payment.query.PaymentQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceImplTest {

    @Mock
    private PaymentCommandService commandService;

    @Mock
    private PaymentQueryService queryService;

    @InjectMocks
    private PaymentServiceImpl paymentService;

    @Test
    @DisplayName("getPaymentById - delegates to queryService")
    void getPaymentById_Delegates() {
        Long id = 1L;
        PaymentResponseDto expected = new PaymentResponseDto();
        when(queryService.getPaymentById(id)).thenReturn(expected);

        PaymentResponseDto result = paymentService.getPaymentById(id);

        assertEquals(expected, result);
        verify(queryService).getPaymentById(id);
    }

    @Test
    @DisplayName("getPaymentByInvestmentId - delegates to queryService")
    void getPaymentByInvestmentId_Delegates() {
        Long id = 101L;
        PaymentResponseDto expected = new PaymentResponseDto();
        when(queryService.getPaymentByInvestmentId(id)).thenReturn(expected);

        PaymentResponseDto result = paymentService.getPaymentByInvestmentId(id);

        assertEquals(expected, result);
        verify(queryService).getPaymentByInvestmentId(id);
    }

    @Test
    @DisplayName("getPaymentStatus - delegates to queryService")
    void getPaymentStatus_Delegates() {
        Long id = 1L;
        when(queryService.getPaymentStatus(id)).thenReturn(PaymentStatus.SUCCESS);

        PaymentStatus result = paymentService.getPaymentStatus(id);

        assertEquals(PaymentStatus.SUCCESS, result);
        verify(queryService).getPaymentStatus(id);
    }

    @Test
    @DisplayName("createOrder - delegates to commandService")
    void createOrder_Delegates() {
        Long id = 101L;
        CreateOrderResponse expected = new CreateOrderResponse("order_1", null, "INR", 101L);
        when(commandService.createOrder(id)).thenReturn(expected);

        CreateOrderResponse result = paymentService.createOrder(id);

        assertEquals(expected, result);
        verify(commandService).createOrder(id);
    }

    @Test
    @DisplayName("confirmPayment - delegates to commandService")
    void confirmPayment_Delegates() {
        String orderId = "order_1";
        String payId = "pay_1";
        String sign = "sign_1";
        ConfirmPaymentResponse expected = new ConfirmPaymentResponse("SUCCESS", 101L);
        when(commandService.confirmPayment(orderId, payId, sign)).thenReturn(expected);

        ConfirmPaymentResponse result = paymentService.confirmPayment(orderId, payId, sign);

        assertEquals(expected, result);
        verify(commandService).confirmPayment(orderId, payId, sign);
    }
}
