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

import com.vaadin.flow.component.Direction;
import com.vaadin.flow.component.UI;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

/**
 * Applies the layout consequences of a locale to a UI (UC-009 BR-004, BR-011): the Vaadin text
 * direction, the {@code dir} and {@code lang} attributes on the UI element (which browserless tests
 * can assert on) and the same attributes on the document element for the browser, plus the locale's
 * font scale.
 * <p>
 * The scale is written to the document element as the {@code --elicit-font-scale} custom property,
 * which {@code styles.css} multiplies the root font size by. Every font size in the Lumo theme, in
 * the brand typography and in the application's own stylesheets is expressed in {@code rem}, so
 * that one property scales the whole page proportionally -- spacing and control sizes with it --
 * and no view has to know the locale.
 */
@ApplicationScoped
public class LocaleLayout {

    /** The CSS custom property {@code styles.css} reads; also mirrored as a UI-element attribute. */
    public static final String FONT_SCALE_PROPERTY = "--elicit-font-scale";
    public static final String FONT_SCALE_ATTRIBUTE = "data-font-scale";

    @Inject
    LocaleConfig localeConfig;

    public void apply(UI ui, Locale locale) {
        if (ui == null || locale == null) {
            return;
        }
        Direction direction = localeConfig.isRightToLeft(locale)
                ? Direction.RIGHT_TO_LEFT : Direction.LEFT_TO_RIGHT;
        String fontScale = format(localeConfig.fontScale(locale));
        ui.setDirection(direction);
        ui.getElement().setAttribute("dir", direction.getClientName());
        ui.getElement().setAttribute("lang", locale.toLanguageTag());
        ui.getElement().setAttribute(FONT_SCALE_ATTRIBUTE, fontScale);
        ui.getPage().executeJs("document.documentElement.lang=$0;document.documentElement.dir=$1;"
                        + "document.documentElement.style.setProperty($2,$3);",
                locale.toLanguageTag(), direction.getClientName(), FONT_SCALE_PROPERTY, fontScale);
    }

    public Direction directionFor(Locale locale) {
        return localeConfig.isRightToLeft(locale) ? Direction.RIGHT_TO_LEFT : Direction.LEFT_TO_RIGHT;
    }

    public double fontScaleFor(Locale locale) {
        return localeConfig.fontScale(locale);
    }

    /**
     * A plain decimal for CSS: {@code Double.toString} would emit scientific notation for extreme
     * values and a trailing {@code .0} for whole ones, neither of which belongs in a stylesheet.
     */
    static String format(double scale) {
        return BigDecimal.valueOf(scale).setScale(4, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
    }
}
