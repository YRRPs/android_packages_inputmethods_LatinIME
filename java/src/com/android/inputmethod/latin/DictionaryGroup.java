/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Modifications Copyright (C) 2026 YRRPs, based on OpenBoard PR #565 and licensed under
 * GPL-3.0-or-later. See NOTICE.multilingual.
 */

package com.android.inputmethod.latin;

import android.text.TextUtils;

import com.android.inputmethod.latin.multilingual.LanguageConfidence;
import com.android.inputmethod.latin.multilingual.LanguageState;
import com.android.inputmethod.latin.multilingual.LoadState;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

/**
 * A group of dictionaries that work together for a single language.
 */
final class DictionaryGroup {
    // TODO: Add null analysis annotations.
    public static final float WEIGHT_FOR_MOST_PROBABLE_LANGUAGE = 1.0f;

    /**
     * The locale associated with the dictionary group.
     */
    @Nullable public final Locale mLocale;

    /**
     * The user account associated with the dictionary group.
     */
    @Nullable public final String mAccount;

    @Nullable private Dictionary mMainDict;
    // Confidence that the user is typing in this group's language.
    public final LanguageConfidence mConfidence = new LanguageConfidence();
    public volatile LoadState mLoadState = LoadState.LOADING;

    public float mWeightForTypingInLocale = WEIGHT_FOR_MOST_PROBABLE_LANGUAGE;
    public float mWeightForGesturingInLocale = WEIGHT_FOR_MOST_PROBABLE_LANGUAGE;
    public final ConcurrentHashMap<String, ExpandableBinaryDictionary> mSubDictMap =
            new ConcurrentHashMap<>();

    public DictionaryGroup() {
        this(null /* locale */, null /* mainDict */, null /* account */,
                Collections.<String, ExpandableBinaryDictionary>emptyMap() /* subDicts */);
    }

    public DictionaryGroup(@Nullable final Locale locale,
            @Nullable final Dictionary mainDict,
            @Nullable final String account,
            final Map<String, ExpandableBinaryDictionary> subDicts) {
        mLocale = locale;
        mAccount = account;
        // The main dictionary can be asynchronously loaded.
        setMainDict(mainDict);
        for (final Map.Entry<String, ExpandableBinaryDictionary> entry : subDicts.entrySet()) {
            setSubDict(entry.getKey(), entry.getValue());
        }
    }

    private void setSubDict(final String dictType, final ExpandableBinaryDictionary dict) {
        if (dict != null) {
            mSubDictMap.put(dictType, dict);
        }
    }

    public void setMainDict(final Dictionary mainDict) {
        // Close old dictionary if exists. Main dictionary can be assigned multiple times.
        final Dictionary oldDict = mMainDict;
        mMainDict = mainDict;
        if (oldDict != null && mainDict != oldDict) {
            oldDict.close();
        }
    }

    public Dictionary getDict(final String dictType) {
        if (Dictionary.TYPE_MAIN.equals(dictType)) {
            return mMainDict;
        }
        return getSubDict(dictType);
    }

    public ExpandableBinaryDictionary getSubDict(final String dictType) {
        return mSubDictMap.get(dictType);
    }

    public boolean hasDict(final String dictType, @Nullable final String account) {
        if (Dictionary.TYPE_MAIN.equals(dictType)) {
            return mMainDict != null;
        }
        if (Dictionary.TYPE_USER_HISTORY.equals(dictType) &&
                !TextUtils.equals(account, mAccount)) {
            // If the dictionary type is user history, & if the account doesn't match,
            // return immediately. If the account matches, continue looking it up in the
            // sub dictionary map.
            return false;
        }
        return mSubDictMap.containsKey(dictType);
    }

    public void closeDict(final String dictType) {
        final Dictionary dict;
        if (Dictionary.TYPE_MAIN.equals(dictType)) {
            dict = mMainDict;
        } else {
            dict = mSubDictMap.remove(dictType);
        }
        if (dict != null) {
            dict.close();
        }
    }

    LanguageState toState() {
        return new LanguageState(mLocale, mConfidence.value(), mConfidence.weightFactor(),
                mLoadState);
    }
}
