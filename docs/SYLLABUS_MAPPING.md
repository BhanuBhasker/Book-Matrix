# Java syllabus → BookMatrix code

Every topic below points to **real, executable code**. "Demo" tells you how to show it:

- **Lab `name`**: Admin → Java Lab → click the demo, or run `mvn -q compile exec:java -Dexec.args="name"`.
- **App**: a feature you can click in the running website.
- **Test**: `mvn test -Dtest=ClassName`.

Paths are relative to `src/main/java/com/bookmatrix/`.

---

## 1. OOP

| Topic | Class | Method / member | What the code does | Demo | Viva explanation |
|---|---|---|---|---|---|
| Classes | `model/User`, `model/Event`, `model/Booking` | – | Blueprints for the main things in the system | Lab `oop` | A class is a template; it defines the data (fields) and behaviour (methods) of its objects. |
| Objects | `demo/OopDemo` | `run()` | `new User(...)`, `new Event()`, `new Booking()` | Lab `oop` | An object is one instance of a class with its own field values. |
| Methods | `model/Event` | `getAvailableTickets()`, `getDisplayStatus(now)`, `isBookable(now)` | Behaviour lives next to the data it uses | Lab `oop` | Methods are the actions an object can perform. |
| Constructors | `model/User` | `User(String, String, String)` | Builds a new user with name, email, hash | Lab `oop` | A constructor initialises a new object; it has the class name and no return type. |
| `this` | `model/BaseEntity` | constructor `this.id = id` | Separates the field from the parameter with the same name | Lab `oop` | `this` refers to the current object. |
| `static` | `demo/OopDemo.Ticket` | `static int issued`, `issuedSoFar()`; also `util/BookingReferenceGenerator.next()` | One counter shared by all tickets; utility methods called without an object | Lab `oop` | Static members belong to the class, not to individual objects. |
| Encapsulation | all `model/*` classes | private fields + getters/setters | `tickets_sold` is only changed through code paths that keep it valid | Lab `oop` | Hide the data, expose safe methods. |
| Inheritance | `model/BaseEntity` → `User`, `Event`, `Booking`; `exception/BookMatrixException` → `NotFoundException` → `EventNotFoundException`; `db/JdbcDao` → every DAO | `extends` | Common id/createdAt and JDBC helpers are written once | Lab `oop`, `exceptions` | A subclass reuses and extends a parent class (IS-A relationship). |
| Polymorphism | `exception/GlobalExceptionHandler` | `handleBookMatrix(BookMatrixException)` | One handler calls `ex.status()`/`ex.code()`; each subclass answers differently | Lab `oop`; App: any error | The same call behaves differently depending on the real object type at run time. |
| Method overriding | `model/User.describe()`, `Event.describe()`, `Booking.describe()`; `BaseEntity.toString()`; each exception's `status()`/`code()` | `@Override` | Subclasses replace the parent's version | Lab `oop` | Overriding = same signature in the subclass, new body. |
| Abstraction | `model/BaseEntity` (abstract), `exception/BookMatrixException` (abstract) | `abstract String describe()` | Defines *what* subclasses must do, not *how* | Lab `oop` | Abstract classes can't be instantiated; they force subclasses to fill in details. |
| Interfaces | `db/Dao<T,ID>`, `db/RowMapper<T>`, `db/SqlFunction<T,R>`, `demo/ConceptDemo`, `demo/TicketCounters.TicketCounter`; `Event implements Comparable<Event>` | – | Contracts that many classes implement | Lab `oop`, `race` | An interface is a pure contract; a class can implement many. |
| Object modelling | `model/*`, `dto/Views` | – | Real-world entities (user, event, venue, booking) mapped to classes and tables | App | We model the problem domain as objects with relationships. |

## 2. Arrays and Strings

