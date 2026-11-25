# BookMatrix — Test report

All results below come from **real executions** on 2026-11-25 00:37 (macOS, JDK 27, MySQL 8.0.46, Google Chrome via Playwright 1.65). This file was generated from `target/surefire-reports/*.xml` and `e2e/test-results/results.json`.

## Update: 2026-11-25 (UI redesign, seat selection, demo payment)

After the UI redesign and the new checkout (card preview, bank OTP, UPI approval, ticket-style confirmation), the **browser suite was run again: 24 / 24 passed** in real Chrome. That includes the new `06-seats-and-payment.spec.js`: pick seats on the map → pay by UPI → the seats show as booked for another user → a wrong OTP is rejected → releasing a hold frees the seat. The main user journey now also pays through checkout, with a declined card and then an approved one.

The JUnit suite was **re-run on 2026-11-25 after the update: 145 / 145 passed, 0 failures, 0 errors** (including `PaymentAndSeatApiTest` 14 tests and `PaymentGatewayTest` 13 tests). The latest UI polish (animations, new category artwork, poster thumbnails) was verified by re-running the full browser suite: 24 / 24 passed.

## Summary

| Suite | Command | Tests | Passed | Failed | Not run |
|---|---|---|---|---|---|
| JUnit (unit, integration, API, concurrency) | `mvn clean test` | 145 | 145 | 0 | 0 |
| Browser (Playwright, real Chrome) | `cd e2e && npx playwright test` | 24 | 24 | 0 | 0 |

Before the final JUnit run, every table in `bookmatrix_test` was **dropped**. The suite then recreated the schema from `schema.sql`, which proves the application starts from a clean database.

Each row's expected result is that all assertions in the test hold (the assertions are in the test source). "Actual" is what happened in this run.

## 1. JUnit tests

