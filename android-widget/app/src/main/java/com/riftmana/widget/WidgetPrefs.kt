package com.riftmana.widget

import android.content.Context
import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

data class CachedValue(
    val value: Double,
    val rawValue: String,
    val updatedAtIso: String,
    val fetchedAtMillis: Long,
    val previousValue: Double?
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

    /** Change vs. the prior hourly reading, or null if there isn't one yet (e.g. first run). */
    fun delta(): Double? {
        val prev = previousValue ?: return null
        if (prev.isNaN() || value.isNaN()) return null
        return value - prev
    }

    /** e.g. "+$3.20 - " (with trailing separator) ready to prefix the subtitle, or null to hide it. */
    fun deltaLabel(): String? {
        val d = delta() ?: return null
        val formatter = NumberFormat.getCurrencyInstance(Locale.US)
        val sign = if (d >= 0) "+" else "-"
        return "$sign${formatter.format(abs(d))}  -  "
    }
}

object WidgetPrefs {
    private const val PREFS = "riftmana_widget_prefs"
    private const val KEY_VALUE = "value"
    private const val KEY_RAW = "raw"
    private const val KEY_UPDATED_AT = "updated_at"
    private const val KEY_FETCHED_AT = "fetched_at"
    private const val KEY_PREV_VALUE = "prev_value"

    /**
     * Saves the latest reading. If [updatedAt] differs from whatever was already cached, that's
     * a genuinely new hourly reading, so the old value shifts into "previous" for delta
     * purposes; if it's the same [updatedAt] (a background poll landing between hourly scrapes),
     * "previous" is left untouched so the delta doesn't reset to zero every 30 minutes.
     */
    fun save(context: Context, value: Double, raw: String, updatedAt: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existingUpdatedAt = prefs.getString(KEY_UPDATED_AT, null)
        val editor = prefs.edit()

        if (existingUpdatedAt != null && existingUpdatedAt != updatedAt && prefs.contains(KEY_VALUE)) {
            editor.putFloat(KEY_PREV_VALUE, prefs.getFloat(KEY_VALUE, Float.NaN))
        }

        editor.putFloat(KEY_VALUE, value.toFloat())
            .putString(KEY_RAW, raw)
            .putString(KEY_UPDATED_AT, updatedAt)
            .putLong(KEY_FETCHED_AT, System.currentTimeMillis())
            .apply()
    }

    fun load(context: Context): CachedValue? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_VALUE)) return null
        val previousValue = if (prefs.contains(KEY_PREV_VALUE)) {
            prefs.getFloat(KEY_PREV_VALUE, Float.NaN).toDouble()
        } else {
            null
        }
        return CachedValue(
            value = prefs.getFloat(KEY_VALUE, Float.NaN).toDouble(),
            rawValue = prefs.getString(KEY_RAW, "") ?: "",
            updatedAtIso = prefs.getString(KEY_UPDATED_AT, "") ?: "",
            fetchedAtMillis = prefs.getLong(KEY_FETCHED_AT, System.currentTimeMillis()),
            previousValue = previousValue
        )
    }
}
