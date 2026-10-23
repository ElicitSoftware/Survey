package com.elicitsoftware.flow;

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

import com.elicitsoftware.flow.PlainViewTestSupport.StubSession;
import com.elicitsoftware.flow.PlainViewTestSupport.StubTranslator;
import com.elicitsoftware.model.ReportDefinition;
import com.elicitsoftware.model.Respondent;
import com.elicitsoftware.model.Survey;
import com.elicitsoftware.report.PDFService;
import com.elicitsoftware.report.ReportRequest;
import com.elicitsoftware.report.ReportResponse;
import com.elicitsoftware.report.builtin.BuiltInReports;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.internal.PendingJavaScriptInvocation;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.Location;
import com.vaadin.flow.router.NavigationEvent;
import com.vaadin.flow.router.NavigationTrigger;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static com.elicitsoftware.flow.PlainViewTestSupport.byId;
import static com.elicitsoftware.flow.PlainViewTestSupport.report;
import static com.elicitsoftware.flow.PlainViewTestSupport.respondent;
import static com.elicitsoftware.flow.PlainViewTestSupport.survey;
import static com.elicitsoftware.flow.PlainViewTestSupport.t;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-005 (view reports and download a PDF): the report page built by {@link ReportView#init()} in a
 * plain browserless UI. Covers the page with no reports (BR-014), the built-in report call by path
 * (BR-015, UC-014 BR-001), the error card a failed report call turns into, and the PDF button.
 * <p>
 * Each report's card is titled through {@code Respondent.snapshotAnchor}, a Panache lookup that
 * cannot run without Quarkus, so in these tests {@code init()} stops at the first card -- after the
 * PDF button is added and after the first report has been called and its response recorded in
 * {@code reportResponses}. The report call is what these tests examine; the cards themselves are
 * covered by the booted {@code ReportViewTest}.
 */
class ReportViewReportCallTest extends PlainViewTestSupport {

    private static final String BUILT_IN = "/api/reports/summary";

    private StubSession session;
    private StubTranslator translator;
    private StubPdfService pdf;
    private StubBuiltInReports builtIns;
    private Survey survey;
    private Respondent respondent;

    @BeforeEach
    void stubs() {
        session = new StubSession();
        translator = new StubTranslator();
        pdf = new StubPdfService();
        builtIns = new StubBuiltInReports();
        survey = survey(7, "Family");
        respondent = respondent(3, survey, false);
        session.setRespondent(respondent);
    }

    private ReportView newView() {
        ReportView view = new ReportView();
        view.sessionDataService = session;
        view.translator = translator;
        view.pdfService = pdf;
        view.builtInReports = builtIns;
        return view;
    }

    private ReportView build() {
        ReportView view = newView();
        view.init();
        UI.getCurrent().add(view);
        return view;
    }

    /** Builds a view for a survey with one report, up to the first card (see the class comment). */
    private ReportView buildWithReport(ReportDefinition rpt) {
        survey.reports = new HashSet<>(Set.of(rpt));
        ReportView view = newView();
        UI.getCurrent().add(view);
        try {
            view.init();
        } catch (RuntimeException expected) {
            // Respondent.snapshotAnchor: no Panache here.
        }
        assertEquals(1, view.reportResponses.size(), "the report was called before the card was built");
        return view;
    }

    private ReportResponse callReport(ReportDefinition rpt) {
        return buildWithReport(rpt).reportResponses.get(0);
    }

    private static BeforeEnterEvent beforeEnter() {
        UI ui = UI.getCurrent();
        NavigationEvent navigation = new NavigationEvent(ui.getInternals().getRouter(), new Location("report"), ui,
                NavigationTrigger.PROGRAMMATIC);
        return new BeforeEnterEvent(navigation, ReportView.class, List.of());
    }

    private static List<PendingJavaScriptInvocation> pendingJs() {
        return UI.getCurrent().getInternals().dumpPendingJavaScriptInvocations();
    }

    private static String pendingJsText() {
        StringBuilder sb = new StringBuilder();
        for (PendingJavaScriptInvocation invocation : pendingJs()) {
            sb.append(invocation.getInvocation().getExpression()).append(' ')
                    .append(invocation.getInvocation().getParameters()).append('\n');
        }
        return sb.toString();
    }

    @Test
    void given_noRespondent_when_init_then_pageIsEmpty() {
        session.setRespondent(null);

        ReportView view = build();

        assertEquals(0, view.getComponentCount());
        assertEquals("100%", view.getWidth());
        assertEquals("100%", view.getHeight());
    }

    @Test
    void given_noRespondent_when_beforeEnter_then_noRedirect() {
        session.setRespondent(null);
        ReportView view = build();
        pendingJs();

        view.beforeEnter(beforeEnter());

        assertEquals("", pendingJsText());
    }

    @Test
    void given_surveyWithoutReportsOrPostSurveyUrl_when_init_then_completeNoticeOnly() {
        survey.reports = null;

        ReportView view = build();

        assertEquals(1, view.getComponentCount());
        Paragraph notice = byId(view, "report-complete-notice", Paragraph.class);
        assertNotNull(notice);
        assertEquals(t("reportView.complete"), notice.getText());
        assertNull(byId(view, "report-generate-pdf-button", Button.class), "BR-014: nothing to print");
    }

    @Test
    void given_emptyReportsAndBlankPostSurveyUrl_when_init_then_completeNoticeShown() {
        survey.reports = new HashSet<>();
        survey.postSurveyURL = "   ";

        ReportView view = build();

        assertNotNull(byId(view, "report-complete-notice", Paragraph.class));
        assertEquals(1, view.getComponentCount());
    }

    @Test
    void given_noReportsButPostSurveyUrl_when_init_then_pageIsEmpty() {
        survey.reports = new HashSet<>();
        survey.postSurveyURL = "https://example.org/after";

        ReportView view = build();

        assertEquals(0, view.getComponentCount());
    }

    @Test
    void given_noReportsButPostSurveyUrl_when_beforeEnter_then_browserIsSentThere() {
        survey.reports = new HashSet<>();
        survey.postSurveyURL = "https://example.org/after";
        ReportView view = build();
        pendingJs();

        view.beforeEnter(beforeEnter());

        assertTrue(pendingJsText().contains("https://example.org/after"), pendingJsText());
    }

    @Test
    void given_noReportsAndNoPostSurveyUrl_when_beforeEnter_then_noRedirect() {
        survey.reports = new HashSet<>();
        ReportView view = build();
        pendingJs();

        view.beforeEnter(beforeEnter());

        assertEquals("", pendingJsText());
    }

    @Test
    void given_reportsAndPostSurveyUrl_when_beforeEnter_then_noRedirect() {
        survey.postSurveyURL = "https://example.org/after";
        builtIns.render = r -> Optional.of(new ReportResponse("T", "<p/>", null));
        ReportView view = buildWithReport(report("Summary", BUILT_IN));
        pendingJs();

        view.beforeEnter(beforeEnter());

        assertEquals("", pendingJsText());
    }

    @Test
    void given_builtInReport_when_init_then_pdfButtonShownAndReportCalledInProcess() {
        ReportResponse rendered = new ReportResponse("Summary", "<p>ok</p>", null);
        builtIns.render = r -> Optional.of(rendered);
        translator.languageTag = "es-419";

        ReportView view = buildWithReport(report("Summary", BUILT_IN));

        Button pdfButton = byId(view, "report-generate-pdf-button", Button.class);
        assertNotNull(pdfButton);
        assertEquals(t("reportView.btnGeneratePdf"), pdfButton.getText());
        assertNull(byId(view, "report-complete-notice", Paragraph.class));
        assertSame(rendered, view.reportResponses.get(0));
        assertEquals(List.of(BUILT_IN), builtIns.looked);
        assertEquals(1, builtIns.requests.size());
        assertEquals(3, builtIns.requests.get(0).getId());
        assertEquals("es-419", builtIns.requests.get(0).getLanguage());
        assertEquals(List.of("report page"), builtIns.callers);
    }

    @Test
    void given_earlierResponses_when_init_then_theyAreDiscarded() {
        builtIns.render = r -> Optional.of(new ReportResponse("T", "<p/>", null));
        survey.reports = new HashSet<>(Set.of(report("Summary", BUILT_IN)));
        ReportView view = newView();
        view.reportResponses.add(new ReportResponse("stale", "", null));
        try {
            view.init();
        } catch (RuntimeException expected) {
            // Respondent.snapshotAnchor: no Panache here.
        }

        assertEquals(1, view.reportResponses.size());
        assertEquals("T", view.reportResponses.get(0).title);
    }

    @Test
    void given_pathWithNoBuiltInService_when_init_then_errorCardNamesThePath() {
        builtIns.service = false;

        ReportResponse response = callReport(report("Summary", "/no/such"));

        assertEquals(t("reportView.error.title", "Summary"), response.title);
        assertEquals(t("reportView.error.html", "Summary", t("reportView.error.noBuiltIn", "/no/such"), ""),
                response.innerHTML);
    }

    @Test
    void given_builtInReportWithNoRespondent_when_init_then_errorCardSaysSo() {
        builtIns.render = r -> Optional.empty();

        ReportResponse response = callReport(report("Summary", BUILT_IN));

        assertEquals(t("reportView.error.title", "Summary"), response.title);
        assertEquals(t("reportView.error.html", "Summary", t("reportView.error.noRespondent"), ""),
                response.innerHTML);
    }

    @Test
    void given_serviceAnswers403WithoutBody_when_init_then_errorCardShowsLicenseMessage() {
        builtIns.render = r -> {
            throw new WebApplicationException(Response.status(403).build());
        };

        ReportResponse response = callReport(report("Summary", BUILT_IN));

        assertEquals(t("reportView.error.title", "Summary"), response.title);
        assertEquals(t("reportView.error.html", "Summary", t("reportView.error.forbidden"),
                t("reportView.error.licenseHint")), response.innerHTML);
    }

    @Test
    void given_serviceAnswers500WithoutBody_when_init_then_errorCardShowsStatus() {
        WebApplicationException failure = new WebApplicationException("boom", 500);
        builtIns.render = r -> {
            throw failure;
        };

        ReportResponse response = callReport(report("Summary", BUILT_IN));

        assertEquals(t("reportView.error.html", "Summary", t("reportView.error.serviceHttp", 500, "boom"),
                t("reportView.error.licenseHint")), response.innerHTML);
    }

    @Test
    void given_serviceFailureMentioningForbidden_when_init_then_messageIsMarkedAsLicenseFailure() {
        builtIns.render = r -> {
            throw new WebApplicationException("Upstream Forbidden", 502);
        };

        ReportResponse response = callReport(report("Summary", BUILT_IN));

        String status = t("reportView.error.serviceHttp", 502, "Upstream Forbidden");
        assertEquals(t("reportView.error.html", "Summary", t("reportView.error.license", status),
                t("reportView.error.licenseHint")), response.innerHTML);
    }

    @Test
    void given_403MentioningForbidden_when_init_then_licenseMessageIsNotWrappedTwice() {
        builtIns.render = r -> {
            throw new WebApplicationException("Forbidden", 403);
        };

        ReportResponse response = callReport(report("Summary", BUILT_IN));

        assertEquals(t("reportView.error.html", "Summary", t("reportView.error.forbidden"),
                t("reportView.error.licenseHint")), response.innerHTML);
    }

    @Test
    void given_serviceFailureWithBody_when_init_then_errorCardShowsTheBody() {
        builtIns.render = r -> {
            throw new WebApplicationException("bad", Response.status(500).entity("license expired").build());
        };

        ReportResponse response = callReport(report("Summary", BUILT_IN));

        assertEquals(t("reportView.error.html", "Summary", "license expired", t("reportView.error.licenseHint")),
                response.innerHTML);
    }

    @Test
    void given_serviceFailureWithBlankBody_when_init_then_errorCardShowsTheServiceMessage() {
        builtIns.render = r -> {
            throw new WebApplicationException("bad", Response.status(500).entity("   ").build());
        };

        ReportResponse response = callReport(report("Summary", BUILT_IN));

        assertEquals(t("reportView.error.html", "Summary", t("reportView.error.service", "bad"),
                t("reportView.error.licenseHint")), response.innerHTML);
    }

    @Test
    void given_403WithUnreadableBody_when_init_then_errorCardShowsLicenseMessage() {
        builtIns.render = r -> {
            throw new WebApplicationException("bad", Response.status(403).entity(new Object()).build());
        };

        ReportResponse response = callReport(report("Summary", BUILT_IN));

        assertEquals(t("reportView.error.html", "Summary", t("reportView.error.forbidden"),
                t("reportView.error.licenseHint")), response.innerHTML);
    }

    @Test
    void given_500WithUnreadableBody_when_init_then_errorCardShowsStatus() {
        builtIns.render = r -> {
            throw new WebApplicationException("bad", Response.status(500).entity(new Object()).build());
        };

        ReportResponse response = callReport(report("Summary", BUILT_IN));

        assertEquals(t("reportView.error.html", "Summary", t("reportView.error.serviceHttp", 500, "bad"),
                t("reportView.error.licenseHint")), response.innerHTML);
    }

    @Test
    void given_unparsableExternalAddress_when_init_then_errorCardWithoutLicenseHint() {
        ReportResponse response = callReport(report("External", "http://bad host/"));

        assertEquals(t("reportView.error.title", "External"), response.title);
        assertTrue(response.innerHTML.startsWith(t("reportView.error.html", "External", "", "").substring(0, 120)));
        assertTrue(response.innerHTML.endsWith("</p></div>"), "no license hint for a non-HTTP failure");
        assertTrue(builtIns.looked.isEmpty(), "an absolute address is never looked up as built-in");
    }

    @Test
    void given_reportPage_when_pdfClicked_then_pdfIsGeneratedAndOpened() {
        builtIns.render = r -> Optional.of(new ReportResponse("T", "<p/>", null));
        survey.title = "Family history";
        ReportView view = buildWithReport(report("Summary", BUILT_IN));
        UI.getCurrent().setLocale(Locale.ENGLISH);
        pendingJs();

        test(byId(view, "report-generate-pdf-button", Button.class)).click();

        assertSame(view.reportResponses, pdf.responses);
        assertEquals("Family history", pdf.title);
        assertEquals(Locale.ENGLISH, pdf.locale);
        String js = pendingJsText();
        assertTrue(js.contains("window.open($0, '_blank')"), js);
        assertTrue(js.contains("/api/pdf/download?key=pdf_"), js);
        assertEquals(0, $(Notification.class).all().size());
    }

    @Test
    void given_pdfGenerationFails_when_pdfClicked_then_errorNotificationShown() {
        builtIns.render = r -> Optional.of(new ReportResponse("T", "<p/>", null));
        pdf.failure = new IllegalStateException("disk full");
        ReportView view = buildWithReport(report("Summary", BUILT_IN));

        test(byId(view, "report-generate-pdf-button", Button.class)).click();

        List<Notification> notifications = $(Notification.class).all();
        assertEquals(1, notifications.size());
        assertEquals(t("reportView.error.pdf", "disk full"), test(notifications.get(0)).getText());
    }

    /** Records the PDF request instead of building a document. */
    static class StubPdfService extends PDFService {
        ArrayList<ReportResponse> responses;
        String title;
        Locale locale;
        RuntimeException failure;

        @Override
        public byte[] generatePDF(ArrayList<ReportResponse> reportResponses, String surveyTitle, Locale locale) {
            if (failure != null) {
                throw failure;
            }
            this.responses = reportResponses;
            this.title = surveyTitle;
            this.locale = locale;
            return new byte[]{1, 2, 3};
        }
    }

    /** One scripted built-in service, found at any path unless {@link #service} is false. */
    static class StubBuiltInReports extends BuiltInReports {
        boolean service = true;
        Function<ReportRequest, Optional<ReportResponse>> render = r -> Optional.empty();
        final List<String> looked = new ArrayList<>();
        final List<ReportRequest> requests = new ArrayList<>();
        final List<String> callers = new ArrayList<>();

        @Override
        public Optional<Service> find(String url) {
            looked.add(url);
            if (!service) {
                return Optional.empty();
            }
            return Optional.of(new Service() {
                @Override
                public String path() {
                    return url;
                }

                @Override
                public Optional<ReportResponse> render(ReportRequest request, String caller) {
                    requests.add(request);
                    callers.add(caller);
                    return render.apply(request);
                }
            });
        }
    }
}
