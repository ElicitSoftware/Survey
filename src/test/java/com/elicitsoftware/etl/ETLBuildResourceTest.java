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
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-008 (Rebuild Reporting Schema) over a real HTTP round-trip against the Library fixture,
 * whose reporting schema {@link ETLService#init()} has already built once for this test JVM.
 * <p>
 * The success branch therefore exercises the rebuild's idempotency (main scenario, BR-001):
 * every dimension table and fact column already exists and the call must still answer 200.
 * The failure branch provokes the one known way the build fails -- a second survey whose
 * step reuses a dimension name, tripping {@code dim_step_un} -- and checks it comes back as
 * 500 with the constraint named, not as a dropped connection (A2, BR-003). The disabled
 * branch needs {@code elicit.etl.enabled=false} at boot and is covered without a database in
 * {@link ETLBuildResourceUnitTest}.
 * <p>
 * No {@code @TestTransaction}: the request runs on the HTTP worker, on its own connection,
 * so fixture rows have to be committed for it to see them and are removed again in
 * {@code finally}.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class ETLBuildResourceTest {

    @TestHTTPResource("/api/etl/build")
    URL buildUrl;

    @Inject
    EntityManager em;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private HttpResponse<String> post() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(java.net.URI.create(buildUrl.toString()))
                .POST(HttpRequest.BodyPublishers.noBody())
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(60))
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    // UC-008 main scenario / BR-001: a rebuild over an already-built schema answers 200 ok
    // with a summary, and the summary mentions the steps that ran.
    void given_schemaAlreadyBuilt_when_post_then_200ok() throws Exception {
        HttpResponse<String> response = post();

        assertEquals(200, response.statusCode(), response.body());
        assertTrue(response.headers().firstValue("content-type").orElse("").startsWith("application/json"),
                "the body is JSON: " + response.headers());
        assertTrue(response.body().contains("\"status\":\"ok\""), response.body());
        assertTrue(response.body().contains("Step dimensions upserted"), response.body());
        assertTrue(response.body().contains("fact_sections_view"), response.body());
    }

    @Test
    // UC-008 A2 / BR-003: two surveys sharing a step dimension name break the site-wide
    // dim_step_un constraint; the endpoint reports that as 500 failed with the driver's
    // message rather than letting the exception out of the request.
    void given_secondSurveyReusesStepDimensionName_when_post_then_500failedNamingTheConstraint() throws Exception {
        String takenDimension = (String) em.createNativeQuery(
                "SELECT dimension_name FROM survey.steps WHERE survey_id = 1 ORDER BY id LIMIT 1").getSingleResult();

        Integer surveyId = QuarkusTransaction.requiringNew().call(() -> {
            Integer id = ((Number) em.createNativeQuery(
                    "INSERT INTO survey.surveys(id, name, display_order, title, description, initial_display_key, post_survey_url, survey_key) "
                            + "VALUES (NEXTVAL('survey.surveys_seq'), 'EtlCollision', 902, 'ETL collision fixture', "
                            + "'Second survey reusing a step dimension name', NULL, NULL, gen_random_uuid()) RETURNING id")
                    .getSingleResult()).intValue();
            em.createNativeQuery(
                    "INSERT INTO survey.steps(id, survey_id, display_order, name, dimension_name, description, step_key) "
                            + "VALUES (NEXTVAL('survey.steps_seq'), ?1, 1, 'Colliding step', ?2, 'Reuses another survey''s dimension name', gen_random_uuid())")
                    .setParameter(1, id).setParameter(2, takenDimension).executeUpdate();
            return id;
        });
        try {
            HttpResponse<String> response = post();

            assertEquals(500, response.statusCode(), response.body());
            assertTrue(response.body().contains("\"status\":\"failed\""), response.body());
            assertTrue(response.body().contains("dim_step_un"),
                    "the message must name the constraint so an operator can act on it: " + response.body());
        } finally {
            QuarkusTransaction.requiringNew().run(() -> {
                em.createNativeQuery("DELETE FROM survey.steps WHERE survey_id = ?1").setParameter(1, surveyId).executeUpdate();
                em.createNativeQuery("DELETE FROM survey.surveys WHERE id = ?1").setParameter(1, surveyId).executeUpdate();
            });
        }
    }
}
