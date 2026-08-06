package com.aniko.app

import android.app.Application
import com.aniko.app.di.initKoinOnce
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger

class AnixApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        initKoinOnce {
            androidLogger()
            androidContext(this@AnixApplication)
        }
    }
}
