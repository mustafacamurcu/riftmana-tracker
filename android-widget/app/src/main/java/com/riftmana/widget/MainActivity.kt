package com.riftmana.widget

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : Activity() {

    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        }

        val statusText = findViewById<TextView>(R.id.status_text)
        val refreshButton = findViewById<Button>(R.id.refresh_button)

        fun render() {
            val cached = WidgetPrefs.load(this)
            statusText.text = if (cached != null) {
                "${cached.displayValue()}\n${cached.relativeUpdatedAt()}"
            } else {
                "No data yet.\nAdd the widget to your home screen, or tap Refresh."
            }
        }

        render()
        refreshButton.setOnClickListener {
            ValueWidgetProvider.requestImmediateRefresh(this)
            statusText.text = "Refreshing..."
            it.postDelayed({ render() }, 3000)
        }

        checkForAppUpdate()
    }

    private fun checkForAppUpdate() {
        scope.launch {
            val update = withContext(Dispatchers.IO) { UpdateChecker.checkForUpdate(applicationContext) }
            if (update != null) {
                showUpdateDialog(update)
            }
        }
    }

    private fun showUpdateDialog(info: UpdateChecker.UpdateInfo) {
        val message = if (info.notes.isBlank()) {
            "Version ${info.versionName} is available."
        } else {
            "Version ${info.versionName} is available.\n\n${info.notes}"
        }
        AlertDialog.Builder(this)
            .setTitle("Update available")
            .setMessage(message)
            .setPositiveButton("Update") { _, _ -> startUpdate(info) }
            .setNegativeButton("Later", null)
            .show()
    }

    private fun startUpdate(info: UpdateChecker.UpdateInfo) {
        if (!UpdateChecker.canInstall(this)) {
            AlertDialog.Builder(this)
                .setTitle("Allow installs")
                .setMessage(
                    "Android needs permission to install updates from this app. " +
                        "Tap Continue, then enable \"Allow from this source\", and come back."
                )
                .setPositiveButton("Continue") { _, _ ->
                    val intent = Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                UpdateChecker.downloadAndInstall(applicationContext, info)
            }
            if (!ok) {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Update failed")
                    .setMessage("Couldn't download the update. Check your connection and try again.")
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }
}
