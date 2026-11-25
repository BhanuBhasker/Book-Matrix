package com.bookmatrix.unit;

import com.bookmatrix.dao.SeatDao.SeatLabel;
import com.bookmatrix.exception.ValidationException;
import com.bookmatrix.payment.CardValidator;
import com.bookmatrix.payment.DemoPaymentGateway;
import com.bookmatrix.payment.GatewayResult;
import com.bookmatrix.payment.PaymentGateway;
import com.bookmatrix.payment.PaymentRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Card validation, the demo gateway's test cards, and seat label parsing. */
class PaymentGatewayTest {

    private final PaymentGateway gateway = new DemoPaymentGateway();
    private final String nextYear = "12/" + (YearMonth.now().getYear() % 100 + 1);

    private PaymentRequest card(String number, String expiry, String cvv) {
        return new PaymentRequest("CARD", number, "Asha Rao", expiry, cvv, null, new BigDecimal("499.00"), "BM23456789");
    }

    @ParameterizedTest
    @CsvSource({"4242424242424242,true", "5555555555554444,true", "4000000000000002,true", "4242424242424241,false",
            "1234567812345678,false", "42424242,false", "abcd424242424242,false"})
    void luhnChecksum(String number, boolean valid) {
        assertThat(CardValidator.passesLuhn(number)).isEqualTo(valid);
    }

    @Test
    void brandExpiryAndCvvRules() {
        assertThat(CardValidator.brand("4242424242424242")).isEqualTo("Visa");
        assertThat(CardValidator.brand("5555555555554444")).isEqualTo("Mastercard");
        assertThat(CardValidator.brand("378282246310005")).isEqualTo("Amex");
        assertThat(CardValidator.parseExpiry("07/29")).contains(YearMonth.of(2029, 7));
        assertThat(CardValidator.parseExpiry("13/29")).isEmpty();
        assertThat(CardValidator.parseExpiry("0729")).isEmpty();
        assertThat(CardValidator.isValidCvv("123", "Visa")).isTrue();
        assertThat(CardValidator.isValidCvv("1234", "Visa")).isFalse();
        assertThat(CardValidator.isValidCvv("1234", "Amex")).isTrue();
        assertThat(CardValidator.isValidUpi("asha@okbank")).isTrue();
        assertThat(CardValidator.isValidUpi("not a upi")).isFalse();
        assertThat(CardValidator.digitsOnly("4242 4242-4242 4242")).isEqualTo("4242424242424242");
    }

    @Test
    void approvedAndDecliningTestCards() {
        GatewayResult ok = gateway.charge(card("4242 4242 4242 4242", nextYear, "123"));
        assertThat(ok.approved()).isTrue();
        assertThat(ok.cardBrand()).isEqualTo("Visa");
        assertThat(ok.cardLast4()).isEqualTo("4242");
        assertThat(ok.gatewayRef()).matches("PAY_[A-Z2-9]{16}");

        GatewayResult declined = gateway.charge(card("4000 0000 0000 0002", nextYear, "123"));
        assertThat(declined.approved()).isFalse();
        assertThat(declined.declineReason()).contains("declined");
        assertThat(gateway.charge(card("4000000000009995", nextYear, "123")).declineReason()).contains("Insufficient funds");
    }

    @Test
    void badDetailsAreRejectedBeforeCharging() {
        assertThatThrownBy(() -> gateway.charge(card("4242 4242 4242 4241", nextYear, "123")))
                .isInstanceOf(ValidationException.class)
                .satisfies(e -> assertThat(((ValidationException) e).getFieldErrors()).containsKey("cardNumber"));
        assertThatThrownBy(() -> gateway.charge(card("4242424242424242", "01/20", "123")))
                .satisfies(e -> assertThat(((ValidationException) e).getFieldErrors()).containsKey("expiry"));
        assertThatThrownBy(() -> gateway.charge(card("4242424242424242", nextYear, "12")))
                .satisfies(e -> assertThat(((ValidationException) e).getFieldErrors()).containsKey("cvv"));
        assertThatThrownBy(() -> gateway.charge(new PaymentRequest("CASH", null, null, null, null, null, BigDecimal.ONE, "x")))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void upiPayments() {
        PaymentRequest good = new PaymentRequest("UPI", null, null, null, null, "Asha@OKBank", BigDecimal.TEN, "BM23456789");
        assertThat(gateway.charge(good).approved()).isTrue();
        assertThat(gateway.charge(good).upiHandle()).isEqualTo("asha@okbank");
        PaymentRequest fail = new PaymentRequest("UPI", null, null, null, null, "fail@upi", BigDecimal.TEN, "BM23456789");
        assertThat(gateway.charge(fail).approved()).isFalse();
    }

    @Test
    void cardDataNeverAppearsInToString() {
        String text = card("4242424242424242", nextYear, "987").toString();
        assertThat(text).doesNotContain("4242424242424242").doesNotContain("987");
    }

    @Test
    void seatLabelsParse() {
        assertThat(SeatLabel.parse(" c12 ")).isEqualTo(new SeatLabel("C", 12));
        assertThat(SeatLabel.parse("A1").toString()).isEqualTo("A1");
        for (String bad : new String[]{"", "1A", "A0", "AA1", "A123", "A1; DROP TABLE"}) {
            assertThatThrownBy(() -> SeatLabel.parse(bad)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
