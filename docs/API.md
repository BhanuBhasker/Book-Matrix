# BookMatrix — REST API

Base URL `http://localhost:8080`. All bodies are JSON unless noted.

**Envelope:** success → `{"success": true, "data": ..., "message": "..."}`. Failure → `{"success": false, "error": {"code", "message", "fields"}}`.

**Authentication:** session cookie `BMSESSION` (set by `/api/auth/login`).

**CSRF:** every `POST`/`PUT`/`PATCH`/`DELETE` must send `X-CSRF-Token: <token>`. Get the token from `GET /api/auth/session` or from the login/logout responses. A missing or wrong token returns `403 CSRF_INVALID`.

**Common errors:** `400 VALIDATION_FAILED` (with `fields`), `400 MALFORMED_REQUEST`, `400 INVALID_PARAMETER`, `401 AUTH_REQUIRED`, `401 SESSION_EXPIRED`, `403 FORBIDDEN`, `404 *_NOT_FOUND`, `405 METHOD_NOT_ALLOWED`, `503 DATABASE_UNAVAILABLE`, `500 INTERNAL_ERROR`.

Access: **Public** = anyone · **Member** = logged in · **Admin** = role ADMIN.

## Authentication

| Method | Endpoint | Access | Body / params | Success | Errors |
|---|---|---|---|---|---|
| GET | `/api/auth/session` | Public | – | `200 {user\|null, csrfToken}` | – |
| POST | `/api/auth/register` | Public | `{fullName, email, password, confirmPassword}` | `201 UserView` (role is always USER) | 400 fields, 409 `EMAIL_TAKEN` |
| POST | `/api/auth/login` | Public | `{email, password}` | `200 {user, csrfToken}`; new session id | 400, 401 `INVALID_CREDENTIALS`, 401 `ACCOUNT_DEACTIVATED`, 429 `TOO_MANY_ATTEMPTS` |
| POST | `/api/auth/logout` | Public | – | `200 {user:null, csrfToken}` | – |
| GET | `/api/health` | Public | – | `200 {application, database}` | 503 when the DB is down |

## Profile (always the logged-in user; there is no id to tamper with)

| Method | Endpoint | Access | Body | Success | Errors |
|---|---|---|---|---|---|
| GET | `/api/me` | Member | – | `UserView` | 401 |
| PATCH | `/api/me` | Member | `{fullName, phone}` (other keys ignored) | `UserView` | 400 |
| PUT | `/api/me/password` | Member | `{currentPassword, newPassword, confirmPassword}` | message | 400 (wrong current, weak, mismatch, same as old) |
| DELETE | `/api/me` | Member | `{password, confirmation:"DELETE"}` | `{outcome: DELETED\|DEACTIVATED, bookingsCancelled}` | 400, 409 `LAST_ADMIN` |

`UserView` = `{id, fullName, email, phone, role, status, createdAt, lastLoginAt}`. It never contains a password or hash.

## Events (public discovery)

| Method | Endpoint | Access | Params | Success | Errors |
|---|---|---|---|---|---|
| GET | `/api/home` | Public | – | `{featured[], upcoming[], categories[], cities[], upcomingCount}` | – |
| GET | `/api/events` | Public | `q, category(id), city, from, to (YYYY-MM-DD), minPrice, maxPrice, status, featured, sort, page, size` | `PageResult<EventView>` | 400 bad number/date/range |
| GET | `/api/events/{id}` | Public | – | `EventView` | 404 (also for archived events) |
| GET | `/api/categories` | Public | – | `[{id, name, slug, description}]` | – |
| GET | `/api/cities` | Public | – | `["Pune", ...]` | – |

- `status`: `UPCOMING` (default; includes sold out), `AVAILABLE`, `SOLD_OUT`, `LIVE`, `COMPLETED`, `CANCELLED`, `ALL`.
- `sort`: `DATE_ASC` (default), `DATE_DESC`, `PRICE_ASC`, `PRICE_DESC`, `POPULAR`, `NEWEST`.
- Unknown `status`/`sort` values fall back to the default, and unknown parameters are ignored.
- `q` is truncated to 100 characters. `size` is capped at 100.

