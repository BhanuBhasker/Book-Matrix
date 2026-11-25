package com.bookmatrix.integration;

import com.bookmatrix.support.IntegrationTestBase;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Role-based access control is enforced on the server for every protected area. */
class PermissionsTest extends IntegrationTestBase {

    private static final List<String> ADMIN_GETS = List.of("/api/admin/dashboard", "/api/admin/events", "/api/admin/users",
            "/api/admin/bookings", "/api/admin/inventory", "/api/admin/activity", "/api/admin/data/events.csv",
            "/api/admin/demos", "/api/admin/payments", "/api/admin/events/1/bookings");

    @Test
    void anonymousUsersGet401OnProtectedApis() throws Exception {
        Client anon = anonymous();
        for (String url : ADMIN_GETS) {
            doGet(anon, url).andExpect(status().isUnauthorized());
        }
        doGet(anon, "/api/bookings").andExpect(status().isUnauthorized());
        doGet(anon, "/api/me").andExpect(status().isUnauthorized());
        doPost(anon, "/api/bookings", Map.of("eventId", 1, "quantity", 1)).andExpect(status().isUnauthorized());
    }

    @Test
    void normalUsersGet403OnEveryAdminApi() throws Exception {
        Client member = newMember();
        long event = createUpcomingEvent("Locked", 10, "100");
        for (String url : ADMIN_GETS) {
            doGet(member, url).andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
        }
        doPost(member, "/api/admin/events", eventForm("Hack", now().plusDays(3), 10, 10)).andExpect(status().isForbidden());
        doPut(member, "/api/admin/events/" + event, eventForm("Hack", now().plusDays(3), 10, 10)).andExpect(status().isForbidden());
        doDelete(member, "/api/admin/events/" + event, null).andExpect(status().isForbidden());
        doPost(member, "/api/admin/events/" + event + "/cancel", null).andExpect(status().isForbidden());
        doPut(member, "/api/admin/users/" + userId(member) + "/role", Map.of("role", "ADMIN")).andExpect(status().isForbidden());
        doPost(member, "/api/admin/data/backup", null).andExpect(status().isForbidden());
    }

    @Test
    void usersCannotPromoteThemselvesThroughTheProfile() throws Exception {
        Client member = newMember();
        doPatch(member, "/api/me", Map.of("fullName", "Sneaky Person", "role", "ADMIN", "status", "ACTIVE", "email", "x@x.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.fullName").value("Sneaky Person"));
    }

    @Test
    void adminsCanUseAdminApis() throws Exception {
        Client admin = newAdmin();
        for (String url : ADMIN_GETS.subList(0, 6)) {
            doGet(admin, url).andExpect(status().isOk());
        }
    }

    @Test
    void protectedPagesRedirect() throws Exception {
        mvc.perform(get("/admin/index.html")).andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/login.html?next=")));
        mvc.perform(get("/bookings.html")).andExpect(status().is3xxRedirection());
        Client member = newMember();
        mvc.perform(get("/admin/users.html").session(member.session())).andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/?denied=admin"));
        mvc.perform(get("/profile.html").session(member.session())).andExpect(status().isOk());
        Client admin = newAdmin();
        mvc.perform(get("/admin/index.html").session(admin.session())).andExpect(status().isOk());
        mvc.perform(get("/index.html")).andExpect(status().isOk());
    }

    @Test
    void unknownPathsReturn404() throws Exception {
        doGet(anonymous(), "/api/does-not-exist").andExpect(status().isNotFound()).andExpect(jsonPath("$.success").value(false));
        mvc.perform(get("/no-such-page.html")).andExpect(status().isNotFound());
    }
}
