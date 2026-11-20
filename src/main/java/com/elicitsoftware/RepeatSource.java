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

import com.elicitsoftware.model.Question;
import com.elicitsoftware.model.SelectItem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The instances a Repeat rule builds, decided from the question the rule reads (UC-002 BR-012).
 * <p>
 * There are two kinds of answer a repeat can be driven by, and they number their instances
 * differently:
 * <ul>
 *   <li>A <b>count</b> — a number the respondent typed — gives the instances 1 to N.</li>
 *   <li>A <b>selection</b> — the items picked in a {@code MULTI_SELECT} or {@code CHECKBOX_GROUP}
 *       question, stored as their coded values joined by commas — gives one instance per selected
 *       item, numbered by <em>the item's position in its list</em>. Selecting the second and fourth
 *       items gives instances 2 and 4, not 1 and 2.</li>
 * </ul>
 * The position, rather than a running count, is what keeps a respondent's answers with the item
 * they are about. Numbered 1..n, adding the first item of the list to a selection of the second
 * and fourth would renumber those two, and the answers given for one would be read under the
 * title of another. Numbered by position, an item's instance never moves: changing the selection
 * only adds and removes instances, and reselecting an item brings its answers back.
 * <p>
 * The same position is what turns an instance back into its item when a token is filled
 * (UC-002 BR-013), which is why both directions live here.
 */
final class RepeatSource {

    /** The question types answered by selecting several items of a list. */
    private static final Set<String> SELECTION_TYPES = Set.of("MULTI_SELECT", "CHECKBOX_GROUP");

    private RepeatSource() {
    }

    /**
     * True when a repeat reading this question builds one instance per selected item rather than
     * 1 to N.
     */
    static boolean perItem(Question question) {
        return question != null && question.questionType != null && question.selectGroupId != null
                && SELECTION_TYPES.contains(question.questionType.name);
    }

    /**
     * The instance numbers the answer asks for, in ascending order.
     *
     * @param question  the question version the answer was given to
     * @param textValue the answer's stored value: a number, or coded values joined by commas
     * @param items     the question's select items in list order, as of the respondent's snapshot
     *                  anchor; read only for a {@link #perItem per-item} question
     * @return the instances, empty when nothing was answered
     * @throws NumberFormatException when a count is not a whole number
     */
    static List<Integer> instances(Question question, String textValue, List<SelectItem> items) {
        List<Integer> instances = new ArrayList<>();
        if (textValue == null || textValue.isBlank()) {
            return instances;
        }
        if (!perItem(question)) {
            int count = Integer.parseInt(textValue.trim());
            for (int i = 1; i <= count; i++) {
                instances.add(i);
            }
            return instances;
        }
        Set<String> selected = new HashSet<>();
        Arrays.stream(textValue.split(",")).map(String::trim).forEach(selected::add);
        for (int i = 0; i < items.size(); i++) {
            // A selected code the list no longer holds has no position, and so no instance.
            if (selected.contains(items.get(i).codedValue)) {
                instances.add(i + 1);
            }
        }
        return instances;
    }

    /**
     * The item an instance was built from, or {@code null} when the list has no such position.
     *
     * @param items    the question's select items in list order
     * @param instance the instance number, 1 for the first item
     */
    static SelectItem item(List<SelectItem> items, int instance) {
        return instance < 1 || instance > items.size() ? null : items.get(instance - 1);
    }
}
