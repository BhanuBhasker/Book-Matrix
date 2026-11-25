package com.bookmatrix.model;

import java.time.LocalDateTime;

/** A registered person. Extends BaseEntity (inheritance) and keeps its data private (encapsulation). */
public class User extends BaseEntity {

    private String fullName;
    private String email;
    private String phone;
    private String passwordHash;
    private Role role = Role.USER;
    private AccountStatus status = AccountStatus.ACTIVE;
    private LocalDateTime updatedAt;
    private LocalDateTime lastLoginAt;
    private LocalDateTime deactivatedAt;

    public User() {
        super();
    }

    /** Constructor used when registering a brand-new user (no id yet). */
    public User(String fullName, String email, String passwordHash) {
        this();
        this.fullName = fullName;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    @Override
    public String describe() {
        return fullName + " <" + email + ">, " + role;
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }
    public AccountStatus getStatus() { return status; }
    public void setStatus(AccountStatus status) { this.status = status; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public LocalDateTime getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(LocalDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }
    public LocalDateTime getDeactivatedAt() { return deactivatedAt; }
    public void setDeactivatedAt(LocalDateTime deactivatedAt) { this.deactivatedAt = deactivatedAt; }
}
