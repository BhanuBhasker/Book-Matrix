-- =====================================================================
--  BookMatrix database schema (MySQL 8.0.16+ / InnoDB)
--  Executed by com.bookmatrix.db.DatabaseInitializer on every start-up.
--  Every statement is idempotent (CREATE ... IF NOT EXISTS), so the
--  application can start from a completely empty database.
--  Statements are separated by a semicolon at the END of a line.
-- =====================================================================

-- ---------------------------------------------------------------------
-- users: everyone who can log in. Role decides USER vs ADMIN.
-- Accounts are never hard-deleted once they have bookings; they are
-- DEACTIVATED instead so booking history stays intact.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id              BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
    full_name       VARCHAR(100)     NOT NULL,
    email           VARCHAR(254)     NOT NULL,
    phone           VARCHAR(20)      NULL,
    password_hash   CHAR(60)         NOT NULL,              -- BCrypt hash, never plain text
    role            ENUM('USER','ADMIN')          NOT NULL DEFAULT 'USER',
    status          ENUM('ACTIVE','DEACTIVATED')  NOT NULL DEFAULT 'ACTIVE',
    created_at      DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    last_login_at   DATETIME         NULL,
    deactivated_at  DATETIME         NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email),
    INDEX idx_users_role_status (role, status),
    CONSTRAINT chk_users_deactivated CHECK ((status = 'DEACTIVATED') = (deactivated_at IS NOT NULL))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ---------------------------------------------------------------------
