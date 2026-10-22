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
package com.elicitsoftware.report.builtin;

import com.elicitsoftware.report.ReportRequest;
import com.elicitsoftware.report.ReportResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Map;
import java.util.Optional;

/**
 * The report services built into Survey, by the path a {@code survey.reports} row names them
 * with (UC-005 BR-015, UC-014 BR-001). A row whose address starts with {@code /} is one of these
 * and is called in process by the report page; the same service answers over HTTP at that path
 * for the Admin module. An absolute address is an external service and is never looked up here.
 */
@ApplicationScoped
public class BuiltInReports {

    /** One built-in report service. */
    public interface Service {
        /** The path the service answers at, as a {@code survey.reports.url} names it. */
        String path();

        /**
         * Renders the report, or returns empty when the request names no respondent.
         *
         * @param request the respondent and language
         * @param caller  who asked, for the log line (UC-014 BR-008)
         */
        Optional<ReportResponse> render(ReportRequest request, String caller);
    }

    @Inject
    RespondentSummaryService respondentSummary;

    private Map<String, Service> byPath;

    /** True when a report address names a built-in service rather than an external one. */
    public static boolean isPath(String url) {
        return url != null && url.startsWith("/") && !url.startsWith("//");
    }

    public Optional<Service> find(String url) {
        if (!isPath(url)) {
            return Optional.empty();
        }
        if (byPath == null) {
            byPath = Map.of(respondentSummary.path(), respondentSummary);
        }
        String path = url;
        int query = path.indexOf('?');
        if (query >= 0) {
            path = path.substring(0, query);
        }
        while (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return Optional.ofNullable(byPath.get(path));
    }
}
