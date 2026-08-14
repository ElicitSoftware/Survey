package com.elicitsoftware.report;

/*-
 * ***LICENSE_START***
 * Elicit Survey
 * %%
 * Copyright (C) 2025 The Regents of the University of Michigan - Rogel Cancer Center
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import com.elicitsoftware.report.pdf.Content;
import com.elicitsoftware.report.pdf.PDFDocument;
import com.elicitsoftware.report.pdf.Table;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UC-005: View Reports & Download PDF — covers PDFService.generatePDF() (main success
 * scenario step 4: "System builds a PDF of the respondent's submitted answers"). PDFService
 * is {@code @RequestScoped} and injects {@code HttpServletRequest} for its footer's base-URL
 * text; under {@code @QuarkusTest} there is no active HTTP request context on the test
 * thread, so (mirroring the workaround used elsewhere in this suite for scoped beans) these
 * tests construct the service directly with {@code new PDFService()} and stub the servlet
 * request field (package-private, same package as this test) rather than injecting it. No
 * database or external HTTP dependency is needed here: generatePDF() operates purely on
 * in-memory {@link ReportResponse}/{@link PDFDocument} data.
 */
class PDFServiceTest {

    /** Package-private field access lets us avoid a real HttpServletRequest/CDI request context. */
    private PDFService newService() {
        PDFService service = new PDFService();
        service.request = fakeRequest();
        return service;
    }

