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
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.i18n.LocaleChangeEvent;
import com.vaadin.flow.i18n.LocaleChangeObserver;

import java.util.List;
import java.util.Locale;

/**
 * Language selector shown on every page (UC-008 step 4). Lists the provided locales (shipped plus
 * mounted), labelled in their own language; choosing one remembers it for the session and reloads
 * the page (dropping any {@code ?lang=} link parameter) so every view is rebuilt in the new language.
 */
public class LanguageSwitcher extends Select<Locale> implements LocaleChangeObserver {

    public static final String ID = "language-switcher";

    private final transient LocaleSelection selection;

    public LanguageSwitcher(LocaleSelection selection, ElicitI18NProvider provider) {
        this.selection = selection;
        setId(ID);
        addClassName("language-switcher");
        List<Locale> locales = provider.getProvidedLocales().stream()
                .filter(l -> !ElicitI18NProvider.PSEUDO_LOCALE.getLanguage().equals(l.getLanguage()))
                .toList();
        setItems(locales);
        // English-only deployments (nothing mounted) have no choice to offer, so the selector stays out of the header.
        setVisible(locales.size() > 1);
        setItemLabelGenerator(LanguageSwitcher::displayName);
        UI ui = UI.getCurrent();
        if (ui != null) {
            setValue(current(locales, ui.getLocale()));
        }
        setAriaLabel(getTranslation("common.language"));
        addValueChangeListener(event -> {
            if (event.isFromClient() && event.getValue() != null) {
                UI current = UI.getCurrent();
                selection.apply(current, event.getValue());
                // Reload without any ?lang= parameter, otherwise the link's language would win again
                current.getPage().executeJs(
                        "const u = new URL(location.href); u.searchParams.delete('lang'); location.replace(u.toString());");
            }
        });
    }

    @Override
    public void localeChange(LocaleChangeEvent event) {
        setAriaLabel(getTranslation("common.language"));
        Locale match = current(getListDataView().getItems().toList(), event.getLocale());
        if (match != null && !match.equals(getValue())) {
            setValue(match);
        }
    }

    static Locale current(List<Locale> locales, Locale wanted) {
        if (wanted == null) {
            return null;
        }
        if (locales.contains(wanted)) {
            return wanted;
        }
        return locales.stream().filter(l -> l.getLanguage().equals(wanted.getLanguage())).findFirst().orElse(null);
    }

    static String displayName(Locale locale) {
        String name = locale.getDisplayName(locale);
        if (name == null || name.isEmpty()) {
            return locale.toLanguageTag();
        }
        return name.substring(0, 1).toUpperCase(locale) + name.substring(1);
    }
}
