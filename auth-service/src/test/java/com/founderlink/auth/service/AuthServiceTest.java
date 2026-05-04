package com.founderlink.auth.service;

import com.founderlink.auth.dto.AuthResponse;
import com.founderlink.auth.dto.LoginRequest;
import com.founderlink.auth.dto.RegisterRequest;
import com.founderlink.auth.dto.RegisterResponse;
import com.founderlink.auth.entity.AuthProvider;
import com.founderlink.auth.entity.EmailVerificationOtp;
import com.founderlink.auth.entity.Role;
import com.founderlink.auth.entity.RefreshToken;
import com.founderlink.auth.entity.User;
import com.founderlink.auth.exception.EmailAlreadyExistsException;
import com.founderlink.auth.exception.EmailNotVerifiedException;
import com.founderlink.auth.exception.ExpiredPasswordResetPinException;
import com.founderlink.auth.exception.InvalidPasswordResetPinException;
import com.founderlink.auth.exception.InvalidRefreshTokenException;
import com.founderlink.auth.exception.UserServiceUnavailableException;
import com.founderlink.auth.publisher.EmailVerificationEventPublisher;
import com.founderlink.auth.publisher.UserRegisteredEventPublisher;
import com.founderlink.auth.repository.EmailVerificationOtpRepository;
import com.founderlink.auth.repository.UserRepository;
import com.founderlink.auth.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    // ── Mocks ─────────────────────────────────────────────────────────────────
    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private SyncService syncService;
    @Mock private com.founderlink.auth.repository.PasswordResetPinRepository passwordResetPinRepository;
    @Mock private com.founderlink.auth.publisher.PasswordResetEventPublisher passwordResetEventPublisher;
    @Mock private UserRegisteredEventPublisher userRegisteredEventPublisher;
    @Mock private EmailVerificationOtpRepository emailVerificationOtpRepository;
    @Mock private EmailVerificationEventPublisher emailVerificationEventPublisher;
    @Mock private Authentication authentication;

    @InjectMocks private AuthService authService;

    // ── Captors ───────────────────────────────────────────────────────────────
    @Captor private ArgumentCaptor<User> userCaptor;
    @Captor private ArgumentCaptor<com.founderlink.auth.entity.PasswordResetPin> passwordResetPinCaptor;
    @Captor private ArgumentCaptor<com.founderlink.auth.dto.PasswordResetEmailEvent> passwordResetEventCaptor;
    @Captor private ArgumentCaptor<com.founderlink.auth.dto.UserRegisteredEvent> userRegisteredEventCaptor;
    @Captor private ArgumentCaptor<EmailVerificationOtp> otpCaptor;
    @Captor private ArgumentCaptor<com.founderlink.auth.dto.EmailVerificationEvent> emailVerificationEventCaptor;

    // ─────────────────────────────────────────────────────────────────────────
    // register()
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void registerShouldPersistUserAndSyncSuccessfully() {
        RegisterRequest request = new RegisterRequest("Alice Founder", "alice@founderlink.com", "PlainPass1", Role.FOUNDER);

        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(request.getPassword())).thenReturn("encoded-password");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(101L);
            return u;
        });
        when(emailVerificationOtpRepository.save(any(EmailVerificationOtp.class)))
                .thenAnswer(i -> i.getArgument(0));
        doNothing().when(syncService).syncUser(any(User.class));

        RegisterResponse response = authService.register(request);

        assertThat(response.getEmail()).isEqualTo(request.getEmail());
        assertThat(response.getRole()).isEqualTo(Role.FOUNDER.name());
        assertThat(response.getMessage()).isEqualTo("Registration successful! Please verify your email to activate your account.");

        // User saved with emailVerified=false, authProvider=LOCAL
        verify(userRepository).saveAndFlush(userCaptor.capture());
        User saved = userCaptor.getValue();
        assertThat(saved.getPassword()).isEqualTo("encoded-password");
        assertThat(saved.getRole()).isEqualTo(Role.FOUNDER);
        assertThat(saved.isEmailVerified()).isFalse();
        assertThat(saved.getAuthProvider()).isEqualTo(AuthProvider.LOCAL);

        // OTP generated and saved
        verify(emailVerificationOtpRepository).save(otpCaptor.capture());
        assertThat(otpCaptor.getValue().getEmail()).isEqualTo(request.getEmail());
        assertThat(otpCaptor.getValue().getOtp()).matches("\\d{6}");

        // Verification email event published
        verify(emailVerificationEventPublisher).publishEmailVerificationEvent(emailVerificationEventCaptor.capture());
        assertThat(emailVerificationEventCaptor.getValue().getEmail()).isEqualTo(request.getEmail());

        // user-service sync + welcome event
        verify(syncService).syncUser(any(User.class));
        verify(userRegisteredEventPublisher).publishUserRegisteredEvent(userRegisteredEventCaptor.capture());
        assertThat(userRegisteredEventCaptor.getValue().getUserId()).isEqualTo(101L);
    }

    @Test
    void registerShouldFailWhenUserServiceSyncFails() {
        RegisterRequest request = new RegisterRequest("Bob Investor", "bob@founderlink.com", "PlainPass1", Role.INVESTOR);
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("encoded");
        when(userRepository.saveAndFlush(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0); u.setId(202L); return u;
        });
        doThrow(new UserServiceUnavailableException("UserClient#createUser", "Service unavailable"))
                .when(syncService).syncUser(any(User.class));

        assertThrows(UserServiceUnavailableException.class, () -> authService.register(request));
        verify(syncService).syncUser(any(User.class));
    }

    @Test
    void registerShouldThrowWhenEmailAlreadyExists() {
        RegisterRequest request = new RegisterRequest("Alice", "alice@founderlink.com", "Pass1", Role.FOUNDER);
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(true);

        EmailAlreadyExistsException ex = assertThrows(EmailAlreadyExistsException.class,
                () -> authService.register(request));

        assertThat(ex.getMessage()).isEqualTo("Email already registered");
        verify(userRepository, never()).saveAndFlush(any());
        verifyNoInteractions(passwordEncoder, syncService);
    }

    @Test
    void registerShouldThrowIllegalArgumentExceptionForMissingRole() {
        RegisterRequest request = new RegisterRequest("Alice", "alice@founderlink.com", "Pass1", null);
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> authService.register(request));
        assertThat(ex.getMessage()).isEqualTo("Role is required");
    }

    @Test
    void registerShouldBlockAdminRoleSelection() {
        RegisterRequest request = new RegisterRequest("Alice", "alice@founderlink.com", "Pass1", Role.ADMIN);
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);

        AccessDeniedException ex = assertThrows(AccessDeniedException.class,
                () -> authService.register(request));
        assertThat(ex.getMessage()).isEqualTo("Requested role is not allowed");
        verify(userRepository, never()).saveAndFlush(any());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // login()
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void loginShouldAuthenticateAndReturnJwtResponseForVerifiedUser() {
        LoginRequest request = new LoginRequest("alice@founderlink.com", "PlainPass1");
        User user = new User();
        user.setId(55L);
        user.setEmail(request.getEmail());
        user.setRole(Role.COFOUNDER);
        user.setEmailVerified(true);
        user.setAuthProvider(AuthProvider.LOCAL);

        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(user));
        when(jwtService.generateToken(55L, "COFOUNDER")).thenReturn("jwt-token");
        when(refreshTokenService.createToken(55L)).thenReturn("refresh-token");

        AuthSession session = authService.login(request);

        assertThat(session.authResponse().getToken()).isEqualTo("jwt-token");
        assertThat(session.authResponse().getEmail()).isEqualTo(user.getEmail());
        assertThat(session.authResponse().getRole()).isEqualTo("COFOUNDER");
        assertThat(session.refreshToken()).isEqualTo("refresh-token");
    }

    @Test
    void loginShouldThrowEmailNotVerifiedExceptionForUnverifiedLocalUser() {
        LoginRequest request = new LoginRequest("alice@founderlink.com", "PlainPass1");
        User user = new User();
        user.setId(1L);
        user.setEmail(request.getEmail());
        user.setRole(Role.FOUNDER);
        user.setEmailVerified(false);        // ← unverified
        user.setAuthProvider(AuthProvider.LOCAL);

        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(user));

        assertThrows(EmailNotVerifiedException.class, () -> authService.login(request));

        verify(jwtService, never()).generateToken(any(), any());
        verify(refreshTokenService, never()).createToken(any());
    }

    @Test
    void loginShouldPropagateAuthenticationFailure() {
        LoginRequest request = new LoginRequest("alice@founderlink.com", "wrong");
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Bad credentials"));

        assertThrows(BadCredentialsException.class, () -> authService.login(request));
        verify(userRepository, never()).findByEmail(any());
    }

    @Test
    void loginShouldThrowWhenUserNotFound() {
        LoginRequest request = new LoginRequest("ghost@test.com", "pass");
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(userRepository.findByEmail("ghost@test.com")).thenReturn(Optional.empty());

        assertThrows(BadCredentialsException.class, () -> authService.login(request));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // verifyEmail()   — new
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void verifyEmailShouldMarkUserVerifiedAndMarkOtpUsed() {
        String email = "alice@founderlink.com";
        String otp   = "123456";

        User user = new User();
        user.setId(1L);
        user.setEmail(email);
        user.setEmailVerified(false);

        EmailVerificationOtp otpEntity = EmailVerificationOtp.builder()
                .email(email).otp(otp)
                .expiryDate(LocalDateTime.now().plusMinutes(5))
                .used(false).build();

        when(emailVerificationOtpRepository.findByEmailAndOtp(email, otp))
                .thenReturn(Optional.of(otpEntity));
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        when(emailVerificationOtpRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        authService.verifyEmail(email, otp);

        // User marked verified
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().isEmailVerified()).isTrue();

        // OTP marked used
        verify(emailVerificationOtpRepository).save(otpCaptor.capture());
        assertThat(otpCaptor.getValue().isUsed()).isTrue();
    }

    @Test
    void verifyEmailShouldThrowOnInvalidOtp() {
        when(emailVerificationOtpRepository.findByEmailAndOtp("x@x.com", "000000"))
                .thenReturn(Optional.empty());

        assertThrows(InvalidPasswordResetPinException.class,
                () -> authService.verifyEmail("x@x.com", "000000"));

        verify(userRepository, never()).save(any());
    }

    @Test
    void verifyEmailShouldThrowOnExpiredOtp() {
        String email = "alice@founderlink.com";
        String otp   = "123456";

        EmailVerificationOtp expired = EmailVerificationOtp.builder()
                .email(email).otp(otp)
                .expiryDate(LocalDateTime.now().minusMinutes(1))   // expired
                .used(false).build();

        when(emailVerificationOtpRepository.findByEmailAndOtp(email, otp))
                .thenReturn(Optional.of(expired));

        assertThrows(ExpiredPasswordResetPinException.class,
                () -> authService.verifyEmail(email, otp));

        verify(userRepository, never()).save(any());
    }

    @Test
    void verifyEmailShouldThrowWhenUserNotFound() {
        String email = "missing@founderlink.com";
        String otp   = "123456";

        EmailVerificationOtp valid = EmailVerificationOtp.builder()
                .email(email).otp(otp)
                .expiryDate(LocalDateTime.now().plusMinutes(5))
                .used(false).build();

        when(emailVerificationOtpRepository.findByEmailAndOtp(email, otp))
                .thenReturn(Optional.of(valid));
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        BadCredentialsException ex = assertThrows(BadCredentialsException.class,
                () -> authService.verifyEmail(email, otp));
        assertThat(ex.getMessage()).isEqualTo("User not found");
    }

    @Test
    void verifyEmailShouldThrowOnAlreadyUsedOtp() {
        String email = "alice@founderlink.com";
        String otp   = "123456";

        EmailVerificationOtp used = EmailVerificationOtp.builder()
                .email(email).otp(otp)
                .expiryDate(LocalDateTime.now().plusMinutes(5))
                .used(true).build();                               // already used

        when(emailVerificationOtpRepository.findByEmailAndOtp(email, otp))
                .thenReturn(Optional.of(used));

        assertThrows(com.founderlink.auth.exception.UsedPasswordResetPinException.class,
                () -> authService.verifyEmail(email, otp));

        verify(userRepository, never()).save(any());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // resendVerification()   — new
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void resendVerificationShouldDeleteOldOtpAndPublishNew() {
        String email = "alice@founderlink.com";
        User user = new User();
        user.setId(1L);
        user.setEmail(email);
        user.setName("Alice");
        user.setEmailVerified(false);

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(emailVerificationOtpRepository.save(any(EmailVerificationOtp.class)))
                .thenAnswer(i -> i.getArgument(0));

        authService.resendVerification(email);

        verify(emailVerificationOtpRepository).deleteByEmail(email);
        verify(emailVerificationOtpRepository).save(otpCaptor.capture());
        assertThat(otpCaptor.getValue().getEmail()).isEqualTo(email);
        assertThat(otpCaptor.getValue().getOtp()).matches("\\d{6}");
        verify(emailVerificationEventPublisher).publishEmailVerificationEvent(any());
    }

    @Test
    void resendVerificationShouldThrowWhenUserAlreadyVerified() {
        User user = new User();
        user.setEmail("alice@founderlink.com");
        user.setEmailVerified(true);         // already done

        when(userRepository.findByEmail("alice@founderlink.com")).thenReturn(Optional.of(user));

        assertThrows(IllegalStateException.class,
                () -> authService.resendVerification("alice@founderlink.com"));

        verify(emailVerificationOtpRepository, never()).save(any());
        verify(emailVerificationEventPublisher, never()).publishEmailVerificationEvent(any());
    }

    @Test
    void resendVerificationShouldThrowWhenUserNotFound() {
        when(userRepository.findByEmail("missing@x.com")).thenReturn(Optional.empty());

        assertThrows(BadCredentialsException.class,
                () -> authService.resendVerification("missing@x.com"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // refresh()
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void refreshShouldReturnNewAccessTokenAndRotatedRefreshToken() {
        String raw = "incoming-refresh-token";
        RefreshToken rt = RefreshToken.builder().id(11L).userId(77L).revoked(false).build();
        User user = new User();
        user.setId(77L);
        user.setEmail("r@fl.com");
        user.setRole(Role.FOUNDER);

        when(refreshTokenService.validateToken(raw)).thenReturn(rt);
        when(userRepository.findById(77L)).thenReturn(Optional.of(user));
        when(jwtService.generateToken(77L, "FOUNDER")).thenReturn("new-access-token");
        when(refreshTokenService.rotateToken(raw)).thenReturn("rotated-token");

        AuthSession session = authService.refresh(raw);

        assertThat(session.authResponse().getToken()).isEqualTo("new-access-token");
        assertThat(session.refreshToken()).isEqualTo("rotated-token");
    }

    @Test
    void refreshShouldThrowWhenUserMissing() {
        RefreshToken rt = RefreshToken.builder().id(1L).userId(99L).revoked(false).build();
        when(refreshTokenService.validateToken("t")).thenReturn(rt);
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        InvalidRefreshTokenException ex = assertThrows(InvalidRefreshTokenException.class,
                () -> authService.refresh("t"));
        assertThat(ex.getMessage()).isEqualTo("Refresh token references a missing user");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // forgotPassword() / resetPassword()
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void forgotPasswordShouldGeneratePinAndPublishEvent() {
        String email = "test@founderlink.com";
        User user = new User();
        user.setId(1L); user.setEmail(email); user.setName("Test");

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(passwordResetPinRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        com.founderlink.auth.dto.ForgotPasswordResponse response = authService.forgotPassword(email);

        assertThat(response.getMessage()).isEqualTo("Password reset PIN has been sent to your email");
        verify(passwordResetPinRepository).deleteByEmail(email);
        verify(passwordResetPinRepository).save(passwordResetPinCaptor.capture());
        assertThat(passwordResetPinCaptor.getValue().getPin()).matches("\\d{6}");
        verify(passwordResetEventPublisher).publishPasswordResetEvent(passwordResetEventCaptor.capture());
        assertThat(passwordResetEventCaptor.getValue().getEmail()).isEqualTo(email);
    }

    @Test
    void forgotPasswordShouldThrowWhenEmailNotFound() {
        when(userRepository.findByEmail("no@x.com")).thenReturn(Optional.empty());

        assertThrows(BadCredentialsException.class, () -> authService.forgotPassword("no@x.com"));
        verify(passwordResetPinRepository, never()).save(any());
    }

    @Test
    void resetPasswordShouldUpdatePasswordSuccessfully() {
        String email = "test@founderlink.com";
        String pin = "123456";

        User user = new User();
        user.setId(1L); user.setEmail(email);

        com.founderlink.auth.entity.PasswordResetPin resetPin = com.founderlink.auth.entity.PasswordResetPin.builder()
                .id(1L).email(email).pin(pin)
                .expiryDate(LocalDateTime.now().plusMinutes(3)).used(false).build();

        when(passwordResetPinRepository.findByEmailAndPin(email, pin)).thenReturn(Optional.of(resetPin));
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("NewPass123")).thenReturn("newEncoded");
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(passwordResetPinRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        com.founderlink.auth.dto.ResetPasswordResponse response = authService.resetPassword(email, pin, "NewPass123");

        assertThat(response.getMessage()).isEqualTo("Password has been reset successfully");
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getPassword()).isEqualTo("newEncoded");
    }

    @Test
    void resetPasswordShouldThrowWhenPinIsInvalid() {
        when(passwordResetPinRepository.findByEmailAndPin(any(), any())).thenReturn(Optional.empty());

        assertThrows(InvalidPasswordResetPinException.class,
                () -> authService.resetPassword("x@x.com", "000000", "pass"));
    }

    @Test
    void resetPasswordShouldThrowWhenPinIsExpired() {
        com.founderlink.auth.entity.PasswordResetPin expired = com.founderlink.auth.entity.PasswordResetPin.builder()
                .email("x@x.com").pin("123456")
                .expiryDate(LocalDateTime.now().minusMinutes(1)).used(false).build();

        when(passwordResetPinRepository.findByEmailAndPin("x@x.com", "123456")).thenReturn(Optional.of(expired));

        assertThrows(ExpiredPasswordResetPinException.class,
                () -> authService.resetPassword("x@x.com", "123456", "pass"));
    }

    @Test
    void resetPasswordShouldThrowWhenPinIsAlreadyUsed() {
        com.founderlink.auth.entity.PasswordResetPin used = com.founderlink.auth.entity.PasswordResetPin.builder()
                .email("x@x.com").pin("123456")
                .expiryDate(LocalDateTime.now().plusMinutes(5)).used(true).build();

        when(passwordResetPinRepository.findByEmailAndPin("x@x.com", "123456")).thenReturn(Optional.of(used));

        assertThrows(com.founderlink.auth.exception.UsedPasswordResetPinException.class,
                () -> authService.resetPassword("x@x.com", "123456", "pass"));
    }

    @Test
    void resetPasswordShouldThrowWhenUserNotFound() {
        String email = "x@x.com";
        String pin = "123456";
        com.founderlink.auth.entity.PasswordResetPin valid = com.founderlink.auth.entity.PasswordResetPin.builder()
                .email(email).pin(pin)
                .expiryDate(LocalDateTime.now().plusMinutes(5)).used(false).build();

        when(passwordResetPinRepository.findByEmailAndPin(email, pin)).thenReturn(Optional.of(valid));
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        BadCredentialsException ex = assertThrows(BadCredentialsException.class,
                () -> authService.resetPassword(email, pin, "pass"));
        assertThat(ex.getMessage()).isEqualTo("User not found");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // logout()
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void logoutShouldInvokeRevokeToken() {
        doNothing().when(refreshTokenService).revokeToken("t");
        authService.logout("t");
        verify(refreshTokenService).revokeToken("t");
    }

    @Test
    void logoutShouldHandleExpiredTokenGracefully() {
        doThrow(new com.founderlink.auth.exception.ExpiredRefreshTokenException("expired"))
                .when(refreshTokenService).revokeToken("expired");
        authService.logout("expired");   // must not throw
        verify(refreshTokenService).revokeToken("expired");
    }

    @Test
    void logoutShouldHandleRevokedTokenGracefully() {
        doThrow(new com.founderlink.auth.exception.RevokedRefreshTokenException("revoked"))
                .when(refreshTokenService).revokeToken("revoked");
        authService.logout("revoked");   // must not throw
        verify(refreshTokenService).revokeToken("revoked");
    }
}
