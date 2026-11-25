# Viva preparation — questions and short answers

## Project and architecture
**Q: What is BookMatrix?**
A web app to discover events and book tickets. Users book and cancel; admins manage events, users, bookings and see statistics. Data is in MySQL; the backend is Java/Spring Boot.

**Q: Describe the architecture.**
Layers: browser → controllers (HTTP) → services (rules + transactions) → DAOs (SQL) → MySQL. Each layer has one job, which keeps the code easy to test and change.

**Q: Why a monolith and not microservices?**
One small team, one database, moderate traffic. Microservices would add network calls and deployment work with no benefit here.

**Q: What does Spring Boot do for you?**
It starts an embedded Tomcat server, routes URLs to controller methods, converts JSON to and from Java objects, creates objects and wires them together (dependency injection), and reads configuration. All the business logic and SQL are ours.

**Q: What is dependency injection?**
Objects don't create their own dependencies; Spring passes them in through the constructor (e.g. `BookingService(Database db, BookingDao bookingDao, ...)`). This makes classes loosely coupled and testable.

## Frontend
**Q: Why no React or Angular?**
The pages are simple forms and lists, so plain HTML, CSS and JavaScript modules are enough. There is no build step and less to learn.

**Q: How does the frontend talk to the backend?**
`fetch()` calls to JSON endpoints in `js/core.js` (`api()` function). It adds the CSRF header, handles timeouts and redirects to login on 401.

**Q: How do you prevent XSS?**
Every dynamic value is inserted through the `html\`\`` template helper, which HTML-escapes it. A title like `<script>` shows as text and is never executed.

**Q: How do you stop double bookings from double clicks?**
The confirm button is disabled while the request runs (`withBusy`), and each booking attempt carries a `requestKey`. The database has a UNIQUE (user_id, request_key) constraint, so a repeat returns the same booking.

## Java and OOP
**Q: Where is inheritance used?**
`User`, `Event` and `Booking` extend the abstract `BaseEntity`. Exceptions extend `BookMatrixException`. All DAOs extend `JdbcDao`.

**Q: Example of polymorphism?**
`GlobalExceptionHandler` catches `BookMatrixException` and calls `status()`. Each subclass (`EventNotFoundException`, `InsufficientTicketsException` …) returns its own HTTP status, so one method handles all of them.

**Q: Abstract class vs interface?**
An abstract class can have fields and implemented methods (e.g. `BaseEntity` has `id`). An interface is a contract (e.g. `Dao<T,ID>`, `RowMapper<T>`). A class can extend one class but implement many interfaces.

**Q: What is encapsulation in your code?**
Fields are private. Available tickets is computed by `getAvailableTickets()`, so it can't be set to a wrong value directly.

**Q: What does `static` mean?**
It belongs to the class, not to objects, e.g. `BookingReferenceGenerator.next()` or the shared counter in `OopDemo.Ticket`.

**Q: Why records?**
`Category`, `Venue` and all DTOs are records: short, immutable data classes with automatic constructor, getters, `equals` and `toString`.

## Strings, arrays, wrappers
**Q: `==` vs `equals()` for strings?**
`==` checks whether two references point to the same object; `equals()` checks the characters. Use `equals()`.

**Q: Why StringBuilder for receipts?**
Strings are immutable, so `+` in a loop creates many objects. StringBuilder appends into one mutable buffer. StringBuffer is the synchronized (thread-safe) version.

**Q: Where are wrapper classes useful?**
Request fields like `Integer quantity` can be `null` (missing), which a primitive `int` can't represent. Collections can only hold objects, so values are autoboxed.

**Q: Where is a 2-D array used?**
`SeatMap` stores a theatre layout as `char[rows][cols]`, with booking, searching for adjacent seats, display and index validation.

## Collections
**Q: Why HashSet in the CSV import?**
To detect duplicate rows quickly: `add()` returns false if the key is already there, in O(1).

