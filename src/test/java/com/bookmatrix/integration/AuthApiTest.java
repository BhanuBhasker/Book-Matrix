package com.bookmatrix.integration;

import com.bookmatrix.model.Role;
import com.bookmatrix.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Registration, login, logout, sessions, CSRF. */
class AuthApiTest extends IntegrationTestBase {

    private Map<String, Object> registration(String email) {
        return Map.of("fullName", "Asha Rao", "email", email, "password", "secret123", "confirmPassword", "secret123");
    }

    @Test
    void registerCreatesNormalUserWithHashedPassword() throws Exception {
        String email = uniqueEmail("new");
        Map<String, Object> body = new java.util.HashMap<>(registration(email.toUpperCase()));
        body.put("role", "ADMIN");     // privilege-escalation attempt: must be ignored
        JsonNode res = body(doPost(anonymous(), "/api/auth/register", body).andExpect(status().isCreated()));
        assertThat(res.at("/data/role").asText()).isEqualTo("USER");
        assertThat(res.at("/data/email").asText()).isEqualTo(email);        // stored lower-case
        assertThat(res.toString()).doesNotContain("password");
        String hash = db.query(conn -> userDao.findByEmail(conn, email)).orElseThrow().getPasswordHash();
        assertThat(hash).startsWith("$2a$").isNotEqualTo("secret123");
    }

    @Test
    void registrationValidation() throws Exception {
        JsonNode res = body(doPost(anonymous(), "/api/auth/register",
                Map.of("fullName", "", "email", "bad", "password", "weak", "confirmPassword", "other"))
                .andExpect(status().isBadRequest()));
        assertThat(res.at("/error/code").asText()).isEqualTo("VALIDATION_FAILED");
        assertThat(res.at("/error/fields").fieldNames()).toIterable()
                .containsExactly("fullName", "email", "password", "confirmPassword");

        doPost(anonymous(), "/api/auth/register", Map.of("fullName", "A".repeat(101), "email", uniqueEmail("x"),
                "password", "secret123", "confirmPassword", "secret123"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.fullName").exists());
        doPost(anonymous(), "/api/auth/register", Map.of("fullName", "<script>", "email", uniqueEmail("x"),
                "password", "secret123", "confirmPassword", "secret123"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void duplicateEmailIsRejected() throws Exception {
        String email = uniqueEmail("dup");
        doPost(anonymous(), "/api/auth/register", registration(email)).andExpect(status().isCreated());
        doPost(anonymous(), "/api/auth/register", registration(email))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("EMAIL_TAKEN"));
    }

    @Test
    void malformedJsonGivesCleanError() throws Exception {
        var c = anonymous();
        mvc.perform(post("/api/auth/register").session(c.session()).header("X-CSRF-Token", c.csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void loginRotatesSessionAndRejectsBadCredentials() throws Exception {
        String email = uniqueEmail("login");
        createUser(email, Role.USER);
        Client anon = anonymous();
        String before = anon.session().getId();
        var result = doPost(anon, "/api/auth/login", Map.of("email", email, "password", PASSWORD))
                .andExpect(status().isOk()).andReturn();
        MockHttpSession after = (MockHttpSession) result.getRequest().getSession(false);
        assertThat(after.getId()).as("session fixation protection").isNotEqualTo(before);
        assertThat(anon.session().isInvalid()).isTrue();

        doPost(anonymous(), "/api/auth/login", Map.of("email", email, "password", "wrong-pass1"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
        doPost(anonymous(), "/api/auth/login", Map.of("email", uniqueEmail("ghost"), "password", "whatever1"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.message").value("Email or password is incorrect."));
        doPost(anonymous(), "/api/auth/login", Map.of("email", "", "password", ""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void repeatedFailuresLockTheAccountTemporarily() throws Exception {
        String email = uniqueEmail("brute");
        createUser(email, Role.USER);
        for (int i = 0; i < 5; i++) {
            doPost(anonymous(), "/api/auth/login", Map.of("email", email, "password", "nope-" + i + "x"));
        }
        doPost(anonymous(), "/api/auth/login", Map.of("email", email, "password", PASSWORD))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void deactivatedUserCannotLogIn() throws Exception {
        String email = uniqueEmail("off");
        long id = createUser(email, Role.USER);
        db.query(conn -> userDao.updateStatus(conn, id, com.bookmatrix.model.AccountStatus.DEACTIVATED, now()));
        doPost(anonymous(), "/api/auth/login", Map.of("email", email, "password", PASSWORD))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code").value("ACCOUNT_DEACTIVATED"));
    }

    @Test
    void sessionEndpointAndLogout() throws Exception {
        Client member = newMember();
        doGet(member, "/api/auth/session").andExpect(jsonPath("$.data.user.role").value("USER"));
        doGet(member, "/api/me").andExpect(status().isOk());
        doPost(member, "/api/auth/logout", null).andExpect(status().isOk());
        doGet(member, "/api/me").andExpect(status().isUnauthorized());
    }

    @Test
    void stateChangingRequestsNeedTheCsrfToken() throws Exception {
        Client member = newMember();
        mvc.perform(post("/api/bookings").session(member.session()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("CSRF_INVALID"));
        mvc.perform(post("/api/bookings").session(member.session()).header("X-CSRF-Token", "forged")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void expiredSessionIsReported() throws Exception {
        Client member = newMember();
        member.session().invalidate();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/me")
                        .cookie(new jakarta.servlet.http.Cookie("BMSESSION", "expired-id")))
                .andExpect(status().isUnauthorized());
    }
}
