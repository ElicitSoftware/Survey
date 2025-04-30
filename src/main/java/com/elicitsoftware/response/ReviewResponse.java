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

    public ReviewResponse(List<ReviewSection> sections) {
        super();
        this.sections = sections;
    }

    public List<ReviewSection> getSections() {
        return sections;
    }
}
