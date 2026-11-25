package com.bookmatrix.controller;

import com.bookmatrix.dto.Requests;
import com.bookmatrix.dto.Views.AccountClosed;
import com.bookmatrix.dto.Views.UserView;
import com.bookmatrix.security.SessionUser;
import com.bookmatrix.service.UserService;
import com.bookmatrix.util.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** "My profile". Always acts on the logged-in user — there is no user id in these URLs to tamper with. */
@RestController
@RequestMapping("/api/me")
public class ProfileController {

    private final UserService userService;

    public ProfileController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ApiResponse<UserView> me(SessionUser user) {
        return ApiResponse.ok(userService.getProfile(user));
    }

    @PatchMapping
    public ApiResponse<UserView> update(SessionUser user, @RequestBody Requests.ProfileUpdate body) {
        return ApiResponse.ok(userService.updateProfile(user, body), "Profile updated.");
    }

    @PutMapping("/password")
    public ApiResponse<Void> changePassword(SessionUser user, @RequestBody Requests.PasswordChange body) {
        userService.changePassword(user, body);
        return ApiResponse.message("Password changed.");
    }

    @DeleteMapping
    public ApiResponse<AccountClosed> close(SessionUser user, @RequestBody Requests.AccountDelete body,
                                            HttpServletRequest request) {
        AccountClosed result = userService.closeAccount(user, body);
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ApiResponse.ok(result, result.message());
    }
}