| # | Test class | Test | Type | Expected | Actual | Result |
|---|---|---|---|---|---|---|
| 1 | `AccountApiTest` | Closing account with bookings deactivates and releases upcoming tickets | Integration / API | All assertions hold | Completed in 0.05s | **PASS** |
| 2 | `AccountApiTest` | Only admin cannot close their account | Integration / API | All assertions hold | Completed in 0.02s | **PASS** |
| 3 | `AccountApiTest` | Password change | Integration / API | All assertions hold | Completed in 0.04s | **PASS** |
| 4 | `AccountApiTest` | Profile update and validation | Integration / API | All assertions hold | Completed in 0.02s | **PASS** |
| 5 | `AccountApiTest` | Closing account without bookings deletes it | Integration / API | All assertions hold | Completed in 0.03s | **PASS** |
| 6 | `AdminUserApiTest` | List search and details never expose passwords | Integration / API | All assertions hold | Completed in 0.09s | **PASS** |
| 7 | `AdminUserApiTest` | Admins cannot lock themselves out or remove the last admin | Integration / API | All assertions hold | Completed in 0.04s | **PASS** |
| 8 | `AdminUserApiTest` | Last active admin is protected | Integration / API | All assertions hold | Completed in 0.04s | **PASS** |
| 9 | `AdminUserApiTest` | Deactivation takes effect immediately | Integration / API | All assertions hold | Completed in 0.04s | **PASS** |
| 10 | `AdminUserApiTest` | User booking history for admins | Integration / API | All assertions hold | Completed in 0.03s | **PASS** |
| 11 | `AuthApiTest` | Session endpoint and logout | Integration / API | All assertions hold | Completed in 0.02s | **PASS** |
| 12 | `AuthApiTest` | Expired session is reported | Integration / API | All assertions hold | Completed in 0.01s | **PASS** |
| 13 | `AuthApiTest` | Registration validation | Integration / API | All assertions hold | Completed in 0.01s | **PASS** |
| 14 | `AuthApiTest` | Login rotates session and rejects bad credentials | Integration / API | All assertions hold | Completed in 0.01s | **PASS** |
| 15 | `AuthApiTest` | Duplicate email is rejected | Integration / API | All assertions hold | Completed in 0.01s | **PASS** |
| 16 | `AuthApiTest` | Malformed json gives clean error | Integration / API | All assertions hold | Completed in 0.01s | **PASS** |
| 17 | `AuthApiTest` | State changing requests need the csrf token | Integration / API | All assertions hold | Completed in 0.01s | **PASS** |
| 18 | `AuthApiTest` | Repeated failures lock the account temporarily | Integration / API | All assertions hold | Completed in 0.02s | **PASS** |
| 19 | `AuthApiTest` | Deactivated user cannot log in | Integration / API | All assertions hold | Completed in 0.01s | **PASS** |
| 20 | `AuthApiTest` | Register creates normal user with hashed password | Integration / API | All assertions hold | Completed in 0.01s | **PASS** |
| 21 | `BookingApiTest` | Sold out started and completed events cannot be booked | Integration / API | All assertions hold | Completed in 0.64s | **PASS** |
| 22 | `BookingApiTest` | Booking search and pagination | Integration / API | All assertions hold | Completed in 0.10s | **PASS** |
| 23 | `BookingApiTest` | Cannot cancel inside the cutoff window | Integration / API | All assertions hold | Completed in 0.06s | **PASS** |
| 24 | `BookingApiTest` | Duplicate submission returns the same booking | Integration / API | All assertions hold | Completed in 0.04s | **PASS** |
| 25 | `BookingApiTest` | Users only see their own bookings | Integration / API | All assertions hold | Completed in 0.04s | **PASS** |
| 26 | `BookingApiTest` | Cancellation restores inventory exactly once | Integration / API | All assertions hold | Completed in 0.04s | **PASS** |
| 27 | `BookingApiTest` | Price changes do not rewrite history | Integration / API | All assertions hold | Completed in 0.06s | **PASS** |
| 28 | `BookingApiTest` | Booking reduces inventory and records price | Integration / API | All assertions hold | Completed in 0.04s | **PASS** |
| 29 | `BookingApiTest` | Invalid quantities and requests | Integration / API | All assertions hold | Completed in 0.03s | **PASS** |
| 30 | `ConcurrencyIntegrationTest` | Database check constraint is the final safety net | Concurrency | All assertions hold | Completed in 0.02s | **PASS** |
| 31 | `ConcurrencyIntegrationTest` | Same request key sent ten times creates one booking | Concurrency | All assertions hold | Completed in 0.03s | **PASS** |
| 32 | `ConcurrencyIntegrationTest` | Simultaneous cancellations restore inventory once | Concurrency | All assertions hold | Completed in 0.02s | **PASS** |
| 33 | `ConcurrencyIntegrationTest` | The last ticket goes to exactly one of two users | Concurrency | All assertions hold | Completed in 0.06s | **PASS** |
| 34 | `ConcurrencyIntegrationTest` | Thirty users race for ten tickets | Concurrency | All assertions hold | Completed in 0.12s | **PASS** |
| 35 | `ConcurrencyIntegrationTest` | Mixed quantities never oversell | Concurrency | All assertions hold | Completed in 0.09s | **PASS** |
| 36 | `DataAndDashboardApiTest` | Java lab runs demos from the browser | Integration / API | All assertions hold | Completed in 0.03s | **PASS** |
| 37 | `DataAndDashboardApiTest` | Exports are valid csv | Integration / API | All assertions hold | Completed in 0.03s | **PASS** |
| 38 | `DataAndDashboardApiTest` | Jdbc demo runs against my sql | Integration / API | All assertions hold | Completed in 0.02s | **PASS** |
| 39 | `DataAndDashboardApiTest` | Import validates every row | Integration / API | All assertions hold | Completed in 0.04s | **PASS** |
| 40 | `DataAndDashboardApiTest` | Import rejects bad files | Integration / API | All assertions hold | Completed in 0.02s | **PASS** |
| 41 | `DataAndDashboardApiTest` | Dashboard reflects real data | Integration / API | All assertions hold | Completed in 0.07s | **PASS** |
| 42 | `DataAndDashboardApiTest` | Backup writes files to disk | Integration / API | All assertions hold | Completed in 0.03s | **PASS** |
| 43 | `DatabaseUnavailableTest` | Static pages still load | Integration / API | All assertions hold | Completed in 0.02s | **PASS** |
| 44 | `DatabaseUnavailableTest` | Health reports database down | Integration / API | All assertions hold | Completed in 0.01s | **PASS** |
| 45 | `DatabaseUnavailableTest` | Api calls get aclear503 without internals | Integration / API | All assertions hold | Completed in 0.02s | **PASS** |
| 46 | `EventApiTest` | Empty listing works | Integration / API | All assertions hold | Completed in 0.02s | **PASS** |
| 47 | `EventApiTest` | Featured toggle shows on home page | Integration / API | All assertions hold | Completed in 0.04s | **PASS** |
| 48 | `EventApiTest` | Event detail shows availability and eligibility | Integration / API | All assertions hold | Completed in 0.02s | **PASS** |
| 49 | `EventApiTest` | Admin can add categories | Integration / API | All assertions hold | Completed in 0.03s | **PASS** |
| 50 | `EventApiTest` | Cancelling an event refunds all bookings and resets inventory | Integration / API | All assertions hold | Completed in 0.06s | **PASS** |
| 51 | `EventApiTest` | Delete archives when there is history | Integration / API | All assertions hold | Completed in 0.05s | **PASS** |
| 52 | `EventApiTest` | Capacity cannot drop below tickets sold | Integration / API | All assertions hold | Completed in 0.03s | **PASS** |
| 53 | `EventApiTest` | Hostile or invalid search input is handled | Integration / API | All assertions hold | Completed in 0.02s | **PASS** |
| 54 | `EventApiTest` | Admin creates updates and validates events | Integration / API | All assertions hold | Completed in 0.04s | **PASS** |
| 55 | `EventApiTest` | Search filter sort and paginate | Integration / API | All assertions hold | Completed in 0.03s | **PASS** |
| 56 | `PermissionsTest` | Anonymous users get401 on protected apis | Integration / API | All assertions hold | Completed in 0.01s | **PASS** |
| 57 | `PermissionsTest` | Users cannot promote themselves through the profile | Integration / API | All assertions hold | Completed in 0.02s | **PASS** |
| 58 | `PermissionsTest` | Normal users get403 on every admin api | Integration / API | All assertions hold | Completed in 0.02s | **PASS** |
| 59 | `PermissionsTest` | Protected pages redirect | Integration / API | All assertions hold | Completed in 0.02s | **PASS** |
| 60 | `PermissionsTest` | Unknown paths return404 | Integration / API | All assertions hold | Completed in 0.01s | **PASS** |
| 61 | `PermissionsTest` | Admins can use admin apis | Integration / API | All assertions hold | Completed in 0.02s | **PASS** |
| 62 | `AnalyticsAndGenericsTest` | Upcoming filters past events and sorts by date | Unit | All assertions hold | Completed in 0.00s | **PASS** |
| 63 | `AnalyticsAndGenericsTest` | Generic methods bounds and wildcards | Unit | All assertions hold | Completed in 0.00s | **PASS** |
| 64 | `AnalyticsAndGenericsTest` | Top selling revenue and consistency | Unit | All assertions hold | Completed in 0.00s | **PASS** |
| 65 | `AnalyticsAndGenericsTest` | Grouping distinct high priced average and paging | Unit | All assertions hold | Completed in 0.01s | **PASS** |
| 66 | `ConcurrencyDemoTest` | Safe counters never oversell | Concurrency | All assertions hold | Completed in 0.03s | **PASS** |
| 67 | `ConcurrencyDemoTest` | Safe counters never oversell | Concurrency | All assertions hold | Completed in 0.02s | **PASS** |
| 68 | `ConcurrencyDemoTest` | Safe counters never oversell | Concurrency | All assertions hold | Completed in 0.02s | **PASS** |
| 69 | `ConcurrencyDemoTest` | Safe counters never oversell | Concurrency | All assertions hold | Completed in 0.02s | **PASS** |
| 70 | `ConcurrencyDemoTest` | Safe counters never oversell | Concurrency | All assertions hold | Completed in 0.02s | **PASS** |
| 71 | `ConcurrencyDemoTest` | Producer consumer processes everything and terminates | Concurrency | All assertions hold | Completed in 0.01s | **PASS** |
| 72 | `ConcurrencyDemoTest` | Login tracker locks after five failures | Concurrency | All assertions hold | Completed in 0.00s | **PASS** |
| 73 | `ConcurrencyDemoTest` | Seat map array operations | Concurrency | All assertions hold | Completed in 0.01s | **PASS** |
| 74 | `ConcurrencyDemoTest` | Closed queue wakes waiting consumers | Concurrency | All assertions hold | Completed in 0.06s | **PASS** |
| 75 | `ConcurrencyDemoTest` | Unsafe counter oversells under arace | Concurrency | All assertions hold | Completed in 0.04s | **PASS** |
| 76 | `CsvTest` | Last line without newline is kept | Unit | All assertions hold | Completed in 0.00s | **PASS** |
| 77 | `CsvTest` | Rejects broken quoting | Unit | All assertions hold | Completed in 0.00s | **PASS** |
| 78 | `CsvTest` | Parses commas quotes newlines and empty values | Unit | All assertions hold | Completed in 0.00s | **PASS** |
| 79 | `CsvTest` | Writer neutralises spreadsheet formulas | Unit | All assertions hold | Completed in 0.00s | **PASS** |
| 80 | `CsvTest` | Writer quotes only when needed and round trips | Unit | All assertions hold | Completed in 0.00s | **PASS** |
| 81 | `DemosRunTest` | Every demo runs | Unit | All assertions hold | Completed in 0.01s | **PASS** |
| 82 | `DemosRunTest` | Every demo runs | Unit | All assertions hold | Completed in 0.00s | **PASS** |
| 83 | `DemosRunTest` | Every demo runs | Unit | All assertions hold | Completed in 0.01s | **PASS** |
| 84 | `DemosRunTest` | Every demo runs | Unit | All assertions hold | Completed in 0.00s | **PASS** |
| 85 | `DemosRunTest` | Every demo runs | Unit | All assertions hold | Completed in 0.00s | **PASS** |
| 86 | `DemosRunTest` | Every demo runs | Unit | All assertions hold | Completed in 0.95s | **PASS** |
| 87 | `DemosRunTest` | Every demo runs | Unit | All assertions hold | Completed in 0.27s | **PASS** |
| 88 | `DemosRunTest` | Every demo runs | Unit | All assertions hold | Completed in 0.04s | **PASS** |
| 89 | `DemosRunTest` | Every demo runs | Unit | All assertions hold | Completed in 0.01s | **PASS** |
| 90 | `DemosRunTest` | Every demo runs | Unit | All assertions hold | Completed in 0.00s | **PASS** |
| 91 | `DemosRunTest` | Every demo runs | Unit | All assertions hold | Completed in 0.01s | **PASS** |
| 92 | `DomainRulesTest` | Natural order is by start time | Unit | All assertions hold | Completed in 0.00s | **PASS** |
| 93 | `DomainRulesTest` | Sold out live completed cancelled archived | Unit | All assertions hold | Completed in 0.00s | **PASS** |
| 94 | `DomainRulesTest` | Upcoming event is bookable | Unit | All assertions hold | Completed in 0.00s | **PASS** |
| 95 | `DomainRulesTest` | Cancelled wins over sold out and completed | Unit | All assertions hold | Completed in 0.00s | **PASS** |
| 96 | `DomainRulesTest` | Booking total uses big decimal | Unit | All assertions hold | Completed in 0.00s | **PASS** |
| 97 | `DomainRulesTest` | Started event cannot be booked | Unit | All assertions hold | Completed in 0.00s | **PASS** |
| 98 | `DomainRulesTest` | Cancellation policy | Unit | All assertions hold | Completed in 0.00s | **PASS** |
| 99 | `ExceptionsTest` | Sql exceptions are translated safely | Unit | All assertions hold | Completed in 0.04s | **PASS** |
| 100 | `ExceptionsTest` | Csv format exception is checked | Unit | All assertions hold | Completed in 0.00s | **PASS** |
| 101 | `ExceptionsTest` | Custom hierarchy and polymorphic status | Unit | All assertions hold | Completed in 0.01s | **PASS** |
| 102 | `UtilitiesTest` | Receipt contains the important facts | Unit | All assertions hold | Completed in 0.03s | **PASS** |
| 103 | `UtilitiesTest` | Money formatting | Unit | All assertions hold | Completed in 0.00s | **PASS** |
| 104 | `UtilitiesTest` | Booking references are well formed and unique | Unit | All assertions hold | Completed in 0.07s | **PASS** |
| 105 | `UtilitiesTest` | Text helpers | Unit | All assertions hold | Completed in 0.00s | **PASS** |
| 106 | `UtilitiesTest` | Page request clamps bad values | Unit | All assertions hold | Completed in 0.00s | **PASS** |
| 107 | `UtilitiesTest` | Page result is generic | Unit | All assertions hold | Completed in 0.00s | **PASS** |
| 108 | `ValidationTest` | Money rules | Unit | All assertions hold | Completed in 0.00s | **PASS** |
| 109 | `ValidationTest` | Collects every field error in form order | Unit | All assertions hold | Completed in 0.01s | **PASS** |
| 110 | `ValidationTest` | Rejects invalid emails(string) | Unit | All assertions hold | Completed in 0.01s | **PASS** |
| 111 | `ValidationTest` | Rejects invalid emails(string) | Unit | All assertions hold | Completed in 0.00s | **PASS** |
| 112 | `ValidationTest` | Rejects invalid emails(string) | Unit | All assertions hold | Completed in 0.00s | **PASS** |
| 113 | `ValidationTest` | Rejects invalid emails(string) | Unit | All assertions hold | Completed in 0.00s | **PASS** |
| 114 | `ValidationTest` | Rejects invalid emails(string) | Unit | All assertions hold | Completed in 0.00s | **PASS** |
| 115 | `ValidationTest` | Password policy | Unit | All assertions hold | Completed in 0.00s | **PASS** |
| 116 | `ValidationTest` | Valid input passes | Unit | All assertions hold | Completed in 0.00s | **PASS** |

