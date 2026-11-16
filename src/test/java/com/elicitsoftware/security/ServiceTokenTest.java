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

import com.elicitsoftware.LogCapture;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-016: the platform service token as configuration. The startup checks (A2 to A5, BR-004), the
 * constant-time comparison (BR-004), which addresses the token is sent to (BR-007) and that the
 * value appears in no log line and no message (BR-005).
 */
class ServiceTokenTest {

    private static final String TOKEN = "0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String FHHS = "http://fhhs:8080";

    // ── A3, BR-004: the length check ──────────────────────────────────────────

    @Test
    void uc016_a3_aTokenShorterThan32CharactersStopsTheStart() {
        String shortToken = "only-22-characters-xyz";

        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> ServiceToken.of(shortToken, null));

        assertTrue(failure.getMessage().contains("ELICIT_SERVICE_TOKEN"), failure.getMessage());
        assertTrue(failure.getMessage().contains("at least 32 characters"), failure.getMessage());
        assertFalse(failure.getMessage().contains(shortToken), "BR-005: never the value");
        assertFalse(failure.getMessage().contains("22"), "nor its length");
    }

    @Test
    void uc016_br004_aTokenOfExactly32CharactersIsAccepted() {
        String token = "a".repeat(ServiceToken.MINIMUM_LENGTH);

        assertTrue(ServiceToken.of(token, null).configured());
        assertThrows(IllegalStateException.class, () -> ServiceToken.of("a".repeat(ServiceToken.MINIMUM_LENGTH - 1), null));
    }

    @Test
    void uc016_a3_theLengthIsMeasuredAfterTrimming() {
        assertThrows(IllegalStateException.class, () -> ServiceToken.of("   short-token" + " ".repeat(40), null));
    }

    @Test
    void uc016_a2_anUnsetOrBlankTokenIsNotAStartupFailure() {
        for (String unset : new String[]{null, "", "   ", "\t"}) {
            ServiceToken token = ServiceToken.of(unset, FHHS);

            assertFalse(token.configured());
            assertFalse(token.matches(""), "nothing matches an unset token");
            assertFalse(token.matches(null));
        }
    }

    // ── BR-004: comparison ────────────────────────────────────────────────────

    @Test
    void uc016_br004_onlyTheConfiguredValueMatches() {
        ServiceToken token = ServiceToken.of(TOKEN, null);

        assertTrue(token.matches(TOKEN));
        assertFalse(token.matches(TOKEN.substring(1)), "shorter");
        assertFalse(token.matches(TOKEN + "0"), "longer");
        assertFalse(token.matches(TOKEN.toUpperCase()), "another case");
        assertFalse(token.matches(" " + TOKEN), "the presented value is compared as sent");
        assertFalse(token.matches(""));
        assertFalse(token.matches(null));
    }

    @Test
    void uc016_br002_surroundingWhitespaceInTheConfigurationIsIgnored() {
        assertTrue(ServiceToken.of("  " + TOKEN + "\n", null).matches(TOKEN));
    }

    // ── A4: trusted origins are validated at startup ──────────────────────────

    @Test
    void uc016_a4_wellFormedOriginsAreAccepted() {
        ServiceToken token = ServiceToken.of(TOKEN, " http://fhhs:8080 , https://Reports.Example.org ,, http://[::1]:9000 ");

        assertEquals(3, token.trustedOrigins().size());
        assertTrue(token.trustedOrigins().contains(new ServiceToken.Origin("https", "reports.example.org", 443)));
    }

    @Test
    void uc016_a4_anEntryThatIsNotAnOriginStopsTheStart() {
        for (String entry : List.of(
                "http://fhhs:8080/",                // a path, even an empty one
                "http://fhhs:8080/proband/report",  // a path
                "http://fhhs:8080?x=1",             // a query
                "http://fhhs:8080#top",             // a fragment
                "http://user@fhhs:8080",            // user information
                "http://user:pw@fhhs:8080",
                "fhhs:8080",                        // no scheme
                "fhhs",
                "//fhhs:8080",
                "ftp://fhhs:8080",                  // not http(s)
                "http://",                          // no host
                "http://fhhs:8080.example",         // not a host and a port
                "http://fh hs:8080",                // not an address
                "*",
                "http://*.example.org")) {
            IllegalStateException failure = assertThrows(IllegalStateException.class,
                    () -> ServiceToken.of(TOKEN, FHHS + "," + entry), entry);

            assertTrue(failure.getMessage().contains("ELICIT_SERVICE_TRUSTED_ORIGINS"), failure.getMessage());
            assertTrue(failure.getMessage().contains("'" + entry + "'"), "the operator is told which entry: " + failure.getMessage());
            assertFalse(failure.getMessage().contains(TOKEN));
        }
    }

    // ── BR-007: which addresses receive the token ─────────────────────────────

    @Test
    void uc016_br007_anAddressAtATrustedOriginReceivesTheToken() {
        ServiceToken token = ServiceToken.of(TOKEN, "http://fhhs:8080,https://reports.example.org,http://plain.example.org");

        for (String address : List.of(
                "http://fhhs:8080/proband/report",
                "http://fhhs:8080",
                "http://fhhs:8080/familyhistory/generate?id=7#x",
                "HTTP://FHHS:8080/pedigree/report",
                "https://reports.example.org/r",
                "https://reports.example.org:443/r",
                "http://plain.example.org:80/r",
                "http://plain.example.org/r")) {
            assertEquals(Optional.of("Bearer " + TOKEN), token.authorizationFor(URI.create(address)), address);
        }
    }

    @Test
    void uc016_br007_nothingButOriginEqualityCounts() {
        ServiceToken token = ServiceToken.of(TOKEN, "http://fhhs:8080,https://reports.example.org");

        for (String address : List.of(
                "http://fhhs:8080@evil.example/",          // the trusted origin as user information
                "http://fhhs:8080.evil.example/",          // the trusted origin as a prefix
                "http://fhhs:8080:8080@evil.example/",
                "http://fhhs@fhhs:8080/",                  // user information, even at the trusted host
                "http://user:pw@fhhs:8080/",
                "http://fhhs:80800/",                      // another port
                "http://fhhs:8081/",
                "http://fhhs/",                            // the default port is not 8080
                "https://fhhs:8080/",                      // another scheme
                "http://fhhs.evil.example:8080/",          // a suffix
                "http://evilfhhs:8080/",                   // a prefix
                "http://fhhs.:8080/",                      // not the same name
                "http://evil.example/?next=http://fhhs:8080/",
                "http://evil.example/http://fhhs:8080/",
                "http://evil.example#@fhhs:8080/",
                "http://evil.example:8080/fhhs:8080",
                "https://reports.example.org.evil.example/r",
                "https://reports.example.org:8443/r",
                "http://reports.example.org/r",            // 80, not 443
                "ftp://fhhs:8080/",
                "//fhhs:8080/x",                           // no scheme
                "/proband/report",
                "fhhs:8080",
                "mailto:someone@fhhs",
                "http:///proband/report")) {               // no host
            assertEquals(Optional.empty(), token.authorizationFor(URI.create(address)), address);
            assertFalse(token.trusts(URI.create(address)), address);
        }
        assertEquals(Optional.empty(), token.authorizationFor(null));
    }

    @Test
    void uc016_br007_withNoTrustedOriginsTheTokenGoesNowhere() {
        ServiceToken token = ServiceToken.of(TOKEN, null);

        assertEquals(Optional.empty(), token.authorizationFor(URI.create("http://fhhs:8080/proband/report")));
        assertEquals(Optional.empty(), token.authorizationFor(URI.create("http://localhost:8080/api/etl/build")));
    }

    @Test
    void uc016_br007_aCallToATrustedOriginIsNotMadeWithoutAToken() {
        ServiceToken unset = ServiceToken.of(null, FHHS);

        ServiceToken.NotConfigured refused = assertThrows(ServiceToken.NotConfigured.class,
                () -> unset.authorizationFor(URI.create("http://fhhs:8080/proband/report")));
        assertEquals("service token not configured", refused.getMessage());
        assertEquals(Optional.empty(), unset.authorizationFor(URI.create("http://elsewhere:8080/r")),
                "a call that would carry no token anyway is unaffected");
    }

    // ── A2, A5, BR-005: what is logged at startup ─────────────────────────────

    @Test
    void uc016_a2_anUnsetTokenIsOneWarningNamingTheVariable() {
        try (LogCapture log = LogCapture.of(ServiceToken.class)) {
            ServiceToken.of(null, null).onStart(null);

            assertEquals(1, log.messages(LogCapture.WARN).size());
            assertTrue(log.contains(LogCapture.WARN, "ELICIT_SERVICE_TOKEN is not set"));
        }
    }

    @Test
    void uc016_a5_theDevelopmentTokenIsAWarning() {
        try (LogCapture log = LogCapture.of(ServiceToken.class)) {
            ServiceToken token = ServiceToken.of(ServiceToken.DEVELOPMENT_TOKEN, null);
            token.onStart(null);

            assertTrue(token.developmentToken());
            assertEquals(1, log.messages(LogCapture.WARN).size());
            assertTrue(log.contains(LogCapture.WARN, "well-known development value"));
            assertFalse(log.contains(LogCapture.WARN, ServiceToken.DEVELOPMENT_TOKEN), "BR-005: not even this one");
        }
    }

    @Test
    void uc016_br005_aSiteTokenIsNeverLogged() {
        try (LogCapture log = LogCapture.of(ServiceToken.class)) {
            ServiceToken token = ServiceToken.of(TOKEN, FHHS);
            token.onStart(null);
            token.matches("wrong-" + TOKEN);
            token.authorizationFor(URI.create("http://fhhs:8080/r"));

            assertFalse(token.developmentToken());
            for (Level level : new Level[]{LogCapture.ERROR, LogCapture.WARN, LogCapture.INFO, LogCapture.DEBUG}) {
                assertTrue(log.messages(level).isEmpty(), "a configured site token needs no log line: " + log.messages(level));
            }
            assertFalse(token.toString().contains(TOKEN), "no toString that prints it");
        }
    }

    @Test
    void uc016_a5_theDevelopmentTokenIsLongEnoughToStart() {
        assertTrue(ServiceToken.DEVELOPMENT_TOKEN.length() >= ServiceToken.MINIMUM_LENGTH);
        assertTrue(TestServiceToken.VALUE.length() >= ServiceToken.MINIMUM_LENGTH);
    }
}
