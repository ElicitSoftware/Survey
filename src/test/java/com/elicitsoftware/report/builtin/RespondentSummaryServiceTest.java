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
package com.elicitsoftware.report.builtin;

import com.elicitsoftware.PostgresTestResource;
import com.elicitsoftware.model.Survey;
import com.elicitsoftware.report.ReportRequest;
import com.elicitsoftware.report.ReportResponse;
import com.elicitsoftware.report.builtin.RespondentSummaryService.Group;
import com.elicitsoftware.report.builtin.RespondentSummaryService.Item;
import com.elicitsoftware.report.builtin.RespondentSummaryService.Summary;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-014 on the library fixture: Tess (respondent 1) finished LibraryCardReg (survey 1). Marks
 * are inserted per test inside the rolled-back transaction.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
class RespondentSummaryServiceTest {

    static final int SURVEY_ID = 1;
    static final int TESS = 1;

    @Inject
    RespondentSummaryService service;

    @Inject
    EntityManager em;

    private int durableQuestion(String shortTextOrText) {
        return ((Number) em.createNativeQuery("SELECT question_id FROM survey.questions WHERE survey_id = ?1 AND (short_text = ?2 OR text = ?2) ORDER BY id LIMIT 1")
                .setParameter(1, SURVEY_ID).setParameter(2, shortTextOrText).getSingleResult()).intValue();
    }

    private int durableQuestionOfType(String type) {
        return ((Number) em.createNativeQuery("SELECT q.question_id FROM survey.questions q JOIN survey.question_types t ON t.id = q.type_id WHERE q.survey_id = ?1 AND t.name = ?2 ORDER BY q.id LIMIT 1")
                .setParameter(1, SURVEY_ID).setParameter(2, type).getSingleResult()).intValue();
    }

    private int durablePlacementOfAnswer(int answerId) {
        return ((Number) em.createNativeQuery("SELECT sq.sections_question_id FROM survey.answers a JOIN survey.sections_questions sq ON sq.id = a.section_question_id WHERE a.id = ?1")
                .setParameter(1, answerId).getSingleResult()).intValue();
    }

    private int durableSectionPlacement(int stepOrder, int sectionOrder) {
        return ((Number) em.createNativeQuery("SELECT steps_sections_id FROM survey.steps_sections WHERE survey_id = ?1 AND step_display_order = ?2 AND section_display_order = ?3 ORDER BY id LIMIT 1")
                .setParameter(1, SURVEY_ID).setParameter(2, stepOrder).setParameter(3, sectionOrder).getSingleResult()).intValue();
    }

    private void mark(String type, Integer ss, Integer q, Integer sq, String label, int order) {
        em.createNativeQuery("INSERT INTO survey.report_items (id, survey_id, report_type, steps_sections_id, question_id, sections_question_id, label, display_order) "
                + "VALUES (nextval('survey.report_items_seq'), ?1, ?2, ?3, ?4, ?5, ?6, ?7)")
                .setParameter(1, SURVEY_ID).setParameter(2, type).setParameter(3, ss).setParameter(4, q)
                .setParameter(5, sq).setParameter(6, label).setParameter(7, order).executeUpdate();
    }

    private static List<Item> items(Summary s) {
        return s.groups().stream().flatMap(g -> g.items().stream()).toList();
    }

    @Test
    @TestTransaction
    void given_noMarks_then_titleAndDateOnly() {
        // UC-014 A2
        Summary s = service.summary(TESS, "en", "test").orElseThrow();
        assertNotNull(s.surveyTitle());
        assertNotNull(s.completed(), "Tess finished, so the completion date is shown");
        assertTrue(s.groups().isEmpty(), "nothing marked, nothing listed");
        assertEquals("en", s.language());
        assertFalse(s.rightToLeft());
        ReportResponse r = service.toResponse(s);
        assertTrue(r.innerHTML.contains("respondent-summary"));
        assertNotNull(r.pdf);
        assertEquals(1, r.pdf.content.length, "the meta line only");
    }

    @Test
    @TestTransaction
    void given_marksOfEachScope_then_itemsAreGroupedInSurveyOrderUnderTheirLabels() {
        // UC-014 BR-003, BR-004: a question-wide mark (first name, labeled), a placement mark
        // (last name), and a section mark (Collection Preferences in Preferences).
        mark("RESPONDENT", null, durableQuestion("First name"), null, "Your first name", 2);
        mark("RESPONDENT", null, null, durablePlacementOfAnswer(7), null, 1);
        mark("RESPONDENT", durableSectionPlacement(3, 4), null, null, null, 3);
        mark("ADMINISTRATOR", null, durableQuestion("Email address"), null, null, 1);

        Summary s = service.summary(TESS, "en", "test").orElseThrow();
        assertEquals(2, s.groups().size(), "two sections carry marked answers: " + s.groups());
        Group patron = s.groups().get(0);
        assertTrue(patron.heading().contains("Patron Information"), patron.heading());
        assertTrue(patron.heading().contains("Patron"), "step name leads: " + patron.heading());
        assertEquals(List.of("Last name", "Your first name"), patron.items().stream().map(Item::label).toList(),
                "within a section the author's mark order decides (BR-004); the label replaces the text (BR-002)");
        assertEquals(List.of("Tester", "Tess"), patron.items().stream().map(Item::value).toList());
        Group preferences = s.groups().get(1);
        assertTrue(preferences.heading().contains("Collection Preferences"), preferences.heading());
        List<String> labels = preferences.items().stream().map(Item::label).toList();
        assertTrue(labels.contains("How many items would you like to check out today?"), labels.toString());
        assertTrue(labels.contains("Which types of media do you want to check out?"), labels.toString());
        assertFalse(items(s).stream().anyMatch(i -> i.value().contains("Tess@Tester.tst")),
                "an ADMINISTRATOR mark is not the respondent report (UC-013 BR-002)");
    }

