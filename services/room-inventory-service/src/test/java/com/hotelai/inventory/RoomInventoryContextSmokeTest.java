package com.hotelai.inventory;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:inventory;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.rabbitmq.dynamic=false",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "hotel.messaging.outbox-delay-ms=3600000",
        "hotel.inventory.expiry-delay-ms=3600000",
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:65535/jwks"
})
class RoomInventoryContextSmokeTest {
    @Autowired ApplicationContext context;

    @Test
    void applicationContextAndRepositoryQueriesStart() {
        assertThat(context.getBean(RoomInventoryServiceApplication.class)).isNotNull();
    }
}
