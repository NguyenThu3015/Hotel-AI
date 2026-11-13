package com.hotelai.identity;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class AuthService {
  private static final String GENERIC_CREDENTIAL_ERROR = "Email hoặc mật khẩu không đúng";

  private final AccountRepository accounts;
  private final RoleRepository roles;
  private final AuthSessionRepository sessions;
  private final OtpChallengeRepository challenges;
  private final IdempotencyRepository idempotency;
  private final PasswordEncoder passwords;
  private final JwtService jwt;
  private final RateLimiter rateLimiter;
  private final OutboxService outbox;
  private final SessionRevocationService revocations;
  private final IdentityProperties properties;
  private final EntityManager entityManager;
  private final SecureRandom random = new SecureRandom();

  @Transactional(noRollbackFor = ApiException.class)
  RegistrationChallenge register(
      String email,
      String password,
      String fullName,
      String phone,
      String idempotencyKey,
      ClientContext client) {
    String normalizedEmail = normalizeEmail(email);
    validatePassword(password);
    rateLimiter.check("register", normalizedEmail + '|' + client.ip(), 5, 3600);
    String requestHash =
        Hashing.sha256(
            normalizedEmail + '|' + fullName + '|' + phone + '|' + Hashing.sha256(password));

    if (idempotencyKey != null && !idempotencyKey.isBlank()) {
      IdempotencyRecord existing =
          idempotency.findByOperationAndIdempotencyKey("register", idempotencyKey).orElse(null);
      if (existing != null) {
        if (!Hashing.constantTimeEquals(existing.getRequestHash(), requestHash)) {
          throw new ApiException(
              HttpStatus.CONFLICT,
              "idempotency_key_reused",
              "Idempotency-Key đã được dùng cho một yêu cầu khác");
        }
        OtpChallenge prior =
            challenges
                .findById(existing.getResourceId())
                .orElseThrow(
                    () ->
                        new ApiException(
                            HttpStatus.CONFLICT,
                            "idempotency_result_expired",
                            "Kết quả idempotency không còn khả dụng"));
        return challengeResponse(prior, normalizedEmail);
      }
    }

    if (accounts.existsByEmailIgnoreCase(normalizedEmail)) {
      throw new ApiException(HttpStatus.CONFLICT, "email_already_used", "Email đã được sử dụng");
    }
    Account account =
        accounts.save(
            Account.pendingCustomer(
                normalizedEmail, passwords.encode(password), fullName.trim(), cleanPhone(phone)));
    OtpChallenge challenge = issueChallenge(account, OtpPurpose.VERIFY_EMAIL);
    if (idempotencyKey != null && !idempotencyKey.isBlank()) {
      idempotency.save(
          new IdempotencyRecord("register", idempotencyKey, requestHash, challenge.getId()));
    }
    outbox.append(
        "Account",
        account.getId(),
        "identity.account.registered.v1",
        Map.of(
            "identityId", account.getId(),
            "email", account.getEmail(),
            "status", account.getStatus().name(),
            "occurredAt", Instant.now()));
    return challengeResponse(challenge, normalizedEmail);
  }

  @Transactional(noRollbackFor = ApiException.class)
  Tokens verifyRegistration(UUID challengeId, String code, ClientContext client) {
    OtpChallenge challenge = validChallenge(challengeId, code, OtpPurpose.VERIFY_EMAIL);
    Account account = challenge.getAccount();
    if (account.getStatus() != AccountStatus.PENDING_VERIFICATION) {
      throw new ApiException(
          HttpStatus.CONFLICT, "account_already_verified", "Tài khoản đã được xác minh");
    }
    Role customer =
        roles
            .findByCode("CUSTOMER")
            .orElseThrow(() -> new IllegalStateException("CUSTOMER role was not seeded"));
    challenge.consume();
    account.activateCustomer(customer);
    outbox.append(
        "Account",
        account.getId(),
        "identity.account.activated.v1",
        Map.of(
            "identityId", account.getId(),
            "email", account.getEmail(),
            "fullName", account.getFullName(),
            "phone", account.getPhone() == null ? "" : account.getPhone(),
            "status", account.getStatus().name(),
            "emailVerified", true,
            "roles", java.util.List.of("CUSTOMER"),
            "accountType", account.getType().name(),
            "hotelId", properties.hotelId(),
            "occurredAt", Instant.now()));
    return createSession(account, client);
  }

  @Transactional(noRollbackFor = ApiException.class)
  Tokens login(String email, String password, ClientContext client) {
    String normalizedEmail = normalizeEmail(email);
    rateLimiter.check("login", normalizedEmail + '|' + client.ip(), 12, 15 * 60);
    // Login updates counters/timestamps. Lock the account row so rapid duplicate submits,
    // password-manager retries or concurrent clients cannot overwrite the same @Version.
    Account account = accounts.findForAuthentication(normalizedEmail).orElse(null);
    if (account == null || !passwords.matches(password, account.getPasswordHash())) {
      if (account != null) {
        Instant now = Instant.now();
        accounts.markLoginFailed(account.getId(), now, now.plusSeconds(15 * 60));
      }
      throw new ApiException(
          HttpStatus.UNAUTHORIZED, "invalid_credentials", GENERIC_CREDENTIAL_ERROR);
    }
    if (account.getStatus() == AccountStatus.PENDING_VERIFICATION) {
      throw new ApiException(
          HttpStatus.FORBIDDEN, "email_not_verified", "Tài khoản chưa xác minh email");
    }
    if (account.getStatus() != AccountStatus.ACTIVE || account.temporarilyLocked()) {
      throw new ApiException(HttpStatus.LOCKED, "account_locked", "Tài khoản đang bị khóa");
    }
    accounts.markLoginSucceeded(account.getId(), Instant.now());
    Tokens tokens = createSession(account, client);
    outbox.append(
        "Account",
        account.getId(),
        "audit.identity.login.v1",
        Map.of(
            "accountId", account.getId(),
            "hotelId", properties.hotelId(),
            "occurredAt", Instant.now()));
    return tokens;
  }

  @Transactional(noRollbackFor = ApiException.class)
  Tokens refresh(String rawRefreshToken, ClientContext client) {
    AuthSession session =
        sessions
            .findByRefreshTokenHash(Hashing.sha256(rawRefreshToken))
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.UNAUTHORIZED,
                        "invalid_refresh_token",
                        "Refresh token không hợp lệ hoặc đã được thay thế"));
    if (!session.usable()) {
      throw new ApiException(
          HttpStatus.UNAUTHORIZED,
          "invalid_refresh_token",
          "Refresh token đã hết hạn hoặc bị thu hồi");
    }
    String replacement = randomToken();
    session.rotate(
        Hashing.sha256(replacement), Instant.now().plus(properties.security().refreshTtl()));
    sessions.save(session);
    return tokenResponse(session.getAccount(), session, replacement);
  }

  @Transactional
  void logout(String rawRefreshToken, UUID authenticatedSessionId) {
    AuthSession session = null;
    if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
      session = sessions.findByRefreshTokenHash(Hashing.sha256(rawRefreshToken)).orElse(null);
    }
    if (session == null && authenticatedSessionId != null) {
      session = sessions.findById(authenticatedSessionId).orElse(null);
    }
    if (session != null) {
      revocations.revokeSession(session.getId(), "LOGOUT");
      session.revoke("LOGOUT");
      sessions.save(session);
      outbox.append(
          "Account",
          session.getAccount().getId(),
          "audit.identity.logout.v1",
          Map.of(
              "accountId", session.getAccount().getId(),
              "sessionId", session.getId(),
              "occurredAt", Instant.now()));
    }
  }

  @Transactional(noRollbackFor = ApiException.class)
  RegistrationChallenge resendVerification(String email, ClientContext client) {
    String normalizedEmail = normalizeEmail(email);
    rateLimiter.check("verify-resend", normalizedEmail + '|' + client.ip(), 3, 15 * 60);
    Account account = accounts.findByEmailIgnoreCase(normalizedEmail).orElse(null);
    Instant expires = Instant.now().plus(properties.security().otpTtl());
    if (account == null || account.getStatus() != AccountStatus.PENDING_VERIFICATION) {
      return new RegistrationChallenge(UUID.randomUUID(), mask(normalizedEmail), expires);
    }
    return challengeResponse(issueChallenge(account, OtpPurpose.VERIFY_EMAIL), normalizedEmail);
  }

  @Transactional(noRollbackFor = ApiException.class)
  RegistrationChallenge forgotPassword(String email, ClientContext client) {
    String normalizedEmail = normalizeEmail(email);
    rateLimiter.check("forgot-password", normalizedEmail + '|' + client.ip(), 3, 15 * 60);
    Account account = accounts.findByEmailIgnoreCase(normalizedEmail).orElse(null);
    Instant expires = Instant.now().plus(properties.security().resetTtl());
    if (account == null || account.getStatus() != AccountStatus.ACTIVE) {
      return new RegistrationChallenge(UUID.randomUUID(), mask(normalizedEmail), expires);
    }
    return challengeResponse(issueChallenge(account, OtpPurpose.RESET_PASSWORD), normalizedEmail);
  }

  @Transactional(noRollbackFor = ApiException.class)
  void resetPassword(UUID challengeId, String code, String newPassword) {
    validatePassword(newPassword);
    OtpChallenge challenge = validChallenge(challengeId, code, OtpPurpose.RESET_PASSWORD);
    Account account = challenge.getAccount();
    // The account may have changed while the OTP was being delivered (profile update,
    // session refresh or another login). Refresh its @Version and hold the row until
    // password change plus session revocation commit atomically.
    entityManager.refresh(account, LockModeType.PESSIMISTIC_WRITE);
    challenge.consume();
    account.changePassword(passwords.encode(newPassword));
    revokeAllSessions(account.getId(), "PASSWORD_RESET");
    outbox.append(
        "Account",
        account.getId(),
        "identity.password.changed.v1",
        Map.of("accountId", account.getId(), "occurredAt", Instant.now()));
  }

  @Transactional(noRollbackFor = ApiException.class)
  void changePassword(UUID accountId, String currentPassword, String newPassword) {
    validatePassword(newPassword);
    Account account = accounts
        .findForUpdateById(accountId)
        .orElseThrow(() -> new ApiException(
            HttpStatus.NOT_FOUND, "account_not_found", "Tài khoản không tồn tại"));
    if (!passwords.matches(currentPassword, account.getPasswordHash())) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "current_password_invalid",
          "Mật khẩu hiện tại không đúng");
    }
    if (passwords.matches(newPassword, account.getPasswordHash())) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "password_unchanged",
          "Mật khẩu mới phải khác mật khẩu hiện tại");
    }
    account.changePassword(passwords.encode(newPassword));
    revokeAllSessions(account.getId(), "PASSWORD_CHANGED");
    outbox.append(
        "Account",
        account.getId(),
        "identity.password.changed.v1",
        Map.of("accountId", account.getId(), "occurredAt", Instant.now()));
  }

  OtpChallenge issueChallenge(Account account, OtpPurpose purpose) {
    String code = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
    Instant expiresAt =
        Instant.now()
            .plus(
                purpose == OtpPurpose.VERIFY_EMAIL
                    ? properties.security().otpTtl()
                    : properties.security().resetTtl());
    OtpChallenge challenge =
        challenges.save(new OtpChallenge(account, purpose, Hashing.sha256(code), expiresAt));
    outbox.append(
        "OtpChallenge",
        challenge.getId(),
        "notification.otp.requested.v1",
        Map.of(
            "challengeId", challenge.getId(),
            "recipient", account.getEmail(),
            "purpose", purpose == OtpPurpose.VERIFY_EMAIL ? "ACCOUNT_ACTIVATION" : purpose.name(),
            "templateCode",
                purpose == OtpPurpose.VERIFY_EMAIL
                    ? "OTP_ACCOUNT_ACTIVATION"
                    : "OTP_PASSWORD_RESET",
            "variables", Map.of("otp", code, "expiresAt", expiresAt.toString()),
            "expiresAt", expiresAt,
            "identityId", account.getId()));
    return challenge;
  }

  private OtpChallenge validChallenge(UUID id, String code, OtpPurpose purpose) {
    OtpChallenge challenge = challenges.findById(id).orElse(null);
    if (challenge == null
        || challenge.getPurpose() != purpose
        || !challenge.usable()
        || !Hashing.constantTimeEquals(challenge.getSecretHash(), Hashing.sha256(code))) {
      if (challenge != null && challenge.getPurpose() == purpose && challenge.usable()) {
        challenge.failedAttempt();
        challenges.save(challenge);
      }
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "invalid_or_expired_challenge",
          "Mã xác minh không đúng hoặc đã hết hạn");
    }
    return challenge;
  }

  private Tokens createSession(Account account, ClientContext client) {
    String refreshToken = randomToken();
    AuthSession session =
        sessions.save(
            new AuthSession(
                account,
                Hashing.sha256(refreshToken),
                Instant.now().plus(properties.security().refreshTtl()),
                truncate(client.userAgent(), 500),
                Hashing.sha256(client.ip())));
    return tokenResponse(account, session, refreshToken);
  }

  private Tokens tokenResponse(Account account, AuthSession session, String refreshToken) {
    return new Tokens(
        jwt.issue(account, session),
        refreshToken,
        "Bearer",
        properties.security().accessTtl().toSeconds());
  }

  void revokeAllSessions(UUID accountId, String reason) {
    sessions
        .findActiveByAccountId(accountId)
        .forEach(
            session -> {
              revocations.revokeSession(session.getId(), reason);
              session.revoke(reason);
              sessions.save(session);
            });
  }

  @Transactional(readOnly = true)
  Profile profile(UUID accountId) {
    Account account =
        accounts
            .findById(accountId)
            .orElseThrow(
                () ->
                    new ApiException(
                        HttpStatus.NOT_FOUND, "account_not_found", "Tài khoản không tồn tại"));
    return new Profile(
        account.getId(),
        account.getId(),
        account.getEmail(),
        account.getFullName(),
        account.getPhone(),
        account.getStatus().name(),
        account.isEmailVerified(),
        account.getRoles().stream().map(Role::getCode).sorted().toList(),
        account.permissionCodes().stream().sorted().toList());
  }

  private String randomToken() {
    byte[] bytes = new byte[48];
    random.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private String normalizeEmail(String email) {
    return email.trim().toLowerCase(Locale.ROOT);
  }

  private String cleanPhone(String phone) {
    return phone == null || phone.isBlank() ? null : phone.trim();
  }

  private void validatePassword(String value) {
    boolean valid =
        value != null
            && value.length() >= 10
            && value.length() <= 72
            && value.chars().anyMatch(Character::isUpperCase)
            && value.chars().anyMatch(Character::isLowerCase)
            && value.chars().anyMatch(Character::isDigit)
            && value.chars().anyMatch(character -> !Character.isLetterOrDigit(character));
    if (!valid) {
      throw new ApiException(
          HttpStatus.BAD_REQUEST,
          "weak_password",
          "Mật khẩu phải dài 10-72 ký tự và có chữ hoa, chữ thường, số, ký tự đặc biệt");
    }
  }

  private RegistrationChallenge challengeResponse(OtpChallenge challenge, String email) {
    return new RegistrationChallenge(challenge.getId(), mask(email), challenge.getExpiresAt());
  }

  private String mask(String email) {
    int at = email.indexOf('@');
    if (at < 1) return "***";
    return email.substring(0, 1) + "***" + email.substring(at);
  }

  private String truncate(String text, int max) {
    if (text == null) return null;
    return text.substring(0, Math.min(text.length(), max));
  }

  record ClientContext(String ip, String userAgent) {}

  record RegistrationChallenge(UUID challengeId, String maskedDestination, Instant expiresAt) {}

  record Tokens(String accessToken, String refreshToken, String tokenType, long expiresInSeconds) {}

  record Profile(
      UUID id,
      UUID identityId,
      String email,
      String fullName,
      String phone,
      String status,
      boolean emailVerified,
      java.util.List<String> roles,
      java.util.List<String> permissions) {}
}
