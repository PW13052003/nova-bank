package com.novabank;

import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TotpServiceTest {

    private final TotpService totp = new TotpService();

    private String currentCode(String secret) throws Exception {
        long counter = Math.floorDiv(new SystemTimeProvider().getTime(), 30);
        return new DefaultCodeGenerator().generate(secret, counter);
    }

    @Test
    void generatedSecretIsBase32AndLongEnough() {
        String secret = totp.generateSecret();
        assertTrue(secret.matches("[A-Z2-7]{16,}"));
    }

    @Test
    void currentCodeIsAccepted() throws Exception {
        String secret = totp.generateSecret();
        assertTrue(totp.isValidCode(secret, currentCode(secret)));
    }

    @Test
    void wrongCodeIsRejected() throws Exception {
        String secret = totp.generateSecret();
        int valid = Integer.parseInt(currentCode(secret));
        String wrong = String.format("%06d", (valid + 500_000) % 1_000_000);
        assertFalse(totp.isValidCode(secret, wrong));
    }

    @Test
    void codeFromAnotherSecretIsRejected() throws Exception {
        String first = totp.generateSecret();
        String second = totp.generateSecret();
        assertFalse(totp.isValidCode(second, currentCode(first)));
    }

    @Test
    void malformedCodesAreRejected() throws Exception {
        String secret = totp.generateSecret();
        assertFalse(totp.isValidCode(secret, null));
        assertFalse(totp.isValidCode(secret, ""));
        assertFalse(totp.isValidCode(secret, "12345"));
        assertFalse(totp.isValidCode(secret, "1234567"));
        assertFalse(totp.isValidCode(secret, "abcdef"));
        assertFalse(totp.isValidCode(secret, " " + currentCode(secret)));
        assertFalse(totp.isValidCode(null, currentCode(secret)));
    }

    @Test
    void otpAuthUriIsWellFormed() {
        String uri = totp.otpAuthUri("user@example.com", "ABCDEFGHIJKLMNOP");
        assertTrue(uri.startsWith("otpauth://totp/Nova%20Bank:user%40example.com?"));
        assertTrue(uri.contains("secret=ABCDEFGHIJKLMNOP"));
        assertTrue(uri.contains("issuer=Nova%20Bank"));
    }
}