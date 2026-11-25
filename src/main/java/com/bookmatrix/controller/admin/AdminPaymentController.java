package com.bookmatrix.controller.admin;

import com.bookmatrix.dto.Views.PaymentView;
import com.bookmatrix.service.BookingService;
import com.bookmatrix.util.ApiResponse;
import com.bookmatrix.util.PageRequest;
import com.bookmatrix.util.PageResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Transaction log of the demo payment gateway: charges (successful and declined) and refunds. */
@RestController
@RequestMapping("/api/admin/payments")
public class AdminPaymentController {

    private final BookingService bookingService;

    public AdminPaymentController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @GetMapping
    public ApiResponse<PageResult<PaymentView>> search(@RequestParam(required = false) String q,
                                                       @RequestParam(required = false) String kind,
                                                       @RequestParam(required = false) String status,
                                                       @RequestParam(required = false) Integer page,
                                                       @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(bookingService.listPayments(q, kind, status, PageRequest.of(page, size)));
    }
}
