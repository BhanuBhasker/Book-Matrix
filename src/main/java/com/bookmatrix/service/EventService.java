package com.bookmatrix.service;

import com.bookmatrix.config.BookMatrixProperties;
import com.bookmatrix.dao.AuditLogDao;
import com.bookmatrix.dao.BookingDao;
import com.bookmatrix.dao.CategoryDao;
import com.bookmatrix.dao.EventDao;
import com.bookmatrix.dao.PaymentDao;
import com.bookmatrix.dao.SeatDao;
import com.bookmatrix.dao.StatsDao;
import com.bookmatrix.dao.VenueDao;
import com.bookmatrix.db.Database;
import com.bookmatrix.dto.ArchiveFilter;
import com.bookmatrix.dto.EventLifecycleFilter;
import com.bookmatrix.dto.EventSearchCriteria;
import com.bookmatrix.dto.EventSort;
import com.bookmatrix.dto.Requests;
import com.bookmatrix.dto.Views.CategoryView;
import com.bookmatrix.dto.Views.DeleteOutcome;
import com.bookmatrix.dto.Views.EventCancelled;
import com.bookmatrix.dto.Views.EventView;
import com.bookmatrix.dto.Views.HomeView;
import com.bookmatrix.dto.Views.InventoryView;
import com.bookmatrix.exception.ConflictException;
import com.bookmatrix.exception.EventNotFoundException;
import com.bookmatrix.exception.ValidationException;
import com.bookmatrix.model.Category;
import com.bookmatrix.model.Event;
import com.bookmatrix.model.EventDisplayStatus;
import com.bookmatrix.model.EventStatus;
import com.bookmatrix.model.Venue;
import com.bookmatrix.security.SessionUser;
import com.bookmatrix.util.Money;
import com.bookmatrix.util.PageRequest;
import com.bookmatrix.util.PageResult;
import com.bookmatrix.util.TextUtils;
import com.bookmatrix.util.TimeProvider;
import com.bookmatrix.validation.Validator;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Event discovery for everyone, and full event management (CRUD + status) for admins. */
@Service
public class EventService {

    private static final int MAX_SEARCH_LENGTH = 100;

    private final Database db;
    private final EventDao eventDao;
    private final CategoryDao categoryDao;
    private final VenueDao venueDao;
    private final BookingDao bookingDao;
    private final StatsDao statsDao;
    private final AuditLogDao auditLogDao;
    private final TimeProvider time;
    private final int maxPerBooking;
    private final EventFormValidator formValidator;
    private final SeatDao seatDao;
    private final PaymentDao paymentDao;

    public EventService(Database db, EventDao eventDao, CategoryDao categoryDao, VenueDao venueDao,
                        BookingDao bookingDao, StatsDao statsDao, AuditLogDao auditLogDao, TimeProvider time,
                        BookMatrixProperties properties, EventFormValidator formValidator, SeatDao seatDao,
                        PaymentDao paymentDao) {
        this.db = db;
        this.eventDao = eventDao;
        this.categoryDao = categoryDao;
        this.venueDao = venueDao;
        this.bookingDao = bookingDao;
        this.statsDao = statsDao;
        this.auditLogDao = auditLogDao;
        this.time = time;
        this.maxPerBooking = properties.maxTicketsPerBooking();
        this.formValidator = formValidator;
        this.seatDao = seatDao;
        this.paymentDao = paymentDao;
    }

    // ================================================================== public discovery

    public PageResult<EventView> search(Map<String, String> params, boolean admin) {
        EventSearchCriteria criteria = parseCriteria(params, admin);
        PageRequest page = PageRequest.of(parseInt(params.get("page"), "page"), parseInt(params.get("size"), "size"));
        LocalDateTime now = time.now();
        return db.query(conn -> PageResult.of(eventDao.search(conn, criteria, page, now), page,
                eventDao.count(conn, criteria, now))).map(e -> toView(e, now));
    }

