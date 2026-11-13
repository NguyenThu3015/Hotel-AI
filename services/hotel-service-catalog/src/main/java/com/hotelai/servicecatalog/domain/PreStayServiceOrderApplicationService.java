package com.hotelai.servicecatalog.domain;

import com.hotelai.servicecatalog.common.ApiException;
import com.hotelai.servicecatalog.config.CatalogProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PreStayServiceOrderApplicationService {
    private final HotelServiceItemRepository services;
    private final ServicePriceVersionRepository prices;
    private final PreStayServiceOrderRepository orders;
    private final CatalogProperties properties;
    private final Clock clock;

    public PreStayServiceOrderApplicationService(
            HotelServiceItemRepository services,
            ServicePriceVersionRepository prices,
            PreStayServiceOrderRepository orders,
            CatalogProperties properties,
            Clock clock) {
        this.services = services;
        this.prices = prices;
        this.orders = orders;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public OrderView create(CreateOrder command, UUID actorId) {
        if (command.scheduledAt() == null || command.scheduledAt().isBefore(clock.instant())) {
            throw ApiException.validation("scheduledAt must be in the future");
        }
        HotelServiceItem service = services.findById(command.serviceId())
                .orElseThrow(() -> ApiException.notFound("Hotel service not found"));
        ZoneId zone = ZoneId.of(properties.timeZone());
        if (!service.isAvailableAt(command.scheduledAt(), zone)) {
            throw ApiException.conflict("service-unavailable", "Service is inactive or unavailable at the requested time");
        }
        ServicePriceVersion price = prices.findByServiceIdAndValidFromLessThanEqualOrderByValidFromDesc(
                        service.getId(), command.scheduledAt()).stream()
                .filter(value -> value.getValidTo() == null || command.scheduledAt().isBefore(value.getValidTo()))
                .findFirst()
                .orElseThrow(() -> ApiException.conflict("service-price-unavailable", "No effective service price exists"));
        return view(orders.save(new PreStayServiceOrder(
                command.reservationId(), command.customerId(), service, price,
                command.quantity(), command.scheduledAt(), command.note(), actorId)));
    }

    @Transactional(readOnly = true)
    public List<OrderView> list(UUID reservationId, UUID customerId) {
        if (reservationId != null) return orders.findByReservationIdOrderByScheduledAtAsc(reservationId).stream().map(this::view).toList();
        if (customerId != null) return orders.findByCustomerIdOrderByScheduledAtDesc(customerId).stream().map(this::view).toList();
        return orders.findTop100ByOrderByCreatedAtDesc().stream().map(this::view).toList();
    }

    @Transactional
    public OrderView confirm(UUID id) {
        PreStayServiceOrder order = required(id);
        order.confirm();
        return view(order);
    }

    @Transactional
    public OrderView fulfill(UUID id) {
        PreStayServiceOrder order = required(id);
        order.fulfill();
        return view(order);
    }

    @Transactional
    public OrderView cancel(UUID id, String reason) {
        PreStayServiceOrder order = required(id);
        order.cancel(reason);
        return view(order);
    }

    private PreStayServiceOrder required(UUID id) {
        return orders.findById(id).orElseThrow(() -> ApiException.notFound("Service order not found"));
    }

    private OrderView view(PreStayServiceOrder value) {
        return new OrderView(value.getId(), value.getReservationId(), value.getCustomerId(), value.getServiceId(),
                value.getServiceCode(), value.getServiceName(), value.getQuantity(), value.getUnitPrice(),
                value.getTaxRate(), value.grossAmount(), value.getCurrency(), value.getScheduledAt(),
                value.getStatus(), value.getNote(), value.getVersion(), value.getCreatedAt(), value.getUpdatedAt());
    }

    public record CreateOrder(UUID reservationId, UUID customerId, UUID serviceId,
                               java.math.BigDecimal quantity, Instant scheduledAt, String note) {}

    public record OrderView(UUID id, UUID reservationId, UUID customerId, UUID serviceId,
                            String serviceCode, String serviceName, java.math.BigDecimal quantity,
                            java.math.BigDecimal unitPrice, java.math.BigDecimal taxRate,
                            java.math.BigDecimal grossAmount, String currency, Instant scheduledAt,
                            PreStayServiceOrderStatus status, String note, long version,
                            Instant createdAt, Instant updatedAt) {}
}
