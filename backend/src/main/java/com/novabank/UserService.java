package com.novabank;

import at.favre.lib.crypto.bcrypt.BCrypt;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class UserService {

    public UUID signup(String email, String password, String fullName) throws SQLException, java.io.IOException {
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

    private boolean emailExists(String email) throws SQLException, java.io.IOException {
        String sql = "SELECT 1 FROM users WHERE email = ?";

        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, email);
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        }
    }

        private static final int MAX_FAILED_ATTEMPTS = 5;

    public UUID login(String email, String password) throws SQLException, java.io.IOException {
        String sql = "SELECT id, password_hash, failed_login_attempts, is_locked FROM users WHERE email = ?";

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

            if (isLocked) {
                logAuditEvent(userId, "LOGIN_BLOCKED_LOCKED");
                throw new IllegalStateException("Account is locked due to repeated failed login attempts");
            }

            BCrypt.Result result = BCrypt.verifyer().verify(password.toCharArray(), storedHash);

            if (!result.verified) {
                recordFailedAttempt(userId);
                logAuditEvent(userId, "LOGIN_FAILURE");
                return null;
            }

            resetFailedAttempts(userId);
            logAuditEvent(userId, "LOGIN_SUCCESS");
            return userId;
        }
    }

    private void recordFailedAttempt(UUID userId) throws SQLException, java.io.IOException {
        String sql = "UPDATE users SET failed_login_attempts = failed_login_attempts + 1, " +
                "is_locked = (failed_login_attempts + 1 >= ?) WHERE id = ?";

        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, MAX_FAILED_ATTEMPTS);
            stmt.setObject(2, userId);
            stmt.executeUpdate();
        }
    }

    private void resetFailedAttempts(UUID userId) throws SQLException, java.io.IOException {
        String sql = "UPDATE users SET failed_login_attempts = 0 WHERE id = ?";

        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, userId);
            stmt.executeUpdate();
        }
    }

    private void logAuditEvent(UUID userId, String eventType) throws SQLException, java.io.IOException {
        String sql = "INSERT INTO audit_log (user_id, event_type) VALUES (?, ?)";

        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, userId);
            stmt.setString(2, eventType);
            stmt.executeUpdate();
        }
    }
}