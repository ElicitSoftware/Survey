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

import com.elicitsoftware.RandomStringGenerator;
import com.vaadin.browserless.quarkus.QuarkusBrowserlessTest;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.textfield.TextField;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.common.QuarkusTestResource;
import com.elicitsoftware.PostgresTestResource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-001: Enter Survey via Access Code — Browserless coverage of the login form itself
 * (MainView), driving it through the stable {@code login-access-code-field} / {@code login-button}
 * IDs rather than the AccessCodeService layer directly (see AccessCodeServiceTest for that coverage).
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class MainViewTest extends QuarkusBrowserlessTest {

    @Test
    // UC-001 main success scenario: a fresh access code, auto-registered (test profile has
    // accessCode.autoRegister=true), logs the respondent in and lands on SectionView.
    void validAccessCode_click_login_navigatesToSection() {
        navigate(MainView.class);

        // Multiple test surveys are seeded, so MainView renders the survey ComboBox
        // (rather than auto-selecting a lone survey) — pick the LibraryCardReg survey (id=1)
        // that the auto-registered respondent below will be attached to.
        if (find(ComboBox.class).exists()) {
            test(find(ComboBox.class).id("login-survey-select")).selectItem("LibraryCardReg");
        }

        String accessCode = new RandomStringGenerator(10).nextString();
        test(find(TextField.class).id("login-access-code-field")).setValue(accessCode);
        test(find(Button.class).id("login-button")).click();

        assertInstanceOf(SectionView.class, getCurrentView());
    }

    @Test
    // UC-001 A-flow: client-side length validation rejects access codes under 5 characters
    // before any AccessCodeService call, so the field is marked invalid and no navigation happens.
    void shortAccessCode_showsFieldValidationError() {
        navigate(MainView.class);

        TextField accessCodeField = find(TextField.class).id("login-access-code-field");
        test(accessCodeField).setValue("abc");

        assertTrue(accessCodeField.isInvalid());
        assertInstanceOf(MainView.class, getCurrentView());
    }
}
