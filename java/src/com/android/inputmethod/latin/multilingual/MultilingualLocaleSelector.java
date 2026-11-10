/*
 * Copyright (C) 2026 YRRPs
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of
 * the GNU General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 */

package com.android.inputmethod.latin.multilingual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Chooses the languages typed together with the current input style. */
public final class MultilingualLocaleSelector {
    private MultilingualLocaleSelector() {}

    /**
     * Keeps every enabled input style that shares the current layout and script and has a
     * main dictionary. The current style's own entry in {@code enabled} is skipped silently.
     */
    public static LocaleSelection select(final LocaleCandidate current,
            final List<LocaleCandidate> enabled) {
        final List<Locale> active = new ArrayList<>();
        final Map<Locale, SkipReason> skipped = new LinkedHashMap<>();
        active.add(current.locale());
        for (final LocaleCandidate candidate : enabled) {
            if (candidate.locale().equals(current.locale())) continue;
            final SkipReason reason = rejectionReason(current, candidate, active);
            if (reason != null) {
                skipped.put(candidate.locale(), reason);
                continue;
            }
            active.add(candidate.locale());
        }
        return new LocaleSelection(active, skipped);
    }

    private static SkipReason rejectionReason(final LocaleCandidate current,
            final LocaleCandidate candidate, final List<Locale> active) {
        if (active.contains(candidate.locale())) return SkipReason.DUPLICATE;
        if (!candidate.layoutSet().equals(current.layoutSet())) return SkipReason.LAYOUT_MISMATCH;
        if (candidate.script() != current.script()) return SkipReason.SCRIPT_MISMATCH;
        if (!candidate.hasMainDictionary()) return SkipReason.NO_DICTIONARY;
        return null;
    }
}
