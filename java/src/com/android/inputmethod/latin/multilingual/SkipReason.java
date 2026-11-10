/*
 * Copyright (C) 2026 YRRPs
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of
 * the GNU General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 */

package com.android.inputmethod.latin.multilingual;

/** Why an enabled input style is not typed together with the current one. */
public enum SkipReason {
    LAYOUT_MISMATCH("layoutMismatch"),
    SCRIPT_MISMATCH("scriptMismatch"),
    NO_DICTIONARY("noDictionary"),
    SELECTOR_FAILED("selectorFailed");

    private final String mCode;

    SkipReason(final String code) {
        mCode = code;
    }

    /** Stable code printed in the dump. */
    public String code() {
        return mCode;
    }
}
