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

import com.elicitsoftware.PostgresTestResource;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-010 (Rename Reporting Schema) and UC-011 (Drop Reporting Schema) over a real HTTP
 * round-trip. Each test installs a small survey of its own, builds it by key (UC-008) so it
 * has a schema, and removes everything in {@code finally}. The test profile sets
 * {@code elicit.etl.drop.enabled=true}; the disabled branch (UC-011 A2) is covered without a
 * database in {@link ETLBuildResourceUnitTest}.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class ETLSchemaResourceTest {

    @TestHTTPResource("/api/etl")
    URL etlUrl;

    @Inject
    EntityManager em;

    @PersistenceContext(unitName = "owner")
    EntityManager ownerEm;

    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private HttpResponse<String> send(String method, String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(etlUrl.toString() + path))
                .method(method, HttpRequest.BodyPublishers.noBody())
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(60))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private record Fixture(int id, UUID key) {
    }

    private Fixture install(String name, int displayOrder) throws IOException, InterruptedException {
        UUID key = UUID.randomUUID();
        Integer id = QuarkusTransaction.requiringNew().call(() -> {
            Integer surveyId = ((Number) em.createNativeQuery(
                    "INSERT INTO survey.surveys(id, name, display_order, title, description, initial_display_key, post_survey_url, survey_key) "
                            + "VALUES (NEXTVAL('survey.surveys_seq'), ?1, ?2, 'Schema resource fixture', 'UC-010/UC-011', NULL, NULL, ?3) RETURNING id")
                    .setParameter(1, name).setParameter(2, displayOrder).setParameter(3, key).getSingleResult()).intValue();
            em.createNativeQuery(
                    "INSERT INTO survey.steps(id, survey_id, display_order, name, dimension_name, description, step_key) "
                            + "VALUES (NEXTVAL('survey.steps_seq'), ?1, 1, 'Only step', 'OnlyStep', 'fixture', gen_random_uuid())")
                    .setParameter(1, surveyId).executeUpdate();
            return surveyId;
        });
        HttpResponse<String> built = send("POST", "/build?survey=" + key);
        assertEquals(200, built.statusCode(), built.body());
        return new Fixture(id, key);
    }

    private void remove(Fixture fixture, String... schemas) {
        QuarkusTransaction.requiringNew().run(() -> {
            em.createNativeQuery("DELETE FROM survey.steps WHERE survey_id = ?1").setParameter(1, fixture.id()).executeUpdate();
            em.createNativeQuery("DELETE FROM survey.surveys WHERE id = ?1").setParameter(1, fixture.id()).executeUpdate();
        });
        for (String schema : schemas) {
            QuarkusTransaction.requiringNew().run(() ->
                    ownerEm.createNativeQuery("DROP SCHEMA IF EXISTS " + schema + " CASCADE").executeUpdate());
        }
    }

    private String schemaOf(Fixture fixture) {
        return (String) em.createNativeQuery("SELECT report_schema FROM survey.surveys WHERE id = ?1")
                .setParameter(1, fixture.id()).getSingleResult();
    }

    private long schemaExists(String name) {
        return ((Number) ownerEm.createNativeQuery("SELECT COUNT(*) FROM pg_namespace WHERE nspname = ?1")
                .setParameter(1, name).getSingleResult()).longValue();
    }

    @Test
    // UC-010 main scenario / BR-001 / BR-002: the schema and the column change together, and
    // the views inside the schema still answer under the new name.
    void given_builtSurvey_when_rename_then_schemaAndColumnMoveAndViewsStillAnswer() throws Exception {
        Fixture fixture = install("Rename Me", 904);
        try {
            assertEquals("report_rename_me", schemaOf(fixture));

            HttpResponse<String> response = send("POST", "/schema/" + fixture.key() + "/rename?name=report_renamed");

            assertEquals(200, response.statusCode(), response.body());
            assertTrue(response.body().contains("\"schema\":\"report_renamed\""), response.body());
            assertEquals("report_renamed", schemaOf(fixture));
            assertEquals(1, schemaExists("report_renamed"));
            assertEquals(0, schemaExists("report_rename_me"));
            assertEquals(0, ((Number) ownerEm.createNativeQuery("SELECT COUNT(*) FROM report_renamed.fact_respondents_view")
                    .getSingleResult()).longValue(), "the views came along and still answer");
            assertEquals(1, ((Number) ownerEm.createNativeQuery("SELECT COUNT(*) FROM report_renamed.dim_step WHERE value = 'OnlyStep'")
                    .getSingleResult()).longValue(), "nothing in the schema was rebuilt");
        } finally {
            remove(fixture, "report_rename_me", "report_renamed");
        }
    }

    @Test
    // UC-010 A1, A2, A4: an invalid name, a taken name and an unknown key are refused and
    // nothing changes.
    void given_badRequests_when_rename_then_refusedAndUnchanged() throws Exception {
        Fixture fixture = install("Keep Me", 905);
        try {
            HttpResponse<String> invalid = send("POST", "/schema/" + fixture.key() + "/rename?name=Report-Bad");
            assertEquals(400, invalid.statusCode(), invalid.body());
            assertTrue(invalid.body().contains("\"status\":\"invalid\""), invalid.body());

            HttpResponse<String> reserved = send("POST", "/schema/" + fixture.key() + "/rename?name=surveyreport");
            assertEquals(400, reserved.statusCode(), reserved.body());

            HttpResponse<String> taken = send("POST", "/schema/" + fixture.key() + "/rename?name=report_librarycardreg");
            assertEquals(409, taken.statusCode(), taken.body());
            assertTrue(taken.body().contains("\"status\":\"taken\""), taken.body());

            HttpResponse<String> unknown = send("POST", "/schema/" + UUID.randomUUID() + "/rename?name=report_whatever");
            assertEquals(404, unknown.statusCode(), unknown.body());

            assertEquals("report_keep_me", schemaOf(fixture));
            assertEquals(1, schemaExists("report_keep_me"));
        } finally {
            remove(fixture, "report_keep_me");
        }
    }

    @Test
    // UC-011 main scenario / A1 / BR-002: the schema and the column go together, a second call
    // is a no-op, another survey's schema is untouched, and the next build creates it again.
    void given_builtSurvey_when_drop_then_goneIdempotentAndRebuildable() throws Exception {
        Fixture fixture = install("Drop Me", 906);
        try {
            assertEquals(1, schemaExists("report_drop_me"));

            HttpResponse<String> response = send("DELETE", "/schema/" + fixture.key());

            assertEquals(200, response.statusCode(), response.body());
            assertTrue(response.body().contains("\"status\":\"ok\""), response.body());
            assertNull(schemaOf(fixture), "report_schema is cleared with the drop");
            assertEquals(0, schemaExists("report_drop_me"));
            assertEquals(1, schemaExists("report_librarycardreg"), "another survey's schema is untouched");

            HttpResponse<String> again = send("DELETE", "/schema/" + fixture.key());
            assertEquals(200, again.statusCode(), again.body());
            assertTrue(again.body().contains("nothing to drop"), again.body());

            HttpResponse<String> unknown = send("DELETE", "/schema/" + UUID.randomUUID());
            assertEquals(404, unknown.statusCode(), unknown.body());

            HttpResponse<String> rebuilt = send("POST", "/build?survey=" + fixture.key());
            assertEquals(200, rebuilt.statusCode(), rebuilt.body());
            assertEquals("report_drop_me", schemaOf(fixture));
            assertEquals(1, schemaExists("report_drop_me"));
        } finally {
            remove(fixture, "report_drop_me");
        }
    }
}
