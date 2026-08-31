package com.novabank;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class AccountService {

    public UUID createAccount(UUID userId, String accountType) throws SQLException, java.io.IOException {
        if (!accountType.equals("CHECKING") && !accountType.equals("SAVINGS")) {
            throw new IllegalArgumentException("Account type must be CHECKING or SAVINGS");
        }

        String accountNumber = generateAccountNumber();

        String sql = "INSERT INTO accounts (user_id, account_number, account_type) VALUES (?, ?, ?) RETURNING id";

        try (Connection conn = Database.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setObject(1, userId);
            stmt.setString(2, accountNumber);
            stmt.setString(3, accountType);

            ResultSet rs = stmt.executeQuery();
            rs.next();
            return (UUID) rs.getObject("id");
        }
    }

    private String generateAccountNumber() {
        long number = ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_999_999_999L);
        return String.valueOf(number);
    }
}