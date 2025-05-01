package com.elicitsoftware.response;

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

import com.elicitsoftware.model.Answer;
import com.elicitsoftware.model.Step;
import jakarta.xml.bind.annotation.XmlRootElement;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the response for a navigation process within a survey or application workflow.
 * This class aggregates navigation step information, the current navigation item,
 * a list of answers related to the current step, and additional navigation items.
 * It is designed to support processing and transferral of navigation-related data.
 */
@XmlRootElement
public class NavResponse {
    private final Step step;
    private final NavigationItem currentNavItem;
    private final List<Answer> answers;
    private final NavigationItem[] navItems;

    /**
     * Constructs a new NavResponse object.
     *
     * @param step           The current step in the navigation process.
     * @param currentNavItem The current navigation item being processed.
     * @param answers2       A list of answers associated with the current step.
     * @param navItems       An array of all navigation items available.
     */
    public NavResponse(Step step, NavigationItem currentNavItem, List<Answer> answers2, NavigationItem[] navItems) {
        super();
        this.step = step;
        this.currentNavItem = currentNavItem;
        this.answers = answers2;
        this.navItems = navItems;
    }

    /**
     * Retrieves the current step associated with this response.
     *
     * @return the {@link Step} object representing the current step.
     */
    public Step getStep() {
        return step;
    }

    /**
     * Retrieves the current navigation item.
     *
     * @return the current {@link NavigationItem} instance.
     */
    public NavigationItem getCurrentNavItem() {
        return currentNavItem;
    }

    /**
     * Retrieves the list of answers associated with this response.
     *
     * @return a list of {@link Answer} objects.
     */
    public List<Answer> getAnswers() {
        return answers;
    }

    /**
     * Retrieves a list of display keys from the answers.
     * Each display key is obtained by calling the {@code getDisplayKey()} method
     * on the {@code Answer} objects in the {@code answers} collection.
     *
     * @return An {@code ArrayList<String>} containing the display keys of all answers.
     */
    public ArrayList<String> getDisplayKeys() {
        ArrayList<String> displayKeys = new ArrayList<>();
        for (Answer a : answers) {
            displayKeys.add(a.getDisplayKey());
        }
        return displayKeys;
    }

    /**
     * Retrieves an Answer object from the list of answers based on the provided key.
     *
     * @param key the display key used to identify the desired Answer object.
     * @return the Answer object with the matching display key, or {@code null} if no match is found.
     */
    public Answer getAnswerByKey(String key) {
        for (Answer a : answers) {
            if (a.getDisplayKey().equals(key)) {
                return a;
            }
        }
        return null;
    }
}
