package com.bookmatrix.integration;

import com.bookmatrix.dto.Requests;
import com.bookmatrix.exception.BookMatrixException;
import com.bookmatrix.model.Role;
import com.bookmatrix.security.SessionUser;
import com.bookmatrix.service.BookingService;
import com.bookmatrix.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Demo payment gateway + reserved seat selection, end to end through the API and MySQL. */
class PaymentAndSeatApiTest extends IntegrationTestBase {

    @Autowired
    BookingService bookingService;

    // ================================================================== payments

    @Test
    void declinedPaymentKeepsTheHoldThenAGoodCardConfirms() throws Exception {
        long id = createUpcomingEvent("Pay Show", 10, "300");
        Client member = newMember();
        String ref = book(member, id, 2);
        doPost(member, "/api/bookings/" + ref + "/pay", card("4000 0000 0000 0002"))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.error.code").value("PAYMENT_DECLINED"));
        doGet(member, "/api/bookings/" + ref).andExpect(jsonPath("$.data.status").value("PENDING_PAYMENT"))
                .andExpect(jsonPath("$.data.payments[0].status").value("FAILED"));
        assertThat(ticketsSold(id)).as("still held after a decline").isEqualTo(2);

        JsonNode ok = body(doPost(member, "/api/bookings/" + ref + "/pay", GOOD_CARD).andExpect(status().isOk()));
        assertThat(ok.at("/data/booking/status").asText()).isEqualTo("CONFIRMED");
        assertThat(ok.at("/data/booking/paidAt").isNull()).isFalse();
        assertThat(ok.at("/data/payment/amount").decimalValue()).isEqualByComparingTo("600");
        assertThat(ok.at("/data/booking/payments").size()).isEqualTo(2);

        doPost(member, "/api/bookings/" + ref + "/pay", GOOD_CARD).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ALREADY_PAID"));
        assertThat(countRows("SELECT COUNT(*) FROM payments p JOIN bookings b ON b.id = p.booking_id "
                + "WHERE b.booking_ref = ? AND p.kind = 'CHARGE' AND p.status = 'SUCCEEDED'", ref)).isEqualTo(1);
    }

