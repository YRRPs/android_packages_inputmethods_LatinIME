/*
 * Copyright (C) 2026 YRRPs
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of
 * the GNU General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 */

package com.android.inputmethod.latin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.LargeTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.inputmethod.latin.multilingual.LocaleCandidate;
import com.android.inputmethod.latin.multilingual.LocaleSelection;
import com.android.inputmethod.latin.multilingual.MultilingualLocaleSelector;
import com.android.inputmethod.latin.utils.ScriptUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class DictionaryFacilitatorMultilingualTests {
    private static final String PREFIX = "test_multilingual_";
    private static final long LOAD_TIMEOUT_SECONDS = 10;

    private DictionaryFacilitatorImpl mFacilitator;

    private static LocaleCandidate qwerty(final Locale locale) {
        return new LocaleCandidate(locale, "qwerty", ScriptUtils.SCRIPT_LATIN, true);
    }

    private static LocaleSelection englishAndFrench() {
        return MultilingualLocaleSelector.select(qwerty(Locale.US),
                Arrays.asList(qwerty(Locale.US), qwerty(Locale.FRENCH)));
    }

    @Before
    public void setUp() throws InterruptedException {
        final Context context = InstrumentationRegistry.getTargetContext();
        mFacilitator = new DictionaryFacilitatorImpl();
        mFacilitator.resetDictionaries(context, englishAndFrench(), false /* contacts */,
                true /* personalized */, false /* forceReload */, null /* account */, PREFIX,
                null /* listener */);
        mFacilitator.waitForLoadingDictionariesForTesting(LOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @After
    public void tearDown() {
        mFacilitator.clearUserHistoryDictionary(InstrumentationRegistry.getTargetContext());
        mFacilitator.closeDictionaries();
    }

    @Test
    public void validInEitherLanguageIsValidSuggestionWord() {
        assertTrue(mFacilitator.isValidSuggestionWord("bonjour"));
        assertTrue(mFacilitator.isValidSuggestionWord("hello"));
    }

    @Test
    public void frenchWordsMakeFrenchPreferred() {
        mFacilitator.addToUserHistory("bonjour", false, NgramContext.BEGINNING_OF_SENTENCE, 0,
                false);
        mFacilitator.addToUserHistory("merci", false, NgramContext.BEGINNING_OF_SENTENCE, 0,
                false);
        assertEquals(Locale.FRENCH, mFacilitator.getPreferredLocaleForTesting());
    }

    @Test
    public void mainLanguageStaysPreferredOnTie() {
        assertEquals(Locale.US, mFacilitator.getPreferredLocaleForTesting());
    }

    @Test
    public void isForLocalesComparesOrderedList() {
        assertTrue(mFacilitator.isForLocales(Arrays.asList(Locale.US, Locale.FRENCH)));
        assertFalse(mFacilitator.isForLocales(Arrays.asList(Locale.FRENCH, Locale.US)));
        assertFalse(mFacilitator.isForLocales(Collections.singletonList(Locale.US)));
    }

    @Test
    public void dumpListsBothLanguages() {
        final String dump = mFacilitator.dump(InstrumentationRegistry.getTargetContext());
        assertTrue(dump, dump.contains("activeLocales=en_US,fr"));
        assertTrue(dump, dump.contains("locale=fr confidence=1"));
    }
}
