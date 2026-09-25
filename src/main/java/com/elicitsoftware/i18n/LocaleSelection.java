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

import com.elicitsoftware.QuestionService;
import com.elicitsoftware.UISessionDataService;
import com.elicitsoftware.model.Respondent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinSession;
import com.vaadin.quarkus.annotation.VaadinServiceEnabled;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.Locale;
import java.util.Optional;

/**
 * The language a respondent has chosen for this browser session (UC-009 BR-003, BR-007).
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

    /**
     * Resolved lazily rather than injected outright: this class is also used before a respondent
     * exists (the login page, the {@code ?lang=} hook), and the two services below are UI-scoped.
     */
    @Inject
    Instance<UISessionDataService> sessionData;

    @Inject
    Instance<QuestionService> questions;

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
        Locale previous = ui.getLocale();
        VaadinSession session = ui.getSession();
        if (session != null) {
            session.setAttribute(SESSION_ATTRIBUTE, locale);
        }
        ui.setLocale(locale);
        layout.apply(ui, locale);
        if (!locale.equals(previous)) {
            relocalizeInProgressAnswers();
        }
    }

    /**
     * Re-renders the labels of a respondent's in-progress answers after a real language change.
     * <p>
     * An answer's label is stored, not derived per draw ({@code answers.display_text_local}), so a
     * switch has to rewrite the ones already built; the navigation and review SQL read those
     * columns directly. Only a genuine change triggers it, never a page load that happens to
     * re-apply the language already in force, and only when a respondent is in session -- on the
     * login page there is nothing to relocalize.
     * <p>
     * Failure here must not break the switch: the language is already applied to the chrome, and a
     * stale label is a cosmetic problem the next answer save corrects.
     */
    private void relocalizeInProgressAnswers() {
        if (sessionData.isUnsatisfied() || questions.isUnsatisfied()) {
            return;
        }
        try {
            Respondent respondent = sessionData.get().getRespondent();
            if (respondent != null && respondent.id != null) {
                questions.get().relocalize(respondent.id.intValue());
            }
        } catch (RuntimeException e) {
            Log.warn("Could not re-render answer labels after a language change", e);
        }
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
