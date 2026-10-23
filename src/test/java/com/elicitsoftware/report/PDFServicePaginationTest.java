package com.elicitsoftware.report;

/*-
 * ***LICENSE_START***
 * Elicit Survey
 * %%
 * Copyright (C) 2025 - 2026 The Regents of the University of Michigan - Rogel Cancer Center
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import com.elicitsoftware.report.pdf.Content;
import com.elicitsoftware.report.pdf.PDFDocument;
import com.elicitsoftware.report.pdfbox.Column;
import com.elicitsoftware.report.pdfbox.TableBuilder;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-005 step 4, the merged PDF of a respondent's reports, beyond what {@code PDFServiceTest}
 * covers: page headers with and without the survey title, the footer's base address, an
 * embedded SVG (the pedigree) on a landscape page, and the page breaks text, titles and long
 * tables take when they reach the bottom of a page. Each PDF is parsed back with PDFBox and its
 * pages and text are checked; the servlet request behind the footer is a proxy.
 */
class PDFServicePaginationTest {

    private static final String SVG = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"400\" height=\"200\">"
            + "<rect x=\"10\" y=\"10\" width=\"300\" height=\"150\" fill=\"#cc0000\"/></svg>";

    private static PDFService service(String scheme, String host, int port, String contextPath) {
        PDFService service = new PDFService();
        service.request = (HttpServletRequest) Proxy.newProxyInstance(HttpServletRequest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getScheme" -> scheme;
                    case "getServerName" -> host;
                    case "getServerPort" -> port;
                    case "getContextPath" -> contextPath;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        return service;
    }

    private static PDFService service() {
        return service("http", "localhost", 8080, "");
    }

    private static ReportResponse report(String title, Content... contents) {
        PDFDocument pdf = new PDFDocument();
        pdf.title = title;
        pdf.content = contents;
        return new ReportResponse(title, "<p>" + title + "</p>", pdf);
    }

    private static Content text(String text) {
        Content content = new Content();
        content.text = text;
        return content;
    }

    private static Content svg() {
        Content content = new Content();
        content.svg = SVG;
        return content;
    }

    private static Content table(int rows) {
        com.elicitsoftware.report.pdf.Table table = new com.elicitsoftware.report.pdf.Table();
        table.headers = new String[]{"Question", "Answer"};
        table.widths = new float[]{250f, 250f};
        table.body = new String[rows][];
        for (int i = 0; i < rows; i++) {
            table.body[i] = new String[]{"Row " + (i + 1), "Value " + (i + 1)};
        }
        return new Content(table);
    }

    private static ArrayList<ReportResponse> reports(ReportResponse... responses) {
        return new ArrayList<>(List.of(responses));
    }

    private static PDDocument parse(byte[] bytes) throws IOException {
        return Loader.loadPDF(bytes);
    }

    private static String textOf(PDDocument document, int page) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setStartPage(page);
        stripper.setEndPage(page);
        return stripper.getText(document);
    }

    private static String contentStreamOf(PDPage page) throws IOException {
        try (InputStream in = page.getContents()) {
            return new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
        }
    }

    // ── headers and footers ────────────────────────────────────────────────────────────────────

    @Test
    void given_aSurveyTitle_when_generatePDF_then_everyPageHeaderNamesItWithThePageNumber() throws IOException {
        byte[] bytes = service().generatePDF(reports(report("Summary", text("Hello"))), "Family History", Locale.ENGLISH);

        try (PDDocument document = parse(bytes)) {
            String text = textOf(document, 1);
            assertTrue(text.contains("Family History - Page 1 of 1"), text);
            assertTrue(text.contains("http://localhost:8080"), text);
        }
    }

    @Test
    void given_aBlankSurveyTitle_when_generatePDF_then_theHeaderIsThePageNumberAlone() throws IOException {
        byte[] bytes = service().generatePDF(reports(report("Summary", text("Hello"))), "  ", Locale.ENGLISH);

        try (PDDocument document = parse(bytes)) {
            String text = textOf(document, 1);
            assertFalse(text.contains(" - Page"), text);
            assertEquals(2, text.split("Page 1 of 1", -1).length - 1, "header and footer both number the page: " + text);
        }
    }

    @Test
    void given_theDefaultHttpsPort_when_generatePDF_then_theFooterAddressHasNoPort() throws IOException {
        byte[] bytes = service("https", "survey.example.org", 443, "/survey").generatePDF(reports(report("S", text("x"))));

        try (PDDocument document = parse(bytes)) {
            String text = textOf(document, 1);
            assertTrue(text.contains("https://survey.example.org/survey"), text);
            assertFalse(text.contains(":443"), text);
        }
    }

