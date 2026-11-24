package com.elicitsoftware.response;

/*-
 * ***LICENSE_START***
 * Elicit Survey
 * %%
 * Copyright (C) 2025 - 2026 Elicit Software LLC
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import jakarta.xml.bind.annotation.XmlRootElement;

import java.util.List;

/**
 * Represents a response containing review sections.
 * <p>
 * This class encapsulates a collection of review sections, each of which may
 * include categorized items and metadata. It is intended to organize information
 * in a structured manner as part of the review process.
 * <p>
 * The {@code sections} list holds multiple {@link ReviewSection} objects, which
 * encapsulate individual sections of a review.
 */
@XmlRootElement
public class ReviewResponse {
    private final List<ReviewSection> sections;

    /**
     * A response holding the given sections.
     *
     * @param sections the review sections, kept as given
     */
    public ReviewResponse(List<ReviewSection> sections) {
        super();
        this.sections = sections;
    }

    /**
     * Returns the review sections.
     *
     * @return the sections, as given to the constructor
     */
    public List<ReviewSection> getSections() {
        return sections;
    }
}