-- categories: small reference table (Music, Comedy, ...).
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS categories (
    id           INT UNSIGNED  NOT NULL AUTO_INCREMENT,
    name         VARCHAR(50)   NOT NULL,
    slug         VARCHAR(50)   NOT NULL,
    description  VARCHAR(255)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_categories_name UNIQUE (name),
    CONSTRAINT uq_categories_slug UNIQUE (slug)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ---------------------------------------------------------------------
-- venues: normalised out of events so a venue/city is stored once and
-- many events can point to it (one-to-many).
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS venues (
    id          INT UNSIGNED  NOT NULL AUTO_INCREMENT,
    name        VARCHAR(120)  NOT NULL,
    city        VARCHAR(80)   NOT NULL,
    address     VARCHAR(255)  NULL,
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_venues_name_city UNIQUE (name, city),
    INDEX idx_venues_city (city)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ---------------------------------------------------------------------
-- events: one row per event. Inventory lives here:
--   available tickets = capacity - tickets_sold
-- The CHECK constraints make overselling impossible at database level,
-- even if application code had a bug.
-- Only SCHEDULED / CANCELLED are stored. "Upcoming", "Sold out", "Live"
-- and "Completed" are DERIVED from dates and inventory, so the stored
-- data can never contradict itself (e.g. "sold out" with free seats).
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS events (
    id            BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
    title         VARCHAR(150)     NOT NULL,
    description   TEXT             NOT NULL,
    category_id   INT UNSIGNED     NOT NULL,
    venue_id      INT UNSIGNED     NOT NULL,
    starts_at     DATETIME         NOT NULL,
    ends_at       DATETIME         NOT NULL,
    ticket_price  DECIMAL(10,2)    NOT NULL,
    capacity      INT UNSIGNED     NOT NULL,
    tickets_sold  INT UNSIGNED     NOT NULL DEFAULT 0,
    image_url     VARCHAR(500)     NULL,
    is_featured   BOOLEAN          NOT NULL DEFAULT FALSE,
    status        ENUM('SCHEDULED','CANCELLED') NOT NULL DEFAULT 'SCHEDULED',
    cancelled_at  DATETIME         NULL,
    archived_at   DATETIME         NULL,               -- soft delete: hidden from the public site
    created_by    BIGINT UNSIGNED  NULL,
    created_at    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_events_category FOREIGN KEY (category_id) REFERENCES categories (id) ON DELETE RESTRICT,
    CONSTRAINT fk_events_venue    FOREIGN KEY (venue_id)    REFERENCES venues (id)     ON DELETE RESTRICT,
    CONSTRAINT fk_events_creator  FOREIGN KEY (created_by)  REFERENCES users (id)      ON DELETE SET NULL,
    CONSTRAINT uq_events_slot UNIQUE (title, venue_id, starts_at),          -- no duplicate events
    CONSTRAINT chk_events_capacity CHECK (capacity BETWEEN 1 AND 100000),
    CONSTRAINT chk_events_sold     CHECK (tickets_sold <= capacity),
    CONSTRAINT chk_events_price    CHECK (ticket_price >= 0 AND ticket_price <= 1000000),
    CONSTRAINT chk_events_times    CHECK (ends_at > starts_at),
    CONSTRAINT chk_events_cancel   CHECK ((status = 'CANCELLED') = (cancelled_at IS NOT NULL)),
    INDEX idx_events_starts (starts_at),
    INDEX idx_events_public (archived_at, status, starts_at),
    INDEX idx_events_category (category_id),
    INDEX idx_events_venue (venue_id),
    INDEX idx_events_price (ticket_price)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ---------------------------------------------------------------------
-- bookings: a user's purchase of N tickets for one event.
-- users 1..* bookings *..1 events  (users and events are many-to-many
-- THROUGH bookings). Price is copied at booking time so later price
-- changes never rewrite history. Cancelled bookings are kept forever.
-- request_key + user_id is UNIQUE: a double-clicked / retried submit
-- can never create two bookings.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS bookings (
    id                   BIGINT UNSIGNED    NOT NULL AUTO_INCREMENT,
    booking_ref          CHAR(10)           NOT NULL,
    user_id              BIGINT UNSIGNED    NOT NULL,
    event_id             BIGINT UNSIGNED    NOT NULL,
    quantity             SMALLINT UNSIGNED  NOT NULL,
    unit_price           DECIMAL(10,2)      NOT NULL,
    total_amount         DECIMAL(12,2)      NOT NULL,
    status               ENUM('CONFIRMED','CANCELLED') NOT NULL DEFAULT 'CONFIRMED',
    request_key          VARCHAR(64)        NOT NULL,
    booked_at            DATETIME           NOT NULL,
    cancelled_at         DATETIME           NULL,
    cancelled_by         BIGINT UNSIGNED    NULL,
    cancellation_reason  VARCHAR(255)       NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_bookings_ref     UNIQUE (booking_ref),
    CONSTRAINT uq_bookings_request UNIQUE (user_id, request_key),
    CONSTRAINT fk_bookings_user      FOREIGN KEY (user_id)      REFERENCES users (id)  ON DELETE RESTRICT,
    CONSTRAINT fk_bookings_event     FOREIGN KEY (event_id)     REFERENCES events (id) ON DELETE RESTRICT,
    CONSTRAINT fk_bookings_cancelled FOREIGN KEY (cancelled_by) REFERENCES users (id)  ON DELETE SET NULL,
    CONSTRAINT chk_bookings_quantity CHECK (quantity BETWEEN 1 AND 10),
    CONSTRAINT chk_bookings_total    CHECK (total_amount = unit_price * quantity),
    CONSTRAINT chk_bookings_cancel   CHECK ((status = 'CANCELLED') = (cancelled_at IS NOT NULL)),
    INDEX idx_bookings_user (user_id, booked_at),
    INDEX idx_bookings_event (event_id, status),
    INDEX idx_bookings_status (status, booked_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ---------------------------------------------------------------------
-- audit_log: append-only history of important actions (bookings,
-- cancellations, admin changes). Powers "Recent activity" for admins.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS audit_log (
    id             BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
    actor_user_id  BIGINT UNSIGNED  NULL,
    action         VARCHAR(40)      NOT NULL,
    entity_type    VARCHAR(20)      NOT NULL,
    entity_id      BIGINT UNSIGNED  NULL,
    details        VARCHAR(500)     NULL,
    created_at     DATETIME         NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_audit_actor FOREIGN KEY (actor_user_id) REFERENCES users (id) ON DELETE SET NULL,
    INDEX idx_audit_created (created_at),
    INDEX idx_audit_entity (entity_type, entity_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
