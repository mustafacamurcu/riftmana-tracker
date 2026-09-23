package com.riftmana.widget

import android.content.Context
import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

data class CachedValue(
    val value: Double,
    val rawValue: String,
    val updatedAtIso: String,
    val fetchedAtMillis: Long
) {
    fun displayValue(): String {
        if (value.isNaN()) return rawValue.ifBlank { "--" }
        val formatter = NumberFormat.getCurrencyInstance(Locale.US)
        return formatter.format(value)
    }

    fun relativeUpdatedAt(): String {
        val diffMs = System.currentTimeMillis() - fetchedAtMillis
        val mins = TimeUnit.MILLISECONDS.toMinutes(diffMs)
        return when {
            mins < 1 -> "Updated just now"
            mins < 60 -> "Updated ${mins}m ago"
            else -> "Updated ${TimeUnit.MILLISECONDS.toHours(diffMs)}h ago"
        }
    }
}

object WidgetPrefs {
    private const val PREFS = "riftmana_widget_prefs"
    private const val KEY_VALUE = "value"
    private const val KEY_RAW = "raw"
    private const val KEY_UPDATED_AT = "updated_at"
    private const val KEY_FETCHED_AT = "fetched_at"

    fun save(context: Context, value: Double, raw: String, updatedAt: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putFloat(KEY_VALUE, value.toFloat())
            .putString(KEY_RAW, raw)
            .putString(KEY_UPDATED_AT, updatedAt)
            .putLong(KEY_FETCHED_AT, System.currentTimeMillis())
            .apply()
    }

    fun load(context: Context): CachedValue? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_VALUE)) return null
        return CachedValue(
            value = prefs.getFloat(KEY_VALUE, Float.NaN).toDouble(),
            rawValue = prefs.getString(KEY_RAW, "") ?: "",
            updatedAtIso = prefs.getString(KEY_UPDATED_AT, "") ?: "",
            fetchedAtMillis = prefs.getLong(KEY_FETCHED_AT, System.currentTimeMillis())
        )
    }
}
