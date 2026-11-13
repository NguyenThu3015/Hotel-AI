package com.hotelai.stay.messaging;

import com.hotelai.stay.domain.StayApplicationService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class StaySagaScheduler {
    private final StayApplicationService stays;

    public StaySagaScheduler(StayApplicationService stays) {
        this.stays = stays;
    }

    @Scheduled(fixedDelayString = "${hotel.messaging.saga-delay-ms:5000}")
    public void retryPending() {
        stays.advancePending(50);
    }
}
