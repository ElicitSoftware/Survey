package com.elicitsoftware.report.pdf;

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

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Represents the style properties for formatting content in a PDF document.
 * This class is used to define visual appearance characteristics such as
 * alignment, font properties, color, and margin.
 * <p>
 * Fields:
 * - alignment: Specifies the text alignment. Expected values include "left",
 * "right", "center", or "justify".
 * - bold: Indicates whether the text should be displayed in bold style.
 * - color: Defines the text color using a color code (e.g., hexadecimal).
 * - fontSize: Specifies the size of the text font.
 * - margin: An array defining the margin around the content, where each value
 * in the array represents a specific margin (e.g., [top, right, bottom, left]).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Style {
    public String alignment;
    public Boolean bold;
    public String color;
    public Integer fontSize;
    public Integer[] margin;
}
