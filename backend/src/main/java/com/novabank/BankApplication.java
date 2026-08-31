package com.novabank;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

public class BankApplication {

    public static void main(String[] args) throws Exception {
        try (Connection conn = Database.getConnection()) {
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM users");
            rs.next();
            int count = rs.getInt(1);
            System.out.println("Connected successfully. User count: " + count);
        }
    }
}