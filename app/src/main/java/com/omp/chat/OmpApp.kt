package com.omp.chat

import android.app.Application
import android.util.Log
import com.omp.chat.data.ChatRepository
import com.omp.chat.data.FileManagerRepository
import com.omp.chat.data.PcProfileStore
import com.omp.chat.data.SettingsStore
import com.omp.chat.data.TaskStore
import com.omp.chat.util.CrashReporter
import com.omp.chat.util.Notifier

class OmpApp : Application() {

    lateinit var settings: SettingsStore
        private set
    lateinit var pcProfiles: PcProfileStore
        private set
    lateinit var tasks: TaskStore
        private set
    lateinit var repository: ChatRepository
        private set
    lateinit var files: FileManagerRepository
        private set

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        instance = this
        settings = SettingsStore(this)
        pcProfiles = PcProfileStore(this)
        tasks = TaskStore(this)
        repository = ChatRepository(settings)
        files = FileManagerRepository(settings, this)
        try {
            Notifier.createChannel(this)
        } catch (error: Throwable) {
            // A missing/invalid notification channel must never block startup.
            Log.w("OmpApp", "Notification channel setup failed", error)
        }
    }

    companion object {
        lateinit var instance: OmpApp
            private set
    }
}
