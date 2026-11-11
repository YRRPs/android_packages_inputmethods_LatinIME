/*
 * Copyright (C) 2026 YRRPs
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of
 * the GNU General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 */

package com.android.inputmethod.latin.multilingual;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Space bar label listing the languages typed together, such as {@code EN · FR · NL}. */
public final class MultilingualLabel {
    static final String SEPARATOR = " · ";
    private static final int MIN_LANGUAGES = 2;

    private MultilingualLabel() {}

    /** Returns the label, or null when fewer than two distinct languages are active. */
    public static String format(final List<Locale> active) {
        final Set<String> languages = new LinkedHashSet<>();
        for (final Locale locale : active) {
            languages.add(locale.getLanguage().toUpperCase(Locale.ROOT));
        }
        if (languages.size() < MIN_LANGUAGES) {
            return null;
        }
        return String.join(SEPARATOR, languages);
    }
}
