package com.bookmatrix.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Background job (runs on Spring's scheduler THREAD, separate from web request threads):
 * every 30 seconds it releases tickets/seats from bookings that were not paid in time.
 * Payment and booking screens also check expiry themselves, so the job is a safety net.
 */
@Component
public class HoldExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(HoldExpiryJob.class);

    private final BookingService bookingService;

    public HoldExpiryJob(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @Scheduled(initialDelayString = "${bookmatrix.hold-sweep-initial-ms:15000}", fixedDelayString = "${bookmatrix.hold-sweep-ms:30000}")
    public void releaseExpiredHolds() {
        try {
            bookingService.expireStaleHolds();
        } catch (RuntimeException e) {
            log.debug("Hold sweep skipped: {}", e.getMessage());     // e.g. database briefly unavailable; next run retries
        }
    }
}