| Topic | Class | Method | What the code does | Demo | Viva explanation |
|---|---|---|---|---|---|
| Multidimensional arrays | `demo/SeatMap` | `char[][] seats`, constructor (init), `countFree()` (traversal), `findAdjacent(n)` (search), `book(r,c)` (update), `render()` (display), `validate()` (index check) | A theatre seat plan as a 2-D array | Lab `arrays`; Test `ConcurrencyDemoTest.seatMapArrayOperations` | `seats[row][col]`: an array of arrays. Jagged arrays have rows of different lengths. |
| String immutability | `demo/StringsDemo`; `util/TextUtils.clean()` | – | `toUpperCase()`/`strip()` return new strings | Lab `strings` | Strings can't change; every "change" creates a new String object. |
| String pool | `demo/StringsDemo` | – | Literals share one pooled object; `intern()` | Lab `strings` | The JVM keeps one copy of each literal to save memory. |
| `==` vs `equals()` | `demo/StringsDemo` | – | `==` compares references, `equals()` compares characters | Lab `strings` | Always compare strings with `equals()`. |
| String comparison | `model/Event.compareTo()` (`compareToIgnoreCase`); `StringsDemo` | – | Tie-break events by title | Lab `strings` | `compareTo` returns negative, zero or positive. |
| Searching | `util/BookingReferenceGenerator.isWellFormed()` (`indexOf`, `startsWith`); `StringsDemo` | – | Validates references character by character | Lab `strings` | `indexOf`, `contains`, `startsWith`, `endsWith`. |
| Manipulation | `util/TextUtils` | `clean()`, `normalizeEmail()`, `escapeLike()`, `slugify()`, `truncate()` | Tidies user input before validation and SQL | Test `UtilitiesTest` | `strip`, `replace`, `replaceAll`, `substring`, `toLowerCase`. |
| Formatting | `util/Money.format()`, `util/ReceiptBuilder.row()` | `String.format("%-18s%28s%n")` | Prices and aligned receipt columns | App: download a receipt | Format specifiers control width, alignment and decimals. |
| StringBuilder | `util/ReceiptBuilder.receipt()`/`confirmationMessage()`, `util/BookingReferenceGenerator.next()`, `db/DatabaseInitializer.runScript()` | `append`, `insert`, `setLength` | Builds receipts, booking references, SQL statements | App: receipt; Lab `strings` | Mutable and fast for building text in a loop (not thread-safe). |
| StringBuffer | `demo/StringsDemo` | two threads append to one `StringBuffer` | Always 2000 chars: its methods are synchronized | Lab `strings` | Like StringBuilder but thread-safe (slower). |
| Wrapper classes | `dto/Requests` (`Integer quantity`, `Long eventId`, `Boolean featured`); `util/PageRequest.of(Integer, Integer)`; `service/EventService.parseInt()` (`Integer.valueOf`); `fileio/CsvWriter.isNumber()` (`Double.parseDouble`); `fileio/DataTransferService.parseBoolean()`; `StringsDemo` | – | Nullable request values; parsing query strings and CSV cells | Lab `strings`; App: `/api/events?minPrice=abc` → 400 | Wrappers let primitives be null and live in collections. Autoboxing converts automatically. |
| Autoboxing / unboxing | `service/BookingService.book()` (`int quantity = request.quantity()`); `db/JdbcDao.nullableLong()` | – | `Integer` ↔ `int` conversions | Lab `strings` | Unboxing a null wrapper throws `NullPointerException`, so we validate first. |

## 3. Exception handling

