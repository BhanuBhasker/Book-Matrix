# BookMatrix

BookMatrix is an event discovery and ticket booking platform built with **Java 21 (Spring Boot), plain JDBC and MySQL**, with a framework-free HTML/CSS/JavaScript frontend.

People can browse and search events, see live availability, book tickets, download a receipt and cancel bookings. Administrators manage events, inventory, users and bookings, import and export CSV files, take backups and see real statistics. A **Java Lab** page runs the Java syllabus demonstrations live in the browser.

---

## Features

**Visitors and members**
- Register, log in and log out (BCrypt passwords, session cookie, CSRF protection, login throttling), with Show/Hide password buttons and a live password checklist
- Home page with featured events, upcoming events, categories and search
- Event discovery: search by name, venue, city, category or description; filters for category, city, dates, price and status; one-click date shortcuts (Today, This weekend, Next 7 days, This month); removable filter chips; sorting and pagination
- Event pages with availability, status (upcoming, sold out, happening now, completed, cancelled) and booking eligibility
- **Seat selection**: reserved-seating events show a live seat map (stage, rows A–Z, centre aisle). Pick up to 10 seats, remove them from the chips, and booked seats are greyed out for everyone. The database makes double-booking a seat impossible.
- **Demo payment**: paid bookings hold your tickets or seats for 10 minutes while you pay by card or UPI through a built-in simulated gateway. It has a live card preview, a bank OTP step and a processing screen, and handles declines and retries. No real money moves.
- Booking flow with clear steps: Seats/Tickets → Review → Payment → a confirmation ticket with a unique reference (e.g. `BM7K2Q9XWD`). A sticky "Book tickets" bar appears on phones.
- Add to calendar (`.ics` for Google/Apple/Outlook), directions to the venue, copy-link sharing, and "Tomorrow" / "In 3 days" labels
- My bookings: search, tabs (all, upcoming, past, cancelled), details, `.txt` receipt download
- Cancellation up to 2 hours before the event, with an optional reason. Tickets go straight back on sale.
- Profile: edit name and phone, change password, close the account

**Administrators**
- Dashboard with real figures: revenue, refunds, tickets sold, inventory, bookings, events, users, best sellers, tickets by category, recent activity
- Event CRUD: create, edit (capacity can never drop below tickets sold), feature, cancel (refunds every booking), delete or archive, restore
- Inventory screen that cross-checks each event's counter against its confirmed bookings
- User management: search, filter, edit, activate/deactivate, change role, booking history (with last-admin and self-lockout protection)
- Booking management: search, filter, details, cancellation
- CSV import (validated row by row) and export, on-disk backups, audit log
- Java Lab: runs the 12 syllabus demonstrations from the browser

---

## Technology stack

| Layer | Choice |
|---|---|
| Language | Java 21 language level (runs on JDK 21 or newer; developed on JDK 26/27) |
| Web | Spring Boot 3.5 (embedded Tomcat, REST controllers) |
| Database access | **Plain JDBC** (`PreparedStatement`, `ResultSet`, manual transactions) through a HikariCP connection pool |
| Database | MySQL 8.0.16+ (CHECK constraints are required) |
| Passwords | BCrypt (`spring-security-crypto` only) |
| Frontend | HTML, CSS and JavaScript ES modules (no framework, no build step) |
| Tests | JUnit 5, Spring MockMvc, AssertJ, Playwright (real Chrome) |
| Build | Maven |

---

## Prerequisites (macOS)

```bash
java -version      # 21 or newer
mvn -v             # Maven 3.9+
mysql --version    # MySQL 8.0.16+ running locally
node -v            # Node 18+ (only needed for the browser tests)
```

With Homebrew: `brew install openjdk maven mysql node`, then `brew services start mysql`.

---

## Installation and database setup

