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

import com.elicitsoftware.report.ReportResponse;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-005: View Reports and Download PDF — a {@link ReportCard} shows one report's title over
 * its rendered body, and centres the body when the report service returned an SVG drawing.
 */
class ReportCardTest {

    private static List<Component> rows(ReportCard card) {
        VerticalLayout layout = (VerticalLayout) card.getChildren().findFirst().orElseThrow();
        return layout.getChildren().toList();
    }

    private static Div body(ReportCard card) {
        return (Div) rows(card).get(1);
    }

    @Test
    void given_report_when_constructed_then_titleAndBodyAreShown() {
        ReportCard card = new ReportCard("Risk summary", new ReportResponse("ignored", "<p>Low</p>", null));

        assertTrue(card.hasClassName("reviewCard"));
        assertEquals("60%", card.getWidth());
        assertTrue(card.getElement().hasAttribute("data-i18n-content"));

        HorizontalLayout header = (HorizontalLayout) rows(card).get(0);
        assertEquals("100%", header.getWidth());
        assertEquals(FlexComponent.JustifyContentMode.BETWEEN, header.getJustifyContentMode());
        assertEquals(FlexComponent.Alignment.CENTER, header.getDefaultVerticalComponentAlignment());
        assertEquals("Risk summary", ((H4) header.getChildren().findFirst().orElseThrow()).getText());

        Div body = body(card);
        assertEquals("100%", body.getWidth());
        assertEquals("<p>Low</p>", body.getElement().getProperty("innerHTML"));
    }

    @Test
    void given_textReport_when_constructed_then_bodyIsNotCentred() {
        Div body = body(new ReportCard("Text", new ReportResponse("t", "<p>No drawing</p>", null)));

        assertNull(body.getStyle().get("text-align"));
        assertNull(body.getStyle().get("align"));
    }

    @Test
    void given_svgReport_when_constructed_then_bodyIsCentredWhateverTheCase() {
        Div lower = body(new ReportCard("Pedigree", new ReportResponse("p", "<div><svg width='10'></svg></div>", null)));
        Div upper = body(new ReportCard("Pedigree", new ReportResponse("p", "<SVG></SVG>", null)));

        assertEquals("center", lower.getStyle().get("text-align"));
        assertEquals("center", lower.getStyle().get("align"));
        assertEquals("center", upper.getStyle().get("text-align"));
    }

    @Test
    void given_emptyReportBody_when_constructed_then_cardStillRenders() {
        Div body = body(new ReportCard("Nothing", new ReportResponse()));

        assertNull(body.getStyle().get("text-align"));
        assertEquals(2, rows(new ReportCard("Nothing", new ReportResponse())).size());
    }

    /** UC-005 BR-021: what a report service sends is cleaned before it is set on the page. */
    @Test
    void given_aReportCarryingScript_when_constructed_then_onlyTheCleanedBodyIsSet() {
        Div body = body(new ReportCard("Hostile", new ReportResponse("h",
                "<p onclick=\"steal()\">Low</p><script>steal()</script><svg onload=\"steal()\"><rect width=\"1\"/></svg>", null)));

        String html = body.getElement().getProperty("innerHTML");
        assertEquals("<p>Low</p><svg><rect width=\"1\" /></svg>", html);
        assertEquals("center", body.getStyle().get("text-align"));
    }
}
