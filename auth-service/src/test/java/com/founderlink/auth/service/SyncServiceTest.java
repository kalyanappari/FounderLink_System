package com.founderlink.auth.service;

import com.founderlink.auth.client.UserClient;
import com.founderlink.auth.dto.UserResponse;
import com.founderlink.auth.entity.Role;
import com.founderlink.auth.entity.User;
import com.founderlink.auth.exception.UserServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest(
        classes = SyncServiceTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
class SyncServiceTest {

    @Autowired
    private SyncService syncService;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    @MockBean
    private UserClient userClient;

    private User user;

    @BeforeEach
    void setUp() {
        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("userServiceSync");
        circuitBreaker.reset();

        user = new User();
        user.setId(300L);
        user.setName("Retry User");
        user.setEmail("retry@founderlink.com");
        user.setRole(Role.FOUNDER);
        clearInvocations(userClient);
    }

    @Test
    void syncUserShouldCompleteWhenUserServiceCallSucceeds() {
        UserResponse response = new UserResponse();
        response.setUserId(user.getId());
        response.setEmail(user.getEmail());
        response.setName(user.getName());

        when(userClient.createUser(any())).thenReturn(ResponseEntity.ok(response));

        assertThatCode(() -> syncService.syncUser(user)).doesNotThrowAnyException();
        verify(userClient, times(1)).createUser(any());
    }

    @Test
    void syncUserShouldRetryOnTransientFailureAndEventuallySucceed() {
        UserResponse response = new UserResponse();
        response.setUserId(user.getId());

        when(userClient.createUser(any()))
                .thenThrow(new UserServiceUnavailableException("UserClient#createUser", "Service unavailable"))
                .thenThrow(new UserServiceUnavailableException("UserClient#createUser", "Service unavailable"))
                .thenReturn(ResponseEntity.ok(response));

        assertThatCode(() -> syncService.syncUser(user)).doesNotThrowAnyException();
        verify(userClient, times(3)).createUser(any());
    }

    @Test
    void syncUserShouldThrowAfterRetriesExhausted() {
        when(userClient.createUser(any()))
                .thenThrow(new UserServiceUnavailableException("UserClient#createUser", "Service unavailable"));

        assertThatThrownBy(() -> syncService.syncUser(user))
                .isInstanceOf(UserServiceUnavailableException.class);
        verify(userClient, times(3)).createUser(any());
    }

    @Test
    void syncUserShouldOpenCircuitBreakerAfterRepeatedFailures() {
        when(userClient.createUser(any()))
                .thenThrow(new UserServiceUnavailableException("UserClient#createUser", "Service unavailable"));

        for (int attempt = 0; attempt < 4; attempt++) {
            assertThatThrownBy(() -> syncService.syncUser(user))
                    .isInstanceOf(UserServiceUnavailableException.class);
        }

        CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker("userServiceSync");

        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        clearInvocations(userClient);

        assertThatThrownBy(() -> syncService.syncUser(user))
                .isInstanceOf(UserServiceUnavailableException.class);
        verify(userClient, never()).createUser(any());
    }

    @Test
    void syncUserFallbackShouldPropagateServiceClientException() {
        com.founderlink.auth.exception.UserServiceClientException clientEx =
                new com.founderlink.auth.exception.UserServiceClientException("UserClient#createUser", 400, "Bad Request");

        assertThatThrownBy(() -> syncService.syncUserFallback(user, clientEx))
                .isSameAs(clientEx);
    }

    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            DataSourceTransactionManagerAutoConfiguration.class,
            HibernateJpaAutoConfiguration.class,
            SecurityAutoConfiguration.class,
            UserDetailsServiceAutoConfiguration.class
    })
    @Import(SyncService.class)
    static class TestApplication {
    }
}
