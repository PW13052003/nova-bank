package com.novabank;

import java.util.UUID;

public class BankApplication {

    public static void main(String[] args) throws Exception {
        UserService userService = new UserService();

        UUID correctLogin = userService.login("test@novabank.com", "SecurePass123");
        System.out.println("Correct password login result: " + correctLogin);

        UUID wrongLogin = userService.login("test@novabank.com", "WrongPassword");
        System.out.println("Wrong password login result: " + wrongLogin);

        UUID nonexistentLogin = userService.login("nobody@novabank.com", "AnyPassword");
        System.out.println("Nonexistent user login result: " + nonexistentLogin);
    }
}