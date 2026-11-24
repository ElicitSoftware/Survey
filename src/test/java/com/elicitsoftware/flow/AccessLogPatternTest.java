package com.elicitsoftware.flow;

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

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * NFR-015: the access log records the method, the path and the protocol of a request, never its
 * query string or the Referer, and leaves out a request for {@code /login/<access code>}
 * altogether. The pattern is configuration, so this reads {@code application.properties} as
 * packaged; a later edit that brings the request line or the Referer back fails here.
 */
class AccessLogPatternTest {

    private static final Properties CONFIG = new Properties();

    @BeforeAll
    static void load() throws IOException {
        try (InputStream in = AccessLogPatternTest.class.getResourceAsStream("/application.properties")) {
            assertNotNull(in, "application.properties is on the classpath");
            CONFIG.load(in);
        }
    }

    @Test
    void theAccessLogIsOnInEveryProfile() {
        assertEquals("true", CONFIG.getProperty("quarkus.http.access-log.enabled"));
    }

    @Test
    void thePatternLogsThePathWithoutTheQueryStringOrTheReferer() {
        String pattern = CONFIG.getProperty("quarkus.http.access-log.pattern");
        assertNotNull(pattern);
        assertTrue(pattern.contains("%m %R %H"), "method, path and protocol");
        assertFalse(pattern.contains("%r"), "the request line carries the query string");
        assertFalse(pattern.contains("%U"), "the request URL carries the query string");
        assertFalse(pattern.contains("%q"), "the query string");
        assertFalse(pattern.toLowerCase().contains("referer"), "the Referer header");
    }

    @Test
    void aLoginLinkThatCarriesAnAccessCodeIsNotLogged() {
        Pattern exclude = Pattern.compile(CONFIG.getProperty("quarkus.http.access-log.exclude-pattern"));
        assertTrue(exclude.matcher("/login/ABCDE12345").matches());
        assertTrue(exclude.matcher("/login/abcde").matches());
        assertFalse(exclude.matcher("/login").matches(), "the login page itself is logged");
        assertFalse(exclude.matcher("/login/").matches());
        assertFalse(exclude.matcher("/section").matches());
        assertFalse(exclude.matcher("/").matches());
    }
}
