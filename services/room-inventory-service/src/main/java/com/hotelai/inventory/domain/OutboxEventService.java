package com.hotelai.inventory.domain;

import com.hotelai.inventory.common.RequestContext;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class OutboxEventService {
    private final OutboxEventRepository events;
    private final ObjectMapper objectMapper;

    public OutboxEventService(OutboxEventRepository events, ObjectMapper objectMapper) {
        this.events = events;
        this.objectMapper = objectMapper;
    }

    public void emit(String aggregateType, UUID aggregateId, String eventType, Map<String, ?> payload) {
        try {
            events.save(new OutboxEvent(
                    aggregateType, aggregateId, eventType, RequestContext.correlationId(),
                    objectMapper.writeValueAsString(payload)));
        } catch (Exception error) {
            throw new IllegalStateException("Cannot serialize inventory domain event", error);
        }
    }
}