| Topic | Class | Method | What the code does | Demo | Viva explanation |
|---|---|---|---|---|---|
| Checked exceptions | `exception/CsvFormatException` (extends `Exception`); `java.sql.SQLException` in every DAO | `CsvParser.parse() throws IOException, CsvFormatException` | Compiler forces callers to handle broken CSV or SQL failures | Lab `exceptions`, `files` | Checked = must be caught or declared. |
| Unchecked exceptions | `exception/BookMatrixException` (extends `RuntimeException`) and subclasses | – | Business errors flow up to the global handler | Lab `exceptions` | Unchecked = subclasses of RuntimeException; not forced by the compiler. |
| try / catch | `db/Database.query()` | `catch (SQLException e) { throw translate(e); }` | Converts low-level SQL errors | Test `ExceptionsTest` | `try` holds risky code; `catch` handles a specific failure. |
| finally | `db/Database.inTransaction()` | `finally { conn.setAutoCommit(previous); }` | Always restores the connection state | Lab `exceptions` | `finally` always runs, used for clean-up. |
| Multiple catch | `db/Database.inTransaction()` | `catch (SQLException \| RuntimeException e)` → rollback | One rollback path for both kinds | Lab `exceptions` | One catch block for several unrelated types. |
| throw | `service/BookingService.book()` | `throw new InsufficientTicketsException(...)` | Rejects a booking | App: book more than available | `throw` raises an exception object. |
| throws | every DAO method `throws SQLException` | – | Declares the checked exception to callers | – | `throws` in a method signature declares what it may raise. |
| Propagation | DAO → `SqlFunction` lambda → `Database` → service → controller → `GlobalExceptionHandler` | – | An exception thrown deep down travels up until caught | Lab `exceptions` (`levelA→B→C`) | Uncaught exceptions move up the call stack. |
| Custom exceptions | `exception/*` (12 classes: `EventNotFoundException`, `BookingNotFoundException`, `InsufficientTicketsException`, `InvalidBookingException`, `UnauthorizedActionException`, `ConflictException`, `ValidationException`, `DatabaseUnavailableException`, …) | `status()`, `code()` | Each maps to an HTTP status and code | Test `ExceptionsTest` | Our own exception classes describe business problems clearly. |
| try-with-resources | `db/JdbcDao.queryList()`, `db/Database.query()`, `fileio/DataTransferService.writeCsvFile()` | `try (PreparedStatement ps = ...)` | Statements, result sets, connections and files are always closed | Lab `exceptions` (close order) | Resources implementing `AutoCloseable` close automatically, in reverse order. |
| Debugging | `exception/GlobalExceptionHandler` logs full stack traces; `ExceptionsDemo` prints trace frames | `log.error(..., ex)` | Server logs show where an error came from; clients get a safe message | Lab `exceptions` | Read the stack trace top-down to find the failing line. |

## 4. Collections

| Topic | Class | Method | What the code does | Demo |
|---|---|---|---|---|
| ArrayList | `db/JdbcDao.queryList()` | `results.add(mapper.map(rs))` | Holds every query result | Lab `collections` |
| LinkedList | `demo/BookingRequestQueue` (`Queue<…> = new LinkedList<>()`), `CollectionsDemo` (waitlist) | `add`, `poll`, `addFirst` | FIFO queue of booking requests | Lab `collections`, `producer-consumer` |
| HashSet | `fileio/DataTransferService.importEvents()` | `seenInFile.add(key)` | Detects duplicate CSV rows in O(1) | App: import a CSV with a duplicate row |
| LinkedHashSet | `CollectionsDemo` | – | Unique "recently viewed", in order | Lab `collections` |
| TreeSet | `CollectionsDemo` | – | Sorted unique cities | Lab `collections` |
| HashMap | `service/EventService.home()` (category counts), `DataTransferService.headerIndex()` (CSV column → index) | `put`, `getOrDefault` | Fast lookups | App: home page category counts |
| LinkedHashMap | `validation/Validator.errors`, `dao/StatsDao.fixedTotals()` | `putIfAbsent` | Field errors keep form order; dashboard metrics keep order | App: submit an empty registration form |
| TreeMap | `analytics/EventAnalytics.ticketsByCategory()` | `groupingBy(..., TreeMap::new, ...)` | Tickets per category, sorted A→Z | App: Admin dashboard |
| Queue | `demo/BookingRequestQueue` | `put`/`take` | Producer-consumer buffer | Lab `producer-consumer` |
| Deque | `CollectionsDemo` (`ArrayDeque` as an undo stack and a queue) | `push`, `pop`, `offer`, `poll` | LIFO + FIFO | Lab `collections` |
| PriorityQueue | `CollectionsDemo` | `poll()` | VIP requests are served first | Lab `collections` |
| Iterator | `CollectionsDemo` | `it.remove()` | Safe removal while iterating | Lab `collections` |
| ListIterator | `CollectionsDemo` | `set`, `hasPrevious`, `previous` | Edit in place and walk backwards | Lab `collections` |
| Comparable | `model/Event implements Comparable<Event>` | `compareTo()` (by start time, then title) | Natural order for `Collections.sort`/`sorted()` | Test `DomainRulesTest.naturalOrderIsByStartTime` |
| Comparator | `analytics/EventAnalytics.topSelling()` | `Comparator.comparingInt(...).reversed().thenComparing(...)` | Best sellers on the dashboard | App: dashboard "Best-selling events" |
| Collections utilities | `CollectionsDemo` | `sort`, `max`, `min`, `binarySearch`, `reverse`, `frequency`, `unmodifiableList` | – | Lab `collections` |
| Concurrent map (bonus) | `security/LoginAttemptTracker` | `ConcurrentHashMap.compute()` | Thread-safe login throttling | Test `ConcurrencyDemoTest.loginTrackerLocksAfterFiveFailures` |

