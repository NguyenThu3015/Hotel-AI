package com.hotelai.payment.api;

import com.hotelai.payment.domain.PaymentApplicationService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
@RequestMapping("/api/v1/admin/refunds")
public class AdminRefundController {
    private final PaymentApplicationService payments;

    public AdminRefundController(PaymentApplicationService payments) {
        this.payments = payments;
    }

    @GetMapping
    public Page<PaymentDtos.RefundResponse> history(
            @RequestParam(required = false) UUID paymentId,
            @PageableDefault(size = 30) Pageable pageable) {
        var refunds = paymentId == null
                ? payments.refundHistory(pageable)
                : payments.refundHistory(paymentId, pageable);
        return refunds.map(PaymentDtos.RefundResponse::from);
    }

    @GetMapping("/{refundId}")
    public PaymentDtos.RefundResponse detail(@PathVariable UUID refundId) {
        return PaymentDtos.RefundResponse.from(payments.getRefund(refundId));
    }

    @PostMapping("/{refundId}/approve")
    public PaymentDtos.RefundResponse approve(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID refundId,
            @RequestHeader("Idempotency-Key") String key) {
        return PaymentDtos.RefundResponse.from(
                payments.approveRefund(PaymentApiSupport.actor(jwt), refundId, key));
    }

    @PostMapping("/{refundId}/reject")
    public PaymentDtos.RefundResponse reject(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID refundId,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody PaymentDtos.RefundRejectionRequest request) {
        return PaymentDtos.RefundResponse.from(payments.rejectRefund(
                PaymentApiSupport.actor(jwt), refundId, request.reason(), key));
    }

    @PostMapping("/{refundId}/complete")
    public PaymentDtos.RefundResponse complete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID refundId,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody PaymentDtos.RefundCompletionRequest request) {
        return PaymentDtos.RefundResponse.from(payments.completeRefundByStaff(
                PaymentApiSupport.actor(jwt),
                refundId,
                request.providerReference(),
                request.note(),
                key));
    }
}
