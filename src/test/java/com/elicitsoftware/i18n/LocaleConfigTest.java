package com.elicitsoftware.i18n;

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
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-009 BR-004 and BR-011: built-in right-to-left languages, the per-locale font scale, and the
 * optional per-deployment manifest that overrides either.
 */
class LocaleConfigTest {

    private static LocaleConfig config(Path mount) {
        LocaleConfig c = new LocaleConfig();
        c.fileSystemPath = mount.toString();
        c.localPath = mount.resolve("no-local").toString();
        return c;
    }

    private static LocaleConfig config(Path mount, String json) throws IOException {
        Files.writeString(mount.resolve("i18n-config.json"), json);
        return config(mount);
    }

    @Test
    void builtInDefaults(@TempDir Path mount) {
        LocaleConfig c = config(mount);
        assertTrue(c.isRightToLeft(Locale.forLanguageTag("ar")));
        assertTrue(c.isRightToLeft(Locale.forLanguageTag("ar-EG")));
        assertTrue(c.isRightToLeft(Locale.forLanguageTag("he")));
        assertFalse(c.isRightToLeft(Locale.forLanguageTag("es-419")));
        assertFalse(c.isRightToLeft(Locale.ENGLISH));
        assertFalse(c.isRightToLeft(null));
    }

    @Test
    void manifestOverridesDirection(@TempDir Path mount) throws IOException {
        LocaleConfig c = config(mount,
                "{\"locales\":[{\"tag\":\"fr\",\"direction\":\"rtl\"},{\"tag\":\"ar\",\"direction\":\"ltr\"}]}");

        assertTrue(c.isRightToLeft(Locale.FRENCH), "manifest can declare a mounted locale rtl");
        assertFalse(c.isRightToLeft(Locale.forLanguageTag("ar")), "manifest can override the built-in default");
    }

    @Test
    void malformedManifest_keepsBuiltInDefaults(@TempDir Path mount) throws IOException {
        LocaleConfig c = config(mount, "{not json");
        assertTrue(c.isRightToLeft(Locale.forLanguageTag("ar")));
        assertEquals(1.0, c.fontScale(Locale.forLanguageTag("ar")));
    }

    @Test
    void fontScaleDefaultsToOne(@TempDir Path mount) {
        LocaleConfig c = config(mount);
        assertEquals(1.0, c.fontScale(Locale.forLanguageTag("ar")));
        assertEquals(1.0, c.fontScale(Locale.ENGLISH));
        assertEquals(1.0, c.fontScale(null));
    }

    @Test
    void manifestDeclaresFontScale(@TempDir Path mount) throws IOException {
        LocaleConfig c = config(mount,
                "{\"locales\":[{\"tag\":\"ar\",\"direction\":\"rtl\",\"fontScale\":1.15}]}");

        assertEquals(1.15, c.fontScale(Locale.forLanguageTag("ar")));
        assertEquals(1.15, c.fontScale(Locale.forLanguageTag("ar-EG")), "a country variant inherits the language's scale");
        assertTrue(c.isRightToLeft(Locale.forLanguageTag("ar")), "the entry still carries its direction");
        assertEquals(1.0, c.fontScale(Locale.ENGLISH), "other languages are untouched");
    }

    @Test
    void fontScaleWithoutDirection_leavesDirectionAtItsDefault(@TempDir Path mount) throws IOException {
        LocaleConfig c = config(mount,
                "{\"locales\":[{\"tag\":\"ar\",\"fontScale\":1.2},{\"tag\":\"es-419\",\"fontScale\":1.1}]}");

        assertEquals(1.2, c.fontScale(Locale.forLanguageTag("ar")));
        assertTrue(c.isRightToLeft(Locale.forLanguageTag("ar")), "the built-in rtl default still stands");
        assertEquals(1.1, c.fontScale(Locale.forLanguageTag("es-419")));
        assertFalse(c.isRightToLeft(Locale.forLanguageTag("es-419")));
    }

    @Test
    void directionOnlyEntry_leavesFontScaleAtOne(@TempDir Path mount) throws IOException {
        LocaleConfig c = config(mount, "{\"locales\":[{\"tag\":\"ar\",\"direction\":\"rtl\"}]}");

        assertEquals(1.0, c.fontScale(Locale.forLanguageTag("ar")),
                "a mount written before font scale existed renders exactly as it did");
        assertTrue(c.isRightToLeft(Locale.forLanguageTag("ar")));
    }

    @Test
    void exactTagWinsOverTheLanguage(@TempDir Path mount) throws IOException {
        LocaleConfig c = config(mount,
                "{\"locales\":[{\"tag\":\"ar\",\"fontScale\":1.1},{\"tag\":\"ar-EG\",\"fontScale\":1.3}]}");

        assertEquals(1.3, c.fontScale(Locale.forLanguageTag("ar-EG")));
        assertEquals(1.1, c.fontScale(Locale.forLanguageTag("ar-SA")));
    }

    @Test
    void outOfRangeOrMalformedFontScale_fallsBackToOne(@TempDir Path mount) throws IOException {
        LocaleConfig c = config(mount, "{\"locales\":["
                + "{\"tag\":\"ar\",\"direction\":\"rtl\",\"fontScale\":9},"
                + "{\"tag\":\"he\",\"fontScale\":0.1},"
                + "{\"tag\":\"fa\",\"fontScale\":\"huge\"},"
                + "{\"tag\":\"ur\",\"fontScale\":0}]}");

        assertEquals(1.0, c.fontScale(Locale.forLanguageTag("ar")), "above the maximum");
        assertEquals(1.0, c.fontScale(Locale.forLanguageTag("he")), "below the minimum");
        assertEquals(1.0, c.fontScale(Locale.forLanguageTag("fa")), "not a number");
        assertEquals(1.0, c.fontScale(Locale.forLanguageTag("ur")), "zero would erase the page");
        assertTrue(c.isRightToLeft(Locale.forLanguageTag("ar")),
                "a refused scale does not cost the entry its direction");
    }

    @Test
    void fontScaleBoundariesAreAccepted(@TempDir Path mount) throws IOException {
        LocaleConfig c = config(mount,
                "{\"locales\":[{\"tag\":\"ar\",\"fontScale\":0.75},{\"tag\":\"he\",\"fontScale\":2.0}]}");

        assertEquals(0.75, c.fontScale(Locale.forLanguageTag("ar")));
        assertEquals(2.0, c.fontScale(Locale.forLanguageTag("he")));
    }

    @Test
    void fontScaleAsAString_isAccepted(@TempDir Path mount) throws IOException {
        LocaleConfig c = config(mount, "{\"locales\":[{\"tag\":\"ar\",\"fontScale\":\"1.15\"}]}");

        assertEquals(1.15, c.fontScale(Locale.forLanguageTag("ar")));
    }

    @Test
    void scaleIsFormattedForCss() {
        assertEquals("1", LocaleLayout.format(1.0));
        assertEquals("1.15", LocaleLayout.format(1.15));
        assertEquals("0.75", LocaleLayout.format(0.75));
        assertEquals("2", LocaleLayout.format(2.0));
    }
}
