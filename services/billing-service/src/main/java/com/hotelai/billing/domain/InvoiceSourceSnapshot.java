package com.hotelai.billing.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record InvoiceSourceSnapshot(
        UUID stayId,
        UUID reservationId,
        UUID customerId,
        String reservationReference,
        String currency,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal taxAmount,
        BigDecimal folioAmount,
        BigDecimal totalAmount,
        BigDecimal paidAmount,
        BigDecimal balance,
        String sourceHash,
        List<InvoiceLineSpec> lines) {}
