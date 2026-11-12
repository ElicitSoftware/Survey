package com.elicitsoftware.psa;

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

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.ConfigProvider;

import java.util.Optional;
import java.util.UUID;

/**
 * The site's credential for a post-survey action (UC-015 BR-003). It is configuration keyed by the
 * action's key, never a column or a definition-file field:
 * {@code elicit.post-survey-action."<key>".bearer-token}, the token the service issued to the site,
 * sent as {@code Authorization: Bearer <token>}. Survey only presents the token it was given; it
 * never obtains or refreshes one. No credential is ever logged or returned to a caller other than
 * the call itself; {@link #describe} says only whether one is configured.
 */
@ApplicationScoped
public class PostSurveyActionCredentials {

    /** The setting for one leaf of an action's credential, e.g. {@code bearer-token}. */
    static String setting(UUID actionKey, String leaf) {
        return "elicit.post-survey-action.\"" + actionKey + "\"." + leaf;
    }

    Optional<String> value(UUID actionKey, String leaf) {
        if (actionKey == null) {
            return Optional.empty();
        }
        String name = setting(actionKey, leaf);
        return ConfigProvider.getConfig().getOptionalValue(name, String.class)
                .map(String::strip)
                .filter(v -> !v.isEmpty());
    }

    /** UC-007 BR-005: whether a credential is configured for the action, never its value. */
    public String describe(UUID actionKey) {
        return value(actionKey, "bearer-token").isPresent() ? "bearer token" : "none";
    }

    /**
     * The {@code Authorization} header value for the action's call, if the site configured a
     * credential for it.
     */
    public Optional<String> authorization(UUID actionKey) {
        return value(actionKey, "bearer-token").map(token -> "Bearer " + token);
    }
}
