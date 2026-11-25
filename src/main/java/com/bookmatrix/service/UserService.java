package com.bookmatrix.service;

import com.bookmatrix.dao.AuditLogDao;
import com.bookmatrix.dao.BookingDao;
import com.bookmatrix.dao.EventDao;
import com.bookmatrix.dao.UserDao;
import com.bookmatrix.db.Database;
import com.bookmatrix.dto.Requests;
import com.bookmatrix.dto.Views.AccountClosed;
import com.bookmatrix.dto.Views.AdminUserView;
import com.bookmatrix.dto.Views.UserView;
import com.bookmatrix.exception.ConflictException;
import com.bookmatrix.exception.UnauthorizedActionException;
import com.bookmatrix.exception.UserNotFoundException;
import com.bookmatrix.exception.ValidationException;
import com.bookmatrix.model.AccountStatus;
import com.bookmatrix.model.Booking;
import com.bookmatrix.model.Role;
import com.bookmatrix.model.User;
import com.bookmatrix.security.PasswordHasher;
import com.bookmatrix.security.SessionUser;
import com.bookmatrix.util.PageRequest;
import com.bookmatrix.util.PageResult;
import com.bookmatrix.util.TextUtils;
import com.bookmatrix.util.TimeProvider;
import com.bookmatrix.validation.PasswordPolicy;
import com.bookmatrix.validation.Validator;
import org.springframework.stereotype.Service;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/** Profile management for users, and user management for admins. */
@Service
public class UserService {

    private final Database db;
    private final UserDao userDao;
    private final BookingDao bookingDao;
    private final EventDao eventDao;
    private final AuditLogDao auditLogDao;
    private final PasswordHasher hasher;
    private final TimeProvider time;
    private final BookingService bookingService;

    public UserService(Database db, UserDao userDao, BookingDao bookingDao, EventDao eventDao,
                       AuditLogDao auditLogDao, PasswordHasher hasher, TimeProvider time, BookingService bookingService) {
        this.db = db;
        this.userDao = userDao;
        this.bookingDao = bookingDao;
        this.eventDao = eventDao;
        this.auditLogDao = auditLogDao;
        this.hasher = hasher;
        this.time = time;
        this.bookingService = bookingService;
    }

    // ------------------------------------------------------------------ own profile

    public UserView getProfile(SessionUser me) {
        return UserView.from(loadUser(me.id()));
    }

    /** Only name and phone can be changed here. Email, role and status are not part of the request at all. */
    public UserView updateProfile(SessionUser me, Requests.ProfileUpdate request) {
        String name = TextUtils.clean(request.fullName());
        String phone = TextUtils.clean(request.phone());
        validateNameAndPhone(name, phone);
        return db.inTransaction(conn -> {
            userDao.updateProfile(conn, me.id(), name, phone);
            auditLogDao.insert(conn, me.id(), "PROFILE_UPDATED", "USER", me.id(), null, time.now());
            return UserView.from(userDao.findById(conn, me.id()).orElseThrow(() -> new UserNotFoundException(me.id())));
        });
    }

    public void changePassword(SessionUser me, Requests.PasswordChange request) {
        User user = loadUser(me.id());
        Validator v = new Validator().required("currentPassword", request.currentPassword(), "Enter your current password.");
        PasswordPolicy.problem(request.newPassword(), user.getEmail()).ifPresent(p -> v.check(false, "newPassword", p));
        v.required("confirmPassword", request.confirmPassword(), "Confirm your new password.");
        if (request.newPassword() != null && !request.newPassword().equals(request.confirmPassword())) {
            v.check(false, "confirmPassword", "Passwords do not match.");
        }
        v.throwIfInvalid();
        if (!hasher.matches(request.currentPassword(), user.getPasswordHash())) {
            throw ValidationException.forField("currentPassword", "Your current password is incorrect.");
        }
        if (hasher.matches(request.newPassword(), user.getPasswordHash())) {
            throw ValidationException.forField("newPassword", "Choose a password you are not already using.");
        }
        String hash = hasher.hash(request.newPassword());
        db.inTransaction(conn -> {
            userDao.updatePassword(conn, me.id(), hash);
            auditLogDao.insert(conn, me.id(), "PASSWORD_CHANGED", "USER", me.id(), null, time.now());
            return null;
        });
    }

    /**
     * Closing an account.
     *  - No booking history at all  -> the user row is deleted for real.
     *  - Has booking history         -> upcoming bookings are cancelled (tickets go back on sale)
     *                                   and the account is DEACTIVATED, so the history stays valid.
     * Everything happens in ONE transaction.
     */
    public AccountClosed closeAccount(SessionUser me, Requests.AccountDelete request) {
        User user = loadUser(me.id());
        if (!"DELETE".equals(request.confirmation())) {
            throw ValidationException.forField("confirmation", "Type DELETE to confirm.");
        }
        if (!hasher.matches(request.password(), user.getPasswordHash())) {
            throw ValidationException.forField("password", "Your password is incorrect.");
        }
        if (user.isAdmin()) {
            db.inTransaction(conn -> {
                if (userDao.countActiveAdminsForUpdate(conn) <= 1) {
                    throw new ConflictException("LAST_ADMIN", "You are the only administrator, so this account cannot be closed.");
                }
                return null;
            });
        }
        LocalDateTime now = time.now();
        return db.inTransaction(conn -> {
            if (bookingDao.countForUser(conn, me.id()) == 0) {
                auditLogDao.insert(conn, null, "ACCOUNT_DELETED", "USER", me.id(), user.getEmail(), now);
                userDao.deleteById(conn, me.id());
                return new AccountClosed("DELETED", 0, "Your account has been permanently deleted.");
            }
            int cancelled = bookingService.cancelUpcomingForUser(conn, me.id(), me.id(), "Account closed by user", now);
            userDao.updateStatus(conn, me.id(), AccountStatus.DEACTIVATED, now);
            auditLogDao.insert(conn, me.id(), "ACCOUNT_DEACTIVATED", "USER", me.id(),
                    "Self-service closure, " + cancelled + " upcoming booking(s) cancelled", now);
            return new AccountClosed("DEACTIVATED", cancelled,
                    "Your account has been closed. Past bookings are kept for our records"
                            + (cancelled > 0 ? " and " + cancelled + " upcoming booking(s) were cancelled and refunded." : "."));
        });
    }

