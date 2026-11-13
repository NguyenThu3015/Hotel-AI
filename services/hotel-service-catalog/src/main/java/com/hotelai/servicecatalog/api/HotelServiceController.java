package com.hotelai.servicecatalog.api;

import com.hotelai.servicecatalog.api.CatalogDtos.CatalogRequest;
import com.hotelai.servicecatalog.api.CatalogDtos.CatalogView;
import com.hotelai.servicecatalog.domain.CatalogApplicationService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HotelServiceController {
    private final CatalogApplicationService application;

    public HotelServiceController(CatalogApplicationService application) {
        this.application = application;
    }

    @GetMapping("/api/v1/hotel-services")
    List<CatalogView> publicCatalog() {
        return application.list(false).stream()
                .map(value -> CatalogView.from(value, false))
                .toList();
    }

    @GetMapping("/api/v1/hotel-services/{id}")
    CatalogView publicDetails(@PathVariable UUID id) {
        return CatalogView.from(application.get(id, false), false);
    }

    @GetMapping("/api/v1/admin/hotel-services")
    List<CatalogView> adminCatalog() {
        return application.list(true).stream()
                .map(value -> CatalogView.from(value, true))
                .toList();
    }

    @GetMapping("/api/v1/admin/hotel-services/{id}")
    CatalogView adminDetails(@PathVariable UUID id) {
        return CatalogView.from(application.get(id, true), true);
    }

    @PostMapping("/api/v1/admin/hotel-services")
    @ResponseStatus(HttpStatus.CREATED)
    CatalogView create(
            Authentication authentication, @Valid @RequestBody CatalogRequest request) {
        return CatalogView.from(
                application.create(actor(authentication), request.command()), true);
    }

    @PutMapping("/api/v1/admin/hotel-services/{id}")
    CatalogView update(
            Authentication authentication,
            @PathVariable UUID id,
            @Valid @RequestBody CatalogRequest request) {
        return CatalogView.from(
                application.update(actor(authentication), id, request.command()), true);
    }

    static UUID actor(Authentication authentication) {
        try {
            return UUID.fromString(authentication.getName());
        } catch (RuntimeException error) {
            throw new IllegalArgumentException("JWT uid must be a UUID", error);
        }
    }
}
