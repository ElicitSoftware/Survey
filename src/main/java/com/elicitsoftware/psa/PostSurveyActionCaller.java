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

import com.elicitsoftware.model.PostSurveyAction;
import jakarta.json.Json;
import jakarta.json.JsonObjectBuilder;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

/**
 * Makes one post-survey action call (UC-015 steps 3-5): the action's method, its address with the
 * placeholders filled, the JSON body for a method that carries one, and the site's credential.
 * It holds no state and touches no database; {@link PostSurveyActionDelivery} records the outcome.
 */
public class PostSurveyActionCaller {

    /** How long a post-survey action service may take to accept the connection (UC-015 BR-008). */
    public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);

    /**
     * How long a post-survey action may take to answer (UC-015 BR-008). The first call runs at
     * finalize, on the respondent's request, so a service that never answers must not hold it
     * forever; FHHS's generate action renders a pedigree and uploads over SFTP, hence the bound.
     */
    public static final Duration TIMEOUT = Duration.ofSeconds(60);

    /** UC-015 BR-001: the placeholder for the respondent's id. */
    public static final String RESPONDENT_ID = "{respondentId}";

    /** UC-015 BR-001: the placeholder for the subject's External ID, on an action that sends it. */
    public static final String EXTERNAL_ID = "{externalId}";

    // Package-private so PostSurveyActionCallerTest can shorten it.
    Duration timeout = TIMEOUT;

    /**
     * Who the call is about, beyond the respondent's id: the subject's External ID and the
     * department's code, read only for an action that sends them (UC-015 BR-001).
     */
    public record Identity(String externalId, String departmentCode) {
        public static final Identity NONE = new Identity(null, null);
    }

    /**
     * A call refused before it is made (UC-015 A2): nothing a retry does can change the outcome,
     * so the delivery is recorded as refused and never retried.
     */
    public static class CallRefused extends Exception {
        public CallRefused(String message) {
            super(message);
        }
    }

    /** The call as it was made, for a test to inspect. */
    public record Built(String method, URI uri, String body) {
    }

    /** Calls an action with no External ID and no credential: today's call, kept for the tests. */
    public String call(PostSurveyAction psa, int respondentId) throws Exception {
        return call(psa, respondentId, Identity.NONE, Optional.empty());
    }

    /**
     * Makes the call and returns the answer's body (which nothing reads beyond the error message,
     * UC-015 BR-007).
     *
     * @param authorization the value of the {@code Authorization} header, if the site configured a
     *                      credential for the action (UC-015 BR-003)
     * @throws CallRefused the call was refused before it was made (UC-015 A2)
     * @throws Exception   the call failed (UC-015 A1); the message says how
     */
    public String call(PostSurveyAction psa, int respondentId, Identity identity, Optional<String> authorization) throws Exception {
        if (psa.url == null || psa.url.trim().isEmpty()) {
            throw new CallRefused(prefix(psa) + "URL is null or empty - please check the action configuration");
        }
        if (respondentId <= 0) {
            throw new CallRefused(prefix(psa) + "Invalid respondent ID (" + respondentId + ") - ID must be a positive number");
        }
        Built built = build(psa, respondentId, identity);
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
            HttpRequest.Builder request = HttpRequest.newBuilder().uri(built.uri()).timeout(timeout);
            authorization.ifPresent(value -> request.header("Authorization", value));
            if (built.body() == null) {
                request.method(built.method(), HttpRequest.BodyPublishers.noBody());
            } else {
                request.header("Content-Type", "application/json")
                        .method(built.method(), HttpRequest.BodyPublishers.ofString(built.body()));
            }
            HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return response.body();
            }
            throw new Exception(errorMessage(psa, respondentId, response.statusCode(), response.body()));
        } catch (HttpTimeoutException e) {
            throw new Exception(prefix(psa) + "No answer from " + psa.url + " within " + timeout.toSeconds() + " seconds", e);
        } catch (java.io.IOException e) {
            throw new Exception(prefix(psa) + "Network communication failed when calling " + psa.url + " - " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Exception(prefix(psa) + "Request was interrupted - " + e.getMessage(), e);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().startsWith("Post Survey Action")) {
                throw e;
            }
            throw new Exception(prefix(psa) + "Unexpected error - " + e.getMessage(), e);
        }
    }

    /**
     * UC-015 BR-001, BR-002: the method, the address with its placeholders filled and the body.
     *
     * @throws CallRefused an External-ID action at an address that is not https, an address that
     *                     needs an External ID the subject does not have, or a placeholder the
     *                     action may not use
     */
    public Built build(PostSurveyAction psa, int respondentId, Identity identity) throws CallRefused {
        String method = refuseUnsendable(psa);
        String url = psa.url.trim();
        url = url.replace(RESPONDENT_ID, encode(String.valueOf(respondentId)));
        if (url.contains(EXTERNAL_ID)) {
            if (!psa.sendsExternalId) {
                throw new CallRefused(prefix(psa) + "Refused: the address uses " + EXTERNAL_ID + " but the action does not send the External ID");
            }
            if (identity.externalId() == null || identity.externalId().isBlank()) {
                throw new CallRefused(prefix(psa) + "Refused: the address needs the External ID and this respondent's subject has none");
            }
            url = url.replace(EXTERNAL_ID, encode(identity.externalId()));
        }
        if (url.indexOf('{') >= 0 || url.indexOf('}') >= 0) {
            throw new CallRefused(prefix(psa) + "Refused: the address holds a placeholder other than " + RESPONDENT_ID + " and " + EXTERNAL_ID);
        }
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw new CallRefused(prefix(psa) + "Invalid URL format '" + url + "' - " + e.getMessage());
        }
        String body = null;
        if (!method.equals("GET")) {
            JsonObjectBuilder json = Json.createObjectBuilder().add("id", respondentId);
            if (psa.sendsExternalId) {
                addOrNull(json, "externalId", identity.externalId());
                addOrNull(json, "departmentCode", identity.departmentCode());
            }
            body = json.build().toString();
        }
        return new Built(method, uri, body);
    }

    /**
     * UC-015 A2: the refusals that need nothing but the action itself, an unsupported method or an
     * External-ID action whose address is not https. Checked before anything is looked up for the
     * call, so a refusal is never mistaken for a failure. Returns the method to call with.
     */
    public String refuseUnsendable(PostSurveyAction psa) throws CallRefused {
        String method = psa.method == null || psa.method.isBlank() ? "POST" : psa.method.trim().toUpperCase();
        if (!method.equals("POST") && !method.equals("GET") && !method.equals("PUT")) {
            throw new CallRefused(prefix(psa) + "Unsupported method " + method + " - only POST, GET and PUT are called");
        }
        if (psa.sendsExternalId && (psa.url == null || !psa.url.trim().regionMatches(true, 0, "https://", 0, 8))) {
            throw new CallRefused(prefix(psa) + "Refused: the action sends the External ID, so its address must be https");
        }
        return method;
    }

    private static void addOrNull(JsonObjectBuilder json, String name, String value) {
        if (value == null) {
            json.addNull(name);
        } else {
            json.add(name, value);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String prefix(PostSurveyAction psa) {
        return "Post Survey Action '" + psa.name + "' Error: ";
    }

    /** The error recorded for a non-2xx answer (UC-015 A1, BR-007). */
    private static String errorMessage(PostSurveyAction psa, int respondentId, int status, String body) {
        if (body != null && !body.trim().isEmpty()) {
            if (status == 403 || body.toLowerCase().contains("license")) {
                String message = prefix(psa) + "License validation failed - " + body;
                if (!message.toLowerCase().contains("premm5") && psa.url.toLowerCase().contains("premm5")) {
                    message += " - Please ensure your PREMM5 license is valid and properly configured.";
                }
                return message;
            }
            return prefix(psa) + "HTTP " + status + " - " + body;
        }
        return switch (status) {
            case 400 -> prefix(psa) + "Bad Request (400) - Invalid request data for respondent ID " + respondentId;
            case 401 -> prefix(psa) + "Unauthorized (401) - Authentication required";
            case 403 -> prefix(psa) + "Forbidden (403) - License validation may have failed. Please check your license configuration.";
            case 404 -> prefix(psa) + "Not Found (404) - Service endpoint not available at " + psa.url;
            case 500 -> prefix(psa) + "Internal Server Error (500) - The service encountered an internal error";
            case 502 -> prefix(psa) + "Bad Gateway (502) - Service is temporarily unavailable";
            case 503 -> prefix(psa) + "Service Unavailable (503) - Service is temporarily down for maintenance";
            default -> prefix(psa) + "HTTP " + status + " - Service returned an error status";
        };
    }
}
