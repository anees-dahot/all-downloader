package com.anees.alldownloader.work

import com.anees.alldownloader.data.Settings
import kotlinx.coroutines.delay

/**
 * Limits how many downloads run at once (Settings.parallel) and starts them oldest-first,
 * so playlist items come down in order. Waiting workers hold no notification.
 */
object DownloadGate {
    private val lock = Any()
    private var running = 0
    private val waiting = sortedSetOf(compareBy<Pair<Long, String>>({ it.first }, { it.second }))

    suspend fun acquire(order: Long, id: String) {
        val key = order to id
        synchronized(lock) { waiting.add(key) }
        try {
            while (true) {
                synchronized(lock) {
                    if (running < Settings.current.parallel.coerceAtLeast(1) && waiting.first() == key) {
                        waiting.remove(key); running++; return
                    }
                }
                delay(300)
            }
        } catch (t: Throwable) {
            synchronized(lock) { waiting.remove(key) }
            throw t
        }
    }

    fun release() = synchronized(lock) { running = (running - 1).coerceAtLeast(0) }
}
