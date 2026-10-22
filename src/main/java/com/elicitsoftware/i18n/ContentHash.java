package com.elicitsoftware.i18n;

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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * The hash a content translation carries of the base text it was translated from
 * ({@code survey.translations.source_hash}, Survey V019; UC-009 BR-005).
 * <p>
 * Lower-case hex SHA-256 of the UTF-8 bytes of the exact stored string, with no trimming and no
 * normalization. Three modules compute it -- Author when it writes a translation, Survey when it
 * decides whether to serve one, and PostgreSQL in any SQL that wants the same answer
 * ({@code encode(sha256(convert_to(q.short_text, 'UTF8')), 'hex')}) -- so the rule has to be
 * simple enough to state once and implement identically. A shared test vector holds them together;
 * drift would silently either serve a stale translation or hide a good one.
 */
public final class ContentHash {

    private ContentHash() {
    }

    /**
     * The hash of one base string.
     *
     * @param text the base text exactly as stored; {@code null} yields {@code null}
     * @return lower-case hex SHA-256 of its UTF-8 bytes
     */
    public static String of(String text) {
        if (text == null) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // Every JVM ships SHA-256; if this one does not, nothing downstream can be trusted.
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    /**
     * Whether a translation made from {@code sourceHash} still matches the current base text.
     *
     * @param sourceHash the hash stored with the translation
     * @param baseText   the element's base text as it stands now
     * @return true when the translation is still faithful to the base text
     */
    public static boolean matches(String sourceHash, String baseText) {
        return sourceHash != null && sourceHash.equals(of(baseText));
    }
}
