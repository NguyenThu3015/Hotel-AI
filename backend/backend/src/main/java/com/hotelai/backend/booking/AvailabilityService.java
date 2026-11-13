package com.hotelai.backend.booking;

import com.hotelai.backend.catalog.*;
import com.hotelai.backend.common.ApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AvailabilityService {
  private final CatalogService catalog;
  private final BookingRoomRepository booked;

  @Value("${hotel.pricing.tax-rate}")
  private BigDecimal taxRate;

  @Value("${hotel.pricing.service-fee-rate}")
  private BigDecimal feeRate;

  @Transactional(readOnly = true)
  public List<Option> search(
      Long hotelId,
      LocalDate in,
      LocalDate out,
      int adults,
      int children,
      int quantity,
      BigDecimal min,
      BigDecimal max,
      List<Long> amenityIds) {
    validate(in, out, adults, quantity);
    List<Option> result = new ArrayList<>();
    for (RoomType t : catalog.roomTypes(hotelId)) {
      if (t.getMaxAdults() * quantity < adults
          || t.getMaxChildren() * quantity < children
          || !t.getAmenities().stream()
              .map(Amenity::getId)
              .collect(java.util.stream.Collectors.toSet())
              .containsAll(amenityIds)) continue;
      Quote q = quote(t, in, out, quantity);
      if (q.available() < quantity
          || (min != null && q.total().compareTo(min) < 0)
          || (max != null && q.total().compareTo(max) > 0)) continue;
      result.add(
          new Option(
              t.getId(),
              t.getCode(),
              t.getName(),
              t.getDescription(),
              t.getMaxAdults(),
              t.getMaxChildren(),
              q.available(),
              q.roomSubtotal(),
              q.tax(),
              q.serviceFee(),
              q.total(),
              catalog.images(t.getId()).stream()
                  .findFirst()
                  .map(com.hotelai.backend.catalog.RoomImage::getUrl)
                  .orElse(null),
              t.getAmenities().stream().map(Amenity::getName).toList(),
              "Hủy miễn phí khi booking còn chờ thanh toán; booking đã xác nhận áp dụng chính sách theo thời điểm hủy"));
    }
    return result;
  }

  public Quote quote(RoomType t, LocalDate in, LocalDate out, int quantity) {
    int available = Integer.MAX_VALUE;
    BigDecimal oneRoom = BigDecimal.ZERO;
    var configured =
        catalog.rates(t.getId(), in, out).stream()
            .collect(java.util.stream.Collectors.toMap(RoomRate::getRateDate, x -> x));
    for (LocalDate d = in; d.isBefore(out); d = d.plusDays(1)) {
      RoomRate rr = configured.get(d);
      if (rr != null && rr.isClosed())
        return new Quote(0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
      int physical = catalog.sellableRoomCount(t.getId());
      int cap =
          rr != null && rr.getAvailableInventory() != null
              ? Math.min(physical, rr.getAvailableInventory())
              : physical;
      available = Math.min(available, cap - (int) booked.occupied(t.getId(), d, Instant.now()));
      oneRoom = oneRoom.add(rr == null ? t.getBasePrice() : rr.getPrice());
    }
    BigDecimal subtotal = oneRoom.multiply(BigDecimal.valueOf(quantity));
    BigDecimal tax = subtotal.multiply(taxRate).setScale(2, RoundingMode.HALF_UP);
    BigDecimal fee = subtotal.multiply(feeRate).setScale(2, RoundingMode.HALF_UP);
    return new Quote(Math.max(0, available), subtotal, tax, fee, subtotal.add(tax).add(fee));
  }

  void validate(LocalDate in, LocalDate out, int adults, int quantity) {
    if (in == null || out == null || !out.isAfter(in) || in.isBefore(LocalDate.now()))
      throw new ApiException(HttpStatus.BAD_REQUEST, "Khoảng ngày nhận/trả không hợp lệ");
    if (adults < 1 || quantity < 1)
      throw new ApiException(HttpStatus.BAD_REQUEST, "Số khách và số phòng phải lớn hơn 0");
  }

  public record Quote(
      int available,
      BigDecimal roomSubtotal,
      BigDecimal tax,
      BigDecimal serviceFee,
      BigDecimal total) {}

  public record Option(
      Long roomTypeId,
      String code,
      String name,
      String description,
      int maxAdults,
      int maxChildren,
      int availableRooms,
      BigDecimal roomSubtotal,
      BigDecimal taxAmount,
      BigDecimal serviceFee,
      BigDecimal totalAmount,
      String imageUrl,
      List<String> amenities,
      String cancellationPolicy) {}
}
