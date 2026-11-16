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

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import java.lang.reflect.Method;
import java.util.Set;

/**
 * Requires the platform service token on every REST path under {@code /api/} (UC-016), except
 * the paths of {@link #OPEN}. A path is judged twice, by the path the request asked for and by
 * the path the matched resource declares, and is protected when either is under {@code /api/};
 * it is open only when the matched resource declares a listed path. So no spelling of a request
 * path reaches a protected resource by looking like something else, and a resource added under
 * {@code /api/} later is protected without being named here (BR-001).
 * <p>
 * The filter runs before the body is read. It logs nothing: neither answer depends on anything
 * but the header, and the header is never to be written anywhere (BR-005).
 */
@Provider
@Priority(Priorities.AUTHENTICATION)
public class ServiceTokenFilter implements ContainerRequestFilter {

    /** The prefix every protected path has. */
    static final String PROTECTED_PREFIX = "/api/";

    /** UC-016 BR-001: the paths under the prefix a browser calls without the token. */
    static final Set<String> OPEN = Set.of("/api/pdf/download");

    static final String NOT_CONFIGURED_BODY = "{\"error\":\"" + ServiceToken.NOT_CONFIGURED + "\"}";

    private static final String BEARER = "Bearer ";

    @Inject
    ServiceToken serviceToken;

    @Context
    ResourceInfo resourceInfo;

    @Override
    public void filter(ContainerRequestContext request) {
        String declared = declaredPath(resourceInfo.getResourceClass(), resourceInfo.getResourceMethod());
        if (!isProtected(request.getUriInfo().getPath(), declared)) {
            return;
        }
        Response refusal = refusal(serviceToken, request.getHeaderString(HttpHeaders.AUTHORIZATION));
        if (refusal != null) {
            request.abortWith(refusal);
        }
    }

    /**
     * UC-016 BR-001.
     *
     * @param requested the path of the request
     * @param declared  the path the matched resource method declares
     */
    static boolean isProtected(String requested, String declared) {
        if (OPEN.contains(declared)) {
            return false;
        }
        return underPrefix(declared) || underPrefix(requested);
    }

    private static boolean underPrefix(String path) {
        String normalized = normalize(path);
        return (normalized + "/").regionMatches(true, 0, PROTECTED_PREFIX, 0, PROTECTED_PREFIX.length());
    }

    /**
     * UC-016 A1, A2: the answer for a request that may not proceed, or null for one that may.
     *
     * @param authorization the request's {@code Authorization} header; may be null
     */
    static Response refusal(ServiceToken serviceToken, String authorization) {
        if (!serviceToken.configured()) {
            return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                    .type(MediaType.APPLICATION_JSON)
                    .entity(NOT_CONFIGURED_BODY)
                    .build();
        }
        if (authorization != null && authorization.regionMatches(true, 0, BEARER, 0, BEARER.length())
                && serviceToken.matches(authorization.substring(BEARER.length()))) {
            return null;
        }
        // Missing, another scheme and a wrong token are one answer (A1).
        return Response.status(Response.Status.UNAUTHORIZED)
                .header(HttpHeaders.WWW_AUTHENTICATE, "Bearer")
                .build();
    }

    /** The class's {@code @Path} followed by the method's, with one leading slash and none trailing. */
    static String declaredPath(Class<?> resource, Method method) {
        StringBuilder path = new StringBuilder();
        if (resource != null && resource.getAnnotation(Path.class) != null) {
            path.append('/').append(resource.getAnnotation(Path.class).value());
        }
        if (method != null && method.getAnnotation(Path.class) != null) {
            path.append('/').append(method.getAnnotation(Path.class).value());
        }
        return normalize(path.toString());
    }

    private static String normalize(String path) {
        String normalized = ("/" + (path == null ? "" : path)).replaceAll("/{2,}", "/");
        return normalized.length() > 1 && normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }
}
