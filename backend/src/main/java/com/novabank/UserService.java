package com.novabank;

import at.favre.lib.crypto.bcrypt.BCrypt;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class UserService {

    public UUID signup(String email, String password, String fullName) throws SQLException, java.io.IOException {
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

    public UUID login(String email, String password) throws SQLException, java.io.IOException {
    String sql = "SELECT id, password_hash FROM users WHERE email = ?";

        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, email);
            ResultSet rs = stmt.executeQuery();

            if (!rs.next()) {
                return null;
            }

            String storedHash = rs.getString("password_hash");
            BCrypt.Result result = BCrypt.verifyer().verify(password.toCharArray(), storedHash);

            if (!result.verified) {
                return null;
            }

            return (UUID) rs.getObject("id");
        }
    }
}