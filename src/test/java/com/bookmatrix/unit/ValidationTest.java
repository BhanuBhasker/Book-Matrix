package com.bookmatrix.unit;

import com.bookmatrix.exception.ValidationException;
import com.bookmatrix.validation.PasswordPolicy;
import com.bookmatrix.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidationTest {

    @Test
    void collectsEveryFieldErrorInFormOrder() {
        Validator v = new Validator()
                .required("name", "  ", "Enter a name.")
                .email("email", "not-an-email")
                .range("quantity", 0, 1, 10, "1 to 10");
        assertThatThrownBy(v::throwIfInvalid)
                .isInstanceOf(ValidationException.class)
                .satisfies(e -> assertThat(((ValidationException) e).getFieldErrors())
                        .containsOnlyKeys("name", "email", "quantity"));
    }

    @Test
    void validInputPasses() {
        new Validator().required("name", "Asha", "x").length("name", "Asha", 2, 100)
                .email("email", "asha@example.com").phone("phone", "+91 98765 43210")
                .money("price", new BigDecimal("799.50"), new BigDecimal("1000000"))
                .throwIfInvalid();
    }

    @ParameterizedTest
    @ValueSource(strings = {"a@b", "no-at-sign.com", "spaces in@x.com", "@x.com", "a@.com"})
    void rejectsInvalidEmails(String email) {
        assertThat(new Validator().email("email", email).isValid()).isFalse();
    }

    @Test
    void moneyRules() {
        assertThat(new Validator().money("p", new BigDecimal("-1"), BigDecimal.TEN).isValid()).isFalse();
        assertThat(new Validator().money("p", new BigDecimal("1.999"), BigDecimal.TEN).isValid()).isFalse();
        assertThat(new Validator().money("p", new BigDecimal("11"), BigDecimal.TEN).isValid()).isFalse();
        assertThat(new Validator().money("p", BigDecimal.ZERO, BigDecimal.TEN).isValid()).isTrue();
    }

    @Test
    void passwordPolicy() {
        assertThat(PasswordPolicy.problem("short1", null)).contains("Use at least 8 characters.");
        assertThat(PasswordPolicy.problem("onlyletters", null)).isPresent();
        assertThat(PasswordPolicy.problem("12345678", null)).isPresent();
        assertThat(PasswordPolicy.problem("a".repeat(80) + "1", null)).isPresent();
        assertThat(PasswordPolicy.problem("user1@x.com", "user1@x.com")).isPresent();
        assertThat(PasswordPolicy.problem(" padded123 ", null)).isPresent();
        assertThat(PasswordPolicy.problem("secret123", "a@b.com")).isEmpty();
    }
}
