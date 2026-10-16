package com.novabank;

import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserServiceTest extends DatabaseTestBase {

    private static final String PASSWORD = "SecurePass123";

    private final UserService userService = new UserService();

    @Test
    void signupThenLoginReturnsSameUserId() throws Exception {
        UUID signedUp = userService.signup("user@example.com", PASSWORD, "Test User");
        UUID loggedIn = userService.login("user@example.com", PASSWORD);
        assertEquals(signedUp, loggedIn);
    }

    @Test
    void authenticateWithoutTwoFactorReturnsUserDirectly() throws Exception {
        UUID userId = userService.signup("plain@example.com", PASSWORD, "Plain User");
        UserService.LoginResult result = userService.authenticate("plain@example.com", PASSWORD);
        assertNotNull(result);
        assertEquals(userId, result.userId());
        assertFalse(result.twoFactorRequired());
    }

    @Test
    void authenticateReportsSecondFactorRequiredOnceEnabled() throws Exception {
        UUID userId = userService.signup("two@example.com", PASSWORD, "Two Factor");
        enableTwoFactor(userId);
        UserService.LoginResult result = userService.authenticate("two@example.com", PASSWORD);
        assertNotNull(result);
        assertEquals(userId, result.userId());
        assertTrue(result.twoFactorRequired());
    }

    @Test
    void passwordOnlyLoginIsRefusedForTwoFactorUsers() throws Exception {
        UUID userId = userService.signup("two@example.com", PASSWORD, "Two Factor");
        enableTwoFactor(userId);
        assertThrows(IllegalStateException.class, () -> userService.login("two@example.com", PASSWORD));
    }

    @Test
    void correctCodeCompletesTwoFactorLogin() throws Exception {
        UUID userId = userService.signup("two@example.com", PASSWORD, "Two Factor");
        String secret = enableTwoFactor(userId);
        assertTrue(userService.verifyLoginCode(userId, currentCode(secret)));
    }

    @Test
    void wrongCodeIsRejected() throws Exception {
        UUID userId = userService.signup("two@example.com", PASSWORD, "Two Factor");
        String secret = enableTwoFactor(userId);
        assertFalse(userService.verifyLoginCode(userId, wrongCode(secret)));
    }

    @Test
    void confirmSetupWithWrongPasswordDoesNotEnableTwoFactor() throws Exception {
        UUID userId = userService.signup("setup@example.com", PASSWORD, "Setup User");
        String secret = userService.beginTwoFactorSetup(userId).secret();
        assertThrows(IllegalArgumentException.class,
                () -> userService.confirmTwoFactorSetup(userId, "WrongPassword1", currentCode(secret)));
        assertFalse(userService.authenticate("setup@example.com", PASSWORD).twoFactorRequired());
    }

    @Test
    void confirmSetupWithWrongCodeDoesNotEnableTwoFactor() throws Exception {
        UUID userId = userService.signup("setup@example.com", PASSWORD, "Setup User");
        String secret = userService.beginTwoFactorSetup(userId).secret();
        assertThrows(IllegalArgumentException.class,
                () -> userService.confirmTwoFactorSetup(userId, PASSWORD, wrongCode(secret)));
        assertFalse(userService.authenticate("setup@example.com", PASSWORD).twoFactorRequired());
    }

    @Test
    void setupCannotRestartOnceEnabled() throws Exception {
        UUID userId = userService.signup("two@example.com", PASSWORD, "Two Factor");
        enableTwoFactor(userId);
        assertThrows(IllegalStateException.class, () -> userService.beginTwoFactorSetup(userId));
    }

    @Test
    void passwordSuccessDoesNotResetCounterForTwoFactorUsers() throws Exception {
        UUID userId = userService.signup("two@example.com", PASSWORD, "Two Factor");
        String secret = enableTwoFactor(userId);

        for (int i = 0; i < 3; i++) {
            assertEquals(null, userService.authenticate("two@example.com", "WrongPassword1"));
        }
        assertEquals(3, failedAttempts(userId));

        assertTrue(userService.authenticate("two@example.com", PASSWORD).twoFactorRequired());
        assertEquals(3, failedAttempts(userId));

        assertTrue(userService.verifyLoginCode(userId, currentCode(secret)));
        assertEquals(0, failedAttempts(userId));
    }

    @Test
    void fiveWrongCodesLockTheAccount() throws Exception {
        UUID userId = userService.signup("two@example.com", PASSWORD, "Two Factor");
        String secret = enableTwoFactor(userId);

        for (int i = 0; i < 5; i++) {
            assertFalse(userService.verifyLoginCode(userId, wrongCode(secret)));
        }

        assertTrue(isLocked(userId));
        assertThrows(IllegalStateException.class, () -> userService.verifyLoginCode(userId, currentCode(secret)));
    }

    @Test
    void parallelWrongCodesAreCappedAtFiveAttempts() throws Exception {
        UUID userId = userService.signup("two@example.com", PASSWORD, "Two Factor");
        String secret = enableTwoFactor(userId);
        String wrong = wrongCode(secret);

        int threads = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger checked = new AtomicInteger();
        AtomicInteger blocked = new AtomicInteger();
        AtomicInteger unexpected = new AtomicInteger();

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    start.await();
                    if (userService.verifyLoginCode(userId, wrong)) {
                        unexpected.incrementAndGet();
                    } else {
                        checked.incrementAndGet();
                    }
                } catch (IllegalStateException e) {
                    blocked.incrementAndGet();
                } catch (Exception e) {
                    unexpected.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }

        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS));
        executor.shutdownNow();

        assertEquals(0, unexpected.get());
        assertEquals(5, checked.get());
        assertEquals(15, blocked.get());
        assertTrue(isLocked(userId));
    }

    private String enableTwoFactor(UUID userId) throws Exception {
        String secret = userService.beginTwoFactorSetup(userId).secret();
        userService.confirmTwoFactorSetup(userId, PASSWORD, currentCode(secret));
        return secret;
    }

    private String currentCode(String secret) throws Exception {
        long counter = Math.floorDiv(new SystemTimeProvider().getTime(), 30);
        return new DefaultCodeGenerator().generate(secret, counter);
    }

    private String wrongCode(String secret) throws Exception {
        int valid = Integer.parseInt(currentCode(secret));
        return String.format("%06d", (valid + 500_000) % 1_000_000);
    }

    private int failedAttempts(UUID userId) throws Exception {
        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT failed_login_attempts FROM users WHERE id = ?")) {
            stmt.setObject(1, userId);
            ResultSet rs = stmt.executeQuery();
            rs.next();
            return rs.getInt(1);
        }
    }

    private boolean isLocked(UUID userId) throws Exception {
        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT is_locked FROM users WHERE id = ?")) {
            stmt.setObject(1, userId);
            ResultSet rs = stmt.executeQuery();
            rs.next();
            return rs.getBoolean(1);
        }
    }
}