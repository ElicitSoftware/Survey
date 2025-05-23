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

import com.elicitsoftware.model.Survey;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.VaadinSession;

/**
 * The AboutView class represents a UI component responsible for displaying information
 * about a survey in the application.
 * <p>
 * This view is mapped to the "about" route and is embedded within the MainLayout.
 * Upon initialization, it retrieves the survey information associated with the current
 * session and displays its description.
 */
@Route(value = "about", layout = MainLayout.class)
public class AboutView extends VerticalLayout {

    VaadinSession session = VaadinSession.getCurrent();

    /**
     * Constructs the AboutView component that displays information about a survey.
     * <p>
     * The constructor retrieves the survey ID stored in the session using the key defined in
     * {@link SessionKeys#SURVEY_ID}. It then fetches the corresponding survey from the database
     * using the {@link Survey#findById(Object)} method. If a survey is found, its description
     * is displayed as a paragraph within this view.
     */
    public AboutView() {
        Survey survey = Survey.findById(session.getAttribute(SessionKeys.SURVEY_ID));
        if (survey != null) {
            Paragraph paragraph = new Paragraph(survey.description);
            add(paragraph);
        }
    }
}
