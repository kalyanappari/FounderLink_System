package com.founderlink.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {

    @Mock
    private JwtService jwtService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtAuthFilter jwtAuthFilter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void doFilterInternalShouldSkipActuatorPaths() throws ServletException, IOException {
        when(request.getRequestURI()).thenReturn("/actuator/health");

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(jwtService);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilterInternalShouldProceedWhenNoAuthHeader() throws ServletException, IOException {
        when(request.getRequestURI()).thenReturn("/api/data");
        when(request.getHeader("Authorization")).thenReturn(null);

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(jwtService);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilterInternalShouldProceedWhenAuthHeaderNotBearer() throws ServletException, IOException {
        when(request.getRequestURI()).thenReturn("/api/data");
        when(request.getHeader("Authorization")).thenReturn("Basic abcd");

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(jwtService);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilterInternalShouldHandleInvalidJwtGracefully() throws ServletException, IOException {
        when(request.getRequestURI()).thenReturn("/api/data");
        when(request.getHeader("Authorization")).thenReturn("Bearer invalid-token");
        when(jwtService.extractUsername("invalid-token")).thenThrow(new RuntimeException("invalid"));

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilterInternalShouldSetAuthenticationWhenTokenIsValid() throws ServletException, IOException {
        String token = "valid-token";
        String username = "alice@founderlink.com";
        String role = "FOUNDER";
        Long userId = 123L;

        when(request.getRequestURI()).thenReturn("/api/data");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtService.extractUsername(token)).thenReturn(username);
        when(jwtService.validateToken(token)).thenReturn(true);
        when(jwtService.extractRole(token)).thenReturn(role);
        when(jwtService.extractUserId(token)).thenReturn(userId);

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        var auth = SecurityContextHolder.getContext().getAuthentication();
        assertThat(auth).isNotNull();
        assertThat(auth.getPrincipal()).isInstanceOf(JwtPrincipal.class);
        JwtPrincipal principal = (JwtPrincipal) auth.getPrincipal();
        assertThat(principal.userId()).isEqualTo(userId);
        assertThat(principal.email()).isEqualTo(username);
        assertThat(principal.role()).isEqualTo(role);
        assertThat(auth.getAuthorities()).hasSize(1);
        assertThat(auth.getAuthorities().iterator().next().getAuthority()).isEqualTo("ROLE_FOUNDER");
    }

    @Test
    void doFilterInternalShouldNotSetAuthWhenRoleIsMissing() throws ServletException, IOException {
        String token = "valid-token";
        String username = "alice@founderlink.com";

        when(request.getRequestURI()).thenReturn("/api/data");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtService.extractUsername(token)).thenReturn(username);
        when(jwtService.validateToken(token)).thenReturn(true);
        when(jwtService.extractRole(token)).thenReturn(null);
        when(jwtService.extractUserId(token)).thenReturn(123L);

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilterInternalShouldNotSetAuthWhenValidationFails() throws ServletException, IOException {
        String token = "invalid-token";
        String username = "alice@founderlink.com";

        when(request.getRequestURI()).thenReturn("/api/data");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtService.extractUsername(token)).thenReturn(username);
        when(jwtService.validateToken(token)).thenReturn(false);

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilterInternalShouldNotSetAuthWhenUsernameIsNull() throws ServletException, IOException {
        String token = "valid-token";

        when(request.getRequestURI()).thenReturn("/api/data");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtService.extractUsername(token)).thenReturn(null);

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilterInternalShouldSkipWhenAuthenticationAlreadyExists() throws ServletException, IOException {
        String token = "valid-token";
        String username = "alice@founderlink.com";
        org.springframework.security.core.Authentication existingAuth = mock(
                org.springframework.security.core.Authentication.class);
        SecurityContextHolder.getContext().setAuthentication(existingAuth);

        when(request.getRequestURI()).thenReturn("/api/data");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtService.extractUsername(token)).thenReturn(username);

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verify(jwtService).extractUsername(token);
        verify(jwtService, never()).validateToken(token); // Short-circuited by existing auth
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isEqualTo(existingAuth);
    }

    @Test
    void doFilterInternalShouldHandleExtractionException() throws ServletException, IOException {
        String token = "malformed-token";
        when(request.getRequestURI()).thenReturn("/api/data");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtService.extractUsername(token)).thenThrow(new RuntimeException("invalid"));

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void doFilterInternalShouldNotSetAuthWhenRoleOrUserIdMissing() throws ServletException, IOException {
        String token = "valid-token";
        String username = "alice@founderlink.com";

        when(request.getRequestURI()).thenReturn("/api/data");
        when(request.getHeader("Authorization")).thenReturn("Bearer " + token);
        when(jwtService.extractUsername(token)).thenReturn(username);
        when(jwtService.validateToken(token)).thenReturn(true);
        when(jwtService.extractRole(token)).thenReturn(""); // blank role
        when(jwtService.extractUserId(token)).thenReturn(123L);

        jwtAuthFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
