package com.bookmatrix.service;

import com.bookmatrix.analytics.EventAnalytics;
import com.bookmatrix.config.BookMatrixProperties;
import com.bookmatrix.dao.AuditLogDao;
import com.bookmatrix.dao.BookingDao;
import com.bookmatrix.dao.EventDao;
import com.bookmatrix.dao.StatsDao;
import com.bookmatrix.db.Database;
import com.bookmatrix.dto.ArchiveFilter;
import com.bookmatrix.dto.BookingFilter;
import com.bookmatrix.dto.EventLifecycleFilter;
import com.bookmatrix.dto.EventSearchCriteria;
import com.bookmatrix.dto.EventSort;
import com.bookmatrix.dto.Views.AuditView;
import com.bookmatrix.dto.Views.BookingView;
import com.bookmatrix.dto.Views.Dashboard;
import com.bookmatrix.dto.Views.TopEvent;
import com.bookmatrix.model.Event;
import com.bookmatrix.util.PageRequest;
import com.bookmatrix.util.TimeProvider;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin dashboard numbers. Every figure is computed from MySQL — nothing is invented.
 * Revenue model: a booking is treated as paid in full when it is CONFIRMED; cancelling
 * refunds it in full. So revenue = SUM(total_amount) of CONFIRMED bookings.
 */
@Service
public class DashboardService {

    private final Database db;
    private final StatsDao statsDao;
    private final BookingDao bookingDao;
    private final EventDao eventDao;
    private final AuditLogDao auditLogDao;
    private final TimeProvider time;
    private final int cutoffHours;

    public DashboardService(Database db, StatsDao statsDao, BookingDao bookingDao, EventDao eventDao,
                            AuditLogDao auditLogDao, TimeProvider time, BookMatrixProperties properties) {
        this.db = db;
        this.statsDao = statsDao;
        this.bookingDao = bookingDao;
        this.eventDao = eventDao;
        this.auditLogDao = auditLogDao;
        this.time = time;
        this.cutoffHours = properties.cancellationCutoffHours();
    }

    public Dashboard build() {
        LocalDateTime now = time.now();
        return db.query(conn -> {
            Map<String, Long> counts = new LinkedHashMap<>(statsDao.fixedTotals(conn));
            counts.putAll(statsDao.timeBasedTotals(conn, now));
            BigDecimal revenue = statsDao.confirmedRevenue(conn);
            BigDecimal refunded = statsDao.refundedAmount(conn);
            long confirmed = counts.getOrDefault("confirmed_bookings", 0L);
            BigDecimal average = confirmed == 0 ? BigDecimal.ZERO
                    : revenue.divide(BigDecimal.valueOf(confirmed), 2, RoundingMode.HALF_UP);

            var inventory = statsDao.inventory(conn, false);
            List<TopEvent> top = EventAnalytics.topSelling(inventory, 5).stream()
                    .map(r -> new TopEvent(r.eventId(), r.title(), r.ticketsSold(), r.capacity(), r.revenue()))
                    .toList();

            EventSearchCriteria all = new EventSearchCriteria(null, null, null, null, null, null, null,
                    EventLifecycleFilter.ALL, ArchiveFilter.EXCLUDE, false, EventSort.DATE_ASC);
            List<Event> events = eventDao.search(conn, all, new PageRequest(1, 10_000), now);
            Map<String, Integer> byCategory = EventAnalytics.ticketsByCategory(events);

            List<BookingView> recent = bookingDao.search(conn, null, null, null, BookingFilter.ALL,
                    new PageRequest(1, 6), now).stream()
                    .map(b -> BookingView.from(b, now, cutoffHours, true)).toList();
            List<AuditView> activity = auditLogDao.recent(conn, 10, null, null).stream().map(AuditView::from).toList();
            int inconsistent = (int) EventAnalytics.countInconsistent(statsDao.inventory(conn, true));
            return new Dashboard(counts, revenue, refunded, average, top, byCategory, recent, activity,
                    inconsistent, now);
        });
    }

    public List<AuditView> activity(int limit, String entityType, Long entityId) {
        int safe = Math.max(1, Math.min(limit, 200));
        return db.query(conn -> auditLogDao.recent(conn, safe, entityType, entityId)).stream()
                .map(AuditView::from).toList();
    }

}
