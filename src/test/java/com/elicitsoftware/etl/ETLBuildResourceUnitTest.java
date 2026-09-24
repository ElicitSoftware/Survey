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

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-008 (Rebuild Reporting Schema) without a database: the status-to-HTTP mapping of
 * {@link ETLBuildResource}, the disabled branch of {@link ETLService#rebuildReportingSchema()}
 * (A1, which needs {@code elicit.etl.enabled=false} and so cannot run in the shared test JVM),
 * the root-cause unwrapping the failure message relies on (A2), and the JSON body's escaping.
 * Same precedent as {@code PDFDownloadResourceTest}: the resource has no request-scoped
 * dependency, so it is called directly.
 */
class ETLBuildResourceUnitTest {

    private static ETLBuildResource resourceAnswering(ETLService.RebuildStatus status, String message) {
        ETLBuildResource resource = new ETLBuildResource();
        resource.etlService = new ETLService() {
            @Override
            public RebuildResult rebuildReportingSchema() {
                return new RebuildResult(status, message);
            }
        };
        return resource;
    }

    @Test
    // UC-008 main scenario: OK maps to 200 with status "ok" and the summary.
    void given_ok_when_build_then_200() {
        Response response = resourceAnswering(ETLService.RebuildStatus.OK, "built 3 things").build();

        assertEquals(200, response.getStatus());
        assertEquals("{\"status\":\"ok\",\"message\":\"built 3 things\"}", response.getEntity());
        assertEquals("application/json", response.getMediaType().toString());
    }

    @Test
    // UC-008 A1: DISABLED maps to 409 so the caller can tell "off by configuration" from "broken".
    void given_disabled_when_build_then_409() {
        Response response = resourceAnswering(ETLService.RebuildStatus.DISABLED,
                "Reporting ETL is disabled (elicit.etl.enabled=false)").build();

        assertEquals(409, response.getStatus());
        assertEquals("{\"status\":\"disabled\",\"message\":\"Reporting ETL is disabled (elicit.etl.enabled=false)\"}",
                response.getEntity());
    }

    @Test
    // UC-008 A2: FAILED maps to 500 with the message intact.
    void given_failed_when_build_then_500() {
        Response response = resourceAnswering(ETLService.RebuildStatus.FAILED,
                "ERROR: duplicate key value violates unique constraint \"dim_step_un\"").build();

        assertEquals(500, response.getStatus());
        assertEquals("{\"status\":\"failed\",\"message\":\"ERROR: duplicate key value violates unique constraint \\\"dim_step_un\\\"\"}",
                response.getEntity());
    }

    @Test
    // UC-008 A1: with elicit.etl.enabled=false the service answers DISABLED before touching
    // the database (this instance has none injected, so any query would throw).
    void given_etlDisabled_when_rebuild_then_disabledWithoutTouchingTheDatabase() {
        ETLService service = new ETLService();
        service.etlEnabled = false;

        ETLService.RebuildResult result = service.rebuildReportingSchema();

        assertEquals(ETLService.RebuildStatus.DISABLED, result.status());
        assertEquals("Reporting ETL is disabled (elicit.etl.enabled=false)", result.message());
    }

    @Test
    // UC-008 A2 / BR-003: the driver's message is the one that names the constraint, and it
    // sits two wrappers down (DatabaseRetryUtil's RuntimeException, then PersistenceException).
    void rootMessage_unwrapsToTheInnermostCause() {
        SQLException driver = new SQLException("ERROR: duplicate key value violates unique constraint \"dim_step_un\"", "23505");
        RuntimeException wrapped = new RuntimeException("Non-retriable exception during updating step dimension table",
                new jakarta.persistence.PersistenceException("could not execute statement", driver));

        assertEquals(driver.getMessage(), ETLService.rootMessage(wrapped));
    }

    @Test
    // UC-008: an exception with no message still yields something an operator can search for.
    void rootMessage_fallsBackToTheClassName() {
        assertEquals("java.lang.NullPointerException", ETLService.rootMessage(new NullPointerException()));
    }

    @Test
    // The body is hand-written JSON; quotes, backslashes and newlines in a database message
    // must not break it.
    void json_escapesControlCharactersQuotesAndBackslashes() {
        String json = ETLBuildResource.json("failed", "line one\nquote \" backslash \\ tab\t");

        assertEquals("{\"status\":\"failed\",\"message\":\"line one\\nquote \\\" backslash \\\\ tab\\t\"}", json);
        assertTrue(json.indexOf('\n') < 0, "no raw newline may survive");
    }
}
