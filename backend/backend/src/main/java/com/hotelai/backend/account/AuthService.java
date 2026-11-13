package com.hotelai.backend.account;

import com.hotelai.backend.audit.AuditService;
import com.hotelai.backend.common.ApiException;
import com.hotelai.backend.security.JwtService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {
  private final UserRepository users;
  private final RoleRepository roles;
  private final RefreshTokenRepository refreshTokens;
  private final PasswordEncoder passwords;
  private final JwtService jwt;
  private final StringRedisTemplate redis;
  private final JavaMailSender mail;
  private final AuditService audit;
  private final SecureRandom random = new SecureRandom();

  @Value("${hotel.security.refresh-token-days}")
  private long refreshDays;

  @Transactional
  public Tokens register(String email, String password, String name, String phone) {
    if (users.existsByEmailIgnoreCase(email))
      throw new ApiException(HttpStatus.CONFLICT, "Email đã được sử dụng");
    Role customer = roles.findByName("CUSTOMER").orElseThrow();
    User user = users.save(new User(email, passwords.encode(password), name, phone, customer));
    audit.record("REGISTER", "USER", user.getId(), email);
    return tokens(user);
  }

  @Transactional
  public Tokens login(String email, String password) {
    User user =
        users
            .findByEmailIgnoreCase(email)
            .orElseThrow(
                () -> new ApiException(HttpStatus.UNAUTHORIZED, "Email hoặc mật khẩu không đúng"));
    if (!"ACTIVE".equals(user.getStatus()))
      throw new ApiException(HttpStatus.LOCKED, "Tài khoản đang bị khóa");
    if (!passwords.matches(password, user.getPasswordHash()))
      throw new ApiException(HttpStatus.UNAUTHORIZED, "Email hoặc mật khẩu không đúng");
    user.login();
    audit.record("LOGIN", "USER", user.getId(), null);
    return tokens(user);
  }

  @Transactional
  public Tokens refresh(String raw) {
    RefreshToken old =
        refreshTokens
            .findByTokenHash(hash(raw))
            .orElseThrow(
                () -> new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token không hợp lệ"));
    if (!old.usable())
      throw new ApiException(HttpStatus.UNAUTHORIZED, "Refresh token đã hết hạn hoặc bị thu hồi");
    old.revoke();
    return tokens(old.getUser());
  }

  @Transactional
  public void logout(String raw) {
    refreshTokens.findByTokenHash(hash(raw)).ifPresent(RefreshToken::revoke);
  }

  public void issueOtp(String email, String purpose) {
    users
        .findByEmailIgnoreCase(email)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản"));
    String otp = String.format("%06d", random.nextInt(1_000_000));
    redis
        .opsForValue()
        .set("otp:" + purpose + ":" + email.toLowerCase(), hash(otp), Duration.ofMinutes(10));
    SimpleMailMessage message = new SimpleMailMessage();
    message.setTo(email);
    message.setSubject("Hotel AI - Mã OTP");
    message.setText("Mã OTP của bạn là " + otp + ". Mã có hiệu lực 10 phút.");
    mail.send(message);
  }

  @Transactional
  public void verifyEmail(String email, String otp) {
    checkOtp(email, "verify-email", otp);
    User u = users.findByEmailIgnoreCase(email).orElseThrow();
    u.verifyEmail();
    audit.record("VERIFY_EMAIL", "USER", u.getId(), null);
  }

  @Transactional
  public void resetPassword(String email, String otp, String newPassword) {
    checkOtp(email, "reset-password", otp);
    User u = users.findByEmailIgnoreCase(email).orElseThrow();
    u.changePassword(passwords.encode(newPassword));
    audit.record("RESET_PASSWORD", "USER", u.getId(), null);
  }

  private void checkOtp(String email, String purpose, String otp) {
    String key = "otp:" + purpose + ":" + email.toLowerCase();
    String expected = redis.opsForValue().get(key);
    if (expected == null
        || !MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8), hash(otp).getBytes(StandardCharsets.UTF_8)))
      throw new ApiException(HttpStatus.BAD_REQUEST, "OTP không đúng hoặc đã hết hạn");
    redis.delete(key);
  }

  private Tokens tokens(User user) {
    byte[] bytes = new byte[48];
    random.nextBytes(bytes);
    String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    refreshTokens.save(
        new RefreshToken(user, hash(raw), Instant.now().plus(refreshDays, ChronoUnit.DAYS)));
    return new Tokens(jwt.issue(user), raw, "Bearer", 900);
  }

  private String hash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public record Tokens(
      String accessToken, String refreshToken, String tokenType, long expiresInSeconds) {}
}
