package com.hotelai.review.config;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hotelai.review.api.AdminReviewController;
import com.hotelai.review.api.ReviewController;
import com.hotelai.review.domain.ReviewApplicationService;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = {ReviewController.class, AdminReviewController.class})
@Import({SecurityConfig.class, CorrelationIdFilter.class, SecurityProblemWriter.class})
@ImportAutoConfiguration({
    SecurityAutoConfiguration.class,
    ServletWebSecurityAutoConfiguration.class,
    SecurityFilterAutoConfiguration.class,
    OAuth2ResourceServerAutoConfiguration.class
})
class ReviewSecurityTest {
    @org.springframework.beans.factory.annotation.Autowired
    MockMvc mvc;

    @MockitoBean
    ReviewApplicationService application;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Test
    void publishedReviewsArePublic() throws Exception {
        when(application.publicReviews(
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(Page.empty());

        mvc.perform(get("/api/v1/reviews"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"));
    }

    @Test
    void eligibleStaysRequireCustomerRole() throws Exception {
        mvc.perform(get("/api/v1/review-stays"))
                .andExpect(status().isUnauthorized());

        UUID identityId = UUID.randomUUID();
        when(application.eligibleStays(identityId)).thenReturn(List.of());
        mvc.perform(get("/api/v1/review-stays")
                        .with(jwt()
                                .jwt(token -> token
                                        .claim("uid", identityId.toString())
                                        .claim("roles", List.of("CUSTOMER")))
                                .authorities(new SimpleGrantedAuthority(
                                        "ROLE_CUSTOMER"))))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void receptionistCannotAccessModerationQueue() throws Exception {
        mvc.perform(get("/api/v1/admin/reviews")
                        .with(jwt()
                                .jwt(token -> token
                                        .claim("uid", UUID.randomUUID().toString())
                                        .claim("roles", List.of("RECEPTIONIST")))
                                .authorities(new SimpleGrantedAuthority(
                                        "ROLE_RECEPTIONIST"))))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(
                        "application/problem+json"));
    }

    @Test
    void errorDispatchIsNotMaskedBySecurity() throws Exception {
        mvc.perform(get("/error"))
                .andExpect(status().isInternalServerError());
    }
}
