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
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.UUID;

/**
 * A survey's reporting schema as a resource, {@code /api/etl/schema/{surveyKey}}:
 * <ul>
 * <li>{@code POST .../rename?name=<new_name>} renames it (UC-010). Answers {@code 200 ok}
 * with the new name in {@code schema}, {@code 400 invalid}, {@code 404 unknown},
 * {@code 409 taken} or {@code 409 unbuilt}, or {@code 500 failed}.</li>
 * <li>{@code DELETE} drops it with everything in it (UC-011), when
 * {@code elicit.etl.drop.enabled=true}. Answers {@code 200 ok} (also when there was nothing to
 * drop), {@code 403 disabled}, {@code 404 unknown} or {@code 500 failed}.</li>
 * </ul>
 * Both take no body and answer the same hand-written JSON as {@link ETLBuildResource}. Both are
 * unauthenticated like every REST path Survey exposes (UC-008 BR-004); the drop is therefore
 * off unless a deployment turns it on (UC-011 BR-001).
 */
@Path("/api/etl/schema/{surveyKey}")
public class ETLSchemaResource {

    @Inject
    ETLService etlService;

    @POST
    @Path("/rename")
    @Produces(MediaType.APPLICATION_JSON)
    public Response rename(@PathParam("surveyKey") String surveyKey, @QueryParam("name") String name) {
        UUID key = parse(surveyKey);
        if (key == null) {
            return ETLBuildResource.respond(Response.Status.BAD_REQUEST, "invalid", "surveyKey must be a survey key (UUID): " + surveyKey);
        }
        ETLService.RenameResult result = etlService.renameReportingSchema(key, name == null ? null : name.trim());
        Response.Status http = switch (result.status()) {
            case OK -> Response.Status.OK;
            case INVALID -> Response.Status.BAD_REQUEST;
            case UNKNOWN -> Response.Status.NOT_FOUND;
            case TAKEN, UNBUILT -> Response.Status.CONFLICT;
            case FAILED -> Response.Status.INTERNAL_SERVER_ERROR;
        };
        String body = "{\"status\":\"" + ETLBuildResource.escape(result.status().name().toLowerCase())
                + "\",\"message\":\"" + ETLBuildResource.escape(result.message())
                + "\",\"schema\":" + (result.schema() == null ? "null" : "\"" + ETLBuildResource.escape(result.schema()) + "\"") + "}";
        return Response.status(http).type(MediaType.APPLICATION_JSON).entity(body).build();
    }

    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    public Response drop(@PathParam("surveyKey") String surveyKey) {
        UUID key = parse(surveyKey);
        if (key == null) {
            return ETLBuildResource.respond(Response.Status.BAD_REQUEST, "invalid", "surveyKey must be a survey key (UUID): " + surveyKey);
        }
        ETLService.DropResult result = etlService.dropReportingSchema(key);
        Response.Status http = switch (result.status()) {
            case OK -> Response.Status.OK;
            case DISABLED -> Response.Status.FORBIDDEN;
            case UNKNOWN -> Response.Status.NOT_FOUND;
            case FAILED -> Response.Status.INTERNAL_SERVER_ERROR;
        };
        return ETLBuildResource.respond(http, result.status().name().toLowerCase(), result.message());
    }

    private static UUID parse(String surveyKey) {
        try {
            return UUID.fromString(surveyKey.trim());
        } catch (RuntimeException e) {
            return null;
        }
    }
}