    @Test
    void given_theDefaultHttpPort_when_generatePDF_then_theFooterAddressHasNoPort() throws IOException {
        byte[] bytes = service("http", "survey.example.org", 80, "").generatePDF(reports(report("S", text("x"))));

        try (PDDocument document = parse(bytes)) {
            String text = textOf(document, 1);
            assertTrue(text.contains("http://survey.example.org"), text);
            assertFalse(text.contains(":80"), text);
        }
    }

    // ── SVG (the pedigree) ─────────────────────────────────────────────────────────────────────

    @Test
    void given_anSvg_when_generatePDF_then_itIsDrawnOnALandscapePageWithACenteredHeader() throws IOException {
        byte[] bytes = service().generatePDF(reports(report("Pedigree", svg())), "Family History", Locale.ENGLISH);

        try (PDDocument document = parse(bytes)) {
            assertEquals(1, document.getNumberOfPages());
            PDRectangle box = document.getPage(0).getMediaBox();
            assertEquals(PDRectangle.LETTER.getHeight(), box.getWidth());
            assertEquals(PDRectangle.LETTER.getWidth(), box.getHeight());
            assertTrue(document.getPage(0).getResources().getXObjectNames().iterator().hasNext(), "the drawing is a form XObject");
            String text = textOf(document, 1);
            assertTrue(text.contains("Family History - Page 1 of 1"), text);
        }
    }

    @Test
    void given_anSvgOnAPortraitHeader_when_generatePDF_then_theLandscapeHeaderIsCenteredNotLeftAligned() throws IOException {
        byte[] bytes = service().generatePDF(reports(report("Pedigree", svg())), "T", Locale.ENGLISH);

        try (PDDocument document = parse(bytes)) {
            String stream = contentStreamOf(document.getPage(0));
            // Portrait headers start at PADDING + 150 = 190; a landscape one is centered on 792.
            assertTrue(stream.contains(" Td"), stream);
            assertFalse(stream.contains("190 582 Td"), stream.substring(Math.max(0, stream.length() - 400)));
        }
    }

    // ── page breaks ────────────────────────────────────────────────────────────────────────────

    @Test
    void given_moreTextThanFitsOnAPage_when_generatePDF_then_itContinuesOnNewPagesWithNothingLost() throws IOException {
        Content[] contents = new Content[90];
        for (int i = 0; i < contents.length; i++) {
            contents[i] = text("Line number " + (i + 1));
        }

        byte[] bytes = service().generatePDF(reports(report("Long", contents)));

        try (PDDocument document = parse(bytes)) {
            assertTrue(document.getNumberOfPages() >= 3, "pages: " + document.getNumberOfPages());
            String all = new PDFTextStripper().getText(document);
            for (int i = 1; i <= contents.length; i++) {
                assertTrue(all.contains("Line number " + i + "\n") || all.contains("Line number " + i + "\r"), "line " + i);
            }
            int pages = document.getNumberOfPages();
            assertTrue(all.contains("Page " + pages + " of " + pages), all);
        }
    }

    @Test
    void given_aTableLongerThanAPage_when_generatePDF_then_itContinuesOnFreshPages() throws IOException {
        byte[] bytes = service().generatePDF(reports(report("Answers", table(120))));

        try (PDDocument document = parse(bytes)) {
            assertTrue(document.getNumberOfPages() >= 3, "pages: " + document.getNumberOfPages());
            String all = new PDFTextStripper().getText(document);
            assertTrue(all.contains("Row 1 ") || all.contains("Row 1\n"), "first row");
            assertTrue(all.contains("Value 120"), "last row");
            String last = textOf(document, document.getNumberOfPages());
            assertTrue(last.contains("Question"), "every table page repeats the header row: " + last);
            for (int p = 0; p < document.getNumberOfPages(); p++) {
                PDRectangle box = document.getPage(p).getMediaBox();
                assertTrue(box.getHeight() > box.getWidth(), "table pages stay portrait");
            }
        }
    }

    /** Opens a fresh document on the service as generatePDF would, with the cursor at {@code y}. */
    private static PDFService openAt(float y) throws IOException {
        PDFService service = service();
        service.document = new PDDocument();
        service.page = new PDPage(PDRectangle.LETTER);
        service.document.addPage(service.page);
        service.contentStream = new PDPageContentStream(service.document, service.page);
        service.contentStream.setFont(PDFService.TEXT_FONT, PDFService.FONT_SIZE);
        service.pageHeight = PDRectangle.LETTER.getHeight();
        service.pageWidth = PDRectangle.LETTER.getWidth();
        service.yPosition = y;
        return service;
    }

