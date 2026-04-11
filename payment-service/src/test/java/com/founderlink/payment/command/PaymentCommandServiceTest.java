package com.founderlink.payment.command;

import com.founderlink.payment.dto.response.ConfirmPaymentResponse;
import com.founderlink.payment.dto.response.CreateOrderResponse;
import com.founderlink.payment.service.RazorpayService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentCommandServiceTest {

    @Mock
    private RazorpayService razorpayService;

    @InjectMocks
    private PaymentCommandService paymentCommandService;

    @Test
    @DisplayName("createOrder - delegates to RazorpayService")
    void createOrder_Delegates() {
        // Arrange
        Long investmentId = 1L;
        CreateOrderResponse expected = new CreateOrderResponse("order_1", null, "INR", 1L);
        when(razorpayService.createOrder(investmentId)).thenReturn(expected);

        // Act
        CreateOrderResponse result = paymentCommandService.createOrder(investmentId);

        // Assert
        assertEquals(expected, result);
        verify(razorpayService, times(1)).createOrder(investmentId);
    }

    @Test
    @DisplayName("confirmPayment - delegates to RazorpayService")
    void confirmPayment_Delegates() {
        // Arrange
        String orderId = "order_1";
        String payId = "pay_1";
        String sign = "sign_1";
        ConfirmPaymentResponse expected = new ConfirmPaymentResponse("SUCCESS", 1L);
        when(razorpayService.confirmPayment(orderId, payId, sign)).thenReturn(expected);

        // Act
        ConfirmPaymentResponse result = paymentCommandService.confirmPayment(orderId, payId, sign);

        // Assert
        assertEquals(expected, result);
        verify(razorpayService, times(1)).confirmPayment(orderId, payId, sign);
    }
}
