/*
 * Copyright (C) 2026 YRRPs
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of
 * the GNU General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 */

package com.android.inputmethod.latin;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Locale;

/** Proves the generated main_nl.dict loads natively and serves Flemish via fallback. */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class DutchMainDictionaryTests {
    @Test
    public void flemishLoadsDutchMainDictionary() {
        final Dictionary dict = DictionaryFactory.createMainDictionaryFromManager(
                InstrumentationRegistry.getTargetContext(), new Locale("nl", "BE"));
        try {
            assertTrue(dict.isInitialized());
            assertTrue(dict.isValidWord("bedankt"));
            assertTrue(dict.isValidWord("een"));
            assertFalse(dict.isValidWord("qzxqzx"));
        } finally {
            dict.close();
        }
    }
}
