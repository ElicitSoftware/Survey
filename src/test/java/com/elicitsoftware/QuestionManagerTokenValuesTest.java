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

import com.elicitsoftware.i18n.ContentTranslator;
import com.elicitsoftware.model.Relationship;
import com.elicitsoftware.model.SelectItem;
import com.elicitsoftware.model.Survey;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * UC-009: Complete the Survey in My Language, with UC-002 BR-010 and BR-013 — the token values
 * QuestionManager keeps for one answer, each beside where it came from: the rule whose authored
 * constant supplied it, or the select item a repeated instance was built from. That provenance is
 * what lets one walk of the dependents produce both the base rendering and the translated one
 * (BR-005 of UC-009): {@code localized} swaps in the translation of exactly the authored values,
 * and {@code putAll} lets an inner value replace an outer one together with its provenance.
 * <p>
 * {@code TokenValues} is a private record, reached by reflection; the translator is a subclass
 * that answers from a table, so no translation rows are read.
 */
class QuestionManagerTokenValuesTest {

    private static final Class<?> TOKEN_VALUES;

    static {
        try {
            TOKEN_VALUES = Class.forName("com.elicitsoftware.QuestionManager$TokenValues");
        } catch (ClassNotFoundException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private static Object newValues() throws Exception {
        Constructor<?> constructor = TOKEN_VALUES.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static Object call(Object target, String name, Class<?>[] types, Object... args) throws Exception {
        Method method = TOKEN_VALUES.getDeclaredMethod(name, types);
        method.setAccessible(true);
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw (Exception) e.getCause();
        }
    }

    @SuppressWarnings("unchecked")
    private static TreeMap<String, String> base(Object values) throws Exception {
        return (TreeMap<String, String>) call(values, "base", new Class<?>[0]);
    }

    @SuppressWarnings("unchecked")
    private static TreeMap<String, Relationship> sources(Object values) throws Exception {
        return (TreeMap<String, Relationship>) call(values, "sources", new Class<?>[0]);
    }

    @SuppressWarnings("unchecked")
    private static TreeMap<String, SelectItem> items(Object values) throws Exception {
        return (TreeMap<String, SelectItem>) call(values, "items", new Class<?>[0]);
    }

    private static void putAll(Object into, Object other) throws Exception {
        call(into, "putAll", new Class<?>[]{TOKEN_VALUES}, other);
    }

    @SuppressWarnings("unchecked")
    private static TreeMap<String, String> localized(Object values, ContentTranslator translator) throws Exception {
        return (TreeMap<String, String>) call(values, "localized",
                new Class<?>[]{Survey.class, ContentTranslator.class, OffsetDateTime.class},
                new Survey(), translator, OffsetDateTime.now());
    }

    private static Relationship rule(int id) {
        Relationship rule = new Relationship();
        rule.id = id;
        return rule;
    }

    private static SelectItem selectItem(String text) {
        SelectItem item = new SelectItem();
        item.displayText = text;
        return item;
    }

    /** Answers item and rule translations from fixed tables, by identity. */
    private static ContentTranslator translator(Map<SelectItem, String> items, Map<Relationship, String> rules) {
        return new ContentTranslator() {
            @Override
            public String displayText(Survey survey, SelectItem item, OffsetDateTime asOf) {
                return items.get(item);
            }

            @Override
            public String defaultUpstreamValue(Survey survey, Relationship relationship, OffsetDateTime asOf) {
                return rules.get(relationship);
            }
        };
    }

    @Test
    void given_noValues_when_constructed_then_allThreeMapsAreEmptyAndMutable() throws Exception {
        Object values = newValues();

        base(values).put("A", "a");
        assertEquals(Map.of("A", "a"), base(values));
        assertEquals(Map.of(), sources(values));
        assertEquals(Map.of(), items(values));
    }

    @Test
    void given_anInnerValueForATokenWithOuterProvenance_when_putAll_then_theProvenanceGoesWithTheOuterValue()
            throws Exception {
        Relationship outerRule = rule(1);
        SelectItem outerItem = selectItem("Chess");
        Object outer = newValues();
        base(outer).put("S1", "your mother");
        sources(outer).put("S1", outerRule);
        base(outer).put("GAME", "Chess");
        items(outer).put("GAME", outerItem);
        base(outer).put("KEEP", "kept");
        sources(outer).put("KEEP", outerRule);

        Relationship innerRule = rule(2);
        SelectItem innerItem = selectItem("Go");
        Object inner = newValues();
        base(inner).put("S1", "Dennis");          // the respondent's own words, no provenance
        base(inner).put("GAME", "Go");
        base(inner).put("NEW", "new");
        sources(inner).put("NEW", innerRule);
        base(inner).put("ITEM", "Go");
        items(inner).put("ITEM", innerItem);

        putAll(outer, inner);

        assertEquals("Dennis", base(outer).get("S1"));
        assertEquals("Go", base(outer).get("GAME"));
        assertEquals("kept", base(outer).get("KEEP"));
        // A replaced value no longer carries the rule or item it used to come from ...
        assertEquals(Map.of("KEEP", outerRule, "NEW", innerRule), sources(outer));
        // ... and the inner value's own provenance arrives with it.
        assertEquals(Map.of("ITEM", innerItem), items(outer));
    }

    @Test
    void given_authoredAndItemValues_when_localized_then_onlyThoseAreTranslated() throws Exception {
        Relationship translatedRule = rule(1);
        Relationship untranslatedRule = rule(2);
        SelectItem game = selectItem("Chess");
        Object values = newValues();
        base(values).put("S1", "your mother");
        sources(values).put("S1", translatedRule);
        base(values).put("S2", "your father");
        sources(values).put("S2", untranslatedRule);
        base(values).put("GAME", "Chess");
        items(values).put("GAME", game);
        base(values).put("NAME", "Dennis");

        TreeMap<String, String> local = localized(values,
                translator(Map.of(game, "Ajedrez"), Map.of(translatedRule, "tu madre")));

        assertEquals("tu madre", local.get("S1"));
        // No translation for the rule's constant: the base text stands rather than a null.
        assertEquals("your father", local.get("S2"));
        assertEquals("Ajedrez", local.get("GAME"));
        // The respondent's own words are never translated.
        assertEquals("Dennis", local.get("NAME"));
        assertEquals(4, local.size());
        // The base rendering is untouched.
        assertEquals("your mother", base(values).get("S1"));
        assertEquals("Chess", base(values).get("GAME"));
    }

    @Test
    void given_values_when_localized_then_aNewMapIsReturned() throws Exception {
        Object values = newValues();
        base(values).put("A", "a");

        TreeMap<String, String> local = localized(values, translator(Map.of(), Map.of()));

        assertEquals(Map.of("A", "a"), local);
        local.put("B", "b");
        assertEquals(Map.of("A", "a"), base(values));
        assertSame(base(values), base(values));
    }
}