    private static byte[] close(PDFService service) throws IOException {
        service.contentStream.close();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        service.document.save(out);
        service.document.close();
        return out.toByteArray();
    }

    @Test
    void given_aTitleNearTheBottom_when_addTitleBlock_then_itStartsANewPageAtTheTop() throws IOException {
        PDFService service = openAt(60f);

        service.addTitleBlock("Second report");

        assertEquals(2, service.document.getNumberOfPages());
        assertEquals(PDRectangle.LETTER.getHeight() - PDFService.TEXT_MARGIN - PDFService.LEADING, service.yPosition);
        try (PDDocument document = parse(close(service))) {
            assertTrue(textOf(document, 2).contains("Second report"));
            assertFalse(textOf(document, 1).contains("Second report"));
        }
    }

    @Test
    void given_aTitleWithRoomLeft_when_addTitleBlock_then_itStaysOnThePage() throws IOException {
        PDFService service = openAt(PDFService.FONT_SIZE + PDFService.PADDING + PDFService.HEADER_MARGIN);

        service.addTitleBlock("Fits");

        assertEquals(1, service.document.getNumberOfPages());
        assertEquals(PDFService.FONT_SIZE + PDFService.PADDING - PDFService.LEADING, service.yPosition);
        close(service);
    }

    @Test
    void given_noTitle_when_addTitleBlock_then_nothingMoves() throws IOException {
        PDFService service = openAt(300f);

        service.addTitleBlock(null);
        service.addTitleBlock("");

        assertEquals(300f, service.yPosition);
        close(service);
    }

    @Test
    void given_textBelowTheBottomMargin_when_addTextBlock_then_aPageIsAdded() throws IOException {
        PDFService service = openAt(30f);

        service.addTextBlock("Continued");

        assertEquals(2, service.document.getNumberOfPages());
        close(service);
    }

    @Test
    void given_textWithRoomLeft_when_addTextBlock_then_eachLineMovesTheCursorAndTheBlockLeavesAGap() throws IOException {
        PDFService service = openAt(PDFService.PADDING + PDFService.FONT_SIZE);

        service.addTextBlock("One line");

        assertEquals(1, service.document.getNumberOfPages());
        assertEquals(PDFService.PADDING + PDFService.FONT_SIZE - (PDFService.FONT_SIZE + 2) - PDFService.LEADING, service.yPosition);
        close(service);
    }

    private static com.elicitsoftware.report.pdfbox.Table landscapeTable(int rows) {
        String[][] content = new String[rows][];
        float[] heights = new float[rows];
        for (int i = 0; i < rows; i++) {
            content[i] = new String[]{"r" + i, "v" + i};
            heights[i] = 15f;
        }
        return new TableBuilder()
                .setCellMargin(2)
                .setColumns(List.of(new Column("A", 100), new Column("B", 100)))
                .setContent(content)
                .setRowHeights(heights)
                .setHeight(600)
                .setNumberOfRows(rows)
                .setRowHeight(15)
                .setMargin(PDFService.PADDING)
                .setPageSize(PDRectangle.LETTER)
                .setLandscape(true)
                .setTextFont(PDFService.TEXT_FONT)
                .setFontSize(PDFService.FONT_SIZE)
                .build();
    }

    @Test
    void given_aLandscapeTableSpanningPages_when_drawTable_then_continuationPagesAreRotated() throws IOException {
        PDFService service = openAt(PDRectangle.LETTER.getHeight() - PDFService.TEXT_MARGIN);

        service.drawTable(landscapeTable(80));

        int pages = service.document.getNumberOfPages();
        assertTrue(pages >= 2, "pages: " + pages);
        try (PDDocument document = parse(close(service))) {
            String continuation = contentStreamOf(document.getPage(1));
            assertTrue(continuation.contains("0 1 -1 0 612 0 cm"), continuation.substring(0, Math.min(200, continuation.length())));
            assertFalse(contentStreamOf(document.getPage(0)).contains(" cm"), "the first page is the caller's");
        }
    }

    @Test
    void given_anEmptyTable_when_drawTable_then_nothingIsDrawn() throws IOException {
        PDFService service = openAt(500f);

        service.drawTable(landscapeTable(0));

        assertEquals(1, service.document.getNumberOfPages());
        assertEquals(500f, service.yPosition);
        close(service);
    }
}
