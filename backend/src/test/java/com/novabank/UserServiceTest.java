package com.novabank;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserServiceTest extends DatabaseTestBase {

    private final UserService userService = new UserService();

    @Test
    void signupThenLoginReturnsSameUserId() throws Exception {
        UUID signedUp = userService.signup("user@example.com", "SecurePass123", "Test User");
        UUID loggedIn = userService.login("user@example.com", "SecurePass123");
        assertEquals(signedUp, loggedIn);
    }
}