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

import com.elicitsoftware.QuestionService;
import com.elicitsoftware.UISessionDataService;
import com.elicitsoftware.model.Respondent;
import com.elicitsoftware.model.Survey;
import com.elicitsoftware.response.NavResponse;
import com.elicitsoftware.response.ReviewResponse;
import com.elicitsoftware.response.ReviewSection;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H5;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import com.vaadin.quarkus.annotation.NormalUIScoped;
import jakarta.annotation.PostConstruct;
import jakarta.inject.Inject;

/**
 * ReviewView is a Vaadin-based component that provides a user interface for reviewing survey responses.
 * It is designed to display a summary of the respondent's answers, allow navigation between sections,
 * and finalize the survey submission process.
 * <p>
 * This view is mapped to the "review" route and is enclosed in the MainLayout framework.
 * This view is UI-scoped to prevent data leakage between browser tabs.
 */
@Route(value = "review", layout = MainLayout.class)
@NormalUIScoped
public class ReviewView extends VerticalLayout {

    final UI ui = UI.getCurrent();
    @Inject
    QuestionService service;

    @Inject
    UISessionDataService sessionDataService;

    NavResponse navResponse;
    Respondent respondent;

    public ReviewView() {
        super();
    }

    /**
     * Initializes the ReviewView component by setting up the layout, loading session data,
     * and dynamically creating user interface elements based on the survey state and respondent's progress.
     * <p>
     * This method retrieves the current survey, respondent, and navigation response from
     * the user session and updates the view accordingly. It includes:
     * - Setting the layout size to full.
     * - Displaying information about the loaded survey and respondent token.
     * - Adding a thank-you message and instructions component.
     * - Dynamically adding review cards for survey sections if available.
     * - Adding navigation buttons (Previous and Finish) and configuring their actions.
     * <p>
     * The method ensures that the view adapts to the current navigation state,
     * allowing users to navigate through survey sections or finalize their responses.
     * It also handles the state of buttons based on the availability of navigation actions.
     */
    @PostConstruct
    public void init() {
        setSizeFull();

        Survey survey = Survey.findById(sessionDataService.getSurveyId());
        respondent = sessionDataService.getRespondent();
        navResponse = sessionDataService.getNavResponse();

        // Add null checks
        if (respondent == null || survey == null) {
            Notification.show("Session expired. Please login again.", 3000, Notification.Position.MIDDLE);
            UI.getCurrent().navigate("");
            return;
        }

        Paragraph paragraph = new Paragraph();
        paragraph.add(new H5("Survey: " + survey.name));
        add(paragraph);
        Div thankYouDiv = getThankYouDiv();
        add(thankYouDiv);

        ReviewResponse response = service.review(respondent.id);

        if (response != null) {
            for (ReviewSection section : response.getSections()) {
                add(new ReviewCard(section, service, sessionDataService));
            }
            Button btnPrevious = new Button("Previous");
            btnPrevious.setDisableOnClick(true);
            btnPrevious.setEnabled(navResponse.getCurrentNavItem().getPrevious() != null);
            btnPrevious.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            btnPrevious.addClickListener(e -> previousSection());
            add(btnPrevious);

            Button btnFinish = new Button("Finish");
            btnFinish.setDisableOnClick(true);
            btnFinish.setEnabled(navResponse.getCurrentNavItem().getPrevious() != null);
            btnFinish.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            btnFinish.addClickListener(e -> deactivate());
            add(btnFinish);
        }
    }

    /**
     * Creates and returns a Div component containing multiple paragraphs of text to thank users
     * for completing the survey, provide instructions to review their answers, and inform them about
     * the inability to make changes after submission.
     * <p>
     * The generated Div contains:
     * - A "Thank you" message paragraph.
     * - A paragraph with instructions on reviewing and editing answers.
     * - A note indicating that answers cannot be edited after submission.
     *
     * @return a Div component with the thanked message, review instructions, and post-submission notice.
     */
    private static Div getThankYouDiv() {
        Div thankYouDiv = new Div();
        Paragraph thanks = new Paragraph("Thank you for taking this survey.");
        thankYouDiv.add(thanks);
        Paragraph review = new Paragraph("Please review your answers for each section below. If you need to change them press \"Edit\" to be taken to that section. When you're done editing you can press \"Review\" to return to this page.");
        thankYouDiv.add(review);
        Paragraph tip = new Paragraph("Note: After submission you will not be able edit your answers.");
        thankYouDiv.add(tip);
        return thankYouDiv;
    }

    /**
     * Deactivates the respondent's session and redirects the user interface to the report page.
     * <p>
     * This method performs two actions:
     * 1. Calls the `service.finalize` method, passing the current respondent's unique identifier to
     * mark the respondent as inactive and finalize any remaining operations related to the survey process.
     * 2. Navigates the user interface to the "report" view to display the relevant report for the respondent.
     * <p>
     * Usage of this method ensures proper cleanup and redirection once the survey process is complete.
     */
    private void deactivate() {
        service.finalize(respondent.id);
        ui.navigate("report");
    }

    /**
     * Navigates to the previous section in the survey workflow. This method updates the navigation
     * state to the previous step using the respondent's unique identifier and the current navigation
     * item's "previous" key. After updating the navigation response in the session service, it navigates
     * directly to the section view.
     * <p>
     * The method retrieves the respondent's next navigation state by invoking the `service.init`
     * method with the respondent ID and the "previous" navigation key of the current navigation item.
     * The updated {@code NavResponse} is stored in the session service.
     */
    private void previousSection() {
        // Add null checks before accessing navigation data
        if (navResponse == null || navResponse.getCurrentNavItem() == null) {
            Notification.show("Navigation data not available. Please refresh the page.", 3000, Notification.Position.MIDDLE);
            return;
        }

        if (respondent == null) {
            Notification.show("Session expired. Please login again.", 3000, Notification.Position.MIDDLE);
            UI.getCurrent().navigate("");
            return;
        }

        String previousKey = navResponse.getCurrentNavItem().getPrevious();
        if (previousKey == null) {
            Notification.show("No previous section available.", 3000, Notification.Position.MIDDLE);
            return;
        }

        try {
            NavResponse newNavResponse = service.init(respondent.id, previousKey);
            sessionDataService.setNavResponse(newNavResponse);
            // Use direct navigation instead of page reload
            UI.getCurrent().navigate("section");
        } catch (Exception e) {
            Notification.show("Error navigating to previous section. Please try again.", 3000, Notification.Position.MIDDLE);
        }
    }
}

