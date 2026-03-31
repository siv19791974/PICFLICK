package com.picflick.app.ui.theme

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.edit

/**
 * Centralized, persisted feature flags for controlled runtime behavior.
 */
object FeatureFlags {
    private const val PREFS_NAME = "picflick_feature_flags"

    private const val KEY_FAST_REFRESH = "fast_refresh_mode"
    private const val KEY_AGGRESSIVE_RECONCILE = "aggressive_reconcile"
    private const val KEY_VERBOSE_LOGS = "verbose_logs"
    private const val KEY_DEV_ENTRY_ENABLED = "developer_entry_enabled"

    var fastRefreshMode = mutableStateOf(false)
        private set

    var aggressiveReconcile = mutableStateOf(false)
        private set

    var verboseLogs = mutableStateOf(true)
        private set

    var developerEntryEnabled = mutableStateOf(true)
        private set

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        fastRefreshMode.value = prefs.getBoolean(KEY_FAST_REFRESH, false)
        aggressiveReconcile.value = prefs.getBoolean(KEY_AGGRESSIVE_RECONCILE, false)
        verboseLogs.value = prefs.getBoolean(KEY_VERBOSE_LOGS, true)
        developerEntryEnabled.value = prefs.getBoolean(KEY_DEV_ENTRY_ENABLED, true)
    }

    fun setFastRefreshMode(context: Context, enabled: Boolean) {
        fastRefreshMode.value = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_FAST_REFRESH, enabled)
        }
    }

    fun setAggressiveReconcile(context: Context, enabled: Boolean) {
        aggressiveReconcile.value = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_AGGRESSIVE_RECONCILE, enabled)
        }
    }

    fun setVerboseLogs(context: Context, enabled: Boolean) {
        verboseLogs.value = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_VERBOSE_LOGS, enabled)
        }
    }

    fun setDeveloperEntryEnabled(context: Context, enabled: Boolean) {
        developerEntryEnabled.value = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit {
            putBoolean(KEY_DEV_ENTRY_ENABLED, enabled)
        }
    }

    fun resetDefaults(context: Context) {
        setFastRefreshMode(context, false)
        setAggressiveReconcile(context, false)
        setVerboseLogs(context, true)
        setDeveloperEntryEnabled(context, true)
    }
}
