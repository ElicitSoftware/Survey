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
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-009 BR-004 and BR-011: built-in right-to-left languages, the per-locale font scale, the
 * classpath manifest the image ships, and the per-deployment property that overrides either.
 */
class LocaleConfigTest {

    /** No overrides; only the shipped classpath {@code i18n-config.json} (declares only {@code ar}) applies. */
    private static LocaleConfig config() {
        return new LocaleConfig();
    }

    /** {@code overrides} stands in for a deployment's configuration; a key it does not list is absent. */
    private static LocaleConfig config(Map<String, String> overrides) {
        LocaleConfig c = new LocaleConfig();
        c.propertyLookup = key -> Optional.ofNullable(overrides.get(key));
        return c;
    }

    @Test
    void builtInDefaults() {
        LocaleConfig c = config();
        assertTrue(c.isRightToLeft(Locale.forLanguageTag("he")), "inferred from RTL_LANGUAGES; not in the shipped file");
        assertTrue(c.isRightToLeft(Locale.forLanguageTag("fa")));
        assertFalse(c.isRightToLeft(Locale.forLanguageTag("es-419")));
        assertFalse(c.isRightToLeft(Locale.ENGLISH));
        assertFalse(c.isRightToLeft(null));
    }

    @Test
    void fontScaleDefaultsToOne() {
        LocaleConfig c = config();
        assertEquals(1.0, c.fontScale(Locale.ENGLISH));
        assertEquals(1.0, c.fontScale(Locale.forLanguageTag("he")));
        assertEquals(1.0, c.fontScale(null));
    }

    @Test
    void shippedFileDeclaresArabic() {
        // META-INF/i18n/i18n-config.json on the classpath: {"tag":"ar","direction":"rtl","fontScale":1.15}
        LocaleConfig c = config();
        assertTrue(c.isRightToLeft(Locale.forLanguageTag("ar")));
        assertEquals(1.15, c.fontScale(Locale.forLanguageTag("ar")));
        assertEquals(1.15, c.fontScale(Locale.forLanguageTag("ar-EG")), "a country variant inherits the language's entry");
        assertEquals(1.0, c.fontScale(Locale.ENGLISH), "other languages are untouched by the shipped entry");
    }

    @Test
    void propertyOverridesDirection() {
        LocaleConfig c = config(Map.of(
                "i18n.direction.fr", "rtl",
                "i18n.direction.ar", "ltr"));

        assertTrue(c.isRightToLeft(Locale.FRENCH), "a property can declare rtl for a language nothing else does");
        assertFalse(c.isRightToLeft(Locale.forLanguageTag("ar")), "a property wins over the shipped file's declaration");
    }

    @Test
    void propertyOverridesFontScale() {
        LocaleConfig c = config(Map.of("i18n.font-scale.ar", "1.4"));

        assertEquals(1.4, c.fontScale(Locale.forLanguageTag("ar")), "a property wins over the shipped file's 1.15");
        assertTrue(c.isRightToLeft(Locale.forLanguageTag("ar")), "overriding the scale alone leaves direction to the shipped file");
    }

    @Test
    void languageOnlyPropertyAppliesToARegionalTag() {
        LocaleConfig c = config(Map.of(
                "i18n.direction.es", "rtl",
                "i18n.font-scale.es", "1.2"));

        assertTrue(c.isRightToLeft(Locale.forLanguageTag("es-419")), "no es-419 key; the language-only key applies");
        assertEquals(1.2, c.fontScale(Locale.forLanguageTag("es-419")));
    }

    @Test
    void exactTagPropertyWinsOverTheLanguageProperty() {
        LocaleConfig c = config(Map.of(
                "i18n.font-scale.ar", "1.1",
                "i18n.font-scale.ar-EG", "1.3"));

        assertEquals(1.3, c.fontScale(Locale.forLanguageTag("ar-EG")));
        assertEquals(1.1, c.fontScale(Locale.forLanguageTag("ar-SA")), "no exact key for ar-SA; falls back to the language");
    }

    @Test
    void outOfRangeFontScaleProperty_isIgnored() {
        LocaleConfig c = config(Map.of(
                "i18n.font-scale.fr", "9",
                "i18n.font-scale.ar", "0.1"));

        assertEquals(1.0, c.fontScale(Locale.FRENCH), "above the maximum; the default stands");
        assertEquals(1.15, c.fontScale(Locale.forLanguageTag("ar")), "below the minimum; the shipped file's value stands");
    }

    @Test
    void nonNumericFontScaleProperty_isIgnored() {
        LocaleConfig c = config(Map.of("i18n.font-scale.ar", "huge"));
        assertEquals(1.15, c.fontScale(Locale.forLanguageTag("ar")), "not a number; the shipped file's value stands");
    }

