package com.elicitsoftware.security;

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

import com.elicitsoftware.PostgresTestResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * NFR-018: Forwarded Headers Come From the Site's Proxy — with forwarding on and the shipped list
 * of trusted proxies, a proxy on loopback or a private network decides the address Survey believes
 * it is served at and the client address it logs.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
@TestProfile(TrustedProxyTest.Forwarding.class)
class TrustedProxyTest {

    /** Forwarding on, as in the image; the test profile turns it off for everything else. */
    public static class Forwarding implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("quarkus.http.proxy.proxy-address-forwarding", "true");
        }
    }

    @TestHTTPResource("/")
    URL root;

    /** Left empty, Quarkus trusts every client: the image ships loopback and the private ranges. */
    @Test
    void given_theShippedConfiguration_then_theTrustedProxiesAreLoopbackAndThePrivateRanges() {
        assertEquals("127.0.0.1,0:0:0:0:0:0:0:1,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16,fc00::/7",
                ConfigProvider.getConfig().getValue("quarkus.http.proxy.trusted-proxies", String.class));
    }

    @Test
    void given_aRequestFromATrustedProxy_when_itForwardsTheSiteAddress_then_surveyUsesIt() throws Exception {
        HttpResponse<String> response = ForwardedRequests.echo(root,
                "X-Forwarded-Proto", "https",
                "X-Forwarded-Host", "survey.example.org",
                "X-Forwarded-For", "198.51.100.20");

        assertEquals(200, response.statusCode());
        assertEquals("https://survey.example.org 198.51.100.20", response.body());
    }
}
