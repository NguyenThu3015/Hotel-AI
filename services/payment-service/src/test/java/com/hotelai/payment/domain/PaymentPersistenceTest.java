package com.hotelai.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

@DataJpaTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:payment-persistence;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class PaymentPersistenceTest {
    @Autowired
    PaymentIntentRepository payments;

    @Autowired
    ReservationPaymentProjectionRepository reservations;

    @Autowired
    RefundRequestRepository refunds;

    @Autowired
    EntityManager entityManager;

    @Test
    void persistsReservationSnapshotPaymentAndRefundState() {
        ReservationPaymentProjection reservation = new ReservationPaymentProjection(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "guest@example.com",
                "RSV-PERSIST",
                "PENDING_PAYMENT",
                "VND",
                new BigDecimal("1000000"),
                new BigDecimal("300000"),
                BigDecimal.ZERO,
                3,
                Instant.now());
        reservation = reservations.save(reservation);
        reservation.synchronizeStay(
                "VND",
                new BigDecimal("1100000"),
                new BigDecimal("300000"),
                Instant.now().plusSeconds(1));
        PaymentIntent payment = PaymentIntent.create(
                reservation.getReservationId(),
                reservation.getCustomerId(),
                reservation.getCustomerId(),
                PaymentPurpose.DEPOSIT,
                PaymentProviderCode.CASH,
                new BigDecimal("300000"),
                "VND",
                reservation,
                "c".repeat(64),
                null);
        payment = payments.save(payment);
        payment.succeed("CASH-PERSIST", Instant.now());
        RefundRequest refund = refunds.save(new RefundRequest(
                payment.getId(),
                new BigDecimal("100000"),
                "VND",
                "Partial cancellation",
                UUID.randomUUID()));
        entityManager.flush();
        entityManager.clear();

        PaymentIntent restored = payments.findById(payment.getId()).orElseThrow();
        RefundRequest restoredRefund = refunds.findById(refund.getId()).orElseThrow();
        assertThat(restored.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(restored.getReservationSnapshotHash()).isEqualTo("c".repeat(64));
        assertThat(reservations.findById(reservation.getReservationId()).orElseThrow().getTotalAmount())
                .isEqualByComparingTo("1100000.00");
        assertThat(restoredRefund.getStatus()).isEqualTo(RefundStatus.REQUESTED);
        assertThat(restoredRefund.getPaymentId()).isEqualTo(payment.getId());
    }
}
