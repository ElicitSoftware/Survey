package com.elicitsoftware.report;

/*-
 * ***LICENSE_START***
 * Elicit Survey
 * %%
 * Copyright (C) 2026 The Regents of the University of Michigan - Rogel Cancer Center
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import com.sun.net.httpserver.HttpServer;
import org.apache.batik.anim.dom.SAXSVGDocumentFactory;
import org.apache.batik.bridge.BridgeContext;
import org.apache.batik.bridge.GVTBuilder;
import org.apache.batik.bridge.UserAgent;
import org.apache.batik.bridge.UserAgentAdapter;
import org.apache.batik.util.XMLResourceDescriptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.svg.SVGDocument;

import java.io.StringReader;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-005: an SVG from a report service is drawn into the PDF without the server fetching anything
 * the SVG names. A local server counts the requests that rendering makes.
 */
class NoExternalResourcesUserAgentTest {

    private HttpServer server;
    private final AtomicInteger requests = new AtomicInteger();

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private String address(String path) {
        return "http://localhost:" + server.getAddress().getPort() + path;
    }

    /**
     * Renders with a document URL on the same origin as the resources: the case in which Batik's
     * default same-origin policy would fetch them. (With no document URL, which is how the PDF
     * services parse today, the default policy already refuses; the agent must not depend on it.)
     */
    private void render(String svg, UserAgent agent) {
        try {
            SAXSVGDocumentFactory factory =
                    new SAXSVGDocumentFactory(XMLResourceDescriptor.getXMLParserClassName());
            SVGDocument document = factory.createSVGDocument(address("/report.svg"), new StringReader(svg));
            BridgeContext ctx = new BridgeContext(agent);
            ctx.setDynamicState(BridgeContext.STATIC);
            new GVTBuilder().build(ctx, document);
        } catch (Exception | Error refused) {
            // A refused resource may abort the build; what matters is that nothing was fetched.
        }
    }

    private String svgWithExternalImage() {
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\""
                + " width=\"10\" height=\"10\">"
                + "<image width=\"10\" height=\"10\" xlink:href=\"" + address("/ssrf.png") + "\"/></svg>";
    }

    @Test
    void defaultAgent_fetchesSameOriginImage_soTheTestCanSeeAFetch() {
        render(svgWithExternalImage(), new UserAgentAdapter());
        assertTrue(requests.get() > 0, "the default agent is expected to fetch, or this test proves nothing");
    }

    @Test
    void externalImage_isNeverFetched() {
        render(svgWithExternalImage(), new NoExternalResourcesUserAgent());
        assertEquals(0, requests.get());
    }

    @Test
    void externalUseReference_isNeverFetched() {
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\">"
                + "<use xlink:href=\"" + address("/other.svg#shape") + "\"/></svg>";
        render(svg, new NoExternalResourcesUserAgent());
        assertEquals(0, requests.get());
    }

    @Test
    void sameDocumentReference_stillRenders() {
        // The pedigree SVG draws its glyphs through <use xlink:href="#glyph...">.
        String svg = "<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\""
                + " width=\"10\" height=\"10\"><defs><rect id=\"glyph0-1\" width=\"4\" height=\"4\"/></defs>"
                + "<use xlink:href=\"#glyph0-1\" x=\"2\" y=\"2\"/></svg>";
        assertDoesNotThrow(() -> {
            SAXSVGDocumentFactory factory =
                    new SAXSVGDocumentFactory(XMLResourceDescriptor.getXMLParserClassName());
            SVGDocument document = factory.createSVGDocument(null, new StringReader(svg));
            BridgeContext ctx = new BridgeContext(new NoExternalResourcesUserAgent());
            ctx.setDynamicState(BridgeContext.STATIC);
            assertTrue(new GVTBuilder().build(ctx, document) != null);
        });
        assertEquals(0, requests.get());
    }
}
