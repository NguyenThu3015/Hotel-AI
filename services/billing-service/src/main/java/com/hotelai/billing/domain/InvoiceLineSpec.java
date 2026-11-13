package com.hotelai.billing.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record InvoiceLineSpec(
        InvoiceLineCategory category,
        UUID sourceId,
        String description,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal amount,
        int displayOrder) {}
