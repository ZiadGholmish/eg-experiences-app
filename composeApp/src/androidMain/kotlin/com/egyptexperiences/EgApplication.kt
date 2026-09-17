package com.egyptexperiences

import android.app.Application
import com.egyptexperiences.core.datastore.APP_PREFERENCES_FILE
import com.egyptexperiences.di.initKoin
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger

class EgApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin(
            baseUrl = BuildConfig.BASE_URL,
            isDebug = BuildConfig.DEBUG,
            // filesDir is app-private and survives updates; DataStore appends
            // its own file name.
            preferencesPath = { filesDir.resolve(APP_PREFERENCES_FILE).absolutePath },
        ) {
            androidContext(this@EgApplication)
            androidLogger()
        }
    }
}
