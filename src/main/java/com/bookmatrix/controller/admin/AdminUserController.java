package com.bookmatrix.controller.admin;

import com.bookmatrix.dto.Requests;
import com.bookmatrix.dto.Views.AdminUserView;
import com.bookmatrix.dto.Views.BookingView;
import com.bookmatrix.dto.Views.UserView;
import com.bookmatrix.model.AccountStatus;
import com.bookmatrix.security.SessionUser;
import com.bookmatrix.service.BookingService;
import com.bookmatrix.service.UserService;
import com.bookmatrix.util.ApiResponse;
import com.bookmatrix.util.PageRequest;
import com.bookmatrix.util.PageResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Admin user management. Responses never contain passwords or hashes (UserView has no such field). */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final UserService userService;
    private final BookingService bookingService;

    public AdminUserController(UserService userService, BookingService bookingService) {
        this.userService = userService;
        this.bookingService = bookingService;
    }

    @GetMapping
    public ApiResponse<PageResult<AdminUserView>> search(@RequestParam(required = false) String q,
                                                         @RequestParam(required = false) String role,
                                                         @RequestParam(required = false) String status,
                                                         @RequestParam(required = false) Integer page,
                                                         @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(userService.search(q, role, status, PageRequest.of(page, size)));
    }

    @GetMapping("/{id}")
    public ApiResponse<UserView> get(@PathVariable long id) {
        return ApiResponse.ok(userService.getUser(id));
    }

    @PatchMapping("/{id}")
    public ApiResponse<UserView> update(SessionUser admin, @PathVariable long id, @RequestBody Requests.AdminUserUpdate body) {
        return ApiResponse.ok(userService.adminUpdate(admin, id, body), "User updated.");
    }

    @PostMapping("/{id}/activate")
    public ApiResponse<UserView> activate(SessionUser admin, @PathVariable long id) {
        return ApiResponse.ok(userService.setStatus(admin, id, AccountStatus.ACTIVE), "Account activated.");
    }

    @PostMapping("/{id}/deactivate")
    public ApiResponse<UserView> deactivate(SessionUser admin, @PathVariable long id) {
        return ApiResponse.ok(userService.setStatus(admin, id, AccountStatus.DEACTIVATED), "Account deactivated.");
    }

    @PutMapping("/{id}/role")
    public ApiResponse<UserView> role(SessionUser admin, @PathVariable long id, @RequestBody Requests.RoleChange body) {
        return ApiResponse.ok(userService.changeRole(admin, id, body.role()), "Role updated.");
    }

    @GetMapping("/{id}/bookings")
    public ApiResponse<PageResult<BookingView>> bookings(@PathVariable long id,
                                                         @RequestParam(required = false) String status,
                                                         @RequestParam(required = false) Integer page,
                                                         @RequestParam(required = false) Integer size) {
        userService.getUser(id);
        return ApiResponse.ok(bookingService.listAll(null, status, null, id, PageRequest.of(page, size)));
    }
}
