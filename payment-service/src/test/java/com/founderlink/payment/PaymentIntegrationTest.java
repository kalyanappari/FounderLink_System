package com.founderlink.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.founderlink.payment.client.WalletServiceClient;
import com.founderlink.payment.dto.request.ConfirmPaymentRequest;
import com.founderlink.payment.dto.request.CreateOrderRequest;
import com.founderlink.payment.entity.Payment;
import com.founderlink.payment.entity.PaymentStatus;
import com.founderlink.payment.repository.PaymentRepository;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PaymentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PaymentRepository paymentRepository;

    @MockBean
    private RazorpayClient razorpayClient;

    @MockBean
    private WalletServiceClient walletServiceClient;

    @MockBean
    private org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate;

    @Test
    void createOrderE2E() throws Exception {
        Long userId = 100L;
        Long investmentId = 200L;
        CreateOrderRequest request = new CreateOrderRequest(investmentId);

        Payment payment = new Payment();
        payment.setId(1L);
        payment.setInvestorId(userId);
        payment.setInvestmentId(investmentId);
        payment.setAmount(BigDecimal.valueOf(500));
        payment.setStatus(PaymentStatus.INITIATED);
        payment.setRazorpayOrderId("order_existing");

        when(paymentRepository.findByInvestmentId(investmentId)).thenReturn(Optional.of(payment));
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        mockMvc.perform(post("/payments/create-order")
                .header("X-User-Id", userId)
                .header("X-User-Role", "ROLE_INVESTOR")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Razorpay order created successfully"))
                .andExpect(jsonPath("$.data.orderId").value("order_existing"));
    }

    @Test
    void getPaymentE2E() throws Exception {
        Long userId = 100L;
        Long paymentId = 1L;

        Payment payment = new Payment();
        payment.setId(paymentId);
        payment.setInvestorId(userId);
        payment.setAmount(BigDecimal.valueOf(1000));
        payment.setStatus(PaymentStatus.SUCCESS);

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        mockMvc.perform(get("/payments/{paymentId}", paymentId)
                .header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Payment retrieved successfully"))
                .andExpect(jsonPath("$.data.id").value(paymentId))
                .andExpect(jsonPath("$.data.status").value("SUCCESS"));
    }

    @Test
    @DisplayName("createOrder - Forbidden - Wrong Role")
    void createOrder_WrongRole() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(200L);

        mockMvc.perform(post("/payments/create-order")
                .header("X-User-Id", 100L)
                .header("X-User-Role", "ROLE_USER") // Needs ROLE_INVESTOR
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("getPayment - Forbidden - Not Owner")
    void getPayment_NotOwner() throws Exception {
        Long paymentId = 1L;
        Payment payment = new Payment();
        payment.setId(paymentId);
        payment.setInvestorId(999L); // different from header

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));

        mockMvc.perform(get("/payments/{paymentId}", paymentId)
                .header("X-User-Id", 100L))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("confirmPayment - Not Found")
    void confirmPayment_NotFound() throws Exception {
        ConfirmPaymentRequest request = new ConfirmPaymentRequest("invalid_order", "pay_1", "sign_1");

        when(paymentRepository.findByRazorpayOrderId(anyString())).thenReturn(Optional.empty());

        mockMvc.perform(post("/payments/confirm")
                .header("X-User-Id", 100L)
                .header("X-User-Role", "ROLE_INVESTOR")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("getPayment - Not Found")
    void getPayment_NotFound() throws Exception {
        when(paymentRepository.findById(anyLong())).thenReturn(Optional.empty());

        mockMvc.perform(get("/payments/1")
                .header("X-User-Id", 100L))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("createOrder - Not Found")
    void createOrder_NotFound() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(200L);
        when(paymentRepository.findByInvestmentId(anyLong())).thenReturn(Optional.empty());

        mockMvc.perform(post("/payments/create-order")
                .header("X-User-Id", 100L)
                .header("X-User-Role", "ROLE_INVESTOR")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("getPaymentByInvestment - Success")
    void getPaymentByInvestment_Success() throws Exception {
        Long userId = 100L;
        Long investmentId = 200L;
        Payment payment = new Payment();
        payment.setInvestorId(userId);
        payment.setInvestmentId(investmentId);
        payment.setAmount(BigDecimal.valueOf(500));
        payment.setStatus(PaymentStatus.SUCCESS);

        when(paymentRepository.findByInvestmentId(investmentId)).thenReturn(Optional.of(payment));

        mockMvc.perform(get("/payments/investment/{investmentId}", investmentId)
                .header("X-User-Id", userId))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("getPaymentByInvestment - Not Found")
    void getPaymentByInvestment_NotFound() throws Exception {
        when(paymentRepository.findByInvestmentId(anyLong())).thenReturn(Optional.empty());

        mockMvc.perform(get("/payments/investment/200")
                .header("X-User-Id", 100L))
                .andExpect(status().isNotFound());
    }
}