Viva: List = ordered with duplicates; Set = no duplicates; Map = key→value; Hash* = fastest with no order; Linked* = insertion order; Tree* = sorted.

## 5. Multithreading

| Topic | Class | Method | What the code does | Demo |
|---|---|---|---|---|
| Process, thread, multiprogramming, multithreading, concurrency | `demo/ThreadBasicsDemo` | section "Process vs thread" | Prints the JVM pid, thread name and CPU cores, and explains the terms | Lab `threads` |
| Sequential vs concurrent, parallelism | `ThreadBasicsDemo` | last section | 3×100 ms tasks: ~300 ms one after another vs ~100 ms concurrently; parallel stream sum | Lab `threads` |
| Extending Thread | `ThreadBasicsDemo.ReminderThread extends Thread` | `run()` | – | Lab `threads` |
| Runnable | `ThreadBasicsDemo` (lambda `Runnable`) | `new Thread(task, name)` | – | Lab `threads` |
| start() vs run() | `ThreadBasicsDemo` | – | `run()` executes in the caller's thread; `start()` creates a new one | Lab `threads` |
| sleep() / join() | `ThreadBasicsDemo` | – | Sleeper thread; main waits with `join()` | Lab `threads` |
| interrupt() | `ThreadBasicsDemo` | – | Long task catches `InterruptedException` and restores the flag | Lab `threads` |
| Lifecycle (NEW, RUNNABLE, BLOCKED, WAITING, TIMED_WAITING, TERMINATED) | `ThreadBasicsDemo` | `record()` | Actually observes all six states | Lab `threads`; Test `DemosRunTest` |
| Daemon threads, priorities | `ThreadBasicsDemo` | `setDaemon`, `setPriority` | Priority is only a hint | Lab `threads` |
| Race condition | `demo/TicketCounters.UnsafeCounter`, `RaceConditionDemo.race()` | `tryBook()` | Two users, one ticket → both succeed → **oversold** | Lab `race`; Test `ConcurrencyDemoTest.unsafeCounterOversellsUnderARace` |
| Critical section, synchronized block, intrinsic lock | `TicketCounters.SynchronizedBlockCounter` | `synchronized (lock) { ... }` | Only the check-and-decrement is locked | Lab `race` |
| synchronized method | `TicketCounters.SynchronizedMethodCounter.tryBook()`; also `db/DatabaseInitializer.tryInitialize()`, `demo/DemoCatalog.run()` | `synchronized` | Whole method guarded by `this` | Lab `race` |
| Atomicity | `TicketCounters.AtomicCounter` (`compareAndSet`), `RaceConditionDemo` (`count++` loses updates) | – | 4 threads × 50 000 increments | Lab `race` |
| Visibility (`volatile`) | `RaceConditionDemo.VolatileFlag`; real use: `DatabaseInitializer.ready` | `volatile boolean` | Other threads see the latest value | Lab `race` |
| wait() / notify() / notifyAll(), guarded blocks | `demo/BookingRequestQueue` (`put`, `take`, `close`); `ProducerConsumerDemo` (notify) | `while (cond) wait(); … notifyAll();` | Bounded queue without busy waiting | Lab `producer-consumer` |
| Producer-consumer | `demo/ProducerConsumerDemo.simulate()` | – | 2 producers → queue(3) → 2 consumers; stops cleanly with `close()` | Lab `producer-consumer`; Test `producerConsumerProcessesEverythingAndTerminates` |
| **Real-world concurrency** | `service/BookingService.book()` | `SELECT … FOR UPDATE` + conditional `UPDATE` + CHECK constraint | Database-level protection that also works across many servers | Test `ConcurrencyIntegrationTest` (30 users race for 10 tickets) |

