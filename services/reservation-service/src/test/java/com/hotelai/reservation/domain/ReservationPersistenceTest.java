package com.hotelai.reservation.domain;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

@DataJpaTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:reservation-persistence;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ReservationPersistenceTest {
    @Autowired
    ReservationRepository reservations;

    @Autowired
    EntityManager entityManager;

    @Test
    void persistsPricePolicyGuestsAndOptimisticVersion() {
        UUID id = UUID.randomUUID();
        Reservation reservation = Reservation.create(
                id,
                "RSV20260717ABCDEF12",
                UUID.randomUUID(),
                UUID.randomUUID(),
                ReservationChannel.ONLINE,
                UUID.randomUUID(),
                LocalDate.now().plusDays(2),
                LocalDate.now().plusDays(3),
                1,
                2,
                0,
                "Nguyen An",
                "an@example.com",
                "0900000000",
                null,
                new InventoryHold(UUID.randomUUID(), Instant.now().plusSeconds(900), 0),
                new PricingSnapshot(
                        UUID.randomUUID(), "price-v1", "VND", new BigDecimal("1000000"),
                        BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("1000000"),
                        new BigDecimal("300000"), false, Instant.now().plusSeconds(300),
                        "{\"nights\":1}", "{\"code\":\"FLEX\"}",
                        Instant.now().plusSeconds(3600), BigDecimal.ZERO));
        reservation.addGuest("Nguyen An", "PASSPORT", "P123", "VN", true);

        reservations.saveAndFlush(reservation);
        entityManager.clear();

        Reservation restored = reservations.findDetailedById(id).orElseThrow();
        assertThat(restored.getStatus()).isEqualTo(ReservationStatus.PENDING_PAYMENT);
        assertThat(restored.getPriceBreakdown()).contains("nights");
        assertThat(restored.getCancellationPolicy()).contains("FLEX");
        assertThat(restored.getGuests()).singleElement().satisfies(guest ->
                assertThat(guest.getDocumentNumber()).isEqualTo("P123"));
        assertThat(restored.getVersion()).isZero();
    }
}
