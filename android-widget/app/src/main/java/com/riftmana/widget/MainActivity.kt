package com.riftmana.widget

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.TextView

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

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
    }
}
