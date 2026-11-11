/*
 * Copyright (C) 2026 YRRPs
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of
 * the GNU General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 */

package com.android.inputmethod.latin.multilingual;

import java.util.Locale;

/** One enabled input style, reduced to what language selection needs. */
public final class LocaleCandidate {
    private final Locale mLocale;
    private final String mLayoutSet;
    private final int mScript;
    private final boolean mHasMainDictionary;

    public LocaleCandidate(final Locale locale, final String layoutSet, final int script,
            final boolean hasMainDictionary) {
        mLocale = locale;
        mLayoutSet = layoutSet;
        mScript = script;
        mHasMainDictionary = hasMainDictionary;
    }

    public Locale locale() {
        return mLocale;
    }

    public String layoutSet() {
        return mLayoutSet;
    }

    public int script() {
        return mScript;
    }

    public boolean hasMainDictionary() {
        return mHasMainDictionary;
    }
}
