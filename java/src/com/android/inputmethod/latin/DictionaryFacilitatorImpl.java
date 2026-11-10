/*
7 * Copyright (C) 2013 The Android Open Source Project
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
 */

package com.android.inputmethod.latin;

import android.Manifest;
import android.content.Context;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;
import android.util.LruCache;

import com.android.inputmethod.annotations.UsedForTesting;
import com.android.inputmethod.keyboard.Keyboard;
import com.android.inputmethod.latin.NgramContext.WordInfo;
import com.android.inputmethod.latin.SuggestedWords.SuggestedWordInfo;
import com.android.inputmethod.latin.common.ComposedData;
import com.android.inputmethod.latin.common.Constants;
import com.android.inputmethod.latin.common.StringUtils;
import com.android.inputmethod.latin.multilingual.LanguageState;
import com.android.inputmethod.latin.multilingual.LoadState;
import com.android.inputmethod.latin.multilingual.LocaleSelection;
import com.android.inputmethod.latin.multilingual.MultilingualDiagnostics;
import com.android.inputmethod.latin.multilingual.MultilingualEvent;
import com.android.inputmethod.latin.permissions.PermissionsUtil;
import com.android.inputmethod.latin.personalization.UserHistoryDictionary;
import com.android.inputmethod.latin.settings.SettingsValuesForSuggestion;
import com.android.inputmethod.latin.utils.ExecutorUtils;
import com.android.inputmethod.latin.utils.SuggestionResults;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Facilitates interaction with different kinds of dictionaries. Provides APIs
 * to instantiate and select the correct dictionaries (based on language or account),
 * update entries and fetch suggestions.
 *
 * Currently AndroidSpellCheckerService and LatinIME both use DictionaryFacilitator as
 * a client for interacting with dictionaries.
 */
public class DictionaryFacilitatorImpl implements DictionaryFacilitator {
    // TODO: Consolidate dictionaries in native code.
    public static final String TAG = DictionaryFacilitatorImpl.class.getSimpleName();

    // HACK: This threshold is being used when adding a capitalized entry in the User History
    // dictionary.
    private static final int CAPITALIZED_FORM_MAX_PROBABILITY_FOR_INSERT = 140;

    // Dictionaries that decide whether a language "knows" a committed word.
    private static final String[] CONFIDENCE_DICTIONARY_TYPES =
            new String[] { Dictionary.TYPE_MAIN, Dictionary.TYPE_USER };

    // One group per active language, main language first. Replaced as a whole, never mutated.
    private volatile List<DictionaryGroup> mDictionaryGroups =
            Collections.singletonList(new DictionaryGroup());
    private volatile CountDownLatch mLatchForWaitingLoadingMainDictionaries = new CountDownLatch(0);
    // To synchronize assigning mDictionaryGroups to ensure closing dictionaries.
    private final Object mLock = new Object();
    private final MultilingualDiagnostics mDiagnostics =
            new MultilingualDiagnostics(SystemClock::uptimeMillis);

    public static final Map<String, Class<? extends ExpandableBinaryDictionary>>
            DICT_TYPE_TO_CLASS = new HashMap<>();

    static {
        DICT_TYPE_TO_CLASS.put(Dictionary.TYPE_USER_HISTORY, UserHistoryDictionary.class);
        DICT_TYPE_TO_CLASS.put(Dictionary.TYPE_USER, UserBinaryDictionary.class);
        DICT_TYPE_TO_CLASS.put(Dictionary.TYPE_CONTACTS, ContactsBinaryDictionary.class);
    }

    private static final String DICT_FACTORY_METHOD_NAME = "getDictionary";
    private static final Class<?>[] DICT_FACTORY_METHOD_ARG_TYPES =
            new Class[] { Context.class, Locale.class, File.class, String.class, String.class };

    private LruCache<String, Boolean> mValidSpellingWordReadCache;
    private LruCache<String, Boolean> mValidSpellingWordWriteCache;

    /** Inputs shared by every group built in one reset. */
    private static final class GroupRequest {
        final Context mContext;
        final Set<String> mSubDictTypes;
        final boolean mForceReloadMainDictionary;
        @Nullable final String mAccount;
        final String mDictNamePrefix;
        final List<DictionaryGroup> mOldGroups;

