package com.elicitsoftware.report;

/*-
 * ***LICENSE_START***
 * Elicit Survey
 * %%
 * Copyright (C) 2025 The Regents of the University of Michigan - Rogel Cancer Center
 * %%
 * PolyForm Noncommercial License 1.0.0
 * <https://polyformproject.org/licenses/noncommercial/1.0.0>
 * ***LICENSE_END***
 */

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UC-005: View Reports & Download PDF — covers PDFDownloadResource, the unauthenticated
 * (BR-013) short-lived server-side PDF cache backing "opens it in a new browser tab" (main
 * success scenario step 4) and the 10-minute reuse window (A3: "Respondent downloads the
 * PDF more than once"). PDFDownloadResource is a plain JAX-RS resource with no UI-scoped
 * dependency and no {@code @Context}-injected parameters, so its {@code downloadPDF(String)}
 * method is called directly here rather than through a real HTTP round-trip. The cache map
 * and its entry timestamp are private implementation details; reflection is used only to
 * fabricate an already-expired entry (there is no seam to shorten the real 10-minute TTL),
 * mirroring how {@code QuestionManagerTest} reaches otherwise-inaccessible logic without
 * changing production visibility.
 */
class PDFDownloadResourceTest {

    private final PDFDownloadResource resource = new PDFDownloadResource();

    // ── BR-013 / A3: missing or unknown key ──────────────────────────────────

    @Test
    // UC-005 BR-013: the unauthenticated endpoint still validates its one input -- a missing
    // key parameter is rejected with 400 rather than a NullPointerException.
    void given_nullKey_when_downloadPDF_then_400BadRequest() {
        Response response = resource.downloadPDF(null);

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    }

    @Test
    // UC-005 BR-013: an empty-string key is likewise rejected with 400.
    void given_emptyKey_when_downloadPDF_then_400BadRequest() {
        Response response = resource.downloadPDF("");

        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
    }

    @Test
    // UC-005 BR-013: an unguessable-but-nonexistent key (never cached, or already expired
    // and swept) returns 404 rather than leaking any information about valid keys.
    void given_unknownKey_when_downloadPDF_then_404NotFound() {
        Response response = resource.downloadPDF("pdf_does_not_exist_" + UUID.randomUUID());

        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
    }

    // ── Main success scenario step 4: cache + retrieve ───────────────────────

    @Test
    // UC-005 step 4: a PDF cached via cachePDF() is retrievable by its returned key, with
    // the expected content type/disposition/no-cache headers and the exact original bytes.
    void given_cachedPdf_when_downloadPDF_then_200WithBytesAndPdfHeaders() {
        byte[] originalBytes = {'%', 'P', 'D', 'F', '-', '1', '.', '4'};
        String key = PDFDownloadResource.cachePDF(originalBytes);

        Response response = resource.downloadPDF(key);

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertArrayEquals(originalBytes, (byte[]) response.getEntity(),
                "Downloaded PDF bytes must be identical to what was cached");
        assertEquals("application/pdf", response.getHeaderString("Content-Type"));
        assertTrue(response.getHeaderString("Content-Disposition").contains("family_history_report.pdf"));
        assertEquals("no-cache, no-store, must-revalidate", response.getHeaderString("Cache-Control"));
    }

    @Test
    // UC-005 A3: retries/re-opens of the same PDF link within the cache window must keep
    // succeeding -- the entry is not removed after the first successful download.
    void given_cachedPdf_when_downloadedTwice_then_bothCallsSucceed() {
        byte[] originalBytes = {'%', 'P', 'D', 'F', '-', 'r', 'e', 't', 'r', 'y'};
        String key = PDFDownloadResource.cachePDF(originalBytes);

        Response first = resource.downloadPDF(key);
        Response second = resource.downloadPDF(key);

        assertEquals(Response.Status.OK.getStatusCode(), first.getStatus());
        assertEquals(Response.Status.OK.getStatusCode(), second.getStatus());
        assertArrayEquals(originalBytes, (byte[]) second.getEntity(),
                "A3: the second (repeat) download must still return the same cached bytes");
    }

    @Test
    // UC-005 A3 (BR-013 unguessability aside): each cachePDF() call mints a distinct key
    // even for identical content, since the key encodes wall-clock time + nanoTime, not a
    // hash of the content -- two respondents downloading the same bytes get unlinkable keys.
    void given_twoCachePdfCalls_when_comparingKeys_then_keysAreDistinct() {
        byte[] bytes = {1, 2, 3};
        String key1 = PDFDownloadResource.cachePDF(bytes);
        String key2 = PDFDownloadResource.cachePDF(bytes);

        assertNotEquals(key1, key2, "Every cachePDF() call must mint a unique cache key");
    }

    // ── A3 (negative direction): expiry ───────────────────────────────────────

    @Test
    // UC-005 A3 boundary: once an entry's age exceeds the 10-minute TTL, it must be treated
    // as gone (404) rather than served, and the sweep on a failed lookup removes it from the
    // cache map so it cannot be found again. There is no production seam to shorten the real
    // 10-minute TTL, so this test uses reflection only to backdate one entry's timestamp --
    // it exercises the same isExpired()/removal code path a real 10-minutes-later request
    // would hit, not different logic.
    void given_entryOlderThanTenMinutes_when_downloadPDF_then_404AndEntryRemovedFromCache() throws Exception {
        byte[] bytes = {9, 9, 9};
        String key = PDFDownloadResource.cachePDF(bytes);

        backdateEntryPastExpiry(key);

        Response response = resource.downloadPDF(key);
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());

        // A second lookup must still be 404 (not e.g. an NPE from a half-removed entry).
        Response secondLookup = resource.downloadPDF(key);
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), secondLookup.getStatus());
    }

    /** Reflectively backdates the timestamp of a cached entry past PDFDownloadResource's private 10-minute TTL. */
    @SuppressWarnings("unchecked")
    private void backdateEntryPastExpiry(String key) throws Exception {
        Field cacheField = PDFDownloadResource.class.getDeclaredField("PDF_CACHE");
        cacheField.setAccessible(true);
        Map<String, Object> cache = (Map<String, Object>) cacheField.get(null);

        Object entry = cache.get(key);
        assertNotNull(entry, "Precondition: the entry must exist in the cache before backdating it");

        Field timestampField = entry.getClass().getDeclaredField("timestamp");
        timestampField.setAccessible(true);
        // 11 minutes in the past -- one minute past the 10-minute TTL.
        timestampField.set(entry, System.currentTimeMillis() - (11 * 60 * 1000));
    }
}
