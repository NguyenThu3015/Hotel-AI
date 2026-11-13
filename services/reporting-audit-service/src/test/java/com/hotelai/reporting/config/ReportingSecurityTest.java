package com.hotelai.reporting.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hotelai.reporting.api.AuditController;
import com.hotelai.reporting.api.ReportingController;
import com.hotelai.reporting.audit.AuditLedgerService;
import com.hotelai.reporting.audit.AuditQueryService;
import com.hotelai.reporting.reporting.ReportExportService;
import com.hotelai.reporting.reporting.ReportFileRenderer;
import com.hotelai.reporting.reporting.ReportingQueryService;
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
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(controllers = {ReportingController.class, AuditController.class})
@Import({SecurityConfig.class, CorrelationIdFilter.class, SecurityProblemWriter.class})
@ImportAutoConfiguration({
    SecurityAutoConfiguration.class,
    ServletWebSecurityAutoConfiguration.class,
    SecurityFilterAutoConfiguration.class,
    OAuth2ResourceServerAutoConfiguration.class
})
class ReportingSecurityTest {
    @org.springframework.beans.factory.annotation.Autowired
    MockMvc mvc;

    @MockitoBean
    ReportingQueryService reports;

    @MockitoBean
    ReportFileRenderer renderer;

    @MockitoBean
    ReportExportService exports;

    @MockitoBean
    AuditLedgerService audit;

    @MockitoBean
    AuditQueryService auditQueries;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Test
    void dashboardRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/v1/admin/reports/dashboard")
                        .param("from", "2026-07-01")
                        .param("to", "2026-07-31"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void managerCanAccessReportsButNotAuditLogs() throws Exception {
        UUID actor = UUID.randomUUID();
        var manager = jwt()
                .jwt(token -> token
                        .claim("uid", actor.toString())
                        .claim("roles", List.of("MANAGER")))
                .authorities(new SimpleGrantedAuthority("ROLE_MANAGER"));

        mvc.perform(get("/api/v1/admin/reports/dashboard")
                        .param("from", "2026-07-01")
                        .param("to", "2026-07-31")
                        .with(manager))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/admin/audit-logs").with(manager))
                .andExpect(status().isForbidden());
    }

    @Test
    void administratorCanAccessAuditLogs() throws Exception {
        UUID actor = UUID.randomUUID();
        var admin = jwt()
                .jwt(token -> token
                        .claim("uid", actor.toString())
                        .claim("roles", List.of("ADMIN")))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
        org.mockito.Mockito.when(auditQueries.search(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyInt(),
                        org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(new com.hotelai.reporting.audit.AuditQueryService.AuditPage(
                        List.of(), 0, 20, 0, 0));

        mvc.perform(get("/api/v1/admin/audit-logs").with(admin))
                .andExpect(status().isOk());
    }
}
