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

import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * VersionView.formatImageCreationDate() is the one method on this class with no
 * hostname/docker-inspect subprocess dependency (see the comment on the method itself,
 * loosened to package-private specifically for this test) -- everything else on VersionView
 * shells out to external processes and isn't safely testable without mocking a subprocess,
 * which this project deliberately doesn't chase. Expected values are computed with the exact
 * same ZoneId.systemDefault() conversion the production code uses, so these tests are
 * portable across whatever timezone they happen to run in.
 */
class VersionViewFormatImageCreationDateTest {

    private static String containerTimeString(String isoInstant) {
        return ZonedDateTime.parse(isoInstant).withZoneSameInstant(ZoneId.systemDefault()).toString();
    }

    @Test
    void formatImageCreationDate_dockerInspectStyleWithNanos_convertsToSystemZone() {
        VersionView view = new VersionView();
        String result = view.formatImageCreationDate("2025-07-24 15:54:44.626850013 +0000");

        assertEquals(containerTimeString("2025-07-24T15:54:44Z"), result);
    }

    @Test
    void formatImageCreationDate_isoWithNanosAndZ_convertsToSystemZone() {
        // Unlike the docker-inspect-style format above, this path parses the full string
        // directly (ZonedDateTime.parse(rawDate)) without stripping nanoseconds first, so the
        // expected value must retain them too.
        VersionView view = new VersionView();
        String result = view.formatImageCreationDate("2025-07-24T15:54:44.626850013Z");

        assertEquals(containerTimeString("2025-07-24T15:54:44.626850013Z"), result);
    }

    @Test
    void formatImageCreationDate_shortIsoWithZ_convertsToSystemZone() {
        VersionView view = new VersionView();
        String result = view.formatImageCreationDate("2025-07-24T15:54:44Z");

        assertEquals(containerTimeString("2025-07-24T15:54:44Z"), result);
    }

    @Test
    void formatImageCreationDate_unrecognizedFormat_returnsInputUnchanged() {
        VersionView view = new VersionView();
        String result = view.formatImageCreationDate("not a date at all");

        assertEquals("not a date at all", result);
    }

    @Test
    void formatImageCreationDate_dockerStyleWithInvalidCalendarValues_fallsBackToRawInput() {
        VersionView view = new VersionView();
        // Matches the docker-inspect regex shape but month 13 makes ZonedDateTime.parse throw,
        // exercising the inner catch-and-fall-back-to-original-string branch.
        String raw = "2025-13-45 25:99:99.000 +0000";

        assertEquals(raw, view.formatImageCreationDate(raw));
    }

    @Test
    void formatImageCreationDate_nullInput_isCaughtAndReturnsNull() {
        VersionView view = new VersionView();
        assertNull(view.formatImageCreationDate(null));
    }
}
