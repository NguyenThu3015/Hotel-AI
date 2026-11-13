package com.hotel.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionLocator;

@SpringBootTest(
    properties = {
      "IDENTITY_SERVICE_URL=http://identity.test:9181",
      "CUSTOMER_SERVICE_URL=http://customer.test:9182",
      "ROOM_INVENTORY_SERVICE_URL=http://inventory.test:9183",
      "RESERVATION_SERVICE_URL=http://reservation.test:9184",
      "PRICING_SERVICE_URL=http://pricing.test:9185",
      "PAYMENT_SERVICE_URL=http://payment.test:9186",
      "STAY_SERVICE_URL=http://stay.test:9187",
      "HOTEL_SERVICE_CATALOG_URL=http://hotel-services.test:9188",
      "BILLING_SERVICE_URL=http://billing.test:9189",
      "REVIEW_SERVICE_URL=http://review.test:9190",
      "NOTIFICATION_SERVICE_URL=http://notification.test:9191",
      "REPORTING_AUDIT_SERVICE_URL=http://reporting.test:9192",
      "AI_CONVERSATION_SERVICE_URL=http://ai-conversation.test:9193",
      "AI_RECOMMENDATION_SERVICE_URL=http://ai-recommendation.test:9194",
      "AI_FORECASTING_SERVICE_URL=http://ai-forecasting.test:9195",
      "AI_REVENUE_SERVICE_URL=http://ai-revenue.test:9196",
      "AI_CUSTOMER_ANALYTICS_SERVICE_URL=http://ai-customer-analytics.test:9197",
      "JWT_ISSUER_URI=https://identity.test",
      "JWT_JWK_SET_URI=https://identity.test/.well-known/jwks.json",
      "debug=false"
    })
class RouteConfigurationTest {

  @Autowired RouteDefinitionLocator routeDefinitionLocator;

