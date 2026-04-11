package com.founderlink.gateway.filter;

import com.founderlink.gateway.security.AuthenticatedUser;
import com.founderlink.gateway.security.Role;
import com.founderlink.gateway.service.HeaderService;
import com.founderlink.gateway.service.JwtService;
import com.founderlink.gateway.service.RbacService;
import com.founderlink.gateway.service.RouteValidator;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthenticationFilterTest {

    private final RouteValidator routeValidator = mock(RouteValidator.class);
    private final JwtService jwtService = mock(JwtService.class);
    private final RbacService rbacService = mock(RbacService.class);
    private final HeaderService headerService = mock(HeaderService.class);
    private final AuthenticationFilter filter = new AuthenticationFilter(routeValidator, jwtService, rbacService, headerService);

    @Test
    void returnsUnauthorizedWhenAuthorizationHeaderIsMissing() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/teams/invite").build()
        );
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(routeValidator.isSecured(exchange.getRequest())).thenReturn(true);

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any(ServerWebExchange.class));
        verify(jwtService, never()).authenticate(any());
    }

    @Test
    void returnsUnauthorizedWhenJwtServiceRejectsToken() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/teams/invite")
                        .header("Authorization", "Bearer expired-token")
                        .build()
        );
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(routeValidator.isSecured(exchange.getRequest())).thenReturn(true);
        when(jwtService.authenticate("expired-token"))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired token"));

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any(ServerWebExchange.class));
        verify(rbacService, never()).verifyAccess(any(), any(), any());
    }

    @Test
    void returnsUnauthorizedWhenTokenDoesNotContainRole() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/investments/startup/42")
                        .header("Authorization", "Bearer missing-role")
                        .build()
        );
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(routeValidator.isSecured(exchange.getRequest())).thenReturn(true);
        when(jwtService.authenticate("missing-role"))
                .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token role is missing"));

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any(ServerWebExchange.class));
        verify(rbacService, never()).verifyAccess(any(), any(), any());
    }

    @Test
    void returnsForbiddenWhenRbacRejectsRequest() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.post("/teams/invite")
                        .header("Authorization", "Bearer investor-token")
                        .build()
        );
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        AuthenticatedUser user = new AuthenticatedUser("investor-9", Role.INVESTOR);

        when(routeValidator.isSecured(exchange.getRequest())).thenReturn(true);
        when(jwtService.authenticate("investor-token")).thenReturn(user);
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "Role not allowed for request"))
                .when(rbacService).verifyAccess(exchange.getRequest().getMethod(), "/teams/invite", Role.INVESTOR);

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(chain, never()).filter(any(ServerWebExchange.class));
        verify(headerService, never()).applyAuthenticationHeaders(any(), any());
    }

    @Test
    void appliesHeadersAndContinuesWhenAuthenticationAndAuthorizationSucceed() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/investments/startup/42")
                        .header("Authorization", "Bearer founder-token")
                        .build()
        );
        MockServerWebExchange mutatedExchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/investments/startup/42")
                        .header("X-User-Id", "founder-1")
                        .header("X-User-Role", "ROLE_FOUNDER")
                        .header("X-Auth-Source", "gateway")
                        .build()
        );
        AuthenticatedUser user = new AuthenticatedUser("founder-1", Role.FOUNDER);
        AtomicReference<ServerWebExchange> chainedExchange = new AtomicReference<>();
        GatewayFilterChain chain = chained -> {
            chainedExchange.set(chained);
            return Mono.empty();
        };

        when(routeValidator.isSecured(exchange.getRequest())).thenReturn(true);
        when(jwtService.authenticate("founder-token")).thenReturn(user);
        when(headerService.applyAuthenticationHeaders(exchange, user)).thenReturn(mutatedExchange);

        filter.filter(exchange, chain).block();

        verify(rbacService).verifyAccess(exchange.getRequest().getMethod(), "/investments/startup/42", Role.FOUNDER);
        verify(headerService).applyAuthenticationHeaders(exchange, user);
        assertThat(chainedExchange.get()).isSameAs(mutatedExchange);
    }

    @Test
    void bypassesAuthenticationForPublicRoute() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/auth/login").build()
        );
        AtomicReference<ServerWebExchange> chainedExchange = new AtomicReference<>();
        GatewayFilterChain chain = chained -> {
            chainedExchange.set(chained);
            return Mono.empty();
        };

        when(routeValidator.isSecured(exchange.getRequest())).thenReturn(false);

        filter.filter(exchange, chain).block();

        assertThat(chainedExchange.get()).isSameAs(exchange);
        verifyNoSecurityInteractions();
    }

    @Test
    void bypassesAuthenticationForOptionsRequest() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.method(HttpMethod.OPTIONS, "/teams/invite").build()
        );
        AtomicReference<ServerWebExchange> chainedExchange = new AtomicReference<>();
        GatewayFilterChain chain = chained -> {
            chainedExchange.set(chained);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        // OPTIONS bypasses everything — isSecured and JWT are never called
        assertThat(chainedExchange.get()).isSameAs(exchange);
        verify(routeValidator, never()).isSecured(any());
        verifyNoSecurityInteractions();
    }

    @Test
    void returnsUnauthorizedWhenAuthorizationSchemeIsNotBearer() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/teams/invite")
                        .header("Authorization", "Basic dXNlcjpwYXNz")
                        .build()
        );
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(routeValidator.isSecured(exchange.getRequest())).thenReturn(true);

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any(ServerWebExchange.class));
        verify(jwtService, never()).authenticate(anyString());
    }

    @Test
    void returnsUnauthorizedWhenBearerTokenIsBlankAfterPrefix() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/teams/invite")
                        .header("Authorization", "Bearer   ")
                        .build()
        );
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(routeValidator.isSecured(exchange.getRequest())).thenReturn(true);

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        verify(chain, never()).filter(any(ServerWebExchange.class));
        verify(jwtService, never()).authenticate(anyString());
    }

    @Test
    void returnsInternalServerErrorOnUnexpectedException() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/teams/invite")
                        .header("Authorization", "Bearer good-token")
                        .build()
        );
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(routeValidator.isSecured(exchange.getRequest())).thenReturn(true);
        when(jwtService.authenticate("good-token"))
                .thenThrow(new RuntimeException("Unexpected internal failure"));

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        verify(chain, never()).filter(any(ServerWebExchange.class));
    }

    @Test
    void processesSecuredRequestWhenTokenIsRealJwtFormat() {
        // Build an actual Base64url.Base64url.Base64url JWT so parts.length == 3 in the debug decode block
        // jwtService is still mocked so authentication succeeds without real parsing
        String fakeJwt =
                "eyJhbGciOiJIUzI1NiJ9" + // header
                ".eyJzdWIiOiJmb3VuZGVyLTEiLCJyb2xlIjoiRk9VTkRFUiJ9" + // payload
                ".SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c";     // signature

        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/users/42")
                        .header("Authorization", "Bearer " + fakeJwt)
                        .build()
        );
        MockServerWebExchange mutatedExchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/users/42")
                        .header("X-User-Id", "founder-1")
                        .header("X-User-Role", "ROLE_FOUNDER")
                        .build()
        );
        AuthenticatedUser user = new AuthenticatedUser("founder-1", Role.FOUNDER);
        AtomicReference<ServerWebExchange> chainedExchange = new AtomicReference<>();
        GatewayFilterChain chain = chained -> {
            chainedExchange.set(chained);
            return Mono.empty();
        };

        when(routeValidator.isSecured(exchange.getRequest())).thenReturn(true);
        when(jwtService.authenticate(fakeJwt)).thenReturn(user);
        when(headerService.applyAuthenticationHeaders(exchange, user)).thenReturn(mutatedExchange);

        filter.filter(exchange, chain).block();

        // The 3-part debug decode branch is hit; request continues to chain
        assertThat(chainedExchange.get()).isSameAs(mutatedExchange);
    }

    private void verifyNoSecurityInteractions() {
        verify(jwtService, never()).authenticate(any());
        verify(rbacService, never()).verifyAccess(any(), any(), any());
        verify(headerService, never()).applyAuthenticationHeaders(any(), any());
        Mockito.verifyNoMoreInteractions(jwtService, rbacService, headerService);
    }
}
