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

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.eclipse.microprofile.rest.client.RestClientBuilder;

import com.elicitsoftware.i18n.ContentTranslator;
import com.elicitsoftware.UISessionDataService;
import com.elicitsoftware.model.ReportDefinition;
import com.elicitsoftware.model.Respondent;
import com.elicitsoftware.report.PDFService;
import com.elicitsoftware.report.ReportRequest;
import com.elicitsoftware.report.ReportResponse;
import com.elicitsoftware.report.ReportService;
import com.elicitsoftware.report.builtin.BuiltInReports;
import com.elicitsoftware.report.builtin.RespondentSummaryService;
import com.elicitsoftware.security.ServiceToken;
import com.elicitsoftware.util.SafeUrls;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.Route;
import com.vaadin.quarkus.annotation.NormalUIScoped;
import io.quarkus.logging.Log;

import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;

/**
 * Represents the ReportView component in the application. This view is responsible for
 * dynamically generating and displaying report cards based on survey data and respondent
 * information retrieved from the current session.
 * <p>
 * The ReportView extends Vaadin's `VerticalLayout` and is set up within a route mapped
 * to "/report", using `MainLayout` as its parent layout. It interacts with session
 * attributes to identify the associated survey and respondent, and dynamically loads the
 * reports defined for the survey.
 * <p>
 * Key responsibilities:
 * - Retrieves session attributes for the survey (via SURVEY_ID) and respondent.
 * - Loads and sets up a layout to host individual report cards.
 * - Invokes a remote report generation service to generate the content of each report.
 * - Displays the generated report content in the form of dynamically created ReportCards.
 * <p>
 * This view is UI-scoped to prevent data leakage between browser tabs.
 * <p>
 * A survey that lists no reports has no report page (UC-005 BR-014): there is nothing to show
 * and nothing to print, so no "Generate PDF" button is rendered, and the respondent is sent
 * straight to the survey's post-survey URL when it has one. Doing that here rather than at each
 * caller covers every path to {@code /report}: Finish, a returning respondent's sign-in and a
 * restored session.
 * <p>
 * The post-survey address and every external report address come from the survey definition.
 * Each is used only when it is an absolute http or https address (UC-005 BR-017); a post-survey
 * address that is anything else is treated as if the survey had none. A report service receives
 * the platform service token only when the origin of its address is one the site trusts
 * (UC-005 BR-020).
 */
@Route(value = "report", layout = MainLayout.class)
@NormalUIScoped
public class ReportView extends VerticalLayout implements BeforeEnterObserver {

    /** How long an external report service may take to accept the connection. */
    static final Duration REPORT_CONNECT_TIMEOUT = Duration.ofSeconds(5);

    /**
     * How long an external report service may take to answer. The page is built on the
     * respondent's request, so a service that never answers must become an error card rather
     * than hold the page (UC-005 A1).
     */
    static final Duration REPORT_READ_TIMEOUT = Duration.ofSeconds(60);

    /**
     * The client for one external report service, bounded by {@link #REPORT_CONNECT_TIMEOUT} and
     * the given read timeout. Package-private so ReportViewTest can exercise it with a short one.
     */
    static ReportService reportClient(URI address, Duration readTimeout) {
        return reportClient(address, readTimeout, Optional.empty());
    }

