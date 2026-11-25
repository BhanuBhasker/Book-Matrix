# Project Synopsis — BookMatrix

**Title:** BookMatrix: An Event Discovery and Ticket Booking Platform
**Course:** Java Programming (CE-3 evaluation)
**Technology:** Java, Spring Boot, JDBC, MySQL, HTML/CSS/JavaScript

## 1. Introduction
BookMatrix is a web application where people find events (concerts, plays, comedy shows, conferences, workshops) and book tickets online, and where organisers manage events, tickets, customers and bookings. It is built in Java, stores all data in MySQL through hand-written JDBC, and covers the complete Java syllabus inside a working product.

## 2. Problem statement
Small venues and organisers often sell tickets by phone, spreadsheets or messaging apps. This leads to:
- **overselling**: two people are promised the last seat;
- no single, searchable list of what is on;
- no reliable record of who booked, cancelled or was refunded;
- organisers have no real-time view of sales and remaining capacity.

## 3. Proposed solution
A web platform with two sides:
- **Customers** search and filter events, see live availability, book in a few clicks, get a unique booking reference and receipt, and manage or cancel their bookings.
- **Administrators** create and manage events, control capacity and status, manage users and bookings, import and export data, and watch real statistics.

Correctness under load is the core requirement. Inventory is protected **inside the database** with transactions, row locks, atomic conditional updates and CHECK constraints, so tickets can never be oversold even when many people book at the same moment.

## 4. Objectives
1. Build a complete, secure, database-driven booking system.
2. Guarantee inventory consistency under concurrent bookings and cancellations.
3. Apply core Java: OOP, collections, exceptions, generics, streams, multithreading, JDBC and file I/O.
4. Provide a clean, accessible, responsive interface.
5. Verify everything with automated unit, integration, concurrency and real-browser tests.

## 5. Technology stack
| Area | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.5 (embedded Tomcat, REST) |
| Database | MySQL 8, accessed with plain JDBC (HikariCP pool) |
| Security | BCrypt hashing, HTTP sessions, CSRF tokens, role-based access |
| Frontend | HTML5, CSS3, JavaScript (ES modules) |
| Testing | JUnit 5, Spring MockMvc, Playwright (Chrome) |
| Build | Maven |

## 6. Architecture
A layered monolith: **Browser → Controllers (REST) → Services (business rules and transactions) → DAOs (SQL via JDBC) → MySQL**. Interceptors handle CSRF, authentication and authorisation before any controller runs. See `ARCHITECTURE.md`.

## 7. Database design
Eight normalised tables: `users`, `categories`, `venues`, `events`, `bookings`, `event_seats`, `payments`, `audit_log`. Users and events have a many-to-many relationship through bookings. Constraints such as `tickets_sold <= capacity`, unique booking references and unique `(user, request_key)` make invalid or duplicate data impossible at the database level. See `DATABASE.md` for the ER diagram.

## 8. Modules
1. **Authentication**: register, login, logout, sessions, CSRF, login throttling, first-admin bootstrap.
2. **Event discovery**: home page, search, filters, sorting, pagination, event details.
3. **Booking**: quantity or seat-map selection, review, 10-minute hold, demo payment (card with OTP or UPI), confirmation, references, receipts, duplicate-submit protection.
4. **My bookings and cancellation**: history, search, details, policy-based cancellation, refunds.
5. **Profile**: edit details, change password, close account.
6. **Admin dashboard**: real statistics, best sellers, category breakdown, activity.
7. **Admin event management**: create, edit, feature, cancel, archive/delete, restore, capacity rules.
8. **Admin users and bookings**: search, details, activate/deactivate, roles, cancellations.
9. **Inventory monitoring**: capacity, sold, available and a consistency check.
10. **Data**: CSV import and export, backups, audit log.
11. **Java Lab**: 12 executable syllabus demonstrations.

## 9. Functional requirements
- Users can register, log in, log out, and update their profile and password.
- Users can search, filter and sort events and view details and availability.
- Users can book 1–10 tickets for an upcoming event with enough seats, and get a unique reference.
- Users can view, search and cancel their own bookings (up to 2 hours before the event).
- Admins can create, read, update, cancel, archive and delete events.
- Admins can manage users (edit, activate/deactivate, change roles) and bookings (view, cancel).
- Admins can see statistics, inventory, import/export CSV and create backups.

## 10. Non-functional requirements
- **Consistency:** no overselling; inventory restored exactly once (proved by concurrency tests).
- **Security:** hashed passwords, CSRF, session-fixation protection, server-side authorisation, no SQL injection or XSS.
- **Reliability:** clear 503 when the database is down, automatic recovery, safe error messages.
- **Usability and accessibility:** responsive from 390 px phones to desktops, keyboard navigation, labels, focus states.
- **Maintainability:** layered packages, small classes, documented decisions, 145 JUnit and 24 browser tests.

## 11. Expected outcome
A working, tested platform that can be run on any Mac with Java and MySQL, demonstrated end to end in a browser, and used to explain every Java syllabus topic with real code.

## 12. Future scope
- A real payment provider (Razorpay/Stripe) in place of the built-in demo gateway.
- Email or SMS confirmations and reminders (an outbox table plus a background worker).
- Waitlists that offer released tickets automatically.
- Image upload and storage instead of image URLs.
- Organiser accounts (a third role) with their own events and reports.
- Deployment with HTTPS (`COOKIE_SECURE=true`) behind a reverse proxy.
