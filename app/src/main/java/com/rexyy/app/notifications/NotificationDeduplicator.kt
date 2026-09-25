package com.rexyy.app.notifications

import java.util.concurrent.ConcurrentHashMap

object NotificationDeduplicator {

    // Global in-memory cache of announced notifications: key -> timestamp (ms)
    // Survives service rebinding and reconnect cycles within the process
    private val announcementCache = ConcurrentHashMap<String, Long>()

    // Cooldown duration to avoid repeating the exact same notification
    private const val COOLDOWN_MILLIS = 20_000L

    // Maximum allowed age for a notification to be announced (30 seconds)
    private const val MAX_NOTIFICATION_AGE_MILLIS = 30_000L

    /**
     * Checks if a notification should be announced or dropped as duplicate / stale.
     *
     * @param eventKey Unique key derived from package, title, and text
     * @param postTime System post time of the notification
     * @param listenerConnectedAt Timestamp when listener was connected (0 if unknown)
     * @param now Current epoch timestamp
     * @return true if notification is fresh and should be announced, false if duplicate or stale
     */
    fun shouldAnnounce(
        eventKey: String,
        postTime: Long,
        listenerConnectedAt: Long,
        now: Long = System.currentTimeMillis()
    ): Boolean {
        // 1. Drop stale notifications posted prior to listener connection (e.g. sitting in shade before reboot)
        if (listenerConnectedAt > 0 && postTime > 0 && postTime < (listenerConnectedAt - 5_000L)) {
            return false
        }

        // 2. Drop notifications older than max allowed age
        if (postTime > 0 && (now - postTime) > MAX_NOTIFICATION_AGE_MILLIS) {
            return false
        }

        // 3. Drop duplicate notifications received within cooldown window
        val lastAnnounced = announcementCache[eventKey] ?: 0L
        if ((now - lastAnnounced) < COOLDOWN_MILLIS) {
            return false
        }

        // Clean up old entries if cache exceeds 200 items
        if (announcementCache.size > 200) {
            val cutoff = now - 60_000L
            announcementCache.entries.removeIf { it.value < cutoff }
        }

        return true
    }

    /**
     * Records a successful announcement to prevent duplicates.
     */
    fun recordAnnouncement(eventKey: String, timestamp: Long = System.currentTimeMillis()) {
        announcementCache[eventKey] = timestamp
    }

    /**
     * Clears cache for testing.
     */
    fun clearCacheForTesting() {
        announcementCache.clear()
    }
}
