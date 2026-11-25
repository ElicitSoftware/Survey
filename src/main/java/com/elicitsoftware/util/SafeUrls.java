package com.elicitsoftware.util;

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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

/**
 * Decides which of the addresses a survey definition carries Survey will use: the post-survey
 * address a respondent's browser is sent to (UC-005 BR-017), a report service's address
 * (UC-005 BR-017) and a post-survey action's address (UC-015 BR-009). An address is used only
 * when it is absolute and its scheme is {@code http} or {@code https}.
 */
public final class SafeUrls {

    private SafeUrls() {
    }

    /**
     * The address as a URI when it is an absolute {@code http} or {@code https} address that
     * names a server, carries no user information and holds no whitespace or control character;
     * empty otherwise. Surrounding whitespace is ignored.
     *
     * @param value the address as the survey definition holds it; may be null
     * @return the address, or empty when Survey must not use it
     */
    public static Optional<URI> httpUrl(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String address = value.strip();
        if (!hasHttpScheme(address)) {
            return Optional.empty();
        }
        for (int i = 0; i < address.length(); i++) {
            char c = address.charAt(i);
            if (Character.isWhitespace(c) || Character.isISOControl(c)) {
                return Optional.empty();
            }
        }
        URI uri;
        try {
            uri = new URI(address);
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
        // The authority, not getHost(): java.net.URI reports no host for a name with an
        // underscore, which a container network's service names may have.
        String authority = uri.getRawAuthority();
        if (authority == null || authority.isEmpty() || authority.indexOf('@') >= 0) {
            return Optional.empty();
        }
        return Optional.of(uri);
    }

    /**
     * Whether the address starts with {@code http://} or {@code https://}, whatever the case of
     * the scheme. For an address that still holds placeholders and so cannot be parsed yet.
     *
     * @param value the address; may be null
     * @return true when the scheme is http or https
     */
    public static boolean hasHttpScheme(String value) {
        if (value == null) {
            return false;
        }
        String address = value.strip();
        return address.regionMatches(true, 0, "http://", 0, 7) || address.regionMatches(true, 0, "https://", 0, 8);
    }
}
