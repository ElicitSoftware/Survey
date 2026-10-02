package com.elicitsoftware.etl;

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

import com.vaadin.quarkus.annotation.NormalUIScoped;
import jakarta.inject.Inject;

/**
 * The finalize-time load (UC-004): a respondent's dimension values and fact rows go into the
 * reporting schema of the survey they answered. The UI-scoped facade {@code QuestionService}
 * injects; the work is {@link ETLService#populateFactSectionTable(Integer)}, the same code the
 * build's back-fill runs (UC-008 step 6), so the two can never drift apart.
 */
@NormalUIScoped
public class ETLRespondentService {

    @Inject
    ETLService etlService;

    /**
     * Loads one respondent into their survey's reporting schema.
     *
     * @param respondentId the respondent who has just finalized
     * @return a summary of what was loaded, or why nothing was: the ETL is disabled on this
     * instance, or the survey has no schema yet and the next build's back-fill will load them
     */
    public String populateFactSectionTable(Integer respondentId) {
        return etlService.populateFactSectionTable(respondentId);
    }
}
