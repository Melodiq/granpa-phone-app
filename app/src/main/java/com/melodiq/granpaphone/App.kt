package com.melodiq.granpaphone

import android.app.Application

class App : Application() {
    lateinit var speaker: Speaker
        private set
    lateinit var ears: Ears
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        speaker = Speaker(this)
        ears = Ears(this)
        ears.load()
    }

    companion object {
        lateinit var instance: App
            private set

        /** Главный экран сейчас на виду (тогда напоминания озвучивает он). */
        @Volatile
        var activityVisible = false
    }
}
