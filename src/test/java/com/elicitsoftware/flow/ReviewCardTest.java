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

import com.elicitsoftware.QuestionService;
import com.elicitsoftware.UISessionDataService;
import com.elicitsoftware.response.NavResponse;
import com.elicitsoftware.response.NavigationItem;
import com.elicitsoftware.response.ReviewItem;
import com.elicitsoftware.response.ReviewSection;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentUtil;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-003: Review Answers — a {@link ReviewCard} shows one reviewed section: its title, one
 * "label: value" row per active answer (BR-008 is applied upstream, by the review query), and an
 * edit icon that re-initialises navigation at that section and goes back to the section page.
 */
class ReviewCardTest {

    /** Records what the edit icon asks of the question service. */
    private static final class RecordingQuestionService extends QuestionService {
        final List<String> initialised = new ArrayList<>();
        final NavResponse response = new NavResponse(null, null, List.of(), new NavigationItem[0]);

        @Override
        public NavResponse init(String displaykey) {
            initialised.add(displaykey);
            return response;
        }
    }

    /** Records the navigation response handed to the session, without persisting it. */
    private static final class RecordingSessionData extends UISessionDataService {
        NavResponse stored;

        @Override
        public void setNavResponse(NavResponse navResponse) {
            stored = navResponse;
        }
    }

    /** A UI that records where it was asked to go instead of routing. */
    private static final class RecordingUI extends UI {
        final List<String> navigations = new ArrayList<>();

        @Override
        public void navigate(String location) {
            navigations.add(location);
        }
    }

    @AfterEach
    void clearCurrentUi() {
        UI.setCurrent(null);
    }

    private static ReviewSection section() {
        return new ReviewSection("Medical history", "1.2.0.0.0.0.0",
                List.of(new ReviewItem("Age", "42"), new ReviewItem("Smoker", "No")));
    }

    private static VerticalLayout layout(ReviewCard card) {
        return (VerticalLayout) card.getChildren().findFirst().orElseThrow();
    }

    private static List<Component> children(Component component) {
        return component.getChildren().toList();
    }

    @Test
    void given_section_when_constructed_then_cardShowsTitleAndEditIcon() {
        ReviewCard card = new ReviewCard(section(), null, null);

        assertTrue(card.hasClassName("reviewCard"));
        assertEquals("60%", card.getWidth());
        assertTrue(card.getElement().hasAttribute("data-i18n-content"));

        HorizontalLayout header = (HorizontalLayout) children(layout(card)).getFirst();
        assertEquals("100%", header.getWidth());
        assertEquals(FlexComponent.JustifyContentMode.BETWEEN, header.getJustifyContentMode());
        assertEquals(FlexComponent.Alignment.CENTER, header.getDefaultVerticalComponentAlignment());
        assertEquals("Medical history:", ((H4) children(header).get(0)).getText());
        assertEquals("review-edit-icon-1.2.0.0.0.0.0", children(header).get(1).getId().orElse(null));
    }

    @Test
    void given_items_when_constructed_then_oneLabelValueRowPerItemInOrder() {
        List<Component> rows = children(layout(new ReviewCard(section(), null, null)));

        assertEquals(3, rows.size(), "the header plus one row per item");
        List<Component> first = children(rows.get(1));
        assertEquals("Age: ", ((Span) first.get(0)).getText());
        assertTrue(((Span) first.get(0)).hasClassName("review-item-question"));
        assertEquals("42", ((Span) first.get(1)).getText());
        List<Component> second = children(rows.get(2));
        assertEquals("Smoker: ", ((Span) second.get(0)).getText());
        assertEquals("No", ((Span) second.get(1)).getText());
    }

    @Test
    void given_noItems_when_constructed_then_onlyTheHeaderIsShown() {
        ReviewCard card = new ReviewCard(new ReviewSection("Empty", "1.3.0.0.0.0.0", List.of()), null, null);

        assertEquals(1, children(layout(card)).size());
    }

    @Test
    void given_editIcon_when_clicked_then_navigationRestartsAtTheSection() {
        RecordingQuestionService service = new RecordingQuestionService();
        RecordingSessionData session = new RecordingSessionData();
        RecordingUI ui = new RecordingUI();
        UI.setCurrent(ui);
        ReviewCard card = new ReviewCard(section(), service, session);
        HorizontalLayout header = (HorizontalLayout) children(layout(card)).getFirst();
        Icon edit = (Icon) children(header).get(1);

        ComponentUtil.fireEvent(edit, new com.vaadin.flow.component.ClickEvent<>(edit));

        assertEquals(List.of("1.2.0.0.0.0.0"), service.initialised);
        assertSame(service.response, session.stored);
        assertEquals(List.of("section"), ui.navigations);
    }
}
