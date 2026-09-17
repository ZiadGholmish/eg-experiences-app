package com.egyptexperiences.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import okio.Path.Companion.toPath

/**
 * The preferences file name. Both entry points must use this one constant: two
 * platforms pointing at two different file names is a bug that only shows up as
 * "my settings reset themselves".
 */
const val APP_PREFERENCES_FILE = "eg_experiences.preferences_pb"

/**
 * Builds the preferences store from a path the caller supplies.
 *
 * The path cannot be computed here: Android needs a `Context` and iOS needs
 * `NSFileManager`. The app module knows both, this module knows neither.
 */
fun createPreferencesDataStore(producePath: () -> String): DataStore<Preferences> =
    PreferenceDataStoreFactory.createWithPath(produceFile = { producePath().toPath() })
