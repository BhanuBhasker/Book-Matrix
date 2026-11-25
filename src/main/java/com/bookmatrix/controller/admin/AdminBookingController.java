package com.bookmatrix.controller.admin;

import com.bookmatrix.dto.Requests;
import com.bookmatrix.dto.Views.BookingView;
import com.bookmatrix.security.SessionUser;
import com.bookmatrix.service.BookingService;
import com.bookmatrix.util.ApiResponse;
import com.bookmatrix.util.PageRequest;
import com.bookmatrix.util.PageResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/bookings")
public class AdminBookingController {

    private final BookingService bookingService;

    public AdminBookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @GetMapping
    public ApiResponse<PageResult<BookingView>> search(@RequestParam(required = false) String q,
                                                       @RequestParam(required = false) String status,
                                                       @RequestParam(required = false) Long eventId,
                                                       @RequestParam(required = false) Integer page,
                                                       @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(bookingService.listAll(q, status, eventId, null, PageRequest.of(page, size)));
    }

    @GetMapping("/{reference}")
    public ApiResponse<BookingView> get(@PathVariable String reference) {
        return ApiResponse.ok(bookingService.getAny(reference));
    }

    @PostMapping("/{reference}/cancel")
    public ApiResponse<BookingView> cancel(SessionUser admin, @PathVariable String reference,
                                           @RequestBody(required = false) Requests.CancelBooking body) {
        return ApiResponse.ok(bookingService.cancelAsAdmin(admin, reference, body), "Booking cancelled and tickets released.");
    }
}
