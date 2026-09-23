package com.riftmana.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class ValueWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_REFRESH = "com.riftmana.widget.ACTION_REFRESH"
        private const val PERIODIC_WORK_NAME = "riftmana_widget_periodic_refresh"
        private const val ONE_TIME_WORK_NAME = "riftmana_widget_manual_refresh"

        fun requestImmediateRefresh(context: Context) {
            val request = OneTimeWorkRequestBuilder<RefreshWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(ONE_TIME_WORK_NAME, ExistingWorkPolicy.REPLACE, request)
        }

        fun schedulePeriodicRefresh(context: Context) {
            // Android enforces a floor well above what updatePeriodMillis alone can
            // reliably deliver, so WorkManager drives the real refresh cadence.
            // 30 min comfortably catches each hourly write without wasted work.
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(30, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                    PERIODIC_WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request
                )
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        schedulePeriodicRefresh(context)
        requestImmediateRefresh(context)
        // Paint whatever's cached right away so the widget isn't blank while the
        // network fetch above is still in flight.
        for (id in appWidgetIds) {
            RefreshWorker.renderWidget(context, appWidgetManager, id)
        }
    }

    override fun onEnabled(context: Context) {
        schedulePeriodicRefresh(context)
    }

    override fun onDisabled(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            requestImmediateRefresh(context)
        }
    }
}
