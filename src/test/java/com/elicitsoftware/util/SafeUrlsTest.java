package com.elicitsoftware.util;

/*-
 * ***LICENSE_START***
 * Elicit Survey
 * %%
 * Copyright (C) 2025 - 2026 Elicit Software LLC
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-005 BR-017, UC-015 BR-009: an address from a survey definition is used only when it is an
 * absolute http or https address.
 */
class SafeUrlsTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "https://example.org/after",
            "http://example.org/after",
            "HTTPS://Example.org/after?x=1&y=2#top",
            "http://localhost:8082/fhhs/report",
            "http://host.docker.internal:8082/api/report",
            "http://report_service:8080/report",
            "https://[::1]:8443/x",
            "  https://example.org/after  "})
    void anAbsoluteHttpAddressIsUsed(String address) {
        assertTrue(SafeUrls.httpUrl(address).isPresent(), address);
        assertEquals(address.strip(), SafeUrls.httpUrl(address).get().toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "javascript:alert(1)",
            "JavaScript:alert(1)",
            "data:text/html,<p>x</p>",
            "vbscript:x",
            "file:///etc/hosts",
            "ftp://example.org/x",
            "mailto:someone@example.org",
            "//example.org/after",
            "/thanks",
            "thanks.html",
            "example.org/after",
            "https:",
            "https://",
            "https:///after",
            "https:example.org",
            "https://user@example.org/after",
            "https://user:secret@example.org/after",
            "https://example.org/a b",
            "https://example.org/a\tb",
            "https://example.org/a\nb",
            "java\tscript:alert(1)",
            "https://exa mple.org/",
            "",
            "   "})
    void anyOtherAddressIsNotUsed(String address) {
        assertFalse(SafeUrls.httpUrl(address).isPresent(), address);
    }

    @Test
    void noAddressIsNotUsed() {
        assertFalse(SafeUrls.httpUrl(null).isPresent());
        assertFalse(SafeUrls.hasHttpScheme(null));
    }

    @Test
    void theSchemeOfAnAddressWithPlaceholdersCanBeChecked() {
        assertTrue(SafeUrls.hasHttpScheme("https://svc.example/r/{respondentId}"));
        assertTrue(SafeUrls.hasHttpScheme(" HTTP://svc.example/{externalId}"));
        assertFalse(SafeUrls.hasHttpScheme("ftp://svc.example/{respondentId}"));
        assertFalse(SafeUrls.hasHttpScheme("file:///tmp/{respondentId}"));
        assertFalse(SafeUrls.hasHttpScheme("svc.example/{respondentId}"));
    }
}
