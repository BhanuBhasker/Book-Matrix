package com.bookmatrix.integration;

import com.bookmatrix.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Booking creation, validation, history, ownership and cancellation. */
class BookingApiTest extends IntegrationTestBase {

    @Test
    void bookingReducesInventoryAndRecordsPrice() throws Exception {
        long id = createUpcomingEvent("Bookable", 50, "799.50");
        Client member = newMember();
        JsonNode res = body(doPost(member, "/api/bookings", Map.of("eventId", id, "quantity", 3, "requestKey", "req-abcdef-1"))
                .andExpect(status().isCreated()));
        String ref = res.at("/data/booking/reference").asText();
        assertThat(ref).matches("BM[A-Z0-9]{8}");
        assertThat(res.at("/data/booking/totalAmount").decimalValue()).isEqualByComparingTo("2398.50");
        assertThat(res.at("/data/booking/unitPrice").decimalValue()).isEqualByComparingTo("799.50");
        assertThat(res.at("/data/booking/status").asText()).as("a paid event starts as a hold").isEqualTo("PENDING_PAYMENT");
        assertThat(res.at("/data/booking/payable").asBoolean()).isTrue();
        assertThat(res.at("/data/booking/holdExpiresAt").isNull()).isFalse();
        assertThat(res.at("/data/booking/userEmail").isNull()).as("customer view does not echo admin fields").isTrue();
        assertThat(ticketsSold(id)).as("held tickets already count against inventory").isEqualTo(3);
        doPost(member, "/api/bookings/" + ref + "/pay", GOOD_CARD).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.booking.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.payment.methodLabel").value("Visa •••• 4242"));

        doGet(member, "/api/bookings").andExpect(jsonPath("$.data.items[0].reference").value(ref));
        doGet(member, "/api/bookings/" + ref).andExpect(status().isOk()).andExpect(jsonPath("$.data.cancellable").value(true));
        doGet(member, "/api/bookings/" + ref.toLowerCase()).andExpect(status().isOk());
        doGet(member, "/api/bookings/" + ref + "/receipt").andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(".txt")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(ref)));
    }

    @Test
    void priceChangesDoNotRewriteHistory() throws Exception {
        Client admin = newAdmin();
        long id = body(doPost(admin, "/api/admin/events", eventForm("Price Change", now().plusDays(9), 100, 30))).at("/data/id").asLong();
        Client member = newMember();
        String ref = bookAndPay(member, id, 2);
        doPut(admin, "/api/admin/events/" + id, eventForm("Price Change", now().plusDays(9), 250, 30)).andExpect(status().isOk());
        doGet(member, "/api/bookings/" + ref).andExpect(jsonPath("$.data.unitPrice").value(100.0))
                .andExpect(jsonPath("$.data.totalAmount").value(200.0));
    }

    @Test
    void invalidQuantitiesAndRequests() throws Exception {
        long id = createUpcomingEvent("Limits", 5, "100");
        Client member = newMember();
        doPost(member, "/api/bookings", Map.of("eventId", id, "quantity", 0)).andExpect(status().isBadRequest());
        doPost(member, "/api/bookings", Map.of("eventId", id, "quantity", -2)).andExpect(status().isBadRequest());
        doPost(member, "/api/bookings", Map.of("eventId", id, "quantity", 11)).andExpect(status().isBadRequest());
        doPost(member, "/api/bookings", Map.of("eventId", id)).andExpect(status().isBadRequest());
        doPost(member, "/api/bookings", Map.of("eventId", id, "quantity", "lots")).andExpect(status().isBadRequest());
        doPost(member, "/api/bookings", Map.of("eventId", id, "quantity", 1, "requestKey", "<bad key>")).andExpect(status().isBadRequest());
        doPost(member, "/api/bookings", Map.of("eventId", 424242, "quantity", 1)).andExpect(status().isNotFound());
        doPost(member, "/api/bookings", Map.of("eventId", id, "quantity", 6)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INSUFFICIENT_TICKETS"));
        assertThat(ticketsSold(id)).isZero();
    }

    @Test
    void soldOutStartedAndCompletedEventsCannotBeBooked() throws Exception {
        Client member = newMember();
        long small = createUpcomingEvent("Tiny", 2, "50");
        doPost(member, "/api/bookings", Map.of("eventId", small, "quantity", 2)).andExpect(status().isCreated());
        doPost(newMember(), "/api/bookings", Map.of("eventId", small, "quantity", 1)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value("Sorry, this event is sold out."));
        long started = createEvent("Already On", now().minusMinutes(30), 10, "50");
        doPost(member, "/api/bookings", Map.of("eventId", started, "quantity", 1)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value("This event has already started."));
        long finished = createEvent("Finished", now().minusDays(3), 10, "50");
        doPost(member, "/api/bookings", Map.of("eventId", finished, "quantity", 1)).andExpect(status().isConflict());
    }

    @Test
    void duplicateSubmissionReturnsTheSameBooking() throws Exception {
        long id = createUpcomingEvent("Double Click", 10, "100");
        Client member = newMember();
        Map<String, Object> req = Map.of("eventId", id, "quantity", 2, "requestKey", "same-key-12345");
        String first = body(doPost(member, "/api/bookings", req).andExpect(status().isCreated())).at("/data/booking/reference").asText();
        JsonNode second = body(doPost(member, "/api/bookings", req).andExpect(status().isOk()));
        assertThat(second.at("/data/duplicate").asBoolean()).isTrue();
        assertThat(second.at("/data/booking/reference").asText()).isEqualTo(first);
        assertThat(ticketsSold(id)).isEqualTo(2);
        assertThat(countRows("SELECT COUNT(*) FROM bookings WHERE event_id = ?", id)).isEqualTo(1);
    }

    @Test
    void usersOnlySeeTheirOwnBookings() throws Exception {
        long id = createUpcomingEvent("Private", 10, "100");
        Client owner = newMember();
        Client other = newMember();
        String ref = body(doPost(owner, "/api/bookings", Map.of("eventId", id, "quantity", 1))).at("/data/booking/reference").asText();
        doGet(other, "/api/bookings/" + ref).andExpect(status().isNotFound());
        doGet(other, "/api/bookings/" + ref + "/receipt").andExpect(status().isNotFound());
        doPost(other, "/api/bookings/" + ref + "/cancel", null).andExpect(status().isNotFound());
        doGet(other, "/api/bookings").andExpect(jsonPath("$.data.totalItems").value(0));
        doGet(owner, "/api/bookings/NOT-A-REF").andExpect(status().isNotFound());
        doGet(owner, "/api/bookings/BM22222222").andExpect(status().isNotFound());
        assertThat(ticketsSold(id)).isEqualTo(1);
    }

    @Test
    void cancellationRestoresInventoryExactlyOnce() throws Exception {
        long id = createUpcomingEvent("Cancel Me", 10, "200");
        Client member = newMember();
        String ref = bookAndPay(member, id, 4);
        assertThat(ticketsSold(id)).isEqualTo(4);
        doPost(member, "/api/bookings/" + ref + "/cancel", Map.of("reason", "Cannot attend")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.cancellationReason").value("Cannot attend"))
                .andExpect(jsonPath("$.data.cancelledAt").exists());
        assertThat(ticketsSold(id)).isZero();
        assertThat(countRows("SELECT COUNT(*) FROM payments p JOIN bookings b ON b.id = p.booking_id WHERE b.booking_ref = ? "
                + "AND p.kind = 'REFUND' AND p.amount = 800", ref)).as("paid booking is refunded once").isEqualTo(1);
        doPost(member, "/api/bookings/" + ref + "/cancel", null).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value("This booking is already cancelled."));
        assertThat(ticketsSold(id)).isZero();
        doGet(member, "/api/bookings?status=CANCELLED").andExpect(jsonPath("$.data.totalItems").value(1));
        doGet(member, "/api/bookings?status=UPCOMING").andExpect(jsonPath("$.data.totalItems").value(0));
    }

    @Test
    void cannotCancelInsideTheCutoffWindow() throws Exception {
        long soon = createEvent("Starts Soon", now().plusMinutes(90), 10, "100");
        Client member = newMember();
        String ref = bookAndPay(member, soon, 1);
        doGet(member, "/api/bookings/" + ref).andExpect(jsonPath("$.data.cancellable").value(false));
        doPost(member, "/api/bookings/" + ref + "/cancel", null).andExpect(status().isConflict());
        Client admin = newAdmin();
        doPost(admin, "/api/admin/bookings/" + ref + "/cancel", Map.of("reason", "Goodwill refund")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cancelledByName").exists());
        assertThat(ticketsSold(soon)).isZero();
    }

    @Test
    void bookingSearchAndPagination() throws Exception {
        long a = createUpcomingEvent("Alpha Night", 30, "10");
        long b = createUpcomingEvent("Beta Morning", 30, "10");
        Client member = newMember();
        for (int i = 0; i < 3; i++) {
            doPost(member, "/api/bookings", Map.of("eventId", a, "quantity", 1)).andExpect(status().isCreated());
        }
        doPost(member, "/api/bookings", Map.of("eventId", b, "quantity", 1)).andExpect(status().isCreated());
        doGet(member, "/api/bookings?q=beta").andExpect(jsonPath("$.data.totalItems").value(1));
        doGet(member, "/api/bookings?size=2&page=2").andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(2));
        Client admin = newAdmin();
        doGet(admin, "/api/admin/bookings?q=alpha").andExpect(jsonPath("$.data.totalItems").value(3))
                .andExpect(jsonPath("$.data.items[0].userEmail").exists());
        doGet(admin, "/api/admin/events/" + a + "/bookings").andExpect(jsonPath("$.data.totalItems").value(3));
    }
}
