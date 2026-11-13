package com.hotelai.identity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

enum AccountType {
  CUSTOMER,
  STAFF
}

enum AccountStatus {
  PENDING_VERIFICATION,
  ACTIVE,
  LOCKED,
  DISABLED
}

enum OtpPurpose {
  VERIFY_EMAIL,
  RESET_PASSWORD
}

@Entity
@Table(name = "permissions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class Permission {
  @Id private UUID id;

  @Column(nullable = false, unique = true, length = 100)
  private String code;

  @Column(nullable = false)
  private String description;
}

@Entity
@Table(name = "roles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class Role {
  @Id private UUID id;

  @Column(nullable = false, unique = true, length = 50)
  private String code;

  @Column(nullable = false, length = 100)
  private String name;

  private String description;

  @Column(name = "system_role", nullable = false)
  private boolean systemRole;

  @ManyToMany(fetch = FetchType.EAGER)
  @JoinTable(
      name = "role_permissions",
      joinColumns = @JoinColumn(name = "role_id"),
      inverseJoinColumns = @JoinColumn(name = "permission_id"))
  private Set<Permission> permissions = new HashSet<>();

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version private long version;

  Role(String code, String name, String description, Set<Permission> permissions) {
    this.id = UUID.randomUUID();
    this.code = code;
    this.name = name;
    this.description = description;
    this.permissions = new HashSet<>(permissions);
    this.systemRole = false;
    this.createdAt = this.updatedAt = Instant.now();
  }

  void update(String name, String description, Set<Permission> permissions) {
    this.name = name;
    this.description = description;
    this.permissions.clear();
    this.permissions.addAll(permissions);
    this.updatedAt = Instant.now();
  }
}

@Entity
@Table(name = "accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class Account {
  @Id private UUID id;

  @Column(nullable = false, unique = true, length = 254)
  private String email;

  @Column(name = "password_hash", nullable = false)
  private String passwordHash;

  @Column(name = "full_name", nullable = false, length = 150)
  private String fullName;

  @Column(length = 30)
  private String phone;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private AccountType type;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private AccountStatus status;

  @Column(name = "email_verified", nullable = false)
  private boolean emailVerified;

  @Column(name = "hotel_id", length = 50)
  private String hotelId;

  @Column(name = "failed_login_attempts", nullable = false)
  private int failedLoginAttempts;

  @Column(name = "locked_until")
  private Instant lockedUntil;

  @Column(name = "password_changed_at", nullable = false)
  private Instant passwordChangedAt;

  @Column(name = "last_login_at")
  private Instant lastLoginAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @ManyToMany(fetch = FetchType.EAGER)
  @JoinTable(
      name = "account_roles",
      joinColumns = @JoinColumn(name = "account_id"),
      inverseJoinColumns = @JoinColumn(name = "role_id"))
  private Set<Role> roles = new HashSet<>();

  @Version private long version;

  static Account pendingCustomer(String email, String passwordHash, String name, String phone) {
    return create(
        email,
        passwordHash,
        name,
        phone,
        AccountType.CUSTOMER,
        null,
        AccountStatus.PENDING_VERIFICATION);
  }

  static Account staff(
      String email,
      String passwordHash,
      String name,
      String phone,
      String hotelId,
      Set<Role> roles) {
    Account account =
        create(email, passwordHash, name, phone, AccountType.STAFF, hotelId, AccountStatus.ACTIVE);
    account.emailVerified = true;
    account.roles.addAll(roles);
    return account;
  }

  private static Account create(
      String email,
      String passwordHash,
      String name,
      String phone,
      AccountType type,
      String hotelId,
      AccountStatus status) {
    Account account = new Account();
    account.id = UUID.randomUUID();
    account.email = email;
    account.passwordHash = passwordHash;
    account.fullName = name;
    account.phone = phone;
    account.type = type;
    account.hotelId = hotelId;
    account.status = status;
    account.passwordChangedAt = Instant.now();
    account.createdAt = account.updatedAt = Instant.now();
    return account;
  }

  void activateCustomer(Role customerRole) {
    this.status = AccountStatus.ACTIVE;
    this.emailVerified = true;
    this.roles.add(customerRole);
    touch();
  }

  void loginSucceeded() {
    this.failedLoginAttempts = 0;
    this.lockedUntil = null;
    this.lastLoginAt = Instant.now();
    touch();
  }

  void loginFailed() {
    this.failedLoginAttempts++;
    if (failedLoginAttempts >= 5) this.lockedUntil = Instant.now().plusSeconds(15 * 60);
    touch();
  }

  boolean temporarilyLocked() {
    return lockedUntil != null && lockedUntil.isAfter(Instant.now());
  }

  void changePassword(String encoded) {
    this.passwordHash = encoded;
    this.passwordChangedAt = Instant.now();
    this.failedLoginAttempts = 0;
    this.lockedUntil = null;
    touch();
  }

  void updateProfile(String fullName, String phone) {
    this.fullName = fullName;
    this.phone = phone;
    touch();
  }

  void changeStatus(AccountStatus status) {
    this.status = status;
    touch();
  }

  void replaceRoles(Set<Role> roles) {
    this.roles.clear();
    this.roles.addAll(roles);
    touch();
  }

  boolean hasRole(String code) {
    return roles.stream().anyMatch(role -> role.getCode().equals(code));
  }

  Set<String> permissionCodes() {
    Set<String> result = new HashSet<>();
    roles.forEach(
        role -> role.getPermissions().forEach(permission -> result.add(permission.getCode())));
    return result;
  }

  private void touch() {
    this.updatedAt = Instant.now();
  }
}