  @Test
  void loadsExplicitMicroserviceRoutesWithoutCatchAllFallback() {
    Map<String, RouteDefinition> routes =
        routeDefinitionLocator
            .getRouteDefinitions()
            .collectList()
            .block(Duration.ofSeconds(5))
            .stream()
            .collect(Collectors.toMap(RouteDefinition::getId, Function.identity()));

    assertThat(routes)
        .containsKeys(
            "identity-auth",
            "customer-me",
            "identity-admin-users",
            "identity-admin-roles",
            "identity-admin-staff",
            "identity-admin-permissions",
            "customer-service",
            "customer-profile",
            "notification-service",
            "room-inventory-public",
            "room-inventory-admin",
            "room-operations",
            "reservation-service",
            "pricing-service",
            "payment-service",
            "stay-service",
            "hotel-service-catalog",
            "billing-service",
            "review-service",
            "reporting-audit-service",
            "ai-conversation-service",
            "ai-recommendation-service",
            "ai-forecasting-service",
            "ai-revenue-service",
            "ai-customer-analytics-service");

    assertThat(routes.get("identity-auth").getUri())
        .isEqualTo(URI.create("http://identity.test:9181"));
    assertThat(routes.get("customer-me").getUri())
        .isEqualTo(URI.create("http://customer.test:9182"));
    assertThat(routes.get("customer-service").getUri())
        .isEqualTo(URI.create("http://customer.test:9182"));
    assertThat(routes.get("notification-service").getUri())
        .isEqualTo(URI.create("http://notification.test:9191"));
    assertThat(routes.get("room-inventory-public").getUri())
        .isEqualTo(URI.create("http://inventory.test:9183"));
    assertThat(routes.get("room-inventory-admin").getUri())
        .isEqualTo(URI.create("http://inventory.test:9183"));
    assertThat(routes.get("room-operations").getUri())
        .isEqualTo(URI.create("http://inventory.test:9183"));
    assertThat(routes.get("reservation-service").getUri())
        .isEqualTo(URI.create("http://reservation.test:9184"));
    assertThat(routes.get("pricing-service").getUri())
        .isEqualTo(URI.create("http://pricing.test:9185"));
    assertThat(routes.get("payment-service").getUri())
        .isEqualTo(URI.create("http://payment.test:9186"));
    assertThat(routes.get("stay-service").getUri())
        .isEqualTo(URI.create("http://stay.test:9187"));
    assertThat(routes.get("hotel-service-catalog").getUri())
        .isEqualTo(URI.create("http://hotel-services.test:9188"));
    assertThat(routes.get("billing-service").getUri())
        .isEqualTo(URI.create("http://billing.test:9189"));
    assertThat(routes.get("review-service").getUri())
        .isEqualTo(URI.create("http://review.test:9190"));
    assertThat(routes.get("reporting-audit-service").getUri())
        .isEqualTo(URI.create("http://reporting.test:9192"));
    assertThat(routes.get("ai-conversation-service").getUri())
        .isEqualTo(URI.create("http://ai-conversation.test:9193"));
    assertThat(routes.get("ai-recommendation-service").getUri())
        .isEqualTo(URI.create("http://ai-recommendation.test:9194"));
    assertThat(routes.get("ai-forecasting-service").getUri())
        .isEqualTo(URI.create("http://ai-forecasting.test:9195"));
    assertThat(routes.get("ai-revenue-service").getUri())
        .isEqualTo(URI.create("http://ai-revenue.test:9196"));
    assertThat(routes.get("ai-customer-analytics-service").getUri())
        .isEqualTo(URI.create("http://ai-customer-analytics.test:9197"));
    assertThat(routes.get("room-inventory-public").getPredicates().toString())
        .contains("/api/v1/catalog/room-types/**")
        .doesNotContain("/api/v1/catalog/**");
    assertThat(routes.get("room-inventory-admin").getPredicates().toString())
        .contains("/api/v1/admin/rooms/**", "/api/v1/admin/hotels/**");
    assertThat(routes.get("room-operations").getPredicates().toString())
        .contains("/api/v1/housekeeping/**", "/api/v1/maintenance/**");
    assertThat(routes.get("reservation-service").getPredicates().toString())
        .contains("/api/v1/admin/reservations/**", "/api/v1/bookings/**");
    assertThat(routes.get("pricing-service").getPredicates().toString())
        .contains("/api/v1/admin/pricing/**");
    assertThat(routes.get("payment-service").getPredicates().toString())
        .contains("/api/v1/payments/**", "/api/v1/admin/refunds/**");
    assertThat(routes.get("stay-service").getPredicates().toString())
        .contains("/api/v1/admin/stays/**", "/api/v1/admin/stay-reservations/**");
    assertThat(routes.get("hotel-service-catalog").getPredicates().toString())
        .contains("/api/v1/hotel-services/**", "/api/v1/admin/service-usages/**");
    assertThat(routes.get("billing-service").getPredicates().toString())
        .contains("/api/v1/admin/invoices/**", "/api/v1/admin/invoice-stays/**");
    assertThat(routes.get("review-service").getPredicates().toString())
        .contains("/api/v1/reviews/**", "/api/v1/admin/reviews/**");
    assertThat(routes.get("reporting-audit-service").getPredicates().toString())
        .contains("/api/v1/admin/reports/**", "/api/v1/admin/audit-logs/**");
    assertThat(routes.get("customer-service").getPredicates().toString())
        .contains("/api/v1/admin/customers/**");
    assertThat(routes.get("notification-service").getPredicates().toString())
        .contains("/api/v1/admin/notifications/**");
    assertThat(routes.get("ai-conversation-service").getPredicates().toString())
        .contains("/api/v1/ai/chat", "/api/v1/ai/staff-assistant/chat");
    assertThat(routes.get("ai-recommendation-service").getPredicates().toString())
        .contains("/api/v1/ai/recommendations");
    assertThat(routes.get("ai-forecasting-service").getPredicates().toString())
        .contains("/api/v1/ai/forecast", "/api/v1/ai/anomalies");
    assertThat(routes.get("ai-revenue-service").getPredicates().toString())
        .contains("/api/v1/ai/revenue/**");
    assertThat(routes.get("ai-customer-analytics-service").getPredicates().toString())
        .contains("/api/v1/ai/reviews/analyze", "/api/v1/ai/risks/**");
    assertThat(routes).doesNotContainKey("legacy-backend-fallback");
    assertThat(routes.get("identity-auth").getFilters())
        .anySatisfy(filter -> assertThat(filter.getName()).isEqualTo("RequestRateLimiter"));
  }
}
