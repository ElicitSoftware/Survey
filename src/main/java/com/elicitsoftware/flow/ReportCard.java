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

import com.elicitsoftware.report.ReportResponse;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;


/**
 * The ReportCard class represents a UI component for displaying a report card layout.
 * It is a custom component that extends the Div class and is designed to combine a
 * title and HTML content into a visually appealing card layout.
 * <p>
 * Features:
 * - Applies custom styling with a "reviewCard" CSS class and sets a default width of 60%.
 * - Displays a title within a header using Vaadin's H4 component.
 * - Allows inner HTML content to be dynamically set and rendered inside the card.
 * - Uses Vaadin layouts for aligning and organizing components, including VerticalLayout
 * and HorizontalLayout.
 * <p>
 * Behavior:
 * - The title is aligned to the center of the header within a horizontally justified layout.
 * - The report content is displayed below the header in a vertical layout.
 * <p>
 * Constructor:
 * - Accepts a String title for the header and a String html for the report content.
 * <p>
 * Extends:
 * - com.vaadin.flow.component.html.Div
 * <p>
 * Usage:
 * - Suitable for generating structured report cards for Vaadin-based web applications.
 */
public class ReportCard extends Div {

    public ReportCard(String title, ReportResponse report) {
        super();
        this.setClassName("reviewCard");
        this.setWidth("60%");


        VerticalLayout layout = new VerticalLayout();
        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull(); // Make layout full width
        header.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN); // Distribute space between elements
        header.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER); // Center components vertically

        H4 titleDiv = new H4(title);
        header.add(titleDiv);
        layout.add(header);

        Div reportDiv = new Div();
        reportDiv.setWidthFull(); // Set reportDiv to full width
        reportDiv.getElement().setProperty("innerHTML", report.innerHTML);

        // Center content if it contains SVG
        if (report.innerHTML != null && report.innerHTML.toLowerCase().contains("<svg")) {
            // Use CSS to center only the SVG while keeping text left-aligned
            reportDiv.getStyle().set("text-align", "center");
            reportDiv.getStyle().set("align", "center");
            reportDiv.getElement().executeJs(
                "this.querySelectorAll('svg').forEach(svg => {" +
                "  svg.style.display = 'block';" +
                "  svg.style.margin = '0 auto';" +
                "  svg.style.width = '100%';" +
                "  svg.style.height = 'auto';" +
                "});"
            );
        }

        layout.add(reportDiv);
        add(layout);
    }
}
