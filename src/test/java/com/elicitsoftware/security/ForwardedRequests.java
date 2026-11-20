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

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Sends a request with forwarding headers to {@link ForwardedEchoResource} (NFR-018). */
final class ForwardedRequests {

    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private ForwardedRequests() {
    }

    /**
     * Calls the echo resource with the given header name and value pairs.
     *
     * @param root    the application's root URL
     * @param headers header names and values, alternating
     * @return the response
     */
    static HttpResponse<String> echo(URL root, String... headers) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(
                URI.create(root.toString().replaceAll("/$", "") + "/test-only/forwarded"))
                .timeout(Duration.ofSeconds(30))
                .GET();
        for (int i = 0; i < headers.length; i += 2) {
            request.header(headers[i], headers[i + 1]);
        }
        return CLIENT.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
