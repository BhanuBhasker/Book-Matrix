package com.bookmatrix.integration;

import com.bookmatrix.model.Role;
import com.bookmatrix.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminUserApiTest extends IntegrationTestBase {

    @Test
    void listSearchAndDetailsNeverExposePasswords() throws Exception {
        Client admin = newAdmin();
        String email = uniqueEmail("findme");
        long id = createUser(email, Role.USER);
        String list = doGet(admin, "/api/admin/users?q=findme").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems").value(1))
                .andExpect(jsonPath("$.data.items[0].user.email").value(email))
                .andReturn().getResponse().getContentAsString();
        assertThat(list).doesNotContainIgnoringCase("password").doesNotContain("$2a$");
        doGet(admin, "/api/admin/users?role=ADMIN").andExpect(jsonPath("$.data.totalItems").value(1));
        doGet(admin, "/api/admin/users/" + id).andExpect(jsonPath("$.data.email").value(email));
        doGet(admin, "/api/admin/users/99999").andExpect(status().isNotFound());
        doPatch(admin, "/api/admin/users/" + id, Map.of("fullName", "Renamed Person", "phone", "+91 98765 43210"))
                .andExpect(jsonPath("$.data.fullName").value("Renamed Person"));
        doPatch(admin, "/api/admin/users/" + id, Map.of("fullName", "", "phone", "abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deactivationTakesEffectImmediately() throws Exception {
        Client admin = newAdmin();
        String email = uniqueEmail("victim");
        long id = createUser(email, Role.USER);
        Client member = login(email);
        doGet(member, "/api/me").andExpect(status().isOk());
        doPost(admin, "/api/admin/users/" + id + "/deactivate", null).andExpect(jsonPath("$.data.status").value("DEACTIVATED"));
        doGet(member, "/api/me").andExpect(status().isUnauthorized());            // open session no longer works
        doPost(admin, "/api/admin/users/" + id + "/deactivate", null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("NO_CHANGE"));
        doPost(admin, "/api/admin/users/" + id + "/activate", null).andExpect(jsonPath("$.data.status").value("ACTIVE"));
        login(email);
    }

    @Test
    void adminsCannotLockThemselvesOutOrRemoveTheLastAdmin() throws Exception {
        Client admin = newAdmin();
        long me = userId(admin);
        doPost(admin, "/api/admin/users/" + me + "/deactivate", null).andExpect(status().isForbidden());
        doPut(admin, "/api/admin/users/" + me + "/role", Map.of("role", "USER")).andExpect(status().isForbidden());

        long other = createUser(uniqueEmail("second"), Role.USER);
        doPut(admin, "/api/admin/users/" + other + "/role", Map.of("role", "ADMIN")).andExpect(jsonPath("$.data.role").value("ADMIN"));
        doPut(admin, "/api/admin/users/" + other + "/role", Map.of("role", "ADMIN")).andExpect(status().isConflict());
        doPut(admin, "/api/admin/users/" + other + "/role", Map.of("role", "SUPERUSER")).andExpect(status().isBadRequest());
        doPut(admin, "/api/admin/users/" + other + "/role", Map.of("role", "USER")).andExpect(jsonPath("$.data.role").value("USER"));
    }

    @Test
    void lastActiveAdminIsProtected() throws Exception {
        Client admin = newAdmin();
        long otherAdmin = createUser(uniqueEmail("admin2"), Role.ADMIN);
        Client second = login(db.query(c -> userDao.findById(c, otherAdmin)).orElseThrow().getEmail());
        doPost(second, "/api/admin/users/" + userId(admin) + "/deactivate", null).andExpect(status().isOk());
        // now "second" is the only active admin; the deactivated one cannot be used to remove them
        doPut(second, "/api/admin/users/" + userId(admin) + "/role", Map.of("role", "USER")).andExpect(status().isOk());
        assertThat(countRows("SELECT COUNT(*) FROM users WHERE role='ADMIN' AND status='ACTIVE'")).isEqualTo(1);
    }

    @Test
    void userBookingHistoryForAdmins() throws Exception {
        Client admin = newAdmin();
        Client member = newMember();
        long event = createUpcomingEvent("History", 10, "100");
        bookAndPay(member, event, 2);
        doGet(admin, "/api/admin/users/" + userId(member) + "/bookings").andExpect(jsonPath("$.data.totalItems").value(1));
        doGet(admin, "/api/admin/users?q=" + "member").andExpect(jsonPath("$.data.items[0].bookingCount").value(1))
                .andExpect(jsonPath("$.data.items[0].ticketsHeld").value(2));
    }
}
