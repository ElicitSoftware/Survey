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
import com.elicitsoftware.UISessionDataService;
import com.elicitsoftware.model.Respondent;
import com.vaadin.browserless.quarkus.QuarkusBrowserlessTest;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * UC-006: Log Out - now that Vaadin Browserless Testing is a project dependency (see
 * MainViewTest/SectionViewTest), the "requires Karibu-Testing/TestBench, explicitly out of
 * scope" rejection recorded in UISessionDataServiceTest/SessionPersistenceServiceTest is
 * stale. Attaching LogoutView through navigate() fires onAttach(), which is exactly the UC-006
 * step 2 trigger those classes' Javadoc says was previously unreachable in tests.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class LogoutViewTest extends QuarkusBrowserlessTest {

    @Inject
    UISessionDataService sessionDataService;

    @Test
    // UC-006 step 2: navigating to LogoutView (attaching it to the UI) must clear every
    // session-held field, regardless of what was populated beforehand.
    void navigateToLogoutView_clearsSessionData() {
        sessionDataService.setSurveyId(1);
        Respondent respondent = new Respondent();
        respondent.id = 999;
        sessionDataService.setRespondent(respondent);

        navigate(LogoutView.class);

        assertNull(sessionDataService.getSurveyId());
        assertNull(sessionDataService.getRespondent());
        assertNull(sessionDataService.getNavResponse());
    }
}