    @Test
    void fontScaleBoundariesAreAccepted() {
        LocaleConfig c = config(Map.of(
                "i18n.font-scale.he", "0.75",
                "i18n.font-scale.fa", "2.0"));

        assertEquals(0.75, c.fontScale(Locale.forLanguageTag("he")));
        assertEquals(2.0, c.fontScale(Locale.forLanguageTag("fa")));
    }

    @Test
    void nonsenseDirectionProperty_isIgnored() {
        LocaleConfig c = config(Map.of("i18n.direction.fr", "sideways"));
        assertFalse(c.isRightToLeft(Locale.FRENCH), "neither \"rtl\" nor \"ltr\"; the built-in inference stands");
    }

    @Test
    void blankPropertyValue_isTreatedAsAbsent() {
        LocaleConfig c = config(Map.of("i18n.direction.ar", "   "));
        assertTrue(c.isRightToLeft(Locale.forLanguageTag("ar")), "a blank value is absent, not a rejected malformed one");
    }

    @Test
    void throwingLookup_isSurvivedAsAbsent() {
        LocaleConfig c = new LocaleConfig();
        c.propertyLookup = key -> {
            throw new RuntimeException("boom");
        };

        // must never take the page down over a bad property; falls through to the shipped file
        assertTrue(c.isRightToLeft(Locale.forLanguageTag("ar")));
        assertEquals(1.15, c.fontScale(Locale.forLanguageTag("ar")));
    }

    @Test
    void scaleIsFormattedForCss() {
        assertEquals("1", LocaleLayout.format(1.0));
        assertEquals("1.15", LocaleLayout.format(1.15));
        assertEquals("0.75", LocaleLayout.format(0.75));
        assertEquals("2", LocaleLayout.format(2.0));
    }

    // ---- the shipped manifest itself (UC-009 BR-004, BR-011) ----
    // Through parse(), because the manifest is a fixed classpath resource: there is no longer a
    // way to hand the running code a different one. It still runs at every startup, so a bad
    // document must leave the built-in defaults standing rather than take the application down.

    @Test
    void parse_readsDirectionAndScale() {
        Map<String, LocaleConfig.Entry> entries =
                LocaleConfig.parse("{\"locales\":[{\"tag\":\"ar\",\"direction\":\"rtl\",\"fontScale\":1.15}]}");
        assertEquals(1, entries.size());
        assertEquals(Boolean.TRUE, entries.get("ar").rightToLeft());
        assertEquals(1.15, entries.get("ar").fontScale());
    }

    @Test
    void parse_keepsAnEntryThatDeclaresOnlyOneOfThem() {
        assertNull(LocaleConfig.parse("{\"locales\":[{\"tag\":\"he\",\"direction\":\"rtl\"}]}")
                .get("he").fontScale(), "a direction-only entry leaves the scale undeclared");
        assertNull(LocaleConfig.parse("{\"locales\":[{\"tag\":\"ar\",\"fontScale\":1.2}]}")
                .get("ar").rightToLeft(), "a scale-only entry leaves the direction undeclared");
    }

    @Test
    void parse_dropsAnEntryThatDeclaresNeitherUsably() {
        // Nothing to say is not the same as saying the default: an entry like this must not
        // shadow the built-in inference for that language.
        assertTrue(LocaleConfig.parse("{\"locales\":[{\"tag\":\"ar\"}]}").isEmpty());
        assertTrue(LocaleConfig.parse("{\"locales\":[{\"tag\":\"ar\",\"direction\":\"sideways\"}]}").isEmpty());
        assertTrue(LocaleConfig.parse("{\"locales\":[{\"direction\":\"rtl\"}]}").isEmpty(), "no tag names nothing");
    }

    @Test
    void parse_survivesAMalformedManifest() {
        assertTrue(LocaleConfig.parse("{ not json").isEmpty());
        assertTrue(LocaleConfig.parse("").isEmpty());
        assertTrue(LocaleConfig.parse(null).isEmpty());
        assertTrue(LocaleConfig.parse("{}").isEmpty(), "no locales key at all");
    }

    @Test
    void parse_refusesAScaleOutsideWhatALayoutAbsorbs() {
        assertTrue(LocaleConfig.parse("{\"locales\":[{\"tag\":\"ar\",\"fontScale\":9}]}").isEmpty());
        assertTrue(LocaleConfig.parse("{\"locales\":[{\"tag\":\"ar\",\"fontScale\":0.1}]}").isEmpty());
    }
}