### What the JUnit suites cover

| Class | Covers |
|---|---|
| `ValidationTest` | Validator rules (required, length, email, phone, money), password policy |
| `DomainRulesTest` | event status model (upcoming, sold out, live, completed, cancelled, archived), booking eligibility, BigDecimal totals, cancellation policy, natural ordering |
| `UtilitiesTest` | 20 000 unique booking references, reference validation, pagination clamping, generic `PageResult.map`, text helpers, money formatting, receipt content |
| `CsvTest` | commas, quotes, newlines, CRLF, empty values, unclosed quotes, stray quotes, writer round trip, formula-injection guard |
| `AnalyticsAndGenericsTest` | every stream pipeline in `EventAnalytics`; generic, bounded and wildcard methods |
| `ExceptionsTest` | custom hierarchy, polymorphic `status()`/`code()`, checked vs unchecked, safe SQL error translation |
| `ConcurrencyDemoTest` | unsafe counter oversells; synchronized method, block and atomic versions never oversell (repeated 5×); producer-consumer finishes and terminates; closing wakes waiters; 2-D seat map; login lockout |
| `DemosRunTest` | all 11 non-database syllabus demos run and print their key results |
| `AuthApiTest` | registration (role injection ignored, hashing, duplicates, field errors, malformed JSON), login, session-id rotation, wrong password, unknown email, lockout (429), deactivated user, logout, CSRF required, expired session |
| `PermissionsTest` | anonymous → 401 and member → 403 on every admin API; profile can't change role; admin access works; protected HTML pages redirect; JSON and HTML 404s |
| `EventApiTest` | empty listing, search (title/venue), filters (city/category/date/price/status), sort, pagination, SQL-injection and wildcard input, oversized input, unknown params, bad filters, 404s; admin create/update/validation, duplicates, capacity below sold, event cancellation refunding all bookings, delete vs archive vs restore, featured, categories |
| `BookingApiTest` | booking maths and inventory, price history, zero/negative/excessive/non-numeric quantity, missing event, insufficient tickets, sold out, started, finished, duplicate request key, other users' bookings (404), cancellation restores inventory once, double cancel, 2-hour cutoff, admin override, search and pagination, receipt |
| `ConcurrencyIntegrationTest` | **30 threads vs 10 tickets → exactly 10 bookings**, mixed quantities never oversell, last ticket goes to exactly one of two users (5 rounds), 10 identical retries → 1 booking, 10 simultaneous cancellations → inventory restored once, CHECK constraint blocks direct overselling |
| `AdminUserApiTest` | search, filters, no password data in responses, edits, deactivation takes effect on open sessions, duplicate operations (409), self-lockout prevention, last-admin protection, booking history |
| `AccountApiTest` | profile update and validation, password change (wrong current, weak, reused), account deletion without bookings, deactivation with bookings (tickets released, history kept), last admin can't close |
| `DataAndDashboardApiTest` | CSV import with per-row errors (duplicate, bad type, unknown category, past date, missing columns) and re-import duplicates; bad files (extension, empty, malformed, header-only, missing columns); CSV export quoting; backups on disk without hashes; dashboard figures match the data; Java Lab API; JDBC demo against MySQL |
| `DatabaseUnavailableTest` | with MySQL unreachable: app starts, `/api/health` and APIs return a clear 503 without connection details, static pages still load |

