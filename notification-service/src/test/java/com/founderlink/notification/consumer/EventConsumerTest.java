package com.founderlink.notification.consumer;

import com.founderlink.notification.dto.*;
import com.founderlink.notification.service.EmailService;
import com.founderlink.notification.service.NotificationService;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventConsumerTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private EventConsumer eventConsumer;

    private NotificationResponseDTO mockResponse;

    @BeforeEach
    void setUp() {
        mockResponse = NotificationResponseDTO.builder()
                .id(1L).userId(100L).type("TEST")
                .message("Test").read(false)
                .createdAt(LocalDateTime.now()).build();
    }

    // --- handleStartupCreated tests ---

    @Test
    @DisplayName("handleStartupCreated - processes event correctly")
    void handleStartupCreated_ProcessesEvent() {
        Map<String, Object> event = new HashMap<>();
        event.put("startupId", 1);
        event.put("startupName", "TechStartup");
        event.put("industry", "Tech");
        event.put("fundingGoal", 500000.0);

        eventConsumer.handleStartupCreated(event);

        verify(notificationService).sendStartupCreatedEmailToAllInvestors(
                eq(1L), eq("TechStartup"), eq("Tech"), eq(500000.0));
    }

    @Test
    @DisplayName("handleStartupCreated - uses default name when startupName is null")
    void handleStartupCreated_UsesDefaultNameWhenStartupNameNull() {
        Map<String, Object> event = new HashMap<>();
        event.put("startupId", 2);
        event.put("startupName", null);
        event.put("industry", "Health");
        event.put("fundingGoal", 100000.0);

        eventConsumer.handleStartupCreated(event);

        verify(notificationService).sendStartupCreatedEmailToAllInvestors(
                eq(2L), eq("New Startup"), eq("Health"), eq(100000.0));
    }

    @Test
    @DisplayName("handleStartupCreatedFallback - logs without throwing")
    void handleStartupCreatedFallback_LogsWithoutThrowing() {
        Map<String, Object> event = new HashMap<>();
        event.put("founderId", 100);

        // Should not throw
        eventConsumer.handleStartupCreatedFallback(event, new RuntimeException("DB down"));

        verify(notificationService, never()).createNotification(any(), any(), any());
    }

    // --- handleInvestmentCreated tests ---

    @Test
    @DisplayName("handleInvestmentCreated - processes event correctly")
    void handleInvestmentCreated_ProcessesEvent() {
        Map<String, Object> event = new HashMap<>();
        event.put("investorId", 200);
        event.put("startupId", 1);
        event.put("amount", 50000.0);
        event.put("founderId", 100);

        eventConsumer.handleInvestmentCreated(event);

        verify(notificationService).sendInvestmentCreatedEmailToFounder(
                eq(1L), eq(100L), eq(200L), eq(50000.0));
    }

    @Test
    @DisplayName("handleInvestmentCreatedFallback - logs without throwing")
    void handleInvestmentCreatedFallback_LogsWithoutThrowing() {
        Map<String, Object> event = new HashMap<>();
        event.put("investorId", 200);

        eventConsumer.handleInvestmentCreatedFallback(event, new RuntimeException("timeout"));

        verify(notificationService, never()).createNotification(any(), any(), any());
    }

    // --- handleTeamInvite tests ---

    @Test
    @DisplayName("handleTeamInvite - processes event correctly")
    void handleTeamInvite_ProcessesEvent() {
        Map<String, Object> event = new HashMap<>();
        event.put("invitedUserId", 300);
        event.put("startupId", 1);
        event.put("role", "CTO");

        when(notificationService.createNotification(eq(300L), eq("TEAM_INVITE_SENT"), anyString()))
                .thenReturn(mockResponse);

        eventConsumer.handleTeamInvite(event);

        verify(notificationService).createNotification(
                eq(300L), eq("TEAM_INVITE_SENT"), contains("CTO"));
        verify(notificationService).sendTeamInviteEmail(1L, 300L, "CTO");
    }

    @Test
    @DisplayName("handleTeamInviteFallback - logs without throwing")
    void handleTeamInviteFallback_LogsWithoutThrowing() {
        Map<String, Object> event = new HashMap<>();
        event.put("invitedUserId", 300);

        eventConsumer.handleTeamInviteFallback(event, new RuntimeException("connection lost"));

        verify(notificationService, never()).createNotification(any(), any(), any());
    }

    // --- handleMessageSent tests ---

    @Test
    @DisplayName("handleMessageSent - processes event correctly")
    void handleMessageSent_ProcessesEvent() {
        Map<String, Object> event = new HashMap<>();
        event.put("receiverId", 400);
        event.put("senderId", 100);
        event.put("senderName", "Alice");

        eventConsumer.handleMessageSent(event);

        verify(notificationService).createNotification(eq(400L), eq("MESSAGE_RECEIVED"), contains("Alice"));
    }

    @Test
    @DisplayName("handleMessageSentFallback - logs without throwing")
    void handleMessageSentFallback_LogsWithoutThrowing() {
        eventConsumer.handleMessageSentFallback(new HashMap<>(), new RuntimeException("fail"));
        verifyNoInteractions(notificationService);
    }

    // --- handlePasswordResetEvent tests ---

    @Test
    @DisplayName("handlePasswordResetEvent - processes event correctly")
    void handlePasswordResetEvent_ProcessesEvent() {
        PasswordResetEmailEvent event = new PasswordResetEmailEvent("test@test.com", "123456", "User");
        eventConsumer.handlePasswordResetEvent(event);
        verify(emailService).sendPasswordResetPinEmail("test@test.com", "User", "123456");
    }

    @Test
    @DisplayName("handlePasswordResetFallback - logs without throwing")
    void handlePasswordResetFallback_LogsWithoutThrowing() {
        PasswordResetEmailEvent event = new PasswordResetEmailEvent("test@test.com", "123456", "User");
        eventConsumer.handlePasswordResetFallback(event, new RuntimeException("fail"));
        verifyNoInteractions(emailService);
    }

    // --- handleTeamMemberAccepted tests ---

    @Test
    @DisplayName("handleTeamMemberAccepted - processes event correctly")
    void handleTeamMemberAccepted_ProcessesEvent() {
        TeamMemberAcceptedEvent event = new TeamMemberAcceptedEvent(10L, 1L, 100L, 300L, "CTO");
        eventConsumer.handleTeamMemberAccepted(event);
        verify(notificationService).sendTeamMemberAcceptedNotification(1L, 100L, 300L, "CTO");
    }

    @Test
    @DisplayName("handleTeamMemberAcceptedFallback - logs without throwing")
    void handleTeamMemberAcceptedFallback_LogsWithoutThrowing() {
        eventConsumer.handleTeamMemberAcceptedFallback(new TeamMemberAcceptedEvent(), new RuntimeException("fail"));
        verifyNoInteractions(notificationService);
    }

    // --- handleTeamMemberRejected tests ---

    @Test
    @DisplayName("handleTeamMemberRejected - processes event correctly")
    void handleTeamMemberRejected_ProcessesEvent() {
        TeamMemberRejectedEvent event = new TeamMemberRejectedEvent(11L, 1L, 100L, 300L, "CTO");
        eventConsumer.handleTeamMemberRejected(event);
        verify(notificationService).sendTeamMemberRejectedNotification(1L, 100L, 300L, "CTO");
    }

    @Test
    @DisplayName("handleTeamMemberRejectedFallback - logs without throwing")
    void handleTeamMemberRejectedFallback_LogsWithoutThrowing() {
        eventConsumer.handleTeamMemberRejectedFallback(new TeamMemberRejectedEvent(), new RuntimeException("fail"));
        verifyNoInteractions(notificationService);
    }

    // --- handlePaymentCompleted tests ---

    @Test
    @DisplayName("handlePaymentCompleted - processes event correctly")
    void handlePaymentCompleted_ProcessesEvent() {
        PaymentCompletedEvent event = new PaymentCompletedEvent(10L, 101L, 200L, 100L, 1L, BigDecimal.valueOf(1000));
        eventConsumer.handlePaymentCompleted(event);
        verify(notificationService).sendPaymentCompletedNotification(10L, 200L, 100L);
    }

    @Test
    @DisplayName("handlePaymentCompletedFallback - logs without throwing")
    void handlePaymentCompletedFallback_LogsWithoutThrowing() {
        eventConsumer.handlePaymentCompletedFallback(new PaymentCompletedEvent(), new RuntimeException("fail"));
        verifyNoInteractions(notificationService);
    }

    // --- handlePaymentFailed tests ---

    @Test
    @DisplayName("handlePaymentFailed - processes event correctly")
    void handlePaymentFailed_ProcessesEvent() {
        PaymentFailedEvent event = new PaymentFailedEvent(10L, 101L, 200L, 100L, 1L, BigDecimal.valueOf(1000), "Insufficient funds");
        eventConsumer.handlePaymentFailed(event);
        verify(notificationService).sendPaymentFailedNotification(10L, 200L, "Insufficient funds");
    }

    @Test
    @DisplayName("handlePaymentFailedFallback - logs without throwing")
    void handlePaymentFailedFallback_LogsWithoutThrowing() {
        eventConsumer.handlePaymentFailedFallback(new PaymentFailedEvent(), new RuntimeException("fail"));
        verifyNoInteractions(notificationService);
    }

    // --- handleInvestmentApproved tests ---

    @Test
    @DisplayName("handleInvestmentApproved - processes event correctly")
    void handleInvestmentApproved_ProcessesEvent() {
        InvestmentApprovedEvent event = new InvestmentApprovedEvent(10L, 200L, 100L, 1L, BigDecimal.valueOf(50000));
        eventConsumer.handleInvestmentApproved(event);
        verify(notificationService).sendInvestmentApprovedNotification(10L, 200L, 1L, "$50000");
    }

    @Test
    @DisplayName("handleInvestmentApprovedFallback - logs without throwing")
    void handleInvestmentApprovedFallback_LogsWithoutThrowing() {
        eventConsumer.handleInvestmentApprovedFallback(new InvestmentApprovedEvent(), new RuntimeException("fail"));
        verifyNoInteractions(notificationService);
    }

    // --- handleInvestmentRejected tests ---

    @Test
    @DisplayName("handleInvestmentRejected - processes event correctly")
    void handleInvestmentRejected_ProcessesEvent() {
        InvestmentRejectedEvent event = new InvestmentRejectedEvent(10L, 200L, 100L, 1L, BigDecimal.valueOf(50000), "Mismatch");
        eventConsumer.handleInvestmentRejected(event);
        verify(notificationService).sendInvestmentRejectedNotification(10L, 200L, 1L, "$50000", "Mismatch");
    }

    @Test
    @DisplayName("handleInvestmentRejectedFallback - logs without throwing")
    void handleInvestmentRejectedFallback_LogsWithoutThrowing() {
        eventConsumer.handleInvestmentRejectedFallback(new InvestmentRejectedEvent(), new RuntimeException("fail"));
        verifyNoInteractions(notificationService);
    }

    // --- handleUserRegistered tests ---

    @Test
    @DisplayName("handleUserRegistered - processes event correctly")
    void handleUserRegistered_ProcessesEvent() {
        UserRegisteredEvent event = new UserRegisteredEvent(500L, "test@test.com", "Tester", "INVESTOR");
        eventConsumer.handleUserRegistered(event);
        verify(emailService).sendWelcomeEmail("test@test.com", "Tester", "INVESTOR");
    }

    @Test
    @DisplayName("handleUserRegisteredFallback - logs without throwing")
    void handleUserRegisteredFallback_LogsWithoutThrowing() {
        UserRegisteredEvent event = new UserRegisteredEvent(500L, "test@test.com", "Tester", "INVESTOR");
        eventConsumer.handleUserRegisteredFallback(event, new RuntimeException("fail"));
        verifyNoInteractions(emailService);
    }

    @Test
    @DisplayName("handleStartupCreated - handles exception during processing")
    void handleStartupCreated_HandlesException() {
        Map<String, Object> event = new HashMap<>();
        event.put("startupId", null); // Causes NPE

        eventConsumer.handleStartupCreated(event);

        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("handleMessageSent - uses 'Someone' fallback when senderName is null")
    void handleMessageSent_SenderNameNull() {
        Map<String, Object> event = new HashMap<>();
        event.put("receiverId", 400);
        event.put("senderId", 100);
        event.put("senderName", null);

        eventConsumer.handleMessageSent(event);

        verify(notificationService).createNotification(eq(400L), eq("MESSAGE_RECEIVED"), contains("Someone"));
    }

    @Test
    @DisplayName("handleInvestmentApproved - amount is null → uses N/A")
    void handleInvestmentApproved_AmountNull() {
        InvestmentApprovedEvent event = new InvestmentApprovedEvent(10L, 200L, 100L, 1L, null);
        eventConsumer.handleInvestmentApproved(event);
        verify(notificationService).sendInvestmentApprovedNotification(10L, 200L, 1L, "N/A");
    }

    @Test
    @DisplayName("handleInvestmentRejected - amount is null → uses N/A")
    void handleInvestmentRejected_AmountNull() {
        InvestmentRejectedEvent event = new InvestmentRejectedEvent(10L, 200L, 100L, 1L, null, "Reason");
        eventConsumer.handleInvestmentRejected(event);
        verify(notificationService).sendInvestmentRejectedNotification(10L, 200L, 1L, "N/A", "Reason");
    }

    @Test
    @DisplayName("handleInvestmentCreated - handles exception")
    void handleInvestmentCreated_HandlesException() {
        Map<String, Object> event = new HashMap<>();
        event.put("investorId", null); // Causes NPE

        eventConsumer.handleInvestmentCreated(event);

        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("handleTeamInvite - handles exception")
    void handleTeamInvite_HandlesException() {
        Map<String, Object> event = new HashMap<>();
        event.put("invitedUserId", null);

        eventConsumer.handleTeamInvite(event);

        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("handleMessageSent - handles exception")
    void handleMessageSent_HandlesException() {
        Map<String, Object> event = new HashMap<>();
        event.put("receiverId", null);

        eventConsumer.handleMessageSent(event);

        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("handlePasswordResetEvent - handles exception")
    void handlePasswordResetEvent_HandlesException() {
        PasswordResetEmailEvent event = new PasswordResetEmailEvent("test@test.com", "123456", "User");
        doThrow(new RuntimeException("Mail error")).when(emailService)
                .sendPasswordResetPinEmail(anyString(), anyString(), anyString());

        eventConsumer.handlePasswordResetEvent(event);

        verify(emailService).sendPasswordResetPinEmail("test@test.com", "User", "123456");
    }

    @Test
    @DisplayName("handleTeamMemberAccepted - handles exception")
    void handleTeamMemberAccepted_HandlesException() {
        TeamMemberAcceptedEvent event = new TeamMemberAcceptedEvent(10L, 1L, 100L, 300L, "CTO");
        doThrow(new RuntimeException("Error")).when(notificationService)
                .sendTeamMemberAcceptedNotification(anyLong(), anyLong(), anyLong(), anyString());

        eventConsumer.handleTeamMemberAccepted(event);

        verify(notificationService).sendTeamMemberAcceptedNotification(1L, 100L, 300L, "CTO");
    }

    @Test
    @DisplayName("handleTeamMemberRejected - handles exception")
    void handleTeamMemberRejected_HandlesException() {
        TeamMemberRejectedEvent event = new TeamMemberRejectedEvent(11L, 1L, 100L, 300L, "CTO");
        doThrow(new RuntimeException("Error")).when(notificationService)
                .sendTeamMemberRejectedNotification(anyLong(), anyLong(), anyLong(), anyString());

        eventConsumer.handleTeamMemberRejected(event);

        verify(notificationService).sendTeamMemberRejectedNotification(1L, 100L, 300L, "CTO");
    }

    @Test
    @DisplayName("handlePaymentCompleted - handles exception")
    void handlePaymentCompleted_HandlesException() {
        PaymentCompletedEvent event = new PaymentCompletedEvent(10L, 101L, 200L, 100L, 1L, BigDecimal.valueOf(1000));
        doThrow(new RuntimeException("Error")).when(notificationService)
                .sendPaymentCompletedNotification(anyLong(), anyLong(), anyLong());

        eventConsumer.handlePaymentCompleted(event);

        verify(notificationService).sendPaymentCompletedNotification(10L, 200L, 100L);
    }

    @Test
    @DisplayName("handlePaymentFailed - handles exception")
    void handlePaymentFailed_HandlesException() {
        PaymentFailedEvent event = new PaymentFailedEvent(10L, 101L, 200L, 100L, 1L, BigDecimal.valueOf(1000), "Failed");
        doThrow(new RuntimeException("Error")).when(notificationService)
                .sendPaymentFailedNotification(anyLong(), anyLong(), anyString());

        eventConsumer.handlePaymentFailed(event);

        verify(notificationService).sendPaymentFailedNotification(10L, 200L, "Failed");
    }

    @Test
    @DisplayName("handleInvestmentApproved - handles exception")
    void handleInvestmentApproved_HandlesException() {
        InvestmentApprovedEvent event = new InvestmentApprovedEvent(10L, 200L, 100L, 1L, BigDecimal.valueOf(50000));
        doThrow(new RuntimeException("Error")).when(notificationService)
                .sendInvestmentApprovedNotification(anyLong(), anyLong(), anyLong(), anyString());

        eventConsumer.handleInvestmentApproved(event);

        verify(notificationService).sendInvestmentApprovedNotification(10L, 200L, 1L, "$50000");
    }

    @Test
    @DisplayName("handleInvestmentRejected - handles exception")
    void handleInvestmentRejected_HandlesException() {
        InvestmentRejectedEvent event = new InvestmentRejectedEvent(10L, 200L, 100L, 1L, BigDecimal.valueOf(50000), "Mismatch");
        doThrow(new RuntimeException("Error")).when(notificationService)
                .sendInvestmentRejectedNotification(anyLong(), anyLong(), anyLong(), anyString(), anyString());

        eventConsumer.handleInvestmentRejected(event);

        verify(notificationService).sendInvestmentRejectedNotification(10L, 200L, 1L, "$50000", "Mismatch");
    }

    @Test
    @DisplayName("handleUserRegistered - handles exception")
    void handleUserRegistered_HandlesException() {
        UserRegisteredEvent event = new UserRegisteredEvent(500L, "test@test.com", "Tester", "INVESTOR");
        doThrow(new RuntimeException("Error")).when(emailService)
                .sendWelcomeEmail(anyString(), anyString(), anyString());

        eventConsumer.handleUserRegistered(event);

        verify(emailService).sendWelcomeEmail("test@test.com", "Tester", "INVESTOR");
    }
}