1. **Create the databases and a dedicated MySQL user** (once). Edit the password inside the script first:
   ```bash
   mysql -u root -p < scripts/setup-database.sql
   ```
   This creates `bookmatrix_dev` (the app's data) and `bookmatrix_test` (wiped by the automated tests). You do **not** create tables yourself: on every start-up the app runs `src/main/resources/db/schema.sql`, whose statements are all `CREATE TABLE IF NOT EXISTS`, so it works on an empty database.

2. **Configure the connection.** Copy the example file and fill in your values:
   ```bash
   cp .env.example .env
   ```
   `.env` is git-ignored and is read automatically by Spring Boot. Settings:

   | Variable | Meaning |
   |---|---|
   | `DB_URL` | JDBC URL of the app database |
   | `DB_USERNAME` / `DB_PASSWORD` | the MySQL user from step 1 |
   | `TEST_DB_URL` | JDBC URL of the test database (must end in `_test`) |
   | `BOOKMATRIX_ADMIN_EMAIL` / `_PASSWORD` / `_NAME` | first administrator (see below) |
   | `PORT`, `COOKIE_SECURE`, `BACKUP_DIR` | optional |

### First administrator

Public registration can only ever create normal members. The first admin is created **from environment variables**:

1. Set `BOOKMATRIX_ADMIN_EMAIL` and `BOOKMATRIX_ADMIN_PASSWORD` in `.env` (at least 8 characters, with a letter and a number).
2. Start the app. If the database has **no** administrator yet, it creates one (or promotes that email if the account already exists) and logs `Created first administrator account …`. The password is never logged.
3. Once an admin exists these variables are ignored. Further admins are promoted from **Admin → Users → Make administrator**.

---

## Running

```bash
mvn spring-boot:run
# or:  mvn -DskipTests package && java -jar target/bookmatrix.jar
```

Open **http://localhost:8080**. Health check: `curl localhost:8080/api/health`.

**Sample events (optional):** log in as admin → **Import & export** → upload `data/sample-events.csv` (14 events, all with reserved seating so every event shows the seat map). The import goes through the same validation as the admin form, so this is real application data, not hard-coded content. If an event date has passed by the time you import, that row is reported as invalid and skipped.

### Demo payment: test details

The checkout uses a **simulated payment gateway** (`DemoPaymentGateway`). Nothing is charged, and only the card brand and last 4 digits are stored.

| Card number | Result |
|---|---|
| `4242 4242 4242 4242` | Visa, approved |
| `5555 5555 5555 4444` | Mastercard, approved |
| `4000 0000 0000 0002` | Declined by bank |
| `4000 0000 0000 9995` | Insufficient funds |
| `4000 0000 0000 0069` | Bank reports card expired |

Use any future expiry (e.g. `12/30`), any 3-digit CVV, and the bank OTP **`123456`**. For UPI, any ID like `name@bank` is approved and `fail@upi` is declined. The checkout page lists these too ("Test cards for this demo"), and clicking a number fills the form.

### Java syllabus demonstrations

```bash
mvn -q compile exec:java                                  # list demos
mvn -q compile exec:java -Dexec.args="all"                # run all 12
mvn -q compile exec:java -Dexec.args="race producer-consumer jdbc"
```
Or open **Admin → Java Lab** and click a demo. See [docs/SYLLABUS_MAPPING.md](docs/SYLLABUS_MAPPING.md).

---

## Tests

```bash
mvn test                       # 116 JUnit tests: unit, integration, API, concurrency (uses bookmatrix_test)

# real-browser tests (the app must be running on :8080)
cd e2e
npm install
npx playwright test            # 24 tests incl. seat selection + payment; uses your installed Google Chrome
npx playwright show-report     # HTML report
```
Integration tests refuse to run unless the database name ends with `_test`, so development data is never wiped. Browser tests create their own users and events, and a global teardown archives the test events. Results are recorded in [docs/TEST_REPORT.md](docs/TEST_REPORT.md), and screenshots are in `docs/screenshots/`.

---

## Project structure

```
BookMatrix/
├── pom.xml                         Maven build (Spring Boot, MySQL driver, BCrypt, exec plugin)
├── .env.example                    configuration template (.env itself is git-ignored)
├── data/sample-events.csv          sample events for the CSV import
├── scripts/setup-database.sql      one-time MySQL database + user setup
├── docs/                           all documentation + screenshots
├── e2e/                            Playwright browser tests
└── src/
    ├── main/java/com/bookmatrix/
    │   ├── BookMatrixApplication.java
    │   ├── config/        typed settings, web wiring (interceptors)
    │   ├── controller/    REST endpoints (public + admin/)
    │   ├── service/       business rules and transactions
    │   ├── dao/           SQL — one class per table group (plain JDBC)
    │   ├── db/            connection/transaction helper, schema initializer, generic DAO base
    │   ├── model/         domain classes (BaseEntity → User, Event, Booking) and enums
    │   ├── dto/           request/response records, search filters
    │   ├── security/      auth/CSRF/page interceptors, BCrypt, admin bootstrap
    │   ├── validation/    Validator, PasswordPolicy
    │   ├── exception/     custom exception hierarchy + global handler
    │   ├── fileio/        CSV parser/writer, import/export/backup
    │   ├── analytics/     Stream API analytics for the dashboard
    │   ├── util/          ApiResponse<T>, PageResult<T>, receipts, references, money
    │   └── demo/          executable Java syllabus demonstrations
    ├── main/resources/
    │   ├── application.properties
    │   ├── db/schema.sql, db/reference-data.sql
    │   └── static/        HTML pages, css/app.css, js/core.js, js/pages/*, js/admin/*
    └── test/java/com/bookmatrix/
        ├── unit/          no database
        ├── integration/   real MySQL + MockMvc
        └── support/       IntegrationTestBase
```

---

## Documentation

| Document | Contents |
|---|---|
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | design decisions, request flow, security model |
| [docs/DATABASE.md](docs/DATABASE.md) | tables, ER diagram, constraints, indexes, transactions |
| [docs/API.md](docs/API.md) | every endpoint with inputs, responses and errors |
| [docs/SYLLABUS_MAPPING.md](docs/SYLLABUS_MAPPING.md) | each Java topic → file, method, how to demo it |
| [docs/TEST_REPORT.md](docs/TEST_REPORT.md) | executed tests and results |
| [docs/PROJECT_SYNOPSIS.md](docs/PROJECT_SYNOPSIS.md) | synopsis for submission |
| [docs/VIVA_QUESTIONS.md](docs/VIVA_QUESTIONS.md) | likely viva questions with short answers |
| [docs/PROJECT_DEMO.md](docs/PROJECT_DEMO.md) | 3–5 minute demo script |

---

## Troubleshooting

| Symptom | Fix |
|---|---|
| Log shows "BookMatrix cannot use the MySQL database right now" and API calls return 503 | MySQL is stopped or `.env` is wrong. Start MySQL (`brew services start mysql`) and check `DB_URL`/`DB_USERNAME`/`DB_PASSWORD`. The app retries automatically, so no restart is needed. |
| `Access denied for user 'bookmatrix'` | Password in `.env` does not match `scripts/setup-database.sql`. |
| `Your password does not satisfy the current policy requirements` when running the setup script | MySQL's password policy is on. Use upper and lower case, a digit and a symbol. |
| Log warns "No administrator exists yet" | Set `BOOKMATRIX_ADMIN_EMAIL`/`BOOKMATRIX_ADMIN_PASSWORD` in `.env` and restart. |
| "Too many failed login attempts" | 5 wrong passwords lock that email for 5 minutes (or restart the app). |
| Port 8080 already in use | `PORT=8081 mvn spring-boot:run`, or stop the other process (`lsof -i :8080`). |
| Integration tests fail with "must only ever run against a *_test database" | `TEST_DB_URL` must point at a database whose name ends with `_test`. |
| Playwright cannot find Chrome | Install Google Chrome, or run `npx playwright install chromium` and then `PW_CHANNEL=chromium npx playwright test`. |
| CSV import says "not valid UTF-8" | In Excel use *Save As → CSV UTF-8*. |
