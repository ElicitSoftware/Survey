package com.elicitsoftware.flow;

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

/**
 * The GlobalStrings class provides a centralized collection of constant string values
 * representing different types of questions. These constants can be used across the
 * application to ensure consistent usage of question type identifiers.
 * <p>
 * This class serves as a utility to avoid hardcoding question type strings in various
 * parts of the application while improving maintainability and readability.
 * <p>
 * Constants in this class include identifiers for various question types such as:
 * - Input field types for text, email, password, etc.
 * - Selection field types for radio buttons, checkboxes, comboboxes, etc.
 * - Specialized input types such as date pickers, time pickers, and multi-select fields.
 * <p>
 * Each constant is defined as a public static final field so it can be accessed
 * without instantiating the class.
 * <p>
 * Note that some constants may contain typos in their names (e.g., "QUESTIION" instead
 * of "QUESTION") and should be corrected if consistency is a goal.
 */
public class GlobalStrings {

    public static final String QUESTION_TYPE_CHECKBOX = "CHECKBOX";
    public static final String QUESTIION_TYPE_CHECKBOX_GROUP = "CHECKBOX_GROUP";
    public static final String QUESTION_TYPE_DATE_PICKER = "DATE_PICKER";
    public static final String QUESTIION_TYPE_DATE_TIME_PICKER = "DATE_TIME_PICKER";
    public static final String QUESTION_TYPE_DOUBLE = "DOUBLE";
    public static final String QUESTION_TYPE_COMBOBOX = "COMBOBOX";
    public static final String QUESTIION_TYPE_EMAIL = "EMAIL";
    public static final String QUESTION_TYPE_HTML = "HTML";
    public static final String QUESTION_TYPE_INTEGER = "INTEGER";
    public static final String QUESTION_TYPE_MODAL = "MODAL";
    public static final String QUESTION_TYPE_MULTI_SELECT = "MULTI_SELECT";
    public static final String QUESTIION_TYPE_MULTI_SELECT_COMBOBOX = "MULTI_SELECT_COMBOBOX";
    public static final String QUESTION_TYPE_RADIO = "RADIO";
    public static final String QUESTION_TYPE_TEXT = "TEXT";
    public static final String QUESTION_TYPE_TEXTAREA = "TEXTAREA";
    public static final String QUESTIION_TYPE_PASSWORD = "PASSWORD";
    public static final String QUESTIION_TYPE_TIME_PICKER = "TIME_PICKER";

}

