package com.elicitsoftware.i18n;

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

import com.elicitsoftware.i18n.PlainVaadin.PlainUi;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.i18n.LocaleChangeEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-009: Complete the Survey in My Language, step 4 (BR-002, BR-003) -- the language selector
 * offers the site's languages in their own names, hides itself when there is nothing to choose,
 * follows a locale change, and applies a respondent's choice and reloads without {@code ?lang=}.
 * <p>
 * Plain: the UI is a {@link PlainUi}, and a respondent's pick is simulated through the field's
 * client-side model update, which is what the browser's selection arrives as.
 */
class LanguageSwitcherTest {

    private static final Locale SPANISH = Locale.forLanguageTag("es-419");
    private static final Locale ARABIC = Locale.forLanguageTag("ar");

    private ElicitI18NProvider provider;
    private PlainUi ui;
    private final List<Locale> applied = new ArrayList<>();
    private final LocaleSelection selection = new LocaleSelection() {
        @Override
        public void apply(UI target, Locale locale) {
            assertSame(ui, target);
            applied.add(locale);
        }
    };

    /** Exposes the client-side value update a respondent's pick arrives as. */
    private final class PickableSwitcher extends LanguageSwitcher {
        PickableSwitcher() {
            super(selection, provider);
        }

        void pick(Locale locale) {
            setModelValue(locale, true);
        }
    }

    @BeforeEach
    void setUp() {
        provider = new ElicitI18NProvider();
        provider.bundledLocales = "en,es-419,ar";
        provider.pseudoLocaleEnabled = true;
        ui = PlainUi.of(Locale.ENGLISH, null);
    }

    @AfterEach
    void tearDown() {
        PlainVaadin.clear();
    }

    @Test
    void given_severalLanguages_when_built_then_visibleWithOfferedLocalesExceptPseudo() {
        LanguageSwitcher switcher = new LanguageSwitcher(selection, provider);

        assertEquals(LanguageSwitcher.ID, switcher.getId().orElseThrow());
        assertTrue(switcher.hasClassName("language-switcher"));
        assertTrue(switcher.isVisible());
        assertEquals(List.of(Locale.ENGLISH, ARABIC, SPANISH), switcher.getListDataView().getItems().toList(),
                "the pseudo-locale is a test device and is never offered to a respondent");
        assertTrue(switcher.getAriaLabel().orElseThrow().contains("common.language"),
                "the selector is labelled through the translation key");
    }

    @Test
    void given_englishOnly_when_built_then_hidden() {
        provider.bundledLocales = "en";
        LanguageSwitcher switcher = new LanguageSwitcher(selection, provider);

        assertFalse(switcher.isVisible(), "a site offering English alone has no choice to offer");
    }

    @Test
    void given_uiLocale_when_built_then_matchingLanguageSelected() {
        ui.setLocale(Locale.forLanguageTag("es-MX"));
        LanguageSwitcher switcher = new LanguageSwitcher(selection, provider);

        assertEquals(SPANISH, switcher.getValue());
    }

    @Test
    void given_noCurrentUi_when_built_then_nothingSelected() {
        PlainVaadin.clear();
        LanguageSwitcher switcher = new LanguageSwitcher(selection, provider);

        assertNull(switcher.getValue());
    }

    @Test
    void given_items_when_labelled_then_eachInItsOwnLanguage() {
        LanguageSwitcher switcher = new LanguageSwitcher(selection, provider);

        assertEquals("English", switcher.getItemLabelGenerator().apply(Locale.ENGLISH));
        assertEquals(LanguageSwitcher.displayName(ARABIC), switcher.getItemLabelGenerator().apply(ARABIC));
    }

    @Test
    void given_respondentPicksLanguage_when_selected_then_appliedAndPageReloadedWithoutLangParameter() {
        PickableSwitcher switcher = new PickableSwitcher();

        switcher.pick(ARABIC);

        assertEquals(List.of(ARABIC), applied);
        assertEquals(1, ui.page.executed.size());
        String reload = ui.page.executed.getFirst().expression();
        assertTrue(reload.contains("searchParams.delete('lang')") && reload.contains("location.replace"),
                "the reload must drop ?lang=, or the link's language would win again: " + reload);
    }

    @Test
    void given_respondentClearsSelection_when_cleared_then_nothingApplied() {
        PickableSwitcher switcher = new PickableSwitcher();

        switcher.pick(null);

        assertTrue(applied.isEmpty());
        assertTrue(ui.page.executed.isEmpty());
    }

    @Test
    void given_serverSideValueChange_when_set_then_notAppliedAsAChoice() {
        LanguageSwitcher switcher = new LanguageSwitcher(selection, provider);

        switcher.setValue(ARABIC);

        assertTrue(applied.isEmpty(), "only a respondent's own choice is remembered and reloads the page");
        assertTrue(ui.page.executed.isEmpty());
    }

    @Test
    void given_localeChange_when_notified_then_selectionAndLabelFollow() {
        LanguageSwitcher switcher = new LanguageSwitcher(selection, provider);
        assertEquals(Locale.ENGLISH, switcher.getValue());
        String label = switcher.getAriaLabel().orElseThrow();
        switcher.setAriaLabel("stale");

        switcher.localeChange(new LocaleChangeEvent(ui, ARABIC));

        assertEquals(ARABIC, switcher.getValue());
        assertEquals(label, switcher.getAriaLabel().orElseThrow(), "the aria label is re-translated");
        assertNotEquals("stale", label);
        assertTrue(applied.isEmpty(), "following a locale change is not a respondent's choice");
    }

    @Test
    void given_localeChangeToUnofferedLanguage_when_notified_then_selectionKept() {
        LanguageSwitcher switcher = new LanguageSwitcher(selection, provider);

        switcher.localeChange(new LocaleChangeEvent(ui, Locale.FRENCH));

        assertEquals(Locale.ENGLISH, switcher.getValue());
    }

    // ------------------------------------------------------------------ static helpers

    @Test
    void given_noWantedLocale_when_matching_then_null() {
        assertNull(LanguageSwitcher.current(List.of(Locale.ENGLISH), null));
    }

    @Test
    void given_offeredLocale_when_matching_then_exactLocale() {
        assertEquals(SPANISH, LanguageSwitcher.current(List.of(Locale.ENGLISH, SPANISH), SPANISH));
    }

    @Test
    void given_otherRegion_when_matching_then_sameLanguageLocale() {
        assertEquals(SPANISH, LanguageSwitcher.current(List.of(Locale.ENGLISH, SPANISH),
                Locale.forLanguageTag("es-ES")));
    }

    @Test
    void given_unofferedLanguage_when_matching_then_null() {
        assertNull(LanguageSwitcher.current(List.of(Locale.ENGLISH, SPANISH), Locale.FRENCH));
    }

    @Test
    void given_locale_when_namingIt_then_ownLanguageCapitalised() {
        assertEquals("English", LanguageSwitcher.displayName(Locale.ENGLISH));
        String spanish = LanguageSwitcher.displayName(SPANISH);
        assertTrue(spanish.startsWith("Español"), spanish);
    }

    @Test
    void given_localeWithoutName_when_namingIt_then_languageTag() {
        assertEquals("und", LanguageSwitcher.displayName(Locale.ROOT));
    }
}
