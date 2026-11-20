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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The status-to-HTTP mapping and the JSON bodies of {@link ETLSchemaResource}, the rename
 * (UC-010) and drop (UC-011) endpoints, without a database: the service is replaced by one that
 * answers a fixed result and records what it was asked. What the service does is covered by
 * {@code ETLServiceStubbedDatabaseTest}; the endpoints over HTTP by {@code ETLSchemaResourceTest}.
 */
class ETLSchemaResourceUnitTest {

    private static final UUID KEY = UUID.fromString("11111111-2222-3333-4444-555555555555");

    private final List<String> asked = new ArrayList<>();

    private ETLSchemaResource resource(ETLService.RenameResult rename, ETLService.DropResult drop) {
        ETLSchemaResource resource = new ETLSchemaResource();
        resource.etlService = new ETLService() {
            @Override
            public RenameResult renameReportingSchema(UUID surveyKey, String newName) {
                asked.add("rename " + surveyKey + " " + newName);
                return rename;
            }

            @Override
            public DropResult dropReportingSchema(UUID surveyKey) {
                asked.add("drop " + surveyKey);
                return drop;
            }
        };
        return resource;
    }

    @ParameterizedTest
    @CsvSource({"OK,200", "INVALID,400", "UNKNOWN,404", "TAKEN,409", "UNBUILT,409", "FAILED,500"})
    void given_aRenameOutcome_when_rename_then_itsHttpStatusAndLowerCaseName(ETLService.RenameStatus status, int http) {
        Response response = resource(new ETLService.RenameResult(status, "said \"so\"", "report_new"), null)
                .rename(KEY.toString(), "report_new");

        assertEquals(http, response.getStatus());
        assertEquals("{\"status\":\"" + status.name().toLowerCase() + "\",\"message\":\"said \\\"so\\\"\",\"schema\":\"report_new\"}",
                response.getEntity());
        assertEquals("application/json", response.getMediaType().toString());
    }

    @Test
    void given_noSchemaInTheResult_when_rename_then_schemaIsJsonNull() {
        Response response = resource(new ETLService.RenameResult(ETLService.RenameStatus.UNKNOWN, "No survey has the key x", null), null)
                .rename(KEY.toString(), "report_new");

        assertEquals("{\"status\":\"unknown\",\"message\":\"No survey has the key x\",\"schema\":null}", response.getEntity());
    }

    @Test
    void given_aPaddedKeyAndName_when_rename_then_bothAreTrimmedBeforeTheServiceIsAsked() {
        resource(new ETLService.RenameResult(ETLService.RenameStatus.OK, "ok", "report_new"), null)
                .rename("  " + KEY + " ", "  report_new  ");

        assertEquals(List.of("rename " + KEY + " report_new"), asked);
    }

    @Test
    void given_noName_when_rename_then_theServiceIsAskedWithNull() {
        resource(new ETLService.RenameResult(ETLService.RenameStatus.INVALID, "A schema name is required.", "report_demo"), null)
                .rename(KEY.toString(), null);

        assertEquals(List.of("rename " + KEY + " null"), asked);
    }

    @Test
    void given_aKeyThatIsNotAUuid_when_rename_then_400WithoutAskingTheService() {
        Response response = resource(null, null).rename("not-a-uuid", "report_new");

        assertEquals(400, response.getStatus());
        assertEquals("{\"status\":\"invalid\",\"message\":\"surveyKey must be a survey key (UUID): not-a-uuid\"}", response.getEntity());
        assertTrue(asked.isEmpty());
    }

    @ParameterizedTest
    @CsvSource({"OK,200", "DISABLED,403", "UNKNOWN,404", "FAILED,500"})
    void given_aDropOutcome_when_drop_then_itsHttpStatusAndLowerCaseName(ETLService.DropStatus status, int http) {
        Response response = resource(null, new ETLService.DropResult(status, "done")).drop(KEY.toString());

        assertEquals(http, response.getStatus());
        assertEquals("{\"status\":\"" + status.name().toLowerCase() + "\",\"message\":\"done\"}", response.getEntity());
        assertEquals(List.of("drop " + KEY), asked);
    }

    @Test
    void given_aKeyThatIsNotAUuid_when_drop_then_400WithoutAskingTheService() {
        Response response = resource(null, null).drop("12345");

        assertEquals(400, response.getStatus());
        assertEquals("{\"status\":\"invalid\",\"message\":\"surveyKey must be a survey key (UUID): 12345\"}", response.getEntity());
        assertTrue(asked.isEmpty());
    }
}
