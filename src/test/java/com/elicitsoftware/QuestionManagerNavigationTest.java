package com.elicitsoftware;

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

import com.elicitsoftware.model.Answer;
import com.elicitsoftware.model.Relationship;
import com.elicitsoftware.response.NavigationItem;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UC-002: Answer Survey Questions — the navigation half of QuestionManager that needs no
 * database: building the Previous/Next chain from the respondent's section marker rows, picking
 * the current item for a section key (BR-014: a repeated section's instance is part of the
 * match), the key a rule's downstream element is built at when the rule names no step or
 * section, and UC-004's purge of soft-deleted rows.
 * <p>
 * The helpers are private and reached by reflection; the queries go to a
 * {@link RecordingEntityManager}, and {@code currentContentLanguage} -- the one lookup in the
 * path that needs a Respondent row -- is overridden. Everything that resolves a respondent's
 * snapshot anchor ({@code Respondent.snapshotAnchor}, a Panache finder) is out of reach here.
 */
class QuestionManagerNavigationTest {

    private static Object invoke(QuestionManager manager, String name, Class<?>[] types, Object... args)
            throws Exception {
        Method method = QuestionManager.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        try {
            return method.invoke(manager, args);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception cause) {
                throw cause;
            }
            throw e;
        }
    }

    /** A manager whose content language is fixed, so buildNavItems needs no Respondent row. */
    private static QuestionManager manager(RecordingEntityManager em, String language) {
        QuestionManager manager = new QuestionManager() {
            @Override
            String currentContentLanguage(int respondentId) {
                return language;
            }
        };
        manager.entityManager = em.entityManager();
        return manager;
    }

    @SuppressWarnings("unchecked")
    private static List<NavigationItem> buildNavItems(QuestionManager manager, int respondentId) throws Exception {
        return (List<NavigationItem>) invoke(manager, "buildNavItems", new Class<?>[]{int.class}, respondentId);
    }

    // ---- buildNavItems -------------------------------------------------------------------------

    @Test
    void given_threeSectionMarkers_when_buildNavItems_then_eachLinksToItsNeighbours() throws Exception {
        List<Object[]> rows = List.of(
                new Object[]{"Intro", "0001-0001-0000-0001-0000-0000-0000"},
                new Object[]{"Health", "0001-0001-0000-0002-0000-0000-0000"},
                new Object[]{"Family", "0001-0002-0000-0001-0000-0000-0000"});
        RecordingEntityManager em = new RecordingEntityManager(sql -> rows);

        List<NavigationItem> items = buildNavItems(manager(em, "es-419"), 12);

        assertEquals(3, items.size());
        assertEquals("Intro", items.get(0).getName());
        assertEquals("0001-0001-0000-0001-0000-0000-0000", items.get(0).getPath());
        assertNull(items.get(0).getPrevious());
        assertEquals("0001-0001-0000-0002-0000-0000-0000", items.get(0).getNext());

        assertEquals("Health", items.get(1).getName());
        assertEquals("0001-0001-0000-0001-0000-0000-0000", items.get(1).getPrevious());
        assertEquals("0001-0002-0000-0001-0000-0000-0000", items.get(1).getNext());

        assertEquals("Family", items.get(2).getName());
        assertEquals("0001-0001-0000-0002-0000-0000-0000", items.get(2).getPrevious());
        assertNull(items.get(2).getNext());
    }

    @Test
    void given_aRespondent_when_buildNavItems_then_queryIsBoundToTheRespondentAndTheirLanguage() throws Exception {
        RecordingEntityManager em = new RecordingEntityManager(sql -> List.of());

        List<NavigationItem> items = buildNavItems(manager(em, "ar"), 12);

        assertTrue(items.isEmpty());
        assertEquals(1, em.queries().size());
        RecordingEntityManager.Recorded query = em.queries().get(0);
        assertEquals(12, query.parameters.get("respondentId"));
        assertEquals("ar", query.parameters.get("language"));
        assertEquals(1, query.resultListCalls);
        // Only active section markers: no question rows, no step-level rows.
        assertTrue(query.sql.contains("a.deleted = false"));
        assertTrue(query.sql.contains("a.question_id is null"));
        assertTrue(query.sql.contains("a.section != 0"));
        assertTrue(query.sql.contains("ORDER BY a.display_key"));
    }

    @Test
    void given_oneSectionMarker_when_buildNavItems_then_itHasNoNeighbours() throws Exception {
        List<Object[]> rows = List.<Object[]>of(new Object[]{"Only", "0001-0001-0000-0001-0000-0000-0000"});
        RecordingEntityManager em = new RecordingEntityManager(sql -> rows);

        List<NavigationItem> items = buildNavItems(manager(em, ""), 1);

        assertEquals(1, items.size());
        assertNull(items.get(0).getPrevious());
        assertNull(items.get(0).getNext());
    }

    // ---- getCurrentNavItem ---------------------------------------------------------------------

    private static NavigationItem item(String path) {
        return new NavigationItem(path, false, path, null, null);
    }

    private static NavigationItem current(NavigationItem[] items, String key) throws Exception {
        return (NavigationItem) invoke(new QuestionManager(), "getCurrentNavItem",
                new Class<?>[]{NavigationItem[].class, String.class}, items, key);
    }

    @Test
    void given_aRepeatedSectionInstance_when_getCurrentNavItem_then_thatInstancesItemIsReturned() throws Exception {
        NavigationItem first = item("0001-0001-0000-0002-0001-0000-0000");
        NavigationItem second = item("0001-0001-0000-0002-0002-0000-0000");
        NavigationItem[] items = {item("0001-0001-0000-0001-0000-0000-0000"), first, second};

        // A question key inside the second instance names the second instance's marker (BR-014).
        assertSame(second, current(items, "1-1-0-2-2-5-1"));
        assertSame(first, current(items, "1-1-0-2-1-0-0"));
    }

    @Test
    void given_noItemForTheSection_when_getCurrentNavItem_then_null() throws Exception {
        NavigationItem[] items = {item("0001-0001-0000-0001-0000-0000-0000")};

        assertNull(current(items, "1-1-0-3-0-0-0"));
        assertNull(current(new NavigationItem[0], "1-1-0-1-0-0-0"));
    }

    // ---- buildDisplayKey -----------------------------------------------------------------------

    @Test
    void given_aRuleNamingNoStepOrSection_when_buildDisplayKey_then_upstreamPlacementIsKeptAndQuestionCleared()
            throws Exception {
        Answer upstream = new Answer();
        upstream.displayKey = "0003-0002-0001-0004-0005-0006-0007";
        upstream.respondentId = 9;
        upstream.question_instance = 7;
        Relationship rule = new Relationship();
        rule.surveyId = 3;

        DisplayKey key = (DisplayKey) invoke(new QuestionManager(), "buildDisplayKey",
                new Class<?>[]{Answer.class, Relationship.class}, upstream, rule);

        // Step, step instance, section and section instance come from the upstream answer; the
        // question and its instance are left for the caller to fill.
        assertEquals("0003-0002-0001-0004-0005-0000-0000", key.getValue());
    }

    // ---- removeDeleted -------------------------------------------------------------------------

    @Test
    void given_aRespondent_when_removeDeleted_then_dependentsAndThenAnswersArePurgedForThemOnly() {
        RecordingEntityManager em = new RecordingEntityManager(sql -> List.of());
        QuestionManager manager = new QuestionManager();
        manager.entityManager = em.entityManager();

        manager.removeDeleted(31);

        List<RecordingEntityManager.Recorded> queries = new ArrayList<>(em.queries());
        assertEquals(2, queries.size());
        // Dependents first: they reference the answers being removed.
        assertEquals("DELETE FROM survey.dependents d where d.deleted = true and d.respondent_id = :respondentId",
                queries.get(0).sql);
        assertEquals("DELETE FROM survey.answers a WHERE a.deleted = true and a.respondent_id = :respondentId",
                queries.get(1).sql);
        for (RecordingEntityManager.Recorded query : queries) {
            assertEquals(31, query.parameters.get("respondentId"));
            assertEquals(1, query.executeUpdates);
        }
    }
}
