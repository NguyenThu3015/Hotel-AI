package com.hotelai.backend.booking;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.security.Principal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class BookingController {
  private final AvailabilityService availability;
  private final BookingService bookings;
  private final PromotionRepository promotions;
  private final com.hotelai.backend.audit.AuditService audit;

  @GetMapping("/availability/search")
  List<AvailabilityService.Option> search(
      @RequestParam Long hotelId,
      @RequestParam LocalDate checkIn,
      @RequestParam LocalDate checkOut,
      @RequestParam @Min(1) int adults,
      @RequestParam(defaultValue = "0") @Min(0) int children,
      @RequestParam(defaultValue = "1") @Min(1) int rooms,
      @RequestParam(required = false) BigDecimal minPrice,
      @RequestParam(required = false) BigDecimal maxPrice,
      @RequestParam(defaultValue = "") List<Long> amenityIds) {
    return availability.search(
        hotelId, checkIn, checkOut, adults, children, rooms, minPrice, maxPrice, amenityIds);
  }

  @PostMapping("/bookings")
  @ResponseStatus(HttpStatus.CREATED)
  BookingService.View create(Principal p, @Valid @RequestBody CreateRequest r) {
    var guests =
        r.guests == null
            ? List.<BookingService.GuestInput>of()
            : r.guests.stream()
                .map(
                    g ->
                        new BookingService.GuestInput(
                            g.fullName, g.documentType, g.documentNumber, g.nationality, g.primary))
                .toList();
    return bookings.view(
        bookings.create(
            p,
            new BookingService.Create(
                r.hotelId,
                r.roomTypeId,
                r.checkIn,
                r.checkOut,
                r.adults,
                r.children,
                r.quantity,
                r.contactName,
                r.contactEmail,
                r.contactPhone,
                r.specialRequests,
                r.promotionCode,
                r.source,
                guests)));
  }

  @GetMapping("/bookings")
  List<BookingService.View> mine(Principal p) {
    return bookings.mine(p);
  }

  @GetMapping("/bookings/{code}")
  BookingService.View get(@PathVariable String code, Principal p) {
    return bookings.get(code, p);
  }

  @PatchMapping("/bookings/{id}/dates")
  BookingService.View dates(@PathVariable Long id, Principal p, @Valid @RequestBody Dates r) {
    return bookings.view(bookings.reschedule(id, p, r.checkIn, r.checkOut));
  }

  @PostMapping("/bookings/{id}/cancel")
  BookingService.Cancellation cancel(
      @PathVariable Long id, Principal p, @Valid @RequestBody Cancel r) {
    return bookings.cancel(id, p, r.reason);
  }

  @GetMapping("/bookings/{id}/cancellation-quote")
  BookingService.CancellationQuote cancellationQuote(@PathVariable Long id, Principal principal) {
    return bookings.cancellationQuote(id, principal);
  }

  @PostMapping("/admin/promotions")
  @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
  Promotion promo(@Valid @RequestBody Promo r) {
    Promotion p =
        promotions.save(
            new Promotion(
                r.code,
                r.name,
                r.discountType,
                r.discountValue,
                r.maxDiscount,
                r.minBookingAmount,
                r.validFrom,
                r.validTo,
                r.usageLimit));
    audit.record("CREATE_PROMOTION", "PROMOTION", p.getId(), p.getCode());
    return p;
  }

  @GetMapping("/admin/bookings")
  @PreAuthorize("hasAnyRole('ADMIN','MANAGER','RECEPTIONIST')")
  List<BookingService.View> adminBookings(
      @RequestParam(required = false) Long hotelId, @RequestParam(required = false) String status) {
    return bookings.adminList(hotelId, status);
  }

  @GetMapping("/admin/promotions")
  @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
  List<Promotion> promotions() {
    return promotions.findAll();
  }

  @GetMapping("/admin/refunds")
  @PreAuthorize("hasAnyRole('ADMIN','MANAGER','RECEPTIONIST')")
  List<BookingService.RefundView> refunds() {
    return bookings.refundRequests();
  }

  record CreateRequest(
      @NotNull Long hotelId,
      @NotNull Long roomTypeId,
      @NotNull @FutureOrPresent LocalDate checkIn,
      @NotNull LocalDate checkOut,
      @Min(1) int adults,
      @Min(0) int children,
      @Min(1) int quantity,
      @NotBlank String contactName,
      @Email @NotBlank String contactEmail,
      String contactPhone,
      String specialRequests,
      String promotionCode,
      @Pattern(regexp = "WEB|AI|RECEPTION") String source,
      List<GuestRequest> guests) {}

  record GuestRequest(
      @NotBlank String fullName,
      String documentType,
      String documentNumber,
      String nationality,
      boolean primary) {}

  record Dates(@NotNull @FutureOrPresent LocalDate checkIn, @NotNull LocalDate checkOut) {}

  record Cancel(@NotBlank @Size(max = 500) String reason) {}

  record Promo(
      @NotBlank String code,
      @NotBlank String name,
      @Pattern(regexp = "PERCENT|FIXED") String discountType,
      @Positive BigDecimal discountValue,
      BigDecimal maxDiscount,
      BigDecimal minBookingAmount,
      @NotNull Instant validFrom,
      @NotNull Instant validTo,
      Integer usageLimit) {}
}
