package com.riftmana.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
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

        return if (fetched != null) Result.success() else Result.retry()
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
            } else {
                views.setTextViewText(R.id.widget_value, "--")
                views.setTextViewText(R.id.widget_subtitle, "Tap to load")
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
