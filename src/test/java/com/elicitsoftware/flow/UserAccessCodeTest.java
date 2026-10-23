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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * UC-001 (enter a survey via access code): {@link User} holds the access code a respondent typed.
 */
class UserAccessCodeTest {

    @Test
    void given_noArguments_when_constructed_then_noAccessCode() {
        assertNull(new User().getAccessCode());
    }

    @Test
    void given_accessCode_when_constructed_then_itIsReturned() {
        assertEquals("Abc12", new User("Abc12").getAccessCode());
    }

    @Test
    void given_user_when_accessCodeSet_then_newCodeIsReturned() {
        User user = new User("Abc12");

        user.setAccessCode("Xyz89");

        assertEquals("Xyz89", user.getAccessCode());
    }
}
