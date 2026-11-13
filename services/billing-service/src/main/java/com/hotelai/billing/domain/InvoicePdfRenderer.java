package com.hotelai.billing.domain;

import com.hotelai.billing.config.BillingProperties;
import com.hotelai.billing.domain.InvoiceApplicationService.InvoiceDetails;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class InvoicePdfRenderer {
    private final BillingProperties properties;

    public InvoicePdfRenderer(BillingProperties properties) {
        this.properties = properties;
    }

    public byte[] render(InvoiceDetails details) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 42, 42, 42, 42);
            PdfWriter.getInstance(document, output);
            document.open();
            Font normal = font(10, Font.NORMAL);
            Font bold = font(10, Font.BOLD);
            Font title = font(18, Font.BOLD);
            Invoice invoice = details.invoice();

            Paragraph heading = new Paragraph("INVOICE", title);
            heading.setAlignment(Element.ALIGN_CENTER);
            document.add(heading);
            document.add(new Paragraph(
                    properties.legalName() + " - Tax code: " + properties.taxCode(), bold));
            document.add(new Paragraph(properties.legalAddress(), normal));
            document.add(new Paragraph("Invoice number: " + invoice.getInvoiceNumber(), bold));
            document.add(new Paragraph(
                    "Issued at: " + DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                            .withZone(ZoneId.of(properties.timeZone()))
                            .format(invoice.getIssuedAt()), normal));
            document.add(new Paragraph(
                    "Reservation: " + invoice.getReservationReference(), normal));
            document.add(new Paragraph("Buyer: " + invoice.getBuyerName(), normal));
            document.add(new Paragraph("Address: " + invoice.getBuyerAddress(), normal));
            if (invoice.getBuyerTaxCode() != null) {
                document.add(new Paragraph(
                        "Buyer tax code: " + invoice.getBuyerTaxCode(), normal));
            }
            document.add(new Paragraph("Email: " + invoice.getBuyerEmail(), normal));
            document.add(new Paragraph(" "));

            PdfPTable table = new PdfPTable(new float[] {1, 5, 1.5f, 2});
            table.setWidthPercentage(100);
            addHeader(table, "#", bold);
            addHeader(table, "Description", bold);
            addHeader(table, "Quantity", bold);
            addHeader(table, "Amount", bold);
            int index = 1;
            for (InvoiceLine line : details.lines()) {
                addCell(table, String.valueOf(index++), normal, Element.ALIGN_CENTER);
                addCell(table, line.getDescription(), normal, Element.ALIGN_LEFT);
                addCell(table, line.getQuantity().stripTrailingZeros().toPlainString(),
                        normal, Element.ALIGN_RIGHT);
                addCell(table, money(line.getAmount()) + " " + invoice.getCurrency(),
                        normal, Element.ALIGN_RIGHT);
            }
            document.add(table);
            document.add(new Paragraph(" "));
            Paragraph total = new Paragraph(
                    "Total: " + money(invoice.getTotalAmount()) + " " + invoice.getCurrency(),
                    bold);
            total.setAlignment(Element.ALIGN_RIGHT);
            document.add(total);
            Paragraph paid = new Paragraph(
                    "Paid: " + money(invoice.getPaidAmount()) + " " + invoice.getCurrency(),
                    normal);
            paid.setAlignment(Element.ALIGN_RIGHT);
            document.add(paid);
            Paragraph balance = new Paragraph(
                    "Balance: " + money(invoice.getBalance()) + " " + invoice.getCurrency(),
                    bold);
            balance.setAlignment(Element.ALIGN_RIGHT);
            document.add(balance);
            document.close();
            return output.toByteArray();
        } catch (Exception error) {
            throw new IllegalStateException("Cannot render invoice PDF", error);
        }
    }

    private Font font(float size, int style) throws Exception {
        String path = properties.pdfFontPath();
        if (path != null && Files.isRegularFile(Path.of(path))) {
            BaseFont base = BaseFont.createFont(path, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            return new Font(base, size, style);
        }
        return new Font(Font.HELVETICA, size, style);
    }

    private static void addHeader(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(new Color(230, 230, 230));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setPadding(6);
        table.addCell(cell);
    }

    private static void addCell(
            PdfPTable table, String text, Font font, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setHorizontalAlignment(alignment);
        cell.setPadding(5);
        table.addCell(cell);
    }

    private static String money(java.math.BigDecimal value) {
        NumberFormat format = NumberFormat.getNumberInstance(Locale.US);
        format.setMinimumFractionDigits(2);
        format.setMaximumFractionDigits(2);
        return format.format(value);
    }
}
