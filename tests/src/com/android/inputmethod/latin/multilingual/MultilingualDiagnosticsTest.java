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

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import org.junit.Test;

public class MultilingualDiagnosticsTest {
    private static final Locale FR = new Locale("fr");
    private static final Locale EN_US = new Locale("en", "US");
    private static final Locale DE = new Locale("de");
    private long mNow = 1000;

    private MultilingualDiagnostics newDiagnostics() {
        return new MultilingualDiagnostics(() -> mNow);
    }

    private static LocaleSelection selectionWithSkip() {
        final Map<Locale, SkipReason> skipped = new LinkedHashMap<>();
        skipped.put(DE, SkipReason.LAYOUT_MISMATCH);
        return new LocaleSelection(Arrays.asList(FR, EN_US), skipped);
    }

    private static String emptyDump(final MultilingualDiagnostics d) {
        return d.dump(Collections.<LanguageState>emptyList());
    }

    @Test
    public void dumpListsEveryGateAndSkipReason() {
        final MultilingualDiagnostics d = newDiagnostics();
        d.setSelection(selectionWithSkip());
        final String dump = d.dump(Arrays.asList(
                new LanguageState(FR, 3, 1.0f, LoadState.LOADED),
                new LanguageState(EN_US, 0, 0.8f, LoadState.LOADING)));
        assertTrue(dump, dump.contains("enabled=true"));
        assertTrue(dump, dump.contains("mainLocale=fr"));
        assertTrue(dump, dump.contains("activeLocales=fr,en_US"));
        assertTrue(dump, dump.contains("locale=fr confidence=3 weight=1.00 load=loaded"));
        assertTrue(dump, dump.contains("locale=en_US confidence=0 weight=0.80 load=loading"));
        assertTrue(dump, dump.contains("skipped=de:layoutMismatch"));
        assertTrue(dump, dump.contains("lastSuggestionMs=0 maxSuggestionMs=0"));
        assertTrue(dump, dump.contains("+1000ms LANGUAGE_SET_REBUILT fr"));
    }

    @Test
    public void dumpBeforeAnySelectionIsDisabled() {
        assertTrue(emptyDump(newDiagnostics()).contains("enabled=false"));
    }

    @Test
    public void failureRecordsOnlyExceptionClassName() {
        final MultilingualDiagnostics d = newDiagnostics();
        d.setSelection(LocaleSelection.single(FR));
        d.recordFailure(MultilingualEvent.DICTIONARY_FAILED, FR, new IOException("secret word"));
        final String dump = emptyDump(d);
        assertTrue(dump, dump.contains("DICTIONARY_FAILED fr IOException"));
        assertFalse(dump, dump.contains("secret word"));
    }

    @Test
    public void suggestionTimingTracksLastAndMaxAndResetsOnRebuild() {
        final MultilingualDiagnostics d = newDiagnostics();
        d.setSelection(LocaleSelection.single(FR));
        d.recordSuggestionMs(11);
        d.recordSuggestionMs(4);
        assertTrue(emptyDump(d).contains("lastSuggestionMs=4 maxSuggestionMs=11"));
        d.setSelection(LocaleSelection.single(FR));
        assertTrue(emptyDump(d).contains("lastSuggestionMs=0 maxSuggestionMs=0"));
    }

    @Test
    public void ringBufferKeepsNewestSixtyFourEvents() {
        final MultilingualDiagnostics d = newDiagnostics();
        for (int i = 0; i < MultilingualDiagnostics.CAPACITY + 5; i++) {
            mNow = i;
            d.record(MultilingualEvent.PREFERRED_CHANGED, FR);
        }
        final String dump = emptyDump(d);
        assertFalse(dump, dump.contains("+4ms PREFERRED_CHANGED"));
        assertTrue(dump, dump.contains("+5ms PREFERRED_CHANGED"));
        assertTrue(dump, dump.contains("+68ms PREFERRED_CHANGED"));
    }
}
