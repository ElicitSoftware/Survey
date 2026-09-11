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

import com.elicitsoftware.PostgresTestResource;
import com.elicitsoftware.model.Survey;
import com.vaadin.browserless.quarkus.QuarkusBrowserlessTest;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AboutView renders one Div per Survey.findAll() row plus a build-info footer. Navigating to
 * it is enough to exercise that loop and the ConfigProvider-driven build-info branches against
 * the real seeded survey fixtures (see CONTRIBUTING.md's test fixtures) - no mocking needed.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class AboutViewTest extends QuarkusBrowserlessTest {

    @Test
    void navigateToAboutView_rendersOneSectionPerSeededSurvey() {
        long surveyCount = Survey.findAll().count();

        navigate(AboutView.class);

        assertInstanceOf(AboutView.class, getCurrentView());
        AboutView view = (AboutView) getCurrentView();
        // One Div per survey, plus the spacer Div, plus (if build info is configured) the
        // build-info Div -- always at least surveyCount + 1 (the spacer).
        assertTrue(view.getChildren().count() >= surveyCount + 1);
    }
}
