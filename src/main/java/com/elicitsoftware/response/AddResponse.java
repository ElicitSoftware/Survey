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
 * It includes the identifier of the respondent, an associated token for identification
 * or access purposes, and an error message if the operation encountered an issue.
 * <p>
 * The {@code respondentId} represents the unique identifier of the user or entity
 * involved in the operation. The {@code token} is typically used for authentication
 * or to reference the operation, while the {@code error} provides details when an
 * error occurs during the add process.
 */
public class AddResponse {
    private int respondentId;
    private String token;
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
     * Gets the token associated with this response.
     *
     * @return the authentication or reference token
     */
    public String getToken() {
        return token;
    }

    /**
     * Sets the token for this response.
     *
     * @param token the authentication or reference token
     */
    public void setToken(String token) {
        this.token = token;
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
        return "AddResponse [respondentId=" + respondentId + ", token=" + token + ", error=" + error + "]";
    }
}
