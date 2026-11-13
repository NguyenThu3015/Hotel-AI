package com.hotelai.reservation.messaging;

import com.hotelai.reservation.domain.ReservationApplicationService;
import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class HoldExpiryScheduler {
    private final ReservationApplicationService reservations;

    public HoldExpiryScheduler(ReservationApplicationService reservations) {
        this.reservations = reservations;
    }

    @Scheduled(fixedDelayString = "${hotel.messaging.hold-expiry-delay-ms:30000}")
    public void expire() {
        reservations.expireHolds(Instant.now(), 100);
    }
}
