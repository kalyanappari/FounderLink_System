package com.founderlink.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Consumed from the email-verification-queue.
 * Published by auth-service when a new LOCAL user registers.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmailVerificationEvent {
    private String email;
    private String name;
    private String otp;
}
