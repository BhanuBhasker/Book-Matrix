package com.bookmatrix.security;

import com.bookmatrix.config.BookMatrixProperties;
import com.bookmatrix.dao.AuditLogDao;
import com.bookmatrix.dao.UserDao;
import com.bookmatrix.model.Role;
import com.bookmatrix.model.User;
import com.bookmatrix.util.TextUtils;
import com.bookmatrix.validation.PasswordPolicy;
import com.bookmatrix.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Creates the FIRST administrator from environment variables, only when the database has no
 * admin at all. Public registration can never create admins, and no admin password is
 * hard-coded in the source code.
 *   BOOKMATRIX_ADMIN_EMAIL, BOOKMATRIX_ADMIN_PASSWORD, BOOKMATRIX_ADMIN_NAME (optional)
 */
@Component
public class AdminBootstrap {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final BookMatrixProperties properties;
    private final UserDao userDao;
    private final AuditLogDao auditLogDao;
    private final PasswordHasher hasher;

    public AdminBootstrap(BookMatrixProperties properties, UserDao userDao, AuditLogDao auditLogDao,
                          PasswordHasher hasher) {
        this.properties = properties;
        this.userDao = userDao;
        this.auditLogDao = auditLogDao;
        this.hasher = hasher;
    }

    public void bootstrap(Connection conn) throws SQLException {
        if (userDao.hasAnyAdmin(conn)) {
            return;
        }
        BookMatrixProperties.Admin admin = properties.admin();
        String email = admin == null ? null : TextUtils.normalizeEmail(admin.email());
        String password = admin == null ? null : admin.password();
        if (email == null || TextUtils.isBlank(password)) {
            log.warn("No administrator exists yet. Set BOOKMATRIX_ADMIN_EMAIL and BOOKMATRIX_ADMIN_PASSWORD "
                    + "(e.g. in .env) and restart to create one. See README -> 'First administrator'.");
            return;
        }
        Validator v = new Validator().email("email", email);
        Optional<String> weak = PasswordPolicy.problem(password, email);
        if (!v.isValid() || weak.isPresent()) {
            log.error("Admin bootstrap skipped: BOOKMATRIX_ADMIN_EMAIL/PASSWORD invalid ({}).",
                    weak.orElse("invalid email"));
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        Optional<User> existing = userDao.findByEmail(conn, email);
        if (existing.isPresent()) {
            userDao.updateRole(conn, existing.get().getId(), Role.ADMIN);
            auditLogDao.insert(conn, null, "ADMIN_BOOTSTRAP", "USER", existing.get().getId(), "Promoted to admin", now);
            log.info("Promoted existing account {} to administrator.", email);
            return;
        }
        String name = TextUtils.isBlank(admin.name()) ? "Administrator" : TextUtils.clean(admin.name());
        User user = new User(name, email, hasher.hash(password));
        user.setRole(Role.ADMIN);
        long id = userDao.insert(conn, user, now);
        auditLogDao.insert(conn, null, "ADMIN_BOOTSTRAP", "USER", id, "First administrator created", now);
        log.info("Created first administrator account {} (password taken from environment).", email);
    }
}