    /**
     * The client for one external report service, with the {@code Authorization} header the call
     * carries, if any (UC-005 BR-020). It never follows a redirect: the header was decided for
     * this address and must not travel to another.
     */
    static ReportService reportClient(URI address, Duration readTimeout, Optional<String> authorization) {
        RestClientBuilder builder = RestClientBuilder.newBuilder()
                .baseUri(address)
                .followRedirects(false)
                .connectTimeout(REPORT_CONNECT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
                .readTimeout(readTimeout.toMillis(), TimeUnit.MILLISECONDS);
        authorization.ifPresent(value -> builder.header("Authorization", value));
        return builder.build(ReportService.class);
    }

    Respondent respondent;

    @Inject
    PDFService pdfService;

    @Inject
    UISessionDataService sessionDataService;

    @Inject
    ContentTranslator translator;

    @Inject
    BuiltInReports builtInReports;

    @Inject
    ServiceToken serviceToken;

    ArrayList<ReportResponse> reportResponses = new ArrayList<>();

    /** How much of a report service's own words one log line holds (UC-005 A1). */
    static final int LOGGED_DETAIL_LIMIT = 300;

    /** The key of the PDF this page generated last, which the next one replaces (UC-005 BR-018). */
    String lastPdfKey;

    public ReportView() {
        super();
    }

    /**
     * Initializes the ReportView component after dependency injection is completed.
     * This method sets up the layout and dynamically loads report cards based on the
     * survey and respondent information from the current UI-scoped session service.
     * <p>
     * Key behaviors:
     * - Sets the layout to occupy the full available size.
     * - Retrieves the associated survey using the session service.
     * - Retrieves the respondent object from the session service.
     * - Iterates through the list of reports associated with the survey.
     * - For each report, creates a ReportCard with the report name and content, and
     * adds it to the layout.
     * <p>
     * Preconditions:
     * - The session service must contain valid survey ID and respondent data.
     * - The Survey entity linked to the session survey ID must exist and contain report definitions.
     */
    @PostConstruct
    public void init() {
        setSizeFull();
        respondent = sessionDataService.getRespondent();
        if (respondent == null) {
            return;
        }

        // UC-005 BR-014: no reports, no report page. With a post-survey URL, beforeEnter() sends
        // the browser there; without one, all there is to say is that the survey is complete.
        if (!hasReports()) {
            if (postSurveyUrl() == null) {
                Paragraph complete = new Paragraph(getTranslation("reportView.complete"));
                complete.setId("report-complete-notice");
                this.add(complete);
            }
            return;
        }

        Button pdfButton = new Button(getTranslation("reportView.btnGeneratePdf"));
        pdfButton.setId("report-generate-pdf-button");
        pdfButton.addClickListener(event -> {
            try {
                // Generate the PDF using the pdfService
                byte[] pdfContent = pdfService.generatePDF(this.reportResponses, this.respondent.survey.title, getLocale());

                // Cache the PDF and get a key; it replaces the one this page generated before
                // (UC-005 BR-018).
                String pdfKey = com.elicitsoftware.report.PDFDownloadResource.cachePDF(pdfContent, lastPdfKey);
                lastPdfKey = pdfKey;

                // Create URL for the PDF download endpoint
                String pdfUrl = "/api/pdf/download?key=" + pdfKey;

                // Open the PDF in a new browser tab
                UI.getCurrent().getPage().executeJs("window.open($0, '_blank')", pdfUrl);
            } catch (Exception e) {
                Log.error("Failed to generate PDF", e);
                Notification.show(getTranslation("reportView.error.pdf"), 3000, Notification.Position.MIDDLE);
            }
        });

        this.add(pdfButton);

        //Make sure this is empty
        this.reportResponses.clear();
        ReportResponse reportResponse;
        for (ReportDefinition rpt : this.respondent.survey.reports) {
            reportResponse = callReport(rpt);
            reportResponses.add(reportResponse);
            ReportCard reportCard = new ReportCard(
                    translator.name(respondent.survey, rpt, Respondent.snapshotAnchor(respondent.id.intValue())),
                    reportResponse);
            this.add(reportCard);
        }


        String postSurveyUrl = postSurveyUrl();
        if (postSurveyUrl != null) {
            Button btnNext = new Button(getTranslation("reportView.btnNext"), event -> {
                getUI().ifPresent(ui -> ui.getPage().open(postSurveyUrl, "_self"));
            });
            btnNext.setId("report-next-button");
            this.add(btnNext);
        }
    }

    /**
     * UC-005 BR-014: a respondent routed to the report page of a survey that lists no reports is
     * sent straight to the survey's post-survey URL, whichever path brought them here.
     */
    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (respondent != null && !hasReports() && postSurveyUrl() != null) {
            event.getUI().getPage().setLocation(postSurveyUrl());
        }
    }

    private boolean hasReports() {
        return respondent.survey.reports != null && !respondent.survey.reports.isEmpty();
    }

    /**
     * UC-005 BR-017: the survey's post-survey address when the browser may be sent to it, an
     * absolute http or https address; null when the survey has none or has one that is not.
     */
    private String postSurveyUrl() {
        String url = respondent.survey.postSurveyURL;
        if (url == null || url.isBlank()) {
            return null;
        }
        String usable = SafeUrls.httpUrl(url).map(URI::toString).orElse(null);
        if (usable == null && !postSurveyUrlReported) {
            postSurveyUrlReported = true;
            Log.warn("The post-survey address of survey " + respondent.survey.id
                    + " is not an absolute http(s) address and is ignored");
        }
        return usable;
    }

