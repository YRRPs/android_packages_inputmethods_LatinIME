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

/**
 * Multilingual facilitator behaviour with real main dictionaries.
 *
 * The "ZZ" country makes the user history files private to this test: UserHistoryDictionary
 * ignores the name prefix and keys its file by locale, so real locales would read and clear the
 * device owner's learned words. en_ZZ and fr_ZZ still load main_en and main_fr by fallback.
 */
@LargeTest
@RunWith(AndroidJUnit4.class)
public class DictionaryFacilitatorMultilingualTests {
    private static final Locale EN_TEST = new Locale("en", "ZZ");
    private static final Locale FR_TEST = new Locale("fr", "ZZ");
    private static final long LOAD_TIMEOUT_SECONDS = 10;
    // Common French words with no English homograph.
    private static final String[] FRENCH_WORDS = { "pourquoi", "beaucoup" };
    private static final String[] ENGLISH_WORDS = { "house", "water", "because", "window" };

    private Context mContext;
    private DictionaryFacilitatorImpl mFacilitator;

    private static LocaleCandidate qwerty(final Locale locale) {
        return new LocaleCandidate(locale, "qwerty", ScriptUtils.SCRIPT_LATIN, true);
    }

    private static LocaleSelection englishAndFrench() {
        return MultilingualLocaleSelector.select(qwerty(EN_TEST),
                Arrays.asList(qwerty(EN_TEST), qwerty(FR_TEST)));
    }

    private void reset() throws InterruptedException {
        mFacilitator.resetDictionaries(mContext, englishAndFrench(), false /* contacts */,
                true /* personalized */, false /* forceReload */, null /* account */,
                "" /* prefix */, null /* listener */);
        mFacilitator.waitForLoadingDictionariesForTesting(LOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private void commit(final String word) throws InterruptedException {
        mFacilitator.addToUserHistory(word, false, NgramContext.BEGINNING_OF_SENTENCE, 0, false);
        mFacilitator.waitForLoadingDictionariesForTesting(LOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    @Before
    public void setUp() throws InterruptedException {
        mContext = InstrumentationRegistry.getTargetContext();
        mFacilitator = new DictionaryFacilitatorImpl();
        reset();
        mFacilitator.clearUserHistoryDictionary(mContext);
    }

    @After
    public void tearDown() {
        mFacilitator.clearUserHistoryDictionary(mContext);
        mFacilitator.closeDictionaries();
    }

    @Test
    public void validInEitherLanguageIsValidSuggestionWord() {
        assertTrue(mFacilitator.isMultilingual());
        assertTrue(mFacilitator.isValidSuggestionWord("pourquoi"));
        assertTrue(mFacilitator.isValidSuggestionWord("house"));
    }

    @Test
    public void frenchWordsMakeFrenchPreferred() throws InterruptedException {
        for (final String word : FRENCH_WORDS) commit(word);
        assertEquals(FR_TEST, mFacilitator.getPreferredLocaleForTesting());
    }

    @Test
    public void mainLanguageStaysPreferredOnTie() {
        assertEquals(EN_TEST, mFacilitator.getPreferredLocaleForTesting());
    }

    @Test
    public void firstFrenchWordAfterEnglishIsLearnedInFrench() throws InterruptedException {
        for (final String word : ENGLISH_WORDS) commit(word);
        commit(FRENCH_WORDS[0]);
        assertTrue(mFacilitator.isInUserHistoryForTesting(FR_TEST, FRENCH_WORDS[0]));
        assertFalse(mFacilitator.isInUserHistoryForTesting(EN_TEST, FRENCH_WORDS[0]));
    }

    @Test
    public void confidenceSurvivesResetWithSameLanguages() throws InterruptedException {
        for (final String word : FRENCH_WORDS) commit(word);
        reset();
        assertEquals(FR_TEST, mFacilitator.getPreferredLocaleForTesting());
    }

    @Test
    public void isForLocalesComparesOrderedList() {
        assertTrue(mFacilitator.isForLocales(Arrays.asList(EN_TEST, FR_TEST)));
        assertFalse(mFacilitator.isForLocales(Arrays.asList(FR_TEST, EN_TEST)));
        assertFalse(mFacilitator.isForLocales(Collections.singletonList(EN_TEST)));
    }

    @Test
    public void dumpListsBothLanguages() {
        final String dump = mFacilitator.dump(mContext);
        assertTrue(dump, dump.contains("activeLocales=en_ZZ,fr_ZZ"));
        assertTrue(dump, dump.contains("locale=fr_ZZ confidence=1 weight=0.90 load=loaded"));
    }
}
