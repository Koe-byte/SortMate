package com.example.sortmate

import android.app.Application

class SortMateApplication : Application() {

    lateinit var container: com.example.sortmate.di.AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = com.example.sortmate.di.AppContainer(this)
        container.start()
    }
}
