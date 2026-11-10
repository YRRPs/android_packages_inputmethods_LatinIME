/*
 * Copyright (C) 2026 YRRPs
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of
 * the GNU General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 */

package com.android.inputmethod.latin.multilingual;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Dump state and transition ring buffer for multilingual typing.
 *
 * Privacy: every entry point takes enums, locales, numbers or a Throwable whose class name
 * alone is kept, so typed text cannot reach the dump.
 */
public final class MultilingualDiagnostics {
    public static final int CAPACITY = 64;
    private static final String INDENT = "  ";

    private final LongSupplier mClock;
    private final ArrayDeque<String> mEvents = new ArrayDeque<>(CAPACITY);
    private LocaleSelection mSelection;
    private long mLastSuggestionMs;
    private long mMaxSuggestionMs;

    public MultilingualDiagnostics(final LongSupplier clock) {
        mClock = clock;
    }

    /** Installs a new language set, resets suggestion timing and records the rebuild. */
    public synchronized void setSelection(final LocaleSelection selection) {
        mSelection = selection;
        mLastSuggestionMs = 0;
        mMaxSuggestionMs = 0;
        record(MultilingualEvent.LANGUAGE_SET_REBUILT, selection.main());
    }

    public synchronized void record(final MultilingualEvent event, final Locale locale) {
        append(event + " " + locale);
    }

    public synchronized void recordFailure(final MultilingualEvent event, final Locale locale,
            final Throwable failure) {
        append(event + " " + locale + " " + failure.getClass().getSimpleName());
    }

    public synchronized void recordSuggestionMs(final long ms) {
        mLastSuggestionMs = ms;
        mMaxSuggestionMs = Math.max(mMaxSuggestionMs, ms);
    }

    public synchronized String dump(final List<LanguageState> states) {
        final StringBuilder sb = new StringBuilder("Multilingual:\n");
        appendSelection(sb);
        for (final LanguageState state : states) {
            line(sb, state.format());
        }
        line(sb, "lastSuggestionMs=" + mLastSuggestionMs + " maxSuggestionMs=" + mMaxSuggestionMs);
        line(sb, "events:");
        for (final String event : mEvents) {
            line(sb, INDENT + event);
        }
        return sb.toString();
    }

    private void appendSelection(final StringBuilder sb) {
        if (mSelection == null) {
            line(sb, "enabled=false");
            return;
        }
        line(sb, "enabled=" + mSelection.isMultilingual());
        line(sb, "mainLocale=" + mSelection.main());
        line(sb, "activeLocales=" + joinLocales(mSelection.active()));
        for (final Map.Entry<Locale, SkipReason> skip : mSelection.skipped().entrySet()) {
            line(sb, "skipped=" + skip.getKey() + ":" + skip.getValue().code());
        }
    }

    private static void line(final StringBuilder sb, final String text) {
        sb.append(INDENT).append(text).append('\n');
    }

    private static String joinLocales(final List<Locale> locales) {
        final StringBuilder sb = new StringBuilder();
        for (final Locale locale : locales) {
            if (sb.length() > 0) sb.append(',');
            sb.append(locale);
        }
        return sb.toString();
    }

    private void append(final String entry) {
        if (mEvents.size() == CAPACITY) {
            mEvents.removeFirst();
        }
        mEvents.addLast("+" + mClock.getAsLong() + "ms " + entry);
    }
}
