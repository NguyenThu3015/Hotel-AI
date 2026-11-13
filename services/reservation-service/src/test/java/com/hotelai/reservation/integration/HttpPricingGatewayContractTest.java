package com.hotelai.reservation.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.hotelai.reservation.config.ReservationProperties;
import com.hotelai.reservation.domain.PricingGateway;
import com.hotelai.reservation.domain.PricingSnapshot;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class HttpPricingGatewayContractTest {

    @Test
    void mapsCanonicalPricingQuoteWithoutLosingFeesDepositOrCancellationPolicy() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://pricing.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpPricingGateway gateway = new HttpPricingGateway(
                builder.build(), properties(), "Asia/Ho_Chi_Minh");
        UUID roomTypeId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID quoteId = UUID.randomUUID();

        server.expect(requestTo("http://pricing.test/internal/v1/pricing/quotes"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Key", "internal-test-key"))
                .andExpect(header("Idempotency-Key", org.hamcrest.Matchers.startsWith("reservation-quote:")))
                .andExpect(jsonPath("$.roomTypeId").value(roomTypeId.toString()))
                .andExpect(jsonPath("$.rooms").value(2))
                .andRespond(withSuccess("""
                        {
                          "quoteId":"%s",
                          "quotedAt":"2026-07-17T08:00:00Z",
                          "validUntil":"2026-07-17T10:00:00Z",
                          "roomTypeId":"%s",
                          "checkIn":"2026-08-01",
                          "checkOut":"2026-08-03",
                          "rooms":2,
                          "adults":3,
                          "children":1,
                          "nights":2,
                          "currency":"VND",
                          "nightlyPrices":[
                            {"date":"2026-08-01","lineTotal":1000000},
                            {"date":"2026-08-02","lineTotal":1000000}
                          ],
                          "roomSubtotal":2000000,
                          "promotion":null,
                          "discountAmount":0,
                          "netSubtotal":2000000,
                          "fees":{
                            "taxPercent":5,
                            "taxAmount":100000,
                            "serviceFeePercent":2.5,
                            "serviceFeeAmount":50000
                          },
                          "totalAmount":2150000,
                          "deposit":{
                            "required":true,
                            "type":"FIXED",
                            "configuredValue":300000,
                            "amount":300000
                          },
                          "policies":[{
                            "policyVersionId":"%s",
                            "policyKey":"%s",
                            "version":3,
                            "type":"CANCELLATION",
                            "scope":"GLOBAL",
                            "guestDisplayText":"Miễn phí trước 48 giờ",
                            "parameters":{
                              "freeCancellationHours":48,
                              "latePenaltyType":"PERCENT",
                              "latePenaltyValue":25
                            }
                          }]
                        }
                        """.formatted(quoteId, roomTypeId, UUID.randomUUID(), UUID.randomUUID()),
                        MediaType.APPLICATION_JSON));

        PricingSnapshot result = gateway.quote(new PricingGateway.QuoteCommand(
                customerId,
                roomTypeId,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 3),
                2,
                3,
                1,
                null,
                "ONLINE"));

        assertThat(result.quoteId()).isEqualTo(quoteId);
        assertThat(result.pricingVersion()).isEqualTo("quote:" + quoteId);
        assertThat(result.subtotal()).isEqualByComparingTo("2000000");
        assertThat(result.taxAmount()).isEqualByComparingTo("150000");
        assertThat(result.totalAmount()).isEqualByComparingTo("2150000");
        assertThat(result.depositRequired()).isEqualByComparingTo("300000");
        assertThat(result.manualConfirmationRequired()).isFalse();
        assertThat(result.freeCancellationUntil()).isEqualTo(Instant.parse("2026-07-29T17:00:00Z"));
        assertThat(result.cancellationFeeAfterCutoff()).isEqualByComparingTo("537500.00");
        assertThat(result.priceBreakdown()).contains("\"nightlyPrices\"");
        assertThat(result.cancellationPolicy()).contains("\"CANCELLATION\"");
        server.verify();
    }

    private static ReservationProperties properties() {
        return new ReservationProperties(
                new ReservationProperties.Security("internal-test-key"),
                new ReservationProperties.Clients(
                        "http://inventory.test", "http://pricing.test",
                        Duration.ofSeconds(1), Duration.ofSeconds(2)),
                new ReservationProperties.Reservation(
                        Duration.ofMinutes(15), 60, Duration.ofHours(24)),
                new ReservationProperties.Messaging(
                        "hotel.events", "payment.queue", "payment.*.v1",
                        "stay.queue", java.util.List.of(
                                "stay.checked-in.v1", "stay.checked-out.v1", "stay.no-show.v1"),
                        1000, 30000));
    }
}
