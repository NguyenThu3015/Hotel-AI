package com.hotelai.backend.catalog;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CatalogController {
  private final CatalogService service;

  @GetMapping("/catalog/hotels")
  List<HotelView> hotels() {
    return service.hotels().stream().map(HotelView::of).toList();
  }

  @GetMapping("/catalog/hotels/{id}/room-types")
  List<TypeView> types(@PathVariable Long id) {
    return service.roomTypes(id).stream().map(TypeView::of).toList();
  }

  @GetMapping("/catalog/room-types/{id}/images")
  List<ImageView> images(@PathVariable Long id) {
    return service.images(id).stream().map(ImageView::of).toList();
  }

  @GetMapping("/catalog/amenities")
  List<Amenity> amenities() {
    return service.amenities();
  }

  @PostMapping("/admin/hotels")
  @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
  @ResponseStatus(HttpStatus.CREATED)
  HotelView hotel(@Valid @RequestBody HotelRequest r) {
    return HotelView.of(service.saveHotel(null, r.toInput()));
  }

  @PutMapping("/admin/hotels/{id}")
  @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
  HotelView hotel(@PathVariable Long id, @Valid @RequestBody HotelRequest r) {
    return HotelView.of(service.saveHotel(id, r.toInput()));
  }

  @PostMapping("/admin/amenities")
  @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
  Amenity amenity(@Valid @RequestBody AmenityRequest r) {
    return service.saveAmenity(
        null, new CatalogService.AmenityInput(r.name, r.description, r.iconUrl));
  }

  @PostMapping("/admin/room-types")
  @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
  TypeView type(@Valid @RequestBody TypeRequest r) {
    return TypeView.of(service.saveType(null, r.toInput()));
  }

  @PutMapping("/admin/room-types/{id}")
  @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
  TypeView type(@PathVariable Long id, @Valid @RequestBody TypeRequest r) {
    return TypeView.of(service.saveType(id, r.toInput()));
  }

  @GetMapping("/admin/hotels/{id}/rooms")
  @PreAuthorize("hasAnyRole('ADMIN','MANAGER','RECEPTIONIST','HOUSEKEEPER')")
  List<RoomView> rooms(@PathVariable Long id) {
    return service.rooms(id).stream().map(RoomView::of).toList();
  }

  @PostMapping("/admin/rooms")
  @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
  RoomView room(@Valid @RequestBody RoomRequest r) {
    return RoomView.of(service.saveRoom(null, r.toInput()));
  }

  @PutMapping("/admin/rooms/{id}")
  @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
  RoomView room(@PathVariable Long id, @Valid @RequestBody RoomRequest r) {
    return RoomView.of(service.saveRoom(id, r.toInput()));
  }

  @PutMapping("/admin/rates")
  @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
  RateView rate(@Valid @RequestBody RateRequest r) {
    return RateView.of(service.saveRate(r.toInput()));
  }

  @PostMapping("/admin/rates/bulk")
  @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
  List<RateView> bulkRates(@Valid @RequestBody BulkRateRequest request) {
    return service.applyRateRule(request.toInput()).stream().map(RateView::of).toList();
  }

  @PostMapping("/admin/room-types/{id}/images")
  @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
  ImageView image(@PathVariable Long id, @Valid @RequestBody ImageRequest r) {
    return ImageView.of(service.addImage(id, r.url, r.altText, r.displayOrder));
  }

  @DeleteMapping("/admin/room-images/{id}")
  @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  void deleteImage(@PathVariable Long id) {
    service.deleteImage(id);
  }

  record HotelRequest(
      @NotBlank String name,
      @NotBlank String address,
      String description,
      String phone,
      @Email String email,
      boolean active) {
    CatalogService.HotelInput toInput() {
      return new CatalogService.HotelInput(name, address, description, phone, email, active);
    }
  }

  record AmenityRequest(@NotBlank String name, String description, String iconUrl) {}

  record TypeRequest(
      @NotNull Long hotelId,
      @NotBlank String code,
      @NotBlank String name,
      String description,
      @Min(1) int maxAdults,
      @Min(0) int maxChildren,
      @NotNull @DecimalMin("0") BigDecimal basePrice,
      BigDecimal sizeM2,
      String bedType,
      boolean active,
      @NotNull List<Long> amenityIds) {
    CatalogService.RoomTypeInput toInput() {
      return new CatalogService.RoomTypeInput(
          hotelId,
          code,
          name,
          description,
          maxAdults,
          maxChildren,
          basePrice,
          sizeM2,
          bedType,
          active,
          amenityIds);
    }
  }

  record RoomRequest(
      @NotNull Long hotelId,
      @NotNull Long roomTypeId,
      @NotBlank String roomNumber,
      String floor,
      @Pattern(regexp = "AVAILABLE|RESERVED|OCCUPIED|WAITING_CLEANING|MAINTENANCE") String status) {
    CatalogService.RoomInput toInput() {
      return new CatalogService.RoomInput(hotelId, roomTypeId, roomNumber, floor, status);
    }
  }

  record RateRequest(
      @NotNull Long roomTypeId,
      @NotNull @FutureOrPresent LocalDate date,
      @NotNull @DecimalMin("0") BigDecimal price,
      @Min(0) Integer inventory,
      @Min(1) int minStay,
      boolean closed) {
    CatalogService.RateInput toInput() {
      return new CatalogService.RateInput(roomTypeId, date, price, inventory, minStay, closed);
    }
  }

  record ImageRequest(
      @NotBlank @Size(max = 500) String url, String altText, @Min(0) int displayOrder) {}

  record ImageView(Long id, Long roomTypeId, String url, String altText, int displayOrder) {
    static ImageView of(RoomImage image) {
      return new ImageView(
          image.getId(),
          image.getRoomType().getId(),
          image.getUrl(),
          image.getAltText(),
          image.getDisplayOrder());
    }
  }

  record BulkRateRequest(
      @NotNull Long roomTypeId,
      @NotNull @FutureOrPresent LocalDate from,
      @NotNull LocalDate to,
      @NotNull @DecimalMin("0.1") BigDecimal weekdayMultiplier,
      @NotNull @DecimalMin("0.1") BigDecimal weekendMultiplier,
      @Min(0) Integer inventory,
      @Min(1) int minStay,
      boolean closed) {
    CatalogService.BulkRateInput toInput() {
      return new CatalogService.BulkRateInput(
          roomTypeId, from, to, weekdayMultiplier, weekendMultiplier, inventory, minStay, closed);
    }
  }

  record HotelView(
      Long id,
      String name,
      String address,
      String description,
      String phone,
      String email,
      boolean active) {
    static HotelView of(Hotel h) {
      return new HotelView(
          h.getId(),
          h.getName(),
          h.getAddress(),
          h.getDescription(),
          h.getPhone(),
          h.getEmail(),
          h.isActive());
    }
  }

  record TypeView(
      Long id,
      Long hotelId,
      String code,
      String name,
      String description,
      int maxAdults,
      int maxChildren,
      BigDecimal basePrice,
      BigDecimal sizeM2,
      String bedType,
      List<String> amenities) {
    static TypeView of(RoomType t) {
      return new TypeView(
          t.getId(),
          t.getHotel().getId(),
          t.getCode(),
          t.getName(),
          t.getDescription(),
          t.getMaxAdults(),
          t.getMaxChildren(),
          t.getBasePrice(),
          t.getSizeM2(),
          t.getBedType(),
          t.getAmenities().stream().map(Amenity::getName).toList());
    }
  }

  record RoomView(Long id, Long roomTypeId, String roomNumber, String floor, String status) {
    static RoomView of(Room r) {
      return new RoomView(
          r.getId(), r.getRoomType().getId(), r.getRoomNumber(), r.getFloor(), r.getStatus());
    }
  }

  record RateView(
      Long id,
      Long roomTypeId,
      LocalDate date,
      BigDecimal price,
      Integer inventory,
      int minStay,
      boolean closed) {
    static RateView of(RoomRate r) {
      return new RateView(
          r.getId(),
          r.getRoomType().getId(),
          r.getRateDate(),
          r.getPrice(),
          r.getAvailableInventory(),
          r.getMinStay(),
          r.isClosed());
    }
  }
}
