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

import java.util.concurrent.ConcurrentHashMap;

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
 */
@Path("/api/pdf/download")
public class PDFDownloadResource {
    
    // Thread-safe cache for PDF data with timestamps
    private static final ConcurrentHashMap<String, PDFCacheEntry> PDF_CACHE = new ConcurrentHashMap<>();
    private static final long CACHE_EXPIRY_MS = 10 * 60 * 1000; // 10 minutes

    /**
     * Inner class to store PDF content with timestamp for expiry
     */
    private static class PDFCacheEntry {
        byte[] content;
        long timestamp;

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
        String key = "pdf_" + new RandomStringGenerator(32).nextString();
        PDF_CACHE.put(key, new PDFCacheEntry(pdfContent));
        return key;
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
