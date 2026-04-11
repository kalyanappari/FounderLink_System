package com.founderlink.notification.client;

import com.founderlink.notification.dto.StartupDTO;
import com.founderlink.notification.dto.UserDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ClientFallbackTest {

    @Test
    @DisplayName("StartupServiceClientFallback - returns null")
    void startupServiceClientFallbackReturnsNull() {
        StartupServiceClientFallback fallback = new StartupServiceClientFallback();
        StartupDTO res = fallback.getStartupById(1L);
        assertThat(res).isNull();
    }

    @Test
    @DisplayName("UserServiceClientFallback - getUserById returns null")
    void userServiceClientFallbackReturnsNull() {
        UserServiceClientFallback fallback = new UserServiceClientFallback();
        UserDTO res = fallback.getUserById(1L);
        assertThat(res).isNull();
    }

    @Test
    @DisplayName("UserServiceClientFallback - getAllUsers returns empty page")
    void userServiceClientFallbackGetAllReturnsEmpty() {
        UserServiceClientFallback fallback = new UserServiceClientFallback();
        var res = fallback.getAllUsers(0, 10);
        assertThat(res).isNotNull();
        assertThat(res.getContent()).isEmpty();
    }

    @Test
    @DisplayName("UserServiceClientFallback - getUsersByRole returns empty page")
    void userServiceClientFallbackGetByRoleReturnsEmpty() {
        UserServiceClientFallback fallback = new UserServiceClientFallback();
        var res = fallback.getUsersByRole("INVESTOR", 0, 10);
        assertThat(res).isNotNull();
        assertThat(res.getContent()).isEmpty();
    }
}
