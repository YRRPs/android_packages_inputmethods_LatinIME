/*
 * Copyright (C) 2026 YRRPs
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of
 * the GNU General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 */

package com.android.inputmethod.latin.multilingual;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import org.junit.Test;

public class MultilingualAutocorrectGuardTest {
    private static final Set<String> KNOWN = new HashSet<>(Arrays.asList("pain", "bedankt"));
    private static final Locale FR = new Locale("fr");

    private static boolean blocks(final boolean multilingual, final String word,
            final boolean firstCharCapitalized) {
        return MultilingualAutocorrectGuard.blocksAutocorrection(multilingual, word,
                firstCharCapitalized, FR, KNOWN::contains);
    }

    @Test
    public void wordKnownInAnyLanguageBlocksAutocorrection() {
        assertTrue(blocks(true, "pain", false));
    }

    @Test
    public void unknownWordDoesNotBlock() {
        assertFalse(blocks(true, "paim", false));
    }

    @Test
    public void capitalizedFirstWordChecksLowerCaseForm() {
        assertTrue(blocks(true, "Bedankt", true));
        assertFalse(blocks(true, "Bedankt", false));
    }

    @Test
    public void singleLanguageKeepsStockBehaviour() {
        assertFalse(blocks(false, "pain", false));
    }
}
