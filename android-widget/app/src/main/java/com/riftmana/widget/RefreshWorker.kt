package com.riftmana.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class RefreshWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    private data class FetchedValue(val value: Double, val rawValue: String, val updatedAt: String)

    override suspend fun doWork(): Result {
        val fetched = fetchLatest()
        if (fetched != null) {
            WidgetPrefs.save(applicationContext, fetched.value, fetched.rawValue, fetched.updatedAt)
        }

        val appWidgetManager = AppWidgetManager.getInstance(applicationContext)
        val ids = appWidgetManager.getAppWidgetIds(
            ComponentName(applicationContext, ValueWidgetProvider::class.java)
        )
        for (id in ids) {
            renderWidget(applicationContext, appWidgetManager, id)
        }

        checkForAppUpdate()

        return if (fetched != null) Result.success() else Result.retry()
    }

    private fun checkForAppUpdate() {
        val update = UpdateChecker.checkForUpdate(applicationContext) ?: return
        if (UpdateChecker.canInstall(applicationContext)) {
            // Install permission was already granted in a previous session, so this can
            // go straight to the system install prompt without waiting for the app to
            // be opened. Android still requires one tap on that prompt to confirm -
            // there's no fully silent install path for a sideloaded app.
            UpdateChecker.downloadAndInstall(applicationContext, update)
        } else {
            NotificationHelper.showUpdateAvailableNotification(applicationContext, update)
        }
    }

    private fun fetchLatest(): FetchedValue? {
        return try {
            val url = URL(
                "https://mustafacamurcu.github.io/riftmana-tracker/latest.json" +
                    "?t=${System.currentTimeMillis()}"
            )
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            connection.useCaches = false
            connection.requestMethod = "GET"
            connection.inputStream.bufferedReader().use { reader ->
                val json = JSONObject(reader.readText())
                FetchedValue(
                    value = json.optDouble("total_value", Double.NaN),
                    rawValue = json.optString("total_value_raw", ""),
                    updatedAt = json.optString("updated_at", "")
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        fun renderWidget(context: Context, appWidgetManager: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_layout)
            val cached = WidgetPrefs.load(context)

            if (cached != null) {
                views.setTextViewText(R.id.widget_value, cached.displayValue())
                views.setTextViewText(R.id.widget_subtitle, cached.relativeUpdatedAt())

                val deltaLabel = cached.deltaLabel()
                val delta = cached.delta()
                if (deltaLabel != null && delta != null) {
                    views.setViewVisibility(R.id.widget_delta, View.VISIBLE)
                    views.setTextViewText(R.id.widget_delta, deltaLabel)
                    val colorRes = when {
                        delta > 0 -> R.color.delta_good
                        delta < 0 -> R.color.delta_critical
                        else -> R.color.widget_text_muted
                    }
                    views.setTextColor(R.id.widget_delta, ContextCompat.getColor(context, colorRes))
                } else {
                    views.setViewVisibility(R.id.widget_delta, View.GONE)
                }
            } else {
                views.setTextViewText(R.id.widget_value, "--")
                views.setTextViewText(R.id.widget_subtitle, "Tap to load")
                views.setViewVisibility(R.id.widget_delta, View.GONE)
            }

            val refreshIntent = Intent(context, ValueWidgetProvider::class.java).apply {
                action = ValueWidgetProvider.ACTION_REFRESH
            }
            val refreshPendingIntent = PendingIntent.getBroadcast(
                context,
                widgetId,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_refresh, refreshPendingIntent)

            val openIntent = Intent(context, MainActivity::class.java)
            val openPendingIntent = PendingIntent.getActivity(
                context,
                widgetId,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, openPendingIntent)

            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }
}
