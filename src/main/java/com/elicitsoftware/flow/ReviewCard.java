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
import com.elicitsoftware.response.NavResponse;
import com.elicitsoftware.response.ReviewItem;
import com.elicitsoftware.response.ReviewSection;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.server.VaadinSession;


/**
 * The ReviewCard class is a Vaadin component that visually represents a review section.
 * It displays a card layout containing a title, items with questions and answers,
 * and an edit icon that allows users to navigate to a specific section for modifications.
 * <p>
 * This class primarily leverages Vaadin components, including VerticalLayout, HorizontalLayout,
 * Icon, and Span to structure and format the visual presentation.
 */
public class ReviewCard extends Div {

    public ReviewCard(QuestionService service, ReviewSection section) {
        super();
        VaadinSession session = VaadinSession.getCurrent();
        this.setClassName("reviewCard");
        this.setWidth("60%");

        VerticalLayout layout = new VerticalLayout();
        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull(); // Make layout full width
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN); // Distribute space between elements
        header.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER); // Center components vertically

        H4 titleDiv = new H4(section.getTitle() + ":");
        header.add(titleDiv);

        Icon editIcon = new Icon(VaadinIcon.EDIT);
        editIcon.addClickListener(e -> {
            NavResponse newNavResponse = service.init(section.getDisplayKey());
            session.setAttribute(SessionKeys.NAV_RESPONSE, newNavResponse);
            UI.getCurrent().navigate("section");
        });

        header.add(editIcon);
        layout.add(header);
        for (ReviewItem item : section.getItems()) {
            HorizontalLayout horizontalLayout = new HorizontalLayout();
            Span question = new Span(item.label() + ": ");
            question.setClassName("review-item-question");
            horizontalLayout.add(question);
            Span answer = new Span(item.value());
            horizontalLayout.add(answer);
            layout.add(horizontalLayout);
        }
        add(layout);
    }
}
