package com.hotelai.payment.api;

import com.hotelai.payment.config.CorrelationIdFilter;
import com.hotelai.payment.domain.PaymentWebhookService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments/webhooks")
public class PaymentWebhookController {
    private final PaymentWebhookService webhooks;

    public PaymentWebhookController(PaymentWebhookService webhooks) {
        this.webhooks = webhooks;
    }

    @PostMapping("/momo")
    public ResponseEntity<?> momo(
            @RequestBody String rawPayload,
            @RequestHeader(value = CorrelationIdFilter.HEADER, required = false) String correlationId) {
        var outcome = webhooks.processMomo(rawPayload, correlationId);
        if (outcome.httpStatus().is2xxSuccessful()) {
            return ResponseEntity.status(outcome.httpStatus()).build();
        }
        return ResponseEntity.status(outcome.httpStatus()).body(Map.of(
                "accepted", false,
                "replay", outcome.replay(),
                "result", outcome.processingResult()));
    }

    @PostMapping("/casso")
    public ResponseEntity<?> casso(
            @RequestBody String rawPayload,
            @RequestHeader(value = "X-Casso-Signature", required = false) String signature,
            @RequestHeader(value = CorrelationIdFilter.HEADER, required = false) String correlationId) {
        var outcome = webhooks.processCasso(rawPayload, signature, correlationId);
        if (outcome.httpStatus().is2xxSuccessful()) {
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "replay", outcome.replay(),
                    "result", outcome.processingResult()));
        }
        return ResponseEntity.status(outcome.httpStatus()).body(Map.of(
                "success", false,
                "replay", outcome.replay(),
                "result", outcome.processingResult()));
    }
}
