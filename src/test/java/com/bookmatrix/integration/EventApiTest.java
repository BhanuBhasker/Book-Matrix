package com.bookmatrix.integration;

import com.bookmatrix.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Public discovery plus admin event CRUD and status rules. */
class EventApiTest extends IntegrationTestBase {

    @Test
    void emptyListingWorks() throws Exception {
        doGet(anonymous(), "/api/events").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems").value(0)).andExpect(jsonPath("$.data.items").isEmpty());
        doGet(anonymous(), "/api/home").andExpect(status().isOk()).andExpect(jsonPath("$.data.categories.length()").value(8));
    }

    @Test
    void searchFilterSortAndPaginate() throws Exception {
        long cheap = createEvent("Acoustic Evening", now().plusDays(2), 50, "200");
        long pricey = createEvent("Opera Gala", now().plusDays(5), 50, "3000");
        createEvent("Past Show", now().minusDays(5), 50, "500");
        Client anon = anonymous();

        doGet(anon, "/api/events").andExpect(jsonPath("$.data.totalItems").value(2));                       // upcoming only
        doGet(anon, "/api/events?status=COMPLETED").andExpect(jsonPath("$.data.items[0].title").value("Past Show"));
        doGet(anon, "/api/events?q=opera").andExpect(jsonPath("$.data.items[0].id").value(pricey));
        doGet(anon, "/api/events?q=Hall").andExpect(jsonPath("$.data.totalItems").value(2));          // venue search
        doGet(anon, "/api/events?city=Pune&maxPrice=1000").andExpect(jsonPath("$.data.items[0].id").value(cheap))
                .andExpect(jsonPath("$.data.totalItems").value(1));
        doGet(anon, "/api/events?sort=PRICE_DESC").andExpect(jsonPath("$.data.items[0].id").value(pricey));
        doGet(anon, "/api/events?from=" + now().plusDays(4).toLocalDate()).andExpect(jsonPath("$.data.totalItems").value(1));
        doGet(anon, "/api/events?category=1").andExpect(jsonPath("$.data.totalItems").value(2));
        doGet(anon, "/api/events?category=2").andExpect(jsonPath("$.data.totalItems").value(0));
        doGet(anon, "/api/events?size=1&page=2").andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.totalPages").value(2));
    }

