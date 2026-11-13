package com.hotelai.payment.api;

import com.hotelai.payment.domain.PaymentApplicationService;
import com.hotelai.payment.domain.PaymentApplicationService.PaymentReceipt;
import com.hotelai.payment.domain.PaymentCommands;
import com.hotelai.payment.domain.PaymentIntent;
import jakarta.validation.Valid;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
@RequestMapping("/api/v1/payments")
public class CustomerPaymentController {
    private final PaymentApplicationService payments;

    public CustomerPaymentController(PaymentApplicationService payments) {
        this.payments = payments;
    }

    @PostMapping("/intents")
    public ResponseEntity<PaymentDtos.PaymentResponse> createIntent(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody PaymentDtos.CreateIntentRequest request) {
        PaymentIntent created =
                payments.createIntent(PaymentApiSupport.actor(jwt), PaymentApiSupport.staff(jwt), request.toCommand(), key);
        return ResponseEntity.created(URI.create("/api/v1/payments/" + created.getId()))
                .body(PaymentDtos.PaymentResponse.from(created));
    }

    @GetMapping
    public Page<PaymentDtos.PaymentResponse> history(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) UUID reservationId,
            @PageableDefault(size = 30) Pageable pageable) {
        return payments.history(
                        PaymentApiSupport.actor(jwt),
                        PaymentApiSupport.staff(jwt),
                        reservationId,
                        pageable)
                .map(PaymentDtos.PaymentResponse::from);
    }

    @GetMapping("/{paymentId}")
    public PaymentDtos.PaymentResponse detail(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID paymentId) {
        return PaymentDtos.PaymentResponse.from(payments.get(
                PaymentApiSupport.actor(jwt), PaymentApiSupport.staff(jwt), paymentId));
    }

    @GetMapping("/{paymentId}/bank-transfer")
    public PaymentDtos.BankTransferInstructionsResponse bankTransferInstructions(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID paymentId) {
        return PaymentDtos.BankTransferInstructionsResponse.from(
                payments.bankTransferInstructions(
                        PaymentApiSupport.actor(jwt), PaymentApiSupport.staff(jwt), paymentId));
    }

    @PostMapping("/{paymentId}/cancel")
    public PaymentDtos.PaymentResponse cancel(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID paymentId,
            @RequestHeader("Idempotency-Key") String key) {
        return PaymentDtos.PaymentResponse.from(payments.cancel(
                PaymentApiSupport.actor(jwt), PaymentApiSupport.staff(jwt), paymentId, key));
    }

    @PostMapping("/{paymentId}/refunds")
    public ResponseEntity<PaymentDtos.RefundResponse> requestRefund(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID paymentId,
            @RequestHeader("Idempotency-Key") String key,
            @Valid @RequestBody PaymentDtos.RefundRequestBody request) {
        var refund = payments.requestRefund(
                PaymentApiSupport.actor(jwt),
                PaymentApiSupport.staff(jwt),
                new PaymentCommands.RequestRefund(paymentId, request.amount(), request.reason()),
                key);
        return ResponseEntity.created(URI.create("/api/v1/admin/refunds/" + refund.getId()))
                .body(PaymentDtos.RefundResponse.from(refund));
    }

    @GetMapping("/{paymentId}/receipt")
    public PaymentDtos.ReceiptResponse receipt(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID paymentId) {
        return PaymentDtos.ReceiptResponse.from(payments.receipt(
                PaymentApiSupport.actor(jwt), PaymentApiSupport.staff(jwt), paymentId));
    }

    @GetMapping(value = "/{paymentId}/receipt/download", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> downloadReceipt(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID paymentId) {
        PaymentReceipt receipt = payments.receipt(
                PaymentApiSupport.actor(jwt), PaymentApiSupport.staff(jwt), paymentId);
        String filename = "receipt-" + receipt.paymentId() + ".html";
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(filename, StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .contentType(MediaType.TEXT_HTML)
                .body(renderReceipt(receipt));
    }

    @PostMapping("/{paymentId}/receipt/email")
    public PaymentDtos.ReceiptResponse emailReceipt(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID paymentId,
            @RequestHeader("Idempotency-Key") String key) {
        return PaymentDtos.ReceiptResponse.from(payments.requestReceiptEmail(
                PaymentApiSupport.actor(jwt), PaymentApiSupport.staff(jwt), paymentId, key));
    }

    @PostMapping("/sandbox/{paymentId}/complete")
    public PaymentDtos.PaymentResponse completeSandbox(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID paymentId,
            @Valid @RequestBody PaymentDtos.SandboxResultRequest request) {
        PaymentIntent payment = payments.get(
                PaymentApiSupport.actor(jwt), PaymentApiSupport.staff(jwt), paymentId);
        return PaymentDtos.PaymentResponse.from(payments.completeSandbox(
                paymentId,
                request.succeeded(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getReservationSnapshotHash(),
                "SBX-TXN-" + UUID.randomUUID(),
                request.failureCode() == null ? "SANDBOX_DECLINED" : request.failureCode()));
    }

    private static String renderReceipt(PaymentReceipt value) {
        return """
                <!doctype html>
                <html lang="vi"><head><meta charset="utf-8"><title>Biên nhận thanh toán</title>
                <style>body{font-family:Arial,sans-serif;max-width:720px;margin:40px auto;color:#172033}
                table{border-collapse:collapse;width:100%%}td{padding:10px;border-bottom:1px solid #ddd}
                td:first-child{font-weight:700;width:40%%}</style></head>
                <body><h1>Biên nhận thanh toán</h1><table>
                <tr><td>Mã thanh toán</td><td>%s</td></tr>
                <tr><td>Mã đặt phòng</td><td>%s</td></tr>
                <tr><td>Số tiền</td><td>%s %s</td></tr>
                <tr><td>Phương thức</td><td>%s</td></tr>
                <tr><td>Mã giao dịch</td><td>%s</td></tr>
                <tr><td>Thời gian</td><td>%s</td></tr>
                </table></body></html>
                """.formatted(
                escape(value.paymentId().toString()),
                escape(value.reservationReference()),
                escape(value.amount().toPlainString()),
                escape(value.currency()),
                escape(value.provider().name()),
                escape(value.providerReference()),
                escape(String.valueOf(value.paidAt())));
    }

    private static String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