    /** Whether this page has already logged that the post-survey address is ignored. */
    private boolean postSurveyUrlReported;

    private ArrayList<ReportCard> getCards() {
        ArrayList<ReportCard> cards = new ArrayList<>();

        for (var component : this.getChildren().toList()) {
            if (component instanceof ReportCard) {
                cards.add((ReportCard) component);
            }
        }
        return cards;
    }

    /**
     * Calls the report generation service using the provided report definition and returns the result.
     * <p>
     * This method sends a POST request to the report service with the respondent ID and retrieves
     * the generated report content. A call that fails for any reason becomes an error card
     * (UC-005 A1): the respondent is told that the report is unavailable, in their language, and
     * the status, the service's answer and the address are logged for the operator. Nothing the
     * service or the failure said is shown on the page.
     *
     * @param rpt the {@link ReportDefinition} containing the details of the report,
     *            including the URL for the report service.
     * @return the ReportResponse containing report data if successful, or the error card if failed
     */
    private ReportResponse callReport(ReportDefinition rpt) {
        try {
            ReportRequest request = new ReportRequest(respondent.id,
                    translator.language(respondent.survey));
            // UC-005 BR-015 / UC-014 BR-001: a path names a report built into Survey; call it in
            // process rather than over HTTP to ourselves.
            if (BuiltInReports.isPath(rpt.url)) {
                BuiltInReports.Service builtIn = builtInReports.find(rpt.url).orElse(null);
                if (builtIn == null) {
                    throw new IllegalStateException("no report is built into Survey at this address"); // i18n:ignore
                }
                return builtIn.render(request, "report page") // i18n:ignore
                        .orElseThrow(() -> new IllegalStateException("the respondent no longer exists")); // i18n:ignore
            }
            // UC-005 BR-017: any other address is called only when it is absolute http(s).
            URI address = SafeUrls.httpUrl(rpt.url).orElse(null);
            if (address == null) {
                throw new IllegalStateException("the address is not an absolute http(s) address"); // i18n:ignore
            }
            // UC-005 BR-020: the service token goes to a trusted origin only. A trusted report is
            // not called while Survey has no token; that throws here and becomes the error card.
            Optional<String> authorization = serviceToken.authorizationFor(address);
            ReportService reportService = reportClient(address, REPORT_READ_TIMEOUT, authorization);
            ReportResponse reportResponse = reportService.callReport(request);
            return reportResponse;
        } catch (jakarta.ws.rs.WebApplicationException e) {
            String detail = e.getMessage();
            int status = 0;
            if (e.getResponse() != null) {
                status = e.getResponse().getStatus();
                // The service's own answer says most; it may already have been consumed.
                if (e.getResponse().hasEntity()) {
                    try {
                        String responseBody = e.getResponse().readEntity(String.class);
                        if (responseBody != null && !responseBody.trim().isEmpty()) {
                            detail = responseBody;
                        }
                    } catch (Exception readException) {
                        // Keep the exception's message.
                    }
                }
            }
            Log.warn("Report '" + loggable(rpt.name) + "' at " + loggable(rpt.url) + " failed with HTTP " + status + ": " + loggable(detail));
            return unavailable(rpt);
        } catch (Exception e) {
            // Handle other exceptions (network issues, URI parsing, etc.)
            Log.warn("Report '" + loggable(rpt.name) + "' at " + loggable(rpt.url) + " failed: " + loggable(e.getMessage()));
            return unavailable(rpt);
        }
    }

    /**
     * UC-005 A1: the card for a report that could not be shown. It names the report, which is
     * survey content and therefore escaped, and says nothing else about the failure.
     */
    private ReportResponse unavailable(ReportDefinition rpt) {
        ReportResponse reportResponse = new ReportResponse();
        reportResponse.title = getTranslation("reportView.error.title", rpt.name);
        reportResponse.innerHTML = getTranslation("reportView.error.unavailable", RespondentSummaryService.escape(rpt.name));
        return reportResponse;
    }

    /** One line of bounded length for the log: no line breaks, at most {@link #LOGGED_DETAIL_LIMIT} characters. */
    static String loggable(String text) {
        if (text == null) {
            return "";
        }
        String oneLine = text.replaceAll("\\s+", " ").strip();
        return oneLine.length() <= LOGGED_DETAIL_LIMIT ? oneLine : oneLine.substring(0, LOGGED_DETAIL_LIMIT) + "...";
    }
}
