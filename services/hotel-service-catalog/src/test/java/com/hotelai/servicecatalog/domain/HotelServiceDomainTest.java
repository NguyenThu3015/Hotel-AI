package com.hotelai.servicecatalog.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hotelai.servicecatalog.domain.StayProjection.StaySnapshot;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class HotelServiceDomainTest {
    private static final UUID ACTOR = UUID.randomUUID();

    @Test
    void validatesScheduleAndAvailability() {
        HotelServiceItem service = service(true);
        ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");

        assertThat(service.isAvailableAt(
                Instant.parse("2026-07-20T03:00:00Z"), zone)).isTrue();
        assertThat(service.isAvailableAt(
                Instant.parse("2026-07-20T13:00:00Z"), zone)).isFalse();

        assertThatThrownBy(() -> new HotelServiceItem(
                "SPA", "Spa", "Treatment", "SESSION", Set.of(DayOfWeek.MONDAY),
                LocalTime.of(18, 0), LocalTime.of(8, 0), null, true, ACTOR))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void snapshotsPriceAndCreatesFinancialDeltasForAdjustAndCancel() {
        StayProjection stay = stay();
        HotelServiceItem service = service(true);
        ServicePriceVersion firstPrice = new ServicePriceVersion(
                service.getId(), 1, new BigDecimal("100000"),
                new BigDecimal("0.1000"), "VND",
                Instant.parse("2026-07-01T00:00:00Z"), ACTOR);
        ServiceUsage usage = new ServiceUsage(
                stay, service, firstPrice, new BigDecimal("2"),
                Instant.parse("2026-07-20T03:00:00Z"), true, ACTOR);

        assertThat(usage.getGrossAmount()).isEqualByComparingTo("220000.00");

        ServicePriceVersion secondPrice = new ServicePriceVersion(
                service.getId(), 2, new BigDecimal("120000"),
                new BigDecimal("0.1000"), "VND",
                Instant.parse("2026-07-20T02:00:00Z"), ACTOR);
        BigDecimal adjustment = usage.adjust(
                service, secondPrice, new BigDecimal("3"),
                Instant.parse("2026-07-20T04:00:00Z"));

        assertThat(usage.getGrossAmount()).isEqualByComparingTo("396000.00");
        assertThat(adjustment).isEqualByComparingTo("176000.00");
        assertThatThrownBy(() -> usage.cancel(UUID.randomUUID(), "Mistake", false))
                .isInstanceOf(IllegalStateException.class);
        assertThat(usage.cancel(UUID.randomUUID(), "Manager correction", true))
                .isEqualByComparingTo("-396000.00");
        assertThat(usage.getStatus()).isEqualTo(ServiceUsageStatus.CANCELLED);
    }

    private static HotelServiceItem service(boolean active) {
        return new HotelServiceItem(
                "BF", "Breakfast", "Breakfast buffet", "PERSON",
                EnumSet.allOf(DayOfWeek.class), LocalTime.of(6, 0), LocalTime.of(19, 0),
                "Book before arrival", active, ACTOR);
    }

    private static StayProjection stay() {
        StayProjection value = new StayProjection();
        value.synchronize(new StaySnapshot(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "RSV-001",
                "Guest", "OPEN", LocalDate.of(2026, 7, 17),
                LocalDate.of(2026, 7, 22), List.of("101"), "VND",
                new BigDecimal("1000000"), new BigDecimal("300000"),
                new BigDecimal("700000")), Instant.parse("2026-07-17T00:00:00Z"));
        return value;
    }
}
