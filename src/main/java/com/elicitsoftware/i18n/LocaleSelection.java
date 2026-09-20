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
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.quarkus.annotation.VaadinServiceEnabled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Locale;
import java.util.Optional;

/**
 * The language a respondent has chosen for this browser session (UC-007 BR-003, BR-007).
 * The choice lives only in the Vaadin session; nothing about it is stored with the respondent.
 */
@ApplicationScoped
public class LocaleSelection {

    public static final String SESSION_ATTRIBUTE = "elicit.locale";

    @Inject
    @VaadinServiceEnabled
    ElicitI18NProvider provider;

    @Inject
    LocaleLayout layout;

    /** Resolves a language tag to a provided locale: exact match first, then same language. */
    public Optional<Locale> resolve(String languageTag) {
        if (languageTag == null || languageTag.isBlank()) {
            return Optional.empty();
        }
        Locale wanted = Locale.forLanguageTag(languageTag.trim());
        if (wanted.getLanguage().isEmpty()) {
            return Optional.empty();
        }
        var provided = provider.getProvidedLocales();
        if (provided.contains(wanted)) {
            return Optional.of(wanted);
        }
        return provided.stream().filter(l -> l.getLanguage().equals(wanted.getLanguage())).findFirst();
    }

    /** Remembers the locale for the session and applies it (texts and layout) to the UI. */
    public void apply(UI ui, Locale locale) {
        VaadinSession session = ui.getSession();
        if (session != null) {
            session.setAttribute(SESSION_ATTRIBUTE, locale);
        }
        ui.setLocale(locale);
        layout.apply(ui, locale);
    }

    /** The locale remembered for the session, if the respondent chose one. */
    public Optional<Locale> stored(VaadinSession session) {
        if (session == null) {
            return Optional.empty();
        }
        Object value = session.getAttribute(SESSION_ATTRIBUTE);
        return value instanceof Locale locale ? Optional.of(locale) : Optional.empty();
    }
}
