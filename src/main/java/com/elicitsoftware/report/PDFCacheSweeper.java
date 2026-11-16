package com.elicitsoftware.report;

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
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * UC-005 BR-018: removes expired PDFs from the download cache on a schedule, so a PDF nobody
 * opens again does not stay in memory until the application stops.
 */
@ApplicationScoped
public class PDFCacheSweeper {

    public PDFCacheSweeper() {
        // CDI managed bean
    }

    @Scheduled(identity = "pdf-cache-sweep", every = "5m", delayed = "5m",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void sweep() {
        int removed = PDFDownloadResource.sweep();
        if (removed > 0) {
            Log.debugf("Removed %d expired PDF(s) from the download cache", removed);
        }
    }
}
