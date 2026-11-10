/*
 * Copyright (C) 2026 YRRPs
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of
 * the GNU General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 */

package com.android.inputmethod.latin.multilingual;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;

import org.junit.Test;

public class MultilingualLocaleSelectorTest {
    private static final int LATIN = 11;
    private static final int CYRILLIC = 3;
    private static final String QWERTY = "qwerty";
    private static final Locale EN_US = new Locale("en", "US");
    private static final Locale FR = new Locale("fr");
    private static final Locale NL_BE = new Locale("nl", "BE");
    private static final Locale RU = new Locale("ru");

    private static LocaleCandidate c(final Locale l, final String layout, final int script,
            final boolean dict) {
        return new LocaleCandidate(l, layout, script, dict);
    }

    @Test
    public void mainFirstThenEnabledOrder() {
        final LocaleSelection s = MultilingualLocaleSelector.select(c(FR, QWERTY, LATIN, true),
                Arrays.asList(c(EN_US, QWERTY, LATIN, true), c(FR, QWERTY, LATIN, true),
                        c(NL_BE, QWERTY, LATIN, true)));
        assertEquals(Arrays.asList(FR, EN_US, NL_BE), s.active());
        assertEquals(FR, s.main());
        assertTrue(s.skipped().isEmpty());
        assertTrue(s.isMultilingual());
    }

    @Test
    public void skipsLayoutMismatch() {
        final LocaleSelection s = MultilingualLocaleSelector.select(c(EN_US, QWERTY, LATIN, true),
                Arrays.asList(c(NL_BE, "azerty", LATIN, true)));
        assertEquals(Collections.singletonList(EN_US), s.active());
        assertEquals(SkipReason.LAYOUT_MISMATCH, s.skipped().get(NL_BE));
    }

    @Test
    public void skipsScriptMismatch() {
        final LocaleSelection s = MultilingualLocaleSelector.select(c(EN_US, QWERTY, LATIN, true),
                Arrays.asList(c(RU, QWERTY, CYRILLIC, true)));
        assertEquals(SkipReason.SCRIPT_MISMATCH, s.skipped().get(RU));
    }

    @Test
    public void skipsDuplicateLocale() {
        final LocaleSelection s = MultilingualLocaleSelector.select(c(EN_US, QWERTY, LATIN, true),
                Arrays.asList(c(FR, QWERTY, LATIN, true), c(FR, QWERTY, LATIN, true)));
        assertEquals(Arrays.asList(EN_US, FR), s.active());
        assertEquals(SkipReason.DUPLICATE, s.skipped().get(FR));
    }

    @Test
    public void skipsSecondaryWithoutDictionaryButKeepsMain() {
        final LocaleSelection s = MultilingualLocaleSelector.select(c(NL_BE, QWERTY, LATIN, false),
                Arrays.asList(c(FR, QWERTY, LATIN, false)));
        assertEquals(Collections.singletonList(NL_BE), s.active());
        assertEquals(SkipReason.NO_DICTIONARY, s.skipped().get(FR));
        assertFalse(s.isMultilingual());
    }

    @Test
    public void singleIsNotMultilingual() {
        final LocaleSelection s = LocaleSelection.single(EN_US);
        assertEquals(Collections.singletonList(EN_US), s.active());
        assertFalse(s.isMultilingual());
        assertFalse(s.selectorFailed());
    }

    @Test
    public void failedSelectionFallsBackToMainOnly() {
        final LocaleSelection s = LocaleSelection.failed(EN_US);
        assertEquals(Collections.singletonList(EN_US), s.active());
        assertTrue(s.selectorFailed());
        assertEquals(SkipReason.SELECTOR_FAILED, s.skipped().get(EN_US));
    }
}
