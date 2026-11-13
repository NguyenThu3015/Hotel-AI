package com.hotelai.customer.api;

import com.hotelai.customer.domain.CustomerApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/customers")
public class InternalCustomerController {
    private final CustomerApplicationService service;

    public InternalCustomerController(CustomerApplicationService service) {
        this.service = service;
    }

    @PostMapping("/identity-created")
    @ResponseStatus(HttpStatus.CREATED)
    CustomerDtos.ProfileResponse identityCreated(@Valid @RequestBody CustomerDtos.IdentityCreatedRequest request) {
        return service.createFromIdentity(request);
    }
}
