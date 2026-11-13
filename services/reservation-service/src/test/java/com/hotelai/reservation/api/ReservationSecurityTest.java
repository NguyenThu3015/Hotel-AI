package com.hotelai.reservation.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hotelai.reservation.config.CorrelationIdFilter;
import com.hotelai.reservation.config.ReservationProperties;
import com.hotelai.reservation.config.SecurityConfig;
import com.hotelai.reservation.config.SecurityProblemWriter;
import com.hotelai.reservation.domain.ReservationApplicationService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
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
    CustomerReservationController.class,
    AdminReservationController.class,
    InternalReservationController.class
})
@Import({SecurityConfig.class, CorrelationIdFilter.class, SecurityProblemWriter.class})
@ImportAutoConfiguration({
    SecurityAutoConfiguration.class,
    ServletWebSecurityAutoConfiguration.class,
    SecurityFilterAutoConfiguration.class,
    OAuth2ResourceServerAutoConfiguration.class
})
@EnableConfigurationProperties(ReservationProperties.class)
@TestPropertySource(properties = {
    "hotel.security.internal-key=test-key",
    "hotel.clients.inventory-url=http://inventory",
    "hotel.clients.pricing-url=http://pricing",
    "hotel.clients.connect-timeout=1s",
    "hotel.clients.read-timeout=2s",
    "hotel.reservation.hold-duration=15m",
    "hotel.reservation.maximum-stay-nights=60",
    "hotel.reservation.change-cutoff=24h",
    "hotel.messaging.exchange=hotel.events",
    "hotel.messaging.payment-queue=reservation.payment.test",
    "hotel.messaging.payment-routing-key=payment.*.v1",
    "hotel.messaging.stay-queue=reservation.stay.test",
    "hotel.messaging.stay-routing-keys=stay.checked-in.v1,stay.checked-out.v1,stay.no-show.v1",
    "hotel.messaging.outbox-delay-ms=1000",
    "hotel.messaging.hold-expiry-delay-ms=30000"
})
class ReservationSecurityTest {
    @org.springframework.beans.factory.annotation.Autowired
    MockMvc mvc;

    @MockitoBean
    ReservationApplicationService reservations;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Test
    void anonymousCustomerHistoryIsRejectedAsProblemJson() throws Exception {
        mvc.perform(get("/api/v1/reservations"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void customerCannotUseStaffHistory() throws Exception {
        mvc.perform(get("/api/v1/admin/reservations")
                        .with(jwt()
                                .jwt(token -> token.claim("uid", UUID.randomUUID().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void receptionistCanUseStaffHistory() throws Exception {
        when(reservations.staffHistory(isNull(), isNull(), isNull(), isNull(), any()))
                .thenReturn(Page.empty());

        mvc.perform(get("/api/v1/admin/reservations")
                        .with(jwt()
                                .jwt(token -> token.claim("uid", UUID.randomUUID().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_RECEPTIONIST"))))
                .andExpect(status().isOk());
    }

    @Test
    void internalContextRejectsMissingServiceCredential() throws Exception {
        mvc.perform(get("/internal/reservations/{id}/payment-context", UUID.randomUUID()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }
}
