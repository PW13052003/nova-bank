package com.novabank;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest(properties = "jwt.secret=integration-test-secret-that-is-comfortably-longer-than-thirty-two-bytes")
@AutoConfigureMockMvc
class AuthFlowTest extends DatabaseTestBase {

    private static final String PASSWORD = "SecurePass123";
    private static final String EMAIL = "flow@example.com";

    @Autowired
    private MockMvc mvc;

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void loginWithoutTwoFactorSetsASessionThatWorks() throws Exception {
        signUp();
        MvcResult login = postJson("/auth/login", loginBody(PASSWORD));
        assertEquals(200, login.getResponse().getStatus());
        assertFalse(body(login).get("twoFactorRequired").asBoolean());
        Cookie session = login.getResponse().getCookie("token");
        assertNotNull(session);
        assertEquals(200, getStatus("/me", session));
    }

    @Test
    void loginWithTwoFactorGivesNoSessionYet() throws Exception {
        enableTwoFactor(signUp());
        MvcResult login = postJson("/auth/login", loginBody(PASSWORD));
        assertEquals(200, login.getResponse().getStatus());
        assertTrue(body(login).get("twoFactorRequired").asBoolean());
        assertNull(login.getResponse().getCookie("token"));
        assertNotNull(login.getResponse().getCookie("twofa"));
    }

    @Test
    void twoFactorTokenIsNeverAcceptedAsASession() throws Exception {
        enableTwoFactor(signUp());
        String pending = postJson("/auth/login", loginBody(PASSWORD)).getResponse().getCookie("twofa").getValue();

        assertEquals(403, getStatus("/me", new Cookie("token", pending)));
        assertEquals(403, getStatus("/me", new Cookie("twofa", pending)));
        assertEquals(403, mvc.perform(get("/me").header("Authorization", "Bearer " + pending))
                .andReturn().getResponse().getStatus());
    }

    @Test
    void correctCodeCompletesLoginAndStartsASession() throws Exception {
        String secret = enableTwoFactor(signUp());
        Cookie pending = postJson("/auth/login", loginBody(PASSWORD)).getResponse().getCookie("twofa");

        MvcResult verify = postJson("/auth/2fa/verify", "{\"code\":\"" + currentCode(secret) + "\"}", pending);
        assertEquals(200, verify.getResponse().getStatus());
        Cookie session = verify.getResponse().getCookie("token");
        assertNotNull(session);
        assertEquals(200, getStatus("/me", session));
    }

    @Test
    void wrongCodeIsRejectedWithoutASession() throws Exception {
        String secret = enableTwoFactor(signUp());
        Cookie pending = postJson("/auth/login", loginBody(PASSWORD)).getResponse().getCookie("twofa");

        MvcResult verify = postJson("/auth/2fa/verify", "{\"code\":\"" + wrongCode(secret) + "\"}", pending);
        assertEquals(401, verify.getResponse().getStatus());
        assertNull(verify.getResponse().getCookie("token"));
    }

    @Test
    void verifyWithoutThePendingCookieIsRejected() throws Exception {
        enableTwoFactor(signUp());
        MvcResult verify = postJson("/auth/2fa/verify", "{\"code\":\"123456\"}");
        assertEquals(401, verify.getResponse().getStatus());
    }

    @Test
    void setupRequiresALoggedInSession() throws Exception {
        assertEquals(403, postJson("/2fa/setup", "{}").getResponse().getStatus());
    }

    @Test
    void confirmWithWrongPasswordLeavesTwoFactorOff() throws Exception {
        Cookie session = signUp();
        String secret = json.readTree(postJson("/2fa/setup", "{}", session).getResponse().getContentAsString())
                .get("secret").asText();

        MvcResult confirm = postJson("/2fa/confirm",
                "{\"password\":\"WrongPassword1\",\"code\":\"" + currentCode(secret) + "\"}", session);
        assertEquals(400, confirm.getResponse().getStatus());

        MvcResult login = postJson("/auth/login", loginBody(PASSWORD));
        assertFalse(body(login).get("twoFactorRequired").asBoolean());
    }

    @Test
    void verifyEndpointIsRateLimited() throws Exception {
        String forwardedFor = "198.51.100.7,203.0.113.7";
        for (int i = 0; i < 5; i++) {
            assertEquals(401, postJson("/auth/2fa/verify", "{\"code\":\"123456\"}", forwardedFor)
                    .getResponse().getStatus());
        }
        assertEquals(429, postJson("/auth/2fa/verify", "{\"code\":\"123456\"}", forwardedFor)
                .getResponse().getStatus());
    }

    private Cookie signUp() throws Exception {
        MvcResult result = postJson("/auth/signup",
                "{\"email\":\"" + EMAIL + "\",\"password\":\"" + PASSWORD + "\",\"fullName\":\"Flow User\"}");
        assertEquals(200, result.getResponse().getStatus());
        return result.getResponse().getCookie("token");
    }

    private String enableTwoFactor(Cookie session) throws Exception {
        MvcResult setup = postJson("/2fa/setup", "{}", session);
        assertEquals(200, setup.getResponse().getStatus());
        String secret = body(setup).get("secret").asText();
        MvcResult confirm = postJson("/2fa/confirm",
                "{\"password\":\"" + PASSWORD + "\",\"code\":\"" + currentCode(secret) + "\"}", session);
        assertEquals(200, confirm.getResponse().getStatus());
        return secret;
    }

    private String loginBody(String password) {
        return "{\"email\":\"" + EMAIL + "\",\"password\":\"" + password + "\"}";
    }

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    private MvcResult postJson(String path, String content, Cookie... cookies) throws Exception {
        return postJson(path, content, "client-" + UUID.randomUUID(), cookies);
    }

    private MvcResult postJson(String path, String content, String forwardedFor, Cookie... cookies) throws Exception {
        MockHttpServletRequestBuilder request = post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Forwarded-For", forwardedFor)
                .content(content);
        if (cookies.length > 0) {
            request = request.cookie(cookies);
        }
        return mvc.perform(request).andReturn();
    }

    private int getStatus(String path, Cookie... cookies) throws Exception {
        MockHttpServletRequestBuilder request = get(path);
        if (cookies.length > 0) {
            request = request.cookie(cookies);
        }
        return mvc.perform(request).andReturn().getResponse().getStatus();
    }

    private String currentCode(String secret) throws Exception {
        long counter = Math.floorDiv(new SystemTimeProvider().getTime(), 30);
        return new DefaultCodeGenerator().generate(secret, counter);
    }

    private String wrongCode(String secret) throws Exception {
        int valid = Integer.parseInt(currentCode(secret));
        return String.format("%06d", (valid + 500_000) % 1_000_000);
    }
}