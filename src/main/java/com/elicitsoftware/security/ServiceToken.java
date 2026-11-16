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

import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * The platform service token (UC-016): the one credential the modules of a deployment present to
 * each other, and the origins Survey sends it to. It is configuration only,
 * {@code elicit.service.token} and {@code elicit.service.trusted-origins}; Survey never generates
 * or stores it.
 * <p>
 * The value never leaves this class except inside the {@code Authorization} header
 * {@link #authorizationFor(URI)} hands to a call: there is no getter and no {@code toString}, and
 * no message built here contains it (BR-005).
 */
@ApplicationScoped
public class ServiceToken {

    /** The environment variable a deployment supplies the token in. */
    public static final String VARIABLE = "ELICIT_SERVICE_TOKEN";

    /** The environment variable a deployment lists the trusted origins in. */
    public static final String ORIGINS_VARIABLE = "ELICIT_SERVICE_TRUSTED_ORIGINS";

    /** UC-016 BR-004: the fewest characters a configured token may have. */
    public static final int MINIMUM_LENGTH = 32;

    /** UC-016 A5: the value the development stack and the dev profile use; never a real site's. */
    public static final String DEVELOPMENT_TOKEN = "elicit-dev-service-token-not-for-a-real-site";

    /** What a refused request and an unmade call say when no token is configured (UC-016 A2). */
    public static final String NOT_CONFIGURED = "service token not configured";

    /** The readiness reason for an instance with no token (UC-016 A2). */
    public static final String NOT_SET = VARIABLE + " is not set";

    /**
     * A call that would carry the token, on an instance that has none (UC-016 BR-007). The call
     * is not made; the message is {@link #NOT_CONFIGURED}.
     */
    public static class NotConfigured extends IllegalStateException {
        public NotConfigured() {
            super(NOT_CONFIGURED);
        }
    }

    /**
     * Scheme, host and effective port: what a destination is compared by (UC-016 BR-007).
     *
     * @param scheme {@code http} or {@code https}
     * @param host   the host, in lower case
     * @param port   the port, 80 or 443 when the address names none
     */
    public record Origin(String scheme, String host, int port) {

        /**
         * The origin of an address about to be called, or empty for an address that is never
         * trusted: one with user information, no host, or a scheme other than http and https.
         * Nothing is resolved and nothing is normalized beyond case and the default port.
         */
        public static Optional<Origin> of(URI address) {
            if (address == null || address.getScheme() == null || address.getRawUserInfo() != null) {
                return Optional.empty();
            }
            String scheme = address.getScheme().toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https")) {
                return Optional.empty();
            }
            // getHost() is null for an authority java.net.URI cannot read as host[:port], such as
            // "fhhs:8080.example": an address this parser cannot place is never trusted.
            String host = address.getHost();
            String authority = address.getRawAuthority();
            if (host == null || host.isEmpty() || authority == null || authority.indexOf('@') >= 0) {
                return Optional.empty();
            }
            int port = address.getPort() >= 0 ? address.getPort() : scheme.equals("https") ? 443 : 80;
            return Optional.of(new Origin(scheme, host.toLowerCase(Locale.ROOT), port));
        }

        /**
         * One entry of {@code elicit.service.trusted-origins}, read with the same parser.
         *
         * @throws IllegalStateException the entry is not an origin: no http(s) scheme, no host, or
         *                               user information, a path, a query or a fragment
         */
        static Origin entry(String entry) {
            URI uri;
            try {
                uri = new URI(entry);
            } catch (URISyntaxException e) {
                throw invalid(entry, "it is not an address");
            }
            if (uri.getRawUserInfo() != null || (uri.getRawAuthority() != null && uri.getRawAuthority().indexOf('@') >= 0)) {
                throw invalid(entry, "it carries user information");
            }
            if (uri.getRawPath() != null && !uri.getRawPath().isEmpty()) {
                throw invalid(entry, "it carries a path");
            }
            if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
                throw invalid(entry, "it carries a query or a fragment");
            }
            return of(uri).orElseThrow(() -> invalid(entry, "it needs an http or https scheme and a host"));
        }

        private static IllegalStateException invalid(String entry, String why) {
            return new IllegalStateException(ORIGINS_VARIABLE + " (elicit.service.trusted-origins) entry '" + entry
                    + "' is not an origin: " + why + ". Write scheme://host[:port], for example http://fhhs.internal:8080");
        }
    }

    @ConfigProperty(name = "elicit.service.token")
    Optional<String> configuredToken;

    @ConfigProperty(name = "elicit.service.trusted-origins")
    Optional<String> configuredOrigins;

    /** SHA-256 of the configured token, or null when none is configured. */
    private byte[] digest;

    /** The header value for a call that carries the token, or null when none is configured. */
    private String authorization;

    private boolean developmentToken;

    private Set<Origin> trustedOrigins = Set.of();

    public ServiceToken() {
        // CDI managed bean
    }

    /**
     * A token outside CDI, for a plain unit test.
     *
     * @param token          the token, or null or blank for none
     * @param trustedOrigins the comma-separated origins, or null or blank for none
     * @throws IllegalStateException as {@link #init()}
     */
    public static ServiceToken of(String token, String trustedOrigins) {
        ServiceToken serviceToken = new ServiceToken();
        serviceToken.configuredToken = Optional.ofNullable(token);
        serviceToken.configuredOrigins = Optional.ofNullable(trustedOrigins);
        serviceToken.init();
        return serviceToken;
    }

    /**
     * Reads and checks the configuration (UC-016 A3, A4).
     *
     * @throws IllegalStateException a token shorter than {@link #MINIMUM_LENGTH}, or a trusted
     *                               origin that is not one; the application does not start
     */
    @PostConstruct
    void init() {
        String token = configuredToken.map(String::strip).filter(v -> !v.isEmpty()).orElse(null);
        if (token != null && token.length() < MINIMUM_LENGTH) {
            // Neither the value nor its length: either would help someone guess it.
            throw new IllegalStateException(VARIABLE + " (elicit.service.token) is too short: it must be at least "
                    + MINIMUM_LENGTH + " characters. Generate one with: openssl rand -hex 32");
        }
        Set<Origin> origins = new LinkedHashSet<>();
        for (String entry : configuredOrigins.orElse("").split(",")) {
            if (!entry.isBlank()) {
                origins.add(Origin.entry(entry.strip()));
            }
        }
        trustedOrigins = Set.copyOf(origins);
        if (token != null) {
            digest = sha256(token);
            authorization = "Bearer " + token;
            developmentToken = token.equals(DEVELOPMENT_TOKEN);
        }
    }

    /** UC-016 A2, A3, A4, A5: the configuration is checked at startup, and its state is logged once. */
    void onStart(@Observes StartupEvent event) {
        if (!configured()) {
            Log.warn(VARIABLE + " is not set: every path under /api/ except the PDF download answers 503, this"
                    + " instance reports not ready, and no call that needs the service token is made");
        } else if (developmentToken()) {
            Log.warn(VARIABLE + " is the well-known development value; a real site must replace it"
                    + " (openssl rand -hex 32)");
        }
    }

    /** Whether a token is configured. */
    public boolean configured() {
        return digest != null;
    }

    /** UC-016 A5: whether the configured token is the well-known development value. */
    public boolean developmentToken() {
        return developmentToken;
    }

    /** The trusted origins, for the diagnostics report. */
    public Set<Origin> trustedOrigins() {
        return trustedOrigins;
    }

    /**
     * UC-016 BR-004: whether a presented token is the configured one, compared in constant time
     * over SHA-256 digests so that neither the first differing character nor the length shows.
     *
     * @param presented the token a caller sent; may be null
     * @return false when it differs, is null, or no token is configured
     */
    public boolean matches(String presented) {
        if (digest == null || presented == null) {
            return false;
        }
        return MessageDigest.isEqual(digest, sha256(presented));
    }

    /** UC-016 BR-007: whether the address's origin equals a trusted one. */
    public boolean trusts(URI address) {
        return Origin.of(address).map(trustedOrigins::contains).orElse(false);
    }

    /**
     * UC-016 BR-007: the {@code Authorization} header for a call to this address, which must be
     * the final address, after any placeholder is filled. Present only when the address's origin
     * is trusted.
     *
     * @param address the address about to be called
     * @return the header value, or empty when the call carries no service token
     * @throws NotConfigured the origin is trusted and this instance has no token; the call must
     *                       not be made
     */
    public Optional<String> authorizationFor(URI address) {
        if (!trusts(address)) {
            return Optional.empty();
        }
        if (authorization == null) {
            throw new NotConfigured();
        }
        return Optional.of(authorization);
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