Viva: `synchronized` protects memory inside **one JVM**. BookMatrix can run as several instances sharing one MySQL, so the real booking relies on **database** transactions and row locks. Java locks are demonstrated separately.

## 6. JDBC

| Topic | Class | Method | What the code does | Demo |
|---|---|---|---|---|
| Relational concepts, SQL | `resources/db/schema.sql`, `docs/DATABASE.md` | – | 6 normalised tables, PK/FK/UNIQUE/CHECK | – |
| JDBC architecture, drivers | `demo/JdbcDemo` | `DatabaseMetaData` | Shows API → DriverManager → MySQL Connector/J (Type 4) | Lab `jdbc` |
| Database URL | `application.properties` (`DB_URL`), `JdbcDemo` | – | `jdbc:mysql://host:port/db?options` | Lab `jdbc` |
| DriverManager | `demo/JdbcDemo.run()` | `DriverManager.getConnection(url, user, pw)` | Direct connection (the app itself uses a pool) | Lab `jdbc` |
| Connection | `db/Database.query()/inTransaction()` | `dataSource.getConnection()` | – | App: any page |
| Statement | `db/DatabaseInitializer.runScript()`, `dao/StatsDao.fixedTotals()`, `JdbcDemo` | `createStatement().execute()` | Fixed SQL with no user input | Lab `jdbc` |
| PreparedStatement + parameter binding | `db/JdbcDao.bind()` | `setString`, `setInt`, `setBigDecimal`, `setObject` | Every query with input | Lab `jdbc` (injection comparison) |
| executeQuery() | `db/JdbcDao.queryList()` | – | SELECTs | – |
| executeUpdate() | `db/JdbcDao.update()`; `dao/EventDao.reserveTickets()` returns rows changed | – | 0 rows = not enough tickets | App: book tickets |
| CRUD | `dao/EventDao` (insert/findById/update/deleteById), `UserDao`, `BookingDao` | – | – | App: admin events |
| ResultSet, cursor iteration | `db/JdbcDao.queryList()` | `while (rs.next())` | – | – |
| Typed getters | `dao/EventDao.map()` | `getLong`, `getString`, `getBigDecimal`, `getBoolean`, `getObject(col, LocalDateTime.class)` | – | – |
| Java object mapping | `db/RowMapper<T>`; `EventDao::map`, `UserDao::mapUser`, `BookingDao::map` | – | Row → object | – |
| Transactions, commit, rollback | `db/Database.inTransaction()`; `BookingService.book()/cancel()` | `setAutoCommit(false)`, `commit()`, `rollback()` | Booking + inventory succeed or fail together | Lab `jdbc` (rollback demo); Test `ConcurrencyIntegrationTest` |
| Resource management | every DAO | try-with-resources | No leaked connections (pool returns them on `close()`) | – |

## 7. Lambda and Streams

| Topic | Class | Method | What the code does | Demo |
|---|---|---|---|---|
| Lambda syntax | `service/*` | `db.query(conn -> userDao.findById(conn, id))` | Passes "work" to the transaction helper | Lab `streams` |
| Functional interfaces | `db/SqlFunction`, `db/RowMapper`, `StreamsDemo.PriceRule`; JDK `Predicate`, `Function`, `Supplier`, `Consumer` | `@FunctionalInterface` | – | Lab `streams` |
| Method references | `EventService.cities()` → `db.query(venueDao::distinctCities)`; `EventDao::map`; `Event::getCategoryName` | – | – | Lab `streams` |
| Stream sources | `StreamsDemo` | `List.stream`, `Stream.of`, `Arrays.stream`, `IntStream.range`, `Stream.iterate` | – | Lab `streams` |
| Pipelines, intermediate and terminal operations | `analytics/EventAnalytics` | `filter → sorted → limit → toList` | Dashboard analytics | App: Admin dashboard |
| Lazy execution | `StreamsDemo` | `peek` prints only after `toList()` | – | Lab `streams` |
| filter() | `EventAnalytics.upcoming()`, `highPriced()` | – | – | Test `AnalyticsAndGenericsTest` |
| map() | `EventAnalytics.distinctCities()`; `EventService.search()` (`Event → EventView`) | – | – | – |
| sorted() | `EventAnalytics.topSelling()` | Comparator | – | – |
| distinct() | `EventAnalytics.distinctCities()` | – | – | – |
| limit() / skip() | `EventAnalytics.topSelling()` (limit), `EventAnalytics.page()` (skip + limit) | – | – | Lab `streams` |
| Collectors | `EventAnalytics.ticketsByCategory()` | `groupingBy`, `summingInt` | – | – |

