package com.hotelai.payment.api;

import com.hotelai.payment.domain.PaymentApplicationService;
import com.hotelai.payment.domain.PaymentIntent;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/payments")
public class AdminPaymentController {
    private final PaymentApplicationService payments;

    public AdminPaymentController(PaymentApplicationService payments) {
        this.payments = payments;
    }

    @PostMapping("/cash")
    public ResponseEntity<PaymentDtos.PaymentResponse> recordCash(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody PaymentDtos.RecordCashRequest request) {
        PaymentIntent payment =
                payments.recordCash(PaymentApiSupport.actor(jwt), request.toCommand(), key);
        return ResponseEntity.created(URI.create("/api/v1/admin/payments/" + payment.getId()))
                .body(PaymentDtos.PaymentResponse.from(payment));
    }

    @GetMapping
    public Page<PaymentDtos.PaymentResponse> history(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam UUID reservationId,
            @PageableDefault(size = 30) Pageable pageable) {
        return payments.history(
                        PaymentApiSupport.actor(jwt), true, reservationId, pageable)
                .map(PaymentDtos.PaymentResponse::from);
    }

    @GetMapping("/{paymentId}")
    public PaymentDtos.PaymentResponse detail(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID paymentId) {
        return PaymentDtos.PaymentResponse.from(
                payments.get(PaymentApiSupport.actor(jwt), true, paymentId));
    }
}
