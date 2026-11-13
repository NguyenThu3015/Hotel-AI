package com.hotelai.backend.payment;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

interface PaymentRepository extends JpaRepository<Payment, Long> {
  Optional<Payment> findByIdempotencyKey(String key);

  Optional<Payment> findByProviderTransactionId(String id);

  Optional<Payment> findFirstByBookingIdAndStatusOrderByPaidAtDesc(Long bookingId, String status);
}

interface InvoiceRepository extends JpaRepository<Invoice, Long> {
  Optional<Invoice> findByBookingId(Long bookingId);
}
