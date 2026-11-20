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

import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

/**
 * Establishes the locale of every new UI (UC-009 BR-003): a locale remembered in the session wins,
 * otherwise Vaadin's Accept-Language negotiation stands; either way the layout direction is applied.
 * A {@code ?lang=} query parameter on any route (the invitation link carries one) selects and
 * remembers that language before the view is built.
 */
@ApplicationScoped
public class LocaleInitializer implements VaadinServiceInitListener {

    public static final String LANG_PARAMETER = "lang";

    @Inject
    LocaleSelection selection;

    @Inject
    LocaleLayout layout;

    @Override
    public void serviceInit(@Observes ServiceInitEvent event) {
        event.getSource().addUIInitListener(uiEvent -> {
            UI ui = uiEvent.getUI();
            selection.stored(ui.getSession()).ifPresent(ui::setLocale);
            layout.apply(ui, ui.getLocale());
            ui.addBeforeEnterListener(enter -> enter.getLocation().getQueryParameters()
                    .getSingleParameter(LANG_PARAMETER)
                    .flatMap(selection::resolve)
                    .filter(locale -> !locale.equals(ui.getLocale()))
                    .ifPresent(locale -> selection.apply(ui, locale)));
        });
    }
}