    @Test
    void invalidPaymentDetailsAreRejectedAndNothingIsRecorded() throws Exception {
        long id = createUpcomingEvent("Bad Cards", 10, "100");
        Client member = newMember();
        String ref = book(member, id, 1);
        String url = "/api/bookings/" + ref + "/pay";
        doPost(member, url, card("4242 4242 4242 4241")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.cardNumber").exists());
        Map<String, Object> expired = card("4242424242424242");
        expired.put("expiry", "01/20");
        doPost(member, url, expired).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.expiry").exists());
        Map<String, Object> shortCvv = card("4242424242424242");
        shortCvv.put("cvv", "1");
        doPost(member, url, shortCvv).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields.cvv").exists());
        doPost(member, url, Map.of("method", "UPI", "upiId", "no-at-sign")).andExpect(status().isBadRequest());
        doPost(member, url, Map.of("method", "BITCOIN")).andExpect(status().isBadRequest());
        assertThat(countRows("SELECT COUNT(*) FROM payments")).isZero();
    }

    @Test
    void upiPayments() throws Exception {
        long id = createUpcomingEvent("UPI Show", 10, "150");
        Client member = newMember();
        String ref = book(member, id, 1);
        doPost(member, "/api/bookings/" + ref + "/pay", Map.of("method", "UPI", "upiId", "fail@upi"))
                .andExpect(status().isPaymentRequired());
        doPost(member, "/api/bookings/" + ref + "/pay", Map.of("method", "UPI", "upiId", "asha@okbank"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.payment.methodLabel").value("UPI asha@okbank"));
    }

    @Test
    void cardNumbersAndCvvAreNeverStored() throws Exception {
        long id = createUpcomingEvent("Privacy", 10, "100");
        Client member = newMember();
        String ref = bookAndPay(member, id, 1);
        long leaks = countRows("SELECT COUNT(*) FROM payments WHERE CONCAT_WS('|', card_brand, card_last4, upi_handle, "
                + "gateway_ref, failure_reason) LIKE '%4242424242%'");
        assertThat(leaks).isZero();
        assertThat(countRows("SELECT COUNT(*) FROM audit_log WHERE details LIKE '%4242 4242%' OR details LIKE '%123%'")).isZero();
        doGet(member, "/api/bookings/" + ref + "/receipt")
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Visa •••• 4242")));
    }

    @Test
    void onlyTheOwnerCanPay() throws Exception {
        long id = createUpcomingEvent("Owner Only", 10, "100");
        String ref = book(newMember(), id, 1);
        doPost(newMember(), "/api/bookings/" + ref + "/pay", GOOD_CARD).andExpect(status().isNotFound());
        doPost(anonymous(), "/api/bookings/" + ref + "/pay", GOOD_CARD).andExpect(status().isUnauthorized());
    }

    @Test
    void unpaidHoldsExpireAndReleaseTickets() throws Exception {
        long id = createUpcomingEvent("Expiring", 5, "100");
        Client member = newMember();
        String a = book(member, id, 2);
        String b = book(member, id, 1);
        assertThat(ticketsSold(id)).isEqualTo(3);
        db.query(conn -> conn.createStatement().executeUpdate(
                "UPDATE bookings SET hold_expires_at = NOW() - INTERVAL 1 MINUTE WHERE event_id = " + id));

        doPost(member, "/api/bookings/" + a + "/pay", GOOD_CARD).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("expired")));
        doGet(member, "/api/bookings/" + a).andExpect(jsonPath("$.data.status").value("EXPIRED"));
        assertThat(bookingService.expireStaleHolds()).as("the background job releases the other one").isEqualTo(1);
        doGet(member, "/api/bookings/" + b).andExpect(jsonPath("$.data.status").value("EXPIRED"));
        assertThat(ticketsSold(id)).isZero();
        assertThat(bookingService.expireStaleHolds()).as("released only once").isZero();
        doPost(member, "/api/bookings/" + b + "/cancel", null).andExpect(status().isConflict());
    }

    @Test
    void freeEventsConfirmWithoutPayment() throws Exception {
        long id = createUpcomingEvent("Free Talk", 10, "0");
        Client member = newMember();
        JsonNode res = body(doPost(member, "/api/bookings", Map.of("eventId", id, "quantity", 2)).andExpect(status().isCreated()));
        assertThat(res.at("/data/booking/status").asText()).isEqualTo("CONFIRMED");
        String ref = res.at("/data/booking/reference").asText();
        doPost(member, "/api/bookings/" + ref + "/pay", GOOD_CARD).andExpect(status().isConflict());
        assertThat(countRows("SELECT COUNT(*) FROM payments")).isZero();
    }

    @Test
    void releasingAnUnpaidHoldChargesAndRefundsNothing() throws Exception {
        long id = createUpcomingEvent("Changed Mind", 10, "100");
        Client member = newMember();
        String ref = book(member, id, 3);
        doPost(member, "/api/bookings/" + ref + "/cancel", null).andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Hold released. Nothing was charged."));
        assertThat(ticketsSold(id)).isZero();
        assertThat(countRows("SELECT COUNT(*) FROM payments")).isZero();
    }

    @Test
    void adminSeesTheTransactionLog() throws Exception {
        long id = createUpcomingEvent("Ledger", 10, "100");
        Client member = newMember();
        String ref = book(member, id, 1);
        doPost(member, "/api/bookings/" + ref + "/pay", card("4000000000009995")).andExpect(status().isPaymentRequired());
        doPost(member, "/api/bookings/" + ref + "/pay", GOOD_CARD).andExpect(status().isOk());
        Client admin = newAdmin();
        doGet(admin, "/api/admin/payments").andExpect(jsonPath("$.data.totalItems").value(2));
        doGet(admin, "/api/admin/payments?status=FAILED").andExpect(jsonPath("$.data.items[0].failureReason")
                .value(org.hamcrest.Matchers.containsString("Insufficient")));
        doGet(admin, "/api/admin/bookings/" + ref).andExpect(jsonPath("$.data.payments.length()").value(2));
        doPost(admin, "/api/admin/bookings/" + ref + "/cancel", Map.of("reason", "Refund test")).andExpect(status().isOk());
        doGet(admin, "/api/admin/payments?kind=REFUND").andExpect(jsonPath("$.data.items[0].gatewayRef").value("RF-" + ref));
    }

    // ================================================================== seats

    private long reservedEvent(Client admin, String title, int rows, int perRow) throws Exception {
        Map<String, Object> form = eventForm(title, now().plusDays(12), 400, null);
        form.put("seatingType", "RESERVED");
        form.put("seatRows", rows);
        form.put("seatsPerRow", perRow);
        JsonNode res = body(doPost(admin, "/api/admin/events", form).andExpect(status().isCreated()));
        assertThat(res.at("/data/capacity").asInt()).isEqualTo(rows * perRow);
        return res.at("/data/id").asLong();
    }

    private Map<String, Object> seatRequest(long eventId, String... seats) {
        return Map.of("eventId", eventId, "seats", List.of(seats));
    }

    @Test
    void reservedSeatsCanBeChosenAndAreNeverDoubleBooked() throws Exception {
        Client admin = newAdmin();
        long id = reservedEvent(admin, "Seated Play", 3, 4);
        assertThat(countRows("SELECT COUNT(*) FROM event_seats WHERE event_id = ?", id)).isEqualTo(12);
        doGet(anonymous(), "/api/events/" + id + "/seats").andExpect(jsonPath("$.data.rows").value(3))
                .andExpect(jsonPath("$.data.seatsPerRow").value(4)).andExpect(jsonPath("$.data.taken").isEmpty());

        Client asha = newMember();
        JsonNode booked = body(doPost(asha, "/api/bookings", seatRequest(id, "a2", "A1")).andExpect(status().isCreated()));
        assertThat(booked.at("/data/booking/seatLabels").asText()).isEqualTo("A1, A2");
        assertThat(booked.at("/data/booking/quantity").asInt()).isEqualTo(2);
        assertThat(booked.at("/data/booking/totalAmount").decimalValue()).isEqualByComparingTo("800");
        doGet(anonymous(), "/api/events/" + id + "/seats").andExpect(jsonPath("$.data.taken.length()").value(2))
                .andExpect(jsonPath("$.data.available").value(10));

        Client ravi = newMember();
        doPost(ravi, "/api/bookings", seatRequest(id, "A2", "A3")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SEATS_TAKEN"))
                .andExpect(jsonPath("$.error.message").value(org.hamcrest.Matchers.containsString("A2")));
        assertThat(ticketsSold(id)).as("the failed attempt changed nothing").isEqualTo(2);

        doPost(ravi, "/api/bookings", seatRequest(id, "D1")).andExpect(status().isBadRequest());           // no row D
        doPost(ravi, "/api/bookings", seatRequest(id, "A9")).andExpect(status().isBadRequest());           // no seat 9
        doPost(ravi, "/api/bookings", seatRequest(id, "B1", "b1")).andExpect(status().isBadRequest());     // twice
        doPost(ravi, "/api/bookings", seatRequest(id, "x")).andExpect(status().isBadRequest());
        doPost(ravi, "/api/bookings", Map.of("eventId", id, "quantity", 2)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fields.seats").exists());
        doPost(ravi, "/api/bookings", Map.of("eventId", id, "quantity", 3, "seats", List.of("B1"))).andExpect(status().isBadRequest());
        long general = createUpcomingEvent("Standing Gig", 10, "100");
        doPost(ravi, "/api/bookings", seatRequest(general, "A1")).andExpect(status().isBadRequest());
        doGet(anonymous(), "/api/events/" + general + "/seats").andExpect(status().isNotFound());
    }

    @Test
    void cancellingOrExpiringFreesTheSeats() throws Exception {
        Client admin = newAdmin();
        long id = reservedEvent(admin, "Seat Release", 2, 3);
        Client member = newMember();
        String paid = body(doPost(member, "/api/bookings", seatRequest(id, "A1", "A2"))).at("/data/booking/reference").asText();
        doPost(member, "/api/bookings/" + paid + "/pay", GOOD_CARD).andExpect(status().isOk());
        String held = body(doPost(member, "/api/bookings", seatRequest(id, "B3"))).at("/data/booking/reference").asText();
        doGet(anonymous(), "/api/events/" + id + "/seats").andExpect(jsonPath("$.data.taken.length()").value(3));

        doPost(member, "/api/bookings/" + paid + "/cancel", null).andExpect(status().isOk());
        db.query(conn -> conn.createStatement().executeUpdate(
                "UPDATE bookings SET hold_expires_at = NOW() - INTERVAL 1 MINUTE WHERE booking_ref = '" + held + "'"));
        bookingService.expireStaleHolds();
        doGet(anonymous(), "/api/events/" + id + "/seats").andExpect(jsonPath("$.data.taken").isEmpty());
        assertThat(ticketsSold(id)).isZero();
        doGet(member, "/api/bookings/" + paid).andExpect(jsonPath("$.data.seatLabels").value("A1, A2"));   // history kept
        doGet(admin, "/api/admin/inventory").andExpect(jsonPath("$.data[0].consistent").value(true))
                .andExpect(jsonPath("$.data[0].seatsTaken").value(0));
    }

    @Test
    void layoutIsLockedOnceSeatsAreTaken() throws Exception {
        Client admin = newAdmin();
        long id = reservedEvent(admin, "Layout", 2, 2);
        Map<String, Object> bigger = eventForm("Layout", now().plusDays(12), 400, null);
        bigger.put("seatingType", "RESERVED");
        bigger.put("seatRows", 3);
        bigger.put("seatsPerRow", 5);
        doPut(admin, "/api/admin/events/" + id, bigger).andExpect(status().isOk()).andExpect(jsonPath("$.data.capacity").value(15));
        assertThat(countRows("SELECT COUNT(*) FROM event_seats WHERE event_id = ?", id)).isEqualTo(15);

        doPost(newMember(), "/api/bookings", seatRequest(id, "C5")).andExpect(status().isCreated());
        bigger.put("seatRows", 2);
        doPut(admin, "/api/admin/events/" + id, bigger).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SEATING_LOCKED"));
        bigger.put("seatRows", 3);
        bigger.put("ticketPrice", 450);                         // other edits are still allowed
        doPut(admin, "/api/admin/events/" + id, bigger).andExpect(status().isOk());
        assertThat(countRows("SELECT COUNT(*) FROM event_seats WHERE event_id = ? AND booking_id IS NOT NULL", id)).isEqualTo(1);
    }

    @Test
    void cancellingAReservedEventRefundsAndFreesEverySeat() throws Exception {
        Client admin = newAdmin();
        long id = reservedEvent(admin, "Called Off", 2, 5);
        Client member = newMember();
        String ref = body(doPost(member, "/api/bookings", seatRequest(id, "A1", "A2", "A3"))).at("/data/booking/reference").asText();
        doPost(member, "/api/bookings/" + ref + "/pay", GOOD_CARD).andExpect(status().isOk());
        doPost(newMember(), "/api/bookings", seatRequest(id, "B5")).andExpect(status().isCreated());
        doPost(admin, "/api/admin/events/" + id + "/cancel", Map.of("reason", "Storm")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bookingsCancelled").value(2));
        assertThat(countRows("SELECT COUNT(*) FROM event_seats WHERE event_id = ? AND booking_id IS NOT NULL", id)).isZero();
        assertThat(countRows("SELECT COUNT(*) FROM payments WHERE kind = 'REFUND' AND amount = 1200")).isEqualTo(1);
    }

    @Test
    void twelveUsersRaceForTheSameSeat() throws Exception {
        Client admin = newAdmin();
        long id = reservedEvent(admin, "Front Row", 1, 6);
        List<SessionUser> users = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            users.add(sessionUser(createUser(uniqueEmail("seat"), Role.USER)));
        }
        ExecutorService pool = Executors.newFixedThreadPool(12);
        CountDownLatch gate = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (SessionUser u : users) {
            results.add(pool.submit(() -> {
                gate.await();
                try {
                    bookingService.book(u, new Requests.CreateBooking(id, null, List.of("A3", "A4"), null));
                    return true;
                } catch (BookMatrixException e) {
                    return false;
                }
            }));
        }
        gate.countDown();
        int winners = 0;
        for (Future<Boolean> f : results) {
            if (f.get(60, TimeUnit.SECONDS)) {
                winners++;
            }
        }
        pool.shutdown();
        assertThat(winners).isEqualTo(1);
        assertThat(countRows("SELECT COUNT(*) FROM event_seats WHERE event_id = ? AND booking_id IS NOT NULL", id)).isEqualTo(2);
        assertThat(ticketsSold(id)).isEqualTo(2);
    }
}