## 2. Browser tests (Playwright, real Google Chrome)

| # | Spec | Test | Type | Expected | Actual | Result |
|---|---|---|---|---|---|---|
| 1 | `01-user-flow.spec.js` | user journey: register → book → cancel → profile → logout | End-to-end flow | All steps and assertions pass | Completed in 4.3s | **PASS** |
| 2 | `02-admin-flow.spec.js` | admin journey: dashboard → event CRUD → users → bookings → inventory → archive | End-to-end flow | All steps and assertions pass | Completed in 6.2s | **PASS** |
| 3 | `03-negative.spec.js` | invalid registration, duplicate email and empty forms show inline errors | Negative | All steps and assertions pass | Completed in 0.8s | **PASS** |
| 4 | `03-negative.spec.js` | wrong password and empty login form | Negative | All steps and assertions pass | Completed in 0.5s | **PASS** |
| 5 | `03-negative.spec.js` | booking limits, stale availability and sold-out events | Negative | All steps and assertions pass | Completed in 1.3s | **PASS** |
| 6 | `03-negative.spec.js` | another user's booking, invalid ids and double cancellation | Negative | All steps and assertions pass | Completed in 1.9s | **PASS** |
| 7 | `03-negative.spec.js` | expired session sends the user back to login | Negative | All steps and assertions pass | Completed in 0.7s | **PASS** |
| 8 | `03-negative.spec.js` | API unavailable shows a friendly error instead of a blank page | Negative | All steps and assertions pass | Completed in 0.5s | **PASS** |
| 9 | `03-negative.spec.js` | broken event images fall back to the typographic poster | Negative | All steps and assertions pass | Completed in 0.3s | **PASS** |
| 10 | `04-responsive-visual.spec.js` | public pages fit on desktop | Visual / responsive | All steps and assertions pass | Completed in 3.6s | **PASS** |
| 11 | `04-responsive-visual.spec.js` | admin pages fit on desktop | Visual / responsive | All steps and assertions pass | Completed in 5.0s | **PASS** |
| 12 | `04-responsive-visual.spec.js` | public pages fit on laptop | Visual / responsive | All steps and assertions pass | Completed in 3.7s | **PASS** |
| 13 | `04-responsive-visual.spec.js` | admin pages fit on laptop | Visual / responsive | All steps and assertions pass | Completed in 4.9s | **PASS** |
| 14 | `04-responsive-visual.spec.js` | public pages fit on tablet | Visual / responsive | All steps and assertions pass | Completed in 3.6s | **PASS** |
| 15 | `04-responsive-visual.spec.js` | admin pages fit on tablet | Visual / responsive | All steps and assertions pass | Completed in 5.0s | **PASS** |
| 16 | `04-responsive-visual.spec.js` | public pages fit on mobile | Visual / responsive | All steps and assertions pass | Completed in 3.6s | **PASS** |
| 17 | `04-responsive-visual.spec.js` | admin pages fit on mobile | Visual / responsive | All steps and assertions pass | Completed in 5.0s | **PASS** |
| 18 | `04-responsive-visual.spec.js` | mobile navigation menu opens and is keyboard accessible | Visual / responsive | All steps and assertions pass | Completed in 0.5s | **PASS** |
| 19 | `04-responsive-visual.spec.js` | accessibility basics: labels, alt text, skip link, dialog focus | Visual / responsive | All steps and assertions pass | Completed in 0.4s | **PASS** |
| 20 | `05-usability.spec.js` | quick date shortcuts and removable filter chips | Usability | All steps and assertions pass | Completed in 0.5s | **PASS** |
| 21 | `05-usability.spec.js` | password fields can be revealed and the checklist guides the user | Usability | All steps and assertions pass | Completed in 0.4s | **PASS** |
| 22 | `05-usability.spec.js` | events in the next few days get a friendly "Tomorrow" label | Usability | All steps and assertions pass | Completed in 0.5s | **PASS** |
| 23 | `05-usability.spec.js` | mobile: sticky "Book tickets" bar, calendar file and copy link | Usability | All steps and assertions pass | Completed in 1.6s | **PASS** |

