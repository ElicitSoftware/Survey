package com.elicitsoftware.psa;

/*-
 * ***LICENSE_START***
 * Elicit Survey
 * %%
 * Copyright (C) 2025 - 2026 Elicit Software LLC
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import com.elicitsoftware.security.ServiceToken;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * UC-015 BR-010: which credential a post-survey action's call carries. The action's own token
 * when the site configured one, whatever the address; otherwise the platform service token at a
 * trusted origin; otherwise none. One header at most.
 */
class PostSurveyActionCredentialsTest {

    private static final String SERVICE_TOKEN = "psa-credentials-service-token-0123456789";
    private static final UUID WITH_OWN_TOKEN = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID WITHOUT = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final URI TRUSTED = URI.create("http://fhhs:8080/familyhistory/generate");
    private static final URI ELSEWHERE = URI.create("https://partner.example.org/notify/7");

    /** Credentials whose per-action setting is the test's, not the configuration's. */
    private static PostSurveyActionCredentials credentials(String serviceToken) {
        PostSurveyActionCredentials credentials = new PostSurveyActionCredentials() {
            @Override
            Optional<String> value(UUID actionKey, String leaf) {
                return WITH_OWN_TOKEN.equals(actionKey) && leaf.equals("bearer-token") ? Optional.of("issued-by-the-partner") : Optional.empty();
            }
        };
        credentials.serviceToken = ServiceToken.of(serviceToken, "http://fhhs:8080");
        return credentials;
    }

    @Test
    void uc015_br010_anActionsOwnTokenIsSentWhateverTheAddress() {
        PostSurveyActionCredentials credentials = credentials(SERVICE_TOKEN);

        assertEquals(Optional.of("Bearer issued-by-the-partner"), credentials.authorizationFor(WITH_OWN_TOKEN, ELSEWHERE));
        assertEquals(Optional.of("Bearer issued-by-the-partner"), credentials.authorizationFor(WITH_OWN_TOKEN, TRUSTED),
                "never the service token, even at a trusted origin");
        assertEquals("bearer token", credentials.describe(WITH_OWN_TOKEN, TRUSTED.toString()));
    }

    @Test
    void uc015_br010_anActionWithoutOneGetsTheServiceTokenAtATrustedOriginOnly() {
        PostSurveyActionCredentials credentials = credentials(SERVICE_TOKEN);

        assertEquals(Optional.of("Bearer " + SERVICE_TOKEN), credentials.authorizationFor(WITHOUT, TRUSTED));
        assertEquals(Optional.empty(), credentials.authorizationFor(WITHOUT, ELSEWHERE));
        assertEquals(Optional.of("Bearer " + SERVICE_TOKEN), credentials.authorizationFor(null, TRUSTED), "an action with no key has no own token");
        assertEquals("service token", credentials.describe(WITHOUT, "http://fhhs:8080/familyhistory/generate/{respondentId}"));
        assertEquals("none", credentials.describe(WITHOUT, ELSEWHERE.toString()));
        assertEquals("none", credentials.describe(WITHOUT, "not an address {"));
        assertEquals("none", credentials.describe(WITHOUT, null));
    }

    @Test
    void uc015_br010_aPlaceholderCannotMakeAnAddressTrusted() {
        PostSurveyActionCredentials credentials = credentials(SERVICE_TOKEN);

        // The decision is made on the address as filled; these are what such fillings look like.
        for (String filled : new String[]{
                "http://fhhs:8080@7.evil.example/",
                "http://fhhs:8080.7.evil.example/",
                "http://7.evil.example/fhhs:8080",
                "http://fhhs:80807/"}) {
            assertEquals(Optional.empty(), credentials.authorizationFor(WITHOUT, URI.create(filled)), filled);
        }
        assertEquals("none", credentials.describe(WITHOUT, "http://fhhs:8080@{respondentId}.evil.example/"));
    }

    @Test
    void uc015_br010_withNoServiceTokenATrustedActionIsNotCalled() {
        PostSurveyActionCredentials credentials = credentials(null);

        ServiceToken.NotConfigured refused = assertThrows(ServiceToken.NotConfigured.class,
                () -> credentials.authorizationFor(WITHOUT, TRUSTED));
        assertEquals("service token not configured", refused.getMessage());
        assertEquals(Optional.of("Bearer issued-by-the-partner"), credentials.authorizationFor(WITH_OWN_TOKEN, TRUSTED),
                "an action with its own token does not need the service token");
        assertEquals(Optional.empty(), credentials.authorizationFor(WITHOUT, ELSEWHERE));
    }
}
