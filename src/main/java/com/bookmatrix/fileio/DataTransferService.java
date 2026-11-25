package com.bookmatrix.fileio;

import com.bookmatrix.config.BookMatrixProperties;
import com.bookmatrix.dao.AuditLogDao;
import com.bookmatrix.dao.BookingDao;
import com.bookmatrix.dao.CategoryDao;
import com.bookmatrix.dao.EventDao;
import com.bookmatrix.dao.SeatDao;
import com.bookmatrix.dao.UserDao;
import com.bookmatrix.dao.VenueDao;
import com.bookmatrix.db.Database;
import com.bookmatrix.dto.ArchiveFilter;
import com.bookmatrix.dto.EventLifecycleFilter;
import com.bookmatrix.dto.EventSearchCriteria;
import com.bookmatrix.dto.EventSort;
import com.bookmatrix.dto.Requests;
import com.bookmatrix.dto.Views.BackupFile;
import com.bookmatrix.dto.Views.BackupReport;
import com.bookmatrix.dto.Views.ImportReport;
import com.bookmatrix.dto.Views.ImportRowError;
import com.bookmatrix.exception.BookMatrixException;
import com.bookmatrix.exception.CsvFormatException;
import com.bookmatrix.exception.ValidationException;
import com.bookmatrix.model.Booking;
import com.bookmatrix.model.Category;
import com.bookmatrix.model.Event;
import com.bookmatrix.model.UserWithStats;
import com.bookmatrix.model.Venue;
import com.bookmatrix.security.SessionUser;
import com.bookmatrix.service.EventFormValidator;
import com.bookmatrix.util.PageRequest;
import com.bookmatrix.util.TextUtils;
import com.bookmatrix.util.TimeProvider;
import org.springframework.stereotype.Service;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Real file handling used by the admin "Data" screen:
 *  - CSV export of events and bookings
 *  - CSV import of events, validated row by row BEFORE anything is saved
 *  - backups written to disk (character streams for CSV, a byte stream for the manifest)
 */
@Service
public class DataTransferService {

    public static final List<String> EVENT_COLUMNS = List.of("title", "description", "category", "venue", "city",
            "address", "starts_at", "ends_at", "price", "capacity", "image_url", "featured", "seating", "seat_rows", "seats_per_row");
    private static final Set<String> REQUIRED_COLUMNS = Set.of("title", "description", "category", "venue", "city",
            "starts_at", "ends_at", "price");
    private static final int MAX_ROWS = 1000;
    private static final DateTimeFormatter FOLDER_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final Database db;
    private final EventDao eventDao;
    private final BookingDao bookingDao;
    private final CategoryDao categoryDao;
    private final VenueDao venueDao;
    private final UserDao userDao;
    private final AuditLogDao auditLogDao;
    private final EventFormValidator formValidator;
    private final SeatDao seatDao;
    private final TimeProvider time;
    private final File backupRoot;

    public DataTransferService(Database db, EventDao eventDao, BookingDao bookingDao, CategoryDao categoryDao,
                               VenueDao venueDao, UserDao userDao, AuditLogDao auditLogDao,
                               EventFormValidator formValidator, SeatDao seatDao, TimeProvider time, BookMatrixProperties properties) {
        this.db = db;
        this.eventDao = eventDao;
        this.bookingDao = bookingDao;
        this.categoryDao = categoryDao;
        this.venueDao = venueDao;
        this.userDao = userDao;
        this.auditLogDao = auditLogDao;
        this.formValidator = formValidator;
        this.seatDao = seatDao;
        this.time = time;
        this.backupRoot = new File(properties.backupDir());
    }

    // ================================================================== export

    public String exportEventsCsv() {
        List<Event> events = loadAllEvents();
        StringWriter out = new StringWriter();
        try {
            writeEvents(out, events);
        } catch (IOException e) {
            throw new IllegalStateException("StringWriter cannot fail", e);
        }
        return out.toString();
    }

    public String exportBookingsCsv() {
        List<Booking> bookings = db.query(bookingDao::findAll);
        StringWriter out = new StringWriter();
        try {
            writeBookings(out, bookings);
        } catch (IOException e) {
            throw new IllegalStateException("StringWriter cannot fail", e);
        }
        return out.toString();
    }

