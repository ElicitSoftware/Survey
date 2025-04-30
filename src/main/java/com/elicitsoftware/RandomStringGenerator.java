package com.elicitsoftware;

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

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;

/**
 * A generator for creating random strings with customizable properties.
 * This class can produce alphanumeric strings or strings with custom character sets.
 * It provides constructors for specifying the length of the string, the random
 * number generator to use, and the set of symbols to draw from.
 */
public class RandomStringGenerator {

    public static final String upper = "BCDFGHJKLMNPQRSTVWXZ";
    public static final String digits = "2456789";
    public static final String lower = upper.toLowerCase(Locale.ROOT);
    public static final String alphanum = upper + lower + digits;
    private final Random random;
    private final char[] symbols;
    private final char[] buf;

    /**
     * Constructs a RandomStringGenerator with a specified string length, random number generator,
     * and character set for generating random strings.
     *
     * @param length  the length of the random string to be generated. Must be greater than or equal to 1.
     * @param random  the Random instance to use for generating random numbers. Cannot be null.
     * @param symbols the set of characters to draw from when constructing the random string.
     *                Must contain at least 2 distinct characters.
     * @throws IllegalArgumentException if the length is less than 1 or if the symbols string contains fewer than 2 distinct characters.
     * @throws NullPointerException     if the random parameter is null.
     */
    public RandomStringGenerator(int length, Random random, String symbols) {
        if (length < 1) throw new IllegalArgumentException();
        if (symbols.length() < 2) throw new IllegalArgumentException();
        this.random = Objects.requireNonNull(random);
        this.symbols = symbols.toCharArray();
        this.buf = new char[length];
    }


    /**
     * Constructs a RandomStringGenerator with a specified string length and a random
     * number generator, defaulting to using the alphanumeric character set for generating random strings.
     *
     * @param length the length of the random string to be generated. Must be greater than or equal to 1.
     * @param random the Random instance to use for generating random numbers. Cannot be null.
     * @throws IllegalArgumentException if the length is less than 1.
     * @throws NullPointerException     if the random parameter is null.
     */
    public RandomStringGenerator(int length, Random random) {
        this(length, random, alphanum);
    }

    /**
     * Constructs a RandomStringGenerator with a specified string length and uses
     * a SecureRandom instance for generating random numbers and the default
     * alphanumeric character set.
     *
     * @param length the length of the random string to be generated. Must be greater than or equal to 1.
     * @throws IllegalArgumentException if the length is less than 1.
     */
    public RandomStringGenerator(int length) {
        this(length, new SecureRandom());
    }

    /**
     * Generates the next random string based on the specified length and character set
     * provided during the initialization of the RandomStringGenerator.
     *
     * @return a randomly generated string consisting of characters from the configured symbol set.
     */
    public String nextString() {
        for (int idx = 0; idx < buf.length; ++idx)
            buf[idx] = symbols[random.nextInt(symbols.length)];
        return new String(buf);
    }
}
