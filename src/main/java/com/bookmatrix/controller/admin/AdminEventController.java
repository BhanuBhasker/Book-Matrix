package com.bookmatrix.controller.admin;

import com.bookmatrix.dto.Requests;
import com.bookmatrix.dto.Views.BookingView;
import com.bookmatrix.dto.Views.DeleteOutcome;
import com.bookmatrix.dto.Views.EventCancelled;
import com.bookmatrix.dto.Views.EventView;
import com.bookmatrix.model.Category;
import com.bookmatrix.security.SessionUser;
import com.bookmatrix.service.BookingService;
import com.bookmatrix.service.EventService;
import com.bookmatrix.util.ApiResponse;
import com.bookmatrix.util.PageRequest;
import com.bookmatrix.util.PageResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Admin event CRUD + status control. */
@RestController
@RequestMapping("/api/admin")
public class AdminEventController {

    private final EventService eventService;
    private final BookingService bookingService;

    public AdminEventController(EventService eventService, BookingService bookingService) {
        this.eventService = eventService;
        this.bookingService = bookingService;
    }

    /** Same filters as the public search plus archived=EXCLUDE|ONLY|INCLUDE; status defaults to ALL. */
    @GetMapping("/events")
    public ApiResponse<PageResult<EventView>> search(@RequestParam Map<String, String> params) {
        return ApiResponse.ok(eventService.search(params, true));
    }

    @GetMapping("/events/{id}")
    public ApiResponse<EventView> get(@PathVariable long id) {
        return ApiResponse.ok(eventService.getForAdmin(id));
    }

    @PostMapping("/events")
    public ResponseEntity<ApiResponse<EventView>> create(SessionUser admin, @RequestBody Requests.EventForm body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(eventService.create(admin, body), "Event created."));
    }

    @PutMapping("/events/{id}")
    public ApiResponse<EventView> update(SessionUser admin, @PathVariable long id, @RequestBody Requests.EventForm body) {
        return ApiResponse.ok(eventService.update(admin, id, body), "Event updated.");
    }

    @PostMapping("/events/{id}/cancel")
    public ApiResponse<EventCancelled> cancel(SessionUser admin, @PathVariable long id,
                                              @RequestBody(required = false) Requests.CancelEvent body) {
        EventCancelled result = eventService.cancel(admin, id, body == null ? null : body.reason());
        return ApiResponse.ok(result, result.message());
    }

    @PatchMapping("/events/{id}/featured")
    public ApiResponse<EventView> featured(SessionUser admin, @PathVariable long id, @RequestBody Requests.Featured body) {
        return ApiResponse.ok(eventService.setFeatured(admin, id, body.featured()));
    }

    @DeleteMapping("/events/{id}")
    public ApiResponse<DeleteOutcome> delete(SessionUser admin, @PathVariable long id) {
        DeleteOutcome outcome = eventService.delete(admin, id);
        return ApiResponse.ok(outcome, outcome.message());
    }

    @PostMapping("/events/{id}/restore")
    public ApiResponse<EventView> restore(SessionUser admin, @PathVariable long id) {
        return ApiResponse.ok(eventService.restore(admin, id), "Event restored from the archive.");
    }

    @GetMapping("/events/{id}/bookings")
    public ApiResponse<PageResult<BookingView>> bookings(@PathVariable long id,
                                                         @RequestParam(required = false) String q,
                                                         @RequestParam(required = false) String status,
                                                         @RequestParam(required = false) Integer page,
                                                         @RequestParam(required = false) Integer size) {
        eventService.getForAdmin(id);   // 404 if the event does not exist
        return ApiResponse.ok(bookingService.listAll(q, status, id, null, PageRequest.of(page, size)));
    }

    @PostMapping("/categories")
    public ResponseEntity<ApiResponse<Category>> createCategory(SessionUser admin, @RequestBody Requests.NewCategory body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(eventService.createCategory(admin, body), "Category added."));
    }
}
