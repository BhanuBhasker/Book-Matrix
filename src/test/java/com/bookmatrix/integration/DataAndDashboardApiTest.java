package com.bookmatrix.integration;

import com.bookmatrix.demo.JdbcDemo;
import com.bookmatrix.demo.DemoContext;
import com.bookmatrix.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** CSV import/export, backups, dashboard statistics, Java Lab and the JDBC demo. */
class DataAndDashboardApiTest extends IntegrationTestBase {

    @Value("${spring.datasource.url}") String dbUrl;
    @Value("${spring.datasource.username}") String dbUser;
    @Value("${spring.datasource.password}") String dbPassword;

    private static final String HEADER = "title,description,category,venue,city,address,starts_at,ends_at,price,capacity,image_url,featured\n";

    private JsonNode upload(Client admin, String filename, String content) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", filename, "text/csv", content.getBytes(StandardCharsets.UTF_8));
        return json.readTree(mvc.perform(multipart("/api/admin/data/import/events").file(file)
                .session(admin.session()).header("X-CSRF-Token", admin.csrf())).andReturn().getResponse().getContentAsString());
    }

    @Test
    void importValidatesEveryRow() throws Exception {
        Client admin = newAdmin();
        String start = now().plusDays(20).withNano(0).toString().substring(0, 16);
        String end = now().plusDays(20).plusHours(2).toString().substring(0, 16);
        String csv = HEADER
                + "\"Good, Show\",\"Line one\nLine \"\"two\"\"\",Music,Hall A,Pune,,"+ start + "," + end + ",500,100,,true\n"
                + "\"Good, Show\",dup row description,Music,Hall A,Pune,," + start + "," + end + ",500,100,,false\n"
                + "Bad Price,Description here,Music,Hall A,Pune,," + start + "," + end + ",cheap,100,,\n"
                + "Bad Category,Description here,Opera,Hall A,Pune,," + start + "," + end + ",10,100,,\n"
                + "Past,Description here,Music,Hall A,Pune,,2020-01-01T10:00,2020-01-01T12:00,10,100,,\n"
                + "Short,row\n";
        JsonNode res = upload(admin, "events.csv", csv);
        assertThat(res.at("/data/rowsRead").asInt()).isEqualTo(6);
        assertThat(res.at("/data/imported").asInt()).isEqualTo(1);
        assertThat(res.at("/data/errors").size()).isEqualTo(5);
        assertThat(res.at("/data/errors").toString()).contains("Duplicate", "Price must be a number", "Unknown category", "future", "Expected 12 columns");

        JsonNode again = upload(admin, "events.csv", HEADER + "\"Good, Show\",\"A long enough description\",Music,Hall A,Pune,," + start + "," + end + ",500,100,,true\n");
        assertThat(again.at("/data/imported").asInt()).isZero();
        assertThat(again.at("/data/errors/0/message").asText()).contains("already exists");

        doGet(anonymous(), "/api/events?q=Good").andExpect(jsonPath("$.data.items[0].title").value("Good, Show"))
                .andExpect(jsonPath("$.data.items[0].description").value("Line one\nLine \"two\""));
    }

    @Test
    void importCreatesSeatMapsForReservedEvents() throws Exception {
        Client admin = newAdmin();
        String start = now().plusDays(20).withNano(0).toString().substring(0, 16);
        String end = now().plusDays(20).plusHours(2).toString().substring(0, 16);
        String header = "title,description,category,venue,city,starts_at,ends_at,price,capacity,seating,seat_rows,seats_per_row\n";
        JsonNode res = upload(admin, "seated.csv", header
                + "Seated Play,A seated theatre evening,Theatre,Hall S,Pune," + start + "," + end + ",300,,RESERVED,2,3\n"
                + "Bad Layout,A seated theatre evening,Theatre,Hall S,Pune," + start + "," + end + ",300,,RESERVED,40,3\n"
                + "Standing,A standing gig for everyone,Music,Hall S,Pune," + start + "," + end + ",300,50,GENERAL,,\n");
        assertThat(res.at("/data/imported").asInt()).isEqualTo(2);
        assertThat(res.at("/data/errors/0/message").asText()).contains("Rows must be between 1 and 26");
        long seated = countRows("SELECT id FROM events WHERE title = 'Seated Play'");
        assertThat(countRows("SELECT COUNT(*) FROM event_seats WHERE event_id = ?", seated)).isEqualTo(6);
        assertThat(countRows("SELECT capacity FROM events WHERE id = ?", seated)).isEqualTo(6);
    }

    @Test
    void importRejectsBadFiles() throws Exception {
        Client admin = newAdmin();
        assertThat(upload(admin, "events.txt", HEADER).at("/error/fields/file").asText()).contains(".csv");
        assertThat(upload(admin, "events.csv", "").at("/error/fields/file").asText()).contains("empty");
        assertThat(upload(admin, "events.csv", "title,city\nA,B\n").at("/error/fields/file").asText()).contains("Missing required column");
        assertThat(upload(admin, "events.csv", HEADER + "\"unclosed,quote\n").at("/error/fields/file").asText()).contains("malformed");
        assertThat(upload(admin, "events.csv", HEADER).at("/error/fields/file").asText()).contains("only has a header");
    }

    @Test
    void exportsAreValidCsv() throws Exception {
        Client admin = newAdmin();
        long id = createUpcomingEvent("Export, \"Quoted\"", 10, "100");
        Client member = newMember();
        doPost(member, "/api/bookings", Map.of("eventId", id, "quantity", 2)).andExpect(status().isCreated());
        doGet(admin, "/api/admin/data/events.csv").andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"Export, \"\"Quoted\"\"\"")));
        doGet(admin, "/api/admin/data/bookings.csv").andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.startsWith("reference,status,booked_at")));
    }

    @Test
    void backupWritesFilesToDisk() throws Exception {
        Client admin = newAdmin();
        createUpcomingEvent("Backed Up", 10, "100");
        JsonNode res = body(doPost(admin, "/api/admin/data/backup", null).andExpect(status().isOk()));
        Path folder = Path.of(res.at("/data/folder").asText());
        assertThat(Files.exists(folder.resolve("events.csv"))).isTrue();
        assertThat(Files.readString(folder.resolve("users.csv"))).doesNotContain("$2a$");
        assertThat(Files.readString(folder.resolve("manifest.txt"))).contains("events.csv");
        doGet(admin, "/api/admin/data/backups").andExpect(jsonPath("$.data[0]").value(folder.getFileName().toString()));
    }

    @Test
    void dashboardReflectsRealData() throws Exception {
        Client admin = newAdmin();
        doGet(admin, "/api/admin/dashboard").andExpect(jsonPath("$.data.counts.total_bookings").value(0))
                .andExpect(jsonPath("$.data.revenue").value(0));
        long id = createUpcomingEvent("Dash", 10, "250");
        Client member = newMember();
        String ref = bookAndPay(member, id, 2);
        bookAndPay(member, id, 1);
        doPost(member, "/api/bookings/" + ref + "/cancel", null).andExpect(status().isOk());
        doPost(member, "/api/bookings/" + book(member, id, 1) + "/pay", card("4000 0000 0000 0002"))   // one declined attempt
                .andExpect(status().isPaymentRequired());
        JsonNode d = body(doGet(admin, "/api/admin/dashboard")).at("/data");
        assertThat(d.at("/counts/total_bookings").asLong()).isEqualTo(3);
        assertThat(d.at("/counts/pending_bookings").asLong()).isEqualTo(1);
        assertThat(d.at("/counts/held_tickets").asLong()).isEqualTo(1);
        assertThat(d.at("/counts/successful_payments").asLong()).isEqualTo(2);
        assertThat(d.at("/counts/failed_payments").asLong()).isEqualTo(1);
        assertThat(d.at("/counts/refunds").asLong()).isEqualTo(1);
        assertThat(d.at("/counts/confirmed_bookings").asLong()).isEqualTo(1);
        assertThat(d.at("/counts/cancelled_bookings").asLong()).isEqualTo(1);
        assertThat(d.at("/counts/tickets_sold").asLong()).isEqualTo(1);
        assertThat(d.at("/counts/available_inventory").asLong()).as("10 - 1 sold - 1 held").isEqualTo(8);
        assertThat(d.at("/revenue").decimalValue()).isEqualByComparingTo("250");
        assertThat(d.at("/refunded").decimalValue()).isEqualByComparingTo("500");
        assertThat(d.at("/inconsistentEvents").asInt()).isZero();
        assertThat(d.at("/topEvents/0/title").asText()).isEqualTo("Dash");
        doGet(admin, "/api/admin/inventory").andExpect(jsonPath("$.data[0].consistent").value(true))
                .andExpect(jsonPath("$.data[0].confirmedTickets").value(1));
        doGet(admin, "/api/admin/activity?limit=5").andExpect(jsonPath("$.data.length()").value(5));
    }

    @Test
    void javaLabRunsDemosFromTheBrowser() throws Exception {
        Client admin = newAdmin();
        doGet(admin, "/api/admin/demos").andExpect(jsonPath("$.data.length()").value(12));
        doPost(admin, "/api/admin/demos/collections/run", null).andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.output").value(org.hamcrest.Matchers.containsString("PriorityQueue")));
        doPost(admin, "/api/admin/demos/nope/run", null).andExpect(status().isNotFound());
    }

    @Test
    void jdbcDemoRunsAgainstMySql() throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        new JdbcDemo().run(new DemoContext(new PrintStream(buffer, true, StandardCharsets.UTF_8), dbUrl, dbUser, dbPassword));
        String out = buffer.toString(StandardCharsets.UTF_8);
        assertThat(out).contains("executeUpdate() returned 1", "rows matched: 0", "after rollback=4", "Connection closed");
    }
}
