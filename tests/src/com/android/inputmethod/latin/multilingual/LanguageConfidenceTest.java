/*
 * Copyright (C) 2026 YRRPs
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of
 * the GNU General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 */

package com.android.inputmethod.latin.multilingual;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class LanguageConfidenceTest {
    private static final float EPS = 1e-6f;

    @Test
    public void startsAtOneWithWeightPointNine() {
        final LanguageConfidence c = new LanguageConfidence();
        assertEquals(1, c.value());
        assertEquals(0.9f, c.weightFactor(), EPS);
    }

    @Test
    public void knownWordsClimbPastNominalMaximum() {
        final LanguageConfidence c = new LanguageConfidence();
        for (int i = 0; i < 4; i++) c.recordKnownWord();
        assertEquals(5, c.value());
        assertEquals(1.0f, c.weightFactor(), EPS);
    }

    @Test
    public void unknownWordAboveMaximumDropsToMaximum() {
        final LanguageConfidence c = new LanguageConfidence();
        for (int i = 0; i < 4; i++) c.recordKnownWord();
        c.recordUnknownWord();
        assertEquals(2, c.value());
    }

    @Test
    public void unknownWordsFloorAtZero() {
        final LanguageConfidence c = new LanguageConfidence();
        c.recordUnknownWord();
        c.recordUnknownWord();
        assertEquals(0, c.value());
        assertEquals(0.8f, c.weightFactor(), EPS);
    }

    @Test
    public void copyFromCarriesValueAcrossReset() {
        final LanguageConfidence old = new LanguageConfidence();
        for (int i = 0; i < 3; i++) old.recordKnownWord();
        final LanguageConfidence fresh = new LanguageConfidence();
        fresh.copyFrom(old);
        assertEquals(4, fresh.value());
    }
}