    /** Public event page. Archived events are hidden from the public (404). */
    public EventView getPublic(long id) {
        Event event = load(id);
        if (event.isArchived()) {
            throw new EventNotFoundException(id);
        }
        return toView(event, time.now());
    }

    public EventView getForAdmin(long id) {
        return toView(load(id), time.now());
    }

    public HomeView home() {
        LocalDateTime now = time.now();
        return db.query(conn -> {
            EventSearchCriteria upcoming = EventSearchCriteria.upcoming();
            List<EventView> featured = eventDao.search(conn, upcoming.withFeaturedOnly(), new PageRequest(1, 3), now)
                    .stream().map(e -> toView(e, now)).toList();
            List<EventView> soon = eventDao.search(conn, upcoming, new PageRequest(1, 6), now)
                    .stream().map(e -> toView(e, now)).toList();
            Map<Integer, Long> counts = new HashMap<>();
            for (Object[] row : categoryDao.countUpcomingPerCategory(conn, now)) {
                counts.put((Integer) row[0], (Long) row[1]);
            }
            List<CategoryView> categories = categoryDao.findAll(conn).stream()
                    .map(c -> new CategoryView(c.id(), c.name(), c.slug(), c.description(), counts.getOrDefault(c.id(), 0L)))
                    .toList();
            return new HomeView(featured, soon, categories, venueDao.distinctCities(conn),
                    eventDao.count(conn, upcoming, now));
        });
    }

    public List<CategoryView> categories() {
        return db.query(conn -> categoryDao.findAll(conn)).stream()
                .map(c -> new CategoryView(c.id(), c.name(), c.slug(), c.description(), 0)).toList();
    }

    public List<String> cities() {
        return db.query(venueDao::distinctCities);     // method reference instead of conn -> venueDao.distinctCities(conn)
    }

    // ================================================================== admin CRUD

    public EventView create(SessionUser admin, Requests.EventForm form) {
        LocalDateTime now = time.now();
        Event event = formValidator.validate(form, null, now);
        long id = db.inTransaction(conn -> {
            Category category = categoryDao.findById(conn, event.getCategoryId())
                    .orElseThrow(() -> ValidationException.forField("categoryId", "Choose a valid category."));
            Venue venue = venueDao.findOrCreate(conn, event.getVenueName(), event.getCity(), event.getVenueAddress(), now);
            event.setVenueId(venue.id());
            if (eventDao.existsSlot(conn, event.getTitle(), venue.id(), event.getStartsAt(), null)) {
                throw new ConflictException("DUPLICATE_EVENT", "An event with this title already exists at this venue and time.");
            }
            long newId = eventDao.insert(conn, event, admin.id(), now);
            if (event.isReservedSeating()) {
                seatDao.generateSeats(conn, newId, event.getSeatRows(), event.getSeatsPerRow());
            }
            auditLogDao.insert(conn, admin.id(), "EVENT_CREATED", "EVENT", newId,
                    event.getTitle() + " (" + category.name() + ")", now);
            return newId;
        });
        return getForAdmin(id);
    }