        GroupRequest(final Context context, final Set<String> subDictTypes,
                final boolean forceReloadMainDictionary, @Nullable final String account,
                final String dictNamePrefix, final List<DictionaryGroup> oldGroups) {
            mContext = context;
            mSubDictTypes = subDictTypes;
            mForceReloadMainDictionary = forceReloadMainDictionary;
            mAccount = account;
            mDictNamePrefix = dictNamePrefix;
            mOldGroups = oldGroups;
        }
    }

    /** Per-call inputs of a suggestion query, shared by every group. */
    private static final class SuggestionQuery {
        final ComposedData mComposedData;
        final NgramContext mNgramContext;
        final long mProximityInfoHandle;
        final SettingsValuesForSuggestion mSettingsValuesForSuggestion;
        final int mSessionId;
        final float[] mWeightOfLangModelVsSpatialModel =
                new float[] { Dictionary.NOT_A_WEIGHT_OF_LANG_MODEL_VS_SPATIAL_MODEL };
        final boolean mApplyLanguageWeight;

        SuggestionQuery(final ComposedData composedData, final NgramContext ngramContext,
                final long proximityInfoHandle,
                final SettingsValuesForSuggestion settingsValuesForSuggestion,
                final int sessionId, final boolean applyLanguageWeight) {
            mComposedData = composedData;
            mNgramContext = ngramContext;
            mProximityInfoHandle = proximityInfoHandle;
            mSettingsValuesForSuggestion = settingsValuesForSuggestion;
            mSessionId = sessionId;
            mApplyLanguageWeight = applyLanguageWeight;
        }
    }

    @Override
    public void setValidSpellingWordReadCache(final LruCache<String, Boolean> cache) {
        mValidSpellingWordReadCache = cache;
    }

    @Override
    public void setValidSpellingWordWriteCache(final LruCache<String, Boolean> cache) {
        mValidSpellingWordWriteCache = cache;
    }

    private DictionaryGroup mainGroup() {
        return mDictionaryGroups.get(0);
    }

    @Override
    public boolean isForLocale(final Locale locale) {
        return locale != null && locale.equals(mainGroup().mLocale);
    }

