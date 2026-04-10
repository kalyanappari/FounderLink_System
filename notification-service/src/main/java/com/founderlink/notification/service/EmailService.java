package com.founderlink.notification.service;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.nio.charset.StandardCharsets;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    public EmailService(JavaMailSender mailSender, TemplateEngine templateEngine) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
    }

    public void sendEmail(String to, String subject, String htmlContent) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, 
                MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, 
                StandardCharsets.UTF_8.name());

            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            helper.setFrom("noreply@founderlink.online", "FounderLink");

            mailSender.send(message);
            log.info("Premium HTML Email sent successfully to: {}", to);
        } catch (Exception e) {
            log.error("Failed to send HTML email to {}: {}", to, e.getMessage());
        }
    }

    public void sendWelcomeEmail(String to, String userName, String role) {
        Context context = new Context();
        context.setVariable("userName", userName);
        context.setVariable("role", role);
        String html = templateEngine.process("welcome-email", context);
        sendEmail(to, "🎉 Welcome to FounderLink!", html);
    }

    public void sendPasswordResetPinEmail(String to, String userName, String pin) {
        Context context = new Context();
        context.setVariable("userName", userName);
        context.setVariable("pin", pin);
        String html = templateEngine.process("otp-email", context);
        sendEmail(to, "Security Verification - FounderLink", html);
    }

    public void sendInvestmentApprovedEmail(String investorEmail, String investorName, Long startupId, String amount) {
        Context context = new Context();
        context.setVariable("userName", investorName);
        context.setVariable("investmentId", startupId);
        context.setVariable("message", "Your investment has been approved by the founder.");
        context.setVariable("amount", amount);
        String html = templateEngine.process("investment-update", context);
        sendEmail(investorEmail, "✅ Your Investment Was Approved", html);
    }

    public void sendInvestmentRejectedEmail(String investorEmail, String investorName, Long startupId, String amount, String reason) {
        Context context = new Context();
        context.setVariable("userName", investorName);
        context.setVariable("investmentId", startupId);
        context.setVariable("message", "We regret to inform you that your investment was rejected.");
        context.setVariable("amount", amount);
        context.setVariable("reason", reason != null ? reason : "Strategic mismatch");
        String html = templateEngine.process("investment-update", context);
        sendEmail(investorEmail, "❌ Your Investment Was Rejected", html);
    }

    public void sendPaymentCompletedEmail(String email, String userName, Long investmentId, String amount) {
        Context context = new Context();
        context.setVariable("userName", userName);
        context.setVariable("investmentId", investmentId);
        context.setVariable("message", "Your payment was successfully processed.");
        context.setVariable("amount", amount);
        String html = templateEngine.process("investment-update", context);
        sendEmail(email, "✅ Payment Successful - Investment Confirmed", html);
    }

    public void sendPaymentFailedEmail(String email, String userName, Long investmentId, String reason) {
        Context context = new Context();
        context.setVariable("userName", userName);
        context.setVariable("investmentId", investmentId);
        context.setVariable("message", "Your payment for investment " + investmentId + " has failed.");
        context.setVariable("amount", "Attempted Transaction");
        context.setVariable("reason", reason);
        String html = templateEngine.process("investment-update", context);
        sendEmail(email, "❌ Payment Failed - Action Required", html);
    }

    public void sendTeamMemberAcceptedEmail(String founderEmail, String founderName, String memberName, String role, Long startupId) {
        Context context = new Context();
        context.setVariable("founderName", founderName);
        context.setVariable("memberName", memberName);
        context.setVariable("role", role);
        context.setVariable("startupId", startupId);
        context.setVariable("status", "ACCEPTED");
        String html = templateEngine.process("team-invite-update", context);
        sendEmail(founderEmail, "✅ Team Member Accepted Your Invitation", html);
    }

    public void sendTeamMemberRejectedEmail(String founderEmail, String founderName, String memberName, String role, Long startupId) {
        Context context = new Context();
        context.setVariable("founderName", founderName);
        context.setVariable("memberName", memberName);
        context.setVariable("role", role);
        context.setVariable("startupId", startupId);
        context.setVariable("status", "REJECTED");
        String html = templateEngine.process("team-invite-update", context);
        sendEmail(founderEmail, "❌ Team Member Rejected Your Invitation", html);
    }

    public void sendBulkEmail(String[] recipients, String subject, String htmlContent) {
        for (String email : recipients) {
            sendEmail(email, subject, htmlContent);
        }
    }
}
