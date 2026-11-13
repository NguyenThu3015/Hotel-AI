package com.hotelai.inventory;

import static com.hotelai.inventory.api.InventoryDtos.FloorCreateRequest;
import static org.assertj.core.api.Assertions.assertThat;

import com.hotelai.inventory.domain.CatalogApplicationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "spring.rabbitmq.dynamic=false",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "hotel.messaging.outbox-delay-ms=3600000",
        "hotel.inventory.expiry-delay-ms=3600000",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:65535/jwks"
})
class RoomInventoryPersistenceIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("room_inventory_db")
            .withUsername("inventory")
            .withPassword("inventory");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired CatalogApplicationService catalog;
    @Autowired JdbcTemplate jdbc;

    @Test
    void flywaySchemaAndTransactionalOutboxAreOperational() {
        var floor = catalog.createFloor(new FloorCreateRequest("F09", "Tầng 9", 9));

        Integer floorRows = jdbc.queryForObject(
                "select count(*) from floors where id=?", Integer.class, floor.id());
        Integer outboxRows = jdbc.queryForObject(
                "select count(*) from outbox_events where aggregate_id=? and event_type=?",
                Integer.class, floor.id(), "inventory.floor.created.v1");

        assertThat(floorRows).isOne();
        assertThat(outboxRows).isOne();
    }
}
