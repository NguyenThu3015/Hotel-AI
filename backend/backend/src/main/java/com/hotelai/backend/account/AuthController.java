package com.hotelai.backend.account;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
  private final AuthService service;

  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  AuthService.Tokens register(@Valid @RequestBody Register r) {
    return service.register(r.email, r.password, r.fullName, r.phone);
  }

  @PostMapping("/login")
  AuthService.Tokens login(@Valid @RequestBody Login r) {
    return service.login(r.email, r.password);
  }

  @PostMapping("/refresh")
  AuthService.Tokens refresh(@Valid @RequestBody Token r) {
    return service.refresh(r.refreshToken);
  }

  @PostMapping("/logout")
  void logout(@Valid @RequestBody Token r) {
    service.logout(r.refreshToken);
  }

  @PostMapping("/otp")
  @ResponseStatus(HttpStatus.ACCEPTED)
  Map<String, String> otp(@Valid @RequestBody OtpRequest r) {
    service.issueOtp(r.email, r.purpose);
    return Map.of("message", "Nếu tài khoản hợp lệ, OTP sẽ được gửi và có hiệu lực 10 phút");
  }

  @PostMapping("/verify-email")
  void verify(@Valid @RequestBody Verify r) {
    service.verifyEmail(r.email, r.otp);
  }

  @PostMapping("/reset-password")
  void reset(@Valid @RequestBody Reset r) {
    service.resetPassword(r.email, r.otp, r.newPassword);
  }

  record Register(
      @Email @NotBlank String email,
      @Size(min = 8, max = 72) String password,
      @NotBlank @Size(max = 150) String fullName,
      @Size(max = 20) String phone) {}

  record Login(@Email @NotBlank String email, @NotBlank String password) {}

  record Token(@NotBlank String refreshToken) {}

  record OtpRequest(
      @Email @NotBlank String email,
      @Pattern(regexp = "verify-email|reset-password") String purpose) {}

  record Verify(@Email @NotBlank String email, @Pattern(regexp = "\\d{6}") String otp) {}

  record Reset(
      @Email @NotBlank String email,
      @Pattern(regexp = "\\d{6}") String otp,
      @Size(min = 8, max = 72) String newPassword) {}
}
