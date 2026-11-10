/*
 * Copyright (C) 2026 YRRPs
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of
 * the GNU General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 */

package com.android.inputmethod.latin.multilingual;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** The ordered languages typed together, main language first, plus what was left out. */
public final class LocaleSelection {
    private final List<Locale> mActive;
    private final Map<Locale, SkipReason> mSkipped;
    private final boolean mSelectorFailed;

    LocaleSelection(final List<Locale> active, final Map<Locale, SkipReason> skipped) {
        this(active, skipped, false);
    }

    private LocaleSelection(final List<Locale> active, final Map<Locale, SkipReason> skipped,
            final boolean selectorFailed) {
        mActive = Collections.unmodifiableList(new ArrayList<>(active));
        mSkipped = Collections.unmodifiableMap(new LinkedHashMap<>(skipped));
        mSelectorFailed = selectorFailed;
    }

    /** Stock single-language behaviour. */
    public static LocaleSelection single(final Locale main) {
        return new LocaleSelection(Collections.singletonList(main),
                Collections.<Locale, SkipReason>emptyMap());
    }

    /** Single-language fallback after the selector threw. */
    public static LocaleSelection failed(final Locale main) {
        return new LocaleSelection(Collections.singletonList(main),
                Collections.singletonMap(main, SkipReason.SELECTOR_FAILED), true);
    }

    public List<Locale> active() {
        return mActive;
    }

    public Locale main() {
        return mActive.get(0);
    }

    public Map<Locale, SkipReason> skipped() {
        return mSkipped;
    }

    public boolean isMultilingual() {
        return mActive.size() > 1;
    }

    public boolean selectorFailed() {
        return mSelectorFailed;
    }
}
