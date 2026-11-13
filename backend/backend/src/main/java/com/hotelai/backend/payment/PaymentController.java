package com.hotelai.backend.payment;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {
  private final PaymentService service;

  @PostMapping
  PaymentService.View create(
      Principal principal,
      @RequestHeader("Idempotency-Key") @NotBlank String key,
      @Valid @RequestBody Create request) {
    return service.create(
        request.bookingId, key, request.provider, request.type, request.amount, principal);
  }

  @PostMapping("/webhook/sandbox")
  PaymentService.View webhook(
      @RequestBody String body,
      @RequestHeader(value = "X-Signature", required = false) String signature)
      throws Exception {
    return service.webhook(body, signature);
  }

  @PostMapping("/sandbox/{id}/complete")
  PaymentService.View completeSandbox(@PathVariable Long id) throws Exception {
    return service.completeSandbox(id);
  }

  @PostMapping("/cash/{id}/complete")
  @PreAuthorize("hasAnyRole('ADMIN','MANAGER','RECEPTIONIST')")
  PaymentService.View completeCash(@PathVariable Long id, Principal principal) throws Exception {
    return service.completeCash(id, principal);
  }

  @PostMapping("/{id}/refund")
  @PreAuthorize("hasAnyRole('ADMIN','MANAGER','RECEPTIONIST')")
  PaymentService.View refund(@PathVariable Long id) {
    return service.refund(id);
  }

  @PostMapping("/refunds/{id}/approve")
  @PreAuthorize("hasAnyRole('ADMIN','MANAGER','RECEPTIONIST')")
  com.hotelai.backend.booking.BookingService.RefundView approveRefund(@PathVariable Long id) {
    return service.approveRefund(id);
  }

  @GetMapping("/invoices/{bookingId}")
  PaymentService.InvoiceView invoice(@PathVariable Long bookingId, Principal principal) {
    return service.invoice(bookingId, principal);
  }

  record Create(
      @NotNull Long bookingId,
      @Pattern(regexp = "VNPAY|MOMO|STRIPE|CASH|SANDBOX") String provider,
      @Pattern(regexp = "DEPOSIT|FULL|BALANCE") String type,
      @Positive BigDecimal amount) {}
}