**Q: Why LinkedHashMap for validation errors?**
It keeps insertion order, so errors appear in the same order as the form fields.

**Q: Comparable vs Comparator?**
Comparable defines the natural order inside the class (`Event.compareTo` → by date). A Comparator is a separate ordering you pass in (best sellers by tickets sold).

**Q: Why use an Iterator to remove items?**
Removing from a list inside a for-each loop throws ConcurrentModificationException; `iterator.remove()` is safe.

## Exceptions
**Q: Checked vs unchecked?**
Checked (e.g. `SQLException`, our `CsvFormatException`) must be caught or declared with `throws`. Unchecked (RuntimeException, e.g. `InsufficientTicketsException`) need not be.

**Q: Why convert SQLException to your own exceptions?**
So services don't depend on JDBC details, and the client gets a safe message (e.g. 503 "database unavailable") instead of SQL internals.

**Q: What does try-with-resources do?**
Anything that is AutoCloseable (Connection, PreparedStatement, ResultSet, file streams) is closed automatically, even if an exception occurs, which prevents leaks.

## JDBC and SQL
**Q: Explain the JDBC architecture.**
Our code uses the JDBC API interfaces → DriverManager or DataSource → the MySQL driver (Connector/J, Type 4, pure Java) → the MySQL server.

**Q: Statement vs PreparedStatement?**
PreparedStatement is precompiled with `?` placeholders; values are bound separately, which prevents SQL injection and handles quoting. We use Statement only for fixed SQL like the schema script.

**Q: How do transactions work in your booking?**
`setAutoCommit(false)` → lock the event row → update inventory → insert booking → `commit()`. If anything fails, `rollback()`, so there is never a booking without inventory change or vice versa.

**Q: executeQuery vs executeUpdate?**
`executeQuery` returns a ResultSet (SELECT). `executeUpdate` returns the number of rows changed (INSERT/UPDATE/DELETE). We use that count: 0 means "not enough tickets".

**Q: What is a connection pool?**
A set of open connections that are reused. `close()` returns the connection to the pool instead of closing it. Opening a new MySQL connection each time is slow.

## Database
**Q: Why a separate venues table?**
Normalisation: venue name, city and address are stored once and shared by many events (one-to-many).

**Q: How are users and events related?**
Many-to-many through `bookings`, which also stores quantity, price at booking time and status.

**Q: Why store `unit_price` in bookings?**
If the admin changes the event price later, old bookings must still show what the customer actually paid.

**Q: How is overselling prevented?**
Three layers in MySQL: a row lock (`SELECT … FOR UPDATE`), an atomic conditional update (`… WHERE tickets_sold + qty <= capacity`) and a CHECK constraint (`tickets_sold <= capacity`).

**Q: Why not delete events or users?**
Bookings reference them. Deleting would break history, so we archive events and deactivate users. Rows without any bookings are really deleted.

## Authentication and security
**Q: How are passwords stored?**
As BCrypt hashes (salted, slow). We compare with `BCryptPasswordEncoder.matches()`. The plain password is never stored or logged.

**Q: Session or token?**
A server session (HttpOnly cookie `BMSESSION`, 30-minute timeout). On login the old session is destroyed and a new id issued, which prevents session fixation.

**Q: What is CSRF and how do you stop it?**
A malicious site can make your browser send requests with your cookie. Every state-changing request must carry the secret token from the session in a header, which other sites can't read.

**Q: How do you stop a user from becoming admin?**
Roles are checked on the server for every `/api/admin` request, request objects have no `role` field, and only an admin can change roles. An admin can't demote themselves or the last admin.

**Q: Can a user see someone else's booking?**
No. Booking lookups check that the owner equals the session user; otherwise the API answers 404 so it doesn't even reveal that the booking exists.

## Multithreading and synchronization
**Q: Thread vs process?**
A process is a running program with its own memory; threads run inside a process and share its memory.

