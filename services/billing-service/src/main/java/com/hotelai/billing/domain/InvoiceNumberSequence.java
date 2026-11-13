package com.hotelai.billing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "invoice_number_sequences")
public class InvoiceNumberSequence {
    @Id
    @Column(name = "series_year", length = 20)
    private String seriesYear;
    @Column(name = "next_value", nullable = false)
    private long nextValue;

    protected InvoiceNumberSequence() {}

    public InvoiceNumberSequence(String series, int year) {
        this.seriesYear = series + "-" + year;
        this.nextValue = 1;
    }

    public String allocate(String series, int year) {
        if (!seriesYear.equals(series + "-" + year)) {
            throw new IllegalArgumentException("Invoice sequence series mismatch");
        }
        long value = nextValue++;
        return "%s-%04d-%06d".formatted(series, year, value);
    }
}
