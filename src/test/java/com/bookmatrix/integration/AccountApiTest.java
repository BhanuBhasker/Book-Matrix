package com.bookmatrix.integration;

import com.bookmatrix.model.Role;
import com.bookmatrix.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Profile, password change and account closure. */
class AccountApiTest extends IntegrationTestBase {

    @Test
    void profileUpdateAndValidation() throws Exception {
        Client member = newMember();
        doPatch(member, "/api/me", Map.of("fullName", "  Meera   Iyer ", "phone", "+91 90000 11111"))
                .andExpect(jsonPath("$.data.fullName").value("Meera Iyer"))
                .andExpect(jsonPath("$.data.phone").value("+91 90000 11111"));
        doPatch(member, "/api/me", Map.of("fullName", "M", "phone", "call me"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.fullName").exists()).andExpect(jsonPath("$.error.fields.phone").exists());
    }

    @Test
    void passwordChange() throws Exception {
        String email = uniqueEmail("pw");
        createUser(email, Role.USER);
        Client member = login(email);
        doPut(member, "/api/me/password", Map.of("currentPassword", "wrong-one1", "newPassword", "brandnew99", "confirmPassword", "brandnew99"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.currentPassword").exists());
        doPut(member, "/api/me/password", Map.of("currentPassword", PASSWORD, "newPassword", "short", "confirmPassword", "short"))
                .andExpect(status().isBadRequest());
        doPut(member, "/api/me/password", Map.of("currentPassword", PASSWORD, "newPassword", PASSWORD, "confirmPassword", PASSWORD))
                .andExpect(status().isBadRequest());
        doPut(member, "/api/me/password", Map.of("currentPassword", PASSWORD, "newPassword", "brandnew99", "confirmPassword", "brandnew99"))
                .andExpect(status().isOk());
        login(email, "brandnew99");
        doPost(anonymous(), "/api/auth/login", Map.of("email", email, "password", PASSWORD)).andExpect(status().isUnauthorized());
    }

    @Test
    void closingAccountWithoutBookingsDeletesIt() throws Exception {
        String email = uniqueEmail("leaver");
        createUser(email, Role.USER);
        Client member = login(email);
        doDelete(member, "/api/me", Map.of("password", PASSWORD, "confirmation", "nope")).andExpect(status().isBadRequest());
        doDelete(member, "/api/me", Map.of("password", "wrongpass1", "confirmation", "DELETE")).andExpect(status().isBadRequest());
        doDelete(member, "/api/me", Map.of("password", PASSWORD, "confirmation", "DELETE"))
                .andExpect(jsonPath("$.data.outcome").value("DELETED"));
        java.util.Optional<com.bookmatrix.model.User> gone = db.query(c -> userDao.findByEmail(c, email));
        assertThat(gone).isEmpty();
    }

    @Test
    void closingAccountWithBookingsDeactivatesAndReleasesUpcomingTickets() throws Exception {
        String email = uniqueEmail("leaver2");
        createUser(email, Role.USER);
        Client member = login(email);
        long event = createUpcomingEvent("Will Release", 10, "100");
        doPost(member, "/api/bookings", Map.of("eventId", event, "quantity", 3)).andExpect(status().isCreated());
        assertThat(ticketsSold(event)).isEqualTo(3);
        doDelete(member, "/api/me", Map.of("password", PASSWORD, "confirmation", "DELETE"))
                .andExpect(jsonPath("$.data.outcome").value("DEACTIVATED"))
                .andExpect(jsonPath("$.data.bookingsCancelled").value(1));
        assertThat(ticketsSold(event)).isZero();
        assertThat(countRows("SELECT COUNT(*) FROM bookings WHERE event_id = ?", event)).isEqualTo(1);   // history kept
        doPost(anonymous(), "/api/auth/login", Map.of("email", email, "password", PASSWORD)).andExpect(status().isUnauthorized());
    }

    @Test
    void onlyAdminCannotCloseTheirAccount() throws Exception {
        Client admin = newAdmin();
        doDelete(admin, "/api/me", Map.of("password", PASSWORD, "confirmation", "DELETE"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("LAST_ADMIN"));
    }
}