    public EventView update(SessionUser admin, long id, Requests.EventForm form) {
        LocalDateTime now = time.now();
        return db.inTransaction(conn -> {
            Event current = eventDao.findByIdForUpdate(conn, id).orElseThrow(() -> new EventNotFoundException(id));
            EventDisplayStatus state = current.getDisplayStatus(now);
            if (state == EventDisplayStatus.CANCELLED || state == EventDisplayStatus.COMPLETED) {
                throw new ConflictException("EVENT_LOCKED", "Cancelled or completed events cannot be edited; they are kept as history.");
            }
            Event changed = formValidator.validate(form, current, now);
            changed.setId(id);
            if (changed.getCapacity() < current.getTicketsSold()) {
                throw new ConflictException("CAPACITY_BELOW_SOLD", "Capacity cannot be lower than the "
                        + current.getTicketsSold() + " tickets already sold.");
            }
            boolean layoutChanged = current.getSeatingType() != changed.getSeatingType()
                    || !java.util.Objects.equals(current.getSeatRows(), changed.getSeatRows())
                    || !java.util.Objects.equals(current.getSeatsPerRow(), changed.getSeatsPerRow());
            if (layoutChanged && current.getTicketsSold() > 0) {
                throw new ConflictException("SEATING_LOCKED", "The seating layout cannot change once tickets are sold or held. "
                        + "Cancel the event or keep the current layout.");
            }
            categoryDao.findById(conn, changed.getCategoryId())
                    .orElseThrow(() -> ValidationException.forField("categoryId", "Choose a valid category."));
            Venue venue = venueDao.findOrCreate(conn, changed.getVenueName(), changed.getCity(), changed.getVenueAddress(), now);
            changed.setVenueId(venue.id());
            if (eventDao.existsSlot(conn, changed.getTitle(), venue.id(), changed.getStartsAt(), id)) {
                throw new ConflictException("DUPLICATE_EVENT", "Another event with this title already exists at this venue and time.");
            }
            if (eventDao.update(conn, changed, now) != 1) {
                throw new ConflictException("CAPACITY_BELOW_SOLD", "Capacity cannot be lower than the tickets already sold.");
            }
            if (layoutChanged) {                       // no seats are taken (checked above), so rebuild the seat rows
                seatDao.deleteSeats(conn, id);
                if (changed.isReservedSeating()) {
                    seatDao.generateSeats(conn, id, changed.getSeatRows(), changed.getSeatsPerRow());
                }
            }
            auditLogDao.insert(conn, admin.id(), "EVENT_UPDATED", "EVENT", id, describeChanges(current, changed), now);
            return toView(eventDao.findById(conn, id).orElseThrow(), now);
        });
    }

    /**
     * Cancelling an event also cancels every confirmed booking for it and resets the inventory —
     * all in ONE transaction, so there is never a cancelled event with "live" bookings.
     */
    public EventCancelled cancel(SessionUser admin, long id, String reasonText) {
        String reason = TextUtils.truncate(TextUtils.clean(reasonText), 200);
        LocalDateTime now = time.now();
        return db.inTransaction(conn -> {
            Event event = eventDao.findByIdForUpdate(conn, id).orElseThrow(() -> new EventNotFoundException(id));
            if (event.getStatus() == EventStatus.CANCELLED) {
                throw new ConflictException("NO_CHANGE", "This event is already cancelled.");
            }
            if (event.getDisplayStatus(now) == EventDisplayStatus.COMPLETED) {
                throw new ConflictException("EVENT_COMPLETED", "This event has already finished and cannot be cancelled.");
            }
            String bookingReason = "Event cancelled by organiser" + (reason == null ? "" : ": " + reason);
            int refunds = paymentDao.refundAllForEvent(conn, id, now);      // refund paid bookings first (they are still CONFIRMED)
            int cancelledBookings = bookingDao.cancelAllForEvent(conn, id, admin.id(), TextUtils.truncate(bookingReason, 255), now);
            seatDao.releaseForEvent(conn, id);
            eventDao.markCancelled(conn, id, now);
            auditLogDao.insert(conn, admin.id(), "EVENT_CANCELLED", "EVENT", id,
                    event.getTitle() + ", " + cancelledBookings + " booking(s) cancelled, " + refunds + " refund(s)", now);
            EventView view = toView(eventDao.findById(conn, id).orElseThrow(), now);
            return new EventCancelled(view, cancelledBookings, "Event cancelled. " + cancelledBookings
                    + " booking(s) were cancelled" + (refunds > 0 ? " and " + refunds + " payment(s) refunded." : "."));
        });
    }

