package com.bookmatrix.controller;

import com.bookmatrix.dto.Views.CategoryView;
import com.bookmatrix.dto.Views.EventView;
import com.bookmatrix.dto.Views.HomeView;
import com.bookmatrix.dto.Views.SeatMapView;
import com.bookmatrix.service.BookingService;
import com.bookmatrix.service.EventService;
import com.bookmatrix.util.ApiResponse;
import com.bookmatrix.util.PageResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Public, read-only event discovery. */
@RestController
@RequestMapping("/api")
public class EventController {

    private final EventService eventService;
    private final BookingService bookingService;

    public EventController(EventService eventService, BookingService bookingService) {
        this.eventService = eventService;
        this.bookingService = bookingService;
    }

    @GetMapping("/home")
    public ApiResponse<HomeView> home() {
        return ApiResponse.ok(eventService.home());
    }

    /**
     * Search/filter/sort/paginate. Recognised parameters: q, category, city, from, to,
     * minPrice, maxPrice, status, featured, sort, page, size. Unknown parameters are ignored.
     */
    @GetMapping("/events")
    public ApiResponse<PageResult<EventView>> search(@RequestParam Map<String, String> params) {
        return ApiResponse.ok(eventService.search(params, false));
    }

    @GetMapping("/events/{id}")
    public ApiResponse<EventView> get(@PathVariable long id) {
        return ApiResponse.ok(eventService.getPublic(id));
    }

    /** Seat map of a reserved-seating event: which seats exist and which are taken (no customer details). */
    @GetMapping("/events/{id}/seats")
    public ApiResponse<SeatMapView> seats(@PathVariable long id) {
        return ApiResponse.ok(bookingService.seatMap(id));
    }

    @GetMapping("/categories")
    public ApiResponse<List<CategoryView>> categories() {
        return ApiResponse.ok(eventService.categories());
    }

    @GetMapping("/cities")
    public ApiResponse<List<String>> cities() {
        return ApiResponse.ok(eventService.cities());
    }
}
