package com.bookmatrix.unit;

import com.bookmatrix.db.Database;
import com.bookmatrix.exception.BookMatrixException;
import com.bookmatrix.exception.BookingNotFoundException;
import com.bookmatrix.exception.ConflictException;
import com.bookmatrix.exception.CsvFormatException;
import com.bookmatrix.exception.DatabaseException;
import com.bookmatrix.exception.DatabaseUnavailableException;
import com.bookmatrix.exception.EventNotFoundException;
import com.bookmatrix.exception.InsufficientTicketsException;
import com.bookmatrix.exception.InvalidBookingException;
import com.bookmatrix.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.SQLTransientConnectionException;

import static org.assertj.core.api.Assertions.assertThat;

class ExceptionsTest {

    @Test
    void customHierarchyAndPolymorphicStatus() {
        BookMatrixException e = new InsufficientTicketsException(5, 2);
        assertThat(e).isInstanceOf(InvalidBookingException.class).isInstanceOf(RuntimeException.class);
        assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(e.code()).isEqualTo("INSUFFICIENT_TICKETS");
        assertThat(e.getMessage()).contains("Only 2");
        assertThat(new InsufficientTicketsException(1, 0).getMessage()).contains("sold out");
        assertThat(new EventNotFoundException(7)).isInstanceOf(NotFoundException.class)
                .extracting(BookMatrixException::status).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(new BookingNotFoundException("BM").code()).isEqualTo("BOOKING_NOT_FOUND");
    }

    @Test
    void csvFormatExceptionIsChecked() {
        assertThat(Exception.class.isAssignableFrom(CsvFormatException.class)).isTrue();
        assertThat(RuntimeException.class.isAssignableFrom(CsvFormatException.class)).isFalse();
        assertThat(new CsvFormatException(4, "bad").getLineNumber()).isEqualTo(4);
    }

    @Test
    void sqlExceptionsAreTranslatedSafely() {
        assertThat(Database.translate(new SQLTransientConnectionException("pool timeout")))
                .isInstanceOf(DatabaseUnavailableException.class)
                .extracting(BookMatrixException::status).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(Database.translate(new SQLException("refused", "08S01"))).isInstanceOf(DatabaseUnavailableException.class);
        assertThat(Database.translate(new SQLIntegrityConstraintViolationException("dup", "23000", 1062)))
                .isInstanceOf(ConflictException.class);
        assertThat(Database.translate(new SQLException("check", "HY000", 3819))).isInstanceOf(ConflictException.class);
        BookMatrixException other = Database.translate(new SQLException("syntax error near users", "42000", 1064));
        assertThat(other).isInstanceOf(DatabaseException.class);
        assertThat(other.getMessage()).doesNotContain("users");    // internal SQL details never reach the client
    }
}