    private HttpServletRequest fakeRequest() {
        return (HttpServletRequest) Proxy.newProxyInstance(
                HttpServletRequest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class},
                (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "getScheme":
                            return "http";
                        case "getServerName":
                            return "localhost";
                        case "getServerPort":
                            return 8080;
                        case "getContextPath":
                            return "";
                        default:
                            Class<?> returnType = method.getReturnType();
                            if (returnType == boolean.class) return false;
                            if (returnType.isPrimitive()) return 0;
                            return null;
                    }
                });
    }

    private ReportResponse textReport(String title, String text, boolean pageBreak) {
        Content content = new Content();
        content.text = text;
        PDFDocument doc = new PDFDocument();
        doc.title = title;
        doc.pageBreak = pageBreak;
        doc.content = new Content[]{content};
        ReportResponse rr = new ReportResponse();
        rr.title = title;
        rr.pdf = doc;
        return rr;
    }

    // ── Main success scenario: PDF built and well-formed ────────────────────

    @Test
    // UC-005 step 4: a single text report produces a non-empty, well-formed PDF.
    void given_singleTextReport_when_generatePDF_then_wellFormedPdfProduced() throws Exception {
        PDFService service = newService();
        ArrayList<ReportResponse> responses = new ArrayList<>();
        responses.add(textReport("Summary Report", "This is the respondent's submitted answers summary.", false));

        byte[] bytes = service.generatePDF(responses);

        assertNotNull(bytes);
        assertTrue(bytes.length > 0, "generatePDF() must produce a non-empty byte array");
        assertPdfMagicBytes(bytes);

        try (PDDocument loaded = Loader.loadPDF(bytes)) {
            assertTrue(loaded.getNumberOfPages() >= 1, "Loaded PDF must have at least one page");
        }
    }

    @Test
    // UC-005 step 4: report content containing a table renders without error and the
    // resulting bytes still load as a valid PDF via PDFBox.
    void given_reportWithTable_when_generatePDF_then_tableRendersIntoValidPdf() throws Exception {
        Table table = new Table();
        table.headers = new String[]{"Item", "Quantity"};
        table.widths = new float[]{0f, 80f}; // index 0 is unused (auto-sized "person"/first column)
        table.body = new String[][]{
                {"Audiobook", "2"},
                {"DVD", "1"}
        };
        Content tableContent = new Content(table);
        PDFDocument doc = new PDFDocument();
        doc.title = "Checkout Table";
        doc.content = new Content[]{tableContent};

        ReportResponse rr = new ReportResponse();
        rr.title = "Checkout Table";
        rr.pdf = doc;

        ArrayList<ReportResponse> responses = new ArrayList<>();
        responses.add(rr);

        PDFService service = newService();
        byte[] bytes = service.generatePDF(responses);

        assertPdfMagicBytes(bytes);
        try (PDDocument loaded = Loader.loadPDF(bytes)) {
            assertTrue(loaded.getNumberOfPages() >= 1);
        }
    }

    @Test
    // UC-005 A1 (PDF-render-side counterpart): when a report's pdf payload is missing --
    // as happens when the external report service call failed upstream -- generatePDF()
    // must render a safe error block instead of throwing, so the overall PDF download
    // still succeeds even if one configured report errored.
    void given_reportWithNullPdfPayload_when_generatePDF_then_errorBlockRenderedInsteadOfThrowing() throws Exception {
        ReportResponse errored = new ReportResponse();
        errored.title = "Error - Risk Calculator";
        errored.innerHTML = "<div>Service error: License validation failed</div>";
        errored.pdf = null; // no PDF payload came back from the failed external call

        ArrayList<ReportResponse> responses = new ArrayList<>();
        responses.add(errored);

        PDFService service = newService();
        byte[] bytes = assertDoesNotThrow(() -> service.generatePDF(responses),
                "A null pdf payload must not cause generatePDF() to throw");

        assertPdfMagicBytes(bytes);
        try (PDDocument loaded = Loader.loadPDF(bytes)) {
            assertTrue(loaded.getNumberOfPages() >= 1);
        }
    }

    @Test
    // UC-005: a null entry in the response list (defensive case) is skipped, not fatal.
    void given_nullEntryInResponseList_when_generatePDF_then_skippedGracefully() throws Exception {
        ArrayList<ReportResponse> responses = new ArrayList<>();
        responses.add(textReport("Report One", "First report body text.", false));
        responses.add(null);
        responses.add(textReport("Report Two", "Second report body text.", false));

        PDFService service = newService();
        byte[] bytes = assertDoesNotThrow(() -> service.generatePDF(responses));

        assertPdfMagicBytes(bytes);
        try (PDDocument loaded = Loader.loadPDF(bytes)) {
            assertTrue(loaded.getNumberOfPages() >= 1);
        }
    }

    @Test
    // UC-005 A2: zero configured reports still produces a valid (header/footer-only) PDF --
    // the PDF download itself must remain available even with no report cards.
    void given_emptyReportList_when_generatePDF_then_stillProducesValidPdf() throws Exception {
        PDFService service = newService();
        byte[] bytes = service.generatePDF(new ArrayList<>());

        assertPdfMagicBytes(bytes);
        try (PDDocument loaded = Loader.loadPDF(bytes)) {
            assertEquals(1, loaded.getNumberOfPages(), "An empty report list still yields the single initial page");
        }
    }

    @Test
    // UC-005 step 4: multiple reports each render their own title, and a report configured
    // with pageBreak=true forces a new page rather than sharing the previous report's page.
    void given_multipleReportsWithPageBreak_when_generatePDF_then_additionalPageCreated() throws Exception {
        ArrayList<ReportResponse> responses = new ArrayList<>();
        responses.add(textReport("First Report", "Body of the first report.", false));
        responses.add(textReport("Second Report", "Body of the second report.", true));

        PDFService service = newService();
        byte[] bytes = service.generatePDF(responses);

        assertPdfMagicBytes(bytes);
        try (PDDocument loaded = Loader.loadPDF(bytes)) {
            assertTrue(loaded.getNumberOfPages() >= 2,
                    "pageBreak=true on the second report must force at least a 2nd page");
        }
    }

    private void assertPdfMagicBytes(byte[] bytes) {
        assertNotNull(bytes);
        assertTrue(bytes.length > 5, "PDF bytes too short to contain a header");
        String header = new String(bytes, 0, 5, java.nio.charset.StandardCharsets.US_ASCII);
        assertEquals("%PDF-", header, "Generated bytes must start with the PDF magic header");
    }
}
