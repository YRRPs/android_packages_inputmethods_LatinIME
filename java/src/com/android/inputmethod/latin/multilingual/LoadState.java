/*
 * Copyright (C) 2026 YRRPs
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of
 * the GNU General Public License as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 */

package com.android.inputmethod.latin.multilingual;

import java.util.Locale;

/** Load state of one language's main dictionary. */
public enum LoadState {
    LOADING,
    LOADED,
    FAILED;

    /** Stable code printed in the dump. */
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }
}
