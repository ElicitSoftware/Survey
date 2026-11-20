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

import io.vertx.core.http.HttpServerRequest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;

/**
 * Test-only resource that answers with the address Survey believes it is served at and the
 * client address it would log, as {@code <scheme>://<host> <client>}. NFR-018's tests read it to
 * see whether a forwarding header was honored. It sits outside {@code /api}, so it needs no
 * service token.
 */
@Path("/test-only/forwarded")
public class ForwardedEchoResource {

    @Context
    UriInfo uriInfo;

    @Context
    HttpServerRequest request;

    /**
     * The scheme and host of the base URI, and the client address.
     *
     * @return {@code <scheme>://<host> <client>}
     */
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String echo() {
        var base = uriInfo.getBaseUri();
        return base.getScheme() + "://" + base.getHost() + " " + request.remoteAddress().hostAddress();
    }
}
