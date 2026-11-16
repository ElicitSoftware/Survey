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
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.w3c.dom.svg.SVGDocument;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-005 BR-019: a report's drawing is plain SVG. A drawing that declares entities, or whose
 * document type carries an internal subset, or that is longer than the limit, is refused before
 * it is parsed; a drawing with no document type, or with one that only names the SVG DTD, is
 * parsed as before.
 */
class PDFServiceSvgParsingTest {

    private static final String SVG_OPEN = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"100\" height=\"50\">";

    @Test
    void aPlainDrawingIsParsed() throws IOException {
        SVGDocument document = PDFService.parseSvg("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + SVG_OPEN + "<text x=\"5\" y=\"20\">pedigree</text></svg>");

        assertEquals("pedigree", document.getDocumentElement().getTextContent());
    }

    @Test
    void aDocumentTypeThatOnlyNamesTheSvgDtdIsParsed() throws IOException {
        SVGDocument document = PDFService.parseSvg("<?xml version=\"1.0\"?>\n"
                + "<!DOCTYPE svg PUBLIC \"-//W3C//DTD SVG 1.1//EN\" \"http://www.w3.org/Graphics/SVG/1.1/DTD/svg11.dtd\">\n"
                + SVG_OPEN + "<text>plain</text></svg>");

        assertEquals("plain", document.getDocumentElement().getTextContent());
    }

    @Test
    void aDrawingThatDeclaresAnExternalHttpEntityIsRefusedAndNothingIsFetched() throws Exception {
        AtomicBoolean connected = new AtomicBoolean();
        try (ServerSocket server = new ServerSocket(0)) {
            Thread accept = new Thread(() -> {
                try (Socket socket = server.accept()) {
                    connected.set(true);
                } catch (IOException closed) {
                    // The test ended without a connection.
                }
            });
            accept.setDaemon(true);
            accept.start();
            String svg = "<!DOCTYPE svg [<!ENTITY x SYSTEM \"http://127.0.0.1:" + server.getLocalPort() + "/hit\">]>"
                    + SVG_OPEN + "<text>&x;</text></svg>";

            assertThrows(IOException.class, () -> PDFService.parseSvg(svg));

            Thread.sleep(300);
            assertFalse(connected.get(), "no connection is made for a refused drawing");
        }
    }

    @Test
    void aDrawingThatDeclaresAnExternalFileEntityIsRefused() throws Exception {
        Path file = Files.createTempFile("drawing-entity", ".txt");
        try {
            Files.writeString(file, "FILE-CONTENT");
            String svg = "<!DOCTYPE svg [<!ENTITY x SYSTEM \"" + file.toUri() + "\">]>"
                    + SVG_OPEN + "<text>&x;</text></svg>";

            IOException refused = assertThrows(IOException.class, () -> PDFService.parseSvg(svg));
            assertFalse(String.valueOf(refused.getMessage()).contains("FILE-CONTENT"));
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    void aDrawingThatDeclaresNestedEntitiesIsRefusedBeforeAnyExpansion() {
        StringBuilder svg = new StringBuilder("<!DOCTYPE svg [<!ENTITY a0 \"aaaaaaaaaa\">");
        for (int level = 1; level <= 4; level++) {
            svg.append("<!ENTITY a").append(level).append(" \"");
            for (int i = 0; i < 10; i++) {
                svg.append("&a").append(level - 1).append(';');
            }
            svg.append("\">");
        }
        svg.append("]>").append(SVG_OPEN).append("<text>&a4;</text></svg>");

        assertThrows(IOException.class, () -> PDFService.parseSvg(svg.toString()));
    }

    @Test
    void theDeclarationIsRecognizedWhateverItsCaseAndSpacing() {
        for (String svg : List.of(
                "<!doctype svg [ <!entity x \"y\"> ]>" + SVG_OPEN + "</svg>",
                "<!DOCTYPE svg\n  [\n<!ELEMENT svg ANY>\n]>" + SVG_OPEN + "</svg>",
                "<?xml version=\"1.0\"?><!DOCTYPE svg PUBLIC \"-//W3C//DTD SVG 1.1//EN\" \"svg11.dtd\" [<!ENTITY x \"y\">]>"
                        + SVG_OPEN + "</svg>")) {
            assertThrows(IOException.class, () -> PDFService.parseSvg(svg), svg);
        }
    }

    @Test
    void aDrawingLongerThanTheLimitIsRefused() throws IOException {
        String padding = "<!-- " + "x".repeat(PDFService.MAX_SVG_LENGTH) + " -->";

        IOException refused = assertThrows(IOException.class,
                () -> PDFService.parseSvg(SVG_OPEN + padding + "</svg>"));
        assertTrue(refused.getMessage().contains(String.valueOf(PDFService.MAX_SVG_LENGTH)));
    }

    @Test
    void aReportWhoseDrawingIsRefusedFailsLikeOneWhoseDrawingIsNotSvg() {
        PDFService service = new PDFService();
        service.request = (HttpServletRequest) Proxy.newProxyInstance(HttpServletRequest.class.getClassLoader(),
                new Class<?>[]{HttpServletRequest.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getScheme" -> "http";
                    case "getServerName" -> "localhost";
                    case "getServerPort" -> 8080;
                    case "getContextPath" -> "";
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        Content drawing = new Content();
        drawing.svg = "<!DOCTYPE svg [<!ENTITY x \"y\">]>" + SVG_OPEN + "<text>&x;</text></svg>";
        PDFDocument pdf = new PDFDocument();
        pdf.title = "Pedigree";
        pdf.content = new Content[]{drawing};
        ArrayList<ReportResponse> reports = new ArrayList<>(List.of(new ReportResponse("Pedigree", "<p/>", pdf)));

        RuntimeException failure = assertThrows(RuntimeException.class,
                () -> service.generatePDF(reports, "Family History", Locale.ENGLISH));
        assertTrue(failure.getCause() instanceof IOException);
    }
}