## 8. Generics

| Topic | Class | Method | Demo |
|---|---|---|---|
| Generic classes | `util/ApiResponse<T>`, `util/PageResult<T>`, `demo/GenericsDemo.Box<T>`, `Pair<K,V>` | – | Lab `generics` |
| Generic methods | `ApiResponse.ok(T)`, `PageResult.of(...)`, `Database.query(SqlFunction<Connection,R>)`, `GenericsDemo.firstOrDefault()`, `EventAnalytics.page()` | `<T>` before the return type | Lab `generics` |
| Type safety | `GenericsDemo` (raw type → `ClassCastException`) | – | Lab `generics` |
| Diamond operator | everywhere: `new ArrayList<>()` | – | Lab `generics` |
| Bounded generics | `UserService.parseEnum(Class<E>, String)` with `<E extends Enum<E>>`; `GenericsDemo.sum(List<T extends Number>)`, `max(<T extends Comparable<? super T>>)` | – | Test `AnalyticsAndGenericsTest` |
| Wildcards | `PageResult.map(Function<? super T, ? extends R>)`; `GenericsDemo.average(List<? extends Number>)`, `addTicketNumbers(List<? super Integer>)`, `countAll(List<?>)` | – | Lab `generics` |
| Generic interfaces | `db/Dao<T, ID>` (implemented by `UserDao`, `EventDao`), `RowMapper<T>` | – | – |

Viva: generics catch type errors at compile time and remove casts. `? extends` = read from it (producer); `? super` = write into it (consumer).

## 9. File handling

| Topic | Class | Method | What the code does | Demo |
|---|---|---|---|---|
| Byte streams, FileOutputStream, buffered | `fileio/DataTransferService.writeManifest()` | `BufferedOutputStream(FileOutputStream)` | Backup manifest | App: Admin → Import & export → Create backup |
| FileInputStream | `demo/FileHandlingDemo` | buffered byte copy | Copies a binary file | Lab `files` |
| Character streams, FileWriter, BufferedWriter | `DataTransferService.writeCsvFile()` | `BufferedWriter(FileWriter(file, UTF_8))` | Backup CSV files | App: backup |
| FileReader, BufferedReader | `demo/DemoRunner.loadEnv()` (reads `.env`), `db/DatabaseInitializer.runScript()` (reads `schema.sql`), `FileHandlingDemo` | `readLine()` | – | Lab `files` |
| StreamTokenizer | `FileHandlingDemo` | `nextToken()`, `TT_WORD`, `TT_NUMBER` | Splits an order line into words and numbers | Lab `files` |
| CSV (commas, quotes, newlines, empty values) | `fileio/CsvParser.parse()`, `fileio/CsvWriter.escape()` | character state machine | RFC 4180 reading and writing | Test `CsvTest`; App: import `data/sample-events.csv` |
| Collection import / export | `DataTransferService.importEvents()` / `exportEventsCsv()`; `FileHandlingDemo` round trip | – | CSV ⇄ `List<Event>` | App: Import & export |
| Validation of imported data | `DataTransferService.toEvent()` → `EventFormValidator.validate()` | – | Missing columns, wrong types, invalid rows, duplicates (file and database) | Test `DataAndDashboardApiTest.importValidatesEveryRow` |
| Exception-safe resource management | all of the above | try-with-resources; `FileNotFoundException`, `CsvFormatException` | – | Lab `files` |