**User flow** (`01-user-flow`, all 24 steps of the brief): home page loads with real events and no console errors or horizontal overflow → register → registration confirmed → log in → browse → search → filters (status, sort, max price, kept in the URL) → event detail → choose 3 tickets → review → confirm → reference shown, availability 20→17 → My bookings lists it → details → cancel with a reason via the dialog → status Cancelled → availability back to 20 → profile update → password change → log out → `/bookings.html`, `/profile.html` and `/admin` redirect to login → the new password works and login returns to the requested page.

**Admin flow** (`02-admin-flow`, all 20 steps): admin login → dashboard figures equal the API values → create event via the form → verify → edit price and capacity → verify → search and status filters → a customer books, and the booking shows on the event page → search users → user details → booking history → deactivate (customer login refused with `ACCOUNT_DEACTIVATED`) → reactivate → find the booking → cancel it with a reason → inventory page "All OK" → cancel the event → archive (typed DELETE confirmation) → public API 404 → CSV export contains the event → Java Lab race demo shows OVERSOLD vs correct versions → a normal user is bounced from `/admin` and the API answers 403.

**Negative tests** (`03-negative`): empty, invalid and duplicate registration; wrong password and empty login; quantity stepper clamps −4 and 50; **stale page: another user buys tickets while the page is open, so the server rejects and the UI refreshes availability**; sold-out page has no booking button; someone else's booking and malformed references show "not found"; invalid event ids; normal user blocked from admin; **double cancellation from two tabs → second gets "already cancelled", inventory restored once**; expired session → login with "session expired"; API unreachable → friendly error with Retry; broken images → typographic poster.