@Entity
@Table(name = "auth_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class AuthSession {
  @Id private UUID id;

  @ManyToOne(fetch = FetchType.EAGER, optional = false)
  @JoinColumn(name = "account_id", nullable = false)
  private Account account;

  @Column(name = "refresh_token_hash", nullable = false, unique = true, length = 64)
  private String refreshTokenHash;

  @Column(name = "token_version", nullable = false)
  private long tokenVersion;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @Column(name = "revoke_reason", length = 100)
  private String revokeReason;

  @Column(name = "user_agent", length = 500)
  private String userAgent;

  @Column(name = "ip_hash", length = 64)
  private String ipHash;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "last_used_at", nullable = false)
  private Instant lastUsedAt;

  @Version private long version;

  AuthSession(
      Account account, String refreshHash, Instant expiresAt, String userAgent, String ipHash) {
    this.id = UUID.randomUUID();
    this.account = account;
    this.refreshTokenHash = refreshHash;
    this.expiresAt = expiresAt;
    this.userAgent = userAgent;
    this.ipHash = ipHash;
    this.createdAt = this.lastUsedAt = Instant.now();
  }

  boolean usable() {
    return revokedAt == null
        && expiresAt.isAfter(Instant.now())
        && account.getStatus() == AccountStatus.ACTIVE;
  }

  void rotate(String refreshHash, Instant newExpiry) {
    this.refreshTokenHash = refreshHash;
    this.expiresAt = newExpiry;
    this.tokenVersion++;
    this.lastUsedAt = Instant.now();
  }

  void revoke(String reason) {
    if (revokedAt == null) {
      this.revokedAt = Instant.now();
      this.revokeReason = reason;
      this.tokenVersion++;
    }
  }
}

@Entity
@Table(name = "otp_challenges")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class OtpChallenge {
  @Id private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "account_id", nullable = false)
  private Account account;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 30)
  private OtpPurpose purpose;

  @Column(name = "secret_hash", nullable = false, length = 64)
  private String secretHash;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(nullable = false)
  private int attempts;

  @Column(name = "used_at")
  private Instant usedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  OtpChallenge(Account account, OtpPurpose purpose, String secretHash, Instant expiresAt) {
    this.id = UUID.randomUUID();
    this.account = account;
    this.purpose = purpose;
    this.secretHash = secretHash;
    this.expiresAt = expiresAt;
    this.createdAt = Instant.now();
  }

  boolean usable() {
    return usedAt == null && expiresAt.isAfter(Instant.now()) && attempts < 5;
  }

  void failedAttempt() {
    attempts++;
  }

  void consume() {
    usedAt = Instant.now();
  }
}

