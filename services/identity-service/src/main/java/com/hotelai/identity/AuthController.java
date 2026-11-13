package com.hotelai.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
class AuthController {
  private final AuthService auth;

  @GetMapping("/me")
  AuthService.Profile me(@AuthenticationPrincipal Jwt jwt) {
    String value = jwt.getClaimAsString("uid");
    return auth.profile(UUID.fromString(value == null ? jwt.getSubject() : value));
  }

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.ACCEPTED)
  AuthService.RegistrationChallenge register(
      @Valid @RequestBody RegisterRequest request,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      HttpServletRequest servletRequest) {
    return auth.register(
        request.email(),
        request.password(),
        request.fullName(),
        request.phone(),
        idempotencyKey,
        context(servletRequest));
  }

  @PostMapping("/register/verify")
  AuthService.Tokens verifyRegistration(
      @Valid @RequestBody ChallengeCode request, HttpServletRequest servletRequest) {
    return auth.verifyRegistration(request.challengeId(), request.code(), context(servletRequest));
  }

  @PostMapping("/register/resend")
  @ResponseStatus(HttpStatus.ACCEPTED)
  AuthService.RegistrationChallenge resend(
      @Valid @RequestBody EmailRequest request, HttpServletRequest servletRequest) {
    return auth.resendVerification(request.email(), context(servletRequest));
  }

  @PostMapping("/login")
  AuthService.Tokens login(
      @Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
    return auth.login(request.email(), request.password(), context(servletRequest));
  }

  @PostMapping("/refresh")
  AuthService.Tokens refresh(
      @Valid @RequestBody RefreshRequest request, HttpServletRequest servletRequest) {
    return auth.refresh(request.refreshToken(), context(servletRequest));
  }

  @PostMapping("/logout")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void logout(
      @RequestBody(required = false) RefreshRequest request, @AuthenticationPrincipal Jwt jwt) {
    String raw = request == null ? null : request.refreshToken();
    UUID sid = jwt == null ? null : UUID.fromString(jwt.getClaimAsString("sid"));
    auth.logout(raw, sid);
  }

  @PostMapping("/forgot-password")
  @ResponseStatus(HttpStatus.ACCEPTED)
  AuthService.RegistrationChallenge forgot(
      @Valid @RequestBody EmailRequest request, HttpServletRequest servletRequest) {
    return auth.forgotPassword(request.email(), context(servletRequest));
  }

  @PostMapping("/reset-password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void reset(@Valid @RequestBody ResetPasswordRequest request) {
    auth.resetPassword(request.challengeId(), request.code(), request.newPassword());
  }

  @PostMapping("/change-password")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void changePassword(
      @AuthenticationPrincipal Jwt jwt,
      @Valid @RequestBody ChangePasswordRequest request) {
    String value = jwt.getClaimAsString("uid");
    auth.changePassword(
        UUID.fromString(value == null ? jwt.getSubject() : value),
        request.currentPassword(),
        request.newPassword());
  }

  private AuthService.ClientContext context(HttpServletRequest request) {
    String forwarded = request.getHeader("X-Forwarded-For");
    String ip =
        forwarded == null || forwarded.isBlank()
            ? request.getRemoteAddr()
            : forwarded.split(",", 2)[0].trim();
    return new AuthService.ClientContext(ip, request.getHeader("User-Agent"));
  }

  record RegisterRequest(
      @Email @NotBlank @Size(max = 254) String email,
      @NotBlank @Size(min = 10, max = 72) String password,
      @NotBlank @Size(max = 150) String fullName,
      @Size(max = 30) String phone) {}

  record EmailRequest(@Email @NotBlank @Size(max = 254) String email) {}

  record ChallengeCode(
      @NotNull UUID challengeId, @NotBlank @Pattern(regexp = "\\d{6}") String code) {}

  record LoginRequest(@Email @NotBlank @Size(max = 254) String email, @NotBlank String password) {}

  record RefreshRequest(@NotBlank String refreshToken) {}

  record ResetPasswordRequest(
      @NotNull UUID challengeId,
      @NotBlank @Pattern(regexp = "\\d{6}") String code,
      @NotBlank @Size(min = 10, max = 72) String newPassword) {}

  record ChangePasswordRequest(
      @NotBlank String currentPassword,
      @NotBlank @Size(min = 10, max = 72) String newPassword) {}
}