**Usability** (`05-usability`): quick date shortcuts ("Today", "This weekend", "Next 7 days", "This month") set the date filters and the URL; each active filter shows as a removable chip, plus "Clear all"; password Show/Hide toggles and a live checklist (length, letter + number, match); "Tomorrow"/"In 3 days" labels on soon events; on a 390×640 phone a sticky **Book tickets** bar appears, jumps to the booking panel and hides itself there; **Add to calendar** downloads a valid `.ics` file; **Copy link** confirms with a toast; the booking panel shows steps 1 Tickets → 2 Review → 3 Done and the confirmation offers Add to calendar.

**Responsive and visual QA** (`04-responsive-visual`): public and admin pages at 1440, 1280, 820 and 390 px wide with no horizontal overflow and no console errors; mobile menu works with the keyboard; every form field has a label; every image has alt text; the skip link is the first tab stop. Screenshots: `docs/screenshots/`.

## 3. Defects found by testing and fixed

| # | Found by | Defect | Fix | Re-test |
|---|---|---|---|---|
| 1 | Playwright user and admin flows | Clicking a search result right after typing re-ran the identical search on input blur, re-rendering the list mid-click so the click was lost | List pages skip a search whose parameters are unchanged (`lastKey`) | PASS |
| 2 | Playwright admin flow | Deactivate / role / cancel-event / delete / admin-cancel buttons did nothing after the confirm dialog: `event.currentTarget` is `null` after `await` | Capture the button before awaiting | PASS |
| 3 | Playwright negative test | A malformed booking reference with slashes got a non-JSON 400 from Tomcat, so a generic error was shown | Validate the reference format in the browser; show "Booking not found" | PASS |
| 4 | Playwright responsive test (admin, 390 px) | Dashboard tables widened `1fr` grid tracks, causing horizontal overflow | `minmax(0, 1fr)` grid tracks and scrollable cards | PASS |
| 5 | Visual review of screenshots | Event images were hidden under the typographic fallback (stacking order) | `position: relative; z-index: 1` on poster images | PASS |
| 6 | Visual review | Hero headline left "for." alone on its own line | Wider measure + `text-wrap: balance` | PASS |
| 7 | Visual review | Events from aborted test runs remained on the public site | Playwright `globalTeardown` cancels and archives all `E2E …` events after every run | PASS (0 left) |

