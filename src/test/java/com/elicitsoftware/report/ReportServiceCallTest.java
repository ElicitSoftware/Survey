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

import com.sun.net.httpserver.HttpServer;
import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UC-005: View Reports & Download PDF — covers the "System calls each report configured
 * for the survey ... passing the respondent's identifier to the report's configured
 * external service endpoint" (main success scenario step 1) and A1 ("A report's external
 * service call fails ... including a special-cased, more specific message when the failure
 * looks like a licensing rejection from a known downstream service").
 * <p>
 * {@link ReportService} is a plain {@code @RegisterRestClient} interface with no CDI-managed
 * implementation to inject: production code (see {@code ReportView.callReport(ReportDefinition)})
 * builds a client instance per call via
 * {@code RestClientBuilder.newBuilder().baseUri(new URI(rpt.url)).build(ReportService.class)},
 * using a URL read from the {@code ReportDefinition.url} DB column rather than static
 * {@code quarkus.rest-client."X".url} config. This test therefore replicates
 * {@code ReportView.callReport()}'s exact error-branching logic locally (mirroring the
 * documented workaround for {@code @NormalUIScoped}/private-method logic used by
 * {@code QuestionServiceFinalizeTest} for the analogous UC-004 post-survey-action call) and
 * points the per-call REST client at a local {@code com.sun.net.httpserver.HttpServer} stub
 * instead of a real network dependency. No database access is required since
 * {@code ReportDefinition.url} is only read here as a plain String, not persisted.
 * {@code @QuarkusTest} is required (even though nothing is injected) because
 * {@code RestClientBuilder.newBuilder()} needs Quarkus's {@code RestClientBuilderResolver}
 * SPI implementation on the classpath/active, which is only wired up when the Quarkus
 * test extension bootstraps the application.
 */
@QuarkusTest
class ReportServiceCallTest {

    private final List<HttpServer> serversToStop = new ArrayList<>();

    @AfterEach
    void stopStubServers() {
        for (HttpServer server : serversToStop) {
            server.stop(0);
        }
        serversToStop.clear();
    }

