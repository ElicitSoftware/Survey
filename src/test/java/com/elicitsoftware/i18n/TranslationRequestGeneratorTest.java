package com.elicitsoftware.i18n;

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

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * UC-007 / FR-020: the committed translation handoff document must match what the generator
 * produces from the shipped bundle. The freshly generated copy is always written to
 * {@code target/i18n/TRANSLATION_REQUEST.md}; on drift, copy it over the committed file.
 */
class TranslationRequestGeneratorTest {

    @Test
    void committedHandoffDocument_isUpToDate() throws IOException {
        Path root = DisplayedStringsCoverageTest.moduleRoot();
        String generated = TranslationRequestGenerator.generate(root);
        Path fresh = root.resolve("target/i18n/TRANSLATION_REQUEST.md");
        Files.createDirectories(fresh.getParent());
        Files.writeString(fresh, generated, StandardCharsets.UTF_8);

        Path committed = root.resolve(TranslationRequestGenerator.OUTPUT);
        String actual = Files.exists(committed) ? Files.readString(committed, StandardCharsets.UTF_8) : "";
        assertEquals(generated, actual,
                "i18n/TRANSLATION_REQUEST.md is out of date; run: cp target/i18n/TRANSLATION_REQUEST.md i18n/TRANSLATION_REQUEST.md");
    }
}
