package com.hotelai.backend.booking;

import com.hotelai.backend.account.User;
import com.hotelai.backend.account.UserRepository;
import com.hotelai.backend.audit.AuditService;
import com.hotelai.backend.catalog.CatalogService;
import com.hotelai.backend.catalog.RoomType;
import com.hotelai.backend.common.ApiException;
import com.hotelai.backend.notification.Notification;
import com.hotelai.backend.notification.NotificationRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BookingService {
  private final BookingRepository bookings;
  private final UserRepository users;
  private final CatalogService catalog;
  private final AvailabilityService availability;
  private final PromotionRepository promotions;
  private final AuditService audit;
  private final NotificationRepository notifications;
  private final RefundRequestRepository refundRequests;
  private final EntityManager em;

  @Value("${hotel.booking.hold-minutes}")
  private long holdMinutes;

  @Value("${hotel.pricing.tax-rate}")
  private BigDecimal taxRate;

  @Value("${hotel.pricing.service-fee-rate}")
  private BigDecimal feeRate;

  @Transactional
  public Booking create(Principal principal, Create r) {
    User user = users.findByEmailIgnoreCase(principal.getName()).orElseThrow();
    availability.validate(r.checkIn, r.checkOut, r.adults, r.quantity);
    RoomType type = catalog.lockedType(r.roomTypeId);
    if (!type.getHotel().getId().equals(r.hotelId)) throw bad("Loại phòng không thuộc khách sạn");
    if (type.getMaxAdults() * r.quantity < r.adults
        || type.getMaxChildren() * r.quantity < r.children) throw bad("Sức chứa phòng không đủ");
    AvailabilityService.Quote q = availability.quote(type, r.checkIn, r.checkOut, r.quantity);
    if (q.available() < r.quantity)
      throw new ApiException(HttpStatus.CONFLICT, "Không còn đủ phòng cho toàn bộ khoảng ngày");
    Promotion promo = null;
    BigDecimal discount = BigDecimal.ZERO;
    if (r.promotionCode != null && !r.promotionCode.isBlank()) {
      promo =
          promotions
              .lockByCode(r.promotionCode)
              .orElseThrow(() -> bad("Mã khuyến mãi không tồn tại"));
      if (!promo.valid(q.roomSubtotal()))
        throw bad("Mã khuyến mãi không còn hiệu lực hoặc chưa đủ điều kiện");
      discount = promo.discount(q.roomSubtotal());
      promo.use();
    }
    BigDecimal taxable = q.roomSubtotal().subtract(discount);
    BigDecimal tax = taxable.multiply(taxRate).setScale(2, RoundingMode.HALF_UP);
    BigDecimal fee = taxable.multiply(feeRate).setScale(2, RoundingMode.HALF_UP);
    BigDecimal total = taxable.add(tax).add(fee);
    String code =
        "BK" + em.createNativeQuery("SELECT nextval('booking_code_seq')").getSingleResult();
    Booking b =
        new Booking(
            code,
            user,
            catalog.hotel(r.hotelId),
            r.checkIn,
            r.checkOut,
            r.adults,
            r.children,
            r.contactName,
            r.contactEmail,
            r.contactPhone,
            r.specialRequests,
            promo,
            q.roomSubtotal(),
            discount,
            tax,
            fee,
            total,
            Instant.now().plus(Duration.ofMinutes(holdMinutes)));
    b.source(r.source);
    BigDecimal avg =
        q.roomSubtotal()
            .divide(
                BigDecimal.valueOf(r.quantity * ChronoUnit.DAYS.between(r.checkIn, r.checkOut)),
                2,
                RoundingMode.HALF_UP);
    for (int i = 0; i < r.quantity; i++)
      b.addRoom(
          new BookingRoom(b, type, avg, (r.adults + r.children + r.quantity - 1) / r.quantity));
    if (r.guests != null)
      for (GuestInput g : r.guests)
        b.addGuest(
            new Guest(b, g.fullName, g.documentType, g.documentNumber, g.nationality, g.primary));
    b = bookings.save(b);
    notifications.save(
        new Notification(
            user,
            "BOOKING_CREATED",
            "Đã giữ phòng",
            "Booking " + b.getBookingCode() + " được giữ trong " + holdMinutes + " phút"));
    audit.record("CREATE_BOOKING", "BOOKING", b.getId(), b.getBookingCode());
    return b;
  }

  @Transactional
  public Booking reschedule(Long id, Principal p, LocalDate in, LocalDate out) {
    Booking b = owned(id, p);
    if (!List.of("PENDING_PAYMENT", "CONFIRMED").contains(b.getStatus()))
      throw bad("Trạng thái booking không cho phép đổi ngày");
    RoomType type = catalog.lockedType(b.getRooms().getFirst().getRoomType().getId());
    availability.validate(in, out, b.getAdults(), b.getRooms().size());
    AvailabilityService.Quote q = availability.quote(type, in, out, b.getRooms().size());
    int self = b.getRooms().size();
    if (q.available() + self < b.getRooms().size())
      throw new ApiException(HttpStatus.CONFLICT, "Không đủ phòng ở khoảng ngày mới");
    BigDecimal discount =
        b.getPromotion() == null ? BigDecimal.ZERO : b.getPromotion().discount(q.roomSubtotal());
    BigDecimal base = q.roomSubtotal().subtract(discount);
    BigDecimal tax = base.multiply(taxRate).setScale(2, RoundingMode.HALF_UP);
    BigDecimal fee = base.multiply(feeRate).setScale(2, RoundingMode.HALF_UP);
    b.reschedule(
        in,
        out,
        q.roomSubtotal(),
        discount,
        tax,
        fee,
        base.add(tax).add(fee),
        Instant.now().plus(Duration.ofMinutes(holdMinutes)));
    notifications.save(
        new Notification(
            b.getUser(),
            "BOOKING_RESCHEDULED",
            "Đã đổi ngày lưu trú",
            "Booking " + b.getBookingCode() + " chuyển sang " + in + " - " + out));
    audit.record("RESCHEDULE_BOOKING", "BOOKING", id, in + "/" + out);
    return b;
  }

  @Transactional
  public Cancellation cancel(Long id, Principal p, String reason) {
    Booking b = owned(id, p);
    if (!List.of("PENDING_PAYMENT", "CONFIRMED").contains(b.getStatus()))
      throw bad("Booking không thể hủy ở trạng thái hiện tại");
    BigDecimal fee = cancellationFee(b);
    BigDecimal refund = b.getPaidAmount().subtract(fee).max(BigDecimal.ZERO);
    b.cancel(reason);
    if (refund.signum() > 0) refundRequests.save(new RefundRequest(b, refund, reason));
    notifications.save(
        new Notification(
            b.getUser(),
            "BOOKING_CANCELLED",
            "Booking đã hủy",
            "Booking " + b.getBookingCode() + " đã hủy; dự kiến hoàn " + refund));
    audit.record("CANCEL_BOOKING", "BOOKING", id, "fee=" + fee);
    return new Cancellation(view(b), fee, refund);
  }

  @Transactional
  public CancellationQuote cancellationQuote(Long id, Principal principal) {
    Booking booking = owned(id, principal);
    if (!List.of("PENDING_PAYMENT", "CONFIRMED").contains(booking.getStatus()))
      throw bad("Booking không thể hủy ở trạng thái hiện tại");
    BigDecimal fee = cancellationFee(booking);
    return new CancellationQuote(
        fee,
        booking.getPaidAmount().subtract(fee).max(BigDecimal.ZERO),
        fee.signum() == 0
            ? "Miễn phí hủy theo trạng thái và thời điểm hiện tại"
            : "Hủy sát ngày nhận phòng: phí 50% tổng booking");
  }

  private BigDecimal cancellationFee(Booking booking) {
    return "CONFIRMED".equals(booking.getStatus())
            && LocalDate.now().plusDays(1).isAfter(booking.getCheckInDate())
        ? booking.getTotalAmount().multiply(new BigDecimal("0.50"))
        : BigDecimal.ZERO;
  }

  @Transactional
  public RefundRequest refundRequest(Long id) {
    return refundRequests
        .findById(id)
        .orElseThrow(
            () -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu hoàn tiền"));
  }

  @Transactional(readOnly = true)
  public List<RefundView> refundRequests() {
    return refundRequests.findAllByOrderByCreatedAtDesc().stream().map(RefundView::of).toList();
  }

  @Transactional(readOnly = true)
  public List<View> mine(Principal p) {
    User u = users.findByEmailIgnoreCase(p.getName()).orElseThrow();
    return bookings.findByUserIdOrderByCreatedAtDesc(u.getId()).stream().map(this::view).toList();
  }

  @Transactional(readOnly = true)
  public View get(String code, Principal p) {
    Booking b =
        bookings
            .findByBookingCode(code)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy booking"));
    User u = users.findByEmailIgnoreCase(p.getName()).orElseThrow();
    if (!b.getUser().getId().equals(u.getId()) && !staff(u))
      throw new ApiException(HttpStatus.FORBIDDEN, "Không có quyền xem booking");
    return view(b);
  }

  @Transactional(readOnly = true)
  public List<View> adminList(Long hotelId, String status) {
    return bookings.findAllByOrderByCreatedAtDesc().stream()
        .filter(b -> hotelId == null || b.getHotel().getId().equals(hotelId))
        .filter(b -> status == null || status.isBlank() || b.getStatus().equals(status))
        .map(this::view)
        .toList();
  }

  @Scheduled(fixedDelay = 60000)
  @Transactional
  public void expireHolds() {
    for (Booking b :
        bookings.findByStatusAndHoldExpiresAtBefore("PENDING_PAYMENT", Instant.now())) {
      b.expire();
      audit.record("EXPIRE_HOLD", "BOOKING", b.getId(), null);
    }
  }

  public Booking lock(Long id) {
    return bookings
        .lockById(id)
        .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy booking"));
  }

  public Booking owned(Long id, Principal p) {
    Booking b = lock(id);
    User u = users.findByEmailIgnoreCase(p.getName()).orElseThrow();
    if (!b.getUser().getId().equals(u.getId()) && !staff(u))
      throw new ApiException(HttpStatus.FORBIDDEN, "Không có quyền thao tác booking");
    return b;
  }

  private boolean staff(User u) {
    return u.getRoles().stream()
        .anyMatch(x -> List.of("ADMIN", "MANAGER", "RECEPTIONIST").contains(x.getName()));
  }

  private ApiException bad(String m) {
    return new ApiException(HttpStatus.BAD_REQUEST, m);
  }

  public View view(Booking b) {
    return new View(
        b.getId(),
        b.getBookingCode(),
        b.getStatus(),
        b.getHotel().getId(),
        b.getCheckInDate(),
        b.getCheckOutDate(),
        b.getAdults(),
        b.getChildren(),
        b.getRooms().stream()
            .map(
                x ->
                    new RoomLine(
                        x.getId(),
                        x.getRoomType().getId(),
                        x.getRoomType().getName(),
                        x.getRoom() == null ? null : x.getRoom().getRoomNumber(),
                        x.getNightlyPrice()))
            .toList(),
        b.getRoomSubtotal(),
        b.getServiceSubtotal(),
        b.getDiscountAmount(),
        b.getTaxAmount(),
        b.getServiceFee(),
        b.getTotalAmount(),
        b.getPaidAmount(),
        b.getHoldExpiresAt());
  }

  public record GuestInput(
      String fullName,
      String documentType,
      String documentNumber,
      String nationality,
      boolean primary) {}

  public record Create(
      Long hotelId,
      Long roomTypeId,
      LocalDate checkIn,
      LocalDate checkOut,
      int adults,
      int children,
      int quantity,
      String contactName,
      String contactEmail,
      String contactPhone,
      String specialRequests,
      String promotionCode,
      String source,
      List<GuestInput> guests) {}

  public record RoomLine(
      Long bookingRoomId,
      Long roomTypeId,
      String roomType,
      String roomNumber,
      BigDecimal nightlyPrice) {}

  public record View(
      Long id,
      String bookingCode,
      String status,
      Long hotelId,
      LocalDate checkIn,
      LocalDate checkOut,
      int adults,
      int children,
      List<RoomLine> rooms,
      BigDecimal roomSubtotal,
      BigDecimal serviceSubtotal,
      BigDecimal discountAmount,
      BigDecimal taxAmount,
      BigDecimal serviceFee,
      BigDecimal totalAmount,
      BigDecimal paidAmount,
      Instant holdExpiresAt) {}

  public record Cancellation(View booking, BigDecimal cancellationFee, BigDecimal expectedRefund) {}

  public record CancellationQuote(
      BigDecimal cancellationFee, BigDecimal expectedRefund, String policy) {}

  public record RefundView(
      Long id,
      String bookingCode,
      BigDecimal amount,
      String reason,
      String status,
      Instant createdAt) {
    public static RefundView of(RefundRequest request) {
      return new RefundView(
          request.getId(),
          request.getBooking().getBookingCode(),
          request.getAmount(),
          request.getReason(),
          request.getStatus(),
          request.getCreatedAt());
    }
  }
}