    private void writeEvents(Writer out, List<Event> events) throws IOException {
        List<String> header = new ArrayList<>(EVENT_COLUMNS);
        header.addAll(List.of("tickets_sold", "status", "archived", "seating"));
        CsvWriter.writeRow(out, header);
        for (Event e : events) {
            CsvWriter.writeRow(out, Arrays.asList(e.getTitle(), e.getDescription(), e.getCategoryName(),
                    e.getVenueName(), e.getCity(), e.getVenueAddress(), e.getStartsAt(), e.getEndsAt(),
                    e.getTicketPrice(), e.getCapacity(), e.getImageUrl(), e.isFeatured(), e.getTicketsSold(),
                    e.getStatus(), e.isArchived(),
                    e.isReservedSeating() ? "RESERVED " + e.getSeatRows() + "x" + e.getSeatsPerRow() : "GENERAL"));
        }
    }

    private void writeBookings(Writer out, List<Booking> bookings) throws IOException {
        CsvWriter.writeRow(out, List.of("reference", "status", "booked_at", "customer", "email", "event", "event_starts_at",
                "quantity", "unit_price", "total_amount", "cancelled_at", "cancellation_reason"));
        for (Booking b : bookings) {
            CsvWriter.writeRow(out, Arrays.asList(b.getReference(), b.getStatus(), b.getBookedAt(), b.getUserName(),
                    b.getUserEmail(), b.getEventTitle(), b.getEventStartsAt(), b.getQuantity(), b.getUnitPrice(),
                    b.getTotalAmount(), b.getCancelledAt(), b.getCancellationReason()));
        }
    }

    private List<Event> loadAllEvents() {
        EventSearchCriteria all = new EventSearchCriteria(null, null, null, null, null, null, null,
                EventLifecycleFilter.ALL, ArchiveFilter.INCLUDE, false, EventSort.DATE_ASC);
        LocalDateTime now = time.now();
        return db.query(conn -> eventDao.search(conn, all, new PageRequest(1, 100_000), now));
    }

    // ================================================================== import

    /** Checks the upload itself: present, not empty, .csv extension, within size. */
    public static void checkUpload(String filename, long size, byte[] content) {
        if (content == null || size == 0) {
            throw ValidationException.forField("file", "The file is empty.");
        }
        if (filename == null || !filename.toLowerCase(Locale.ROOT).endsWith(".csv")) {
            throw ValidationException.forField("file", "Only .csv files can be imported.");
        }
        if (size > 1_048_576) {
            throw ValidationException.forField("file", "The file is larger than 1 MB.");
        }
    }

    public ImportReport importEvents(SessionUser admin, String csvText) {
        List<CsvParser.CsvRecord> records;
        try {
            String text = csvText.startsWith("﻿") ? csvText.substring(1) : csvText;  // drop a UTF-8 BOM
            records = CsvParser.parse(new StringReader(text));
        } catch (CsvFormatException e) {
            throw ValidationException.forField("file", "The CSV file is malformed. " + e.getMessage());
        } catch (IOException e) {
            throw ValidationException.forField("file", "The file could not be read.");
        }
        records = records.stream().filter(r -> !r.isBlank()).toList();
        if (records.isEmpty()) {
            throw ValidationException.forField("file", "The file has no rows.");
        }
        Map<String, Integer> columns = headerIndex(records.get(0));
        List<CsvParser.CsvRecord> rows = records.subList(1, records.size());
        if (rows.isEmpty()) {
            throw ValidationException.forField("file", "The file only has a header row.");
        }
        if (rows.size() > MAX_ROWS) {
            throw ValidationException.forField("file", "At most " + MAX_ROWS + " rows can be imported at once.");
        }

        LocalDateTime now = time.now();
        Map<String, Category> categories = new HashMap<>();
        db.query(categoryDao::findAll).forEach(c -> categories.put(c.name().toLowerCase(Locale.ROOT), c));

        List<ImportRowError> errors = new ArrayList<>();
        Map<Event, Integer> valid = new java.util.LinkedHashMap<>();   // keeps file order, remembers each row's line
        Set<String> seenInFile = new HashSet<>();             // HashSet: fast duplicate detection

        // Pass 1: validate every row, nothing is written yet
        for (CsvParser.CsvRecord row : rows) {
            try {
                Event event = toEvent(row, columns, categories, now);
                String key = (event.getTitle() + "|" + event.getVenueName() + "|" + event.getCity() + "|"
                        + event.getStartsAt()).toLowerCase(Locale.ROOT);
                if (!seenInFile.add(key)) {                    // add() returns false if already present
                    throw ValidationException.forField("row", "Duplicate of an earlier row in this file.");
                }
                valid.put(event, row.lineNumber());
            } catch (ValidationException e) {
                errors.add(new ImportRowError(row.lineNumber(), describe(e)));
            }
        }

        // Pass 2: save the valid rows in ONE transaction
        int imported = db.inTransaction(conn -> {
            int count = 0;
            for (Map.Entry<Event, Integer> entry : valid.entrySet()) {
                if (insertIfNew(conn, admin, entry.getKey(), now)) {
                    count++;
                } else {
                    errors.add(new ImportRowError(entry.getValue(), "This event already exists (same title, venue and start time)."));
                }
            }
            auditLogDao.insert(conn, admin.id(), "EVENTS_IMPORTED", "EVENT", null,
                    count + " imported, " + (rows.size() - count) + " skipped", now);
            return count;
        });
        errors.sort((a, b) -> Integer.compare(a.line(), b.line()));
        return new ImportReport(rows.size(), imported, rows.size() - imported, errors);
    }