    @Test
    void hostileOrInvalidSearchInputIsHandled() throws Exception {
        createUpcomingEvent("Safe Event", 10, "100");
        Client anon = anonymous();
        doGet(anon, "/api/events?q=' OR '1'='1' -- ").andExpect(status().isOk()).andExpect(jsonPath("$.data.totalItems").value(0));
        doGet(anon, "/api/events?q=%25").andExpect(status().isOk()).andExpect(jsonPath("$.data.totalItems").value(0));   // LIKE wildcard escaped
        doGet(anon, "/api/events?q=" + "x".repeat(5000)).andExpect(status().isOk());
        doGet(anon, "/api/events?unexpected=1&sort=DROP_TABLE&status=nonsense").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems").value(1));
        doGet(anon, "/api/events?minPrice=abc").andExpect(status().isBadRequest());
        doGet(anon, "/api/events?minPrice=-5").andExpect(status().isBadRequest());
        doGet(anon, "/api/events?from=31-12-2026").andExpect(status().isBadRequest());
        doGet(anon, "/api/events?from=2027-02-01&to=2027-01-01").andExpect(status().isBadRequest());
        doGet(anon, "/api/events?category=music").andExpect(status().isBadRequest());
        doGet(anon, "/api/events/abc").andExpect(status().isBadRequest());
        doGet(anon, "/api/events/999999").andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("EVENT_NOT_FOUND"));
    }

    @Test
    void eventDetailShowsAvailabilityAndEligibility() throws Exception {
        long id = createUpcomingEvent("Detail Show", 40, "250");
        doGet(anonymous(), "/api/events/" + id).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.availableTickets").value(40))
                .andExpect(jsonPath("$.data.displayStatus").value("UPCOMING"))
                .andExpect(jsonPath("$.data.bookable").value(true))
                .andExpect(jsonPath("$.data.maxPerBooking").value(10));
        long past = createEvent("Old", now().minusDays(2), 10, "10");
        doGet(anonymous(), "/api/events/" + past).andExpect(jsonPath("$.data.bookable").value(false))
                .andExpect(jsonPath("$.data.displayStatus").value("COMPLETED"));
    }

    @Test
    void adminCreatesUpdatesAndValidatesEvents() throws Exception {
        Client admin = newAdmin();
        JsonNode created = body(doPost(admin, "/api/admin/events", eventForm("New Gig", now().plusDays(7), 499.5, 120))
                .andExpect(status().isCreated()));
        long id = created.at("/data/id").asLong();
        assertThat(created.at("/data/venueName").asText()).isEqualTo("Blue Frog");
        assertThat(created.at("/data/ticketPrice").decimalValue()).isEqualByComparingTo("499.50");

        Map<String, Object> update = eventForm("New Gig (Extended)", now().plusDays(8), 550, 150);
        doPut(admin, "/api/admin/events/" + id, update).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("New Gig (Extended)")).andExpect(jsonPath("$.data.capacity").value(150));

        doPost(admin, "/api/admin/events", eventForm("New Gig (Extended)", now().plusDays(8), 550, 150))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("DUPLICATE_EVENT"));

        JsonNode invalid = body(doPost(admin, "/api/admin/events", eventForm("", now().minusDays(1), -5, 0)).andExpect(status().isBadRequest()));
        assertThat(invalid.at("/error/fields").fieldNames()).toIterable().contains("title", "ticketPrice", "capacity", "startsAt");
        Map<String, Object> badDates = eventForm("Bad Dates", now().plusDays(3), 10, 10);
        badDates.put("endsAt", now().plusDays(2).toString());
        doPost(admin, "/api/admin/events", badDates).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.endsAt").exists());
        Map<String, Object> badText = eventForm("Bad Date Text", null, 10, 10);
        badText.put("startsAt", "next friday");
        doPost(admin, "/api/admin/events", badText).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.startsAt").exists());
        Map<String, Object> badCategory = eventForm("Bad Category", now().plusDays(3), 10, 10);
        badCategory.put("categoryId", 9999);
        doPost(admin, "/api/admin/events", badCategory).andExpect(status().isBadRequest());
        Map<String, Object> badImage = eventForm("Bad Image", now().plusDays(3), 10, 10);
        badImage.put("imageUrl", "javascript:alert(1)");
        doPost(admin, "/api/admin/events", badImage).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.imageUrl").exists());
        doPut(admin, "/api/admin/events/987654", update).andExpect(status().isNotFound());
    }

    @Test
    void capacityCannotDropBelowTicketsSold() throws Exception {
        Client admin = newAdmin();
        long id = body(doPost(admin, "/api/admin/events", eventForm("Cap Test", now().plusDays(7), 100, 10))).at("/data/id").asLong();
        Client member = newMember();
        doPost(member, "/api/bookings", Map.of("eventId", id, "quantity", 6)).andExpect(status().isCreated());
        Map<String, Object> shrink = eventForm("Cap Test", now().plusDays(7), 100, 5);
        doPut(admin, "/api/admin/events/" + id, shrink).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CAPACITY_BELOW_SOLD"));
        doPut(admin, "/api/admin/events/" + id, eventForm("Cap Test", now().plusDays(7), 100, 6)).andExpect(status().isOk());
        assertThat(ticketsSold(id)).isEqualTo(6);
    }

    @Test
    void cancellingAnEventRefundsAllBookingsAndResetsInventory() throws Exception {
        Client admin = newAdmin();
        long id = createUpcomingEvent("To Cancel", 20, "300");
        Client a = newMember();
        Client b = newMember();
        bookAndPay(a, id, 3);                                   // paid -> must be refunded
        book(b, id, 2);                                         // only held -> just released
        assertThat(ticketsSold(id)).isEqualTo(5);

        doPost(admin, "/api/admin/events/" + id + "/cancel", Map.of("reason", "Artist unwell")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bookingsCancelled").value(2))
                .andExpect(jsonPath("$.data.event.displayStatus").value("CANCELLED"));
        assertThat(ticketsSold(id)).isZero();
        assertThat(countRows("SELECT COUNT(*) FROM bookings WHERE event_id = ? AND status IN ('CONFIRMED','PENDING_PAYMENT')", id)).isZero();
        assertThat(countRows("SELECT COUNT(*) FROM payments p JOIN bookings bk ON bk.id = p.booking_id WHERE bk.event_id = ? AND p.kind = 'REFUND'", id))
                .as("only the paid booking gets a refund").isEqualTo(1);
        doPost(admin, "/api/admin/events/" + id + "/cancel", null).andExpect(status().isConflict());
        doPost(a, "/api/bookings", Map.of("eventId", id, "quantity", 1)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value("This event has been cancelled."));
        doPut(admin, "/api/admin/events/" + id, eventForm("To Cancel", now().plusDays(10), 300, 20))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("EVENT_LOCKED"));
    }

    @Test
    void deleteArchivesWhenThereIsHistory() throws Exception {
        Client admin = newAdmin();
        long empty = createUpcomingEvent("Nobody Came", 10, "100");
        doDelete(admin, "/api/admin/events/" + empty, null).andExpect(jsonPath("$.data.outcome").value("DELETED"));
        doGet(admin, "/api/admin/events/" + empty).andExpect(status().isNotFound());

        long booked = createUpcomingEvent("Has Bookings", 10, "100");
        Client member = newMember();
        doPost(member, "/api/bookings", Map.of("eventId", booked, "quantity", 1)).andExpect(status().isCreated());
        doDelete(admin, "/api/admin/events/" + booked, null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("HAS_ACTIVE_BOOKINGS"));
        doPost(admin, "/api/admin/events/" + booked + "/cancel", null).andExpect(status().isOk());
        doDelete(admin, "/api/admin/events/" + booked, null).andExpect(jsonPath("$.data.outcome").value("ARCHIVED"));
        doGet(anonymous(), "/api/events/" + booked).andExpect(status().isNotFound());                // hidden from public
        doGet(member, "/api/bookings?status=ALL").andExpect(jsonPath("$.data.totalItems").value(1));   // history kept
        doGet(admin, "/api/admin/events?archived=ONLY").andExpect(jsonPath("$.data.totalItems").value(1));
        doPost(admin, "/api/admin/events/" + booked + "/restore", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.archived").value(false));
    }

    @Test
    void featuredToggleShowsOnHomePage() throws Exception {
        Client admin = newAdmin();
        long id = createUpcomingEvent("Star Show", 10, "100");
        doPatch(admin, "/api/admin/events/" + id + "/featured", Map.of("featured", true)).andExpect(jsonPath("$.data.featured").value(true));
        doGet(anonymous(), "/api/home").andExpect(jsonPath("$.data.featured[0].id").value(id));
        doGet(anonymous(), "/api/events?featured=true").andExpect(jsonPath("$.data.totalItems").value(1));
    }

    @Test
    void adminCanAddCategories() throws Exception {
        Client admin = newAdmin();
        doPost(admin, "/api/admin/categories", Map.of("name", "Food & Drink")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.slug").value("food-drink"));
        doPost(admin, "/api/admin/categories", Map.of("name", "Food & Drink")).andExpect(status().isConflict());
    }
}