@Entity
@Table(name = "rate_limits")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class RateLimit {
  @Id private UUID id;

  @Column(name = "subject_hash", nullable = false, length = 64)
  private String subjectHash;

  @Column(nullable = false, length = 50)
  private String action;

  @Column(name = "window_started_at", nullable = false)
  private Instant windowStartedAt;

  @Column(nullable = false)
  private int attempts;

  @Version private long version;

  RateLimit(String subjectHash, String action) {
    this.id = UUID.randomUUID();
    this.subjectHash = subjectHash;
    this.action = action;
    this.windowStartedAt = Instant.now();
    this.attempts = 1;
  }

  boolean incrementOrReset(Instant now, long windowSeconds, int maxAttempts) {
    if (windowStartedAt.plusSeconds(windowSeconds).isBefore(now)) {
      windowStartedAt = now;
      attempts = 1;
      return true;
    }
    if (attempts >= maxAttempts) return false;
    attempts++;
    return true;
  }
}

@Entity
@Table(name = "idempotency_records")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class IdempotencyRecord {
  @Id private UUID id;

  @Column(nullable = false, length = 80)
  private String operation;

  @Column(name = "idempotency_key", nullable = false, length = 100)
  private String idempotencyKey;

  @Column(name = "request_hash", nullable = false, length = 64)
  private String requestHash;

  @Column(name = "resource_id", nullable = false)
  private UUID resourceId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  IdempotencyRecord(String operation, String key, String requestHash, UUID resourceId) {
    this.id = UUID.randomUUID();
    this.operation = operation;
    this.idempotencyKey = key;
    this.requestHash = requestHash;
    this.resourceId = resourceId;
    this.createdAt = Instant.now();
    this.expiresAt = Instant.now().plusSeconds(24 * 60 * 60);
  }
}

@Entity
@Table(name = "outbox_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class OutboxEvent {
  @Id private UUID id;

  @Column(name = "aggregate_type", nullable = false, length = 80)
  private String aggregateType;

  @Column(name = "aggregate_id", nullable = false)
  private UUID aggregateId;

  @Column(name = "event_type", nullable = false, length = 150)
  private String eventType;

  @Column(name = "correlation_id", nullable = false)
  private UUID correlationId;

  @Column(name = "schema_version", nullable = false)
  private int schemaVersion;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private String payload;

  @Column(name = "occurred_at", nullable = false, updatable = false)
  private Instant occurredAt;

  OutboxEvent(
      String aggregateType,
      UUID aggregateId,
      String eventType,
      UUID correlationId,
      String payload) {
    this.id = UUID.randomUUID();
    this.aggregateType = aggregateType;
    this.aggregateId = aggregateId;
    this.eventType = eventType;
    this.correlationId = correlationId;
    this.schemaVersion = 1;
    this.payload = payload;
    this.occurredAt = Instant.now();
  }
}

@Entity
@Table(name = "outbox_deliveries")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class OutboxDelivery {
  @Id
  @Column(name = "event_id")
  private UUID eventId;

  @Column(nullable = false)
  private int attempts;

  @Column(name = "last_attempt_at")
  private Instant lastAttemptAt;

  @Column(name = "published_at")
  private Instant publishedAt;

  @Column(name = "last_error", length = 500)
  private String lastError;

  @Version private long version;

  OutboxDelivery(UUID eventId) {
    this.eventId = eventId;
  }

  void succeeded() {
    attempts++;
    lastAttemptAt = Instant.now();
    publishedAt = lastAttemptAt;
    lastError = null;
  }

  void failed(String error) {
    attempts++;
    lastAttemptAt = Instant.now();
    lastError = error == null ? "unknown" : error.substring(0, Math.min(error.length(), 500));
  }
}
