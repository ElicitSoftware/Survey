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
import com.elicitsoftware.QuestionManager;
import com.elicitsoftware.RepeatPerItemFixture;
import com.elicitsoftware.model.Answer;
import com.elicitsoftware.model.Respondent;
import com.elicitsoftware.model.Section;
import com.elicitsoftware.model.SelectItem;
import com.elicitsoftware.model.Survey;
import com.vaadin.browserless.quarkus.QuarkusBrowserlessTest;
import com.vaadin.flow.component.UI;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * UC-002 BR-013, the half that needs a respondent's language: inside an instance built from a
 * selected item, the token holds the item's text in the base rendering and <em>its translation</em>
 * in the local one. BR-014 is what lets a repeated section's title be translated at all -- the
 * section a marker names is resolved without its instance.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class LocalizedRepeatItemTest extends QuarkusBrowserlessTest {

    private static final Locale SPANISH = Locale.forLanguageTag("es-419");

    @Inject
    QuestionManager questionManager;

    @Inject
    ContentTranslator translator;

    @Inject
    EntityManager em;

    @AfterEach
    void restore() {
        UI.getCurrent().setLocale(Locale.ENGLISH);
    }

    private void translate(int surveyId, String elementType, UUID elementKey, String field, String base, String value) {
        em.createNativeQuery("""
                        INSERT INTO survey.translations
                            (id, survey_id, translation_key, element_type, element_key, field, language, value, source_hash)
                        VALUES (nextval('survey.translations_seq'), ?1, ?2, ?3, ?4, ?5, 'es-419', ?6, ?7)
                        """)
                .setParameter(1, surveyId).setParameter(2, UUID.randomUUID()).setParameter(3, elementType)
                .setParameter(4, elementKey).setParameter(5, field).setParameter(6, value)
                .setParameter(7, ContentHash.of(base)).executeUpdate();
    }

    @Test
    @TestTransaction
    void aRepeatedSectionIsTitledWithItsItemInTheRespondentsLanguage() {
        int surveyId = RepeatPerItemFixture.build(em);
        em.createNativeQuery("UPDATE survey.surveys SET content_languages = 'es-419' WHERE id = ?1")
                .setParameter(1, surveyId).executeUpdate();
        em.flush();
        em.clear();

        Section game = Section.find("id", RepeatPerItemFixture.SECTION_GAME).firstResult();
        translate(surveyId, ContentTranslator.SECTIONS, game.sectionKey, "name", game.name, "Michigan contra {<GAME>|este partido}");
        SelectItem iowa = SelectItem.find("surveyId = ?1 and codedValue = 'G04'", surveyId).firstResult();
        translate(surveyId, ContentTranslator.SELECT_ITEMS, iowa.selectItemKey, "display_text", iowa.displayText, "Iowa (visitante)");
        em.flush();
        translator.invalidate(surveyId);
        UI.getCurrent().setLocale(SPANISH);

        Respondent r = new Respondent();
        r.survey = Survey.findById(surveyId);
        r.accessCode = "peritem_es_" + System.nanoTime();
        r.active = true;
        r.logins = 0;
        r.persist();
        int rid = r.id.intValue();
        questionManager.init(rid, RepeatPerItemFixture.key(surveyId, "0001-0000-0001-0000-0000-0000"));
        Answer games = Answer.findByDisplayKeyActive(rid, RepeatPerItemFixture.key(surveyId, "0001-0000-0001-0000-0001-0000"));
        assertNotNull(games);
        games.setTextValue("G02,G04");
        em.flush();
        questionManager.deleteDownstreamAnswers(rid, games, games.id);
        questionManager.buildDownstreamQuestions(games);

        // Iowa is the fourth item: the base label holds its base text, the local one its translation.
        Answer iowaSection = Answer.findByDisplayKeyActive(rid, RepeatPerItemFixture.key(surveyId, "0002-0000-0001-0004-0000-0000"));
        assertEquals("Michigan vs. Iowa", iowaSection.displayText);
        assertEquals("Michigan contra Iowa (visitante)", iowaSection.displayTextLocal);
        assertEquals("es-419", iowaSection.displayLanguage);

        // Oklahoma's text has no translation, so the translated title carries its base text.
        Answer oklahomaSection = Answer.findByDisplayKeyActive(rid, RepeatPerItemFixture.key(surveyId, "0002-0000-0001-0002-0000-0000"));
        assertEquals("Michigan contra Oklahoma", oklahomaSection.displayTextLocal);
    }
}
