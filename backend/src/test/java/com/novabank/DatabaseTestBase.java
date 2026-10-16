package com.novabank;

import org.junit.jupiter.api.BeforeEach;

import java.sql.Connection;
import java.sql.Statement;

abstract class DatabaseTestBase {

    @BeforeEach
    void resetDatabase() throws Exception {
        String url = System.getenv("DATABASE_URL");
        if (url == null || !url.contains("novabank_test")) {
            throw new IllegalStateException("Refusing to run: DATABASE_URL must point at novabank_test");
        }
        try (Connection conn = Database.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("TRUNCATE TABLE ledger_entries, transactions, audit_log, accounts, users CASCADE");
        }
    }
}