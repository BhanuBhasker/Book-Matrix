package com.bookmatrix.controller;

import com.bookmatrix.dto.Requests;
import com.bookmatrix.dto.Views.BookingConfirmation;
import com.bookmatrix.dto.Views.BookingView;
import com.bookmatrix.dto.Views.PaymentResult;
import com.bookmatrix.security.SessionUser;
import com.bookmatrix.service.BookingService;
import com.bookmatrix.util.ApiResponse;
import com.bookmatrix.util.PageRequest;
import com.bookmatrix.util.PageResult;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The logged-in user's own bookings. Ownership is checked in BookingService. */
@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<BookingConfirmation>> book(SessionUser user,
                                                                 @RequestBody Requests.CreateBooking body) {
        BookingConfirmation result = bookingService.book(user, body);
        HttpStatus status = result.duplicate() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(ApiResponse.ok(result, result.message()));
    }

    @GetMapping
    public ApiResponse<PageResult<BookingView>> mine(SessionUser user,
                                                     @RequestParam(required = false) String q,
                                                     @RequestParam(required = false) String status,
                                                     @RequestParam(required = false) Integer page,
                                                     @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(bookingService.listMine(user, q, status, PageRequest.of(page, size)));
    }

    @GetMapping("/{reference}")
    public ApiResponse<BookingView> get(SessionUser user, @PathVariable String reference) {
        return ApiResponse.ok(bookingService.getMine(user, reference));
    }

    /** Pays for a held booking through the demo payment gateway. Declined = 402 PAYMENT_DECLINED (hold kept, retry allowed). */
    @PostMapping("/{reference}/pay")
    public ApiResponse<PaymentResult> pay(SessionUser user, @PathVariable String reference, @RequestBody Requests.Pay body) {
        PaymentResult result = bookingService.pay(user, reference, body);
        return ApiResponse.ok(result, result.message());
    }

    @PostMapping("/{reference}/cancel")
    public ApiResponse<BookingView> cancel(SessionUser user, @PathVariable String reference,
                                           @RequestBody(required = false) Requests.CancelBooking body) {
        BookingView cancelled = bookingService.cancelMine(user, reference, body);
        return ApiResponse.ok(cancelled, cancelled.paidAt() != null
                ? "Booking cancelled. Your tickets were released and the full amount was refunded."
                : "Hold released. Nothing was charged.");
    }

    /** Plain-text receipt built with StringBuilder, downloaded as a .txt file. */
    @GetMapping("/{reference}/receipt")
    public ResponseEntity<String> receipt(SessionUser user, @PathVariable String reference) {
        String text = bookingService.receipt(user, reference);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "plain", java.nio.charset.StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"bookmatrix-" + reference.toUpperCase() + ".txt\"")
                .body(text);
    }
}
