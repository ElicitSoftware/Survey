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

import com.elicitsoftware.QuestionService;
import com.elicitsoftware.UISessionDataService;
import com.elicitsoftware.i18n.PlainVaadin.PlainUi;
import com.elicitsoftware.i18n.PlainVaadin.StubSession;
import com.elicitsoftware.model.Respondent;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-009: Complete the Survey in My Language (BR-002, BR-003, BR-007) -- resolving a requested
 * language against what the site offers, remembering the choice in the session only, and
 * re-rendering in-progress answer labels after a genuine change.
 * <p>
 * Plain: the UI and session are {@link PlainVaadin} stand-ins and the CDI collaborators are
 * hand-written stubs.
 */
class LocaleSelectionTest {

    private static final Locale SPANISH = Locale.forLanguageTag("es-419");
    private static final Locale ARABIC = Locale.forLanguageTag("ar");

    private LocaleSelection selection;
    private final List<Locale> laidOut = new ArrayList<>();
    private final List<Integer> relocalized = new ArrayList<>();
    private Respondent respondent;
    private PlainUi ui;

    private final class RecordingQuestions extends QuestionService {
        @Override
        public void relocalize(int respondentId) {
            relocalized.add(respondentId);
        }
    }

    private final class StubSessionData extends UISessionDataService {
        @Override
        public Respondent getRespondent() {
            return respondent;
        }
    }

    @BeforeEach
    void setUp() {
        ElicitI18NProvider provider = new ElicitI18NProvider();
        provider.bundledLocales = "en,es-419,ar";
        selection = new LocaleSelection();
        selection.provider = provider;
        selection.layout = new LocaleLayout() {
            @Override
            public void apply(UI target, Locale locale) {
                laidOut.add(locale);
            }
        };
        selection.sessionData = StubInstance.of(new StubSessionData());
        selection.questions = StubInstance.of(new RecordingQuestions());
        respondent = new Respondent();
        respondent.id = 7;
        ui = PlainUi.of(Locale.ENGLISH, new StubSession());
    }

    @AfterEach
    void tearDown() {
        PlainVaadin.clear();
    }

    // ------------------------------------------------------------------ resolve

    @Test
    void given_noTag_when_resolving_then_empty() {
        assertEquals(Optional.empty(), selection.resolve(null));
        assertEquals(Optional.empty(), selection.resolve(""));
        assertEquals(Optional.empty(), selection.resolve("   "));
    }

    @Test
    void given_tagWithoutLanguage_when_resolving_then_empty() {
        assertEquals(Optional.empty(), selection.resolve("x-private"));
    }

    @Test
    void given_offeredTag_when_resolving_then_exactLocale() {
        assertEquals(Optional.of(SPANISH), selection.resolve(" es-419 "));
        assertEquals(Optional.of(ARABIC), selection.resolve("ar"));
    }

    @Test
    void given_otherRegionOfOfferedLanguage_when_resolving_then_offeredLocale() {
        assertEquals(Optional.of(SPANISH), selection.resolve("es-MX"));
    }

    @Test
    void given_languageNotOffered_when_resolving_then_empty() {
        assertEquals(Optional.empty(), selection.resolve("fr"), "BR-002: a withheld language is unreachable");
    }

    // ------------------------------------------------------------------ apply

    @Test
    void given_newLanguage_when_applying_then_rememberedAppliedLaidOutAndRelocalized() {
        selection.apply(ui, ARABIC);

        assertEquals(ARABIC, ui.getSession().getAttribute(LocaleSelection.SESSION_ATTRIBUTE));
        assertEquals(ARABIC, ui.getLocale());
        assertEquals(List.of(ARABIC), laidOut);
        assertEquals(List.of(7), relocalized);
    }

    @Test
    void given_uiWithoutSession_when_applying_then_localeAppliedWithoutRemembering() {
        ui.session = null;

        selection.apply(ui, ARABIC);

        assertEquals(ARABIC, ui.getLocale());
        assertEquals(List.of(ARABIC), laidOut);
    }

    @Test
    void given_languageAlreadyInForce_when_applying_then_notRelocalized() {
        selection.apply(ui, Locale.ENGLISH);

        assertEquals(List.of(Locale.ENGLISH), laidOut, "the layout is still applied on every load");
        assertTrue(relocalized.isEmpty(), "re-applying the language in force rewrites nothing");
    }

    @Test
    void given_noSessionDataService_when_applying_then_noRelocalization() {
        selection.sessionData = StubInstance.unsatisfied();

        selection.apply(ui, SPANISH);

        assertEquals(SPANISH, ui.getLocale());
        assertTrue(relocalized.isEmpty());
    }

    @Test
    void given_noQuestionService_when_applying_then_noRelocalization() {
        selection.questions = StubInstance.unsatisfied();

        selection.apply(ui, SPANISH);

        assertEquals(SPANISH, ui.getLocale());
        assertTrue(relocalized.isEmpty());
    }

    @Test
    void given_noRespondentInSession_when_applying_then_noRelocalization() {
        respondent = null;
        selection.apply(ui, SPANISH);
        assertTrue(relocalized.isEmpty());
    }

    @Test
    void given_unsavedRespondent_when_applying_then_noRelocalization() {
        respondent.id = null;
        selection.apply(ui, SPANISH);
        assertTrue(relocalized.isEmpty());
    }

    @Test
    void given_relocalizationFails_when_applying_then_languageStillApplied() {
        selection.questions = StubInstance.of(new QuestionService() {
            @Override
            public void relocalize(int respondentId) {
                throw new IllegalStateException("database down");
            }
        });

        selection.apply(ui, SPANISH);

        assertEquals(SPANISH, ui.getLocale());
        assertEquals(SPANISH, ui.getSession().getAttribute(LocaleSelection.SESSION_ATTRIBUTE));
    }

    // ------------------------------------------------------------------ stored

    @Test
    void given_noSession_when_readingStored_then_empty() {
        assertEquals(Optional.empty(), selection.stored(null));
    }

    @Test
    void given_sessionWithoutChoice_when_readingStored_then_empty() {
        assertEquals(Optional.empty(), selection.stored(new StubSession()));
    }

    @Test
    void given_sessionWithChoice_when_readingStored_then_thatLocale() {
        VaadinSession session = new StubSession();
        session.setAttribute(LocaleSelection.SESSION_ATTRIBUTE, ARABIC);
        Optional<Locale> stored = selection.stored(session);
        assertTrue(stored.isPresent());
        assertSame(ARABIC, stored.get());
    }

    @Test
    void given_sessionAttributeOfWrongType_when_readingStored_then_empty() {
        VaadinSession session = new StubSession();
        session.setAttribute(LocaleSelection.SESSION_ATTRIBUTE, "ar");
        assertEquals(Optional.empty(), selection.stored(session));
    }
}
