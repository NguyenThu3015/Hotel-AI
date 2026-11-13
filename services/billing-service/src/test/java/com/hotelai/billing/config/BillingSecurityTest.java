package com.hotelai.billing.config;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hotelai.billing.api.InvoiceController;
import com.hotelai.billing.domain.InvoiceApplicationService;
import com.hotelai.billing.domain.InvoicePdfRenderer;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = InvoiceController.class)
@Import({SecurityConfig.class, CorrelationIdFilter.class, SecurityProblemWriter.class})
@ImportAutoConfiguration({
    SecurityAutoConfiguration.class,
    ServletWebSecurityAutoConfiguration.class,
    SecurityFilterAutoConfiguration.class,
    OAuth2ResourceServerAutoConfiguration.class
})
class BillingSecurityTest {
    @org.springframework.beans.factory.annotation.Autowired
    MockMvc mvc;

    @MockitoBean
    InvoiceApplicationService application;

    @MockitoBean
    InvoicePdfRenderer pdf;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Test
    void invoiceSearchRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/admin/invoice-stays"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"));
    }

    @Test
    void receptionistCanSearchInvoiceSources() throws Exception {
        when(application.searchStays(null)).thenReturn(List.of());

        mvc.perform(get("/api/v1/admin/invoice-stays")
                        .with(jwt()
                                .jwt(token -> token
                                        .claim("uid", UUID.randomUUID().toString())
                                        .claim("roles", List.of("RECEPTIONIST")))
                                .authorities(new SimpleGrantedAuthority(
                                        "ROLE_RECEPTIONIST"))))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }
}
