package com.elicitsoftware.etl;

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

import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Optional;
import java.util.UUID;

/**
 * {@code POST /api/etl/build?survey=<survey_key>}: builds one survey's reporting star schema
 * (UC-008), so a survey definition the Admin module has just installed or updated gets its own
 * schema, dimensions, fact columns and views without a restart of this application. Without
 * {@code survey}, every installed survey is built in turn (A5).
 * <p>
 * The endpoint takes no body and answers JSON, {@code {"status": ..., "message": ...}}:
 * <ul>
 * <li>{@code 200 ok} -- the build ran; the message summarizes what it did, one line per survey.</li>
 * <li>{@code 400 invalid} -- {@code survey} is not a UUID.</li>
 * <li>{@code 404 unknown} -- no survey has that key.</li>
 * <li>{@code 409 disabled} -- {@code elicit.etl.enabled=false} on this instance.</li>
 * <li>{@code 500 failed} -- a build threw; the message names the survey and the root cause.
 * The failure is logged here and never propagates.</li>
 * </ul>
 * The call is idempotent -- it runs the same build the application runs at startup, every
 * step of which only creates what is missing or upserts by durable key -- and synchronous:
 * the response comes back when the build is done, which for a survey with many respondents
 * still missing fact rows can take a while. Concurrent calls are serialized.
 * <p>
 * Survey has no authentication on its REST endpoints (it authenticates respondents by access
 * code in the UI only). This endpoint is intended for the Admin application on the internal
 * network, next to the report and post-survey-action services Survey itself calls; it exposes
 * nothing about respondents and can do nothing a restart would not, but a deployment that
 * publishes Survey's {@code /api} paths to the internet should keep this one behind the same
 * boundary as the others.
 */
@Path("/api/etl/build")
public class ETLBuildResource {

    @Inject
    ETLService etlService;

    /**
     * Runs the reporting schema build and reports the outcome.
     *
     * @param survey the portable key of the survey to build, or null for every survey
     * @return 200, 400, 404, 409 or 500 with a {@code status}/{@code message} JSON body
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Response build(@QueryParam("survey") String survey) {
        Optional<UUID> key;
        try {
            key = survey == null || survey.isBlank() ? Optional.empty() : Optional.of(UUID.fromString(survey.trim()));
        } catch (IllegalArgumentException e) {
            return respond(Response.Status.BAD_REQUEST, "invalid", "survey must be a survey key (UUID): " + survey);
        }
        ETLService.RebuildResult result = etlService.rebuildReportingSchema(key);
        Response.Status http = switch (result.status()) {
            case OK -> Response.Status.OK;
            case DISABLED -> Response.Status.CONFLICT;
            case UNKNOWN -> Response.Status.NOT_FOUND;
            case FAILED -> Response.Status.INTERNAL_SERVER_ERROR;
        };
        return respond(http, result.status().name().toLowerCase(), result.message());
    }

    static Response respond(Response.Status http, String status, String message) {
        return Response.status(http)
                .type(MediaType.APPLICATION_JSON)
                .entity(json(status, message))
                .build();
    }

    /**
     * The two-field body, written by hand so Survey does not need a server-side JSON provider
     * for one endpoint (its REST dependencies are client-side only).
     */
    static String json(String status, String message) {
        return "{\"status\":\"" + escape(status) + "\",\"message\":\"" + escape(message) + "\"}";
    }

    static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }
}
