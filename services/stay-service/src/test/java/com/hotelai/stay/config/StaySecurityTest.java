package com.hotelai.stay.config;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hotelai.stay.api.StayController;
import com.hotelai.stay.domain.StayApplicationService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = StayController.class)
@Import({SecurityConfig.class, CorrelationIdFilter.class, SecurityProblemWriter.class})
@ImportAutoConfiguration({
    SecurityAutoConfiguration.class,
    ServletWebSecurityAutoConfiguration.class,
    SecurityFilterAutoConfiguration.class,
    OAuth2ResourceServerAutoConfiguration.class
})
@EnableConfigurationProperties(StayProperties.class)
@TestPropertySource(properties = {
    "hotel.security.internal-key=test-internal-key",
    "hotel.security.document-encryption-key=MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=",
    "hotel.clients.inventory-url=http://inventory",
    "hotel.clients.connect-timeout=2s",
    "hotel.clients.read-timeout=5s",
    "hotel.stay.time-zone=Asia/Ho_Chi_Minh",
    "hotel.stay.standard-check-in-hour=14",
    "hotel.stay.child-maximum-age=11",
    "hotel.stay.primary-guest-minimum-age=18",
    "hotel.stay.maximum-extension-days=30",
    "hotel.messaging.exchange=hotel.events",
    "hotel.messaging.reservation-queue=stay.reservation.test",
    "hotel.messaging.reservation-routing-key=reservation.*.v1",
    "hotel.messaging.inventory-queue=stay.inventory.test",
    "hotel.messaging.inventory-routing-key=inventory.#",
    "hotel.messaging.dead-letter-exchange=hotel.events.dlx",
    "hotel.messaging.dead-letter-queue=stay.test.dlq",
    "hotel.messaging.dead-letter-routing-key=stay.test.dead",
    "hotel.messaging.outbox-delay-ms=1000",
    "hotel.messaging.saga-delay-ms=5000"
})
class StaySecurityTest {
    @org.springframework.beans.factory.annotation.Autowired
    MockMvc mvc;

    @MockitoBean
    StayApplicationService stays;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Test
    void anonymousCannotSearchCheckInReservations() throws Exception {
        mvc.perform(get("/api/v1/admin/stay-reservations"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void customerCannotAccessReceptionOperations() throws Exception {
        mvc.perform(get("/api/v1/admin/stay-reservations")
                        .with(jwt()
                                .jwt(token -> token.claim("uid", UUID.randomUUID().toString()))
                                .authorities(new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void receptionistCanSearchCheckInReservations() throws Exception {
        when(stays.searchReservations("RSV")).thenReturn(List.of());
        mvc.perform(get("/api/v1/admin/stay-reservations")
                        .param("query", "RSV")
                        .with(jwt()
                                .jwt(token -> token
                                        .claim("uid", UUID.randomUUID().toString())
                                        .claim("roles", List.of("RECEPTIONIST")))
                                .authorities(new SimpleGrantedAuthority("ROLE_RECEPTIONIST"))))
                .andExpect(status().isOk());
    }
}
