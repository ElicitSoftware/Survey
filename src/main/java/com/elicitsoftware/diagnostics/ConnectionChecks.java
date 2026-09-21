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

import com.elicitsoftware.model.PostSurveyAction;
import com.elicitsoftware.model.ReportDefinition;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Bounded, harmless probes of Survey's outbound dependencies (UC-007 step 5): every report
 * service and post-survey action configured in the database, and the telemetry collector.
 * These are the calls a respondent's finalize and report views make, so a target that is
 * unreachable from where Survey runs is found at startup rather than by a respondent.
 * Every probe gives up after {@link #TIMEOUT} (NFR-009).
 * <p>
 * Report services and post-survey actions are only ever GET-probed here; the real calls are
 * POSTs that carry respondent data and are never made by a diagnostic.
 */
@ApplicationScoped
public class ConnectionChecks {

    /** The bound on every probe. */
    public static final Duration TIMEOUT = Duration.ofSeconds(5);

    /** How a target is probed. */
    public enum Kind {
        /** GET the address; any HTTP answer counts as reachable. */
        HTTP,
        /** Open a TCP connection to host:port. */
        TCP
    }

    /**
     * One outbound dependency.
     *
     * @param group   what kind of dependency
     * @param name    the row or setting it came from
     * @param address the address as configured
     * @param kind    how it is probed
     */
    public record Target(String group, String name, String address, Kind kind) {
    }

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public ConnectionChecks() {
        // CDI managed bean
    }

    /** Every target this deployment knows, in report order. */
    @ActivateRequestContext
    public List<Target> targets() {
        Config config = ConfigProvider.getConfig();
        List<Target> targets = new ArrayList<>();

        for (ReportDefinition report : ReportDefinition.<ReportDefinition>listAll()) {
            if (report.url != null && !report.url.isBlank()) {
                String survey = report.survey != null ? report.survey.name : "?";
                targets.add(new Target("Report service", report.name + " (" + survey + ")", report.url, Kind.HTTP));
            }
        }
        for (PostSurveyAction action : PostSurveyAction.<PostSurveyAction>listAll()) {
            if (action.url != null && !action.url.isBlank()) {
                String survey = action.survey != null ? action.survey.name : "?";
                targets.add(new Target("Post-survey action", action.name + " (" + survey + ")", action.url, Kind.HTTP));
            }
        }

        boolean otelEnabled = config.getOptionalValue("quarkus.otel.enabled", Boolean.class).orElse(true);
        if (otelEnabled) {
            value(config, "quarkus.otel.exporter.otlp.endpoint")
                    .or(() -> value(config, "quarkus.otel.exporter.otlp.traces.endpoint"))
                    .ifPresent(endpoint -> targets.add(new Target("Telemetry collector",
                            "quarkus.otel.exporter.otlp.endpoint", endpoint, Kind.TCP)));
        }
        return targets;
    }

    /** Probes one target within {@link #TIMEOUT}. */
    public CheckResult check(Target target) {
        long start = System.nanoTime();
        try {
            return switch (target.kind()) {
                case HTTP -> http(target, start);
                case TCP -> tcp(target, start);
            };
        } catch (HttpTimeoutException e) {
            return CheckResult.down(target.name(), "timed out after " + TIMEOUT.toSeconds() + " s", elapsed(start));
        } catch (IOException | RuntimeException e) {
            return CheckResult.down(target.name(), reason(e), elapsed(start));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return CheckResult.down(target.name(), "interrupted", elapsed(start));
        }
    }

    private CheckResult http(Target target, long start) throws IOException, InterruptedException {
        HttpResponse<Void> response = client.send(get(URI.create(target.address())), HttpResponse.BodyHandlers.discarding());
        long ms = elapsed(start);
        if (response.statusCode() == HttpURLConnection.HTTP_FORBIDDEN) {
            // The PREMM5 service answers 403 when its license is missing or expired (UC-004 A1).
            return CheckResult.up(target.name(), "reachable, HTTP 403: license validation may have failed", ms);
        }
        return CheckResult.up(target.name(), "reachable, HTTP " + response.statusCode(), ms);
    }

    private static CheckResult tcp(Target target, long start) throws IOException {
        URI uri = target.address().contains("://") ? URI.create(target.address()) : URI.create("tcp://" + target.address());
        String host = uri.getHost();
        int port = uri.getPort();
        if (host == null || port < 0) {
            return CheckResult.unknown(target.name(), "address " + target.address() + " has no host and port");
        }
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), (int) TIMEOUT.toMillis());
        }
        return CheckResult.up(target.name(), "connected to " + host + ":" + port, elapsed(start));
    }

    private static HttpRequest get(URI uri) {
        return HttpRequest.newBuilder(uri).GET().timeout(TIMEOUT).build();
    }

    private static long elapsed(long start) {
        return (System.nanoTime() - start) / 1_000_000;
    }

    private static String reason(Exception e) {
        if (e instanceof java.net.SocketTimeoutException) {
            return "timed out after " + TIMEOUT.toSeconds() + " s";
        }
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }

    private static Optional<String> value(Config config, String property) {
        try {
            return config.getOptionalValue(property, String.class).filter(v -> !v.isBlank());
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }
}
