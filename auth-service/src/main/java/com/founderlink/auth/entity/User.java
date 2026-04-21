package com.founderlink.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    /**
     * True once the user has verified their email address via OTP.
     * Defaults to false for LOCAL registrations; set to true immediately for GOOGLE OAuth users.
     * A one-time migration sets this to true for all users that existed before this feature.
     */
    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    /**
     * Identifies how this account was created.
     * LOCAL  = email + password registration
     * GOOGLE = Google OAuth (password field holds a random UUID, never used for auth)
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false, length = 20)
    private AuthProvider authProvider = AuthProvider.LOCAL;

    /**
     * The external provider's unique user identifier.
     * For GOOGLE users this is the Google `sub` claim.
     * Null for LOCAL users.
     */
    @Column(name = "provider_id")
    private String providerId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
