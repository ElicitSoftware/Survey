package com.elicitsoftware.survey;

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

import com.elicitsoftware.RandomStringGenerator;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertThrows;

@QuarkusTest
public class RandomStringGeneratorTest {

    @Test
    public void testValidConstruction() {
        Random random = new Random();
        String symbols = "ABCD";  // Valid symbols string

        RandomStringGenerator generator = new RandomStringGenerator(10, random, symbols);
        String result = generator.nextString();
        assert result != null && result.length() == 10;
    }

    @Test
    public void testInvalidLength() {
        Random random = new Random();
        String symbols = "ABCD";  // Valid symbols string

        assertThrows(IllegalArgumentException.class, () -> {
            new RandomStringGenerator(0, random, symbols);
        });
    }

    @Test
    public void testInvalidSymbolsLength() {
        Random random = new Random();

        assertThrows(IllegalArgumentException.class, () -> {
            new RandomStringGenerator(10, random, "A");
        });
    }

    @Test
    public void testNullRandom() {
        String symbols = "ABCD";
        assertThrows(NullPointerException.class, () -> {
            new RandomStringGenerator(10, null, symbols);
        });
    }
}
