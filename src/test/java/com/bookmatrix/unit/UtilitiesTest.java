package com.bookmatrix.unit;

import com.bookmatrix.model.Booking;
import com.bookmatrix.model.BookingStatus;
import com.bookmatrix.util.BookingReferenceGenerator;
import com.bookmatrix.util.Money;
import com.bookmatrix.util.PageRequest;
import com.bookmatrix.util.PageResult;
import com.bookmatrix.util.ReceiptBuilder;
import com.bookmatrix.util.TextUtils;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class UtilitiesTest {

    @Test
    void bookingReferencesAreWellFormedAndUnique() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 20_000; i++) {
            String ref = BookingReferenceGenerator.next();
            assertThat(BookingReferenceGenerator.isWellFormed(ref)).isTrue();
            seen.add(ref);
        }
        assertThat(seen).hasSize(20_000);
        assertThat(BookingReferenceGenerator.isWellFormed("BM' OR 1=1")).isFalse();
        assertThat(BookingReferenceGenerator.isWellFormed("XX12345678")).isFalse();
        assertThat(BookingReferenceGenerator.isWellFormed(null)).isFalse();
    }

    @Test
    void pageRequestClampsBadValues() {
        assertThat(PageRequest.of(null, null)).isEqualTo(new PageRequest(1, PageRequest.DEFAULT_SIZE));
        assertThat(PageRequest.of(-4, 5000)).isEqualTo(new PageRequest(1, PageRequest.MAX_SIZE));
        assertThat(new PageRequest(3, 10).offset()).isEqualTo(20);
    }

    @Test
    void pageResultIsGeneric() {
        PageResult<String> page = PageResult.of(List.of("a", "bb"), new PageRequest(1, 2), 5);
        assertThat(page.totalPages()).isEqualTo(3);
        PageResult<Integer> lengths = page.map(String::length);
        assertThat(lengths.items()).containsExactly(1, 2);
        assertThat(PageResult.of(List.of(), new PageRequest(1, 10), 0).totalPages()).isEqualTo(1);
    }

    @Test
    void textHelpers() {
        assertThat(TextUtils.clean("  Comedy   Night ")).isEqualTo("Comedy Night");
        assertThat(TextUtils.clean("   ")).isNull();
        assertThat(TextUtils.normalizeEmail(" Asha@Example.COM ")).isEqualTo("asha@example.com");
        assertThat(TextUtils.escapeLike("100%_off")).isEqualTo("100\\%\\_off");
        assertThat(TextUtils.slugify("Food & Drink!")).isEqualTo("food-drink");
        assertThat(TextUtils.truncate("abcdef", 4)).hasSize(4);
    }

    @Test
    void moneyFormatting() {
        assertThat(Money.format(new BigDecimal("1499.5"))).isEqualTo("Rs. 1,499.50");
        assertThat(Money.format(BigDecimal.ZERO)).isEqualTo("Free");
        assertThat(Money.normalize(new BigDecimal("2.005"))).isEqualByComparingTo("2.01");
    }

    @Test
    void receiptContainsTheImportantFacts() {
        Booking b = new Booking();
        b.setReference("BM23456789");
        b.setStatus(BookingStatus.CANCELLED);
        b.setBookedAt(LocalDateTime.of(2026, 12, 1, 10, 0));
        b.setCancelledAt(LocalDateTime.of(2026, 12, 2, 10, 0));
        b.setCancellationReason("Change of plans");
        b.setEventTitle("Indie Night");
        b.setVenueName("Blue Frog");
        b.setCity("Pune");
        b.setEventStartsAt(LocalDateTime.of(2026, 12, 12, 19, 30));
        b.setQuantity(2);
        b.setUnitPrice(new BigDecimal("799.00"));
        b.setTotalAmount(new BigDecimal("1598.00"));
        String receipt = ReceiptBuilder.receipt(b);
        assertThat(receipt).contains("BM23456789", "CANCELLED BOOKING", "Indie Night", "Rs. 1,598.00", "Change of plans", "Refund");
        assertThat(ReceiptBuilder.confirmationMessage(b)).contains("2 tickets", "Indie Night", "BM23456789");
    }
}
