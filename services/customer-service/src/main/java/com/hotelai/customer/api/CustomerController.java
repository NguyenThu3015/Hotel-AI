package com.hotelai.customer.api;

import com.hotelai.customer.domain.CustomerApplicationService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/api/v1/customers/me", "/api/v1/me", "/api/v1/profile"})
public class CustomerController {
    private final CustomerApplicationService service;

    public CustomerController(CustomerApplicationService service) {
        this.service = service;
    }

    @GetMapping
    CustomerDtos.ProfileResponse get(@AuthenticationPrincipal Jwt jwt) {
        return service.getMine(identityId(jwt));
    }

    @PutMapping
    CustomerDtos.ProfileResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CustomerDtos.UpdateProfileRequest request) {
        return service.updateMine(identityId(jwt), request);
    }

    private static UUID identityId(Jwt jwt) {
        String value = jwt.getClaimAsString("uid");
        return UUID.fromString(value == null ? jwt.getSubject() : value);
    }
}
