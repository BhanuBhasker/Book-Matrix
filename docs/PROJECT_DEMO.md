# 3–5 minute demo script

## Before the demo (5 minutes earlier)
1. Start MySQL, then the app: `mvn spring-boot:run`. Wait for `Started BookMatrixApplication`.
2. If the site has no events: log in as admin → **Import & export** → upload `data/sample-events.csv`.
3. Open two browser windows:
   - **Window A** (normal): `http://localhost:8080`
   - **Window B** (private/incognito): logged in as admin (email and password from your `.env`: `BOOKMATRIX_ADMIN_EMAIL` / `BOOKMATRIX_ADMIN_PASSWORD`).
4. Have a terminal ready in the project folder.

---

## 0:00 – 0:30 · Introduction (Window A, home page)
**Say:** "BookMatrix is an event booking platform built in Java with Spring Boot, plain JDBC and MySQL. Everything you see comes from the database. The architecture is layered: controllers, services, DAOs and MySQL."
**Show:** featured events, the "Coming up" list, the category chips with live counts.

## 0:30 – 1:15 · Discovery (Window A)
1. Click **Events**. Type `music` in Search, choose a **City**, set **Sort by → Price: low to high**.
2. **Say:** "Search and filters run as parameterised SQL. Sort options are enums mapped to fixed SQL, so injection isn't possible. Results are paginated."
3. Click an event. **Say:** "Status like *Upcoming* or *Sold out* is computed from dates and inventory, so it can never contradict the data."
**Java concepts:** PreparedStatement and parameter binding, enums, `Event implements Comparable`.

## 1:15 – 2:30 · Register, book, cancel (Window A)
1. **Create account** → register `demo1@example.com` / `demo1234` → log in.
2. Open an event → press **+** twice → **Review booking** → **Confirm booking**.
3. **Say:** "Confirming runs one database transaction: lock the event row with SELECT FOR UPDATE, an atomic UPDATE that only succeeds if `tickets_sold + 3 <= capacity`, insert the booking, then commit. If anything fails it rolls back. A CHECK constraint makes overselling impossible even with a bug."
4. Point at the reference (`BM…`) and the availability bar dropping by 3.
5. **View booking** → **Download receipt** (built with StringBuilder) → **Cancel booking** → give a reason → confirm.
6. **Say:** "Cancellation is guarded by `WHERE status='CONFIRMED'`, so tickets are released exactly once, even if the button is clicked twice."
**Java concepts:** JDBC transactions (commit/rollback), custom exceptions (`InsufficientTicketsException`), StringBuilder.

## 2:30 – 3:30 · Admin (Window B)
1. **Dashboard**: "every figure is a live SQL aggregate; best sellers and the category breakdown use the Stream API (`sorted`, `limit`, `groupingBy` into a TreeMap)."
2. **Events → New event**: fill in quickly and save. Then **Edit** and try capacity `0` → show the inline validation message.
3. **Inventory**: "'Sold' is the stored counter; 'From bookings' is recomputed from confirmed bookings. They must match, and they do."
4. **Users**: search for `demo1`, open it, show booking history and the **Deactivate** button (don't press it unless you want to show it).
**Java concepts:** Streams and lambdas, Collections (TreeMap, LinkedHashMap for validation errors), role-based access checked on the server.

## 3:30 – 4:30 · Java syllabus live (Window B → Java Lab)
1. Click **Race conditions and synchronization**. **Say:** "Two users try to buy the last ticket. Without a lock both succeed and we are oversold (-1). The synchronized method, synchronized block and AtomicInteger versions are all correct. The real app uses database locks, because `synchronized` only protects one JVM."
2. Click **Producer-consumer**: "a bounded queue with `wait()` and `notifyAll()` in guarded `while` loops; threads stop cleanly with `close()`."
3. (If asked) **Thread basics**: shows all six thread states; **JDBC step by step**: DriverManager, Statement vs PreparedStatement, commit and rollback.

## 4:30 – 5:00 · Security and testing (terminal)
1. In Window A, log in as the normal user and visit `/admin/index.html` → you are redirected away. **Say:** "Admin APIs return 403 for normal users; this is enforced on the server."
2. Terminal: `mvn test` (or show `docs/TEST_REPORT.md`). **Say:** "145 JUnit tests including 30 threads racing for 10 tickets against real MySQL, plus 24 Playwright tests in a real Chrome browser, including seat selection and payment."

### If something goes wrong
- A page shows "database unavailable": MySQL stopped. Run `brew services start mysql` (or start it from System Settings → MySQL); the app reconnects by itself.
- You forgot the admin password: it's `BOOKMATRIX_ADMIN_PASSWORD` in `.env`.
