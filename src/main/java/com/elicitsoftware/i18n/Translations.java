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
import com.vaadin.flow.i18n.I18NProvider;
import com.vaadin.flow.server.VaadinService;
import com.vaadin.flow.server.VaadinSession;

import java.util.Locale;

/**
 * Translation lookup for code that is not a Vaadin component (validators, the PDF renderer, REST
 * resources). Uses the running service's provider when there is one and falls back to a
 * standalone {@link ElicitI18NProvider} reading the classpath bundles, so plain unit tests and
 * non-Vaadin threads translate too.
 */
public final class Translations {

    private static volatile ElicitI18NProvider standalone;

    private Translations() {
    }

    /** Translates for the current UI's locale (or the session's, or English). */
    public static String get(String key, Object... params) {
        return get(currentLocale(), key, params);
    }

    public static String get(Locale locale, String key, Object... params) {
        return provider().getTranslation(key, locale == null ? ElicitI18NProvider.DEFAULT_LOCALE : locale, params);
    }

    public static Locale currentLocale() {
        UI ui = UI.getCurrent();
        if (ui != null) {
            return ui.getLocale();
        }
        VaadinSession session = VaadinSession.getCurrent();
        if (session != null && session.getLocale() != null) {
            return session.getLocale();
        }
        return ElicitI18NProvider.DEFAULT_LOCALE;
    }

    static I18NProvider provider() {
        VaadinService service = VaadinService.getCurrent();
        if (service != null) {
            I18NProvider fromService = service.getInstantiator().getI18NProvider();
            if (fromService != null) {
                return fromService;
            }
        }
        ElicitI18NProvider p = standalone;
        if (p == null) {
            p = new ElicitI18NProvider();
            standalone = p;
        }
        return p;
    }
}
