package kz.jondeu.estimates;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import org.springframework.stereotype.Service;

/** Renders an estimate to PDF using an embedded Unicode font (Cyrillic + tenge sign). */
@Service
public class EstimatePdfService {
    private final BaseFont regular;
    private final BaseFont bold;

    public EstimatePdfService() {
        regular = load("/fonts/DejaVuSans.ttf");
        bold = load("/fonts/DejaVuSans-Bold.ttf");
    }

    private static BaseFont load(String path) {
        try (InputStream in = EstimatePdfService.class.getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("Missing font " + path);
            return BaseFont.createFont(path, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, in.readAllBytes(), null);
        } catch (IOException | DocumentException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String money(java.math.BigDecimal v, String cur) {
        DecimalFormatSymbols sym = new DecimalFormatSymbols(Locale.ROOT);
        sym.setGroupingSeparator(' ');
        sym.setDecimalSeparator(',');
        return new DecimalFormat("#,##0.00", sym).format(v) + " " + ("KZT".equals(cur) ? "₸" : cur);
    }

    public byte[] render(EstimateService.EstimateView e) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 40, 40, 40, 40);
        try {
            PdfWriter.getInstance(doc, out);
            doc.open();
            Font h1 = new Font(bold, 18, Font.NORMAL, new java.awt.Color(0x18, 0x3B, 0x50));
            Font b = new Font(regular, 10);
            Font bb = new Font(bold, 10);
            Font small = new Font(regular, 8, Font.NORMAL, java.awt.Color.GRAY);
            doc.add(new Paragraph("JONDEU — Смета " + e.number() + " (версия " + e.version() + ")", h1));
            doc.add(new Paragraph("Статус: " + e.status() + "   Валюта: " + e.currency(), b));
            doc.add(new Paragraph(" ", b));
            PdfPTable t = new PdfPTable(new float[]{0.6f, 4f, 1.2f, 1f, 1.8f, 2f});
            t.setWidthPercentage(100);
            for (String h : new String[]{"№", "Работа / материал", "Кол-во", "Ед.", "Цена", "Сумма"}) {
                PdfPCell c = new PdfPCell(new Phrase(h, bb));
                c.setBackgroundColor(new java.awt.Color(0xF7, 0xF5, 0xF0));
                c.setPadding(5);
                t.addCell(c);
            }
            for (var i : e.items()) {
                t.addCell(cell(String.valueOf(i.position()), b, Element.ALIGN_LEFT));
                t.addCell(cell(i.title(), b, Element.ALIGN_LEFT));
                t.addCell(cell(i.quantity().stripTrailingZeros().toPlainString(), b, Element.ALIGN_RIGHT));
                t.addCell(cell(i.unit(), b, Element.ALIGN_LEFT));
                t.addCell(cell(money(i.unitPrice(), e.currency()), b, Element.ALIGN_RIGHT));
                t.addCell(cell(money(i.total(), e.currency()), b, Element.ALIGN_RIGHT));
            }
            doc.add(t);
            doc.add(new Paragraph(" ", b));
            PdfPTable s = new PdfPTable(new float[]{3f, 2f});
            s.setHorizontalAlignment(Element.ALIGN_RIGHT);
            s.setWidthPercentage(55);
            row(s, "Подитог", money(e.subtotal(), e.currency()), b);
            row(s, "Дополнительные расходы", money(e.additionalCosts(), e.currency()), b);
            row(s, "Скидка", "−" + money(e.discount(), e.currency()), b);
            row(s, "Налог (" + e.taxRate().stripTrailingZeros().toPlainString() + "%)", money(e.taxAmount(), e.currency()), b);
            row(s, "ИТОГО", money(e.total(), e.currency()), bb);
            row(s, "Оплачено", money(e.paid(), e.currency()), b);
            row(s, "Остаток", money(e.remaining(), e.currency()), bb);
            doc.add(s);
            doc.add(new Paragraph(" ", b));
            doc.add(new Paragraph("Сформировано платформой JONDEU. Суммы рассчитаны на сервере.", small));
            doc.close();
        } catch (DocumentException ex) {
            throw new IllegalStateException(ex);
        }
        return out.toByteArray();
    }

    private static PdfPCell cell(String text, Font f, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text, f));
        c.setHorizontalAlignment(align);
        c.setPadding(4);
        c.setBorderColor(new java.awt.Color(0xE5, 0xE2, 0xDA));
        return c;
    }

    private static void row(PdfPTable t, String l, String r, Font f) {
        PdfPCell a = new PdfPCell(new Phrase(l, f)); a.setBorder(Rectangle.NO_BORDER); a.setPadding(3);
        PdfPCell c = new PdfPCell(new Phrase(r, f)); c.setBorder(Rectangle.NO_BORDER); c.setHorizontalAlignment(Element.ALIGN_RIGHT); c.setPadding(3);
        t.addCell(a); t.addCell(c);
    }
}
