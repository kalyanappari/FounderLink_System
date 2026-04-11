package com.founderlink.payment.service;

import com.founderlink.payment.client.WalletServiceClient;
import com.founderlink.payment.dto.external.WalletDepositRequestDto;
import com.founderlink.payment.dto.response.ConfirmPaymentResponse;
import com.founderlink.payment.dto.response.CreateOrderResponse;
import com.founderlink.payment.entity.Payment;
import com.founderlink.payment.entity.PaymentStatus;
import com.founderlink.payment.event.PaymentResultEventPublisher;
import com.founderlink.payment.exception.PaymentGatewayException;
import com.founderlink.payment.exception.PaymentNotFoundException;
import com.founderlink.payment.repository.PaymentRepository;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class RazorpayServiceTest {

    @Mock
    private RazorpayClient razorpayClient;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentResultEventPublisher paymentResultEventPublisher;

    @Mock
    private WalletServiceClient walletServiceClient;

    @InjectMocks
    private RazorpayService razorpayService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(razorpayService, "keySecret", "dummy_secret");
    }

    @Test
    void createOrder_Success() throws Exception {
        Long investmentId = 1L;
        Payment payment = new Payment();
        payment.setId(100L);
        payment.setAmount(BigDecimal.valueOf(500));
        payment.setInvestmentId(investmentId);
        payment.setStatus(PaymentStatus.PENDING);

        when(paymentRepository.findByInvestmentId(investmentId)).thenReturn(Optional.of(payment));
        
        // Mocking deeply nested Razorpay client
        com.razorpay.OrderClient mockOrderClient = mock(com.razorpay.OrderClient.class);
        ReflectionTestUtils.setField(razorpayClient, "orders", mockOrderClient);
        
        Order mockOrder = mock(Order.class);
        when(mockOrder.get("id")).thenReturn("order_new");
        when(mockOrderClient.create(any(JSONObject.class))).thenReturn(mockOrder);

        // Act
        CreateOrderResponse response = razorpayService.createOrder(investmentId);

        // Assert
        assertEquals("order_new", response.getOrderId());
        assertEquals(PaymentStatus.INITIATED, payment.getStatus());
        verify(paymentRepository).save(payment);
    }

    @Test
    void createOrder_AlreadyCompleted() {
        Long investmentId = 1L;
        Payment payment = new Payment();
        payment.setInvestmentId(investmentId);
        payment.setStatus(PaymentStatus.SUCCESS);

        when(paymentRepository.findByInvestmentId(investmentId)).thenReturn(Optional.of(payment));

        assertThrows(PaymentGatewayException.class, () -> razorpayService.createOrder(investmentId));
    }

    @Test
    void createOrder_Idempotency() {
        Long investmentId = 1L;
        Payment payment = new Payment();
        payment.setInvestmentId(investmentId);
        payment.setAmount(BigDecimal.valueOf(500));
        payment.setStatus(PaymentStatus.INITIATED);
        payment.setRazorpayOrderId("order_existing");

        when(paymentRepository.findByInvestmentId(investmentId)).thenReturn(Optional.of(payment));

        CreateOrderResponse response = razorpayService.createOrder(investmentId);

        assertNotNull(response);
        assertEquals("order_existing", response.getOrderId());
    }

    @Test
    void confirmPayment_Success() throws Exception {
        // Arrange
        String orderId = "order_123";
        Payment payment = new Payment();
        payment.setId(100L);
        payment.setInvestmentId(1L);
        payment.setStartupId(10L);
        payment.setAmount(BigDecimal.valueOf(1000));
        payment.setStatus(PaymentStatus.INITIATED);

        when(paymentRepository.findByRazorpayOrderId(orderId)).thenReturn(Optional.of(payment));

        // Mock static Razorpay Utils
        try (var mockedUtils = mockStatic(com.razorpay.Utils.class)) {
            mockedUtils.when(() -> com.razorpay.Utils.verifyPaymentSignature(any(JSONObject.class), anyString()))
                    .thenReturn(true);

            // Act
            ConfirmPaymentResponse response = razorpayService.confirmPayment(orderId, "pay_1", "sign_1");

            // Assert
            assertEquals("SUCCESS", response.getStatus());
            assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
            assertTrue(payment.isWalletCredited());
            verify(walletServiceClient).depositFunds(any(WalletDepositRequestDto.class));
            verify(paymentResultEventPublisher).publishPaymentCompleted(any());
        }
    }

    @Test
    void confirmPayment_InvalidSignature() throws Exception {
        // Arrange
        String orderId = "order_123";
        Payment payment = new Payment();
        payment.setStatus(PaymentStatus.INITIATED);

        when(paymentRepository.findByRazorpayOrderId(orderId)).thenReturn(Optional.of(payment));

        try (var mockedUtils = mockStatic(com.razorpay.Utils.class)) {
            mockedUtils.when(() -> com.razorpay.Utils.verifyPaymentSignature(any(JSONObject.class), anyString()))
                    .thenReturn(false);

            // Act & Assert
            assertThrows(PaymentGatewayException.class, () -> razorpayService.confirmPayment(orderId, "pay_1", "sign_1"));
            assertEquals(PaymentStatus.FAILED, payment.getStatus());
            verify(paymentResultEventPublisher).publishPaymentFailed(any());
        }
    }

    @Test
    @DisplayName("createOrder - Payment Not Found")
    void createOrder_NotFound() {
        when(paymentRepository.findByInvestmentId(1L)).thenReturn(Optional.empty());
        assertThrows(PaymentNotFoundException.class, () -> razorpayService.createOrder(1L));
    }

    @Test
    @DisplayName("confirmPayment - Payment Not Found")
    void confirmPayment_NotFound() {
        when(paymentRepository.findByRazorpayOrderId("invalid")).thenReturn(Optional.empty());
        assertThrows(PaymentNotFoundException.class, () -> razorpayService.confirmPayment("invalid", "pay_1", "sign_1"));
    }

    @Test
    @DisplayName("confirmPayment - Already Successful")
    void confirmPayment_AlreadySuccess() {
        Payment payment = new Payment();
        payment.setStatus(PaymentStatus.SUCCESS);
        payment.setInvestmentId(101L);
        when(paymentRepository.findByRazorpayOrderId("order_1")).thenReturn(Optional.of(payment));

        ConfirmPaymentResponse resp = razorpayService.confirmPayment("order_1", "pay_1", "sign_1");
        assertEquals("SUCCESS", resp.getStatus());
        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("confirmPayment - Already Failed")
    void confirmPayment_AlreadyFailed() {
        Payment payment = new Payment();
        payment.setStatus(PaymentStatus.FAILED);
        when(paymentRepository.findByRazorpayOrderId("order_1")).thenReturn(Optional.of(payment));

        assertThrows(PaymentGatewayException.class, () -> razorpayService.confirmPayment("order_1", "pay_1", "sign_1"));
    }

    @Test
    @DisplayName("createOrder - RazorpayException")
    @SuppressWarnings("unchecked")
    void createOrder_RazorpayException() throws Exception {
        Payment payment = new Payment();
        payment.setStatus(PaymentStatus.PENDING);
        payment.setAmount(BigDecimal.TEN);
        when(paymentRepository.findByInvestmentId(1L)).thenReturn(Optional.of(payment));
        
        // Mock specific field access or avoid it
        com.razorpay.OrderClient mockOrderClient = mock(com.razorpay.OrderClient.class);
        ReflectionTestUtils.setField(razorpayClient, "orders", mockOrderClient);
        when(mockOrderClient.create(any(JSONObject.class))).thenThrow(new RazorpayException("RZP Error"));

        assertThrows(PaymentGatewayException.class, () -> razorpayService.createOrder(1L));
    }

    @Test
    @DisplayName("confirmPayment - RazorpayException (Signature Verify)")
    void confirmPayment_RazorpayException() throws Exception {
        Payment payment = new Payment();
        payment.setStatus(PaymentStatus.INITIATED);
        when(paymentRepository.findByRazorpayOrderId("order_1")).thenReturn(Optional.of(payment));

        try (var mockedUtils = mockStatic(com.razorpay.Utils.class)) {
            mockedUtils.when(() -> com.razorpay.Utils.verifyPaymentSignature(any(JSONObject.class), anyString()))
                    .thenThrow(new RazorpayException("Sig Error"));

            assertThrows(PaymentGatewayException.class, () -> razorpayService.confirmPayment("order_1", "pay_1", "sign_1"));
            assertEquals(PaymentStatus.FAILED, payment.getStatus());
        }
    }

    @Test
    @DisplayName("confirmPayment - Wallet Service Failure (Graceful)")
    void confirmPayment_WalletFailure() throws Exception {
        Payment payment = new Payment();
        payment.setStatus(PaymentStatus.INITIATED);
        payment.setInvestmentId(101L);
        payment.setStartupId(201L);
        payment.setAmount(BigDecimal.TEN);
        when(paymentRepository.findByRazorpayOrderId("order_1")).thenReturn(Optional.of(payment));

        try (var mockedUtils = mockStatic(com.razorpay.Utils.class)) {
            mockedUtils.when(() -> com.razorpay.Utils.verifyPaymentSignature(any(JSONObject.class), anyString()))
                    .thenReturn(true);
            
            doThrow(new RuntimeException("Wallet Down")).when(walletServiceClient).createWallet(anyLong());

            ConfirmPaymentResponse resp = razorpayService.confirmPayment("order_1", "pay_1", "sign_1");
            assertEquals("SUCCESS", resp.getStatus());
            // Should still be success because payment was confirmed, only wallet credit failed
            assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
        }
    }

    @Test
    @DisplayName("createOrder - Status Initiated but missing OrderId")
    void createOrder_InitiatedMissingOrderId() throws Exception {
        Long investmentId = 1L;
        Payment payment = new Payment();
        payment.setAmount(BigDecimal.valueOf(500));
        payment.setStatus(PaymentStatus.INITIATED);
        payment.setRazorpayOrderId(null); // hits line 60 branch (T, F)

        when(paymentRepository.findByInvestmentId(investmentId)).thenReturn(Optional.of(payment));
        
        // Mocking deeply nested Razorpay client
        com.razorpay.OrderClient mockOrderClient = mock(com.razorpay.OrderClient.class);
        ReflectionTestUtils.setField(razorpayClient, "orders", mockOrderClient);
        
        Order mockOrder = mock(Order.class);
        when(mockOrder.get("id")).thenReturn("order_renewed");
        when(mockOrderClient.create(any(JSONObject.class))).thenReturn(mockOrder);

        // Act
        CreateOrderResponse response = razorpayService.createOrder(investmentId);

        // Assert
        assertEquals("order_renewed", response.getOrderId());
    }
}
