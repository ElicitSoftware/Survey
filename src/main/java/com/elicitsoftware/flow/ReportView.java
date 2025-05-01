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

import com.elicitsoftware.model.ReportDefinition;
import com.elicitsoftware.model.Respondent;
import com.elicitsoftware.model.Survey;
import com.elicitsoftware.report.PDFService;
import com.elicitsoftware.report.ReportRequest;
import com.elicitsoftware.report.ReportResponse;
import com.elicitsoftware.report.ReportService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;
import com.vaadin.flow.server.VaadinSession;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.RestClientBuilder;

import java.net.URI;
import java.util.ArrayList;

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
 */
@Route(value = "report", layout = MainLayout.class)
public class ReportView extends VerticalLayout {

    @ConfigProperty(name = "fhhs.url")
    String FHHSURL;

    VaadinSession session = VaadinSession.getCurrent();
    Respondent respondent;

    @Inject
    PDFService pdfService;

    ArrayList<ReportResponse> reportResponses = new ArrayList<>();

    public ReportView() {
        super();
    }

    /**
     * Initializes the ReportView component after dependency injection is completed.
     * This method sets up the layout and dynamically loads report cards based on the
     * survey and respondent information from the current session.
     * <p>
     * Key behaviors:
     * - Sets the layout to occupy the full available size.
     * - Retrieves the associated survey using the SURVEY_ID session attribute.
     * - Retrieves the respondent object from the RESPONDENT session attribute.
     * - Iterates through the list of reports associated with the survey.
     * - For each report, creates a ReportCard with the report name and content, and
     * adds it to the layout.
     * <p>
     * Preconditions:
     * - The SURVEY_ID and RESPONDENT session attributes must be present and valid.
     * - The Survey entity linked to the session SURVEY_ID must exist and contain report definitions.
     */
    @PostConstruct
    public void init() {
        setSizeFull();
        Survey survey = Survey.findById(session.getAttribute(SessionKeys.SURVEY_ID));
        respondent = (Respondent) session.getAttribute(SessionKeys.RESPONDENT);

        Button pdfButton = new Button("Generate PDF", event -> {
            try {
                // Generate the PDF using the pdfService
                StreamResource pdfContent = pdfService.generatePDF(this.reportResponses);

                // Register the StreamResource and get its URL
                String pdfUrl = UI.getCurrent().getSession().getResourceRegistry().registerResource(pdfContent).getResourceUri().toString();

                // Open the PDF in a new browser tab
                UI.getCurrent().getPage().open(pdfUrl, "_blank");
            } catch (Exception e) {
                e.printStackTrace();
                Notification.show("Failed to generate PDF: " + e.getMessage(), 3000, Notification.Position.MIDDLE);
            }
        });

        this.add(pdfButton);

        //Make sure this is empty
        this.reportResponses.clear();
        ReportResponse reportResponse;
        for (ReportDefinition rpt : survey.reports) {
            reportResponse = callReport(rpt);
            reportResponses.add(reportResponse);
            ReportCard reportCard = new ReportCard(rpt.name, reportResponse);
            this.add(reportCard);
        }
    }

    private ArrayList<ReportCard> getCards() {
        ArrayList<ReportCard> cards = new ArrayList<>();

        StringBuilder htmlBuilder = new StringBuilder();
        for (var component : this.getChildren().toList()) {
            if (component instanceof ReportCard) {
                cards.add((ReportCard) component);
            }
        }
        return cards;
    }

    /**
     * Calls the report generation service using the provided report definition and returns the result.
     * The method sends a POST request to the report service with the respondent ID and retrieves
     * the HTML content of the generated report. If an error occurs during the process, it returns
     * the exception message.
     *
     * @param rpt the {@link ReportDefinition} containing the details of the report,
     *            including the URL for the report service.
     * @return the HTML content of the report if the report service call is successful,
     * or the exception message if an error occurs.
     */
    private ReportResponse callReport(ReportDefinition rpt) {
        try {
            ReportRequest request = new ReportRequest(respondent.id);
            ReportService reportService = RestClientBuilder.newBuilder()
                    .baseUri(new URI(FHHSURL + rpt.url))
                    .build(ReportService.class);
            ReportResponse reportResponse = reportService.callReport(request);
            return reportResponse;
        } catch (Exception e) {
            ReportResponse reportResponse = new ReportResponse();
            reportResponse.innerHTML = e.getMessage();
            return reportResponse;
        }
    }
}
