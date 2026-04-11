package com.founderlink.team.client;

import com.founderlink.team.exception.StartupServiceUnavailableException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TeamClientTest {

    private final StartupServiceClientFallback fallback = new StartupServiceClientFallback();

    @Test
    void getStartupById_ShouldThrowStartupServiceUnavailableException() {
        assertThatThrownBy(() -> fallback.getStartupById(100L))
                .isInstanceOf(StartupServiceUnavailableException.class)
                .hasMessageContaining("Startup service is temporarily unavailable");
    }
}
