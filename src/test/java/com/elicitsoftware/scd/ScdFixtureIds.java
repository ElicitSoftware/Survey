package com.elicitsoftware.scd;

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

import jakarta.persistence.EntityManager;

/**
 * Looks up the durable ids in the V011__SCD_Spec_Fixture.sql fixture by name/marker
 * rather than by hardcoded numeric id — the fixture is inserted after every other test
 * migration (V005/V005.5/V005.6), so its actual sequence-assigned ids are not fixed
 * small numbers the way the V005 fixture's are.
 * <p>
 * Every method here queries TODAY's columns only (id, name/text, survey_id, ...) so this
 * class compiles and works both before and after the Type 2 migration lands.
 */
final class ScdFixtureIds {

    private ScdFixtureIds() {
    }

    static Integer surveyId(EntityManager em) {
        return (Integer) em.createNativeQuery(
                "SELECT id FROM survey.surveys WHERE name = 'ScdSpecFixture'").getSingleResult();
    }

    static Integer stepId(EntityManager em) {
        return (Integer) em.createNativeQuery(
                "SELECT id FROM survey.steps WHERE name = 'ScdStep' AND survey_id = " + surveyId(em)).getSingleResult();
    }

    static Integer sectionId(EntityManager em) {
        return (Integer) em.createNativeQuery(
                "SELECT id FROM survey.sections WHERE name = 'ScdSection' AND survey_id = " + surveyId(em)).getSingleResult();
    }

    static Integer stepsSectionsId(EntityManager em) {
        return (Integer) em.createNativeQuery(
                "SELECT id FROM survey.steps_sections WHERE step_id = " + stepId(em) + " AND section_id = " + sectionId(em)).getSingleResult();
    }

    static Integer selectGroupId(EntityManager em) {
        return (Integer) em.createNativeQuery(
                "SELECT id FROM survey.select_groups WHERE name = 'ScdSelectGroup' AND survey_id = " + surveyId(em)).getSingleResult();
    }

    static Integer selectItemId(EntityManager em) {
        return (Integer) em.createNativeQuery(
                "SELECT id FROM survey.select_items WHERE group_id = " + selectGroupId(em) + " AND display_text = 'Option A'").getSingleResult();
    }

    static Integer questionId(EntityManager em) {
        return (Integer) em.createNativeQuery(
                "SELECT id FROM survey.questions WHERE short_text = 'ScdQ' AND survey_id = " + surveyId(em)).getSingleResult();
    }

    static Integer sectionsQuestionId(EntityManager em) {
        return (Integer) em.createNativeQuery(
                "SELECT id FROM survey.sections_questions WHERE question_id = " + questionId(em) + " AND section_id = " + sectionId(em)).getSingleResult();
    }

    static Integer relationshipId(EntityManager em) {
        return (Integer) em.createNativeQuery(
                "SELECT id FROM survey.relationships WHERE description = 'ScdSpecFixture self-reference' AND survey_id = " + surveyId(em)).getSingleResult();
    }
}
