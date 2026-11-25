package com.bookmatrix.controller;

import com.bookmatrix.dto.Requests;
import com.bookmatrix.dto.Views.SessionInfo;
import com.bookmatrix.dto.Views.UserView;
import com.bookmatrix.model.User;
import com.bookmatrix.security.CsrfTokens;
import com.bookmatrix.security.SessionKeys;
import com.bookmatrix.security.SessionUser;
import com.bookmatrix.service.AuthService;
import com.bookmatrix.service.UserService;
import com.bookmatrix.util.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Registration, login, logout, current session. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    public AuthController(AuthService authService, UserService userService) {
        this.authService = authService;
        this.userService = userService;
    }

    /** Returns the CSRF token (creating a session if needed) and who is logged in, if anyone. */
    @GetMapping("/session")
    public ApiResponse<SessionInfo> session(HttpServletRequest request) {
        String token = CsrfTokens.getOrCreate(request);
        Object current = request.getAttribute(SessionKeys.CURRENT_USER);
        UserView user = current instanceof SessionUser su ? userService.getProfile(su) : null;
        return ApiResponse.ok(new SessionInfo(user, token));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserView>> register(@RequestBody Requests.Register body) {
        UserView user = authService.register(body);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(user, "Account created. You can now log in."));
    }

    /**
     * Session-fixation protection: the old session is thrown away and a brand-new session id is
     * issued on login, so an attacker who planted a session id beforehand gains nothing.
     * The CSRF token is rotated at the same time.
     */
    @PostMapping("/login")
    public ApiResponse<SessionInfo> login(@RequestBody Requests.Login body, HttpServletRequest request) {
        User user = authService.authenticate(body);
        HttpSession old = request.getSession(false);
        if (old != null) {
            old.invalidate();
        }
        HttpSession session = request.getSession(true);
        session.setAttribute(SessionKeys.USER_ID, user.getId());
        String token = CsrfTokens.rotate(session);
        return ApiResponse.ok(new SessionInfo(UserView.from(user), token), "Welcome back, " + user.getFullName() + ".");
    }

    @PostMapping("/logout")
    public ApiResponse<SessionInfo> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        String token = CsrfTokens.getOrCreate(request);   // fresh anonymous session + token
        return ApiResponse.ok(new SessionInfo(null, token), "You have been logged out.");
    }
}
