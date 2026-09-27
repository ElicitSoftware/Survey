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

import com.elicitsoftware.PostgresTestResource;
import com.elicitsoftware.QuestionService;
import com.elicitsoftware.model.Answer;
import com.elicitsoftware.model.Question;
import com.vaadin.browserless.quarkus.QuarkusBrowserlessTest;
import com.vaadin.flow.component.UI;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * UC-009 BR-005 end to end: the stored label of an answer, in the respondent's language.
 * <p>
 * {@code answers.display_text} stays in the base language because the ETL, the console and the
 * respondent export all read it; {@code display_text_local} carries what the respondent actually
 * saw. This drives the real pipeline -- {@code QuestionManager.buildDipslayText} through
 * {@code relocalize} -- rather than asserting on the translator alone.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class LocalizedAnswerLabelTest extends QuarkusBrowserlessTest {

    private static final int RESPONDENT_ID = 1;
    private static final int SURVEY_ID = 1;
    private static final Locale SPANISH = Locale.forLanguageTag("es-419");
    private static final String SPANISH_TEXT = "¿Cuál es su nombre?";

    @Inject
    QuestionService questionService;

    @Inject
    ContentTranslator translator;

    @Inject
    EntityManager em;

    @AfterEach
    void restore() {
        QuarkusTransaction.requiringNew().run(() -> {
            em.createNativeQuery("DELETE FROM survey.translations WHERE survey_id = ?1")
                    .setParameter(1, SURVEY_ID).executeUpdate();
            em.createNativeQuery("UPDATE survey.surveys SET content_languages = NULL WHERE id = ?1")
                    .setParameter(1, SURVEY_ID).executeUpdate();
            em.createNativeQuery("UPDATE survey.answers SET display_text_local = NULL, display_language = NULL "
                    + "WHERE respondent_id = ?1").setParameter(1, RESPONDENT_ID).executeUpdate();
        });
        translator.invalidate(SURVEY_ID);
        UI.getCurrent().setLocale(Locale.ENGLISH);
    }

    /** An answered question of the seeded respondent, with its base text and element key. */
    private Answer anAnsweredQuestion() {
        List<Answer> answers = QuarkusTransaction.requiringNew().call(() ->
                Answer.list("respondentId = ?1 and deleted = false and question is not null", RESPONDENT_ID));
        assertNotNull(answers);
        return answers.stream().filter(a -> a.question != null && a.question.text != null).findFirst()
                .orElseThrow(() -> new IllegalStateException("seeded respondent has no answered question"));
    }

    private void publishSpanish(Question question, String value) {
        QuarkusTransaction.requiringNew().run(() -> {
            em.createNativeQuery("UPDATE survey.surveys SET content_languages = 'es-419' WHERE id = ?1")
                    .setParameter(1, SURVEY_ID).executeUpdate();
            em.createNativeQuery("""
                            INSERT INTO survey.translations
                                (id, survey_id, translation_key, element_type, element_key, field, language, value, source_hash)
                            VALUES (nextval('survey.translations_seq'), ?1, ?2, 'questions', ?3, 'text', 'es-419', ?4, ?5)
                            """)
                    .setParameter(1, SURVEY_ID).setParameter(2, UUID.randomUUID())
                    .setParameter(3, question.questionKey).setParameter(4, value)
                    .setParameter(5, ContentHash.of(question.text)).executeUpdate();
        });
        translator.invalidate(SURVEY_ID);
    }

    @Test
    void relocalizeWritesTheLocalLabelAndLeavesTheBaseOneAlone() {
        Answer answer = anAnsweredQuestion();
        String baseLabel = answer.displayText;
        publishSpanish(answer.question, SPANISH_TEXT);
        UI.getCurrent().setLocale(SPANISH);

        questionService.relocalize(RESPONDENT_ID);

        Answer reloaded = QuarkusTransaction.requiringNew().call(() -> Answer.findById(answer.id));
        assertEquals(SPANISH_TEXT, reloaded.displayTextLocal, "the respondent's language");
        assertEquals("es-419", reloaded.displayLanguage);
        assertEquals(baseLabel, reloaded.displayText, "the base column is what every other reader depends on");
        assertEquals(SPANISH_TEXT, reloaded.label(), "label() prefers the local rendering");
    }

    @Test
    void switchingBackToTheBaseLanguageClearsTheLocalLabel() {
        Answer answer = anAnsweredQuestion();
        publishSpanish(answer.question, SPANISH_TEXT);

        UI.getCurrent().setLocale(SPANISH);
        questionService.relocalize(RESPONDENT_ID);
        UI.getCurrent().setLocale(Locale.ENGLISH);
        questionService.relocalize(RESPONDENT_ID);

        Answer reloaded = QuarkusTransaction.requiringNew().call(() -> Answer.findById(answer.id));
        assertNull(reloaded.displayTextLocal, "no stale Spanish left behind for an English respondent");
        assertNull(reloaded.displayLanguage);
        assertEquals(reloaded.displayText, reloaded.label());
    }

    @Test
    void aStaleTranslationLeavesTheLabelInTheBaseLanguage() {
        // The wording was edited after the translation was made, so the respondent reads the base
        // text rather than a question that may no longer mean the same thing (BR-008).
        Answer answer = anAnsweredQuestion();
        QuarkusTransaction.requiringNew().run(() -> {
            em.createNativeQuery("UPDATE survey.surveys SET content_languages = 'es-419' WHERE id = ?1")
                    .setParameter(1, SURVEY_ID).executeUpdate();
            em.createNativeQuery("""
                            INSERT INTO survey.translations
                                (id, survey_id, translation_key, element_type, element_key, field, language, value, source_hash)
                            VALUES (nextval('survey.translations_seq'), ?1, ?2, 'questions', ?3, 'text', 'es-419', ?4, ?5)
                            """)
                    .setParameter(1, SURVEY_ID).setParameter(2, UUID.randomUUID())
                    .setParameter(3, answer.question.questionKey).setParameter(4, SPANISH_TEXT)
                    .setParameter(5, ContentHash.of("a wording this question no longer has")).executeUpdate();
        });
        translator.invalidate(SURVEY_ID);
        UI.getCurrent().setLocale(SPANISH);

        questionService.relocalize(RESPONDENT_ID);

        Answer reloaded = QuarkusTransaction.requiringNew().call(() -> Answer.findById(answer.id));
        assertNull(reloaded.displayTextLocal, "a stale translation is not served");
    }

    @Test
    void withNoTranslationForTheQuestionTheLabelStaysInTheBaseLanguage() {
        Answer answer = anAnsweredQuestion();
        QuarkusTransaction.requiringNew().run(() ->
                em.createNativeQuery("UPDATE survey.surveys SET content_languages = 'es-419' WHERE id = ?1")
                        .setParameter(1, SURVEY_ID).executeUpdate());
        translator.invalidate(SURVEY_ID);
        UI.getCurrent().setLocale(SPANISH);

        questionService.relocalize(RESPONDENT_ID);

        Answer reloaded = QuarkusTransaction.requiringNew().call(() -> Answer.findById(answer.id));
        assertNull(reloaded.displayTextLocal, "the fallback is per string, not per page");
    }
}