Three JUnit assertions were themselves wrong on the first run (a 9-character description that the validator correctly rejected, a double-encoded `%20` in a MockMvc URL, and a test display name with digits). In each case the application behaved correctly and the test was fixed.

| 8 | UX review of mobile screenshots | On phones the price and Book button sat below the whole description | Sticky mobile "Book tickets" bar that hides when the booking panel is on screen | PASS |
| 9 | UX review | Date filter inputs overflowed the filter column on the events page | Stacked "From date" / "To date" fields with visible labels | PASS |
| 10 | UX review | Revenue of ₹0 was shown as "Free" on the dashboard | Separate `formatPrice` (ticket prices) and `formatMoney` (amounts) | PASS |

## 4. Not run / limitations (stated honestly)

| Item | Status | Notes |
|---|---|---|
| Firefox / Safari (WebKit) browsers | NOT RUN | Only Google Chrome was used. The code uses standard APIs (`<dialog>`, ES modules, `fetch`). |
| Real 30-minute session timeout | NOT RUN (simulated) | Expiry is simulated by sending an invalid session cookie; the timeout itself is configured as `server.servlet.session.timeout=30m`. |
| Stopping the actual MySQL server mid-run | NOT RUN (simulated) | `DatabaseUnavailableTest` points the app at an unreachable port instead; the recovery path (`ensureReady`, retried every 5 s) is the same. |
| Load beyond 30 concurrent users | NOT RUN | Concurrency correctness is proven by database locking; performance under heavy load was not measured. |
| HTTPS / `Secure` cookies | NOT RUN | Local development uses HTTP. Set `COOKIE_SECURE=true` behind HTTPS. |
