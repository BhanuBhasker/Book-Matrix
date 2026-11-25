-- =====================================================================
--  Migration V2: demo payment gateway + reserved seat selection.
--  Applied once by DatabaseInitializer (tracked in schema_migrations).
--  Existing data is kept: old confirmed/cancelled bookings are marked as paid.
-- =====================================================================

-- ---------------------------------------------------------------------
-- events: an event is either GENERAL admission (quantity only) or
-- RESERVED seating laid out as rows A.. x seats 1..N.
-- For RESERVED events capacity must equal rows x seats per row.
-- ---------------------------------------------------------------------
ALTER TABLE events
    ADD COLUMN seating_type  ENUM('GENERAL','RESERVED') NOT NULL DEFAULT 'GENERAL' AFTER tickets_sold,
    ADD COLUMN seat_rows     TINYINT UNSIGNED NULL AFTER seating_type,
    ADD COLUMN seats_per_row TINYINT UNSIGNED NULL AFTER seat_rows;

ALTER TABLE events
    ADD CONSTRAINT chk_events_layout CHECK (
        (seating_type = 'GENERAL' AND seat_rows IS NULL AND seats_per_row IS NULL)
     OR (seating_type = 'RESERVED' AND seat_rows BETWEEN 1 AND 26 AND seats_per_row BETWEEN 1 AND 30
         AND capacity = seat_rows * seats_per_row));

-- ---------------------------------------------------------------------
-- bookings: a booking now starts as PENDING_PAYMENT (tickets/seats held
-- until hold_expires_at), becomes CONFIRMED when paid, or EXPIRED when the
-- hold runs out. seat_labels keeps a snapshot such as "A1, A2" for history.
-- ---------------------------------------------------------------------
ALTER TABLE bookings
    MODIFY COLUMN status ENUM('PENDING_PAYMENT','CONFIRMED','CANCELLED','EXPIRED') NOT NULL DEFAULT 'PENDING_PAYMENT',
    ADD COLUMN seat_labels     VARCHAR(255) NULL AFTER quantity,
    ADD COLUMN hold_expires_at DATETIME     NULL AFTER booked_at,
    ADD COLUMN paid_at         DATETIME     NULL AFTER hold_expires_at,
    ADD INDEX idx_bookings_hold (status, hold_expires_at);

-- bookings made before payments existed were confirmed on the spot = paid
UPDATE bookings SET paid_at = booked_at WHERE status IN ('CONFIRMED', 'CANCELLED') AND paid_at IS NULL;

ALTER TABLE bookings
    ADD CONSTRAINT chk_bookings_hold CHECK (status <> 'PENDING_PAYMENT' OR hold_expires_at IS NOT NULL),
    ADD CONSTRAINT chk_bookings_paid CHECK (status <> 'CONFIRMED' OR paid_at IS NOT NULL);

-- ---------------------------------------------------------------------
-- event_seats: one row per physical seat of a RESERVED event.
-- booking_id NULL = free; otherwise the seat belongs to that booking
-- (held or sold). A seat row can point to only ONE booking at a time,
-- and the unique key means a seat exists only once — so the database
-- itself makes double-booking a seat impossible.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS event_seats (
    id           BIGINT UNSIGNED  NOT NULL AUTO_INCREMENT,
    event_id     BIGINT UNSIGNED  NOT NULL,
    row_label    CHAR(1)          NOT NULL,
    seat_number  TINYINT UNSIGNED NOT NULL,
    booking_id   BIGINT UNSIGNED  NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_event_seat UNIQUE (event_id, row_label, seat_number),
    CONSTRAINT fk_seats_event   FOREIGN KEY (event_id)   REFERENCES events (id)   ON DELETE CASCADE,
    CONSTRAINT fk_seats_booking FOREIGN KEY (booking_id) REFERENCES bookings (id) ON DELETE SET NULL,
    INDEX idx_seats_booking (booking_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ---------------------------------------------------------------------
-- payments: every attempt with the (demo) payment gateway, plus refunds.
-- Only the card brand and last 4 digits are stored — never the full number
-- or the CVV. The two generated columns + UNIQUE keys guarantee at most ONE
-- successful charge and ONE refund per booking, even under concurrency.
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS payments (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    booking_id      BIGINT UNSIGNED NOT NULL,
    kind            ENUM('CHARGE','REFUND')     NOT NULL,
    status          ENUM('SUCCEEDED','FAILED')  NOT NULL,
    amount          DECIMAL(12,2)   NOT NULL,
    method          ENUM('CARD','UPI')          NOT NULL,
    card_brand      VARCHAR(20)     NULL,
    card_last4      CHAR(4)         NULL,
    upi_handle      VARCHAR(60)     NULL,
    gateway_ref     VARCHAR(40)     NOT NULL,
    failure_reason  VARCHAR(255)    NULL,
    created_at      DATETIME        NOT NULL,
    one_charge_key  BIGINT UNSIGNED GENERATED ALWAYS AS (CASE WHEN kind = 'CHARGE' AND status = 'SUCCEEDED' THEN booking_id END) STORED,
    one_refund_key  BIGINT UNSIGNED GENERATED ALWAYS AS (CASE WHEN kind = 'REFUND' THEN booking_id END) STORED,
    PRIMARY KEY (id),
    CONSTRAINT uq_payments_ref        UNIQUE (gateway_ref),
    CONSTRAINT uq_payments_one_charge UNIQUE (one_charge_key),
    CONSTRAINT uq_payments_one_refund UNIQUE (one_refund_key),
    CONSTRAINT fk_payments_booking FOREIGN KEY (booking_id) REFERENCES bookings (id) ON DELETE RESTRICT,
    CONSTRAINT chk_payments_amount CHECK (amount >= 0),
    INDEX idx_payments_booking (booking_id),
    INDEX idx_payments_created (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
