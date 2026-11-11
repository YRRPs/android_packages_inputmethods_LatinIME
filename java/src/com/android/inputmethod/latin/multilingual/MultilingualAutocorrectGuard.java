/*
 * Copyright (C) 2026 YRRPs
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of
 * the GNU General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 */

package com.android.inputmethod.latin.multilingual;

import java.util.Locale;
import java.util.function.Predicate;

/** Decides whether multilingual typing forbids autocorrecting the typed word. */
public final class MultilingualAutocorrectGuard {
    private MultilingualAutocorrectGuard() {}

    /**
     * Returns true when several languages are active and any of them knows the typed word, or
     * its lower-case form when only the first character is capitalized. Single-language typing
     * always returns false so stock autocorrection is unchanged.
     */
    public static boolean blocksAutocorrection(final boolean multilingual, final String word,
            final boolean firstCharCapitalized, final Locale locale,
            final Predicate<String> isValidInAnyLanguage) {
        if (!multilingual || word.isEmpty()) {
            return false;
        }
        if (isValidInAnyLanguage.test(word)) {
            return true;
        }
        return firstCharCapitalized && isValidInAnyLanguage.test(word.toLowerCase(locale));
    }
}
