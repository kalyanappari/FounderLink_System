package com.founderlink.auth.service;

import com.founderlink.auth.dto.*;
import com.founderlink.auth.entity.PasswordResetPin;
import com.founderlink.auth.entity.Role;
import com.founderlink.auth.entity.User;
import com.founderlink.auth.exception.*;
import com.founderlink.auth.publisher.EmailVerificationEventPublisher;
import com.founderlink.auth.publisher.PasswordResetEventPublisher;
import com.founderlink.auth.publisher.UserRegisteredEventPublisher;
import com.founderlink.auth.repository.EmailVerificationOtpRepository;
import com.founderlink.auth.repository.PasswordResetPinRepository;
import com.founderlink.auth.repository.UserRepository;
import com.founderlink.auth.security.JwtService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Random;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordResetPinRepository passwordResetPinRepository;
    private final EmailVerificationOtpRepository emailVerificationOtpRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final AuthenticationManager authenticationManager;
    private final SyncService syncService;
    private final PasswordResetEventPublisher passwordResetEventPublisher;
    private final UserRegisteredEventPublisher userRegisteredEventPublisher;
    private final EmailVerificationEventPublisher emailVerificationEventPublisher;

    private static final Set<Role> ALLOWED_SELF_ROLES = Set.of(
            Role.FOUNDER,
            Role.INVESTOR,
            Role.COFOUNDER);

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        log.debug("Processing registration request");

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyExistsException("Email already registered");
        }

        Role requestedRole = getRole(request);

        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(requestedRole);
        user.setEmailVerified(false);
        user.setAuthProvider(com.founderlink.auth.entity.AuthProvider.LOCAL);

        User savedUser = userRepository.saveAndFlush(user);
        log.debug("User persisted in auth-service");

        syncService.syncUser(savedUser);

        // Publish welcome/sync event
        com.founderlink.auth.dto.UserRegisteredEvent event = com.founderlink.auth.dto.UserRegisteredEvent.builder()
                .userId(savedUser.getId())
                .email(savedUser.getEmail())
                .name(savedUser.getName())
                .role(savedUser.getRole().name())
                .build();
        userRegisteredEventPublisher.publishUserRegisteredEvent(event);

        // Generate OTP and publish email verification event
        sendEmailVerificationOtp(savedUser.getEmail(), savedUser.getName());

        return RegisterResponse.builder()
                .email(savedUser.getEmail())
                .role(savedUser.getRole().name())
                .message("Registration successful! Please verify your email to activate your account.")
                .build();
    }

    private static @NonNull Role getRole(RegisterRequest request) {
        Role requestedRole = request.getRole();

        if (requestedRole == null) {
            throw new IllegalArgumentException("Role is required");
        }

        if (requestedRole == Role.ADMIN) {
            throw new AccessDeniedException("Requested role is not allowed");
        }

        if (!ALLOWED_SELF_ROLES.contains(requestedRole)) {
            throw new IllegalArgumentException("Invalid role selection");
        }
        return requestedRole;
    }

    public AuthSession login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        // Block LOCAL users who haven't verified their email yet
        if (!user.isEmailVerified() &&
                user.getAuthProvider() == com.founderlink.auth.entity.AuthProvider.LOCAL) {
            throw new com.founderlink.auth.exception.EmailNotVerifiedException(
                    "Please verify your email before logging in. Check your inbox for the OTP.");
        }

        String token = jwtService.generateToken(
                user.getId(),
                user.getRole().name());
        String refreshToken = refreshTokenService.createToken(user.getId());

        return new AuthSession(buildAuthResponse(user, token), refreshToken);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Email Verification
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public void verifyEmail(String email, String otp) {
        com.founderlink.auth.entity.EmailVerificationOtp record =
                emailVerificationOtpRepository.findByEmailAndOtp(email, otp)
                        .orElseThrow(() -> new com.founderlink.auth.exception.InvalidPasswordResetPinException(
                                "Invalid OTP or email address"));

        if (record.isUsed()) {
            throw new com.founderlink.auth.exception.UsedPasswordResetPinException("OTP has already been used");
        }
        if (java.time.LocalDateTime.now().isAfter(record.getExpiryDate())) {
            throw new com.founderlink.auth.exception.ExpiredPasswordResetPinException("OTP has expired. Request a new one.");
        }

        record.setUsed(true);
        emailVerificationOtpRepository.save(record);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("User not found"));
        user.setEmailVerified(true);
        userRepository.save(user);

        log.info("Email verified successfully for: {}", email);
    }

    @Transactional
    public void resendVerification(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("No account found with that email"));

        if (user.isEmailVerified()) {
            throw new IllegalStateException("Email is already verified");
        }

        // Invalidate any existing OTPs for this email
        emailVerificationOtpRepository.deleteByEmail(email);

        sendEmailVerificationOtp(email, user.getName());
        log.info("Resent email verification OTP to: {}", email);
    }

    /** Shared helper — generates OTP, persists it, and publishes the event. */
    private void sendEmailVerificationOtp(String email, String name) {
        String otp = generateSixDigitPin();

        com.founderlink.auth.entity.EmailVerificationOtp otpRecord =
                com.founderlink.auth.entity.EmailVerificationOtp.builder()
                        .email(email)
                        .otp(otp)
                        .expiryDate(java.time.LocalDateTime.now().plusMinutes(10))
                        .used(false)
                        .build();
        emailVerificationOtpRepository.save(otpRecord);

        com.founderlink.auth.dto.EmailVerificationEvent verificationEvent =
                com.founderlink.auth.dto.EmailVerificationEvent.builder()
                        .email(email)
                        .name(name)
                        .otp(otp)
                        .build();
        emailVerificationEventPublisher.publishEmailVerificationEvent(verificationEvent);
    }

    public AuthSession refresh(String refreshToken) {
        var persistedRefreshToken = refreshTokenService.validateToken(refreshToken);

        User user = userRepository.findById(persistedRefreshToken.getUserId())
                .orElseThrow(() -> new InvalidRefreshTokenException("Refresh token references a missing user"));

        log.debug("Accepted refresh token usage");

        String accessToken = jwtService.generateToken(
                user.getId(),
                user.getRole().name());
        String rotatedRefreshToken = refreshTokenService.rotateToken(refreshToken);

        return new AuthSession(buildAuthResponse(user, accessToken), rotatedRefreshToken);
    }

    public void logout(String refreshToken) {
        try {
            refreshTokenService.revokeToken(refreshToken);
        } catch (InvalidRefreshTokenException | RevokedRefreshTokenException | ExpiredRefreshTokenException ex) {

            log.debug("Logout no-op: {}", ex.getMessage());
        }
    }

    private AuthResponse buildAuthResponse(User user, String accessToken) {
        return AuthResponse.builder()
                .token(accessToken)
                .email(user.getEmail())
                .role(user.getRole().name())
                .userId(user.getId())
                .build();
    }

    @Transactional
    public ForgotPasswordResponse forgotPassword(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("Email not found"));

        passwordResetPinRepository.deleteByEmail(email);

        String pin = generateSixDigitPin();

        PasswordResetPin resetPin = PasswordResetPin.builder()
                .email(email)
                .pin(pin)
                .expiryDate(LocalDateTime.now().plusMinutes(5))
                .used(false)
                .build();

        passwordResetPinRepository.save(resetPin);
        log.debug("Generated password reset PIN for email: {}", email);

        PasswordResetEmailEvent event = PasswordResetEmailEvent.builder()
                .email(email)
                .pin(pin)
                .userName(user.getName())
                .build();

        passwordResetEventPublisher.publishPasswordResetEvent(event);

        return ForgotPasswordResponse.builder()
                .message("Password reset PIN has been sent to your email")
                .build();
    }

    @Transactional
    public ResetPasswordResponse resetPassword(String email, String pin, String newPassword) {
        PasswordResetPin resetPin = passwordResetPinRepository.findByEmailAndPin(email, pin)
                .orElseThrow(() -> new InvalidPasswordResetPinException("Invalid PIN or email"));

        if (resetPin.isUsed()) {
            throw new UsedPasswordResetPinException("PIN has already been used");
        }

        if (LocalDateTime.now().isAfter(resetPin.getExpiryDate())) {
            throw new ExpiredPasswordResetPinException("PIN has expired");
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadCredentialsException("User not found"));

        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        resetPin.setUsed(true);
        passwordResetPinRepository.save(resetPin);

        log.info("Password reset successful for email: {}", email);

        return ResetPasswordResponse.builder()
                .message("Password has been reset successfully")
                .build();
    }

    private String generateSixDigitPin() {
        Random random = new Random();
        int pin = 100000 + random.nextInt(900000);
        return String.valueOf(pin);
    }
}
