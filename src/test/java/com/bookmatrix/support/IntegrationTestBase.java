package com.bookmatrix.support;

import com.bookmatrix.dao.CategoryDao;
import com.bookmatrix.dao.EventDao;
import com.bookmatrix.dao.UserDao;
import com.bookmatrix.dao.VenueDao;
import com.bookmatrix.db.Database;
import com.bookmatrix.model.Event;
import com.bookmatrix.model.Role;
import com.bookmatrix.model.User;
import com.bookmatrix.model.Venue;
import com.bookmatrix.security.PasswordHasher;
import com.bookmatrix.security.SessionKeys;
import com.bookmatrix.security.SessionUser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * Shared setup for integration tests: a real Spring context, real MySQL (bookmatrix_test),
 * MockMvc for HTTP calls, and helpers to create users/events and log in.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    public static final String PASSWORD = "Passw0rd-test";
    private static final AtomicInteger SEQ = new AtomicInteger();

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired protected DataSource dataSource;
    @Autowired protected Database db;
    @Autowired protected UserDao userDao;
    @Autowired protected EventDao eventDao;
    @Autowired protected VenueDao venueDao;
    @Autowired protected CategoryDao categoryDao;
    @Autowired protected PasswordHasher hasher;

    /** A browser-like client: its own session cookie and CSRF token. */
    public record Client(MockHttpSession session, String csrf) { }

    @BeforeEach
    void cleanDatabase() throws Exception {
        try (Connection conn = dataSource.getConnection(); Statement st = conn.createStatement()) {
            String catalog = conn.getCatalog();
            assertThat(catalog).as("tests must only ever run against a *_test database").endsWith("_test");
            st.execute("DELETE FROM audit_log");
            st.execute("DELETE FROM payments");
            st.execute("DELETE FROM event_seats");
            st.execute("DELETE FROM bookings");
            st.execute("DELETE FROM events");
            st.execute("DELETE FROM venues");
            st.execute("DELETE FROM users");
            st.execute("DELETE FROM categories WHERE id > 8");
        }
    }

    protected static String uniqueEmail(String prefix) {
        return prefix + "-" + SEQ.incrementAndGet() + "-" + System.nanoTime() % 100000 + "@test.local";
    }

    protected long createUser(String email, Role role) {
        User u = new User("Test " + role.name().charAt(0) + role.name().substring(1).toLowerCase(), email, hasher.hash(PASSWORD));
        u.setRole(role);
        return db.query(conn -> userDao.insert(conn, u, now()));
    }

    protected SessionUser sessionUser(long id) {
        return db.query(conn -> userDao.findById(conn, id)).map(SessionUser::from).orElseThrow();
    }

    protected static LocalDateTime now() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    }

    // ------------------------------------------------------------------ HTTP helpers

    protected Client anonymous() throws Exception {
        MockHttpSession session = new MockHttpSession();
        MvcResult r = mvc.perform(get("/api/auth/session").session(session)).andReturn();
        String token = json.readTree(r.getResponse().getContentAsString()).at("/data/csrfToken").asText();
        return new Client(session, token);
    }

    protected Client login(String email) throws Exception {
        return login(email, PASSWORD);
    }

    protected Client login(String email, String password) throws Exception {
        Client anon = anonymous();
        MvcResult r = mvc.perform(withCsrf(post("/api/auth/login"), anon)
                .content(json.writeValueAsString(Map.of("email", email, "password", password)))).andReturn();
        assertThat(r.getResponse().getStatus()).as("login of " + email + ": " + r.getResponse().getContentAsString()).isEqualTo(200);
        MockHttpSession newSession = (MockHttpSession) r.getRequest().getSession(false);
        String token = json.readTree(r.getResponse().getContentAsString()).at("/data/csrfToken").asText();
        assertThat(newSession.getAttribute(SessionKeys.USER_ID)).isNotNull();
        return new Client(newSession, token);
    }

    protected Client newAdmin() throws Exception {
        String email = uniqueEmail("admin");
        createUser(email, Role.ADMIN);
        return login(email);
    }

    protected Client newMember() throws Exception {
        String email = uniqueEmail("member");
        createUser(email, Role.USER);
        return login(email);
    }

    protected long userId(Client c) {
        return (Long) c.session().getAttribute(SessionKeys.USER_ID);
    }

    private MockHttpServletRequestBuilder withCsrf(MockHttpServletRequestBuilder b, Client c) {
        return b.session(c.session()).header(SessionKeys.CSRF_HEADER, c.csrf()).contentType(MediaType.APPLICATION_JSON);
    }

    protected ResultActions doGet(Client c, String url) throws Exception {
        return mvc.perform(get(url).session(c.session()));
    }

    protected ResultActions doPost(Client c, String url, Object body) throws Exception {
        MockHttpServletRequestBuilder b = withCsrf(post(url), c);
        return mvc.perform(body == null ? b : b.content(json.writeValueAsString(body)));
    }

    protected ResultActions doPut(Client c, String url, Object body) throws Exception {
        return mvc.perform(withCsrf(put(url), c).content(json.writeValueAsString(body)));
    }

    protected ResultActions doPatch(Client c, String url, Object body) throws Exception {
        return mvc.perform(withCsrf(patch(url), c).content(json.writeValueAsString(body)));
    }

    protected ResultActions doDelete(Client c, String url, Object body) throws Exception {
        MockHttpServletRequestBuilder b = withCsrf(delete(url), c);
        return mvc.perform(body == null ? b : b.content(json.writeValueAsString(body)));
    }

    protected JsonNode body(ResultActions actions) throws Exception {
        return json.readTree(actions.andReturn().getResponse().getContentAsString());
    }

    // ------------------------------------------------------------------ data helpers

    /** Inserts an event straight into MySQL (lets tests create past/started events that the API would reject). */
    protected long createEvent(String title, LocalDateTime startsAt, int capacity, String price) {
        return db.inTransaction(conn -> {
            Venue venue = venueDao.findOrCreate(conn, "Test Hall", "Pune", null, now());
            Event e = new Event();
            e.setTitle(title);
            e.setDescription("Integration test event description");
            e.setCategoryId(categoryDao.findByName(conn, "Music").orElseThrow().id());
            e.setVenueId(venue.id());
            e.setStartsAt(startsAt);
            e.setEndsAt(startsAt.plusHours(3));
            e.setTicketPrice(new BigDecimal(price));
            e.setCapacity(capacity);
            return eventDao.insert(conn, e, firstUserIdOrNull(conn), now());
        });
    }

    protected long createUpcomingEvent(String title, int capacity, String price) {
        return createEvent(title, now().plusDays(10), capacity, price);
    }

    private long firstUserIdOrNull(Connection conn) throws java.sql.SQLException {
        try (Statement st = conn.createStatement(); var rs = st.executeQuery("SELECT MIN(id) FROM users")) {
            rs.next();
            long id = rs.getLong(1);
            if (rs.wasNull()) {
                // events.created_by is nullable; create a throwaway admin so the FK is satisfied
                User u = new User("Event Owner", uniqueEmail("owner"), hasher.hash(PASSWORD));
                u.setRole(Role.ADMIN);
                return userDao.insert(conn, u, now());
            }
            return id;
        }
    }

    /** Demo-gateway test card that is always approved. */
    protected static final Map<String, Object> GOOD_CARD = Map.of("method", "CARD", "cardNumber", "4242 4242 4242 4242",
            "cardName", "Test Person", "expiry", "12/" + (now().getYear() % 100 + 3), "cvv", "123");

    protected static Map<String, Object> card(String number) {
        Map<String, Object> m = new java.util.HashMap<>(GOOD_CARD);
        m.put("cardNumber", number);
        return m;
    }

    /** Books (creates the hold) and returns the booking reference. */
    protected String book(Client c, long eventId, int quantity) throws Exception {
        ResultActions r = doPost(c, "/api/bookings", Map.of("eventId", eventId, "quantity", quantity));
        assertThat(r.andReturn().getResponse().getStatus()).as(r.andReturn().getResponse().getContentAsString()).isEqualTo(201);
        return body(r).at("/data/booking/reference").asText();
    }

    /** Books and pays with the approved test card: the booking ends up CONFIRMED. */
    protected String bookAndPay(Client c, long eventId, int quantity) throws Exception {
        String ref = book(c, eventId, quantity);
        ResultActions paid = doPost(c, "/api/bookings/" + ref + "/pay", GOOD_CARD);
        assertThat(paid.andReturn().getResponse().getStatus()).as(paid.andReturn().getResponse().getContentAsString()).isEqualTo(200);
        return ref;
    }

    protected int ticketsSold(long eventId) {
        return db.query(conn -> eventDao.findById(conn, eventId)).orElseThrow().getTicketsSold();
    }

    protected long countRows(String sql, Object... params) {
        return db.query(conn -> {
            try (var ps = conn.prepareStatement(sql)) {
                for (int i = 0; i < params.length; i++) {
                    ps.setObject(i + 1, params[i]);
                }
                try (var rs = ps.executeQuery()) {
                    rs.next();
                    return rs.getLong(1);
                }
            }
        });
    }

    protected Map<String, Object> eventForm(String title, LocalDateTime start, Object price, Object capacity) {
        Map<String, Object> m = new java.util.HashMap<>();
        m.put("title", title);
        m.put("description", "A perfectly fine description for an event.");
        m.put("categoryId", 1);
        m.put("venueName", "Blue Frog");
        m.put("city", "Pune");
        m.put("venueAddress", "Koregaon Park");
        m.put("startsAt", start == null ? null : start.toString());
        m.put("endsAt", start == null ? null : start.plusHours(2).toString());
        m.put("ticketPrice", price);
        m.put("capacity", capacity);
        m.put("featured", false);
        return m;
    }
}
