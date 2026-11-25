package com.bookmatrix.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** With MySQL unreachable the app must still start and answer with a clear, safe 503. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:mysql://127.0.0.1:3999/nowhere?connectTimeout=500",
        "spring.datasource.hikari.connection-timeout=1000"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DatabaseUnavailableTest {

    @Autowired
    MockMvc mvc;

    @Test
    void healthReportsDatabaseDown() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.data.database").value("DOWN"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("3999"))));
    }

    @Test
    void apiCallsGetAClear503WithoutInternals() throws Exception {
        mvc.perform(get("/api/events").session(new MockHttpSession()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("DATABASE_UNAVAILABLE"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsStringIgnoringCase("jdbc"))));
    }

    @Test
    void staticPagesStillLoad() throws Exception {
        mvc.perform(get("/index.html")).andExpect(status().isOk());
    }
}
