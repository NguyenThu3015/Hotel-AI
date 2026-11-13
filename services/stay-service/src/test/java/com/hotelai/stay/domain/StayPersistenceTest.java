package com.hotelai.stay.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.hotelai.stay.domain.ReservationStayProjection.ReservationSnapshot;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

@DataJpaTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:stay-persistence;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class StayPersistenceTest {
    @Autowired ReservationStayProjectionRepository reservations;
    @Autowired StayRepository stays;
    @Autowired StayGuestRepository guests;
    @Autowired RoomProjectionRepository rooms;
    @Autowired RoomAssignmentRepository assignments;
    @Autowired StayOperationRepository operations;
    @Autowired StayFolioEntryRepository folio;
    @Autowired EntityManager entityManager;

    @Test
    void persistsStayGuestsAssignmentsOperationsAndFolio() {
        UUID roomTypeId = UUID.randomUUID();
        ReservationStayProjection reservation = new ReservationStayProjection(
                new ReservationSnapshot(
                        UUID.randomUUID(), "RSV-PERSIST", UUID.randomUUID(),
                        "Guest", "guest@example.com", "0900000000", "CONFIRMED", 2,
                        roomTypeId, LocalDate.of(2026, 7, 17), LocalDate.of(2026, 7, 19),
                        1, 1, 0, "VND", new BigDecimal("1000000"),
                        new BigDecimal("300000"), new BigDecimal("300000"), UUID.randomUUID()),
                Instant.now());
        reservations.save(reservation);
        Stay stay = Stay.pendingCheckIn(
                reservation, UUID.randomUUID(), "c".repeat(64), false);
        stays.save(stay);
        guests.save(new StayGuest(
                stay.getId(), "Guest", LocalDate.of(1990, 1, 1), "VN",
                "CCCD", "encrypted-value", "1234", true));
        RoomProjection room = new RoomProjection();
        room.synchronize(
                UUID.randomUUID(), "101", roomTypeId, "AVAILABLE", true,
                null, 0, Instant.now());
        rooms.save(room);
        RoomAssignment assignment = RoomAssignment.planned(
                stay.getId(), room, UUID.randomUUID(), "CHECK_IN");
        assignments.save(assignment);
        StayOperation operation = operations.save(StayOperation.checkout(
                stay.getId(), "checkout-persist", "d".repeat(64), UUID.randomUUID()));
        operation.releasePending();
        folio.save(new StayFolioEntry(
                stay.getId(), operation.getId(), "MINIBAR", "Water",
                new BigDecimal("20000"), "VND", UUID.randomUUID()));
        entityManager.flush();
        entityManager.clear();

        Stay restored = stays.findById(stay.getId()).orElseThrow();
        assertThat(restored.getStatus()).isEqualTo(StayStatus.CHECK_IN_PENDING);
        assertThat(guests.findByStayIdOrderByPrimaryGuestDescCreatedAtAsc(stay.getId()))
                .singleElement()
                .extracting(StayGuest::getDocumentNumberLast4)
                .isEqualTo("1234");
        assertThat(assignments.findByStayIdOrderByCreatedAtAsc(stay.getId())).hasSize(1);
        assertThat(operations.findByStayIdOrderByCreatedAtAsc(stay.getId()))
                .singleElement()
                .extracting(StayOperation::getStatus)
                .isEqualTo(OperationStatus.RELEASE_PENDING);
        assertThat(folio.findByStayIdOrderByRecordedAtAsc(stay.getId())).hasSize(1);
    }

    @Test
    void findsCompletedPriceOperationsWhoseFolioEntryIsMissing() {
        UUID stayId = UUID.randomUUID();
        StayOperation operation = operations.save(StayOperation.extension(
                stayId,
                "extension-folio-repair",
                "e".repeat(64),
                LocalDate.of(2026, 7, 19),
                LocalDate.of(2026, 7, 20),
                new BigDecimal("100000"),
                "Late checkout",
                UUID.randomUUID()));
        operation.complete();
        entityManager.flush();

        assertThat(operations.findCompletedWithMissingFolio(PageRequest.of(0, 10)))
                .extracting(StayOperation::getId)
                .containsExactly(operation.getId());

        folio.save(new StayFolioEntry(
                stayId, operation.getId(), "EXTENSION", "Stay extension",
                new BigDecimal("100000"), "VND", UUID.randomUUID()));
        entityManager.flush();

        assertThat(operations.findCompletedWithMissingFolio(PageRequest.of(0, 10)))
                .isEmpty();
    }
}
