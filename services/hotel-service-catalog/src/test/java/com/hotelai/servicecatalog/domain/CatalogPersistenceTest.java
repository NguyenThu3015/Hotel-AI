package com.hotelai.servicecatalog.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.hotelai.servicecatalog.domain.StayProjection.StaySnapshot;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;

@DataJpaTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:catalog-persistence;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CatalogPersistenceTest {
    @Autowired HotelServiceItemRepository services;
    @Autowired ServicePriceVersionRepository prices;
    @Autowired StayProjectionRepository stays;
    @Autowired ServiceUsageRepository usages;
    @Autowired ServiceUsageRevisionRepository revisions;
    @Autowired EntityManager entityManager;

    @Test
    void persistsPriceHistoryUsageAndImmutableRevision() {
        UUID actor = UUID.randomUUID();
        HotelServiceItem service = services.save(new HotelServiceItem(
                "LAUNDRY", "Laundry", "Laundry by item", "ITEM",
                EnumSet.allOf(DayOfWeek.class), null, null, null, true, actor));
        ServicePriceVersion price = prices.save(new ServicePriceVersion(
                service.getId(), 1, new BigDecimal("25000"),
                new BigDecimal("0.0800"), "VND", Instant.now().minusSeconds(60), actor));
        StayProjection stay = new StayProjection();
        stay.synchronize(new StaySnapshot(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "RSV-PERSIST",
                "Guest", "OPEN", LocalDate.now().minusDays(1), LocalDate.now().plusDays(1),
                List.of("301"), "VND", new BigDecimal("1000000"),
                new BigDecimal("1000000"), BigDecimal.ZERO), Instant.now());
        stay = stays.save(stay);
        ServiceUsage usage = usages.save(new ServiceUsage(
                stay, service, price, new BigDecimal("2"), Instant.now(), false, actor));
        revisions.save(new ServiceUsageRevision(
                usage, "RECORDED", "catalog-persist-001", "a".repeat(64),
                usage.getGrossAmount(), "Guest request", actor));
        entityManager.flush();
        entityManager.clear();

        ServiceUsage restored = usages.findById(usage.getId()).orElseThrow();
        assertThat(restored.getGrossAmount()).isEqualByComparingTo("54000.00");
        assertThat(revisions.findByUsageIdOrderByRevisionNumberAsc(usage.getId()))
                .singleElement()
                .extracting(ServiceUsageRevision::getDeltaAmount)
                .isEqualTo(new BigDecimal("54000.00"));
        assertThat(prices.findByServiceIdOrderByValidFromDesc(service.getId())).hasSize(1);
    }
}
