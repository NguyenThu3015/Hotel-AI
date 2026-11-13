package com.hotelai.servicecatalog.api;

import com.hotelai.servicecatalog.domain.PreStayServiceOrderApplicationService;
import com.hotelai.servicecatalog.domain.PreStayServiceOrderApplicationService.CreateOrder;
import com.hotelai.servicecatalog.domain.PreStayServiceOrderApplicationService.OrderView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
public class PreStayServiceOrderController {
    private final PreStayServiceOrderApplicationService application;

    public PreStayServiceOrderController(PreStayServiceOrderApplicationService application) {
        this.application = application;
    }

    @GetMapping({"/api/v1/service-orders", "/api/v1/admin/service-orders"})
    public List<OrderView> list(@RequestParam(required = false) UUID reservationId,
                                @RequestParam(required = false) UUID customerId) {
        return application.list(reservationId, customerId);
    }

    @PostMapping("/api/v1/service-orders")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderView create(Authentication authentication, @Valid @RequestBody CreateOrderRequest request) {
        return application.create(request.command(), actor(authentication));
    }

    @PostMapping("/api/v1/admin/service-orders")
    @ResponseStatus(HttpStatus.CREATED)
    public OrderView createAdmin(Authentication authentication, @Valid @RequestBody CreateOrderRequest request) {
        return application.create(request.command(), actor(authentication));
    }

    @PostMapping("/api/v1/admin/service-orders/{id}/confirm")
    public OrderView confirm(@PathVariable UUID id) { return application.confirm(id); }

    @PostMapping("/api/v1/admin/service-orders/{id}/fulfill")
    public OrderView fulfill(@PathVariable UUID id) { return application.fulfill(id); }

    @PostMapping("/api/v1/service-orders/{id}/cancel")
    public OrderView cancel(@PathVariable UUID id, @RequestBody(required = false) CancelRequest request) {
        return application.cancel(id, request == null ? null : request.reason());
    }

    private static UUID actor(Authentication authentication) {
        try { return UUID.fromString(authentication.getName()); }
        catch (Exception ignored) { return UUID.randomUUID(); }
    }

    public record CreateOrderRequest(@NotNull UUID reservationId, @NotNull UUID customerId,
                                     @NotNull UUID serviceId,
                                     @NotNull @DecimalMin("0.001") @DecimalMax("999.999") BigDecimal quantity,
                                     @NotNull Instant scheduledAt, String note) {
        CreateOrder command() { return new CreateOrder(reservationId, customerId, serviceId, quantity, scheduledAt, note); }
    }
    public record CancelRequest(String reason) {}
}
