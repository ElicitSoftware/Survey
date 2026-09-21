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

import com.elicitsoftware.PostgresTestResource;
import com.elicitsoftware.flow.MainView;
import com.vaadin.browserless.quarkus.QuarkusBrowserlessTest;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.router.QueryParameters;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UC-008 BR-003/BR-004: direction and language attributes follow the locale, the invitation
 * link's {@code ?lang=} selects a language before the view is built, and the language switcher
 * offers the shipped locales.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class LocaleLayoutTest extends QuarkusBrowserlessTest {

    @Inject
    LocaleLayout layout;

    @Inject
    LocaleSelection selection;

    @Test
    void arabic_isRightToLeft() {
        UI ui = UI.getCurrent();
        layout.apply(ui, Locale.forLanguageTag("ar"));

        assertEquals("rtl", ui.getElement().getAttribute("dir"));
        assertEquals("ar", ui.getElement().getAttribute("lang"));
    }

    @Test
    void latinAmericanSpanish_isLeftToRight() {
        UI ui = UI.getCurrent();
        layout.apply(ui, Locale.forLanguageTag("es-419"));

        assertEquals("ltr", ui.getElement().getAttribute("dir"));
        assertEquals("es-419", ui.getElement().getAttribute("lang"));
    }

    @Test
    void langQueryParameter_selectsLanguageAndRemembersIt() {
        UI.getCurrent().navigate("login", QueryParameters.of("lang", "ar"));
        assertInstanceOf(MainView.class, getCurrentView());

        UI ui = UI.getCurrent();
        assertEquals("ar", ui.getLocale().toLanguageTag());
        assertEquals("rtl", ui.getElement().getAttribute("dir"));
        assertEquals(Locale.forLanguageTag("ar"), ui.getSession().getAttribute(LocaleSelection.SESSION_ATTRIBUTE));
    }

    @Test
    void unsupportedLangQueryParameter_isIgnored() {
        Locale before = UI.getCurrent().getLocale();
        UI.getCurrent().navigate("login", QueryParameters.of("lang", "xx-YY"));
        assertInstanceOf(MainView.class, getCurrentView());

        assertEquals(before, UI.getCurrent().getLocale());
        assertNull(UI.getCurrent().getSession().getAttribute(LocaleSelection.SESSION_ATTRIBUTE));
    }

    @Test
    void countryVariant_resolvesToProvidedLanguage() {
        assertEquals(Locale.forLanguageTag("es-419"), selection.resolve("es-GT").orElseThrow());
        assertEquals(Locale.forLanguageTag("ar"), selection.resolve("ar-EG").orElseThrow());
        assertTrue(selection.resolve("zz").isEmpty());
    }

    @Test
    void switcher_listsShippedLocales_andTracksCurrent() {
        navigate(MainView.class);
        LanguageSwitcher switcher = find(LanguageSwitcher.class).single();

        var items = switcher.getListDataView().getItems().toList();
        assertTrue(items.contains(Locale.ENGLISH), items.toString());
        assertTrue(items.contains(Locale.forLanguageTag("es-419")), items.toString());
        assertTrue(items.contains(Locale.forLanguageTag("ar")), items.toString());
        assertFalse(items.contains(ElicitI18NProvider.PSEUDO_LOCALE), "pseudo-locale is never offered to users");
        assertEquals(UI.getCurrent().getLocale().getLanguage(), switcher.getValue().getLanguage());
    }

    @Test
    void switcher_isHidden_whenOnlyEnglishIsAvailable() {
        ElicitI18NProvider englishOnly = new ElicitI18NProvider();
        englishOnly.fileSystemPath = Path.of("target/no-such-i18n-mount").toString();
        englishOnly.localPath = Path.of("target/no-such-i18n-local").toString();
        englishOnly.appName = "survey";
        englishOnly.bundledLocales = "en";
        englishOnly.pseudoLocaleEnabled = false;

        LanguageSwitcher switcher = new LanguageSwitcher(selection, englishOnly);

        assertEquals(List.of(Locale.ENGLISH), switcher.getListDataView().getItems().toList());
        assertFalse(switcher.isVisible(), "an English-only deployment must not show the language selector");
    }
}
