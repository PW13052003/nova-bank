package com.novabank;

public class BankApplication {

    public static void main(String[] args) throws Exception {
        UserService userService = new UserService();
        var result = userService.login("test@novabank.com", "SecurePass123");
        System.out.println("Login result: " + result);
    }
}