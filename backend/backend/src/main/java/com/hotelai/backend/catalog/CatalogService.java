package com.hotelai.backend.catalog;

import com.hotelai.backend.audit.AuditService;
import com.hotelai.backend.common.ApiException;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CatalogService {
  private final HotelRepository hotels;
  private final RoomTypeRepository types;
  private final RoomRepository rooms;
  private final AmenityRepository amenities;
  private final RoomRateRepository rates;
  private final RoomImageRepository images;
  private final AuditService audit;

  public List<Hotel> hotels() {
    return hotels.findByActiveTrue();
  }

  public List<RoomType> roomTypes(Long hotel) {
    return types.findByHotelIdAndActiveTrue(hotel);
  }

  public List<Amenity> amenities() {
    return amenities.findAll();
  }

  public List<Room> rooms(Long hotel) {
    return rooms.findByHotelId(hotel);
  }

  public List<RoomRate> rates(Long typeId, LocalDate from, LocalDate to) {
    return rates.findByRoomTypeIdAndRateDateGreaterThanEqualAndRateDateLessThan(typeId, from, to);
  }

  public List<RoomImage> images(Long typeId) {
    return images.findByRoomTypeIdOrderByDisplayOrder(typeId);
  }

  @Transactional
  public RoomImage addImage(Long typeId, String url, String altText, int displayOrder) {
    RoomImage image = images.save(new RoomImage(type(typeId), url, altText, displayOrder));
    audit.record("ADD_ROOM_IMAGE", "ROOM_TYPE", typeId, url);
    return image;
  }

  @Transactional
  public void deleteImage(Long id) {
    RoomImage image = images.findById(id).orElseThrow(notFound("ảnh phòng"));
    audit.record("DELETE_ROOM_IMAGE", "ROOM_TYPE", image.getRoomType().getId(), image.getUrl());
    images.delete(image);
  }

  public int sellableRoomCount(Long typeId) {
    return (int) rooms.countByRoomTypeIdAndStatusNot(typeId, "MAINTENANCE");
  }

  public RoomType lockedType(Long id) {
    return types.findWithLockById(id).orElseThrow(notFound("loại phòng"));
  }

  @Transactional
  public Hotel saveHotel(Long id, HotelInput r) {
    Hotel h =
        id == null ? new Hotel(r.name, r.address, r.description, r.phone, r.email) : hotel(id);
    if (id != null) h.update(r.name, r.address, r.description, r.phone, r.email, r.active);
    h = hotels.save(h);
    audit.record(id == null ? "CREATE_HOTEL" : "UPDATE_HOTEL", "HOTEL", h.getId(), h.getName());
    return h;
  }

  @Transactional
  public Amenity saveAmenity(Long id, AmenityInput r) {
    Amenity a =
        id == null
            ? new Amenity(r.name, r.description, r.iconUrl)
            : amenities.findById(id).orElseThrow(notFound("tiện nghi"));
    if (id != null) a.update(r.name, r.description, r.iconUrl);
    return amenities.save(a);
  }

  @Transactional
  public RoomType saveType(Long id, RoomTypeInput r) {
    Set<Amenity> am = new HashSet<>(amenities.findAllById(r.amenityIds));
    if (am.size() != r.amenityIds.size())
      throw new ApiException(HttpStatus.BAD_REQUEST, "Có tiện nghi không tồn tại");
    RoomType t =
        id == null
            ? new RoomType(
                hotel(r.hotelId),
                r.code,
                r.name,
                r.description,
                r.maxAdults,
                r.maxChildren,
                r.basePrice,
                r.sizeM2,
                r.bedType,
                am)
            : type(id);
    if (id != null)
      t.update(
          r.name,
          r.description,
          r.maxAdults,
          r.maxChildren,
          r.basePrice,
          r.sizeM2,
          r.bedType,
          r.active,
          am);
    t = types.save(t);
    audit.record(
        id == null ? "CREATE_ROOM_TYPE" : "UPDATE_ROOM_TYPE", "ROOM_TYPE", t.getId(), t.getCode());
    return t;
  }

  @Transactional
  public Room saveRoom(Long id, RoomInput r) {
    RoomType t = type(r.roomTypeId);
    if (!t.getHotel().getId().equals(r.hotelId))
      throw new ApiException(HttpStatus.BAD_REQUEST, "Loại phòng không thuộc khách sạn");
    Room room =
        id == null
            ? new Room(hotel(r.hotelId), t, r.roomNumber, r.floor)
            : rooms.findById(id).orElseThrow(notFound("phòng"));
    if (id != null) room.update(t, r.roomNumber, r.floor, r.status);
    room = rooms.save(room);
    audit.record(
        id == null ? "CREATE_ROOM" : "UPDATE_ROOM", "ROOM", room.getId(), room.getRoomNumber());
    return room;
  }

  @Transactional
  public RoomRate saveRate(RateInput r) {
    RoomType t = type(r.roomTypeId);
    RoomRate rate =
        rates
            .findByRoomTypeIdAndRateDate(r.roomTypeId, r.date)
            .orElseGet(() -> new RoomRate(t, r.date, r.price, r.inventory, r.minStay, r.closed));
    rate.update(r.price, r.inventory, r.minStay, r.closed);
    rate = rates.save(rate);
    audit.record("UPSERT_RATE", "ROOM_TYPE", r.roomTypeId, r.date + ":" + r.price);
    return rate;
  }

  @Transactional
  public List<RoomRate> applyRateRule(BulkRateInput rule) {
    if (rule.to.isBefore(rule.from))
      throw new ApiException(HttpStatus.BAD_REQUEST, "Khoảng ngày áp dụng giá không hợp lệ");
    RoomType roomType = type(rule.roomTypeId);
    List<RoomRate> result = new java.util.ArrayList<>();
    for (LocalDate day = rule.from; !day.isAfter(rule.to); day = day.plusDays(1)) {
      boolean weekend =
          day.getDayOfWeek() == java.time.DayOfWeek.SATURDAY
              || day.getDayOfWeek() == java.time.DayOfWeek.SUNDAY;
      BigDecimal multiplier = weekend ? rule.weekendMultiplier : rule.weekdayMultiplier;
      BigDecimal price =
          roomType.getBasePrice().multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
      result.add(
          saveRate(
              new RateInput(
                  rule.roomTypeId, day, price, rule.inventory, rule.minStay, rule.closed)));
    }
    audit.record("APPLY_RATE_RULE", "ROOM_TYPE", rule.roomTypeId, rule.from + ".." + rule.to);
    return result;
  }

  public Hotel hotel(Long id) {
    return hotels.findById(id).orElseThrow(notFound("khách sạn"));
  }

  public RoomType type(Long id) {
    return types.findById(id).orElseThrow(notFound("loại phòng"));
  }

  public Room lockedRoom(Long id) {
    return rooms.findWithLockById(id).orElseThrow(notFound("phòng"));
  }

  private java.util.function.Supplier<ApiException> notFound(String what) {
    return () -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy " + what);
  }

  public record HotelInput(
      String name,
      String address,
      String description,
      String phone,
      String email,
      boolean active) {}

  public record AmenityInput(String name, String description, String iconUrl) {}

  public record RoomTypeInput(
      Long hotelId,
      String code,
      String name,
      String description,
      int maxAdults,
      int maxChildren,
      BigDecimal basePrice,
      BigDecimal sizeM2,
      String bedType,
      boolean active,
      List<Long> amenityIds) {}

  public record RoomInput(
      Long hotelId, Long roomTypeId, String roomNumber, String floor, String status) {}

  public record RateInput(
      Long roomTypeId,
      LocalDate date,
      BigDecimal price,
      Integer inventory,
      int minStay,
      boolean closed) {}

  public record BulkRateInput(
      Long roomTypeId,
      LocalDate from,
      LocalDate to,
      BigDecimal weekdayMultiplier,
      BigDecimal weekendMultiplier,
      Integer inventory,
      int minStay,
      boolean closed) {}
}
