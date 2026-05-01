package com.picflick.app.util

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Singleton cost-control manager that periodically fetches feature flags from Firestore
 * and caches them for synchronous checks throughout the app.
 *
 * To use: set flags in Firestore at appConfig/featureFlags document, e.g.:
 *   killSnapshotListeners: true
 *   killChatListeners: true
 */
object CostControlManager {
    private val db: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var flags: Map<String, Boolean> = emptyMap()

    /** Check a cached flag synchronously (safe from any thread). */
    fun isEnabled(flagName: String): Boolean = flags[flagName] == true

    /** Start periodic refresh (call once from Application.onCreate or MainActivity). */
    fun startRefresh(periodMs: Long = 60_000L) {
        scope.launch {
            while (true) {
                refresh()
                delay(periodMs)
            }
        }
    }

    /** Force an immediate refresh (e.g. on app foreground). */
    suspend fun refresh() {
        try {
            val doc = db.collection(com.picflick.app.Constants.FeatureFlags.CONFIG_COLLECTION)
                .document(com.picflick.app.Constants.FeatureFlags.CONFIG_DOCUMENT)
                .get()
                .await()
            val data = doc.data ?: return
            flags = data.mapValues { it.value == true }
        } catch (_: Exception) {
            // Fail-open: keep previous flags on error
        }
    }
}
