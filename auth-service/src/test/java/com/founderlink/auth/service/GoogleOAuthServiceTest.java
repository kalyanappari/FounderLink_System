package com.founderlink.auth.service;

import com.founderlink.auth.config.GoogleOAuthProperties;
import com.founderlink.auth.dto.AuthResponse;
import com.founderlink.auth.dto.OAuthPendingResponse;
import com.founderlink.auth.dto.UserRegisteredEvent;
import com.founderlink.auth.entity.AuthProvider;
import com.founderlink.auth.entity.Role;
import com.founderlink.auth.entity.User;
import com.founderlink.auth.publisher.UserRegisteredEventPublisher;
import com.founderlink.auth.repository.UserRepository;
import com.founderlink.auth.security.JwtService;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GoogleOAuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private JwtService jwtService;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private SyncService syncService;
    @Mock private UserRegisteredEventPublisher userRegisteredEventPublisher;
    @Mock private GoogleOAuthProperties googleOAuthProperties;
    @Mock private GoogleIdToken mockedGoogleToken;

    @Captor private ArgumentCaptor<User> userCaptor;
    @Captor private ArgumentCaptor<UserRegisteredEvent> eventCaptor;

    private GoogleOAuthService googleOAuthService;
    private MockedConstruction<GoogleIdTokenVerifier.Builder> mockedBuilder;
    private MockedConstruction<GoogleIdTokenVerifier> mockedVerifier;

    private GoogleIdTokenVerifier verifierInstance;

    @BeforeEach
    void setUp() throws Exception {
        lenient().when(googleOAuthProperties.getClientId()).thenReturn("mock-client-id");

        verifierInstance = mock(GoogleIdTokenVerifier.class);
        
        mockedBuilder = mockConstruction(GoogleIdTokenVerifier.Builder.class, (mock, context) -> {
            when(mock.setAudience(any())).thenReturn(mock);
            when(mock.build()).thenReturn(verifierInstance);
        });

        googleOAuthService = new GoogleOAuthService(
                userRepository, jwtService, refreshTokenService, syncService,
                userRegisteredEventPublisher, googleOAuthProperties
        );
    }

    @AfterEach
    void tearDown() {
        if (mockedBuilder != null) {
            mockedBuilder.close();
        }
        if (mockedVerifier != null) {
            mockedVerifier.close();
        }
    }

    private GoogleIdToken.Payload createPayload(String email, String name, String sub) {
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setEmail(email);
        payload.set("name", name);
        payload.setSubject(sub);
        return payload;
    }

    @Test
    void handleGoogleLoginShouldReturnAuthSessionForExistingUser() throws Exception {
        GoogleIdToken.Payload payload = createPayload("g@x.com", "Gina", "sub-123");
        when(verifierInstance.verify("valid-token")).thenReturn(mockedGoogleToken);
        when(mockedGoogleToken.getPayload()).thenReturn(payload);

        User existing = new User();
        existing.setId(1L);
        existing.setEmail("g@x.com");
        existing.setRole(Role.FOUNDER);

        when(userRepository.findByEmail("g@x.com")).thenReturn(Optional.of(existing));
        when(jwtService.generateToken(1L, "FOUNDER")).thenReturn("jwt-mock");
        when(refreshTokenService.createToken(1L)).thenReturn("rt-mock");

        Object result = googleOAuthService.handleGoogleLogin("valid-token");

        assertThat(result).isInstanceOf(AuthSession.class);
        AuthSession session = (AuthSession) result;
        assertThat(session.authResponse().getToken()).isEqualTo("jwt-mock");
        assertThat(session.authResponse().getEmail()).isEqualTo("g@x.com");
        assertThat(session.refreshToken()).isEqualTo("rt-mock");

        // Verify providerId is set if it was null
        verify(userRepository).saveAndFlush(existing);
        assertThat(existing.getProviderId()).isEqualTo("sub-123");
    }

    @Test
    void handleGoogleLoginShouldReturnPendingResponseForNewUser() throws Exception {
        GoogleIdToken.Payload payload = createPayload("new@x.com", "Neo", "sub-456");
        when(verifierInstance.verify("new-token")).thenReturn(mockedGoogleToken);
        when(mockedGoogleToken.getPayload()).thenReturn(payload);

        when(userRepository.findByEmail("new@x.com")).thenReturn(Optional.empty());

        Object result = googleOAuthService.handleGoogleLogin("new-token");

        assertThat(result).isInstanceOf(OAuthPendingResponse.class);
        OAuthPendingResponse pending = (OAuthPendingResponse) result;
        assertThat(pending.getEmail()).isEqualTo("new@x.com");
        assertThat(pending.getName()).isEqualTo("Neo");
        assertThat(pending.getOauthToken()).isNotBlank();
    }

    @Test
    void handleGoogleLoginShouldThrowWhenTokenIsInvalid() throws Exception {
        when(verifierInstance.verify("bad-token")).thenReturn(null);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> googleOAuthService.handleGoogleLogin("bad-token"));
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void handleGoogleLoginShouldThrowWhenSecurityExceptionOccurs() throws Exception {
        when(verifierInstance.verify("error-token")).thenThrow(new GeneralSecurityException("boom"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> googleOAuthService.handleGoogleLogin("error-token"));
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void handleGoogleLoginShouldUseEmailPrefixIfNameIsBlank() throws Exception {
        GoogleIdToken.Payload payload = createPayload("blankname@x.com", "", "sub-456");
        when(verifierInstance.verify("blank-token")).thenReturn(mockedGoogleToken);
        when(mockedGoogleToken.getPayload()).thenReturn(payload);
        when(userRepository.findByEmail("blankname@x.com")).thenReturn(Optional.empty());

        Object result = googleOAuthService.handleGoogleLogin("blank-token");

        OAuthPendingResponse pending = (OAuthPendingResponse) result;
        assertThat(pending.getName()).isEqualTo("blankname");
    }

    @Test
    void completeGoogleRegistrationShouldThrowForInvalidToken() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> googleOAuthService.completeGoogleRegistration("non-existent-token", Role.FOUNDER));
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void completeGoogleRegistrationShouldThrowForAdminRole() throws Exception {
        // Setup pending user
        GoogleIdToken.Payload payload = createPayload("new@x.com", "Neo", "sub");
        when(verifierInstance.verify("token")).thenReturn(mockedGoogleToken);
        when(mockedGoogleToken.getPayload()).thenReturn(payload);
        OAuthPendingResponse pending = (OAuthPendingResponse) googleOAuthService.handleGoogleLogin("token");

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> googleOAuthService.completeGoogleRegistration(pending.getOauthToken(), Role.ADMIN));
        assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void completeGoogleRegistrationShouldHandleRaceConditionAndReturnSessionForAlreadySavedUser() throws Exception {
        GoogleIdToken.Payload payload = createPayload("race@x.com", "Race", "sub");
        when(verifierInstance.verify("token")).thenReturn(mockedGoogleToken);
        when(mockedGoogleToken.getPayload()).thenReturn(payload);
        OAuthPendingResponse pending = (OAuthPendingResponse) googleOAuthService.handleGoogleLogin("token");

        User existing = new User();
        existing.setId(5L);
        existing.setEmail("race@x.com");
        existing.setRole(Role.INVESTOR);

        when(userRepository.existsByEmail("race@x.com")).thenReturn(true);
        when(userRepository.findByEmail("race@x.com")).thenReturn(Optional.of(existing));
        when(jwtService.generateToken(5L, "INVESTOR")).thenReturn("jwt");
        when(refreshTokenService.createToken(5L)).thenReturn("rt");

        AuthSession session = googleOAuthService.completeGoogleRegistration(pending.getOauthToken(), Role.INVESTOR);

        assertThat(session.authResponse().getToken()).isEqualTo("jwt");
        verify(userRepository, never()).saveAndFlush(any());
    }

    @Test
    void completeGoogleRegistrationShouldSaveNewUserAndReturnSession() throws Exception {
        GoogleIdToken.Payload payload = createPayload("new@x.com", "Neo", "sub");
        when(verifierInstance.verify("token")).thenReturn(mockedGoogleToken);
        when(mockedGoogleToken.getPayload()).thenReturn(payload);
        OAuthPendingResponse pending = (OAuthPendingResponse) googleOAuthService.handleGoogleLogin("token");

        when(userRepository.existsByEmail("new@x.com")).thenReturn(false);

        User savedUser = new User();
        savedUser.setId(10L);
        savedUser.setEmail("new@x.com");
        savedUser.setName("Neo");
        savedUser.setRole(Role.COFOUNDER);

        when(userRepository.saveAndFlush(any(User.class))).thenReturn(savedUser);
        when(jwtService.generateToken(10L, "COFOUNDER")).thenReturn("jwt-10");
        when(refreshTokenService.createToken(10L)).thenReturn("rt-10");

        AuthSession session = googleOAuthService.completeGoogleRegistration(pending.getOauthToken(), Role.COFOUNDER);

        assertThat(session.authResponse().getToken()).isEqualTo("jwt-10");
        
        verify(userRepository).saveAndFlush(userCaptor.capture());
        User created = userCaptor.getValue();
        assertThat(created.getEmail()).isEqualTo("new@x.com");
        assertThat(created.getName()).isEqualTo("Neo");
        assertThat(created.getRole()).isEqualTo(Role.COFOUNDER);
        assertThat(created.isEmailVerified()).isTrue();
        assertThat(created.getAuthProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(created.getProviderId()).isEqualTo("sub");

        verify(syncService).syncUser(savedUser);
        verify(userRegisteredEventPublisher).publishUserRegisteredEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().getEmail()).isEqualTo("new@x.com");
    }

}
