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

import io.quarkus.oidc.client.OidcClient;
import io.quarkus.oidc.client.OidcClients;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.ConfigProvider;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * The site's credential for a post-survey action (UC-015 BR-003). It is configuration keyed by the
 * action's key, never a column or a definition-file field:
 * <ul>
 *   <li>{@code elicit.post-survey-action."<key>".bearer-token}: sent as {@code Authorization: Bearer <token>};</li>
 *   <li>{@code elicit.post-survey-action."<key>".oidc-client}: the name of a configured OIDC client
 *       ({@code quarkus.oidc-client.<name>.*}) whose access token is sent the same way.</li>
 * </ul>
 * A bearer token wins when both are set. No credential is ever logged or returned to a caller
 * other than the call itself; {@link #describe} says only which kind is configured.
 */
@ApplicationScoped
public class PostSurveyActionCredentials {

    @Inject
    Instance<OidcClients> oidcClients;

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

    /** UC-007 BR-005: which kind of credential is configured for the action, never its value. */
    public String describe(UUID actionKey) {
        if (value(actionKey, "bearer-token").isPresent()) {
            return "bearer token";
        }
        return value(actionKey, "oidc-client").map(name -> "OIDC client " + name).orElse("none");
    }

    /**
     * The {@code Authorization} header value for the action's call, if the site configured a
     * credential for it.
     *
     * @throws Exception the named OIDC client does not exist or could not obtain a token; the
     *                   call is then a failed call, retried later (UC-015 A1)
     */
    public Optional<String> authorization(UUID actionKey, String actionName, Duration timeout) throws Exception {
        Optional<String> token = value(actionKey, "bearer-token");
        if (token.isPresent()) {
            return Optional.of("Bearer " + token.get());
        }
        Optional<String> clientName = value(actionKey, "oidc-client");
        if (clientName.isEmpty()) {
            return Optional.empty();
        }
        String prefix = "Post Survey Action '" + actionName + "' Error: ";
        if (oidcClients == null || !oidcClients.isResolvable()) {
            throw new Exception(prefix + "OIDC client " + clientName.get() + " is configured but OIDC clients are unavailable");
        }
        OidcClient client = oidcClients.get().getClient(clientName.get());
        if (client == null) {
            throw new Exception(prefix + "No OIDC client named " + clientName.get() + " is configured (quarkus.oidc-client." + clientName.get() + ".*)");
        }
        try {
            String accessToken = client.getTokens().await().atMost(timeout).getAccessToken();
            return Optional.of("Bearer " + accessToken);
        } catch (RuntimeException e) {
            throw new Exception(prefix + "OIDC client " + clientName.get() + " could not obtain a token - " + e.getMessage(), e);
        }
    }
}
