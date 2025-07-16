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
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.Route;
import com.vaadin.quarkus.annotation.NormalUIScoped;

import java.util.List;

/**
 * The AboutView class represents a UI component responsible for displaying information
 * about a survey in the application.
 * <p>
 * This view is mapped to the "about" route and is embedded within the MainLayout.
 * Upon initialization, it retrieves the survey information associated with the current
 * session and displays its description.
 * <p>
 * This view is UI-scoped to prevent data leakage between browser tabs.
 */
@Route(value = "about", layout = MainLayout.class)
@NormalUIScoped
public class AboutView extends VerticalLayout {

    /**
     * Constructs the AboutView component that displays information about a survey.
     * <p>
     * The constructor retrieves the survey ID stored in the UI-scoped session service
     * and fetches the corresponding survey from the database using the {@link Survey#findById(Object)} method.
     * If a survey is found, its description is displayed as a paragraph within this view.
     */
    public AboutView() {
        List<Survey> surveys = Survey.findAll().list();
        for (Survey survey : surveys) {
            Div aboutSurvey = new Div();
            aboutSurvey.getElement().setProperty("innerHTML", ("<h4>" + survey.name + "</h4>" + survey.description));
            add(aboutSurvey);
        }
    }
}
