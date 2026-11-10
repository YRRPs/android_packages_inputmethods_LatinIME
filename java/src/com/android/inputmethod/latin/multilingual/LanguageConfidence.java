/*
 * Copyright (C) 2026 YRRPs
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of
 * the GNU General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 */

package com.android.inputmethod.latin.multilingual;

/**
 * Word-by-word confidence that the user is typing in one language.
 *
 * Based on OpenBoard PR #565 (https://github.com/openboard-team/openboard/pull/565).
 */
public final class LanguageConfidence {
    static final int INITIAL = 1;
    static final int MIN = 0;
    static final int MAX = 2;
    private static final float BASE_WEIGHT = 8f;
    private static final float WEIGHT_DIVISOR = 10f;

    // Written on the UI thread, read on the suggestion thread.
    private volatile int mValue = INITIAL;

    /** Records a committed word this language knows. Climbs past MAX on purpose. */
    public void recordKnownWord() {
        mValue++;
    }

    /** Records a committed word this language does not know. */
    public void recordUnknownWord() {
        if (mValue > MAX) {
            mValue = MAX;
            return;
        }
        if (mValue > MIN) {
            mValue--;
        }
    }

    /** Takes over another instance's value, used when a reset keeps the same languages. */
    public void copyFrom(final LanguageConfidence other) {
        mValue = other.mValue;
    }

    public int value() {
        return mValue;
    }

    /** Multiplier applied to this language's suggestion weight: 0.8, 0.9 or 1.0. */
    public float weightFactor() {
        if (mValue >= MAX) {
            return 1f;
        }
        return (BASE_WEIGHT + mValue) / WEIGHT_DIVISOR;
    }
}
