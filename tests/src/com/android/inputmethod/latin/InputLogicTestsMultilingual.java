/*
 * Copyright (C) 2026 YRRPs
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of
 * the GNU General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 */

package com.android.inputmethod.latin;

import androidx.test.filters.LargeTest;

import com.android.inputmethod.latin.multilingual.LocaleCandidate;
import com.android.inputmethod.latin.multilingual.MultilingualLocaleSelector;
import com.android.inputmethod.latin.utils.ScriptUtils;

import java.util.Arrays;
import java.util.Locale;

/**
 * Autocorrection through LatinIME and Suggest with English and French active together.
 * en_ZZ/fr_ZZ keep the user history away from the device owner's real dictionaries.
 */
@LargeTest
public class InputLogicTestsMultilingual extends InputTestsBase {
    private static final Locale EN_TEST = new Locale("en", "ZZ");
    private static final Locale FR_TEST = new Locale("fr", "ZZ");

    private static LocaleCandidate qwerty(final Locale locale) {
        return new LocaleCandidate(locale, "qwerty", ScriptUtils.SCRIPT_LATIN, true);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mLatinIME.replaceDictionariesForTest(MultilingualLocaleSelector.select(qwerty(EN_TEST),
                Arrays.asList(qwerty(EN_TEST), qwerty(FR_TEST))));
        waitForDictionariesToBeLoaded();
    }

    public void testFrenchWordIsNotAutocorrected() {
        type("pourquoi ");
        assertEquals("French word kept", "pourquoi ", mEditText.getText().toString());
    }

    public void testNonWordIsStillAutocorrected() {
        type("tgis ");
        assertEquals("guard does not disable autocorrection", "this ",
                mEditText.getText().toString());
    }
}
