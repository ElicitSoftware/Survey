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

import java.util.Locale;

/**
 * Applies the layout consequences of a locale to a UI (UC-007 BR-004): the Vaadin text direction,
 * the {@code dir} and {@code lang} attributes on the UI element (which browserless tests can
 * assert on) and the same attributes on the document element for the browser.
 */
@ApplicationScoped
public class LocaleLayout {

    @Inject
    LocaleDirectionConfig directionConfig;

    public void apply(UI ui, Locale locale) {
        if (ui == null || locale == null) {
            return;
        }
        Direction direction = directionConfig.isRightToLeft(locale)
                ? Direction.RIGHT_TO_LEFT : Direction.LEFT_TO_RIGHT;
        ui.setDirection(direction);
        ui.getElement().setAttribute("dir", direction.getClientName());
        ui.getElement().setAttribute("lang", locale.toLanguageTag());
        ui.getPage().executeJs("document.documentElement.lang=$0;document.documentElement.dir=$1;",
                locale.toLanguageTag(), direction.getClientName());
    }

    public Direction directionFor(Locale locale) {
        return directionConfig.isRightToLeft(locale) ? Direction.RIGHT_TO_LEFT : Direction.LEFT_TO_RIGHT;
    }
}