    /**
     * Delete policy:
     *  - no bookings ever           -> real DELETE (nothing to preserve)
     *  - has bookings               -> ARCHIVE (hidden, history preserved), allowed only once the
     *                                  event is cancelled or completed, so ticket holders are never
     *                                  left with an invisible event.
     */
    public DeleteOutcome delete(SessionUser admin, long id) {
        LocalDateTime now = time.now();
        return db.inTransaction(conn -> {
            Event event = eventDao.findByIdForUpdate(conn, id).orElseThrow(() -> new EventNotFoundException(id));
            if (bookingDao.countForEvent(conn, id) == 0) {
                eventDao.deleteById(conn, id);
                auditLogDao.insert(conn, admin.id(), "EVENT_DELETED", "EVENT", id, event.getTitle(), now);
                return new DeleteOutcome("DELETED", "The event had no bookings and was permanently deleted.");
            }
            if (event.isArchived()) {
                throw new ConflictException("NO_CHANGE", "This event is already archived.");
            }
            EventDisplayStatus state = event.getDisplayStatus(now);
            if (state != EventDisplayStatus.CANCELLED && state != EventDisplayStatus.COMPLETED) {
                throw new ConflictException("HAS_ACTIVE_BOOKINGS",
                        "This event has bookings. Cancel the event first (this refunds every booking), then archive it.");
            }
            eventDao.setArchived(conn, id, now, now);
            auditLogDao.insert(conn, admin.id(), "EVENT_ARCHIVED", "EVENT", id, event.getTitle(), now);
            return new DeleteOutcome("ARCHIVED", "The event has booking history, so it was archived instead of deleted.");
        });
    }

    public EventView restore(SessionUser admin, long id) {
        LocalDateTime now = time.now();
        return db.inTransaction(conn -> {
            Event event = eventDao.findByIdForUpdate(conn, id).orElseThrow(() -> new EventNotFoundException(id));
            if (!event.isArchived()) {
                throw new ConflictException("NO_CHANGE", "This event is not archived.");
            }
            eventDao.setArchived(conn, id, null, now);
            auditLogDao.insert(conn, admin.id(), "EVENT_RESTORED", "EVENT", id, event.getTitle(), now);
            return toView(eventDao.findById(conn, id).orElseThrow(), now);
        });
    }

    public EventView setFeatured(SessionUser admin, long id, Boolean featured) {
        if (featured == null) {
            throw ValidationException.forField("featured", "Say whether the event is featured (true/false).");
        }
        LocalDateTime now = time.now();
        return db.inTransaction(conn -> {
            eventDao.findByIdForUpdate(conn, id).orElseThrow(() -> new EventNotFoundException(id));
            eventDao.setFeatured(conn, id, featured);       // auto-unboxing Boolean -> boolean
            auditLogDao.insert(conn, admin.id(), featured ? "EVENT_FEATURED" : "EVENT_UNFEATURED", "EVENT", id, null, now);
            return toView(eventDao.findById(conn, id).orElseThrow(), now);
        });
    }

    public Category createCategory(SessionUser admin, Requests.NewCategory request) {
        String name = TextUtils.clean(request.name());
        String description = TextUtils.clean(request.description());
        new Validator().required("name", name, "Enter a category name.").length("name", name, 2, 50)
                .length("description", description, 0, 255).throwIfInvalid();
        String slug = TextUtils.slugify(name);
        LocalDateTime now = time.now();
        return db.inTransaction(conn -> {
            if (categoryDao.findByName(conn, name).isPresent()) {
                throw new ConflictException("DUPLICATE_CATEGORY", "That category already exists.");
            }
            int id = categoryDao.insert(conn, name, slug, description);
            auditLogDao.insert(conn, admin.id(), "CATEGORY_CREATED", "CATEGORY", (long) id, name, now);
            return new Category(id, name, slug, description);
        });
    }

    /** Inventory screen: stored counters next to the totals recomputed from bookings. */
    public List<InventoryView> inventory(boolean includeArchived) {
        return db.query(conn -> statsDao.inventory(conn, includeArchived)).stream()
                .map(r -> new InventoryView(r.eventId(), r.title(), r.city(), r.startsAt(), r.status(), r.archived(),
                        r.capacity(), r.ticketsSold(), r.available(), r.confirmedTickets(), r.heldTickets(), r.seatsTaken(),
                        r.revenue(), r.consistent(), r.capacity() == 0 ? 0 : (int) Math.round(100.0 * r.ticketsSold() / r.capacity())))
                .toList();
    }

