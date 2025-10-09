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

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * ReportService is a RESTful client service interface to handle report-related operations.
 * <p>
 * This interface represents a REST client that can consume a JSON payload through a POST request
 * to retrieve a report in the form of a {@link ReportResponse}. It is designed to interact with
 * an external service that generates and provides report-related data.
 * <p>
 * Error handling:
 * The service implementations may throw {@link jakarta.ws.rs.WebApplicationException} for various
 * error conditions including validation errors, database errors, and service communication failures.
 * These exceptions contain detailed error messages that should be handled appropriately by calling services.
 * 
 * @throws jakarta.ws.rs.WebApplicationException for validation errors, service errors, or communication failures
 */
@Path("/")
@RegisterRestClient
public interface ReportService {
    /**
     * Calls the external report service to generate a report based on the provided request.
     * 
     * @param request The report request containing parameters for report generation
     * @return ReportResponse containing the generated report data
     * @throws jakarta.ws.rs.WebApplicationException if validation fails, service errors occur, or communication fails
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    ReportResponse callReport(ReportRequest request);
}
