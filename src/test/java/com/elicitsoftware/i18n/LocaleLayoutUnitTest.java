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

import com.elicitsoftware.i18n.PlainVaadin.Js;
import com.elicitsoftware.i18n.PlainVaadin.PlainUi;
import com.vaadin.flow.component.Direction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-009: Complete the Survey in My Language (BR-004, BR-011) -- direction, language and font
 * scale follow the locale, on the UI element and, through JavaScript, on the document element.
 * Plain counterpart of {@code LocaleLayoutTest}, using the shipped classpath manifest.
 */
class LocaleLayoutUnitTest {

    private static final Locale ARABIC = Locale.forLanguageTag("ar");
    private static final Locale SPANISH = Locale.forLanguageTag("es-419");

    private LocaleLayout layout;
    private PlainUi ui;

    @BeforeEach
    void setUp() {
        layout = new LocaleLayout();
        layout.localeConfig = new LocaleConfig();
        ui = PlainUi.of(Locale.ENGLISH, null);
    }

    @AfterEach
    void tearDown() {
        PlainVaadin.clear();
    }

    private Js documentUpdate() {
        return ui.page.executed.stream()
                .filter(js -> js.expression().contains("document.documentElement.lang"))
                .findFirst().orElseThrow(() -> new AssertionError("no document element update in " + ui.page.executed));
    }

    @Test
    void given_arabic_when_applied_then_rightToLeftAndEnlarged() {
        layout.apply(ui, ARABIC);

        assertTrue(ui.page.executed.contains(new Js("document.dir = $0", List.of("rtl"))),
                "UI.setDirection tells the client too: " + ui.page.executed);
        assertEquals("rtl", ui.getElement().getAttribute("dir"));
        assertEquals("ar", ui.getElement().getAttribute("lang"));
        assertEquals("1.15", ui.getElement().getAttribute(LocaleLayout.FONT_SCALE_ATTRIBUTE));
        assertEquals(List.of("ar", "rtl", LocaleLayout.FONT_SCALE_PROPERTY, "1.15"), documentUpdate().parameters());
    }

    @Test
    void given_spanish_when_applied_then_leftToRightAndUnscaled() {
        layout.apply(ui, SPANISH);

        assertTrue(ui.page.executed.contains(new Js("document.dir = $0", List.of("ltr"))),
                "UI.setDirection tells the client too: " + ui.page.executed);
        assertEquals("ltr", ui.getElement().getAttribute("dir"));
        assertEquals("es-419", ui.getElement().getAttribute("lang"));
        assertEquals("1", ui.getElement().getAttribute(LocaleLayout.FONT_SCALE_ATTRIBUTE));
        assertEquals(List.of("es-419", "ltr", LocaleLayout.FONT_SCALE_PROPERTY, "1"), documentUpdate().parameters());
    }

    @Test
    void given_missingUiOrLocale_when_applied_then_nothingHappens() {
        layout.apply(ui, null);
        layout.apply(null, ARABIC);

        assertNull(ui.getElement().getAttribute(LocaleLayout.FONT_SCALE_ATTRIBUTE));
        assertTrue(ui.page.executed.isEmpty());
    }

    @Test
    void given_locale_when_askingForDirection_then_followsLanguage() {
        assertEquals(Direction.RIGHT_TO_LEFT, layout.directionFor(ARABIC));
        assertEquals(Direction.LEFT_TO_RIGHT, layout.directionFor(SPANISH));
    }

    @Test
    void given_locale_when_askingForFontScale_then_shippedScale() {
        assertEquals(1.15, layout.fontScaleFor(ARABIC), 1e-9);
        assertEquals(1.0, layout.fontScaleFor(SPANISH), 1e-9);
    }
}
