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

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How one outbound dependency is probed for the startup report (UC-007 step 5, BR-003): a
 * built-in report is up without a probe, an address is GET-probed or TCP-connected, and every
 * failure becomes a DOWN or UNKNOWN line rather than an exception. The reachable targets are
 * servers this test starts on the loopback interface; nothing leaves the machine.
 */
class ConnectionChecksProbeTest {

    private final ConnectionChecks checks = new ConnectionChecks();
    private HttpServer server;

    @AfterEach
    void stop() {
        Thread.interrupted();
        if (server != null) {
            server.stop(0);
        }
    }

    private String serve(int status) throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/report";
    }

    private static ConnectionChecks.Target target(String address, ConnectionChecks.Kind kind) {
        return new ConnectionChecks.Target("Report service", "Pedigree (FHHS)", address, kind);
    }

    private static int closedPort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        }
    }

    @Test
    void given_aBuiltInReport_when_check_then_upWithoutAProbe() {
        CheckResult result = checks.check(target("/api/reports/respondent-summary", ConnectionChecks.Kind.BUILT_IN));

        assertEquals(CheckResult.Status.UP, result.status());
        assertEquals("Pedigree (FHHS)", result.name());
        assertEquals("built into Survey", result.detail());
    }

    @Test
    void given_aServiceAnswering200_when_check_then_upWithTheStatus() throws IOException {
        CheckResult result = checks.check(target(serve(200), ConnectionChecks.Kind.HTTP));

        assertEquals(CheckResult.Status.UP, result.status());
        assertEquals("reachable, HTTP 200", result.detail());
        assertTrue(result.durationMs() >= 0);
    }

    @Test
    void given_aServiceAnswering500_when_check_then_stillReachable() throws IOException {
        CheckResult result = checks.check(target(serve(500), ConnectionChecks.Kind.HTTP));

        assertEquals(CheckResult.Status.UP, result.status(), "any HTTP answer counts as reachable");
        assertEquals("reachable, HTTP 500", result.detail());
    }

    @Test
    void given_aServiceAnswering403_when_check_then_upWithTheLicenseHint() throws IOException {
        CheckResult result = checks.check(target(serve(403), ConnectionChecks.Kind.HTTP));

        assertEquals(CheckResult.Status.UP, result.status());
        assertEquals("reachable, HTTP 403: license validation may have failed", result.detail());
    }

    @Test
    void given_anInterruptedThread_when_checkHttp_then_downAndTheInterruptKept() throws IOException {
        String address = serve(200);
        Thread.currentThread().interrupt();

        CheckResult result = checks.check(target(address, ConnectionChecks.Kind.HTTP));

        assertEquals(CheckResult.Status.DOWN, result.status());
        assertEquals("interrupted", result.detail());
        assertTrue(Thread.interrupted(), "the interrupt is restored");
    }

    @Test
    void given_anAddressThatIsNotAUri_when_checkHttp_then_downWithTheReason() {
        CheckResult result = checks.check(target("http://bad host/", ConnectionChecks.Kind.HTTP));

        assertEquals(CheckResult.Status.DOWN, result.status());
        assertTrue(result.detail().contains("Illegal character"), result.detail());
    }

    @Test
    void given_anHttpTargetOnAClosedPort_when_check_then_down() throws IOException {
        CheckResult result = checks.check(target("http://127.0.0.1:" + closedPort() + "/", ConnectionChecks.Kind.HTTP));

        assertEquals(CheckResult.Status.DOWN, result.status());
        assertEquals("Pedigree (FHHS)", result.name());
    }

    @Test
    void given_aListeningCollector_when_checkTcp_then_connected() throws IOException {
        try (ServerSocket collector = new ServerSocket(0, 50, InetAddress.getLoopbackAddress())) {
            int port = collector.getLocalPort();

            CheckResult bare = checks.check(target("127.0.0.1:" + port, ConnectionChecks.Kind.TCP));
            CheckResult withScheme = checks.check(target("http://127.0.0.1:" + port, ConnectionChecks.Kind.TCP));

            assertEquals(CheckResult.Status.UP, bare.status());
            assertEquals("connected to 127.0.0.1:" + port, bare.detail());
            assertEquals("connected to 127.0.0.1:" + port, withScheme.detail());
        }
    }

    @Test
    void given_aTcpTargetOnAClosedPort_when_check_then_downWithTheReason() throws IOException {
        CheckResult result = checks.check(target("127.0.0.1:" + closedPort(), ConnectionChecks.Kind.TCP));

        assertEquals(CheckResult.Status.DOWN, result.status());
        assertTrue(result.detail().toLowerCase().contains("refused"), result.detail());
    }

    @Test
    void given_anAddressWithoutAPort_when_checkTcp_then_unknown() {
        CheckResult bare = checks.check(target("collector", ConnectionChecks.Kind.TCP));
        CheckResult withScheme = checks.check(target("http://collector", ConnectionChecks.Kind.TCP));

        assertEquals(CheckResult.unknown("Pedigree (FHHS)", "address collector has no host and port"), bare);
        assertEquals(CheckResult.Status.UNKNOWN, withScheme.status());
        assertEquals("address http://collector has no host and port", withScheme.detail());
    }

    @Test
    void given_anAddressWithoutAHost_when_checkTcp_then_unknown() {
        CheckResult result = checks.check(target("tcp://:4317", ConnectionChecks.Kind.TCP));

        assertEquals(CheckResult.Status.UNKNOWN, result.status());
    }

    @Test
    void given_portZero_when_checkTcp_then_itIsTriedNotRefusedAsUnaddressed() {
        CheckResult result = checks.check(target("127.0.0.1:0", ConnectionChecks.Kind.TCP));

        assertEquals(CheckResult.Status.DOWN, result.status(), result.detail());
    }

    private static String reason(Exception e) throws Exception {
        Method reason = ConnectionChecks.class.getDeclaredMethod("reason", Exception.class);
        reason.setAccessible(true);
        return (String) reason.invoke(null, e);
    }

    @Test
    void given_aSocketTimeout_when_reason_then_theBoundIsNamed() throws Exception {
        assertEquals("timed out after 5 s", reason(new SocketTimeoutException("Read timed out")));
    }

    @Test
    void given_aFailureWithoutMessage_when_reason_then_itsTypeIsNamed() throws Exception {
        assertEquals("IOException", reason(new IOException()));
        assertEquals("IOException", reason(new IOException("  ")));
        assertEquals("no route", reason(new IOException("no route")));
    }
}