    private HttpServer startStubServer(int statusCode, String responseBody, String contentType) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", contentType);
            exchange.sendResponseHeaders(statusCode, bytes.length == 0 ? -1 : bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                if (bytes.length > 0) {
                    os.write(bytes);
                }
            }
        });
        server.start();
        serversToStop.add(server);
        return server;
    }

    private String stubUrl(HttpServer server) {
        return "http://localhost:" + server.getAddress().getPort() + "/";
    }

    /**
     * Verbatim copy of {@code ReportView.callReport(ReportDefinition)}'s logic, parameterized on a
     * plain URL string and respondent id instead of a persisted {@code ReportDefinition}/{@code Respondent},
     * since neither entity needs to touch the database for this test to exercise the real call +
     * error-handling code path.
     */
    private ReportResponse callReport(String reportName, String url, int respondentId) {
        try {
            ReportRequest request = new ReportRequest(respondentId);
            ReportService reportService = RestClientBuilder.newBuilder()
                    .baseUri(new URI(url))
                    .build(ReportService.class);
            return reportService.callReport(request);
        } catch (jakarta.ws.rs.WebApplicationException e) {
            String errorMessage = "Service error: " + e.getMessage();

            if (e.getResponse() != null) {
                int status = e.getResponse().getStatus();

                if (e.getResponse().hasEntity()) {
                    try {
                        String responseBody = e.getResponse().readEntity(String.class);
                        if (responseBody != null && !responseBody.trim().isEmpty()) {
                            errorMessage = responseBody;
                        }
                    } catch (Exception readException) {
                        if (status == 403) {
                            errorMessage = "Access forbidden - License validation may have failed. Please check your license configuration.";
                        } else {
                            errorMessage = "Service error (HTTP " + status + "): " + e.getMessage();
                        }
                    }
                } else {
                    if (status == 403) {
                        errorMessage = "Access forbidden - License validation may have failed. Please check your license configuration.";
                    } else {
                        errorMessage = "Service error (HTTP " + status + "): " + e.getMessage();
                    }
                }
            }

            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("forbidden")) {
                if (!errorMessage.toLowerCase().contains("license")) {
                    errorMessage = "License validation failed - " + errorMessage;
                }
            }

            ReportResponse reportResponse = new ReportResponse();
            reportResponse.title = "Error - " + reportName;
            reportResponse.innerHTML = "<div style='color: red; padding: 20px; border: 1px solid red; background-color: #ffe6e6;'>" +
                    "<h3>Report Generation Error</h3>" +
                    "<p><strong>Service:</strong> " + reportName + "</p>" +
                    "<p><strong>Error:</strong> " + errorMessage + "</p>" +
                    "<p><em>If this is a license error, please ensure your PREMM5 license is valid and properly configured.</em></p>" +
                    "</div>";
            return reportResponse;
        } catch (Exception e) {
            ReportResponse reportResponse = new ReportResponse();
            reportResponse.title = "Error - " + reportName;
            reportResponse.innerHTML = "<div style='color: red; padding: 20px; border: 1px solid red; background-color: #ffe6e6;'>" +
                    "<h3>Report Generation Error</h3>" +
                    "<p><strong>Service:</strong> " + reportName + "</p>" +
                    "<p><strong>Error:</strong> " + e.getMessage() + "</p>" +
                    "</div>";
            return reportResponse;
        }
    }

    // ── Main success scenario step 1 ─────────────────────────────────────────

    @Test
    // UC-005 step 1/2: a successful external report call returns the report's title/HTML
    // content as-is, ready to be rendered in a ReportCard.
    void given_successfulReportService_when_callReport_then_reportResponseReturnedUnmodified() throws IOException {
        String json = "{\"title\":\"Patron Checkout Summary\",\"innerHTML\":\"<p>Checkout: 2 items</p>\"}";
        HttpServer stub = startStubServer(200, json, "application/json");

        ReportResponse response = callReport("Patron Checkout Summary", stubUrl(stub), 1);

        assertNotNull(response);
        assertEquals("Patron Checkout Summary", response.title);
        assertEquals("<p>Checkout: 2 items</p>", response.innerHTML);
    }

    // ── A1: generic external service failure ─────────────────────────────────

    @Test
    // UC-005 A1: a plain 500 failure is rendered as an errored card (title prefixed "Error -",
    // innerHTML containing the service name and a generic error), without throwing out to the caller.
    void given_serverError500_when_callReport_then_errorCardReturnedWithServiceName() throws IOException {
        HttpServer stub = startStubServer(500, "boom", "text/plain");

        ReportResponse response = callReport("Risk Assessment", stubUrl(stub), 1);

        assertNotNull(response);
        assertEquals("Error - Risk Assessment", response.title);
        assertTrue(response.innerHTML.contains("Risk Assessment"));
        assertTrue(response.innerHTML.contains("Report Generation Error"));
    }

    // ── A1: license-rejection special case ────────────────────────────────────

    @Test
    // UC-005 A1: a 403 whose response body already mentions a licensing rejection produces
    // an error card whose message is exactly that response body -- the "license" special
    // case does not need to add its own wording when the upstream body already covers it.
    void given_403WithLicenseBodyText_when_callReport_then_errorCardContainsOriginalLicenseMessage() throws IOException {
        HttpServer stub = startStubServer(403, "License check failed for PREMM5 calculator", "text/plain");

        ReportResponse response = callReport("PREMM5 Risk Calculator", stubUrl(stub), 1);

        assertNotNull(response);
        assertEquals("Error - PREMM5 Risk Calculator", response.title);
        assertTrue(response.innerHTML.toLowerCase().contains("license"),
                "License-flavored 403 body text must be surfaced in the error card: " + response.innerHTML);
        assertTrue(response.innerHTML.contains("PREMM5"),
                "The static PREMM5 guidance text must always be present in report error cards: " + response.innerHTML);
    }

    @Test
    // UC-005 A1: a bare 403 with no response body at all still gets the license-specific
    // guidance message rather than a generic "Service error (HTTP 403)" message.
    void given_403WithNoBody_when_callReport_then_errorCardUsesLicenseGuidanceMessage() throws IOException {
        HttpServer stub = startStubServer(403, "", "text/plain");

        ReportResponse response = callReport("Locked Report", stubUrl(stub), 1);

        assertNotNull(response);
        assertEquals("Error - Locked Report", response.title);
        assertTrue(response.innerHTML.toLowerCase().contains("license validation may have failed"),
                "A bodiless 403 must fall back to the license-specific guidance message: " + response.innerHTML);
    }

    // ── Network-level failure (service unreachable) ───────────────────────────

    @Test
    // UC-005 A1: "is unreachable" -- an invalid/unreachable base URL still produces a
    // rendered error card (via the generic Exception branch) rather than propagating and
    // taking down the whole ReportView for the other configured reports.
    void given_unreachableService_when_callReport_then_errorCardReturnedNotThrown() {
        // Port 1 is a reserved/unroutable port that should refuse or fail to connect quickly.
        ReportResponse response = callReport("Unreachable Service", "http://localhost:1/", 1);

        assertNotNull(response, "An unreachable service must still yield an error ReportResponse, not throw");
        assertEquals("Error - Unreachable Service", response.title);
        assertTrue(response.innerHTML.contains("Unreachable Service"));
    }
}
