package com.elicitsoftware.flow;

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

import com.elicitsoftware.UISessionDataService;
import com.elicitsoftware.model.Respondent;
import com.elicitsoftware.model.Survey;
import com.vaadin.browserless.quarkus.QuarkusBrowserlessTest;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.internal.PendingJavaScriptInvocation;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.common.QuarkusTestResource;
import com.elicitsoftware.PostgresTestResource;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-005: View Reports & Download PDF — Browserless coverage of ReportView's
 * report-generate-pdf-button / report-next-button. The seeded LibraryCardReg (survey id 1)
 * report points at a local URL nothing is listening to during tests, so
 * ReportView.callReport()'s existing exception handling produces an error ReportCard rather
 * than crashing — the same graceful-failure path ReportServiceCallTest already covers at the
 * service layer.
 * <p>
 * No {@code @TestTransaction}: see SectionViewTest for why respondent creation/cleanup goes
 * through {@code QuarkusTransaction.requiringNew()} instead.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class ReportViewTest extends QuarkusBrowserlessTest {

    private static final int SURVEY_ID = 1;

    @Inject
    UISessionDataService sessionDataService;

    @Inject
    EntityManager em;

    @Test
    // UC-005 main success scenario: an inactive (post-review) respondent lands on
    // ReportView and sees the PDF download button.
    void inactiveRespondent_reportView_showsGeneratePdfButton() {
        Respondent respondent = QuarkusTransaction.requiringNew().call(this::createFinishedRespondent);
        try {
            sessionDataService.setSurveyId(SURVEY_ID);
            sessionDataService.setRespondent(respondent);

            navigate(ReportView.class);

            assertTrue(find(Button.class).id("report-generate-pdf-button").isEnabled());
        } finally {
            cleanup(respondent.id);
        }
    }

    @Test
    // UC-005: LibraryCardReg (survey id 1) has no post-survey URL configured, so the
    // "Next" button must not be rendered.
    void surveyWithoutPostSurveyUrl_reportView_hidesNextButton() {
        Respondent respondent = QuarkusTransaction.requiringNew().call(this::createFinishedRespondent);
        try {
            sessionDataService.setSurveyId(SURVEY_ID);
            sessionDataService.setRespondent(respondent);

            navigate(ReportView.class);

            assertFalse(find(Button.class).withId("report-next-button").exists());
        } finally {
            cleanup(respondent.id);
        }
    }

    @Test
    // UC-005 A2 / BR-014: a survey with no reports and a post-survey URL has no report page.
    // The PDF button is not rendered and the browser is sent straight to the URL.
    void surveyWithoutReports_withPostSurveyUrl_hidesPdfButtonAndRedirects() {
        Respondent respondent = QuarkusTransaction.requiringNew().call(this::createFinishedRespondent);
        try {
            // The survey is detached once the transaction above has returned, so these edits
            // reach the view through the session only and never the database.
            respondent.survey.reports = new HashSet<>();
            respondent.survey.postSurveyURL = "https://example.test/after-the-survey";
            sessionDataService.setSurveyId(SURVEY_ID);
            sessionDataService.setRespondent(respondent);

            navigate(ReportView.class);

            assertFalse(find(Button.class).withId("report-generate-pdf-button").exists());
            assertFalse(find(Paragraph.class).withId("report-complete-notice").exists());
            List<PendingJavaScriptInvocation> pending =
                    UI.getCurrent().getInternals().dumpPendingJavaScriptInvocations();
            assertTrue(pending.stream().anyMatch(p -> p.getInvocation().getParameters()
                            .contains("https://example.test/after-the-survey")),
                    "expected a redirect to the post-survey URL, got " + pending);
        } finally {
            cleanup(respondent.id);
        }
    }

    @Test
    // UC-005 A2 / BR-014: a survey with no reports and no post-survey URL shows a completion
    // notice and nothing else.
    void surveyWithoutReports_withoutPostSurveyUrl_showsCompletionNoticeOnly() {
        Respondent respondent = QuarkusTransaction.requiringNew().call(this::createFinishedRespondent);
        try {
            respondent.survey.reports = new HashSet<>();
            respondent.survey.postSurveyURL = null;
            sessionDataService.setSurveyId(SURVEY_ID);
            sessionDataService.setRespondent(respondent);

            navigate(ReportView.class);

            assertFalse(find(Button.class).withId("report-generate-pdf-button").exists());
            assertFalse(find(Button.class).withId("report-next-button").exists());
            assertTrue(find(Paragraph.class).withId("report-complete-notice").exists());
        } finally {
            cleanup(respondent.id);
        }
    }

    @Test
    // UC-005 step 5 / BR-017: an absolute http(s) post-survey address gives the "Next" button.
    void surveyWithReports_withAbsoluteHttpPostSurveyUrl_showsNextButton() {
        Respondent respondent = QuarkusTransaction.requiringNew().call(this::createFinishedRespondent);
        try {
            respondent.survey.postSurveyURL = "https://example.test/after-the-survey";
            sessionDataService.setSurveyId(SURVEY_ID);
            sessionDataService.setRespondent(respondent);

            navigate(ReportView.class);

            assertTrue(find(Button.class).withId("report-next-button").exists());
        } finally {
            cleanup(respondent.id);
        }
    }

    @Test
    // UC-005 BR-017: a post-survey address that is not absolute http(s) is treated as none, so
    // no "Next" button is rendered.
    void surveyWithReports_withUnusablePostSurveyUrl_hidesNextButton() {
        Respondent respondent = QuarkusTransaction.requiringNew().call(this::createFinishedRespondent);
        try {
            respondent.survey.postSurveyURL = "javascript:alert(1)";
            sessionDataService.setSurveyId(SURVEY_ID);
            sessionDataService.setRespondent(respondent);

            navigate(ReportView.class);

            assertTrue(find(Button.class).withId("report-generate-pdf-button").exists());
            assertFalse(find(Button.class).withId("report-next-button").exists());
        } finally {
            cleanup(respondent.id);
        }
    }

    @Test
    // UC-005 A2 / BR-017: with no reports and a post-survey address that is not absolute http(s),
    // the browser is sent nowhere and the page shows the completion notice.
    void surveyWithoutReports_withUnusablePostSurveyUrl_showsCompletionNoticeAndDoesNotRedirect() {
        Respondent respondent = QuarkusTransaction.requiringNew().call(this::createFinishedRespondent);
        try {
            respondent.survey.reports = new HashSet<>();
            respondent.survey.postSurveyURL = "javascript:document.title='x'";
            sessionDataService.setSurveyId(SURVEY_ID);
            sessionDataService.setRespondent(respondent);
            UI.getCurrent().getInternals().dumpPendingJavaScriptInvocations();

            navigate(ReportView.class);

            assertTrue(find(Paragraph.class).withId("report-complete-notice").exists());
            List<PendingJavaScriptInvocation> pending =
                    UI.getCurrent().getInternals().dumpPendingJavaScriptInvocations();
            assertFalse(pending.stream().anyMatch(p -> p.getInvocation().getParameters().toString()
                            .contains("javascript:")),
                    "expected no redirect, got " + pending);
        } finally {
            cleanup(respondent.id);
        }
    }

    @Test
    // UC-005 BR-015 / UC-014 BR-001: a report whose address is a path is the built-in report,
    // rendered in process -- a card with the summary, not an error card and no HTTP call.
    void surveyWithAPathReport_reportView_rendersTheBuiltInReportInProcess() {
        Respondent respondent = QuarkusTransaction.requiringNew().call(this::createFinishedRespondent);
        try {
            com.elicitsoftware.model.ReportDefinition builtIn = new com.elicitsoftware.model.ReportDefinition();
            builtIn.name = "Summary of your answers";
            builtIn.description = "The answers you gave.";
            builtIn.url = com.elicitsoftware.report.builtin.RespondentSummaryService.PATH;
            builtIn.displayOrder = 1;
            builtIn.survey = respondent.survey;
            respondent.survey.reports = new HashSet<>(List.of(builtIn));
            sessionDataService.setSurveyId(SURVEY_ID);
            sessionDataService.setRespondent(respondent);

            navigate(ReportView.class);

            ReportCard card = find(ReportCard.class).single();
            String html = card.getElement().getChildren()
                    .flatMap(c -> c.getChildren())
                    .map(e -> e.getProperty("innerHTML"))
                    .filter(java.util.Objects::nonNull)
                    .reduce("", String::concat);
            assertTrue(html.contains("respondent-summary"), "the built-in report's card, got: " + html);
            assertFalse(html.contains("Report Unavailable"), html);
            assertTrue(find(Button.class).id("report-generate-pdf-button").isEnabled());
        } finally {
            cleanup(respondent.id);
        }
    }

    private Respondent createFinishedRespondent() {
        // ReportView.init() iterates respondent.survey.reports outside of any transaction
        // (the view is rendered after this @Transactional-boundary method returns), so the
        // collection must be eagerly loaded here rather than left as a lazy proxy that would
        // throw LazyInitializationException once the session closes.
        Survey survey = Survey.find("FROM Survey s LEFT JOIN FETCH s.reports WHERE s.id = ?1", SURVEY_ID)
                .firstResult();
        Respondent r = new Respondent();
        r.survey = survey;
        r.accessCode = "test_" + System.nanoTime();
        r.active = false;
        r.logins = 1;
        r.persist();
        return r;
    }

    private void cleanup(Integer respondentId) {
        QuarkusTransaction.requiringNew().run(() ->
                em.createNativeQuery("DELETE FROM survey.respondents WHERE id = ?1")
                        .setParameter(1, respondentId).executeUpdate());
    }

    @Test
    // UC-005 A1: a report service that accepts the call and never answers fails at the read
    // timeout, which callReport turns into an error card, instead of holding the page.
    void reportClient_serviceNeverAnswers_givesUpAtTheReadTimeout() throws Exception {
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        com.sun.net.httpserver.HttpServer server =
                com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            try {
                release.await(10, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });
        server.start();
        try {
            com.elicitsoftware.report.ReportService client = ReportView.reportClient(
                    java.net.URI.create("http://localhost:" + server.getAddress().getPort() + "/report"),
                    java.time.Duration.ofMillis(300));
            long start = System.nanoTime();
            assertThrows(RuntimeException.class,
                    () -> client.callReport(new com.elicitsoftware.report.ReportRequest(1, "en")));
            long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

            assertTrue(elapsedMillis >= 250, "waited for the service, " + elapsedMillis + " ms");
            assertTrue(elapsedMillis < 5_000, "gave up after " + elapsedMillis + " ms");
        } finally {
            release.countDown();
            server.stop(0);
        }
    }

    /** A report service stand-in that records the Authorization header of each call and answers with the given status. */
    private static com.sun.net.httpserver.HttpServer reportService(java.util.List<String> seen, int status, String location)
            throws java.io.IOException {
        com.sun.net.httpserver.HttpServer server =
                com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("localhost", 0), 0);
        server.createContext("/", exchange -> {
            String header = exchange.getRequestHeaders().getFirst("Authorization");
            seen.add(header == null ? "(none)" : header);
            if (location != null) {
                exchange.getResponseHeaders().add("Location", location);
            }
            byte[] body = "{\"title\":\"T\",\"innerHTML\":\"<p>ok</p>\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, status == 200 ? body.length : -1);
            if (status == 200) {
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        });
        server.start();
        return server;
    }

    private static final String SERVICE_TOKEN = "report-call-service-token-0123456789abcdef";

    @Test
    // UC-005 BR-020 / UC-016 BR-007: a report service at a trusted origin is called with the
    // platform service token; one anywhere else is called with no credential.
    void reportClient_trustedOriginReceivesTheServiceToken_othersReceiveNothing() throws Exception {
        java.util.List<String> trustedSeen = new java.util.concurrent.CopyOnWriteArrayList<>();
        java.util.List<String> otherSeen = new java.util.concurrent.CopyOnWriteArrayList<>();
        com.sun.net.httpserver.HttpServer trusted = reportService(trustedSeen, 200, null);
        com.sun.net.httpserver.HttpServer other = reportService(otherSeen, 200, null);
        try {
            com.elicitsoftware.security.ServiceToken token = com.elicitsoftware.security.ServiceToken.of(
                    SERVICE_TOKEN, "http://localhost:" + trusted.getAddress().getPort());
            for (com.sun.net.httpserver.HttpServer server : List.of(trusted, other)) {
                java.net.URI address = java.net.URI.create("http://localhost:" + server.getAddress().getPort() + "/proband/report");
                ReportView.reportClient(address, java.time.Duration.ofSeconds(5), token.authorizationFor(address))
                        .callReport(new com.elicitsoftware.report.ReportRequest(1, "en"));
            }

            org.junit.jupiter.api.Assertions.assertEquals(List.of("Bearer " + SERVICE_TOKEN), trustedSeen);
            org.junit.jupiter.api.Assertions.assertEquals(List.of("(none)"), otherSeen);
        } finally {
            trusted.stop(0);
            other.stop(0);
        }
    }

    @Test
    // UC-005 BR-020: a call that carries the token never follows a redirect, so a trusted
    // service cannot pass the header on to another address.
    void reportClient_aRedirectIsNotFollowed() throws Exception {
        java.util.List<String> elsewhereSeen = new java.util.concurrent.CopyOnWriteArrayList<>();
        java.util.List<String> trustedSeen = new java.util.concurrent.CopyOnWriteArrayList<>();
        com.sun.net.httpserver.HttpServer elsewhere = reportService(elsewhereSeen, 200, null);
        for (int status : new int[]{301, 302, 303, 307, 308}) {
            com.sun.net.httpserver.HttpServer trusted = reportService(trustedSeen, status,
                    "http://localhost:" + elsewhere.getAddress().getPort() + "/collect");
            try {
                java.net.URI address = java.net.URI.create("http://localhost:" + trusted.getAddress().getPort() + "/proband/report");
                com.elicitsoftware.security.ServiceToken token = com.elicitsoftware.security.ServiceToken.of(
                        SERVICE_TOKEN, "http://localhost:" + trusted.getAddress().getPort());

                assertThrows(RuntimeException.class, () -> ReportView.reportClient(address, java.time.Duration.ofSeconds(5),
                        token.authorizationFor(address)).callReport(new com.elicitsoftware.report.ReportRequest(1, "en")),
                        "a " + status + " is a failed call, which becomes the error card");
            } finally {
                trusted.stop(0);
            }
        }
        elsewhere.stop(0);

        org.junit.jupiter.api.Assertions.assertEquals(5, trustedSeen.size());
        assertTrue(elsewhereSeen.isEmpty(), "the redirect target was never called: " + elsewhereSeen);
    }
}
