package com.founderlink.User_Service.exceptions;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ExceptionTest {

    @Test
    void testUserNotFoundException() {
        UserNotFoundException ex = new UserNotFoundException("User not found");
        assertThat(ex.getMessage()).isEqualTo("User not found");
    }

    @Test
    void testConflictException() {
        ConflictException ex = new ConflictException("Conflict occurred");
        assertThat(ex.getMessage()).isEqualTo("Conflict occurred");
    }
}
