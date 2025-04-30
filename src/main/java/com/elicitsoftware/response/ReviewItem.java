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

/**
 * Represents an individual item in a review, consisting of a label and a value.
 * <p>
 * This record is a simple data structure designed to encapsulate a piece of information
 * within a review section. Each review item pairs a descriptive label with its corresponding
 * value, enabling clear representation of key-value pairs in review responses.
 * <p>
 * The label is intended to describe or identify the item, while the value contains
 * the associated data or description for that label.
 */
public record ReviewItem(String label, String value) {
}
