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

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UC-009 BR-001: the provider's classpath-only resolution (Survey#123 removed the filesystem
 * mount and local-directory tiers; {@code vaadin-i18n/translations[_tag].properties} on the
 * classpath is now the only source). {@code bundledLocales} is package-private (the BrandUtilTest
 * pattern) so it can be set directly without CDI. The English bundle is authored in
 * {@code src/main/resources}; the Arabic and Spanish (es-419) bundles are received from a
 * translator under {@code i18n/} and packaged onto the classpath by the {@code <resource>} block
 * in {@code pom.xml} — that packaging step is what {@link #bundledLocalesAndPackaging_offerRealTranslations()}
 * exists to catch if it ever breaks.
 */
class ElicitI18NProviderTest {

    private static final Locale ES_419 = Locale.forLanguageTag("es-419");
    private static final Locale AR = Locale.forLanguageTag("ar");
    private static final Locale AR_EG = Locale.forLanguageTag("ar-EG");
    private static final Locale FR = Locale.FRENCH;

    private static ElicitI18NProvider provider(String bundledLocales) {
        ElicitI18NProvider p = new ElicitI18NProvider();
        p.bundledLocales = bundledLocales;
        p.pseudoLocaleEnabled = false;
        return p;
    }

    @Test
    void unknownLanguage_fallsBackToEnglish() {
        // "fr" has no bundle at all on the classpath: exact tag misses, language-only misses
        // (same tag), so this exercises the final tier landing on English.
        ElicitI18NProvider p = provider("en");

        assertEquals("About", p.getTranslation("sideNav.about", FR));
    }

    @Test
    void countryLocale_fallsBackToLanguageBundle() {
        // "ar-EG" has no bundle of its own; it must fall back to translations_ar.properties
        // rather than skipping straight to English.
        ElicitI18NProvider p = provider("en,ar");

        assertEquals("حول", p.getTranslation("sideNav.about", AR_EG));
    }

    @Test
    void unknownKey_rendersVisibleMarker() {
        ElicitI18NProvider p = provider("en,ar");

        assertEquals("!no.such.key!", p.getTranslation("no.such.key", Locale.ENGLISH));
        assertEquals("!no.such.key!", p.getTranslation("no.such.key", AR));
    }

    @Test
    void parameters_areFormatted_onlyWhenGiven() {
        ElicitI18NProvider p = provider("en");

        assertEquals("Survey: Elicit Demo", p.getTranslation("reviewView.surveyLabel", Locale.ENGLISH, "Elicit Demo"));
        assertEquals("Failed to generate PDF: {0}", p.getTranslation("reportView.error.pdf", Locale.ENGLISH),
                "no params given: the value is returned as-is, not run through MessageFormat");
    }

    @Test
    void getAllTranslations_mergesLanguageOverEnglish() {
        ElicitI18NProvider p = provider("en,ar");

        assertEquals("About", p.getAllTranslations(Locale.ENGLISH).get("sideNav.about"),
                "the default locale itself gets no overlay");
        assertEquals("حول", p.getAllTranslations(AR).get("sideNav.about"),
                "a non-default locale overlays its bundle onto the English base");
    }

    @Test
    void pseudoLocale_marksEveryKnownKey() {
        ElicitI18NProvider p = provider("en");
        p.pseudoLocaleEnabled = true;

        assertTrue(p.getProvidedLocales().contains(ElicitI18NProvider.PSEUDO_LOCALE));
        assertEquals("⟦mainView.btnLogin⟧", p.getTranslation("mainView.btnLogin", ElicitI18NProvider.PSEUDO_LOCALE));
        assertEquals("!no.such.key!", p.getTranslation("no.such.key", ElicitI18NProvider.PSEUDO_LOCALE));
        assertTrue(p.getAllTranslations(ElicitI18NProvider.PSEUDO_LOCALE).values().stream()
                .allMatch(v -> v.startsWith("⟦")));
    }

    @Test
    void bundledLocalesAndPackaging_offerRealTranslations() {
        // With no filesystem tier left, this is the only thing standing between a broken pom.xml
        // <resource> mapping and a silent English-only release: it reads the real packaged
        // translations_ar.properties / translations_es_419.properties, not a fixture.
        ElicitI18NProvider p = provider("en,ar,es-419");

        List<Locale> locales = p.getProvidedLocales();
        assertEquals(Locale.ENGLISH, locales.get(0), "English sorts first");
        assertTrue(locales.contains(AR), "ar must be offered: " + locales);
        assertTrue(locales.contains(ES_419), "es-419 must be offered: " + locales);

        assertEquals("حول", p.getTranslation("sideNav.about", AR));
        assertEquals("Acerca de", p.getTranslation("sideNav.about", ES_419));
    }

    @Test
    void bundledLocales_narrowsOfferedLocales_butNotWhatIsLoadable() {
        // A site narrows i18n.bundled.locales to hide a language from the selector; the bundle
        // stays in the image either way (classpath resources cannot be deleted per-deployment).
        ElicitI18NProvider p = provider("en,ar,es-419");
        assertTrue(p.getProvidedLocales().contains(AR), "sanity: starts wide");

        p.bundledLocales = "en";
        p.clearCache(); // getProvidedLocales() caches in providedLocales; must clear to re-discover

        List<Locale> locales = p.getProvidedLocales();
        assertEquals(List.of(Locale.ENGLISH), locales, "narrowed site offers only English: " + locales);
        assertEquals("حول", p.getTranslation("sideNav.about", AR),
                "the ar bundle is still on the classpath -- narrowing only trims what is offered");
    }
}