    // ------------------------------------------------------------------ admin

    public PageResult<AdminUserView> search(String text, String role, String status, PageRequest page) {
        Role roleFilter = parseEnum(Role.class, role);
        AccountStatus statusFilter = parseEnum(AccountStatus.class, status);
        String q = TextUtils.truncate(TextUtils.clean(text), 100);
        return db.query(conn -> PageResult.of(
                userDao.search(conn, q, roleFilter, statusFilter, page),
                page, userDao.count(conn, q, roleFilter, statusFilter))).map(AdminUserView::from);
    }

    public UserView getUser(long id) {
        return UserView.from(loadUser(id));
    }

    public UserView adminUpdate(SessionUser admin, long id, Requests.AdminUserUpdate request) {
        loadUser(id);
        String name = TextUtils.clean(request.fullName());
        String phone = TextUtils.clean(request.phone());
        validateNameAndPhone(name, phone);
        return db.inTransaction(conn -> {
            userDao.updateProfile(conn, id, name, phone);
            auditLogDao.insert(conn, admin.id(), "USER_EDITED", "USER", id, null, time.now());
            return UserView.from(userDao.findById(conn, id).orElseThrow());
        });
    }

    /** Activate or deactivate. Admins cannot lock themselves out or remove the last active admin. */
    public UserView setStatus(SessionUser admin, long id, AccountStatus newStatus) {
        if (admin.id() == id) {
            throw new UnauthorizedActionException("You cannot change the status of your own account.");
        }
        LocalDateTime now = time.now();
        return db.inTransaction(conn -> {
            User target = userDao.findById(conn, id).orElseThrow(() -> new UserNotFoundException(id));
            if (target.getStatus() == newStatus) {
                throw new ConflictException("NO_CHANGE", "This account is already " + newStatus.name().toLowerCase(Locale.ROOT) + ".");
            }
            if (newStatus == AccountStatus.DEACTIVATED && target.isAdmin() && userDao.countActiveAdminsForUpdate(conn) <= 1) {
                throw new ConflictException("LAST_ADMIN", "This is the last active administrator and cannot be deactivated.");
            }
            userDao.updateStatus(conn, id, newStatus, now);
            auditLogDao.insert(conn, admin.id(), newStatus == AccountStatus.ACTIVE ? "USER_ACTIVATED" : "USER_DEACTIVATED",
                    "USER", id, target.getEmail(), now);
            return UserView.from(userDao.findById(conn, id).orElseThrow());
        });
    }

    public UserView changeRole(SessionUser admin, long id, String roleText) {
        Role newRole = parseEnum(Role.class, roleText);
        if (newRole == null) {
            throw ValidationException.forField("role", "Role must be USER or ADMIN.");
        }
        if (admin.id() == id) {
            throw new UnauthorizedActionException("You cannot change your own role.");
        }
        LocalDateTime now = time.now();
        return db.inTransaction(conn -> {
            User target = userDao.findById(conn, id).orElseThrow(() -> new UserNotFoundException(id));
            if (target.getRole() == newRole) {
                throw new ConflictException("NO_CHANGE", "This account already has the role " + newRole + ".");
            }
            if (newRole == Role.USER && target.isActive() && userDao.countActiveAdminsForUpdate(conn) <= 1) {
                throw new ConflictException("LAST_ADMIN", "The last active administrator cannot be demoted.");
            }
            userDao.updateRole(conn, id, newRole);
            auditLogDao.insert(conn, admin.id(), "USER_ROLE_CHANGED", "USER", id, target.getEmail() + " -> " + newRole, now);
            return UserView.from(userDao.findById(conn, id).orElseThrow());
        });
    }

    // ------------------------------------------------------------------ helpers

    private User loadUser(long id) {
        return db.query(conn -> userDao.findById(conn, id)).orElseThrow(() -> new UserNotFoundException(id));
    }

    private static void validateNameAndPhone(String name, String phone) {
        Validator v = new Validator()
                .required("fullName", name, "Enter a name.")
                .length("fullName", name, 2, 100)
                .phone("phone", phone);
        if (name != null && !v.hasError("fullName")) {
            v.check(name.matches(AuthService.NAME_PATTERN), "fullName", "Use letters, spaces, apostrophes or hyphens only.");
        }
        v.throwIfInvalid();
    }

    /** Generic method with a bounded type parameter: works for any enum class. */
    static <E extends Enum<E>> E parseEnum(Class<E> type, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