    @Override
    public boolean isForLocales(final List<Locale> locales) {
        final List<DictionaryGroup> groups = mDictionaryGroups;
        if (groups.size() != locales.size()) {
            return false;
        }
        for (int i = 0; i < groups.size(); i++) {
            if (!locales.get(i).equals(groups.get(i).mLocale)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns whether this facilitator is exactly for this account.
     *
     * @param account the account to test against.
     */
    public boolean isForAccount(@Nullable final String account) {
        return TextUtils.equals(mainGroup().mAccount, account);
    }

    public DictionaryFacilitatorImpl() {
    }

    @Override
    public void onStartInput() {
    }

    @Override
    public void onFinishInput(Context context) {
    }

    @Override
    public boolean isActive() {
        return mainGroup().mLocale != null;
    }

    @Override
    public Locale getLocale() {
        return mainGroup().mLocale;
    }

    @Override
    public boolean usesContacts() {
        return mainGroup().getSubDict(Dictionary.TYPE_CONTACTS) != null;
    }

    @Override
    public String getAccount() {
        return null;
    }

    @Nullable
    private static ExpandableBinaryDictionary getSubDict(final String dictType,
            final Context context, final Locale locale, final File dictFile,
            final String dictNamePrefix, @Nullable final String account) {
        final Class<? extends ExpandableBinaryDictionary> dictClass =
                DICT_TYPE_TO_CLASS.get(dictType);
        if (dictClass == null) {
            return null;
        }
        try {
            final Method factoryMethod = dictClass.getMethod(DICT_FACTORY_METHOD_NAME,
                    DICT_FACTORY_METHOD_ARG_TYPES);
            final Object dict = factoryMethod.invoke(null /* obj */,
                    new Object[] { context, locale, dictFile, dictNamePrefix, account });
            return (ExpandableBinaryDictionary) dict;
        } catch (final NoSuchMethodException | SecurityException | IllegalAccessException
                | IllegalArgumentException | InvocationTargetException e) {
            Log.e(TAG, "Cannot create dictionary: " + dictType, e);
            return null;
        }
    }

    @Nullable
    static DictionaryGroup findDictionaryGroupWithLocale(final List<DictionaryGroup> groups,
            final Locale locale) {
        for (final DictionaryGroup group : groups) {
            if (locale.equals(group.mLocale)) {
                return group;
            }
        }
        return null;
    }

    @Override
    public void resetDictionaries(
            final Context context,
            final Locale newLocale,
            final boolean useContactsDict,
            final boolean usePersonalizedDicts,
            final boolean forceReloadMainDictionary,
            @Nullable final String account,
            final String dictNamePrefix,
            @Nullable final DictionaryInitializationListener listener) {
        resetDictionaries(context, LocaleSelection.single(newLocale), useContactsDict,
                usePersonalizedDicts, forceReloadMainDictionary, account, dictNamePrefix,
                listener);
    }

    @Override
    public void resetDictionaries(
            final Context context,
            final LocaleSelection selection,
            final boolean useContactsDict,
            final boolean usePersonalizedDicts,
            final boolean forceReloadMainDictionary,
            @Nullable final String account,
            final String dictNamePrefix,
            @Nullable final DictionaryInitializationListener listener) {
        final GroupRequest request = new GroupRequest(context,
                subDictTypesToUse(context, useContactsDict, usePersonalizedDicts),
                forceReloadMainDictionary, account, dictNamePrefix, mDictionaryGroups);
        final List<Locale> locales = selection.active();
        final List<DictionaryGroup> newGroups = new ArrayList<>(locales.size());
        for (int i = 0; i < locales.size(); i++) {
            newGroups.add(buildGroup(request, locales.get(i), i == 0));
        }

        synchronized (mLock) {
            mDictionaryGroups = Collections.unmodifiableList(newGroups);
            if (hasAtLeastOneUninitializedMainDictionary()) {
                asyncReloadUninitializedMainDictionaries(context, listener);
            }
        }
        mDiagnostics.setSelection(selection);
        if (listener != null) {
            listener.onUpdateMainDictionaryAvailability(hasAtLeastOneInitializedMainDictionary());
        }
        closeReleasedDictionaries(request.mOldGroups, newGroups);
        if (mValidSpellingWordWriteCache != null) {
            mValidSpellingWordWriteCache.evictAll();
        }
    }

    private static Set<String> subDictTypesToUse(final Context context,
            final boolean useContactsDict, final boolean usePersonalizedDicts) {
        // TODO: Make subDictTypesToUse configurable by resource or a static final list.
        final HashSet<String> subDictTypesToUse = new HashSet<>();
        subDictTypesToUse.add(Dictionary.TYPE_USER);

        // Do not use contacts dictionary if we do not have permissions to read contacts.
        final boolean contactsPermissionGranted = PermissionsUtil.checkAllPermissionsGranted(
                context, Manifest.permission.READ_CONTACTS);
        if (useContactsDict && contactsPermissionGranted) {
            subDictTypesToUse.add(Dictionary.TYPE_CONTACTS);
        }
        if (usePersonalizedDicts) {
            subDictTypesToUse.add(Dictionary.TYPE_USER_HISTORY);
        }
        return subDictTypesToUse;
    }

    /**
     * Builds one language's group, reusing that language's open dictionaries where possible.
     * Personal dictionaries are keyed to {@code locale} itself, so each language learns
     * separately. Contacts load into the main group only.
     */
    private static DictionaryGroup buildGroup(final GroupRequest request, final Locale locale,
            final boolean isMain) {
        final DictionaryGroup existing =
                findDictionaryGroupWithLocale(request.mOldGroups, locale);
        final Dictionary mainDict = reusableMainDict(request, existing);
        final Map<String, ExpandableBinaryDictionary> subDicts = new HashMap<>();
        for (final String subDictType : request.mSubDictTypes) {
            if (!isMain && Dictionary.TYPE_CONTACTS.equals(subDictType)) continue;
            subDicts.put(subDictType, reusableOrNewSubDict(request, existing, subDictType,
                    locale));
        }
        final DictionaryGroup group =
                new DictionaryGroup(locale, mainDict, request.mAccount, subDicts);
        if (mainDict != null) {
            group.mLoadState = LoadState.LOADED;
        }
        return group;
    }

    @Nullable
    private static Dictionary reusableMainDict(final GroupRequest request,
            @Nullable final DictionaryGroup existing) {
        if (request.mForceReloadMainDictionary || existing == null
                || !existing.hasDict(Dictionary.TYPE_MAIN, request.mAccount)) {
            return null;
        }
        return existing.getDict(Dictionary.TYPE_MAIN);
    }

    @Nullable
    private static ExpandableBinaryDictionary reusableOrNewSubDict(final GroupRequest request,
            @Nullable final DictionaryGroup existing, final String subDictType,
            final Locale locale) {
        if (existing != null && existing.hasDict(subDictType, request.mAccount)) {
            return existing.getSubDict(subDictType);
        }
        return getSubDict(subDictType, request.mContext, locale, null /* dictFile */,
                request.mDictNamePrefix, request.mAccount);
    }

    /** Closes every dictionary of the old groups that no new group reuses. */
    private static void closeReleasedDictionaries(final List<DictionaryGroup> oldGroups,
            final List<DictionaryGroup> newGroups) {
        for (final DictionaryGroup oldGroup : oldGroups) {
            for (final String dictType : ALL_DICTIONARY_TYPES) {
                final Dictionary dict = oldGroup.getDict(dictType);
                if (dict == null || isReused(newGroups, dictType, dict)) continue;
                oldGroup.closeDict(dictType);
            }
        }
    }

    private static boolean isReused(final List<DictionaryGroup> groups, final String dictType,
            final Dictionary dict) {
        for (final DictionaryGroup group : groups) {
            if (group.getDict(dictType) == dict) {
                return true;
            }
        }
        return false;
    }

    private void asyncReloadUninitializedMainDictionaries(final Context context,
            final DictionaryInitializationListener listener) {
        final CountDownLatch latchForWaitingLoadingMainDictionary = new CountDownLatch(1);
        mLatchForWaitingLoadingMainDictionaries = latchForWaitingLoadingMainDictionary;
        ExecutorUtils.getBackgroundExecutor(ExecutorUtils.KEYBOARD).execute(new Runnable() {
            @Override
            public void run() {
                doReloadUninitializedMainDictionaries(
                        context, listener, latchForWaitingLoadingMainDictionary);
            }
        });
    }

    void doReloadUninitializedMainDictionaries(final Context context,
            final DictionaryInitializationListener listener,
            final CountDownLatch latchForWaitingLoadingMainDictionary) {
        try {
            for (final DictionaryGroup group : mDictionaryGroups) {
                if (group.mLocale == null || hasInitializedMainDictionary(group)) continue;
                loadMainDictionary(context, group);
            }
            if (listener != null) {
                listener.onUpdateMainDictionaryAvailability(
                        hasAtLeastOneInitializedMainDictionary());
            }
        } finally {
            latchForWaitingLoadingMainDictionary.countDown();
        }
    }

    private void loadMainDictionary(final Context context, final DictionaryGroup group) {
        final Dictionary mainDict;
        try {
            mainDict = DictionaryFactory.createMainDictionaryFromManager(context, group.mLocale);
        } catch (final RuntimeException e) {
            group.mLoadState = LoadState.FAILED;
            mDiagnostics.recordFailure(MultilingualEvent.DICTIONARY_FAILED, group.mLocale, e);
            Log.w(TAG, "Main dictionary failed to load for " + group.mLocale, e);
            return;
        }
        synchronized (mLock) {
            if (!mDictionaryGroups.contains(group)) {
                // Dictionary facilitator has been reset for other locales.
                mainDict.close();
                return;
            }
            group.setMainDict(mainDict);
        }
        recordMainDictionaryOutcome(group, mainDict.isInitialized());
    }

    private void recordMainDictionaryOutcome(final DictionaryGroup group,
            final boolean initialized) {
        if (!initialized) {
            group.mLoadState = LoadState.FAILED;
            mDiagnostics.record(MultilingualEvent.DICTIONARY_FAILED, group.mLocale);
            return;
        }
        group.mLoadState = LoadState.LOADED;
        mDiagnostics.record(MultilingualEvent.DICTIONARY_LOADED, group.mLocale);
    }

    @UsedForTesting
    public void resetDictionariesForTesting(final Context context, final Locale locale,
            final ArrayList<String> dictionaryTypes, final HashMap<String, File> dictionaryFiles,
            final Map<String, Map<String, String>> additionalDictAttributes,
            @Nullable final String account) {
        Dictionary mainDictionary = null;
        final Map<String, ExpandableBinaryDictionary> subDicts = new HashMap<>();

        for (final String dictType : dictionaryTypes) {
            if (dictType.equals(Dictionary.TYPE_MAIN)) {
                mainDictionary = DictionaryFactory.createMainDictionaryFromManager(context,
                        locale);
            } else {
                final File dictFile = dictionaryFiles.get(dictType);
                final ExpandableBinaryDictionary dict = getSubDict(
                        dictType, context, locale, dictFile, "" /* dictNamePrefix */, account);
                if (additionalDictAttributes.containsKey(dictType)) {
                    dict.clearAndFlushDictionaryWithAdditionalAttributes(
                            additionalDictAttributes.get(dictType));
                }
                if (dict == null) {
                    throw new RuntimeException("Unknown dictionary type: " + dictType);
                }
                dict.reloadDictionaryIfRequired();
                dict.waitAllTasksForTests();
                subDicts.put(dictType, dict);
            }
        }
        mDictionaryGroups = Collections.singletonList(
                new DictionaryGroup(locale, mainDictionary, account, subDicts));
    }

    public void closeDictionaries() {
        final List<DictionaryGroup> groupsToClose;
        synchronized (mLock) {
            groupsToClose = mDictionaryGroups;
            mDictionaryGroups = Collections.singletonList(new DictionaryGroup());
        }
        for (final DictionaryGroup group : groupsToClose) {
            for (final String dictType : ALL_DICTIONARY_TYPES) {
                group.closeDict(dictType);
            }
        }
    }

    @UsedForTesting
    public ExpandableBinaryDictionary getSubDictForTesting(final String dictName) {
        return mainGroup().getSubDict(dictName);
    }

    @UsedForTesting
    Locale getPreferredLocaleForTesting() {
        return preferredGroup().mLocale;
    }

    private static boolean hasInitializedMainDictionary(final DictionaryGroup group) {
        final Dictionary mainDict = group.getDict(Dictionary.TYPE_MAIN);
        return mainDict != null && mainDict.isInitialized();
    }

    // The main dictionaries are loaded asynchronously.  Don't cache the return value
    // of these methods.
    // Reflects the main language only: it drives the stock "dictionary available" state.
    public boolean hasAtLeastOneInitializedMainDictionary() {
        return hasInitializedMainDictionary(mainGroup());
    }

    public boolean hasAtLeastOneUninitializedMainDictionary() {
        for (final DictionaryGroup group : mDictionaryGroups) {
            if (!hasInitializedMainDictionary(group)) {
                return true;
            }
        }
        return false;
    }

    public void waitForLoadingMainDictionaries(final long timeout, final TimeUnit unit)
            throws InterruptedException {
        mLatchForWaitingLoadingMainDictionaries.await(timeout, unit);
    }

    @UsedForTesting
    public void waitForLoadingDictionariesForTesting(final long timeout, final TimeUnit unit)
            throws InterruptedException {
        waitForLoadingMainDictionaries(timeout, unit);
        for (final DictionaryGroup group : mDictionaryGroups) {
            for (final ExpandableBinaryDictionary dict : group.mSubDictMap.values()) {
                dict.waitAllTasksForTests();
            }
        }
    }

    public void addToUserHistory(final String suggestion, final boolean wasAutoCapitalized,
            @Nonnull final NgramContext ngramContext, final long timeStampInSeconds,
            final boolean blockPotentiallyOffensive) {
        // Update the spelling cache before learning. Words that are not yet added to user history
        // and appear in no other language model are not considered valid.
        putWordIntoValidSpellingWordCache("addToUserHistory", suggestion);

        final String[] words = suggestion.split(Constants.WORD_SEPARATOR);
        if (mDictionaryGroups.size() > 1 && words.length == 1) {
            updateConfidences(suggestion, wasAutoCapitalized);
        }
        final DictionaryGroup learningGroup = preferredGroup();
        NgramContext ngramContextForCurrentWord = ngramContext;
        for (int i = 0; i < words.length; i++) {
            final String currentWord = words[i];
            final boolean wasCurrentWordAutoCapitalized = (i == 0) ? wasAutoCapitalized : false;
            addWordToUserHistory(learningGroup, ngramContextForCurrentWord, currentWord,
                    wasCurrentWordAutoCapitalized, (int) timeStampInSeconds,
                    blockPotentiallyOffensive);
            ngramContextForCurrentWord =
                    ngramContextForCurrentWord.getNextNgramContext(new WordInfo(currentWord));
        }
    }

    /** Raises the confidence of every language that knows the word and lowers the rest. */
    private void updateConfidences(final String word, final boolean wasAutoCapitalized) {
        final DictionaryGroup before = preferredGroup();
        for (final DictionaryGroup group : mDictionaryGroups) {
            if (isKnownWord(group, word, wasAutoCapitalized)) {
                group.mConfidence.recordKnownWord();
                continue;
            }
            group.mConfidence.recordUnknownWord();
        }
        final DictionaryGroup after = preferredGroup();
        if (after != before) {
            mDiagnostics.record(MultilingualEvent.PREFERRED_CHANGED, after.mLocale);
        }
    }

    private static boolean isKnownWord(final DictionaryGroup group, final String word,
            final boolean wasAutoCapitalized) {
        if (isValidWordInGroup(group, word, CONFIDENCE_DICTIONARY_TYPES)) {
            return true;
        }
        if (!wasAutoCapitalized || word.isEmpty()) {
            return false;
        }
        return isValidWordInGroup(group, decapitalize(word, group.mLocale),
                CONFIDENCE_DICTIONARY_TYPES);
    }

    // Lower-cases only the first code point: auto-capitalization does not touch the others.
    private static String decapitalize(final String word, final Locale locale) {
        final int firstEnd = word.offsetByCodePoints(0, 1);
        return word.substring(0, firstEnd).toLowerCase(locale) + word.substring(firstEnd);
    }

    /** The group with the highest confidence; the main group wins ties. */
    private DictionaryGroup preferredGroup() {
        final List<DictionaryGroup> groups = mDictionaryGroups;
        DictionaryGroup best = groups.get(0);
        for (final DictionaryGroup group : groups) {
            if (group.mConfidence.value() > best.mConfidence.value()) {
                best = group;
            }
        }
        return best;
    }

    private void putWordIntoValidSpellingWordCache(
            @Nonnull final String caller,
            @Nonnull final String originalWord) {
        if (mValidSpellingWordWriteCache == null) {
            return;
        }

        final String lowerCaseWord = originalWord.toLowerCase(getLocale());
        final boolean lowerCaseValid = isValidSpellingWord(lowerCaseWord);
        mValidSpellingWordWriteCache.put(lowerCaseWord, lowerCaseValid);

        final String capitalWord =
                StringUtils.capitalizeFirstAndDowncaseRest(originalWord, getLocale());
        final boolean capitalValid;
        if (lowerCaseValid) {
            // The lower case form of the word is valid, so the upper case must be valid.
            capitalValid = true;
        } else {
            capitalValid = isValidSpellingWord(capitalWord);
        }
        mValidSpellingWordWriteCache.put(capitalWord, capitalValid);
    }

    private void addWordToUserHistory(final DictionaryGroup dictionaryGroup,
            final NgramContext ngramContext, final String word, final boolean wasAutoCapitalized,
            final int timeStampInSeconds, final boolean blockPotentiallyOffensive) {
        final ExpandableBinaryDictionary userHistoryDictionary =
                dictionaryGroup.getSubDict(Dictionary.TYPE_USER_HISTORY);
        if (userHistoryDictionary == null
                || !userHistoryDictionary.mLocale.equals(dictionaryGroup.mLocale)) {
            return;
        }
        final int maxFreq = getFrequency(word, dictionaryGroup);
        if (maxFreq == 0 && blockPotentiallyOffensive) {
            return;
        }
        final String secondWord =
                wordFormToLearn(dictionaryGroup, word, wasAutoCapitalized, maxFreq);
        // We demote unrecognized words (frequency < 0, below) by specifying them as "invalid".
        // We don't add words with 0-frequency (assuming they would be profanity etc.).
        final boolean isValid = maxFreq > 0;
        UserHistoryDictionary.addToDictionary(userHistoryDictionary, ngramContext, secondWord,
                isValid, timeStampInSeconds);
    }

    private static String wordFormToLearn(final DictionaryGroup dictionaryGroup,
            final String word, final boolean wasAutoCapitalized, final int maxFreq) {
        final String lowerCasedWord = word.toLowerCase(dictionaryGroup.mLocale);
        if (wasAutoCapitalized) {
            if (isValidWordInGroup(dictionaryGroup, word, ALL_DICTIONARY_TYPES)
                    && !isValidWordInGroup(dictionaryGroup, lowerCasedWord,
                            ALL_DICTIONARY_TYPES)) {
                // If the word was auto-capitalized and exists only as a capitalized word in the
                // dictionary, then we must not downcase it before registering it. For example,
                // the name of the contacts in start-of-sentence position would come here with the
                // wasAutoCapitalized flag: if we downcase it, we'd register a lower-case version
                // of that contact's name which would end up popping in suggestions.
                return word;
            }
            // If however the word is not in the dictionary, or exists as a lower-case word
            // only, then we consider that was a lower-case word that had been auto-capitalized.
            return lowerCasedWord;
        }
        // HACK: We'd like to avoid adding the capitalized form of common words to the User
        // History dictionary in order to avoid suggesting them until the dictionary
        // consolidation is done.
        // TODO: Remove this hack when ready.
        final int lowerCaseFreqInMainDict = dictionaryGroup.hasDict(Dictionary.TYPE_MAIN,
                null /* account */) ?
                dictionaryGroup.getDict(Dictionary.TYPE_MAIN).getFrequency(lowerCasedWord) :
                Dictionary.NOT_A_PROBABILITY;
        if (maxFreq < lowerCaseFreqInMainDict
                && lowerCaseFreqInMainDict >= CAPITALIZED_FORM_MAX_PROBABILITY_FOR_INSERT) {
            // Use lower cased word as the word can be a distracter of the popular word.
            return lowerCasedWord;
        }
        return word;
    }

    private void removeWord(final String dictName, final String word) {
        for (final DictionaryGroup group : mDictionaryGroups) {
            final ExpandableBinaryDictionary dictionary = group.getSubDict(dictName);
            if (dictionary != null) {
                dictionary.removeUnigramEntryDynamically(word);
            }
        }
    }

    @Override
    public void unlearnFromUserHistory(final String word,
            @Nonnull final NgramContext ngramContext, final long timeStampInSeconds,
            final int eventType) {
        // TODO: Decide whether or not to remove the word on EVENT_BACKSPACE.
        if (eventType != Constants.EVENT_BACKSPACE) {
            removeWord(Dictionary.TYPE_USER_HISTORY, word);
        }

        // Update the spelling cache after unlearning. Words that are removed from user history
        // and appear in no other language model are not considered valid.
        putWordIntoValidSpellingWordCache("unlearnFromUserHistory", word.toLowerCase());
    }

    // TODO: Revise the way to fusion suggestion results.
    @Override
    @Nonnull public SuggestionResults getSuggestionResults(ComposedData composedData,
            NgramContext ngramContext, @Nonnull final Keyboard keyboard,
            SettingsValuesForSuggestion settingsValuesForSuggestion, int sessionId,
            int inputStyle) {
        final long start = SystemClock.uptimeMillis();
        final List<DictionaryGroup> groups = mDictionaryGroups;
        final SuggestionQuery query = new SuggestionQuery(composedData, ngramContext,
                keyboard.getProximityInfo().getNativeProximityInfo(),
                settingsValuesForSuggestion, sessionId, groups.size() > 1);
        final SuggestionResults suggestionResults = new SuggestionResults(
                SuggestedWords.MAX_SUGGESTIONS, ngramContext.isBeginningOfSentenceContext(),
                false /* firstSuggestionExceedsConfidenceThreshold */);
        for (final DictionaryGroup group : groups) {
            addGroupSuggestions(group, query, suggestionResults);
        }
        mDiagnostics.recordSuggestionMs(SystemClock.uptimeMillis() - start);
        return suggestionResults;
    }

    private static void addGroupSuggestions(final DictionaryGroup group,
            final SuggestionQuery query, final SuggestionResults suggestionResults) {
        final float weightForLocale = languageWeight(group, query);
        for (final String dictType : ALL_DICTIONARY_TYPES) {
            final Dictionary dictionary = group.getDict(dictType);
            if (null == dictionary) continue;
            final ArrayList<SuggestedWordInfo> dictionarySuggestions =
                    dictionary.getSuggestions(query.mComposedData, query.mNgramContext,
                            query.mProximityInfoHandle, query.mSettingsValuesForSuggestion,
                            query.mSessionId, weightForLocale,
                            query.mWeightOfLangModelVsSpatialModel);
            if (null == dictionarySuggestions) continue;
            suggestionResults.addAll(dictionarySuggestions);
            if (null != suggestionResults.mRawSuggestions) {
                suggestionResults.mRawSuggestions.addAll(dictionarySuggestions);
            }
        }
    }

    // A single language keeps the stock weight so its ranking is unchanged.
    private static float languageWeight(final DictionaryGroup group,
            final SuggestionQuery query) {
        final float baseWeight = query.mComposedData.mIsBatchMode
                ? group.mWeightForGesturingInLocale
                : group.mWeightForTypingInLocale;
        if (!query.mApplyLanguageWeight) {
            return baseWeight;
        }
        return baseWeight * group.mConfidence.weightFactor();
    }

    public boolean isValidSpellingWord(final String word) {
        if (mValidSpellingWordReadCache != null) {
            final Boolean cachedValue = mValidSpellingWordReadCache.get(word);
            if (cachedValue != null) {
                return cachedValue;
            }
        }

        return isValidWord(word, ALL_DICTIONARY_TYPES);
    }

    /**
     * True if any active language knows the word. Autocorrection checks this before replacing
     * a typed word, so a word valid in any language is never corrected.
     */
    public boolean isValidSuggestionWord(final String word) {
        return isValidWord(word, ALL_DICTIONARY_TYPES);
    }

    private boolean isValidWord(final String word, final String[] dictionariesToCheck) {
        if (TextUtils.isEmpty(word)) {
            return false;
        }
        for (final DictionaryGroup group : mDictionaryGroups) {
            if (isValidWordInGroup(group, word, dictionariesToCheck)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isValidWordInGroup(final DictionaryGroup group, final String word,
            final String[] dictionariesToCheck) {
        if (TextUtils.isEmpty(word) || group.mLocale == null) {
            return false;
        }
        for (final String dictType : dictionariesToCheck) {
            final Dictionary dictionary = group.getDict(dictType);
            // Ideally the passed map would come out of a {@link java.util.concurrent.Future} and
            // would be immutable once it's finished initializing, but concretely a null test is
            // probably good enough for the time being.
            if (null == dictionary) continue;
            if (dictionary.isValidWord(word)) {
                return true;
            }
        }
        return false;
    }

    private static int getFrequency(final String word, final DictionaryGroup group) {
        if (TextUtils.isEmpty(word)) {
            return Dictionary.NOT_A_PROBABILITY;
        }
        int maxFreq = Dictionary.NOT_A_PROBABILITY;
        for (final String dictType : ALL_DICTIONARY_TYPES) {
            final Dictionary dictionary = group.getDict(dictType);
            if (dictionary == null) continue;
            final int tempFreq = dictionary.getFrequency(word);
            if (tempFreq >= maxFreq) {
                maxFreq = tempFreq;
            }
        }
        return maxFreq;
    }

    private boolean clearSubDictionary(final String dictName) {
        boolean cleared = false;
        for (final DictionaryGroup group : mDictionaryGroups) {
            final ExpandableBinaryDictionary dictionary = group.getSubDict(dictName);
            if (dictionary == null) continue;
            dictionary.clear();
            cleared = true;
        }
        return cleared;
    }

    @Override
    public boolean clearUserHistoryDictionary(final Context context) {
        return clearSubDictionary(Dictionary.TYPE_USER_HISTORY);
    }

    @Override
    public void dumpDictionaryForDebug(final String dictName) {
        boolean dumped = false;
        for (final DictionaryGroup group : mDictionaryGroups) {
            final ExpandableBinaryDictionary dictToDump = group.getSubDict(dictName);
            if (dictToDump == null) continue;
            dictToDump.dumpAllWordsForDebug();
            dumped = true;
        }
        if (!dumped) {
            Log.e(TAG, "Cannot dump " + dictName + ". "
                    + "The dictionary is not being used for suggestion or cannot be dumped.");
        }
    }

    @Override
    @Nonnull public List<DictionaryStats> getDictionaryStats(final Context context) {
        final ArrayList<DictionaryStats> statsOfEnabledSubDicts = new ArrayList<>();
        for (final DictionaryGroup group : mDictionaryGroups) {
            for (final String dictType : DYNAMIC_DICTIONARY_TYPES) {
                final ExpandableBinaryDictionary dictionary = group.getSubDict(dictType);
                if (dictionary == null) continue;
                statsOfEnabledSubDicts.add(dictionary.getDictionaryStats());
            }
        }
        return statsOfEnabledSubDicts;
    }

    @Override
    public String dump(final Context context) {
        final List<LanguageState> states = new ArrayList<>();
        for (final DictionaryGroup group : mDictionaryGroups) {
            if (group.mLocale == null) continue;
            states.add(group.toState());
        }
        return mDiagnostics.dump(states);
    }
}
