package com.hotelai.reservation.messaging;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hotelai.reservation.domain.ReservationApplicationService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class PaymentEventConsumerTest {
    private final ReservationApplicationService reservations = mock(ReservationApplicationService.class);
    private final PaymentEventConsumer consumer =
            new PaymentEventConsumer(new ObjectMapper(), reservations);

    @Test
    void acknowledgesPaymentLifecycleEventsThatDoNotMutateReservations() throws Exception {
        consumer.consume("""
                {
                  "eventId":"f16c0bcc-ccfb-4947-92d6-210d99b40bd7",
                  "eventType":"payment.intent-created.v1",
                  "occurredAt":"2026-07-17T00:00:00Z",
                  "producer":"payment-service",
                  "correlationId":"test",
                  "aggregateType":"Payment",
                  "aggregateId":"39cc9365-e2e9-4b21-b8a8-fe4d9e7fc680",
                  "schemaVersion":1,
                  "payload":{}
                }
                """);

        verifyNoInteractions(reservations);
    }
}
