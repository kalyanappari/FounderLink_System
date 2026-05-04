package com.founderlink.auth.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Stores a short-lived 6-digit OTP used to verify a user's email address
 * after registration. Mirrors the structure of {@link PasswordResetPin}.
 */
@Entity
@Table(
        name = "email_verification_otps",
        indexes = {
                @Index(name = "idx_email_verification_email",   columnList = "email"),
                @Index(name = "idx_email_verification_expiry",  columnList = "expiry_date")
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailVerificationOtp {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 6-digit numeric OTP */
    @Column(nullable = false, length = 6)
    private String otp;

    @Column(nullable = false)
    private String email;

    @Column(name = "expiry_date", nullable = false)
    private LocalDateTime expiryDate;

    /** True once the OTP has been successfully used. */
    @Column(nullable = false)
    private boolean used;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
