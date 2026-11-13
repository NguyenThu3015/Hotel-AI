package com.hotelai.servicecatalog.domain;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
class CatalogEventService {
    private final CatalogOutboxRepository outbox;
    private final ObjectMapper objectMapper;

    CatalogEventService(CatalogOutboxRepository outbox, ObjectMapper objectMapper) {
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    void catalogChanged(
            HotelServiceItem service, ServicePriceVersion price, String eventType) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("serviceId", service.getId());
        payload.put("code", service.getCode());
        payload.put("name", service.getName());
        payload.put("description", service.getDescription());
        payload.put("unit", service.getUnit());
        payload.put("serviceDays", service.getServiceDays());
        payload.put("serviceStartTime", service.getServiceStartTime());
        payload.put("serviceEndTime", service.getServiceEndTime());
        payload.put("serviceConditions", service.getServiceConditions());
        payload.put("active", service.isActive());
        payload.put("catalogVersion", service.getVersion());
        payload.put("priceVersion", price.getVersionNumber());
        payload.put("unitPrice", price.getUnitPrice());
        payload.put("taxRate", price.getTaxRate());
        payload.put("currency", price.getCurrency());
        append("HotelService", service.getId(), eventType, payload);
    }

    void usageChanged(
            ServiceUsage usage,
            String action,
            BigDecimal deltaAmount,
            UUID actorId,
            String eventType) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("usageId", usage.getId());
        payload.put("stayId", usage.getStayId());
        payload.put("reservationId", usage.getReservationId());
        payload.put("serviceId", usage.getServiceId());
        payload.put("serviceCode", usage.getServiceCode());
        payload.put("serviceName", usage.getServiceName());
        payload.put("unit", usage.getUnit());
        payload.put("quantity", usage.getQuantity());
        payload.put("unitPrice", usage.getUnitPrice());
        payload.put("taxRate", usage.getTaxRate());
        payload.put("netAmount", usage.getNetAmount());
        payload.put("taxAmount", usage.getTaxAmount());
        payload.put("grossAmount", usage.getGrossAmount());
        payload.put("deltaAmount", deltaAmount.setScale(2));
        payload.put("currency", usage.getCurrency());
        payload.put("occurredAt", usage.getOccurredAt());
        payload.put("status", usage.getStatus().name());
        payload.put("revision", usage.getRevision());
        payload.put("action", action);
        payload.put("changedBy", actorId);
        append("ServiceUsage", usage.getId(), eventType, payload);
    }

    private void append(
            String aggregateType,
            UUID aggregateId,
            String eventType,
            Map<String, Object> payload) {
        try {
            outbox.save(new CatalogOutboxEvent(
                    aggregateType, aggregateId, eventType,
                    objectMapper.writeValueAsString(payload)));
        } catch (Exception error) {
            throw new IllegalStateException("Cannot serialize catalog event", error);
        }
    }
}
