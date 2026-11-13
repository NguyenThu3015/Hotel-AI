package com.hotelai.reporting.api;

import com.hotelai.reporting.audit.AuditLedgerService;
import com.hotelai.reporting.audit.AuditLedgerService.AuditRecord;
import com.hotelai.reporting.common.ApiException;
import com.hotelai.reporting.config.ReportingProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.UUID;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/internal/v1/audit-events")
public class InternalAuditController {
    private final AuditLedgerService audit;
    private final ReportingProperties properties;

    public InternalAuditController(
            AuditLedgerService audit, ReportingProperties properties) {
        this.audit = audit;
        this.properties = properties;
    }

    @PostMapping
    AuditAccepted append(
            @RequestHeader("X-Internal-Key") String internalKey,
            @Valid @RequestBody AuditCommand command) {
        requireInternalKey(internalKey);
        UUID id = audit.append(new AuditRecord(
                command.eventId(),
                command.actorId(),
                command.action(),
                command.entityType(),
                command.entityId(),
                command.producer(),
                command.correlationId(),
                command.ipAddress(),
                command.beforeState(),
                command.afterState(),
                command.details(),
                command.occurredAt()));
        return new AuditAccepted(id);
    }

    private void requireInternalKey(String provided) {
        boolean valid = MessageDigest.isEqual(
                properties.internalKey().getBytes(StandardCharsets.UTF_8),
                (provided == null ? "" : provided).getBytes(StandardCharsets.UTF_8));
        if (!valid) throw new ApiException(
                401, "invalid-internal-key", "Internal service authentication failed");
    }

    public record AuditCommand(
            @NotNull UUID eventId,
            UUID actorId,
            @NotBlank String action,
            @NotBlank String entityType,
            String entityId,
            @NotBlank String producer,
            String correlationId,
            String ipAddress,
            JsonNode beforeState,
            JsonNode afterState,
            String details,
            Instant occurredAt) {}

    public record AuditAccepted(UUID auditId) {}
}
