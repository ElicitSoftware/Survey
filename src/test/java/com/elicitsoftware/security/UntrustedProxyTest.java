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
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * NFR-018: Forwarded Headers Come From the Site's Proxy — a client that is not in the list of
 * trusted proxies cannot choose the address Survey believes it is served at, nor the client
 * address it logs, by sending forwarding headers of its own.
 */
@QuarkusTest
@QuarkusTestResource(PostgresTestResource.class)
@TestProfile(UntrustedProxyTest.ProxyElsewhere.class)
class UntrustedProxyTest {

    /**
     * Forwarding on, trusting only a documentation address (RFC 5737) as the proxy, so the test
     * client, which connects from loopback, is a client the headers must not be taken from.
     */
    public static class ProxyElsewhere implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.http.proxy.proxy-address-forwarding", "true",
                    "quarkus.http.proxy.trusted-proxies", "203.0.113.7");
        }
    }

    @TestHTTPResource("/")
    URL root;

    @Test
    void given_aClientOutsideTheTrustedList_when_itSendsForwardingHeaders_then_theyAreIgnored() throws Exception {
        HttpResponse<String> response = ForwardedRequests.echo(root,
                "X-Forwarded-Proto", "https",
                "X-Forwarded-Host", "attacker.example",
                "X-Forwarded-For", "198.51.100.20",
                "Forwarded", "for=198.51.100.21;host=attacker.example;proto=https");

        assertEquals(200, response.statusCode());
        String body = response.body();
        assertFalse(body.contains("attacker.example"), body);
        assertFalse(body.contains("198.51.100.2"), body);
        assertFalse(body.startsWith("https://"), body);
    }
}
