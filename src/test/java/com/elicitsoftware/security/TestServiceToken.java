package com.elicitsoftware.security;

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

/** The platform service token of the test profile ({@code %test.elicit.service.token}), for tests that call a protected path. */
public final class TestServiceToken {

    public static final String VALUE = "survey-test-service-token-0123456789abcdef";

    /** The {@code Authorization} header a platform service sends. */
    public static final String AUTHORIZATION = "Bearer " + VALUE;

    private TestServiceToken() {
    }
}
