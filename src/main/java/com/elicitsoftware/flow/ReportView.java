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
import com.elicitsoftware.model.ReportDefinition;
import com.elicitsoftware.model.Respondent;
import com.elicitsoftware.report.PDFService;
import com.elicitsoftware.report.ReportRequest;
import com.elicitsoftware.report.ReportResponse;
import com.elicitsoftware.report.ReportService;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import com.vaadin.quarkus.annotation.NormalUIScoped;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;
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
 * <p>
 * This view is UI-scoped to prevent data leakage between browser tabs.
 */
@Route(value = "report", layout = MainLayout.class)
@NormalUIScoped
public class ReportView extends VerticalLayout {

    Respondent respondent;

    @Inject
    PDFService pdfService;

    @Inject
    UISessionDataService sessionDataService;

    ArrayList<ReportResponse> reportResponses = new ArrayList<>();

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
//        Survey survey = Survey.findById(sessionDataService.getSurveyId());
        respondent = sessionDataService.getRespondent();

        Button pdfButton = new Button("Generate PDF", event -> {
            try {
                // Generate the PDF using the pdfService
                byte[] pdfContent = pdfService.generatePDF(this.reportResponses);

                // Cache the PDF and get a key
                String pdfKey = com.elicitsoftware.report.PDFDownloadResource.cachePDF(pdfContent);

                // Create URL for the PDF download endpoint
                String pdfUrl = "./pdf-download?key=" + pdfKey;

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
        for (ReportDefinition rpt : this.respondent.survey.reports) {
            reportResponse = callReport(rpt);
            reportResponses.add(reportResponse);
            ReportCard reportCard = new ReportCard(rpt.name, reportResponse);
            this.add(reportCard);
        }


        if (this.respondent.survey.postSurveyURL != null) {
            Button btnNext = new Button("Next", event -> {
                getUI().ifPresent(ui -> ui.getPage().open(this.respondent.survey.postSurveyURL, "_self"));
            });
            this.add(btnNext);
        }
    }

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
     * the generated report content. It includes comprehensive error handling to provide meaningful
     * feedback when service calls fail.
     * <p>
     * Error handling includes:
     * - WebApplicationException handling for service-specific errors
     * - Detailed error message extraction and formatting
     * - Fallback error responses for all exception types
     * - License validation error detection and user guidance
     *
     * @param rpt the {@link ReportDefinition} containing the details of the report,
     *            including the URL for the report service.
     * @return the ReportResponse containing report data if successful, or error information if failed
     */
    private ReportResponse callReport(ReportDefinition rpt) {
        try {
            ReportRequest request = new ReportRequest(respondent.id);
            ReportService reportService = RestClientBuilder.newBuilder()
                    .baseUri(new URI(rpt.url))
                    .build(ReportService.class);
            ReportResponse reportResponse = reportService.callReport(request);
            return reportResponse;
        } catch (jakarta.ws.rs.WebApplicationException e) {
            // Handle license validation errors and other HTTP errors specifically
            String errorMessage = "Service error: " + e.getMessage();
            
            // Try to extract more detailed error information
            if (e.getResponse() != null) {
                int status = e.getResponse().getStatus();
                
                // Try to read the response entity if it exists and hasn't been consumed
                if (e.getResponse().hasEntity()) {
                    try {
                        String responseBody = e.getResponse().readEntity(String.class);
                        if (responseBody != null && !responseBody.trim().isEmpty()) {
                            errorMessage = responseBody;
                        }
                    } catch (Exception readException) {
                        // Response may have already been consumed, fall back to status-based message
                        if (status == 403) {
                            errorMessage = "Access forbidden - License validation may have failed. Please check your license configuration.";
                        } else {
                            errorMessage = "Service error (HTTP " + status + "): " + e.getMessage();
                        }
                    }
                } else {
                    // No response entity, provide status-based error message
                    if (status == 403) {
                        errorMessage = "Access forbidden - License validation may have failed. Please check your license configuration.";
                    } else {
                        errorMessage = "Service error (HTTP " + status + "): " + e.getMessage();
                    }
                }
            }
            
            // Check if the exception message contains clues about license validation
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("forbidden")) {
                if (!errorMessage.toLowerCase().contains("license")) {
                    errorMessage = "License validation failed - " + errorMessage;
                }
            }
            
            ReportResponse reportResponse = new ReportResponse();
            reportResponse.title = "Error - " + rpt.name;
            reportResponse.innerHTML = "<div style='color: red; padding: 20px; border: 1px solid red; background-color: #ffe6e6;'>" +
                    "<h3>Report Generation Error</h3>" +
                    "<p><strong>Service:</strong> " + rpt.name + "</p>" +
                    "<p><strong>Error:</strong> " + errorMessage + "</p>" +
                    "<p><em>If this is a license error, please ensure your PREMM5 license is valid and properly configured.</em></p>" +
                    "</div>";
            return reportResponse;
        } catch (Exception e) {
            // Handle other exceptions (network issues, URI parsing, etc.)
            ReportResponse reportResponse = new ReportResponse();
            reportResponse.title = "Error - " + rpt.name;
            reportResponse.innerHTML = "<div style='color: red; padding: 20px; border: 1px solid red; background-color: #ffe6e6;'>" +
                    "<h3>Report Generation Error</h3>" +
                    "<p><strong>Service:</strong> " + rpt.name + "</p>" +
                    "<p><strong>Error:</strong> " + e.getMessage() + "</p>" +
                    "</div>";
            return reportResponse;
        }
    }
}
