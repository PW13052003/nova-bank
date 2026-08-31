package com.novabank;

import java.util.UUID;

public class BankApplication {

    public static void main(String[] args) throws Exception {
        AccountService accountService = new AccountService();
        UUID userId = UUID.fromString("d86a0358-6f9a-4f12-8301-51c4393be113");

        UUID checkingId = accountService.createAccount(userId, "CHECKING");
        System.out.println("Created checking account: " + checkingId);

        UUID savingsId = accountService.createAccount(userId, "SAVINGS");
        System.out.println("Created savings account: " + savingsId);
    }
}