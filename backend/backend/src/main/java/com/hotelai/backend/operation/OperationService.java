package com.hotelai.backend.operation;

import com.hotelai.backend.account.User;
import com.hotelai.backend.account.UserRepository;
import com.hotelai.backend.audit.AuditService;
import com.hotelai.backend.booking.Booking;
import com.hotelai.backend.booking.BookingRoom;
import com.hotelai.backend.booking.BookingService;
import com.hotelai.backend.catalog.CatalogService;
import com.hotelai.backend.catalog.Room;
import com.hotelai.backend.common.ApiException;
import com.hotelai.backend.notification.Notification;
import com.hotelai.backend.notification.NotificationRepository;
import com.hotelai.backend.payment.Invoice;
import com.hotelai.backend.payment.PaymentService;
import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OperationService {
  private final BookingService bookings;
  private final CatalogService catalog;
  private final UserRepository users;
  private final ExtraServiceRepository services;
  private final ServiceUsageRepository usages;
  private final HousekeepingRepository housekeeping;
  private final MaintenanceRepository maintenance;
  private final PaymentService payments;
  private final NotificationRepository notifications;
  private final AuditService audit;

  @Transactional
  public BookingService.View checkIn(Long bookingId, List<Long> roomIds) {
    Booking b = bookings.lock(bookingId);
    if (!"CONFIRMED".equals(b.getStatus())) throw bad("Chỉ booking đã xác nhận mới được check-in");
    if (!b.getCheckInDate().equals(java.time.LocalDate.now()))
      throw bad("Ngày hiện tại không phải ngày nhận phòng");
    if (roomIds.size() != b.getRooms().size()) throw bad("Số phòng phân không khớp booking");
    for (int i = 0; i < roomIds.size(); i++) {
      BookingRoom br = b.getRooms().get(i);
      Room room = catalog.lockedRoom(roomIds.get(i));
      if (!room.getRoomType().getId().equals(br.getRoomType().getId())
          || !"AVAILABLE".equals(room.getStatus()))
        throw new ApiException(
            HttpStatus.CONFLICT, "Phòng " + room.getRoomNumber() + " không sẵn sàng hoặc sai loại");
      br.assign(room);
      room.status("OCCUPIED");
    }
    b.checkIn();
    notifications.save(
        new Notification(
            b.getUser(),
            "CHECK_IN",
            "Check-in thành công",
            "Booking " + b.getBookingCode() + " đã nhận phòng"));
    audit.record("CHECK_IN", "BOOKING", bookingId, roomIds.toString());
    return bookings.view(b);
  }

  @Transactional
  public Checkout checkOut(Long bookingId) {
    Booking b = bookings.lock(bookingId);
    if (!"CHECKED_IN".equals(b.getStatus())) throw bad("Booking chưa check-in");
    BigDecimal balance = b.getTotalAmount().subtract(b.getPaidAmount());
    if (balance.signum() > 0)
      throw new ApiException(HttpStatus.PAYMENT_REQUIRED, "Còn phải thanh toán " + balance);
    b.checkOut();
    for (BookingRoom br : b.getRooms()) {
      Room room = catalog.lockedRoom(br.getRoom().getId());
      room.status("WAITING_CLEANING");
      housekeeping.save(new HousekeepingTask(room, b, "HIGH", "Dọn phòng sau check-out"));
    }
    Invoice invoice = payments.invoice(b);
    notifications.save(
        new Notification(
            b.getUser(),
            "CHECK_OUT",
            "Check-out thành công",
            "Hóa đơn " + invoice.getInvoiceNumber() + " đã được phát hành"));
    audit.record("CHECK_OUT", "BOOKING", bookingId, invoice.getInvoiceNumber());
    return new Checkout(
        bookings.view(b),
        new InvoiceView(
            invoice.getId(),
            invoice.getInvoiceNumber(),
            invoice.getSubtotal(),
            invoice.getDiscountAmount(),
            invoice.getTaxAmount(),
            invoice.getTotalAmount()));
  }

  @Transactional
  public BookingService.View noShow(Long bookingId) {
    Booking booking = bookings.lock(bookingId);
    if (!"CONFIRMED".equals(booking.getStatus()))
      throw bad("Chỉ booking đã xác nhận mới có thể ghi nhận no-show");
    if (booking.getCheckInDate().isAfter(java.time.LocalDate.now()))
      throw bad("Chưa đến ngày nhận phòng");
    booking.noShow();
    audit.record("NO_SHOW", "BOOKING", bookingId, null);
    return bookings.view(booking);
  }

  @Transactional
  public UsageView useService(
      Long bookingId, Long serviceId, BigDecimal quantity, String note, Principal p) {
    Booking b = bookings.lock(bookingId);
    if (!"CHECKED_IN".equals(b.getStatus())) throw bad("Chỉ ghi dịch vụ cho booking đang lưu trú");
    ExtraService s = services.findById(serviceId).orElseThrow(() -> notFound("dịch vụ"));
    if (!s.isActive() || !s.getHotel().getId().equals(b.getHotel().getId()))
      throw bad("Dịch vụ không áp dụng cho khách sạn này");
    ServiceUsage u = usages.save(new ServiceUsage(b, s, quantity, current(p), note));
    b.addService(u.getTotalAmount());
    audit.record("ADD_SERVICE_USAGE", "BOOKING", bookingId, s.getCode() + " x" + quantity);
    return new UsageView(u.getId(), s.getName(), quantity, u.getUnitPrice(), u.getTotalAmount());
  }

  @Transactional
  public UsageView prebookService(
      Long bookingId, Long serviceId, BigDecimal quantity, String note, Principal principal) {
    Booking booking = bookings.owned(bookingId, principal);
    if (!List.of("PENDING_PAYMENT", "CONFIRMED").contains(booking.getStatus()))
      throw bad("Chỉ có thể đặt trước dịch vụ cho booking sắp lưu trú");
    ExtraService service = services.findById(serviceId).orElseThrow(() -> notFound("dịch vụ"));
    if (!service.isActive() || !service.getHotel().getId().equals(booking.getHotel().getId()))
      throw bad("Dịch vụ không áp dụng cho khách sạn này");
    ServiceUsage usage =
        usages.save(new ServiceUsage(booking, service, quantity, current(principal), note));
    booking.addService(usage.getTotalAmount());
    audit.record("PREBOOK_SERVICE", "BOOKING", bookingId, service.getCode() + " x" + quantity);
    return new UsageView(
        usage.getId(), service.getName(), quantity, usage.getUnitPrice(), usage.getTotalAmount());
  }

  @Transactional
  public ExtraService saveService(
      Long id,
      Long hotelId,
      String code,
      String name,
      String unit,
      BigDecimal price,
      boolean active) {
    ExtraService s =
        id == null
            ? new ExtraService(catalog.hotel(hotelId), code, name, unit, price)
            : services.findById(id).orElseThrow(() -> notFound("dịch vụ"));
    if (id != null) s.update(name, unit, price, active);
    return services.save(s);
  }

  public List<ExtraService> services(Long hotel) {
    return services.findByHotelIdAndActiveTrue(hotel);
  }

  @Transactional
  public TaskView assignTask(Long id, Long userId) {
    HousekeepingTask t = housekeeping.findById(id).orElseThrow(() -> notFound("công việc"));
    t.assign(users.findById(userId).orElseThrow(() -> notFound("nhân viên")));
    return task(t);
  }

  @Transactional
  public TaskView taskStatus(Long id, String status) {
    HousekeepingTask t = housekeeping.findById(id).orElseThrow(() -> notFound("công việc"));
    t.progress(status);
    if ("COMPLETED".equals(status)) catalog.lockedRoom(t.getRoom().getId()).status("AVAILABLE");
    audit.record("HOUSEKEEPING_" + status, "ROOM", t.getRoom().getId(), null);
    return task(t);
  }

  @Transactional(readOnly = true)
  public List<TaskView> tasks(Principal p) {
    User user = current(p);
    boolean manager =
        user.getRoles().stream().anyMatch(r -> List.of("ADMIN", "MANAGER").contains(r.getName()));
    return (manager
            ? housekeeping.findAll().stream()
            : housekeeping.findByAssignedToIdOrAssignedToIsNull(user.getId()).stream())
        .map(this::task)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<MaintenanceRequest> maintenance(boolean includeResolved) {
    return includeResolved ? maintenance.findAll() : maintenance.findByStatusNot("RESOLVED");
  }

  @Transactional
  public MaintenanceRequest report(
      Long roomId, String title, String description, String severity, Principal p) {
    Room room = catalog.lockedRoom(roomId);
    room.status("MAINTENANCE");
    MaintenanceRequest m =
        maintenance.save(new MaintenanceRequest(room, current(p), title, description, severity));
    audit.record("REPORT_MAINTENANCE", "ROOM", roomId, title);
    return m;
  }

  @Transactional
  public MaintenanceRequest resolve(Long id, String note) {
    MaintenanceRequest m = maintenance.findById(id).orElseThrow(() -> notFound("yêu cầu bảo trì"));
    m.resolve(note);
    catalog.lockedRoom(m.getRoom().getId()).status("AVAILABLE");
    audit.record("RESOLVE_MAINTENANCE", "ROOM", m.getRoom().getId(), note);
    return m;
  }

  private User current(Principal p) {
    return users.findByEmailIgnoreCase(p.getName()).orElseThrow();
  }

  private ApiException bad(String m) {
    return new ApiException(HttpStatus.BAD_REQUEST, m);
  }

  private ApiException notFound(String m) {
    return new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy " + m);
  }

  private TaskView task(HousekeepingTask t) {
    return new TaskView(
        t.getId(),
        t.getRoom().getId(),
        t.getRoom().getRoomNumber(),
        t.getAssignedTo() == null ? null : t.getAssignedTo().getId(),
        t.getStatus(),
        t.getPriority(),
        t.getNote());
  }

  public record UsageView(
      Long id, String service, BigDecimal quantity, BigDecimal unitPrice, BigDecimal total) {}

  public record TaskView(
      Long id,
      Long roomId,
      String roomNumber,
      Long assignedTo,
      String status,
      String priority,
      String note) {}

  public record InvoiceView(
      Long id,
      String number,
      BigDecimal subtotal,
      BigDecimal discount,
      BigDecimal tax,
      BigDecimal total) {}

  public record Checkout(BookingService.View booking, InvoiceView invoice) {}
}
