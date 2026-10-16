package com.novabank;

import at.favre.lib.crypto.bcrypt.BCrypt;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

@Service
public class UserService {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final String LOCKED_MESSAGE = "Account is locked due to repeated failed login attempts";
    private static final String ALREADY_ENABLED_MESSAGE = "Two-factor authentication is already enabled";

    private final TotpService totp = new TotpService();

    public record LoginResult(UUID userId, boolean twoFactorRequired) {
    }

    public record TwoFactorSetup(String secret, String otpAuthUri) {
    }

    private record TwoFactorState(String passwordHash, String secret, boolean enabled, boolean locked) {
    }

    public UUID signup(String email, String password, String fullName) throws SQLException {
        if (email == null || !email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalArgumentException("Invalid email address");
        }
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters");
        }
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("Full name is required");
        }
        if (emailExists(email)) {
            throw new IllegalArgumentException("An account with this email already exists");
        }

        String passwordHash = BCrypt.withDefaults().hashToString(12, password.toCharArray());

        String sql = "INSERT INTO users (email, password_hash, full_name) VALUES (?, ?, ?) RETURNING id";

        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, email);
            stmt.setString(2, passwordHash);
            stmt.setString(3, fullName);

            ResultSet rs = stmt.executeQuery();
            rs.next();
            return (UUID) rs.getObject("id");
        }
    }

    public LoginResult authenticate(String email, String password) throws SQLException {
        if (email == null || password == null) {
            return null;
        }

        String sql = "SELECT id, password_hash, is_locked, totp_enabled FROM users WHERE email = ?";

        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, email);
            ResultSet rs = stmt.executeQuery();

            if (!rs.next()) {
                return null;
            }

            UUID userId = (UUID) rs.getObject("id");
            String storedHash = rs.getString("password_hash");
            boolean isLocked = rs.getBoolean("is_locked");
            boolean twoFactorEnabled = rs.getBoolean("totp_enabled");

            if (isLocked) {
                logAuditEvent(userId, "LOGIN_BLOCKED_LOCKED");
                throw new IllegalStateException(LOCKED_MESSAGE);
            }

            BCrypt.Result result = BCrypt.verifyer().verify(password.toCharArray(), storedHash);

            if (!result.verified) {
                recordFailedAttempt(userId);
                logAuditEvent(userId, "LOGIN_FAILURE");
                return null;
            }

            if (twoFactorEnabled) {
                logAuditEvent(userId, "LOGIN_PASSWORD_VERIFIED");
                return new LoginResult(userId, true);
            }

            resetFailedAttempts(userId);
            logAuditEvent(userId, "LOGIN_SUCCESS");
            return new LoginResult(userId, false);
        }
    }

    public UUID login(String email, String password) throws SQLException {
        LoginResult result = authenticate(email, password);
        if (result == null) {
            return null;
        }
        if (result.twoFactorRequired()) {
            throw new IllegalStateException("Two-factor code required");
        }
        return result.userId();
    }

    public boolean verifyLoginCode(UUID userId, String code) throws SQLException {
        TwoFactorState state = loadTwoFactorState(userId);
        if (state == null) {
            return false;
        }
        if (state.locked()) {
            logAuditEvent(userId, "LOGIN_BLOCKED_LOCKED");
            throw new IllegalStateException(LOCKED_MESSAGE);
        }
        if (!state.enabled() || state.secret() == null) {
            return false;
        }
        if (!reserveCodeAttempt(userId)) {
            logAuditEvent(userId, "LOGIN_BLOCKED_LOCKED");
            throw new IllegalStateException(LOCKED_MESSAGE);
        }
        if (!totp.isValidCode(state.secret(), code)) {
            lockIfExhausted(userId);
            logAuditEvent(userId, "LOGIN_2FA_FAILURE");
            return false;
        }
        resetFailedAttempts(userId);
        logAuditEvent(userId, "LOGIN_SUCCESS");
        return true;
    }

    public TwoFactorSetup beginTwoFactorSetup(UUID userId) throws SQLException {
        TwoFactorState state = loadTwoFactorState(userId);
        if (state == null) {
            throw new IllegalArgumentException("User not found");
        }
        if (state.enabled()) {
            throw new IllegalStateException(ALREADY_ENABLED_MESSAGE);
        }

        String secret = totp.generateSecret();
        String sql = "UPDATE users SET totp_secret = ? WHERE id = ? AND totp_enabled = FALSE";

        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, secret);
            stmt.setObject(2, userId);
            if (stmt.executeUpdate() != 1) {
                throw new IllegalStateException(ALREADY_ENABLED_MESSAGE);
            }
        }

        return new TwoFactorSetup(secret, totp.otpAuthUri(loadEmail(userId), secret));
    }

    public void confirmTwoFactorSetup(UUID userId, String password, String code) throws SQLException {
        TwoFactorState state = loadTwoFactorState(userId);
        if (state == null) {
            throw new IllegalArgumentException("User not found");
        }
        if (state.locked()) {
            throw new IllegalStateException(LOCKED_MESSAGE);
        }
        if (state.enabled()) {
            throw new IllegalStateException(ALREADY_ENABLED_MESSAGE);
        }
        if (state.secret() == null) {
            throw new IllegalStateException("Start two-factor setup first");
        }
        if (password == null || !BCrypt.verifyer().verify(password.toCharArray(), state.passwordHash()).verified) {
            recordFailedAttempt(userId);
            logAuditEvent(userId, "TWO_FACTOR_SETUP_FAILURE");
            throw new IllegalArgumentException("Incorrect password");
        }
        if (!totp.isValidCode(state.secret(), code)) {
            throw new IllegalArgumentException("Invalid code");
        }

        String sql = "UPDATE users SET totp_enabled = TRUE "
                + "WHERE id = ? AND totp_enabled = FALSE AND totp_secret = ?";

        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, userId);
            stmt.setString(2, state.secret());
            if (stmt.executeUpdate() != 1) {
                throw new IllegalStateException("Setup changed, please start again");
            }
        }

        logAuditEvent(userId, "TWO_FACTOR_ENABLED");
    }

    private boolean emailExists(String email) throws SQLException {
        String sql = "SELECT 1 FROM users WHERE email = ?";

        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, email);
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        }
    }

    private TwoFactorState loadTwoFactorState(UUID userId) throws SQLException {
        String sql = "SELECT password_hash, totp_secret, totp_enabled, is_locked FROM users WHERE id = ?";

        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, userId);
            ResultSet rs = stmt.executeQuery();
            if (!rs.next()) {
                return null;
            }
            return new TwoFactorState(
                    rs.getString("password_hash"),
                    rs.getString("totp_secret"),
                    rs.getBoolean("totp_enabled"),
                    rs.getBoolean("is_locked"));
        }
    }

    private String loadEmail(UUID userId) throws SQLException {
        String sql = "SELECT email FROM users WHERE id = ?";

        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, userId);
            ResultSet rs = stmt.executeQuery();
            rs.next();
            return rs.getString(1);
        }
    }

    private boolean reserveCodeAttempt(UUID userId) throws SQLException {
        String sql = "UPDATE users SET failed_login_attempts = failed_login_attempts + 1 "
                + "WHERE id = ? AND is_locked = FALSE AND failed_login_attempts < ?";

        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, userId);
            stmt.setInt(2, MAX_FAILED_ATTEMPTS);
            return stmt.executeUpdate() == 1;
        }
    }

    private void lockIfExhausted(UUID userId) throws SQLException {
        String sql = "UPDATE users SET is_locked = (failed_login_attempts >= ?) WHERE id = ?";

        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, MAX_FAILED_ATTEMPTS);
            stmt.setObject(2, userId);
            stmt.executeUpdate();
        }
    }

    private void recordFailedAttempt(UUID userId) throws SQLException {
        String sql = "UPDATE users SET failed_login_attempts = failed_login_attempts + 1, "
                + "is_locked = (failed_login_attempts + 1 >= ?) WHERE id = ?";

        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, MAX_FAILED_ATTEMPTS);
            stmt.setObject(2, userId);
            stmt.executeUpdate();
        }
    }

    private void resetFailedAttempts(UUID userId) throws SQLException {
        String sql = "UPDATE users SET failed_login_attempts = 0 WHERE id = ?";

        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, userId);
            stmt.executeUpdate();
        }
    }

    private void logAuditEvent(UUID userId, String eventType) throws SQLException {
        String sql = "INSERT INTO audit_log (user_id, event_type) VALUES (?, ?)";

        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, userId);
            stmt.setString(2, eventType);
            stmt.executeUpdate();
        }
    }
}