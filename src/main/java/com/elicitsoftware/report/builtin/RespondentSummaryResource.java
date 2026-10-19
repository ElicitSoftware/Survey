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
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Optional;

/**
 * The built-in respondent report over HTTP (UC-014 A1): the same {@code ReportRequest} /
 * {@code ReportResponse} contract every report service implements, so the Admin module's print
 * reaches it through the path it resolves under its Survey address (Admin UC-005 BR-023). The
 * report page never calls this; it dispatches the path in process (UC-005 BR-015).
 * <p>
 * Like Survey's other {@code /api} paths this one carries no authentication and is meant for the
 * Admin module on the internal network; it returns one respondent's answers, so a deployment
 * that publishes Survey's {@code /api} paths must keep it behind the same boundary as the rest.
 */
@Path(RespondentSummaryService.PATH)
public class RespondentSummaryResource {

    @Inject
    RespondentSummaryService service;

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response render(ReportRequest request) {
        if (request == null) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        Optional<ReportResponse> report = service.render(request, "HTTP");
        return report.map(r -> Response.ok(r).build())
                .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build());
    }
}