**Q: start() vs run()?**
`start()` creates a new thread that then calls `run()`. Calling `run()` directly just runs the method in the current thread.

**Q: What is a race condition? Show one.**
When the result depends on timing. In `UnsafeCounter`, two threads both see "1 ticket left" and both book it, giving -1 tickets. The Java Lab "race" demo shows this.

**Q: How does `synchronized` fix it?**
Only one thread at a time can hold the object's intrinsic lock, so the check and the decrement happen together (a critical section).

**Q: Why isn't `synchronized` enough for the real app?**
It only protects one JVM's memory. With several servers or direct database writes it doesn't help, so the real booking relies on database transactions and locks.

**Q: What does volatile do?**
It guarantees visibility: a write by one thread is immediately seen by others. It doesn't make compound actions atomic.

**Q: Thread states?**
NEW → RUNNABLE → (BLOCKED / WAITING / TIMED_WAITING) → TERMINATED. Our threads demo observes all six.

## Producer-consumer
**Q: Explain your producer-consumer.**
Producers put booking requests into a bounded queue; consumers take and process them. If the queue is full, producers `wait()`; if empty, consumers `wait()`. After changes we `notifyAll()`.

**Q: Why `while` and not `if` around wait()?**
After waking up, the condition must be checked again: another thread may have taken the item, or a spurious wake-up may have happened.

**Q: How do threads stop cleanly?**
`close()` sets a flag and calls `notifyAll()`; consumers drain the queue and then `take()` returns null, so they exit.

## Streams, lambdas, generics
**Q: What is a lambda?**
A short anonymous function, e.g. `conn -> userDao.findById(conn, id)`. It implements a functional interface (one abstract method).

**Q: What is lazy evaluation in streams?**
Intermediate operations (`filter`, `map`) don't run until a terminal operation (`toList`, `count`) is called. The demo shows this with `peek`.

**Q: Where do you use streams for real?**
`EventAnalytics`: best sellers (`sorted` + `limit`), tickets per category (`groupingBy` into a TreeMap), and mapping entities to views.

**Q: Why generics?**
Type safety without casts: `PageResult<EventView>`, `ApiResponse<T>`, `Dao<T, ID>`. `<T extends Number>` restricts T to numbers; `? super Integer` lets us add integers to a list of a parent type.

## File handling
**Q: Byte stream vs character stream?**
Byte streams (`FileInputStream`/`FileOutputStream`) handle raw bytes such as images. Character streams (`FileReader`/`FileWriter`) handle text with an encoding.

**Q: How does your CSV parser handle commas inside values?**
Values with commas, quotes or newlines are wrapped in quotes, and quotes inside are doubled. The parser reads character by character and tracks whether it is inside quotes.

**Q: How is imported data validated?**
Every row goes through the same validator as the admin form (types, ranges, dates, category). Duplicates are checked within the file (HashSet) and against the database. Only valid rows are saved, in one transaction; invalid rows are listed with line numbers.

**Q: What is StreamTokenizer?**
A class that splits a character stream into tokens such as words and numbers (`TT_WORD`, `TT_NUMBER`), shown in the files demo.

## Testing
**Q: How did you test the project?**
145 JUnit tests (unit tests for rules, CSV, streams, generics and concurrency demos; integration tests with real MySQL through MockMvc covering every API, permission and edge case; concurrency tests with real threads) plus 24 Playwright tests in a real Chrome browser for the full user and admin journeys, negative cases, responsive layouts and usability helpers.

**Q: How did you test concurrency?**
30 threads, released together by a CountDownLatch, try to book a 10-ticket event. The test asserts exactly 10 succeed, 20 are rejected and the database shows 10 sold. Similar tests cover the last ticket, retries with the same key and simultaneous cancellations.

**Q: How do you keep tests from destroying real data?**
They use a separate `bookmatrix_test` database, and the base class refuses to run unless the database name ends with `_test`.
