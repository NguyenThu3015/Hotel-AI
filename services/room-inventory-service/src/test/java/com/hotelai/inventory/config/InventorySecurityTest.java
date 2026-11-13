package com.hotelai.inventory.config;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hotelai.inventory.api.AdminInventoryController;
import com.hotelai.inventory.domain.CatalogApplicationService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = AdminInventoryController.class)
@Import({SecurityConfig.class, CorrelationIdFilter.class, InternalServiceKeyFilter.class})
@ImportAutoConfiguration({
    SecurityAutoConfiguration.class,
    ServletWebSecurityAutoConfiguration.class,
    SecurityFilterAutoConfiguration.class,
    OAuth2ResourceServerAutoConfiguration.class
})
@TestPropertySource(properties = "hotel.security.internal-key=inventory-security-test")
class InventorySecurityTest {
    @org.springframework.beans.factory.annotation.Autowired
    MockMvc mvc;

    @MockitoBean
    CatalogApplicationService catalog;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Test
    void receptionistCanReadRoomsForFrontDesk() throws Exception {
        when(catalog.listRooms()).thenReturn(List.of());

        mvc.perform(get("/api/v1/admin/rooms").with(role("RECEPTIONIST")))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void housekeeperCanReadHotelRoomsForOperations() throws Exception {
        when(catalog.listRooms()).thenReturn(List.of());

        mvc.perform(get("/api/v1/admin/hotels/1/rooms").with(role("HOUSEKEEPER")))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void receptionistCannotCreateRooms() throws Exception {
        mvc.perform(post("/api/v1/admin/rooms")
                        .with(role("RECEPTIONIST"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void housekeeperCannotCreateRooms() throws Exception {
        mvc.perform(post("/api/v1/admin/rooms")
                        .with(role("HOUSEKEEPER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor role(String role) {
        return jwt()
                .jwt(token -> token.claim("uid", UUID.randomUUID().toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
