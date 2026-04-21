package com.founderlink.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Published to RabbitMQ after a new user registers (LOCAL flow).
 * The notification-service consumes this and sends the OTP email.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailVerificationEvent {
    private String email;
    private String name;
    private String otp;
}
