package com.hotelai.billing.api;

import com.hotelai.billing.api.BillingDtos.CorrectionRequest;
import com.hotelai.billing.api.BillingDtos.DraftInvoiceRequest;
import com.hotelai.billing.api.BillingDtos.EmailInvoiceRequest;
import com.hotelai.billing.api.BillingDtos.InvoiceStayView;
import com.hotelai.billing.api.BillingDtos.InvoiceView;
import com.hotelai.billing.common.ApiException;
import com.hotelai.billing.domain.InvoiceApplicationService;
import com.hotelai.billing.domain.InvoiceApplicationService.CorrectionCommand;
import com.hotelai.billing.domain.InvoiceApplicationService.DraftCommand;
import com.hotelai.billing.domain.InvoiceApplicationService.InvoiceDetails;
import com.hotelai.billing.domain.InvoicePdfRenderer;
import com.hotelai.billing.domain.InvoiceStatus;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class InvoiceController {
    private final InvoiceApplicationService application;
    private final InvoicePdfRenderer pdf;

    public InvoiceController(
            InvoiceApplicationService application, InvoicePdfRenderer pdf) {
        this.application = application;
        this.pdf = pdf;
    }

    @GetMapping("/invoice-stays")
    List<InvoiceStayView> stays(
            @RequestParam(name = "query", required = false) String query) {
        return application.searchStays(query).stream()
                .map(InvoiceStayView::from)
                .toList();
    }

    @GetMapping("/invoices")
    List<InvoiceView> byStay(@RequestParam UUID stayId) {
        return application.byStay(stayId).stream().map(InvoiceView::from).toList();
    }

    @PostMapping("/invoices/drafts")
    @ResponseStatus(HttpStatus.CREATED)
    InvoiceView draft(
            Authentication authentication,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody DraftInvoiceRequest request) {
        return InvoiceView.from(application.createDraft(
                actor(authentication),
                idempotencyKey,
                new DraftCommand(
                        request.stayId(), request.buyerName(), request.buyerEmail(),
                        request.buyerAddress(), request.buyerTaxCode())));
    }

    @GetMapping("/invoices/{id}")
    InvoiceView details(@PathVariable UUID id) {
        return InvoiceView.from(application.details(id));
    }

    @PostMapping("/invoices/{id}/issue")
    InvoiceView issue(
            Authentication authentication,
            @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return InvoiceView.from(application.issue(
                actor(authentication), id, idempotencyKey));
    }

    @GetMapping("/invoices/{id}/download")
    ResponseEntity<byte[]> download(@PathVariable UUID id) {
        InvoiceDetails details = application.details(id);
        if (details.invoice().getStatus() == InvoiceStatus.DRAFT) {
            throw ApiException.conflict(
                    "invoice-not-issued", "Draft invoice cannot be downloaded");
        }
        byte[] content = pdf.render(details);
        String fileName = details.invoice().getInvoiceNumber() + ".pdf";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(fileName, StandardCharsets.UTF_8)
                .build());
        headers.setContentLength(content.length);
        return new ResponseEntity<>(content, headers, HttpStatus.OK);
    }

    @PostMapping("/invoices/{id}/email")
    InvoiceView email(
            Authentication authentication,
            @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody EmailInvoiceRequest request) {
        return InvoiceView.from(application.requestEmail(
                actor(authentication), id, idempotencyKey, request.recipient()));
    }

    @PostMapping("/invoices/{id}/corrections")
    InvoiceView correct(
            Authentication authentication,
            @PathVariable UUID id,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CorrectionRequest request) {
        return InvoiceView.from(application.correct(
                actor(authentication),
                manager(authentication),
                id,
                idempotencyKey,
                new CorrectionCommand(
                        request.type(), request.amountDelta(), request.reason())));
    }

    private static UUID actor(Authentication authentication) {
        try {
            return UUID.fromString(authentication.getName());
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("JWT uid must be a UUID", error);
        }
    }

    private static boolean manager(Authentication authentication) {
        return authentication.getAuthorities().stream().anyMatch(authority ->
                "ROLE_MANAGER".equals(authority.getAuthority())
                        || "ROLE_ADMIN".equals(authority.getAuthority()));
    }
}
