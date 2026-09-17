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
 * Represents the response generated after an add operation.
 * <p>
 * This class encapsulates information relevant to the result of an addition process.
 * It includes the identifier of the respondent, the access code the respondent enters to
 * reach the survey, and an error message if the operation encountered an issue.
 * <p>
 * The {@code respondentId} represents the unique identifier of the user or entity
 * involved in the operation. The {@code accessCode} is the credential issued to that
 * respondent, while the {@code error} provides details when an error occurs during the
 * add process.
 */
public class AddResponse {
    private int respondentId;
    private String accessCode;
    private String error;

    /**
     * Gets the respondent ID associated with this response.
     *
     * @return the unique identifier of the respondent
     */
    public int getRespondentId() {
        return respondentId;
    }

    /**
     * Sets the respondent ID for this response.
     *
     * @param respondentId the unique identifier of the respondent
     */
    public void setRespondentId(int respondentId) {
        this.respondentId = respondentId;
    }

    /**
     * Gets the access code issued to the respondent.
     *
     * @return the respondent's access code
     */
    public String getAccessCode() {
        return accessCode;
    }

    /**
     * Sets the access code issued to the respondent.
     *
     * @param accessCode the respondent's access code
     */
    public void setAccessCode(String accessCode) {
        this.accessCode = accessCode;
    }

    /**
     * Gets the error message if the operation encountered an issue.
     *
     * @return the error message, or null if no error occurred
     */
    public String getError() {
        return error;
    }

    /**
     * Sets the error message for this response.
     *
     * @param error the error message to set
     */
    public void setError(String error) {
        this.error = error;
    }

    @Override
    public String toString() {
        // The access code is a credential, so only report whether one was issued.
        return "AddResponse [respondentId=" + respondentId + ", accessCodePresent=" + (accessCode != null) + ", error=" + error + "]";
    }
}
