package com.hotelai.payment.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hotelai.payment.api.AdminPaymentController;
import com.hotelai.payment.api.AdminRefundController;
import com.hotelai.payment.api.CustomerPaymentController;
import com.hotelai.payment.api.PaymentWebhookController;
import com.hotelai.payment.domain.PaymentApplicationService;
import com.hotelai.payment.domain.PaymentWebhookService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {
    CustomerPaymentController.class,
    AdminPaymentController.class,
    AdminRefundController.class,
    PaymentWebhookController.class
})
@Import({SecurityConfig.class, CorrelationIdFilter.class, SecurityProblemWriter.class})
@ImportAutoConfiguration({
    SecurityAutoConfiguration.class,
    ServletWebSecurityAutoConfiguration.class,
    SecurityFilterAutoConfiguration.class,
    OAuth2ResourceServerAutoConfiguration.class
})
@EnableConfigurationProperties(PaymentProperties.class)
@TestPropertySource(properties = {
    "hotel.security.internal-key=test-internal-key",
    "hotel.payment.intent-ttl=15m",
    "hotel.payment.idempotency-ttl=24h",
    "hotel.messaging.exchange=hotel.events",
    "hotel.messaging.reservation-queue=payment.reservation.test",
    "hotel.messaging.reservation-routing-key=reservation.*.v1",
    "hotel.messaging.stay-queue=payment.stay.test",
    "hotel.messaging.stay-routing-key=stay.*.v1",
    "hotel.messaging.dead-letter-exchange=hotel.events.dlx",
    "hotel.messaging.dead-letter-queue=payment.test.dlq",
    "hotel.messaging.dead-letter-routing-key=payment.test.dead",
    "hotel.messaging.outbox-delay-ms=1000",
    "hotel.providers.momo.enabled=false",
    "hotel.providers.momo.endpoint=https://momo.example/create",
    "hotel.providers.momo.query-endpoint=https://momo.example/query",
    "hotel.providers.momo.refund-endpoint=https://momo.example/refund",
    "hotel.providers.momo.refund-query-endpoint=https://momo.example/refund/query",
    "hotel.providers.momo.partner-code=",
    "hotel.providers.momo.access-key=",
    "hotel.providers.momo.secret-key=",
    "hotel.providers.momo.redirect-url=http://localhost/result",
    "hotel.providers.momo.ipn-url=http://localhost/ipn",
    "hotel.providers.casso.enabled=false",
    "hotel.providers.casso.webhook-secret=",
    "hotel.providers.casso.bank-id=970422",
    "hotel.providers.casso.account-number=1234567890",
    "hotel.providers.casso.account-name=HOTEL AI",
    "hotel.providers.casso.qr-base-url=https://img.vietqr.io/image",
    "hotel.providers.casso.qr-template=compact2",
    "hotel.providers.casso.transfer-prefix=HOTELAI",
    "hotel.providers.casso.time-zone=Asia/Ho_Chi_Minh",
    "hotel.providers.sandbox.enabled=true",
    "hotel.providers.sandbox.checkout-base-url=http://localhost/sandbox"
})
class PaymentSecurityTest {
    @org.springframework.beans.factory.annotation.Autowired
    MockMvc mvc;

    @MockitoBean
    PaymentApplicationService payments;

    @MockitoBean
    PaymentWebhookService webhooks;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Test
    void anonymousPaymentHistoryIsRejectedAsProblemJson() throws Exception {
        mvc.perform(get("/api/v1/payments"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void customerCannotUseStaffPaymentHistory() throws Exception {
        mvc.perform(get("/api/v1/admin/payments")
                        .param("reservationId", UUID.randomUUID().toString())
                        .with(jwt()
                                .jwt(token -> token.claim("uid", UUID.randomUUID().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void receptionistCanUseStaffPaymentHistory() throws Exception {
        UUID actor = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        when(payments.history(eq(actor), eq(true), eq(reservationId), any()))
                .thenReturn(Page.empty());

        mvc.perform(get("/api/v1/admin/payments")
                        .param("reservationId", reservationId.toString())
                        .with(jwt()
                                .jwt(token -> token
                                        .claim("uid", actor.toString())
                                        .claim("roles", java.util.List.of("RECEPTIONIST")))
                                .authorities(new SimpleGrantedAuthority("ROLE_RECEPTIONIST"))))
                .andExpect(status().isOk());
    }

    @Test
    void cassoWebhookIsPublicAndReturnsStrictModeSuccessBody() throws Exception {
        when(webhooks.processCasso("{\"error\":0}", "t=1,v1=signature", null))
                .thenReturn(new PaymentWebhookService.WebhookOutcome(
                        HttpStatus.NO_CONTENT, false, "IGNORED_UNMATCHED", null));

        mvc.perform(post("/api/v1/payments/webhooks/casso")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Casso-Signature", "t=1,v1=signature")
                        .content("{\"error\":0}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }
}
