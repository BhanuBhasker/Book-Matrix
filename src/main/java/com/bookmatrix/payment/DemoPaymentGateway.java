package com.bookmatrix.payment;

import com.bookmatrix.exception.ValidationException;
import com.bookmatrix.util.TextUtils;
import com.bookmatrix.validation.Validator;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.YearMonth;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * A simulated payment gateway for demonstrations. NO REAL MONEY MOVES.
 * It behaves like a real one from the app's point of view: it validates the details and
 * approves or declines. Test numbers (any future expiry, any 3-digit CVV):
 *   4242 4242 4242 4242  Visa        -> approved
 *   5555 5555 5555 4444  Mastercard  -> approved
 *   4000 0000 0000 0002  Visa        -> declined: card declined
 *   4000 0000 0000 9995  Visa        -> declined: insufficient funds
 *   4000 0000 0000 0069  Visa        -> declined: card expired (bank says)
 *   any other Luhn-valid number      -> approved
 * UPI: any id like name@bank is approved, except fail@upi (declined).
 */
@Component
public class DemoPaymentGateway implements PaymentGateway {

    /** Test cards that always decline, with the reason a bank would give. */
    static final Map<String, String> DECLINING_CARDS = Map.of(
            "4000000000000002", "Your card was declined by the bank.",
            "4000000000009995", "Insufficient funds on this card.",
            "4000000000000069", "The bank reports this card as expired.");

    private static final char[] REF_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public String name() {
        return "BookMatrix Demo Gateway";
    }

    @Override
    public GatewayResult charge(PaymentRequest request) {
        String method = request.method() == null ? "" : request.method().trim().toUpperCase(Locale.ROOT);
        return switch (method) {
            case "CARD" -> chargeCard(request);
            case "UPI" -> chargeUpi(request);
            default -> throw ValidationException.forField("method", "Choose card or UPI.");
        };
    }

    private GatewayResult chargeCard(PaymentRequest r) {
        String digits = CardValidator.digitsOnly(r.cardNumber());
        String brand = CardValidator.brand(digits);
        Validator v = new Validator()
                .required("cardNumber", digits, "Enter the card number.")
                .required("cardName", TextUtils.clean(r.cardName()), "Enter the name on the card.")
                .length("cardName", TextUtils.clean(r.cardName()), 2, 60)
                .required("expiry", r.expiry(), "Enter the expiry date (MM/YY).")
                .required("cvv", r.cvv(), "Enter the security code.");
        if (!digits.isEmpty()) {
            v.check(CardValidator.passesLuhn(digits), "cardNumber", "This card number is not valid. Check the digits.");
        }
        if (r.expiry() != null && !r.expiry().isBlank()) {
            Optional<YearMonth> expiry = CardValidator.parseExpiry(r.expiry());
            v.check(expiry.isPresent(), "expiry", "Use the format MM/YY.");
            expiry.ifPresent(e -> v.check(!e.isBefore(YearMonth.now()), "expiry", "This card has expired."));
        }
        if (r.cvv() != null && !r.cvv().isBlank()) {
            v.check(CardValidator.isValidCvv(r.cvv().trim(), brand), "cvv",
                    "Amex".equals(brand) ? "Enter the 4-digit code." : "Enter the 3-digit code on the back.");
        }
        v.throwIfInvalid();

        String last4 = digits.substring(digits.length() - 4);
        String decline = DECLINING_CARDS.get(digits);
        return new GatewayResult(decline == null, newReference(), "CARD", brand, last4, null, decline);
    }

    private GatewayResult chargeUpi(PaymentRequest r) {
        String handle = r.upiId() == null ? null : r.upiId().trim().toLowerCase(Locale.ROOT);
        if (!CardValidator.isValidUpi(handle)) {
            throw ValidationException.forField("upiId", "Enter a UPI ID like name@bank.");
        }
        boolean approved = !"fail@upi".equals(handle);
        return new GatewayResult(approved, newReference(), "UPI", null, null, handle,
                approved ? null : "The UPI payment request was declined.");
    }

    /** Gateway transaction reference, e.g. "PAY_7KQ2M9XWD4H8RT3B". */
    static String newReference() {
        StringBuilder sb = new StringBuilder("PAY_");
        for (int i = 0; i < 16; i++) {
            sb.append(REF_CHARS[RANDOM.nextInt(REF_CHARS.length)]);
        }
        return sb.toString();
    }
}
