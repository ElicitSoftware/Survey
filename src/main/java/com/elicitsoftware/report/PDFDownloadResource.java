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

import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import com.elicitsoftware.RandomStringGenerator;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import com.elicitsoftware.i18n.Translations;
import java.util.Locale;

/**
 * REST resource for handling PDF downloads.
 * This endpoint provides PDF download functionality without using deprecated StreamResource.
 * It retrieves PDF data from an in-memory cache and streams it directly to the client.
 * <p>
 * The cache is bounded (NFR-003, UC-005 BR-018): a PDF is kept for ten minutes, generating
 * again replaces the page's previous PDF, expired PDFs are removed on a schedule
 * ({@link PDFCacheSweeper}) as well as when asked for, and the instance holds at most
 * {@link #MAX_ENTRIES} PDFs and {@link #MAX_BYTES} bytes, discarding the oldest first.
 */
@Path("/api/pdf/download")
public class PDFDownloadResource {
    
    // Thread-safe cache for PDF data with timestamps
    private static final ConcurrentHashMap<String, PDFCacheEntry> PDF_CACHE = new ConcurrentHashMap<>();
    private static final long CACHE_EXPIRY_MS = 10 * 60 * 1000; // 10 minutes

    /** The most PDFs the instance keeps at once (UC-005 BR-018). */
    static final int MAX_ENTRIES = 200;

    /** The most bytes of PDF the instance keeps at once (UC-005 BR-018). */
    static final long MAX_BYTES = 256L * 1024 * 1024;

    /** Orders entries cached in the same millisecond. */
    private static final AtomicLong SEQUENCE = new AtomicLong();

    /**
     * Inner class to store PDF content with timestamp for expiry
     */
    private static class PDFCacheEntry {
        byte[] content;
        long timestamp;
        final long sequence = SEQUENCE.incrementAndGet();

        PDFCacheEntry(byte[] content) {
            this.content = content;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_EXPIRY_MS;
        }
    }

    /**
     * Store PDF content in the cache
     */
    public static String cachePDF(byte[] pdfContent) {
        return cachePDF(pdfContent, null);
    }

    /**
     * Stores a PDF and discards the one it replaces (UC-005 BR-018).
     *
     * @param pdfContent  the PDF
     * @param replacesKey the key of the PDF this one supersedes, or null when there is none
     * @return the key the new PDF is downloaded with
     */
    public static String cachePDF(byte[] pdfContent, String replacesKey) {
        return cachePDF(pdfContent, replacesKey, MAX_ENTRIES, MAX_BYTES);
    }

    /** As {@link #cachePDF(byte[], String)}, with the ceilings given so a test can reach them. */
    static synchronized String cachePDF(byte[] pdfContent, String replacesKey, int maxEntries, long maxBytes) {
        if (replacesKey != null) {
            PDF_CACHE.remove(replacesKey);
        }
        sweep();
        String key = "pdf_" + new RandomStringGenerator(32).nextString();
        PDF_CACHE.put(key, new PDFCacheEntry(pdfContent));
        // Oldest first, and never the PDF just stored: its respondent is about to open it.
        while (PDF_CACHE.size() > maxEntries || (PDF_CACHE.size() > 1 && cachedBytes() > maxBytes)) {
            String oldest = PDF_CACHE.entrySet().stream()
                    .filter(e -> !e.getKey().equals(key))
                    .min(Comparator.comparingLong((Map.Entry<String, PDFCacheEntry> e) -> e.getValue().timestamp)
                            .thenComparingLong(e -> e.getValue().sequence))
                    .map(Map.Entry::getKey)
                    .orElse(null);
            if (oldest == null) {
                break;
            }
            PDF_CACHE.remove(oldest);
        }
        return key;
    }

    /**
     * Removes every expired PDF, whether or not anyone asked for it again (UC-005 BR-018).
     *
     * @return how many were removed
     */
    static synchronized int sweep() {
        int before = PDF_CACHE.size();
        PDF_CACHE.values().removeIf(PDFCacheEntry::isExpired);
        return before - PDF_CACHE.size();
    }

    /** How many PDFs are cached. */
    static int cachedEntries() {
        return PDF_CACHE.size();
    }

    /** How many bytes of PDF are cached. */
    static long cachedBytes() {
        long bytes = 0;
        for (PDFCacheEntry entry : PDF_CACHE.values()) {
            bytes += entry.content == null ? 0 : entry.content.length;
        }
        return bytes;
    }

    /** Downloads with English error texts; kept for callers that have no request headers. */
    public Response downloadPDF(String key) {
        return downloadPDF(key, null);
    }

    @GET
    @Produces("application/pdf")
    public Response downloadPDF(@QueryParam("key") String key, @Context HttpHeaders headers) {
        Locale locale = headers == null || headers.getAcceptableLanguages().isEmpty()
                ? Locale.ENGLISH : headers.getAcceptableLanguages().get(0);
        if (key == null || key.isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(Translations.get(locale, "pdf.download.missingKey"))
                    .build();
        }

        PDFCacheEntry entry = PDF_CACHE.get(key);
        if (entry == null || entry.isExpired()) {
            // Clean up expired entry
            PDF_CACHE.remove(key);
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Translations.get(locale, "pdf.download.expired"))
                    .build();
        }

        byte[] pdfContent = entry.content;
        // Note: Keep the cache entry alive for retries - don't remove it immediately
        // It will expire naturally after CACHE_EXPIRY_MS

        // Return the PDF content with appropriate headers
        return Response.ok(pdfContent)
                .header("Content-Type", "application/pdf")
                .header("Content-Disposition", "inline; filename=\"family_history_report.pdf\"")
                .header("Cache-Control", "no-cache, no-store, must-revalidate")
                .header("Pragma", "no-cache")
                .header("Expires", "0")
                .build();
    }
}
