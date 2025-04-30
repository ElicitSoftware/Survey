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

import java.util.List;

/**
 * Represents a section in a review comprising a title, a display key for identification,
 * and a list of review items.
 * <p>
 * This class is used to organize and represent categorized information within a review response.
 * It contains the metadata for the section such as its title and key, along with a collection
 * of associated items that provide additional details.
 */
public class ReviewSection {

    private final String displayKey;
    private String title;
    private List<ReviewItem> items;

    public ReviewSection(String title, String displayKey, List<ReviewItem> items) {
        super();
        this.title = title;
        this.displayKey = displayKey;
        this.items = items;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDisplayKey() {
        return displayKey;
    }

    public List<ReviewItem> getItems() {
        return items;
    }

    public void setItems(List<ReviewItem> items) {
        this.items = items;
    }

}
