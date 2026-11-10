/*
 * Copyright (C) 2026 YRRPs
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of
 * the GNU General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 */

package com.android.inputmethod.latin.multilingual;

import android.content.res.Resources;
import android.util.Log;
import android.view.inputmethod.InputMethodSubtype;

import com.android.inputmethod.latin.RichInputMethodManager;
import com.android.inputmethod.latin.RichInputMethodSubtype;
import com.android.inputmethod.latin.utils.DictionaryInfoUtils;
import com.android.inputmethod.latin.utils.ScriptUtils;
import com.android.inputmethod.latin.utils.SubtypeLocaleUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Reads the enabled input styles of this IME and selects the languages typed together. */
public final class MultilingualLocales {
    private static final String TAG = MultilingualLocales.class.getSimpleName();

    private MultilingualLocales() {}

    /**
     * Returns the language selection for the current input style. Falls back to the current
     * language alone if reading the enabled styles fails.
     */
    public static LocaleSelection compute(final RichInputMethodManager imm, final Resources res,
            final Locale currentLocale) {
        try {
            final RichInputMethodSubtype currentSubtype = imm.getCurrentSubtype();
            // "No language" styles type without dictionaries; mixing languages makes no sense.
            if (currentSubtype.isNoLanguage()) {
                return LocaleSelection.single(currentLocale);
            }
            final LocaleCandidate current =
                    toCandidate(res, currentSubtype.getRawSubtype(), currentLocale);
            final List<LocaleCandidate> enabled = new ArrayList<>();
            for (final InputMethodSubtype subtype
                    : imm.getMyEnabledInputMethodSubtypeList(true /* implicit */)) {
                enabled.add(toCandidate(res, subtype,
                        new RichInputMethodSubtype(subtype).getLocale()));
            }
            return MultilingualLocaleSelector.select(current, enabled);
        } catch (final RuntimeException e) {
            Log.w(TAG, "Multilingual selection failed: " + e.getClass().getSimpleName());
            return LocaleSelection.failed(currentLocale);
        }
    }

    // RichInputMethodSubtype#getLocale applies the same locale mapping the facilitator sees.
    // Only dictionaries bundled in the APK count; this ROM ships no dictionary pack.
    private static LocaleCandidate toCandidate(final Resources res,
            final InputMethodSubtype subtype, final Locale locale) {
        return new LocaleCandidate(locale,
                SubtypeLocaleUtils.getKeyboardLayoutSetName(subtype),
                ScriptUtils.getScriptFromSpellCheckerLocale(locale),
                DictionaryInfoUtils.getMainDictionaryResourceIdIfAvailableForLocale(res, locale)
                        != 0);
    }
}
