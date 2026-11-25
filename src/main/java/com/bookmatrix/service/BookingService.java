package com.bookmatrix.service;

import com.bookmatrix.config.BookMatrixProperties;
import com.bookmatrix.dao.AuditLogDao;
import com.bookmatrix.dao.BookingDao;
import com.bookmatrix.dao.EventDao;
import com.bookmatrix.dao.PaymentDao;
import com.bookmatrix.dao.SeatDao;
import com.bookmatrix.dao.SeatDao.SeatLabel;
import com.bookmatrix.db.Database;
import com.bookmatrix.dto.BookingFilter;
import com.bookmatrix.dto.Requests;
import com.bookmatrix.dto.Views.BookingConfirmation;
import com.bookmatrix.dto.Views.BookingView;
import com.bookmatrix.dto.Views.PaymentResult;
import com.bookmatrix.dto.Views.PaymentView;
import com.bookmatrix.dto.Views.SeatMapView;
import com.bookmatrix.exception.BookingNotFoundException;
import com.bookmatrix.exception.ConflictException;
import com.bookmatrix.exception.EventNotFoundException;
import com.bookmatrix.exception.InsufficientTicketsException;
import com.bookmatrix.exception.InvalidBookingException;
import com.bookmatrix.exception.NotFoundException;
import com.bookmatrix.exception.PaymentDeclinedException;
import com.bookmatrix.exception.ValidationException;
import com.bookmatrix.model.Booking;
import com.bookmatrix.model.BookingStatus;
import com.bookmatrix.model.Event;
import com.bookmatrix.model.EventDisplayStatus;
import com.bookmatrix.model.Payment;
import com.bookmatrix.payment.GatewayResult;
import com.bookmatrix.payment.PaymentGateway;
import com.bookmatrix.payment.PaymentRequest;
import com.bookmatrix.security.SessionUser;
import com.bookmatrix.util.BookingReferenceGenerator;
import com.bookmatrix.util.PageRequest;
import com.bookmatrix.util.PageResult;
import com.bookmatrix.util.ReceiptBuilder;
import com.bookmatrix.util.TextUtils;
import com.bookmatrix.util.TimeProvider;
import com.bookmatrix.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Booking, payment and cancellation — the part of BookMatrix where correctness matters most.
 *
 * Flow:  book()  -> PENDING_PAYMENT (tickets/seats HELD for a few minutes)
 *        pay()   -> CONFIRMED (via the PaymentGateway)
 *        cancel()-> CANCELLED (+ refund if it was paid)
 *        expireStaleHolds() (background job) -> EXPIRED, hold released
 * Free events skip payment and are CONFIRMED straight away.
 *
 * Overselling and double-booked seats are prevented by the DATABASE, not just Java:
 *  1. SELECT ... FOR UPDATE locks the event row, so bookings for one event queue up.
 *  2. The inventory UPDATE is conditional ("... AND tickets_sold + qty <= capacity").
 *  3. Seats are claimed with "UPDATE event_seats ... WHERE booking_id IS NULL"; if any seat was
 *     already taken, fewer rows change and the whole transaction rolls back.
 *  4. CHECK and UNIQUE constraints reject anything that slips through (tickets_sold <= capacity,
 *     one successful charge per booking, one refund per booking).
 */
