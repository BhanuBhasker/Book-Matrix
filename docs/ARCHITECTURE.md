# BookMatrix — Architecture

## 1. Overview

BookMatrix is a single Spring Boot application (a **monolith**). It serves the static website and a JSON REST API from one process. MySQL is the only external dependency. Microservices, Docker and message queues would add complexity without benefit at this scale.

```
 Browser (HTML + CSS + JS modules)
      │  fetch() JSON, session cookie BMSESSION, X-CSRF-Token header
      ▼
 ┌──────────────────────────── Spring Boot (embedded Tomcat) ─────────────────────────────┐
 │ Interceptors: CsrfInterceptor → DatabaseReadyInterceptor → AuthInterceptor              │
 │               PageAccessInterceptor (protects /admin/*, /bookings.html, /profile.html)  │
 │ Controllers  (controller/, controller/admin/)  – HTTP in/out, no business logic          │
 │ Services     (service/, fileio/)               – rules, validation, transactions         │
 │ DAOs         (dao/)                            – hand-written SQL with PreparedStatement  │
 │ Database     (db/Database)                     – opens connections, commit/rollback       │
 └───────────────────────────────────────────────┬────────────────────────────────────────┘
                                                 │ JDBC (MySQL Connector/J, HikariCP pool)
                                                 ▼
                                              MySQL 8
```

## 2. Key decisions

| Decision | Why |
|---|---|
| **Plain JDBC instead of JPA/Hibernate** | JDBC is in the syllabus, and plain JDBC shows every concept (Connection, PreparedStatement, ResultSet, commit/rollback) in the real booking flow. Hibernate would hide all of it. |
| **HikariCP pool** (via `spring-boot-starter-jdbc`) | Opening a MySQL connection per request is slow. The pool hands out ready connections, and `close()` returns them. All SQL is still ours. `JdbcDemo` shows the raw `DriverManager` way. |
| **Session cookie + CSRF token** (not JWT) | The site and API share one origin, so a server-side `HttpSession` is the simplest secure choice. It is HttpOnly, SameSite=Lax and expires after 30 minutes. Logout simply invalidates the session. |
| **Only `spring-security-crypto`** (BCrypt), not full Spring Security | Authentication fits in two small interceptors that are easy to read and explain. |
| **Framework-free frontend** | Plain HTML/CSS/ES modules: no build step, nothing to install, and easy to explain. A tagged-template helper `html\`...\`` escapes every value to prevent XSS. |
| **Two seating types: GENERAL and RESERVED** | General-admission events sell "N tickets". Reserved events have a seat map (rows A–Z × up to 30 seats) stored in `event_seats`; a seat is claimed with `UPDATE … SET booking_id = ? WHERE booking_id IS NULL`, so it can belong to only one booking. Arrays are also demonstrated by `SeatMap` (a 2-D array) in the demo module. |
| **Demo payment gateway behind an interface** | `PaymentGateway` is an interface; `DemoPaymentGateway` simulates a bank (test cards, declines, UPI). Paid bookings are held as `PENDING_PAYMENT` for 10 minutes and released automatically if unpaid. A real provider (Razorpay/Stripe) could replace the demo class without touching the booking code. |
| **Derived event status** | Only `SCHEDULED` / `CANCELLED` are stored. "Upcoming / Sold out / Live / Completed" are calculated from dates and inventory, so the data can never contradict itself. |
| **Soft delete (archive)** for events with bookings | Booking history must survive. Events without any booking are really deleted. |
| **Deactivate, not delete**, users with bookings | Same reason: bookings must keep a valid `user_id`. |

## 3. Request lifecycle (example: booking tickets)

1. `event.js` sends `POST /api/bookings {eventId, quantity, requestKey}` with the `X-CSRF-Token` header.
2. `CsrfInterceptor` compares the header with the token in the session → 403 if it is wrong.
3. `DatabaseReadyInterceptor` makes sure the schema exists → 503 if MySQL is down.
4. `AuthInterceptor` loads the user from MySQL → 401 if not logged in or deactivated.
5. `BookingController.book()` calls `BookingService.book()`.
6. `BookingService` validates the input, then **in one transaction**:
   `SELECT … FOR UPDATE` (lock the event row) → check for a duplicate `requestKey` → check the business rules → atomic `UPDATE events SET tickets_sold = tickets_sold + ? WHERE … AND tickets_sold + ? <= capacity` → `INSERT INTO bookings` → `INSERT INTO audit_log` → `commit()`. Any exception → `rollback()`.
7. `GlobalExceptionHandler` turns any exception into `{success:false, error:{code, message, fields}}` with the right HTTP status.

## 4. Security model

| Threat | Protection |
|---|---|
| Password theft | BCrypt (salted, slow). Hashes never leave the server: response records have no hash field. |
| Brute force | 5 failures → 5-minute lock per email (`LoginAttemptTracker`). The same error message is used for unknown email and wrong password, and the hash check runs either way so timing doesn't reveal which emails exist. |
| Session fixation | Old session invalidated and a new id issued on login; CSRF token rotated. |
| CSRF | Synchroniser token in the session plus the `X-CSRF-Token` header on every POST/PUT/PATCH/DELETE; SameSite=Lax cookie. |
| XSS | Every dynamic value is HTML-escaped by `html\`\``. Admin image URLs are restricted to http(s) or `/images/`. |
| SQL injection | 100% `PreparedStatement` parameters. Sort orders and filters are enums mapped to fixed SQL. `LIKE` wildcards are escaped. |
| Privilege escalation | Roles are checked on the server for every `/api/admin/**` call. Request records have no `role` field. Admins cannot demote or deactivate themselves or remove the last active admin. |
| IDOR (reading others' data) | `/api/me` and `/api/bookings` use the session user. Someone else's booking returns 404, the same as a missing one. |
| Information leaks | Stack traces and SQL errors are logged on the server only. Clients get stable codes and friendly messages. |
| Spreadsheet formula injection | CSV export prefixes cells starting with `= + - @`. |
| Open redirect | The login `next` parameter must be a same-site path. |

## 5. Error format

```json
{ "success": false,
  "error": { "code": "VALIDATION_FAILED", "message": "Please fix the 2 highlighted fields.",
             "fields": { "email": "Enter a valid email address.", "password": "Use at least 8 characters." } } }
```

Status codes: 400 validation or malformed input, 401 not logged in or session expired, 403 forbidden or CSRF, 404 not found, 405, 409 business-rule conflict, 413 file too large, 429 too many logins, 500 unexpected, 503 database unavailable.

## 6. Frontend structure

- `static/*.html`: one small HTML shell per page.
- `js/core.js`: API client (CSRF, timeouts, retry on expired token, redirect on 401), safe HTML templating, layout (header/footer), toasts, an accessible `<dialog>` confirmation, form helpers (`handleForm`, `withBusy` to block double submits), formatters, shared renderers (event rows, posters, pagination).
- `js/pages/*.js`: public and member pages. `js/admin/*.js`: admin pages (`shell.js` draws the sidebar).
- `css/app.css`: the whole design system (paper/ink palette, one accent, serif display type).
