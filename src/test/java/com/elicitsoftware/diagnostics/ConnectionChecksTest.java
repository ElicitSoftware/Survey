package com.elicitsoftware.diagnostics;

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

import com.elicitsoftware.PostgresTestResource;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Probes are harmless, bounded, and read the right answer from each kind of target.
 *
 * <p>Traceability: UC-007 step 5, A1, NFR-009.</p>
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class ConnectionChecksTest {

    private static HttpServer server;
    private static String base;

    @Inject
    ConnectionChecks checks;

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/ok", exchange -> respond(exchange, 200, "ok"));
        server.createContext("/forbidden", exchange -> respond(exchange, 403, "no license"));
        server.createContext("/slow", exchange -> {
            try {
                Thread.sleep(ConnectionChecks.TIMEOUT.toMillis() + 2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            respond(exchange, 200, "late");
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterAll
    static void stopServer() {
        server.stop(0);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    /** UC-007 step 5: every report service and post-survey action in the database is a target. */
    @Test
    void targetsComeFromTheReportAndPostSurveyActionRows() {
        List<ConnectionChecks.Target> targets = checks.targets();

        assertTrue(targets.stream().anyMatch(t -> t.group().equals("Report service")
                && t.name().startsWith("Patron Checkout Summary")
                && t.address().equals("http://localhost:8080/survey-summary/report")), targets.toString());
        assertTrue(targets.stream().anyMatch(t -> t.group().equals("Post-survey action")
                && t.name().startsWith("Notify ILS System")
                && t.address().equals("http://localhost:8080/api/ils/notify")), targets.toString());
        // %test.quarkus.otel.enabled=false: a collector that is not exporting to is not probed.
        assertFalse(targets.stream().anyMatch(t -> t.group().equals("Telemetry collector")), targets.toString());
    }

    /** UC-007 step 5: any HTTP answer counts as reachable. */
    @Test
    void httpTargetThatAnswersIsUp() {
        CheckResult result = checks.check(new ConnectionChecks.Target("Report service", "t", base + "/ok",
                ConnectionChecks.Kind.HTTP));

        assertTrue(result.isUp(), result.detail());
        assertTrue(result.detail().contains("HTTP 200"));
    }

    /** UC-007 step 5: a 403 is reachable, with the licensing hint UC-004 A1 records. */
    @Test
    void forbiddenAnswerIsUpWithLicenseHint() {
        CheckResult result = checks.check(new ConnectionChecks.Target("Post-survey action", "t", base + "/forbidden",
                ConnectionChecks.Kind.HTTP));

        assertTrue(result.isUp(), result.detail());
        assertTrue(result.detail().contains("license"), result.detail());
    }

    /** UC-007 A1 / NFR-009: a target that does not answer in time is reported as timed out. */
    @Test
    void slowTargetTimesOut() {
        CheckResult result = checks.check(new ConnectionChecks.Target("Report service", "t", base + "/slow",
                ConnectionChecks.Kind.HTTP));

        assertFalse(result.isUp());
        assertTrue(result.detail().contains("timed out"), result.detail());
        assertTrue(result.durationMs() < ConnectionChecks.TIMEOUT.toMillis() + 1500, "the probe must give up at the bound");
    }

    /** UC-007 A1: a refused connection is down with the reason, not an exception. */
    @Test
    void refusedTargetIsDown() throws IOException {
        int closed;
        try (ServerSocket socket = new ServerSocket(0)) {
            closed = socket.getLocalPort();
        }
        CheckResult result = checks.check(new ConnectionChecks.Target("Report service", "t",
                "http://127.0.0.1:" + closed + "/", ConnectionChecks.Kind.HTTP));

        assertEquals(CheckResult.Status.DOWN, result.status());
        assertFalse(result.detail().isBlank());
    }

    /** UC-007 step 5: a socket target is connected to and closed. */
    @Test
    void tcpTargetIsUpWhenListeningAndDownWhenClosed() throws IOException {
        int listening = server.getAddress().getPort();
        int closed;
        try (ServerSocket socket = new ServerSocket(0)) {
            closed = socket.getLocalPort();
        }

        CheckResult up = checks.check(new ConnectionChecks.Target("Telemetry collector", "otlp",
                "http://127.0.0.1:" + listening, ConnectionChecks.Kind.TCP));
        CheckResult down = checks.check(new ConnectionChecks.Target("Telemetry collector", "otlp",
                "http://127.0.0.1:" + closed, ConnectionChecks.Kind.TCP));

        assertTrue(up.isUp(), up.detail());
        assertEquals(CheckResult.Status.DOWN, down.status());
    }

    /** UC-007 A1: an address without a port cannot be socket-probed and says so. */
    @Test
    void tcpTargetWithoutPortIsUnknown() {
        CheckResult result = checks.check(new ConnectionChecks.Target("Telemetry collector", "otlp",
                "http://collector", ConnectionChecks.Kind.TCP));

        assertEquals(CheckResult.Status.UNKNOWN, result.status());
    }
}