@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);
    private static final Pattern REQUEST_KEY = Pattern.compile("^[A-Za-z0-9-]{8,64}$");
    private static final int MAX_REFERENCE_ATTEMPTS = 5;

    private final Database db;
    private final BookingDao bookingDao;
    private final EventDao eventDao;
    private final SeatDao seatDao;
    private final PaymentDao paymentDao;
    private final AuditLogDao auditLogDao;
    private final PaymentGateway gateway;
    private final TimeProvider time;
    private final int maxPerBooking;
    private final int cutoffHours;
    private final int holdMinutes;

    public BookingService(Database db, BookingDao bookingDao, EventDao eventDao, SeatDao seatDao, PaymentDao paymentDao,
                          AuditLogDao auditLogDao, PaymentGateway gateway, TimeProvider time, BookMatrixProperties properties) {
        this.db = db;
        this.bookingDao = bookingDao;
        this.eventDao = eventDao;
        this.seatDao = seatDao;
        this.paymentDao = paymentDao;
        this.auditLogDao = auditLogDao;
        this.gateway = gateway;
        this.time = time;
        this.maxPerBooking = properties.maxTicketsPerBooking();
        this.cutoffHours = properties.cancellationCutoffHours();
        this.holdMinutes = properties.paymentHoldMinutes() > 0 ? properties.paymentHoldMinutes() : 10;
    }

    // ================================================================== seat map

    public SeatMapView seatMap(long eventId) {
        Event event = db.query(conn -> eventDao.findById(conn, eventId)).orElseThrow(() -> new EventNotFoundException(eventId));
        if (event.isArchived()) {
            throw new EventNotFoundException(eventId);
        }
        if (!event.isReservedSeating()) {
            throw new NotFoundException("This event has general admission, so there is no seat map.");
        }
        List<SeatDao.SeatRow> seats = db.query(conn -> seatDao.seatMap(conn, eventId));
        List<String> taken = seats.stream().filter(SeatDao.SeatRow::taken).map(SeatDao.SeatRow::label).toList();
        List<String> rows = new ArrayList<>();
        for (int r = 0; r < event.getSeatRows(); r++) {
            rows.add(String.valueOf((char) ('A' + r)));
        }
        return new SeatMapView(eventId, event.getSeatRows(), event.getSeatsPerRow(), rows, taken,
                seats.size() - taken.size(), maxPerBooking);
    }

    // ================================================================== booking (creates the hold)

    public BookingConfirmation book(SessionUser user, Requests.CreateBooking request) {
        List<SeatLabel> seats = parseSeats(request.seats());
        Validator v = new Validator().required("eventId", request.eventId(), "Choose an event.");
        if (request.eventId() != null) {
            v.check(request.eventId() > 0, "eventId", "Choose a valid event.");
        }
        if (seats.isEmpty()) {
            v.required("quantity", request.quantity(), "Choose how many tickets you want.")
                    .range("quantity", request.quantity(), 1, maxPerBooking,
                            "You can book between 1 and " + maxPerBooking + " tickets at a time.");
        } else {
            v.check(seats.size() <= maxPerBooking, "seats", "You can choose at most " + maxPerBooking + " seats at a time.");
            v.check(request.quantity() == null || request.quantity() == seats.size(), "quantity",
                    "The number of tickets must match the seats you picked.");
        }
        String requestKey = TextUtils.isBlank(request.requestKey()) ? UUID.randomUUID().toString() : request.requestKey().strip();
        v.check(REQUEST_KEY.matcher(requestKey).matches(), "requestKey", "Invalid request key.");
        v.throwIfInvalid();

        long eventId = request.eventId();                                 // unboxing Long -> long
        int quantity = seats.isEmpty() ? request.quantity() : seats.size();
        LocalDateTime now = time.now();

        try {
            return db.inTransaction(conn -> {
                // (1) lock the event row: concurrent bookings for this event now queue up here
                Event event = eventDao.findByIdForUpdate(conn, eventId).orElseThrow(() -> new EventNotFoundException(eventId));

                // duplicate submit (double click, network retry)? return the booking already made
                Optional<Booking> previous = bookingDao.findByRequestKey(conn, user.id(), requestKey);
                if (previous.isPresent()) {
                    return new BookingConfirmation(toView(previous.get(), now, false), "This booking was already created.", true);
                }

                Optional<String> blocked = event.getBookingBlockReason(now);
                if (blocked.isPresent()) {
                    if (event.getDisplayStatus(now) == EventDisplayStatus.SOLD_OUT) {
                        throw new InsufficientTicketsException(quantity, 0);
                    }
                    throw new InvalidBookingException(blocked.get());
                }
                validateSeatsForEvent(event, seats);
                if (quantity > event.getAvailableTickets()) {
                    throw new InsufficientTicketsException(quantity, event.getAvailableTickets());
                }
                if (event.isReservedSeating()) {
                    List<String> alreadyTaken = takenAmong(conn, eventId, seats);
                    if (!alreadyTaken.isEmpty()) {
                        throw new ConflictException("SEATS_TAKEN", "Sorry, " + String.join(", ", alreadyTaken)
                                + (alreadyTaken.size() == 1 ? " was" : " were") + " just taken. Please choose other seats.");
                    }
                }

                // (2) atomic, conditional inventory update — the database has the final say
                if (eventDao.reserveTickets(conn, eventId, quantity, now) != 1) {
                    throw new InsufficientTicketsException(quantity, 0);
                }

                boolean free = event.getTicketPrice().signum() == 0;
                Booking booking = new Booking();
                booking.setUserId(user.id());
                booking.setEventId(eventId);
                booking.setQuantity(quantity);
                booking.setSeatLabels(seats.isEmpty() ? null : seats.stream().map(SeatLabel::toString).collect(Collectors.joining(", ")));
                booking.setUnitPrice(event.getTicketPrice());                   // price frozen at booking time
                booking.setTotalAmount(Booking.calculateTotal(event.getTicketPrice(), quantity));
                booking.setRequestKey(requestKey);
                booking.setBookedAt(now);
                booking.setStatus(free ? BookingStatus.CONFIRMED : BookingStatus.PENDING_PAYMENT);
                booking.setHoldExpiresAt(free ? null : now.plusMinutes(holdMinutes));
                booking.setPaidAt(free ? now : null);
                insertWithUniqueReference(conn, booking);

                // (3) claim the exact seats; fewer rows changed = someone took one => roll everything back
                if (!seats.isEmpty() && seatDao.reserve(conn, eventId, booking.getId(), seats) != seats.size()) {
                    throw new ConflictException("SEATS_TAKEN", "One of the seats you picked was just taken. Please choose again.");
                }

                auditLogDao.insert(conn, user.id(), free ? "BOOKING_CONFIRMED" : "BOOKING_HELD", "BOOKING", booking.getId(),
                        booking.getReference() + " x" + quantity + (seats.isEmpty() ? "" : " (" + booking.getSeatLabels() + ")"), now);
                Booking saved = bookingDao.findByReference(conn, booking.getReference()).orElseThrow();
                String message = free ? ReceiptBuilder.confirmationMessage(saved)
                        : "Your " + (seats.isEmpty() ? "tickets are" : "seats are") + " held for " + holdMinutes
                          + " minutes. Complete the payment to confirm.";
                return new BookingConfirmation(toView(saved, now, false), message, false);
            });
        } catch (ConflictException e) {
            // Two identical requests raced past the duplicate check: the UNIQUE (user_id, request_key)
            // constraint stopped the second, which was rolled back. Return the booking that won.
            if (!"CONSTRAINT_VIOLATION".equals(e.code())) {
                throw e;
            }
            return db.query(conn -> bookingDao.findByRequestKey(conn, user.id(), requestKey))
                    .map(b -> new BookingConfirmation(toView(b, now, false), "This booking was already created.", true))
                    .orElseThrow(() -> e);
        }
    }

    /** Seat labels from the request: trimmed, upper-case, no duplicates, sorted A1, A2, B1... */
    static List<SeatLabel> parseSeats(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        if (raw.size() > 50) {
            throw ValidationException.forField("seats", "Too many seats in one request.");
        }
        Set<SeatLabel> unique = new LinkedHashSet<>();
        for (String label : raw) {
            try {
                if (!unique.add(SeatLabel.parse(label))) {
                    throw ValidationException.forField("seats", "Seat " + label.trim().toUpperCase() + " was chosen twice.");
                }
            } catch (IllegalArgumentException e) {
                throw ValidationException.forField("seats", "'" + TextUtils.truncate(String.valueOf(label), 10) + "' is not a valid seat.");
            }
        }
        List<SeatLabel> sorted = new ArrayList<>(unique);
        sorted.sort(Comparator.comparing(SeatLabel::row).thenComparingInt(SeatLabel::number));
        return sorted;
    }

    private static void validateSeatsForEvent(Event event, List<SeatLabel> seats) {
        if (!event.isReservedSeating()) {
            if (!seats.isEmpty()) {
                throw ValidationException.forField("seats", "This event has general admission — choose a number of tickets instead of seats.");
            }
            return;
        }
        if (seats.isEmpty()) {
            throw ValidationException.forField("seats", "Choose your seats on the seat map.");
        }
        char lastRow = (char) ('A' + event.getSeatRows() - 1);
        for (SeatLabel s : seats) {
            if (s.row().charAt(0) > lastRow || s.number() > event.getSeatsPerRow()) {
                throw ValidationException.forField("seats", "Seat " + s + " does not exist at this venue.");
            }
        }
    }

    private List<String> takenAmong(Connection conn, long eventId, List<SeatLabel> wanted) throws SQLException {
        Set<String> taken = seatDao.seatMap(conn, eventId).stream().filter(SeatDao.SeatRow::taken)
                .map(SeatDao.SeatRow::label).collect(Collectors.toSet());
        return wanted.stream().map(SeatLabel::toString).filter(taken::contains).toList();
    }

    /** Booking references are random; on the (very unlikely) clash we simply try a new one. */
    private void insertWithUniqueReference(Connection conn, Booking booking) throws SQLException {
        for (int attempt = 1; ; attempt++) {
            booking.setReference(BookingReferenceGenerator.next());
            try {
                booking.setId(bookingDao.insert(conn, booking));
                return;
            } catch (SQLException e) {
                boolean referenceClash = Database.isDuplicateKey(e) && String.valueOf(e.getMessage()).contains("uq_bookings_ref");
                if (!referenceClash || attempt >= MAX_REFERENCE_ATTEMPTS) {
                    throw e;
                }
            }
        }
    }

    // ================================================================== payment

    /**
     * Pays for a held booking through the PaymentGateway.
     * Declined payments are recorded (status FAILED) and the hold stays, so the user can retry.
     */
    public PaymentResult pay(SessionUser user, String reference, Requests.Pay details) {
        Booking booking = loadOwned(user, reference);
        LocalDateTime now = time.now();
        switch (booking.getStatus()) {
            case CONFIRMED -> throw new ConflictException("ALREADY_PAID", "This booking is already paid and confirmed.");
            case CANCELLED -> throw new InvalidBookingException("This booking was cancelled, so it can no longer be paid.");
            case EXPIRED -> throw new InvalidBookingException("This booking expired because it was not paid in time. Please book again.");
            case PENDING_PAYMENT -> { }
        }
        if (!booking.isPayable(now)) {
            expireOne(booking.getReference(), now);
            throw new InvalidBookingException("Your hold expired and the tickets were released. Please book again.");
        }
        if (details == null) {
            throw ValidationException.forField("method", "Choose card or UPI.");
        }

        // the gateway validates the details (400 if malformed) and approves or declines
        GatewayResult result = gateway.charge(new PaymentRequest(details.method(), details.cardNumber(), details.cardName(),
                details.expiry(), details.cvv(), details.upiId(), booking.getTotalAmount(), booking.getReference()));
        Payment.Method method = Payment.Method.valueOf(result.method());

        if (!result.approved()) {
            db.query(conn -> {          // auto-commit: the failed attempt must be kept even though we throw
                paymentDao.insert(conn, booking.getId(), Payment.Kind.CHARGE, Payment.Status.FAILED, booking.getTotalAmount(),
                        method, result.cardBrand(), result.cardLast4(), result.upiHandle(), result.gatewayRef(),
                        result.declineReason(), now);
                auditLogDao.insert(conn, user.id(), "PAYMENT_FAILED", "BOOKING", booking.getId(),
                        booking.getReference() + ": " + result.declineReason(), now);
                return null;
            });
            throw new PaymentDeclinedException(result.declineReason());
        }

        return db.inTransaction(conn -> {
            Booking locked = bookingDao.findByReferenceForUpdate(conn, booking.getReference())
                    .orElseThrow(() -> new BookingNotFoundException(booking.getReference()));
            if (locked.getStatus() == BookingStatus.CONFIRMED) {
                throw new ConflictException("ALREADY_PAID", "This booking is already paid and confirmed.");
            }
            if (!locked.isPayable(now)) {
                throw new InvalidBookingException("Your hold expired before the payment finished. Please book again.");
            }
            long paymentId = paymentDao.insert(conn, locked.getId(), Payment.Kind.CHARGE, Payment.Status.SUCCEEDED,
                    locked.getTotalAmount(), method, result.cardBrand(), result.cardLast4(), result.upiHandle(),
                    result.gatewayRef(), null, now);
            if (bookingDao.markPaid(conn, locked.getId(), now) != 1) {
                throw new ConflictException("ALREADY_PAID", "This booking is already paid and confirmed.");
            }
            auditLogDao.insert(conn, user.id(), "PAYMENT_SUCCEEDED", "BOOKING", locked.getId(),
                    locked.getReference() + " paid " + locked.getTotalAmount() + " via " + method, now);
            Booking paid = bookingDao.findByReference(conn, locked.getReference()).orElseThrow();
            List<PaymentView> payments = paymentDao.findForBooking(conn, paid.getId()).stream().map(PaymentView::from).toList();
            PaymentView payment = payments.stream().filter(p -> p.id() == paymentId).findFirst().orElseThrow();
            return new PaymentResult(toView(paid, now, false).withPayments(payments), payment,
                    "Payment successful. " + ReceiptBuilder.confirmationMessage(paid));
        });
    }

    // ================================================================== hold expiry

    /** Releases every hold whose payment window has passed. Called by HoldExpiryJob every 30 seconds. */
    public int expireStaleHolds() {
        LocalDateTime now = time.now();
        List<String> refs = db.query(conn -> bookingDao.findExpiredHolds(conn, now, 200));
        int expired = 0;
        for (String ref : refs) {
            if (expireOne(ref, now)) {
                expired++;
            }
        }
        if (expired > 0) {
            log.info("Released {} expired booking hold(s)", expired);
        }
        return expired;
    }

    private boolean expireOne(String reference, LocalDateTime now) {
        return db.inTransaction(conn -> {
            Booking b = bookingDao.findByReferenceForUpdate(conn, reference).orElse(null);
            if (b == null || b.getStatus() != BookingStatus.PENDING_PAYMENT || b.isPayable(now)) {
                return false;
            }
            bookingDao.markExpired(conn, b.getId());
            eventDao.releaseTickets(conn, b.getEventId(), b.getQuantity());
            seatDao.releaseForBooking(conn, b.getId());
            auditLogDao.insert(conn, null, "BOOKING_EXPIRED", "BOOKING", b.getId(),
                    b.getReference() + " x" + b.getQuantity() + " released (not paid in time)", now);
            return true;
        });
    }

    // ================================================================== reading

    public PageResult<BookingView> listMine(SessionUser user, String text, String filter, PageRequest page) {
        LocalDateTime now = time.now();
        BookingFilter f = BookingFilter.parse(filter);
        String q = TextUtils.truncate(TextUtils.clean(text), 100);
        return db.query(conn -> PageResult.of(bookingDao.search(conn, user.id(), null, q, f, page, now), page,
                bookingDao.count(conn, user.id(), null, q, f, now))).map(b -> toView(b, now, false));
    }

    /** A user can only see their own booking. Someone else's reference looks exactly like a missing one (404). */
    public BookingView getMine(SessionUser user, String reference) {
        Booking booking = loadOwned(user, reference);
        return toView(booking, time.now(), false).withPayments(paymentsOf(booking));
    }

    public String receipt(SessionUser user, String reference) {
        Booking booking = user.isAdmin() ? loadAny(reference) : loadOwned(user, reference);
        List<Payment> payments = db.query(conn -> paymentDao.findForBooking(conn, booking.getId()));
        return ReceiptBuilder.receipt(booking, payments);
    }

    public PageResult<BookingView> listAll(String text, String filter, Long eventId, Long userId, PageRequest page) {
        LocalDateTime now = time.now();
        BookingFilter f = BookingFilter.parse(filter);
        String q = TextUtils.truncate(TextUtils.clean(text), 100);
        return db.query(conn -> PageResult.of(bookingDao.search(conn, userId, eventId, q, f, page, now), page,
                bookingDao.count(conn, userId, eventId, q, f, now))).map(b -> toView(b, now, true));
    }

    public BookingView getAny(String reference) {
        Booking booking = loadAny(reference);
        return toView(booking, time.now(), true).withPayments(paymentsOf(booking));
    }

    public PageResult<PaymentView> listPayments(String text, String kind, String status, PageRequest page) {
        String q = TextUtils.truncate(TextUtils.clean(text), 100);
        return db.query(conn -> PageResult.of(paymentDao.search(conn, q, kind, status, page), page,
                paymentDao.count(conn, q, kind, status))).map(PaymentView::from);
    }

    private List<PaymentView> paymentsOf(Booking booking) {
        return db.query(conn -> paymentDao.findForBooking(conn, booking.getId())).stream().map(PaymentView::from).toList();
    }

    // ================================================================== cancellation

    public BookingView cancelMine(SessionUser user, String reference, Requests.CancelBooking request) {
        loadOwned(user, reference);    // ownership check first (404 for other people's bookings)
        return cancel(user, reference, request == null ? null : request.reason(), false);
    }

    /** Admin cancellation uses exactly the same transactional path as user cancellation. */
    public BookingView cancelAsAdmin(SessionUser admin, String reference, Requests.CancelBooking request) {
        return cancel(admin, reference, request == null ? null : request.reason(), true);
    }

    private BookingView cancel(SessionUser actor, String reference, String reasonText, boolean byAdmin) {
        String reason = TextUtils.truncate(TextUtils.clean(reasonText), 255);
        String normalized = normalizeReference(reference);
        LocalDateTime now = time.now();
        return db.inTransaction(conn -> {
            // lock the booking row: a second cancel request waits here, then sees CANCELLED
            Booking booking = bookingDao.findByReferenceForUpdate(conn, normalized)
                    .orElseThrow(() -> new BookingNotFoundException(normalized));
            if (booking.getStatus() == BookingStatus.CANCELLED) {
                throw new InvalidBookingException("This booking is already cancelled.");
            }
            if (booking.getStatus() == BookingStatus.EXPIRED) {
                throw new InvalidBookingException("This booking already expired; its tickets were released.");
            }
            if (byAdmin) {
                if (booking.getEventEndsAt() != null && !now.isBefore(booking.getEventEndsAt())) {
                    throw new InvalidBookingException("The event has finished; its bookings can no longer be cancelled.");
                }
            } else {
                booking.getCancellationBlockReason(now, cutoffHours).ifPresent(msg -> {
                    throw new InvalidBookingException(msg);
                });
            }
            String finalReason = reason != null ? reason
                    : booking.getStatus() == BookingStatus.PENDING_PAYMENT ? "Hold released before payment"
                    : byAdmin ? "Cancelled by administrator" : "Cancelled by customer";
            if (!cancelLocked(conn, booking, actor.id(), finalReason, now)) {
                throw new InvalidBookingException("This booking is already cancelled.");
            }
            auditLogDao.insert(conn, actor.id(), byAdmin ? "BOOKING_CANCELLED_BY_ADMIN" : "BOOKING_CANCELLED",
                    "BOOKING", booking.getId(), booking.getReference() + " x" + booking.getQuantity() + " released", now);
            Booking cancelled = bookingDao.findByReference(conn, normalized).orElseThrow();
            return toView(cancelled, now, byAdmin).withPayments(
                    paymentDao.findForBooking(conn, cancelled.getId()).stream().map(PaymentView::from).toList());
        });
    }

    /**
     * The shared core of every cancellation (user, admin, account closure). Must run inside a
     * transaction on a locked/known booking. Status flips only once (guarded UPDATE), then the
     * tickets and seats go back, and a paid booking gets exactly one REFUND row.
     */
    boolean cancelLocked(Connection conn, Booking booking, long actorId, String reason, LocalDateTime now) throws SQLException {
        boolean wasPaid = booking.getStatus() == BookingStatus.CONFIRMED;
        if (bookingDao.markCancelled(conn, booking.getId(), actorId, TextUtils.truncate(reason, 255), now) != 1) {
            return false;
        }
        if (eventDao.releaseTickets(conn, booking.getEventId(), booking.getQuantity()) != 1) {
            throw new IllegalStateException("Inventory for event " + booking.getEventId() + " is inconsistent");
        }
        seatDao.releaseForBooking(conn, booking.getId());
        if (wasPaid && booking.getTotalAmount().signum() > 0) {
            Optional<Payment> charge = paymentDao.findSuccessfulCharge(conn, booking.getId());
            if (charge.isPresent()) {
                Payment c = charge.get();
                paymentDao.insert(conn, booking.getId(), Payment.Kind.REFUND, Payment.Status.SUCCEEDED, c.amount(), c.method(),
                        c.cardBrand(), c.cardLast4(), c.upiHandle(), "RF-" + booking.getReference(), null, now);
            }
        }
        return true;
    }

    /** Used when an account is closed: cancels every upcoming active booking of that user. */
    int cancelUpcomingForUser(Connection conn, long userId, long actorId, String reason, LocalDateTime now) throws SQLException {
        int cancelled = 0;
        for (Booking booking : bookingDao.findActiveUpcomingForUser(conn, userId, now)) {
            if (cancelLocked(conn, booking, actorId, reason, now)) {
                cancelled++;
            }
        }
        return cancelled;
    }

    // ================================================================== helpers

    private Booking loadOwned(SessionUser user, String reference) {
        Booking booking = loadAny(reference);
        if (booking.getUserId() != user.id()) {
            throw new BookingNotFoundException(normalizeReference(reference));
        }
        return booking;
    }

    private Booking loadAny(String reference) {
        String normalized = normalizeReference(reference);
        return db.query(conn -> bookingDao.findByReference(conn, normalized))
                .orElseThrow(() -> new BookingNotFoundException(normalized));
    }

    /** Validates the reference format before touching the database. */
    static String normalizeReference(String reference) {
        String value = reference == null ? "" : reference.strip().toUpperCase();
        if (!BookingReferenceGenerator.isWellFormed(value)) {
            throw new BookingNotFoundException(TextUtils.truncate(value, 20));
        }
        return value;
    }

    private BookingView toView(Booking booking, LocalDateTime now, boolean forAdmin) {
        return BookingView.from(booking, now, cutoffHours, forAdmin);
    }
}
