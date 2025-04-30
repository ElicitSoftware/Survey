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
 * Represents a user with a token for identification or authentication purposes.
 */
public class User {
    private String token;

    /**
     * Default constructor for the User class.
     * Initializes a new instance of the User class with default values.
     */
    public User() {}

    /**
     * Constructs a new User with the specified token.
     *
     * @param token the unique token associated with the user
     */
    public User(String token) {
        this.token = token;
    }

    /**
     * Retrieves the token associated with the user.
     *
     * @return the token as a String
     */
    public String getToken() {
        return token;
    }

    /**
     * Sets the token for the user.
     *
     * @param token the token to be assigned to the user
     */
    public void setToken(String token) {
        this.token = token;
    }
}