    // ================================================================== helpers

    Event load(long id) {
        return db.query(conn -> eventDao.findById(conn, id)).orElseThrow(() -> new EventNotFoundException(id));
    }

    EventView toView(Event event, LocalDateTime now) {
        return EventView.from(event, now, maxPerBooking);
    }

    private static String describeChanges(Event before, Event after) {
        StringBuilder sb = new StringBuilder();
        if (!before.getTitle().equals(after.getTitle())) sb.append("title; ");
        if (!before.getStartsAt().equals(after.getStartsAt())) sb.append("date/time; ");
        if (before.getTicketPrice().compareTo(after.getTicketPrice()) != 0) sb.append("price ")
                .append(before.getTicketPrice()).append(" -> ").append(after.getTicketPrice()).append("; ");
        if (before.getCapacity() != after.getCapacity()) sb.append("capacity ")
                .append(before.getCapacity()).append(" -> ").append(after.getCapacity()).append("; ");
        if (!before.getVenueName().equals(after.getVenueName()) || !before.getCity().equals(after.getCity())) sb.append("venue; ");
        return sb.isEmpty() ? "details" : sb.substring(0, sb.length() - 2);
    }

    // ---------------------------------------------------------------- query-string parsing

    EventSearchCriteria parseCriteria(Map<String, String> p, boolean admin) {
        String text = TextUtils.truncate(TextUtils.clean(p.get("q")), MAX_SEARCH_LENGTH);
        Integer categoryId = parseInt(p.get("category"), "category");
        String city = TextUtils.truncate(TextUtils.clean(p.get("city")), 80);
        LocalDate from = parseDate(p.get("from"), "from");
        LocalDate to = parseDate(p.get("to"), "to");
        BigDecimal min = parsePrice(p.get("minPrice"), "minPrice");
        BigDecimal max = parsePrice(p.get("maxPrice"), "maxPrice");
        if (from != null && to != null && to.isBefore(from)) {
            throw ValidationException.forField("to", "The 'to' date must be on or after the 'from' date.");
        }
        if (min != null && max != null && max.compareTo(min) < 0) {
            throw ValidationException.forField("maxPrice", "The maximum price must be at least the minimum price.");
        }
        EventLifecycleFilter lifecycle = EventLifecycleFilter.parse(p.get("status"),
                admin ? EventLifecycleFilter.ALL : EventLifecycleFilter.UPCOMING);
        ArchiveFilter archive = admin ? ArchiveFilter.parse(p.get("archived")) : ArchiveFilter.EXCLUDE;
        boolean featured = "true".equalsIgnoreCase(p.get("featured"));
        EventSort sort = EventSort.parse(p.get("sort"));
        return new EventSearchCriteria(text, categoryId, city, from, to, min, max, lifecycle, archive, featured, sort);
    }

    /** Wrapper-class parsing: Integer.parseInt throws NumberFormatException for "abc". */
    static Integer parseInt(String value, String field) {
        if (TextUtils.isBlank(value)) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            throw ValidationException.forField(field, "'" + TextUtils.truncate(value, 20) + "' is not a valid number.");
        }
    }

    private static LocalDate parseDate(String value, String field) {
        if (TextUtils.isBlank(value)) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException e) {
            throw ValidationException.forField(field, "Dates must look like 2026-12-31.");
        }
    }

    private static BigDecimal parsePrice(String value, String field) {
        if (TextUtils.isBlank(value)) {
            return null;
        }
        try {
            BigDecimal price = new BigDecimal(value.trim());
            if (price.signum() < 0) {
                throw ValidationException.forField(field, "Prices cannot be negative.");
            }
            return price;
        } catch (NumberFormatException e) {
            throw ValidationException.forField(field, "Prices must be numbers.");
        }
    }
}
