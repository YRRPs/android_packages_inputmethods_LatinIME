/*
 * Copyright (C) 2026 YRRPs
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of
 * the GNU General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 */

package com.android.inputmethod.latin.multilingual;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;

import org.junit.Test;

public class MultilingualLabelTest {
    @Test
    public void joinsUppercaseLanguageCodes() {
        assertEquals("EN · FR · NL", MultilingualLabel.format(Arrays.asList(
                new Locale("en", "US"), new Locale("fr"), new Locale("nl", "BE"))));
    }

    @Test
    public void singleLanguageHasNoLabel() {
        assertNull(MultilingualLabel.format(Collections.singletonList(new Locale("en", "US"))));
    }

    @Test
    public void sameLanguageTwiceHasNoLabel() {
        assertNull(MultilingualLabel.format(Arrays.asList(
                new Locale("en", "US"), new Locale("en", "GB"))));
    }
}
