package com.hotelai.servicecatalog.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CatalogOutboxPublisherTest {
    @Test
    void serviceUsageEventTypesUseAStableTopicRoutingKey() {
        assertThat(CatalogOutboxPublisher.routingKey(
                "hotel-service.usage-recorded.v1"))
                .isEqualTo("hotel-service.usage.recorded.v1");
        assertThat(CatalogOutboxPublisher.routingKey(
                "hotel-service.usage-cancelled.v1"))
                .isEqualTo("hotel-service.usage.cancelled.v1");
    }

    @Test
    void nonUsageEventRoutingKeysRemainUnchanged() {
        assertThat(CatalogOutboxPublisher.routingKey(
                "hotel-service.catalog-created.v1"))
                .isEqualTo("hotel-service.catalog-created.v1");
    }
}
