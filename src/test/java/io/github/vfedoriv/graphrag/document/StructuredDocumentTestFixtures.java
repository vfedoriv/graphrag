package io.github.vfedoriv.graphrag.document;

import java.io.ByteArrayOutputStream;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFStyles;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STStyleType;

public final class StructuredDocumentTestFixtures {

    private StructuredDocumentTestFixtures() {
    }

    public static byte[] docxBytes() throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            XWPFStyles styles = document.createStyles();
            for (int level = 1; level <= 6; level++) {
                addStyle(styles, "Heading" + level, "Heading " + level);
            }
            addStyle(styles, "CustomCallout", "Custom Callout");

            addStyledParagraph(document, "Heading 1", "Heading1");
            addStyledParagraph(document, "Repeated paragraph", null);
            for (int level = 2; level <= 6; level++) {
                addStyledParagraph(document, "Heading " + level, "Heading" + level);
            }
            XWPFTable table = document.createTable(1, 2);
            table.getRow(0).getCell(0).setText("Cell A");
            table.getRow(0).getCell(1).setText("Cell B");
            addStyledParagraph(document, "1. Literal list marker", null);
            addStyledParagraph(document, "Custom style text", "CustomCallout");
            addStyledParagraph(document, "Repeated paragraph", null);
            document.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    public static byte[] pdfBytes(String... pageTexts) throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            for (String pageText : List.of(pageTexts)) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                    contentStream.beginText();
                    contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    contentStream.newLineAtOffset(72, 720);
                    contentStream.showText(pageText);
                    contentStream.endText();
                }
            }
            document.save(outputStream);
            return outputStream.toByteArray();
        }
    }

    public static byte[] pdfPageWithLines(String... lines) throws Exception {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.setLeading(18);
                contentStream.newLineAtOffset(72, 720);
                for (int index = 0; index < lines.length; index++) {
                    if (index > 0) {
                        contentStream.newLine();
                    }
                    contentStream.showText(lines[index]);
                }
                contentStream.endText();
            }
            document.save(outputStream);
            return outputStream.toByteArray();
        }
    }

    private static void addStyle(XWPFStyles styles, String styleId, String styleName) {
        CTStyle style = CTStyle.Factory.newInstance();
        style.setStyleId(styleId);
        style.setType(STStyleType.PARAGRAPH);
        style.addNewName().setVal(styleName);
        styles.addStyle(new XWPFStyle(style));
    }

    private static void addStyledParagraph(XWPFDocument document, String text, String styleId) {
        XWPFParagraph paragraph = document.createParagraph();
        if (styleId != null) {
            paragraph.setStyle(styleId);
        }
        XWPFRun run = paragraph.createRun();
        run.setText(text);
    }
}
