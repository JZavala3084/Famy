package com.famy.identity.domain;

import com.famy.identity.UserSummary;
import com.famy.shared.Ids;
import com.famy.shared.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "app_user")
public class AppUser extends BaseEntity<UUID> {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String email;

    private Instant emailVerifiedAt;

    private String passwordHash;

    @Column(nullable = false)
    private String displayName;

    private String avatarUrl;
    private String color;
    private LocalDate birthday;

    @Column(nullable = false)
    private String timezone;

    @Column(nullable = false)
    private String locale;

    @Column(nullable = false)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected AppUser() {
    }

    public AppUser(String email, String displayName, String timezone, String locale) {
        this.id = Ids.newId();
        this.email = normalizeEmail(email);
        this.displayName = displayName.strip();
        this.timezone = timezone;
        this.locale = locale;
        this.currency = "USD";
        this.status = UserStatus.ACTIVE;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }

    public UserSummary toSummary() {
        return new UserSummary(id, email, displayName, avatarUrl, color, isEmailVerified(), currency, timezone,
                birthday);
    }

    public boolean isEmailVerified() {
        return emailVerifiedAt != null;
    }

    public boolean isActive() {
        return status == UserStatus.ACTIVE;
    }

    public boolean hasPassword() {
        return passwordHash != null;
    }

    public void markEmailVerified() {
        if (emailVerifiedAt == null) {
            emailVerifiedAt = Instant.now();
            touch();
        }
    }

    public void changePasswordHash(String hash) {
        this.passwordHash = hash;
        touch();
    }

    public void updateProfile(String displayName, String avatarUrl, String color, LocalDate birthday,
            String timezone, String locale, String currency) {
        if (displayName != null) {
            this.displayName = displayName.strip();
        }
        if (avatarUrl != null) {
            this.avatarUrl = avatarUrl.isBlank() ? null : avatarUrl;
        }
        if (color != null) {
            this.color = color;
        }
        if (birthday != null) {
            this.birthday = birthday;
        }
        if (timezone != null) {
            this.timezone = timezone;
        }
        if (locale != null) {
            this.locale = locale;
        }
        if (currency != null) {
            this.currency = currency;
        }
        touch();
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    @Override
    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public Instant getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public String getColor() {
        return color;
    }

    public LocalDate getBirthday() {
        return birthday;
    }

    public String getTimezone() {
        return timezone;
    }

    public String getLocale() {
        return locale;
    }

    public String getCurrency() {
        return currency;
    }

    public UserStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
