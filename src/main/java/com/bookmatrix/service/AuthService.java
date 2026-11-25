package com.bookmatrix.service;

import com.bookmatrix.dao.AuditLogDao;
import com.bookmatrix.dao.UserDao;
import com.bookmatrix.db.Database;
import com.bookmatrix.dto.Requests;
import com.bookmatrix.dto.Views.UserView;
import com.bookmatrix.exception.AuthenticationRequiredException;
import com.bookmatrix.exception.ConflictException;
import com.bookmatrix.exception.TooManyAttemptsException;
import com.bookmatrix.model.Role;
import com.bookmatrix.model.User;
import com.bookmatrix.security.LoginAttemptTracker;
import com.bookmatrix.security.PasswordHasher;
import com.bookmatrix.util.TextUtils;
import com.bookmatrix.util.TimeProvider;
import com.bookmatrix.validation.PasswordPolicy;
import com.bookmatrix.validation.Validator;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

/** Registration and login. Session handling itself lives in AuthController. */
@Service
public class AuthService {

    static final String NAME_PATTERN = "^[\\p{L}][\\p{L} .'-]*$";

    private final Database db;
    private final UserDao userDao;
    private final AuditLogDao auditLogDao;
    private final PasswordHasher hasher;
    private final LoginAttemptTracker attempts;
    private final TimeProvider time;

    public AuthService(Database db, UserDao userDao, AuditLogDao auditLogDao, PasswordHasher hasher,
                       LoginAttemptTracker attempts, TimeProvider time) {
        this.db = db;
        this.userDao = userDao;
        this.auditLogDao = auditLogDao;
        this.hasher = hasher;
        this.attempts = attempts;
        this.time = time;
    }

    /** Creates a normal USER account. The role is fixed here — registration can never create an admin. */
    public UserView register(Requests.Register request) {
        String name = TextUtils.clean(request.fullName());
        String email = TextUtils.normalizeEmail(request.email());
        Validator v = new Validator()
                .required("fullName", name, "Enter your name.")
                .length("fullName", name, 2, 100)
                .required("email", email, "Enter your email address.")
                .email("email", email);
        if (name != null && !v.hasError("fullName")) {
            v.check(name.matches(NAME_PATTERN), "fullName", "Use letters, spaces, apostrophes or hyphens only.");
        }
        PasswordPolicy.problem(request.password(), email).ifPresent(p -> v.check(false, "password", p));
        v.required("confirmPassword", request.confirmPassword(), "Confirm your password.");
        if (request.password() != null && request.confirmPassword() != null
                && !request.password().equals(request.confirmPassword())) {
            v.check(false, "confirmPassword", "Passwords do not match.");
        }
        v.throwIfInvalid();

        String hash = hasher.hash(request.password());     // slow on purpose, done before opening a transaction
        LocalDateTime now = time.now();
        return db.inTransaction(conn -> {
            if (userDao.emailExists(conn, email)) {
                throw new ConflictException("EMAIL_TAKEN", "An account with this email already exists. Try logging in.");
            }
            User user = new User(name, email, hash);
            user.setRole(Role.USER);
            long id = userDao.insert(conn, user, now);
            auditLogDao.insert(conn, id, "USER_REGISTERED", "USER", id, email, now);
            return UserView.from(userDao.findById(conn, id).orElseThrow());
        });
    }

    /** Checks email + password. Returns the user on success; throws a clear exception otherwise. */
    public User authenticate(Requests.Login request) {
        String email = TextUtils.normalizeEmail(request.email());
        new Validator()
                .required("email", email, "Enter your email address.")
                .required("password", request.password(), "Enter your password.")
                .throwIfInvalid();
        if (attempts.isLocked(email)) {
            throw new TooManyAttemptsException();
        }
        Optional<User> found = db.query(conn -> userDao.findByEmail(conn, email));
        if (found.isEmpty()) {
            hasher.simulateCheck(request.password());
            attempts.recordFailure(email);
            throw invalidCredentials();
        }
        User user = found.get();
        if (!hasher.matches(request.password(), user.getPasswordHash())) {
            attempts.recordFailure(email);
            throw invalidCredentials();
        }
        if (!user.isActive()) {
            throw new AuthenticationRequiredException("ACCOUNT_DEACTIVATED",
                    "This account has been deactivated. Contact support if you think this is a mistake.");
        }
        attempts.recordSuccess(email);
        LocalDateTime now = time.now();
        db.query(conn -> {
            userDao.updateLastLogin(conn, user.getId(), now);
            auditLogDao.insert(conn, user.getId(), "USER_LOGIN", "USER", user.getId(), null, now);
            return null;
        });
        user.setLastLoginAt(now);
        return user;
    }

    private static AuthenticationRequiredException invalidCredentials() {
        // same message for "no such email" and "wrong password": does not reveal which emails exist
        return new AuthenticationRequiredException("INVALID_CREDENTIALS", "Email or password is incorrect.");
    }
}
