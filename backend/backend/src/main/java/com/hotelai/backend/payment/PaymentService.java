package com.hotelai.backend.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotelai.backend.audit.AuditService;
import com.hotelai.backend.booking.Booking;
import com.hotelai.backend.booking.BookingService;
import com.hotelai.backend.common.ApiException;
import com.hotelai.backend.notification.Notification;
import com.hotelai.backend.notification.NotificationRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.Principal;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentService {
  private final PaymentRepository payments;
  private final InvoiceRepository invoices;
  private final BookingService bookings;
  private final AuditService audit;
  private final ObjectMapper json;
  private final NotificationRepository notifications;
  private final JavaMailSender mail;

  @Value("${hotel.payment.webhook-secret}")
  private String secret;

  @Transactional
  public View create(
      Long bookingId,
      String key,
      String provider,
      String type,
      BigDecimal amount,
      Principal principal) {
    Payment existing = payments.findByIdempotencyKey(key).orElse(null);
    if (existing != null) {
      bookings.owned(existing.getBooking().getId(), principal);
      return view(existing);
    }
    Booking booking = bookings.owned(bookingId, principal);
    if (!java.util.List.of("PENDING_PAYMENT", "CONFIRMED", "CHECKED_IN")
        .contains(booking.getStatus())) throw bad("Trạng thái booking không cho phép thanh toán");
    BigDecimal due = booking.getTotalAmount().subtract(booking.getPaidAmount());
    if (amount.signum() <= 0 || amount.compareTo(due) > 0)
      throw bad("Số tiền thanh toán vượt số còn phải trả");
    Payment payment = payments.save(new Payment(booking, key, provider, type, amount));
    audit.record("CREATE_PAYMENT", "PAYMENT", payment.getId(), "booking=" + bookingId);
    return view(payment);
  }

  @Transactional
  public View webhook(String body, String signature) throws Exception {
    String expected = hmac(body);
    if (signature == null
        || !MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8)))
      throw new ApiException(HttpStatus.UNAUTHORIZED, "Chữ ký webhook không hợp lệ");
    Webhook webhook = json.readValue(body, Webhook.class);
    Payment payment =
        payments
            .findByIdempotencyKey(webhook.idempotencyKey)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy giao dịch"));
    if ("SUCCESS".equals(webhook.status) && payment.success(webhook.transactionId, body)) {
      Booking booking = bookings.lock(payment.getBooking().getId());
      booking.paid(payment.getAmount());
      if ("PENDING_PAYMENT".equals(booking.getStatus())) booking.confirm();
      notifications.save(
          new Notification(
              booking.getUser(),
              "PAYMENT_SUCCEEDED",
              "Thanh toán thành công",
              "Booking " + booking.getBookingCode() + " đã được xác nhận"));
      sendConfirmation(booking);
      audit.record("PAYMENT_SUCCEEDED", "PAYMENT", payment.getId(), webhook.transactionId);
    } else if (!"SUCCESS".equals(webhook.status)) payment.fail(body);
    return view(payment);
  }

  @Transactional
  public View refund(Long id) {
    Payment payment =
        payments
            .findById(id)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy giao dịch"));
    if (!"SUCCEEDED".equals(payment.getStatus())) throw bad("Chỉ hoàn giao dịch đã thành công");
    payment.refund(payment.getAmount());
    payment.getBooking().refunded(payment.getAmount());
    audit.record("REFUND_PAYMENT", "PAYMENT", id, null);
    return view(payment);
  }

  @Transactional
  public View completeSandbox(Long id) throws Exception {
    Payment payment =
        payments
            .findById(id)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy giao dịch"));
    if (!"SANDBOX".equals(payment.getProvider())) throw bad("Chỉ dùng cho cổng thanh toán sandbox");
    String body =
        json.writeValueAsString(
            new Webhook(
                payment.getIdempotencyKey(), "sandbox-" + java.util.UUID.randomUUID(), "SUCCESS"));
    return webhook(body, hmac(body));
  }

  @Transactional
  public View completeCash(Long id, Principal principal) throws Exception {
    Payment payment =
        payments
            .findById(id)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Không tìm thấy giao dịch"));
    bookings.owned(payment.getBooking().getId(), principal);
    if (!"CASH".equals(payment.getProvider()))
      throw bad("Giao dịch không phải thanh toán tại quầy");
    String body =
        json.writeValueAsString(
            new Webhook(
                payment.getIdempotencyKey(), "cash-" + java.util.UUID.randomUUID(), "SUCCESS"));
    return webhook(body, hmac(body));
  }

  public Invoice invoice(Booking booking) {
    return invoices
        .findByBookingId(booking.getId())
        .orElseGet(() -> invoices.save(new Invoice(booking)));
  }

  @Transactional
  public InvoiceView invoice(Long bookingId, Principal principal) {
    bookings.owned(bookingId, principal);
    Invoice invoice =
        invoices
            .findByBookingId(bookingId)
            .orElseThrow(
                () -> new ApiException(HttpStatus.NOT_FOUND, "Hóa đơn chưa được phát hành"));
    return new InvoiceView(
        invoice.getId(),
        invoice.getInvoiceNumber(),
        bookingId,
        invoice.getSubtotal(),
        invoice.getDiscountAmount(),
        invoice.getTaxAmount(),
        invoice.getTotalAmount(),
        invoice.getIssuedAt());
  }

  private String hmac(String body) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    return java.util.HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
  }

  @Transactional
  public BookingService.RefundView approveRefund(Long requestId) {
    var request = bookings.refundRequest(requestId);
    if (!"PENDING".equals(request.getStatus())) throw bad("Yêu cầu hoàn tiền đã được xử lý");
    Payment payment =
        payments
            .findFirstByBookingIdAndStatusOrderByPaidAtDesc(
                request.getBooking().getId(), "SUCCEEDED")
            .orElseThrow(() -> bad("Không tìm thấy giao dịch thành công để hoàn"));
    payment.refund(request.getAmount());
    request.getBooking().refunded(request.getAmount());
    request.complete();
    audit.record(
        "APPROVE_REFUND", "BOOKING", request.getBooking().getId(), request.getAmount().toString());
    return BookingService.RefundView.of(request);
  }

  private void sendConfirmation(Booking booking) {
    try {
      SimpleMailMessage message = new SimpleMailMessage();
      message.setTo(booking.getContactEmail());
      message.setSubject("HotelAI - Xác nhận " + booking.getBookingCode());
      message.setText(
          "Thanh toán thành công. Booking "
              + booking.getBookingCode()
              + " đã được xác nhận cho kỳ nghỉ "
              + booking.getCheckInDate()
              + " đến "
              + booking.getCheckOutDate()
              + ".");
      mail.send(message);
    } catch (RuntimeException ignored) {
      // Giao dịch không bị rollback khi máy chủ email tạm thời gián đoạn.
    }
  }

  private ApiException bad(String message) {
    return new ApiException(HttpStatus.BAD_REQUEST, message);
  }

  private View view(Payment p) {
    return new View(
        p.getId(),
        p.getBooking().getId(),
        p.getProvider(),
        p.getType(),
        p.getStatus(),
        p.getAmount(),
        p.getCurrency(),
        p.getRefundedAmount(),
        p.getProviderTransactionId());
  }

  public record Webhook(String idempotencyKey, String transactionId, String status) {}

  public record View(
      Long id,
      Long bookingId,
      String provider,
      String type,
      String status,
      BigDecimal amount,
      String currency,
      BigDecimal refundedAmount,
      String providerTransactionId) {}

  public record InvoiceView(
      Long id,
      String invoiceNumber,
      Long bookingId,
      BigDecimal subtotal,
      BigDecimal discountAmount,
      BigDecimal taxAmount,
      BigDecimal totalAmount,
      java.time.Instant issuedAt) {}
}
