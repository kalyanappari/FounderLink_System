package com.founderlink.notification.service;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.IContext;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceNewMethodsTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private TemplateEngine templateEngine;

    @InjectMocks
    private EmailService emailService;

    private MimeMessage mimeMessage;

    @BeforeEach
    void setUp() {
        mimeMessage = mock(MimeMessage.class);
        lenient().when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        lenient().doNothing().when(mailSender).send(any(MimeMessage.class));
        lenient().when(templateEngine.process(anyString(), any(IContext.class)))
                .thenReturn("<html>Test Content</html>");
    }

    @Test
    @DisplayName("sendTeamMemberAcceptedEmail - success")
    void sendTeamMemberAcceptedEmail_Success() {
        emailService.sendTeamMemberAcceptedEmail(
                "founder@test.com", "John", "Alice", "CTO", 101L);
        verify(mailSender, times(1)).send(mimeMessage);
    }

    @Test
    @DisplayName("sendTeamMemberRejectedEmail - success")
    void sendTeamMemberRejectedEmail_Success() {
        emailService.sendTeamMemberRejectedEmail(
                "founder@test.com", "John", "Bob", "CFO", 102L);
        verify(mailSender, times(1)).send(mimeMessage);
    }

    @Test
    @DisplayName("sendPaymentCompletedEmail - success")
    void sendPaymentCompletedEmail_Success() {
        emailService.sendPaymentCompletedEmail(
                "investor@test.com", "Alice", 1L, "$50,000");
        verify(mailSender, times(1)).send(mimeMessage);
    }

    @Test
    @DisplayName("sendPaymentFailedEmail - success")
    void sendPaymentFailedEmail_Success() {
        emailService.sendPaymentFailedEmail(
                "investor@test.com", "Bob", 2L, "Insufficient funds");
        verify(mailSender, times(1)).send(mimeMessage);
    }

    @Test
    @DisplayName("sendInvestmentApprovedEmail - success")
    void sendInvestmentApprovedEmail_Success() {
        emailService.sendInvestmentApprovedEmail(
                "investor@test.com", "Charlie", 101L, "$100,000");
        verify(mailSender, times(1)).send(mimeMessage);
    }

    @Test
    @DisplayName("sendInvestmentRejectedEmail - success")
    void sendInvestmentRejectedEmail_Success() {
        emailService.sendInvestmentRejectedEmail(
                "investor@test.com", "David", 102L, "$75,000", "Not aligned with goals");
        verify(mailSender, times(1)).send(mimeMessage);
    }

    @Test
    @DisplayName("sendInvestmentRejectedEmail - handles null reason")
    void sendInvestmentRejectedEmail_NullReason() {
        emailService.sendInvestmentRejectedEmail(
                "investor@test.com", "Eve", 103L, "$50,000", null);
        verify(mailSender, times(1)).send(mimeMessage);
    }

    @Test
    @DisplayName("sendWelcomeEmail - success")
    void sendWelcomeEmail_Success() {
        emailService.sendWelcomeEmail("user@test.com", "User", "INVESTOR");
        verify(mailSender, times(1)).send(mimeMessage);
    }

    @Test
    @DisplayName("sendPasswordResetPinEmail - success")
    void sendPasswordResetPinEmail_Success() {
        emailService.sendPasswordResetPinEmail("user@test.com", "User", "123456");
        verify(mailSender, times(1)).send(mimeMessage);
    }

    @Test
    @DisplayName("sendTeamInviteEmail - success")
    void sendTeamInviteEmail_Success() {
        emailService.sendTeamInviteEmail("member@test.com", "Member", "DEVELOPER", 1L);
        verify(mailSender, times(1)).send(mimeMessage);
    }

    @Test
    @DisplayName("sendStartupAlertEmail - success")
    void sendStartupAlertEmail_Success() {
        emailService.sendStartupAlertEmail(new String[] { "inv1@test.com", "inv2@test.com" }, "TechOne", "IT", "$1M",
                101L);
        verify(mailSender, times(2)).send(mimeMessage);
    }

    @Test
    @DisplayName("sendEmail - handles plain text dispatch")
    void sendEmail_HandlesPlainText() {
        emailService.sendEmail("test@test.com", "Subject", "Body", false);
        verify(mailSender, times(1)).send(mimeMessage);
    }

    @Test
    @DisplayName("sendEmailVerificationOtpEmail - sends OTP email successfully")
    void sendEmailVerificationOtpEmail_Success() {
        emailService.sendEmailVerificationOtpEmail("alice@test.com", "Alice", "483920");
        verify(mailSender, times(1)).send(mimeMessage);
    }

    @Test
    @DisplayName("sendEmailVerificationOtpEmail - handles mail sender exception without rethrowing")
    void sendEmailVerificationOtpEmail_HandlesMailSenderException() {
        doThrow(new org.springframework.mail.MailSendException("SMTP down"))
                .when(mailSender).send(any(MimeMessage.class));

        // EmailService catches exceptions internally — should not throw
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(
                () -> emailService.sendEmailVerificationOtpEmail("alice@test.com", "Alice", "483920")
        );

        // send was still attempted
        verify(mailSender, times(1)).send(mimeMessage);
    }
}
