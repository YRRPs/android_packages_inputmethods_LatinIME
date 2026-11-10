/*
 * Copyright (C) 2026 YRRPs
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of
 * the GNU General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 */

package com.android.inputmethod.latin.multilingual;

import java.util.Locale;

/** Snapshot of one active language for the dump. */
public final class LanguageState {
    private final Locale mLocale;
    private final int mConfidence;
    private final float mWeight;
    private final LoadState mLoadState;

    public LanguageState(final Locale locale, final int confidence, final float weight,
            final LoadState loadState) {
        mLocale = locale;
        mConfidence = confidence;
        mWeight = weight;
        mLoadState = loadState;
    }

    String format() {
        return String.format(Locale.ROOT, "locale=%s confidence=%d weight=%.2f load=%s",
                mLocale, mConfidence, mWeight, mLoadState.code());
    }
}
