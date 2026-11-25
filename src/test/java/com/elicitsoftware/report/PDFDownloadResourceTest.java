package com.elicitsoftware.report;

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
        assertEquals("inline; filename=\"report.pdf\"", response.getHeaderString("Content-Disposition"));
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

    // ── BR-018: the cache is bounded ─────────────────────────────────────────

    @Test
    // UC-005 BR-018: generating again replaces the previous PDF; its link answers as expired.
    void given_replacedKey_when_downloadPDF_then_404AndTheNewKeyDownloads() {
        String first = PDFDownloadResource.cachePDF(new byte[]{1});
        String second = PDFDownloadResource.cachePDF(new byte[]{2}, first);

        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), resource.downloadPDF(first).getStatus());
        assertEquals(Response.Status.OK.getStatusCode(), resource.downloadPDF(second).getStatus());
    }

    @Test
    // UC-005 BR-018: nothing to replace is the same as the one-argument call.
    void given_noReplacedKey_when_cachePDF_then_nothingElseIsRemoved() {
        String other = PDFDownloadResource.cachePDF(new byte[]{1});
        String key = PDFDownloadResource.cachePDF(new byte[]{2}, null);

        assertEquals(Response.Status.OK.getStatusCode(), resource.downloadPDF(other).getStatus());
        assertEquals(Response.Status.OK.getStatusCode(), resource.downloadPDF(key).getStatus());
    }

    @Test
    // UC-005 BR-018: an expired PDF is removed by the sweep, without anyone asking for it again.
    void given_expiredEntry_when_sweep_then_removedWithoutBeingRequested() throws Exception {
        String expired = PDFDownloadResource.cachePDF(new byte[]{7, 7});
        String fresh = PDFDownloadResource.cachePDF(new byte[]{8});
        backdateEntryPastExpiry(expired);
        int before = PDFDownloadResource.cachedEntries();

        int removed = PDFDownloadResource.sweep();

        assertTrue(removed >= 1, "the expired entry is removed");
        assertEquals(before - removed, PDFDownloadResource.cachedEntries());
        assertFalse(cache().containsKey(expired));
        assertTrue(cache().containsKey(fresh), "a PDF inside its window stays");
        assertEquals(0, PDFDownloadResource.sweep(), "nothing left to remove");
    }

    @Test
    // UC-005 BR-018: storing a PDF also removes the expired ones.
    void given_expiredEntry_when_anotherPdfIsCached_then_theExpiredOneIsRemoved() throws Exception {
        String expired = PDFDownloadResource.cachePDF(new byte[]{7});
        backdateEntryPastExpiry(expired);

        PDFDownloadResource.cachePDF(new byte[]{8});

        assertFalse(cache().containsKey(expired));
    }

    @Test
    // UC-005 BR-018: past the ceiling on entries, the oldest PDFs go first and the newest stays.
    void given_moreEntriesThanTheCeiling_when_cachePDF_then_oldestAreDiscardedFirst() {
        cache().clear();
        String a = PDFDownloadResource.cachePDF(new byte[]{1}, null, 3, Long.MAX_VALUE);
        String b = PDFDownloadResource.cachePDF(new byte[]{2}, null, 3, Long.MAX_VALUE);
        String c = PDFDownloadResource.cachePDF(new byte[]{3}, null, 3, Long.MAX_VALUE);
        assertEquals(3, PDFDownloadResource.cachedEntries());

        String d = PDFDownloadResource.cachePDF(new byte[]{4}, null, 3, Long.MAX_VALUE);

        assertEquals(3, PDFDownloadResource.cachedEntries());
        assertFalse(cache().containsKey(a), "the oldest is discarded");
        assertTrue(cache().containsKey(b));
        assertTrue(cache().containsKey(c));
        assertTrue(cache().containsKey(d), "the newest always stays");
    }

    @Test
    // UC-005 BR-018: past the ceiling on bytes, the oldest PDFs go first; a single PDF larger
    // than the ceiling is still kept, because its respondent is about to open it.
    void given_moreBytesThanTheCeiling_when_cachePDF_then_oldestAreDiscardedFirst() {
        cache().clear();
        String a = PDFDownloadResource.cachePDF(new byte[40], null, 100, 100);
        String b = PDFDownloadResource.cachePDF(new byte[40], null, 100, 100);
        assertEquals(80, PDFDownloadResource.cachedBytes());

        String c = PDFDownloadResource.cachePDF(new byte[40], null, 100, 100);

        assertFalse(cache().containsKey(a));
        assertTrue(cache().containsKey(b));
        assertTrue(cache().containsKey(c));
        assertEquals(80, PDFDownloadResource.cachedBytes());

        String large = PDFDownloadResource.cachePDF(new byte[500], null, 100, 100);

        assertEquals(1, PDFDownloadResource.cachedEntries());
        assertTrue(cache().containsKey(large));
    }

    @Test
    // UC-005 BR-018: the ceilings the instance runs with.
    void theCeilingsAreFixed() {
        assertEquals(200, PDFDownloadResource.MAX_ENTRIES);
        assertEquals(256L * 1024 * 1024, PDFDownloadResource.MAX_BYTES);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cache() {
        try {
            Field cacheField = PDFDownloadResource.class.getDeclaredField("PDF_CACHE");
            cacheField.setAccessible(true);
            return (Map<String, Object>) cacheField.get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
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

    /** UC-005 BR-022: the PDF is offered under the survey's name. */
    @Test
    void given_aPdfOfASurvey_when_downloaded_then_itIsNamedAfterTheSurvey() {
        String key = PDFDownloadResource.cachePDF(new byte[]{'%', 'P'}, null, "Family History Survey");

        assertEquals("inline; filename=\"family-history-survey-report.pdf\"",
                resource.downloadPDF(key).getHeaderString("Content-Disposition"));
    }

    /** UC-005 BR-022: only a-z and digits are kept, as one hyphen per run of anything else. */
    @Test
    void given_surveyNames_when_namingThePdf_then_onlyLettersAndDigitsAreKept() {
        assertEquals("family-history-survey-report.pdf", PDFDownloadResource.fileName("Family History Survey"));
        assertEquals("premm5-v2-report.pdf", PDFDownloadResource.fileName("  PREMM5 (v2) "));
        assertEquals("caf-r-sum-report.pdf", PDFDownloadResource.fileName("Café \"résumé\""));
        assertEquals("report.pdf", PDFDownloadResource.fileName("استبيان"));
        assertEquals("report.pdf", PDFDownloadResource.fileName(""));
        assertEquals("report.pdf", PDFDownloadResource.fileName(null));
    }
}
