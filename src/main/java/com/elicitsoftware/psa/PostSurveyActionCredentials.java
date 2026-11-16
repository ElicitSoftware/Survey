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

import com.elicitsoftware.security.ServiceToken;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.ConfigProvider;

import java.net.URI;
import java.util.Optional;
import java.util.UUID;

/**
 * The site's credential for a post-survey action (UC-015 BR-003). It is configuration keyed by the
 * action's key, never a column or a definition-file field:
 * {@code elicit.post-survey-action."<key>".bearer-token}, the token the service issued to the site,
 * sent as {@code Authorization: Bearer <token>}. Survey only presents the token it was given; it
 * never obtains or refreshes one. No credential is ever logged or returned to a caller other than
 * the call itself; {@link #describe} says only whether one is configured.
 * <p>
 * An action with no credential of its own is called with the platform service token when its
 * address is at an origin the site trusts (UC-015 BR-010). One header at most is ever sent.
 */
@ApplicationScoped
public class PostSurveyActionCredentials {

    @Inject
    ServiceToken serviceToken;

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
     * UC-007 BR-005: which credential a call to the action carries, never its value.
     *
     * @param address the action's address as the definition holds it; placeholders may be unfilled
     * @return {@code bearer token}, {@code service token} or {@code none}
     */
    public String describe(UUID actionKey, String address) {
        if (value(actionKey, "bearer-token").isPresent()) {
            return "bearer token";
        }
        return trusted(address) ? "service token" : "none";
    }

    private boolean trusted(String address) {
        if (serviceToken == null || address == null) {
            return false;
        }
        try {
            // For the diagnostics line only: placeholders are filled with a digit so the address
            // parses. The call itself decides on the address as it is really filled.
            return serviceToken.trusts(URI.create(address.strip().replaceAll("\\{[A-Za-z]+}", "0")));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * UC-015 BR-010: the {@code Authorization} header for the action's call to this address. The
     * action's own credential when the site configured one, whatever the address; otherwise the
     * platform service token when the address's origin is trusted; otherwise none.
     *
     * @param address the action's address with its placeholders filled
     * @throws ServiceToken.NotConfigured the call would carry the service token and Survey has none
     */
    public Optional<String> authorizationFor(UUID actionKey, URI address) {
        Optional<String> own = authorization(actionKey);
        if (own.isPresent()) {
            return own;
        }
        return serviceToken.authorizationFor(address);
    }

    /**
     * The {@code Authorization} header value for the action's call, if the site configured a
     * credential for it.
     */
    public Optional<String> authorization(UUID actionKey) {
        return value(actionKey, "bearer-token").map(token -> "Bearer " + token);
    }
}
