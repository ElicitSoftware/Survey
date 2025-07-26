package com.elicitsoftware.event;

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
 * Event fired when section navigation data should be refreshed.
 * <p>
 * This event is fired when saveAnswer() is called and new navigation items
 * may have been added to the database.
 */
public class SectionNavigationUpdateEvent {
    
    private final Integer respondentId;
    
    /**
     * Creates a new section navigation update event.
     *
     * @param respondentId the ID of the respondent whose navigation data was updated
     */
    public SectionNavigationUpdateEvent(Integer respondentId) {
        this.respondentId = respondentId;
    }
    
    /**
     * Gets the respondent ID.
     *
     * @return the respondent ID
     */
    public Integer getRespondentId() {
        return respondentId;
    }
}
