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
 * Represents a user with an access code for identification or authentication purposes.
 */
public class User {
    private String accessCode;

    /**
     * Default constructor for the User class.
     * Initializes a new instance of the User class with default values.
     */
    public User() {
    }

    /**
     * Constructs a new User with the specified access code.
     *
     * @param accessCode the unique access code associated with the user
     */
    public User(String accessCode) {
        this.accessCode = accessCode;
    }

    /**
     * Retrieves the access code associated with the user.
     *
     * @return the access code as a String
     */
    public String getAccessCode() {
        return accessCode;
    }

    /**
     * Sets the access code for the user.
     *
     * @param accessCode the access code to be assigned to the user
     */
    public void setAccessCode(String accessCode) {
        this.accessCode = accessCode;
    }
}
