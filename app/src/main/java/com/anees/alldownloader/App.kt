package com.anees.alldownloader

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import com.anees.alldownloader.data.Engine
import com.anees.alldownloader.data.HistoryStore
import com.anees.alldownloader.data.Settings

class App : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        Settings.init(this)
        HistoryStore.init(this)
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CHANNEL_DOWNLOADS, "Active downloads", NotificationManager.IMPORTANCE_LOW))
            nm.createNotificationChannel(NotificationChannel(CHANNEL_DONE, "Finished downloads", NotificationManager.IMPORTANCE_DEFAULT))
        }
        Engine.start(this)
    }

    override fun newImageLoader(context: PlatformContext) =
        ImageLoader.Builder(context).crossfade(true).build()

    companion object {
        const val CHANNEL_DOWNLOADS = "downloads"
        const val CHANNEL_DONE = "done"
    }
}
