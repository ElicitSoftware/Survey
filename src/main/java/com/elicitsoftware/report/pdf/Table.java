package com.elicitsoftware.report.pdf;

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

/**
 * A table in a PDF request ({@link Content#table}): its header row, its column widths and
 * its body rows. {@code PDFService} sizes the first column to its content and takes the
 * other columns' widths from {@link #widths}.
 */
public class Table {
    public String[] headers;
    public float[] widths;
    public String[][] body;
}