`EventView` = `{id, title, description, categoryId, categoryName, venueName, city, venueAddress, startsAt, endsAt, ticketPrice, capacity, ticketsSold, availableTickets, imageUrl, featured, status, displayStatus, displayStatusLabel, bookable, bookingBlockReason, maxPerBooking, archived, cancelledAt}`

`PageResult<T>` = `{items[], page, size, totalItems, totalPages}`

## Bookings (own bookings only)

| Method | Endpoint | Access | Body / params | Success | Errors |
|---|---|---|---|---|---|
| POST | `/api/bookings` | Member | `{eventId, quantity (1-10), requestKey?}` | `201 {booking, message, duplicate:false}`; a repeated `requestKey` returns `200` with the same booking and `duplicate:true` | 400 qty/fields, 404 `EVENT_NOT_FOUND`, 409 `INSUFFICIENT_TICKETS`, 409 `BOOKING_NOT_ALLOWED` (cancelled, started, finished) |
| GET | `/api/bookings` | Member | `q, status (ALL\|UPCOMING\|PAST\|CONFIRMED\|CANCELLED), page, size` | `PageResult<BookingView>` | 401 |
| GET | `/api/bookings/{ref}` | Member | – | `BookingView` | 404 (also for other people's bookings) |
| POST | `/api/bookings/{ref}/cancel` | Member | `{reason?}` | `BookingView` (CANCELLED) | 404, 409 already cancelled or within 2 h of start |
| GET | `/api/bookings/{ref}/receipt` | Member (admins: any) | – | `text/plain` attachment | 404 |

`BookingView` = `{id, reference, eventId, eventTitle, eventStartsAt, eventEndsAt, eventStatus, venueName, city, quantity, unitPrice, totalAmount, status, bookedAt, cancelledAt, cancelledByName, cancellationReason, cancellable, cancellationBlockReason, eventPast, userId*, userName*, userEmail*}` (*filled in admin responses only)

## Admin: dashboard and monitoring

| Method | Endpoint | Params | Returns |
|---|---|---|---|
| GET | `/api/admin/dashboard` | – | `{counts{total_users, active_users, admins, total_events, archived_events, cancelled_events, total_bookings, confirmed_bookings, cancelled_bookings, tickets_sold, upcoming_events, live_events, completed_events, sold_out_events, available_inventory, bookings_last_7_days}, revenue, refunded, averageBookingValue, topEvents[], ticketsByCategory{}, recentBookings[], recentActivity[], inconsistentEvents, generatedAt}` |
| GET | `/api/admin/inventory` | `includeArchived` | `[{eventId, title, capacity, ticketsSold, available, confirmedTickets, revenue, consistent, percentSold, ...}]` |
| GET | `/api/admin/activity` | `limit (≤200), entityType, entityId` | audit entries |

Revenue = sum of `total_amount` over CONFIRMED bookings (a booking counts as paid in full when confirmed, and cancellation refunds it in full).

## Admin: events

| Method | Endpoint | Body / params | Success | Errors |
|---|---|---|---|---|
| GET | `/api/admin/events` | public filters + `archived=EXCLUDE\|ONLY\|INCLUDE`; status defaults to ALL | `PageResult<EventView>` | 400 |
| GET | `/api/admin/events/{id}` | – | `EventView` (archived included) | 404 |
| POST | `/api/admin/events` | `EventForm` | `201 EventView` | 400 fields, 409 `DUPLICATE_EVENT` |
| PUT | `/api/admin/events/{id}` | `EventForm` | `EventView` | 400, 404, 409 `CAPACITY_BELOW_SOLD`, 409 `EVENT_LOCKED` (cancelled/completed) |
| PATCH | `/api/admin/events/{id}/featured` | `{featured: true\|false}` | `EventView` | 400, 404 |
| POST | `/api/admin/events/{id}/cancel` | `{reason?}` | `{event, bookingsCancelled, message}`; every booking refunded | 404, 409 already cancelled or completed |
| DELETE | `/api/admin/events/{id}` | – | `{outcome: DELETED\|ARCHIVED}` | 404, 409 `HAS_ACTIVE_BOOKINGS` (cancel first), 409 already archived |
| POST | `/api/admin/events/{id}/restore` | – | `EventView` | 409 not archived |
| GET | `/api/admin/events/{id}/bookings` | `q, status, page, size` | `PageResult<BookingView>` | 404 |
| POST | `/api/admin/categories` | `{name, description?}` | `201 Category` | 400, 409 duplicate |

`EventForm` = `{title (3-150), description (10-5000), categoryId, venueName, city, venueAddress?, startsAt, endsAt ("2027-01-15T19:30"), ticketPrice (0-1,000,000, ≤2 decimals), capacity (1-100,000), imageUrl? (http(s) or /images/...), featured?}`. The start must be in the future when set or changed, the end must be after the start, and an event lasts at most 30 days.

## Admin: users

| Method | Endpoint | Body / params | Success | Errors |
|---|---|---|---|---|
| GET | `/api/admin/users` | `q, role, status, page, size` | `PageResult<{user, bookingCount, ticketsHeld, totalSpent}>` | – |
| GET | `/api/admin/users/{id}` | – | `UserView` | 404 |
| PATCH | `/api/admin/users/{id}` | `{fullName, phone}` | `UserView` | 400, 404 |
| POST | `/api/admin/users/{id}/activate` | – | `UserView` | 403 self, 409 `NO_CHANGE` |
| POST | `/api/admin/users/{id}/deactivate` | – | `UserView`; their open sessions stop working immediately | 403 self, 409 `NO_CHANGE`, 409 `LAST_ADMIN` |
| PUT | `/api/admin/users/{id}/role` | `{role: USER\|ADMIN}` | `UserView` | 400, 403 self, 409 `NO_CHANGE`, 409 `LAST_ADMIN` |
| GET | `/api/admin/users/{id}/bookings` | `status, page, size` | `PageResult<BookingView>` | 404 |

## Admin: bookings

| Method | Endpoint | Body / params | Success | Errors |
|---|---|---|---|---|
| GET | `/api/admin/bookings` | `q` (reference, customer, email, event), `status`, `eventId`, `page`, `size` | `PageResult<BookingView>` | – |
| GET | `/api/admin/bookings/{ref}` | – | `BookingView` | 404 |
| POST | `/api/admin/bookings/{ref}/cancel` | `{reason?}` | `BookingView`; same transaction rules as the customer path, allowed until the event ends | 404, 409 |

## Admin: data and Java Lab

| Method | Endpoint | Body | Success | Errors |
|---|---|---|---|---|
| GET | `/api/admin/data/events.csv` | – | `text/csv` download | – |
| GET | `/api/admin/data/bookings.csv` | – | `text/csv` download | – |
| POST | `/api/admin/data/import/events` | `multipart/form-data`, field `file` (.csv, UTF-8, ≤1 MB, ≤1000 rows) | `{rowsRead, imported, skipped, errors[{line, message}]}` | 400 empty, wrong extension, malformed, missing columns, header only, not UTF-8; 413 too large |
| POST | `/api/admin/data/backup` | – | `{folder, files[{name, bytes}], createdAt}` | – |
| GET | `/api/admin/data/backups` | – | `["backup-20261125-001500", ...]` | – |
| GET | `/api/admin/demos` | – | `[{name, title, topics}]` | – |
| POST | `/api/admin/demos/{name}/run` | – | `{name, title, output, millis, success}` | 404 |

## Example session (curl)

```bash
T=$(curl -s -c jar -b jar localhost:8080/api/auth/session | jq -r .data.csrfToken)
T=$(curl -s -c jar -b jar -X POST localhost:8080/api/auth/login -H "X-CSRF-Token: $T" \
      -H 'Content-Type: application/json' -d '{"email":"you@example.com","password":"secret123"}' | jq -r .data.csrfToken)
curl -s -b jar -X POST localhost:8080/api/bookings -H "X-CSRF-Token: $T" \
      -H 'Content-Type: application/json' -d '{"eventId":1,"quantity":2,"requestKey":"my-unique-key-1"}'
```