    @Test
    @TestTransaction
    void given_eachQuestionType_then_valuesRenderByType() {
        // UC-014 BR-005, BR-006
        mark("RESPONDENT", null, durableQuestion("Would you like access to the digital library (eBooks, audiobooks, and streaming media)?"), null, "Digital", 1);
        mark("RESPONDENT", null, durableQuestion("Which types of media do you want to check out?"), null, "Media", 2);
        mark("RESPONDENT", null, durableQuestion("I agree to the library terms of use, borrowing policies, and privacy notice."), null, "Terms", 3);
        mark("RESPONDENT", null, durableQuestion("What is your date of birth?"), null, "Born", 4);
        mark("RESPONDENT", null, durableQuestion("How many items would you like to check out today?"), null, "Items", 5);
        mark("RESPONDENT", null, durableQuestion("Create a PIN or password for your library account"), null, "PIN", 6);
        mark("RESPONDENT", null, durableQuestionOfType("HTML"), null, "Welcome", 7);

        Summary s = service.summary(TESS, "en", "test").orElseThrow();
        List<Item> all = items(s);
        Item digital = all.stream().filter(i -> i.label().equals("Digital")).findFirst().orElseThrow();
        assertFalse(digital.value().equals("TRUE"), "a RADIO shows the option's text, never its code: " + digital.value());
        Item media = all.stream().filter(i -> i.label().equals("Media")).findFirst().orElseThrow();
        assertTrue(media.value().contains(", "), "a CHECKBOX_GROUP lists each chosen option: " + media.value());
        assertFalse(media.value().contains("audiobook,dvd"), media.value());
        Item terms = all.stream().filter(i -> i.label().equals("Terms")).findFirst().orElseThrow();
        assertEquals("Yes", terms.value(), "a CHECKBOX is Yes or No");
        Item born = all.stream().filter(i -> i.label().equals("Born")).findFirst().orElseThrow();
        assertTrue(born.value().contains("2000") && !born.value().equals("2000-01-01"), "a date is formatted for the language: " + born.value());
        Item count = all.stream().filter(i -> i.label().equals("Items")).findFirst().orElseThrow();
        assertEquals("2", count.value());
        assertTrue(all.stream().noneMatch(i -> i.label().equals("PIN")), "BR-003: a PASSWORD question never shows");
        assertTrue(all.stream().noneMatch(i -> i.label().equals("Welcome")), "BR-003: an HTML question never shows");
        ReportResponse r = service.toResponse(s);
        assertTrue(r.innerHTML.contains("<dt>Terms</dt><dd>Yes</dd>"), r.innerHTML);
        assertFalse(r.innerHTML.contains("<h1>"), "BR-005: authored text is escaped, never rendered as HTML");
    }

    @Test
    @TestTransaction
    void given_anUnansweredMarkedQuestion_then_itIsLeftOut() {
        // UC-014 BR-006: Tess never answered the renewals question (no text_value).
        int unanswered = ((Number) em.createNativeQuery("SELECT q.question_id FROM survey.answers a JOIN survey.questions q ON q.id = a.question_id "
                + "WHERE a.respondent_id = ?1 AND a.text_value IS NULL AND q.type_id NOT IN (SELECT id FROM survey.question_types WHERE name IN ('HTML','CHECKBOX','PASSWORD','MODAL')) ORDER BY a.id LIMIT 1")
                .setParameter(1, TESS).getSingleResult()).intValue();
        mark("RESPONDENT", null, unanswered, null, "Skipped", 1);
        Summary s = service.summary(TESS, "en", "test").orElseThrow();
        assertTrue(items(s).isEmpty(), "nothing to say about a skipped question: " + items(s));
    }

    @Test
    @TestTransaction
    void given_noLanguageInTheRequest_then_theRespondentsThenTheSurveysLanguageIsUsed() {
        // UC-014 BR-002
        Survey survey = Survey.findById(SURVEY_ID);
        assertEquals(survey.baseLanguage == null ? "en" : survey.baseLanguage, service.resolveLanguage(TESS, null, survey),
                "Tess's rows carry no display_language, so the survey's base language applies");
        em.createNativeQuery("UPDATE survey.answers SET display_language = 'es-419' WHERE respondent_id = ?1 AND id = 6")
                .setParameter(1, TESS).executeUpdate();
        assertEquals("es-419", service.resolveLanguage(TESS, "", survey), "the latest saved answer's language");
        assertEquals("ar", service.resolveLanguage(TESS, "ar", survey), "the request's language wins");
        Summary arabic = service.summary(TESS, "ar", "test").orElseThrow();
        assertTrue(arabic.rightToLeft());
        assertTrue(service.toResponse(arabic).innerHTML.contains("dir=\"rtl\""), "BR-007");
    }

    @Test
    @TestTransaction
    void given_anUnknownRespondent_then_empty() {
        // UC-014 A4
        assertTrue(service.summary(999_999, "en", "test").isEmpty());
        assertTrue(service.render(new ReportRequest(999_999, "en"), "test").isEmpty());
        Optional<ReportResponse> r = service.render(new ReportRequest(TESS, "en"), "test");
        assertTrue(r.isPresent());
        assertNull(r.get().pdf.styles);
        assertEquals(RespondentSummaryService.PATH, service.path());
    }
}
