package com.hotelai.servicecatalog.config;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hotelai.servicecatalog.api.HotelServiceController;
import com.hotelai.servicecatalog.domain.CatalogApplicationService;
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

@WebMvcTest(controllers = HotelServiceController.class)
@Import({SecurityConfig.class, CorrelationIdFilter.class, SecurityProblemWriter.class})
@ImportAutoConfiguration({
    SecurityAutoConfiguration.class,
    ServletWebSecurityAutoConfiguration.class,
    SecurityFilterAutoConfiguration.class,
    OAuth2ResourceServerAutoConfiguration.class
})
@EnableConfigurationProperties(CatalogProperties.class)
@TestPropertySource(properties = {
    "hotel.time-zone=Asia/Ho_Chi_Minh",
    "hotel.messaging.exchange=hotel.events",
    "hotel.messaging.stay-queue=catalog.stay.test",
    "hotel.messaging.stay-routing-key=stay.#",
    "hotel.messaging.dead-letter-exchange=hotel.events.dlx",
    "hotel.messaging.dead-letter-queue=catalog.test.dlq",
    "hotel.messaging.dead-letter-routing-key=catalog.test.dead",
    "hotel.messaging.outbox-delay-ms=1000"
})
class CatalogSecurityTest {
    @org.springframework.beans.factory.annotation.Autowired
    MockMvc mvc;

    @MockitoBean
    CatalogApplicationService catalog;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Test
    void publicCatalogDoesNotRequireAuthentication() throws Exception {
        when(catalog.list(false)).thenReturn(List.of());
        mvc.perform(get("/api/v1/hotel-services"))
                .andExpect(status().isOk());
    }

    @Test
    void receptionistCannotChangeCatalog() throws Exception {
        mvc.perform(get("/api/v1/admin/hotel-services")
                        .with(jwt()
                                .jwt(token -> token.claim(
                                        "uid", UUID.randomUUID().toString()))
                                .authorities(new SimpleGrantedAuthority(
                                        "ROLE_RECEPTIONIST"))))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(
                        "application/problem+json"));
    }

    @Test
    void managerCanViewAdministrativeCatalog() throws Exception {
        when(catalog.list(true)).thenReturn(List.of());
        mvc.perform(get("/api/v1/admin/hotel-services")
                        .with(jwt()
                                .jwt(token -> token
                                        .claim("uid", UUID.randomUUID().toString())
                                        .claim("roles", List.of("MANAGER")))
                                .authorities(new SimpleGrantedAuthority("ROLE_MANAGER"))))
                .andExpect(status().isOk());
    }
}