    private boolean insertIfNew(Connection conn, SessionUser admin, Event event, LocalDateTime now) throws SQLException {
        Venue venue = venueDao.findOrCreate(conn, event.getVenueName(), event.getCity(), event.getVenueAddress(), now);
        if (eventDao.existsSlot(conn, event.getTitle(), venue.id(), event.getStartsAt(), null)) {
            return false;
        }
        event.setVenueId(venue.id());
        long id = eventDao.insert(conn, event, admin.id(), now);
        if (event.isReservedSeating()) {
            seatDao.generateSeats(conn, id, event.getSeatRows(), event.getSeatsPerRow());
        }
        return true;
    }

    private Map<String, Integer> headerIndex(CsvParser.CsvRecord header) {
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < header.fields().size(); i++) {
            index.put(header.fields().get(i).strip().toLowerCase(Locale.ROOT), i);
        }
        List<String> missing = REQUIRED_COLUMNS.stream().filter(c -> !index.containsKey(c)).sorted().toList();
        if (!missing.isEmpty()) {
            throw ValidationException.forField("file", "Missing required column(s): " + String.join(", ", missing)
                    + ". Expected header: " + String.join(",", EVENT_COLUMNS));
        }
        return index;
    }

    /** Converts one CSV row into an Event, re-using the same validation as the admin form. */
    private Event toEvent(CsvParser.CsvRecord row, Map<String, Integer> columns, Map<String, Category> categories,
                          LocalDateTime now) {
        int expected = columns.values().stream().mapToInt(Integer::intValue).max().orElse(0) + 1;
        if (row.fields().size() != expected) {
            throw ValidationException.forField("row", "Expected " + expected + " columns but found " + row.fields().size() + ".");
        }
        String categoryName = value(row, columns, "category");
        Category category = categoryName == null ? null : categories.get(categoryName.toLowerCase(Locale.ROOT));
        if (category == null) {
            throw ValidationException.forField("category", "Unknown category '" + TextUtils.truncate(categoryName, 30)
                    + "'. Use one of: " + String.join(", ", categories.values().stream().map(Category::name).sorted().toList()));
        }
        BigDecimal price;
        Integer capacity;
        try {
            String p = value(row, columns, "price");
            price = p == null ? null : new BigDecimal(p);
        } catch (NumberFormatException e) {
            throw ValidationException.forField("price", "Price must be a number.");
        }
        try {
            String c = value(row, columns, "capacity");
            capacity = c == null ? null : Integer.parseInt(c);
        } catch (NumberFormatException e) {
            throw ValidationException.forField("capacity", "Capacity must be a whole number.");
        }
        String featuredText = value(row, columns, "featured");
        Boolean featured = parseBoolean(featuredText);
        Integer seatRows;
        Integer seatsPerRow;
        try {
            String r = value(row, columns, "seat_rows");
            String n = value(row, columns, "seats_per_row");
            seatRows = r == null ? null : Integer.valueOf(r);
            seatsPerRow = n == null ? null : Integer.valueOf(n);
        } catch (NumberFormatException e) {
            throw ValidationException.forField("seat_rows", "seat_rows and seats_per_row must be whole numbers.");
        }

        Requests.EventForm form = new Requests.EventForm(value(row, columns, "title"), value(row, columns, "description"),
                category.id(), value(row, columns, "venue"), value(row, columns, "city"), value(row, columns, "address"),
                value(row, columns, "starts_at"), value(row, columns, "ends_at"), price, capacity,
                value(row, columns, "image_url"), featured, value(row, columns, "seating"), seatRows, seatsPerRow);
        Event event = formValidator.validate(form, null, now);
        event.setCategoryName(category.name());
        return event;
    }

    private static Boolean parseBoolean(String text) {
        if (text == null) {
            return Boolean.FALSE;
        }
        return switch (text.toLowerCase(Locale.ROOT)) {
            case "true", "yes", "1" -> Boolean.TRUE;
            case "false", "no", "0" -> Boolean.FALSE;
            default -> throw ValidationException.forField("featured", "featured must be true or false.");
        };
    }

    private static String value(CsvParser.CsvRecord row, Map<String, Integer> columns, String name) {
        Integer index = columns.get(name);
        if (index == null || index >= row.fields().size()) {
            return null;
        }
        return TextUtils.isBlank(row.fields().get(index)) ? null : row.fields().get(index).strip();
    }

    private static String describe(BookMatrixException e) {
        if (e instanceof ValidationException ve && !ve.getFieldErrors().isEmpty()) {
            List<String> parts = new ArrayList<>();
            ve.getFieldErrors().forEach((field, message) -> parts.add(field + ": " + message));
            return String.join("; ", parts);
        }
        return e.getMessage();
    }

    // ================================================================== backup

    /**
     * Writes a backup folder: events.csv, bookings.csv and users.csv (no password hashes) using
     * character streams (FileWriter + BufferedWriter), and manifest.txt using a byte stream
     * (FileOutputStream + BufferedOutputStream). try-with-resources closes every file.
     */
    public BackupReport backup(SessionUser admin) {
        LocalDateTime now = time.now();
        File folder = new File(backupRoot, "backup-" + now.format(FOLDER_STAMP));
        if (!folder.exists() && !folder.mkdirs()) {
            throw new IllegalStateException("Cannot create backup folder " + folder.getAbsolutePath());
        }
        List<Event> events = loadAllEvents();
        List<Booking> bookings = db.query(bookingDao::findAll);
        List<UserWithStats> users = db.query(conn -> userDao.search(conn, null, null, null, new PageRequest(1, 100_000)));
        try {
            List<BackupFile> files = new ArrayList<>();
            files.add(writeCsvFile(new File(folder, "events.csv"), w -> writeEvents(w, events)));
            files.add(writeCsvFile(new File(folder, "bookings.csv"), w -> writeBookings(w, bookings)));
            files.add(writeCsvFile(new File(folder, "users.csv"), w -> {
                CsvWriter.writeRow(w, List.of("id", "full_name", "email", "role", "status", "created_at", "bookings"));
                for (UserWithStats u : users) {
                    CsvWriter.writeRow(w, Arrays.asList(u.user().getId(), u.user().getFullName(), u.user().getEmail(),
                            u.user().getRole(), u.user().getStatus(), u.user().getCreatedAt(), u.bookingCount()));
                }
            }));
            files.add(writeManifest(new File(folder, "manifest.txt"), files, now));
            db.query(conn -> {
                auditLogDao.insert(conn, admin.id(), "BACKUP_CREATED", "SYSTEM", null, folder.getName(), now);
                return null;
            });
            return new BackupReport(folder.getPath(), files, now);
        } catch (IOException e) {
            throw new IllegalStateException("Backup failed while writing " + folder.getPath(), e);
        }
    }

    public List<String> listBackups() {
        String[] names = backupRoot.list((dir, name) -> name.startsWith("backup-"));
        if (names == null) {
            return List.of();
        }
        return Arrays.stream(names).sorted((a, b) -> b.compareTo(a)).toList();
    }

    @FunctionalInterface
    private interface CsvContent {
        void write(Writer writer) throws IOException;
    }

    private BackupFile writeCsvFile(File file, CsvContent content) throws IOException {
        try (Writer writer = new BufferedWriter(new FileWriter(file, StandardCharsets.UTF_8))) {
            content.write(writer);
        }
        return new BackupFile(file.getName(), file.length());
    }

    private BackupFile writeManifest(File file, List<BackupFile> files, LocalDateTime now) throws IOException {
        StringBuilder sb = new StringBuilder("BookMatrix backup\ncreated: ").append(now).append('\n');
        for (BackupFile f : files) {
            sb.append(String.format("%-14s %10d bytes%n", f.name(), f.bytes()));
        }
        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(file))) {
            out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        }
        return new BackupFile(file.getName(), file.length());
    }
}
