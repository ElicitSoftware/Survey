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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-001: Enter Survey via Token — Browserless coverage of the login form itself
 * (MainView), driving it through the stable {@code login-token-field} / {@code login-button}
 * IDs rather than the TokenService layer directly (see TokenServiceTest for that coverage).
 */
@QuarkusTest
class MainViewTest extends QuarkusBrowserlessTest {

    @Test
    // UC-001 main success scenario: a fresh token, auto-registered (test profile has
    // token.autoRegister=true), logs the respondent in and lands on SectionView.
    void validToken_click_login_navigatesToSection() {
        navigate(MainView.class);

        // Multiple test surveys are seeded, so MainView renders the survey ComboBox
        // (rather than auto-selecting a lone survey) — pick the LibraryCardReg survey (id=1)
        // that the auto-registered respondent below will be attached to.
        if (find(ComboBox.class).exists()) {
            test(find(ComboBox.class).id("login-survey-select")).selectItem("LibraryCardReg");
        }

        String token = new RandomStringGenerator(10).nextString();
        test(find(TextField.class).id("login-token-field")).setValue(token);
        test(find(Button.class).id("login-button")).click();

        assertInstanceOf(SectionView.class, getCurrentView());
    }

    @Test
    // UC-001 A-flow: client-side length validation rejects tokens under 5 characters
    // before any TokenService call, so the field is marked invalid and no navigation happens.
    void shortToken_showsFieldValidationError() {
        navigate(MainView.class);

        TextField tokenField = find(TextField.class).id("login-token-field");
        test(tokenField).setValue("abc");

        assertTrue(tokenField.isInvalid());
        assertInstanceOf(MainView.class, getCurrentView());
    }
}
