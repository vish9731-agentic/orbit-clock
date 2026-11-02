package dev.orbit.clock.phone

import dev.orbit.clock.core.ClockActivity
import android.content.Intent

class MainActivity : ClockActivity() {
    override val watch = false
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent) }
    override fun onResume() {
        super.onResume()
        when (intent.action) {
            AlarmWidget.BEDTIME -> { intent.action = Intent.ACTION_MAIN; openWidgetBedtime() }
            AlarmWidget.EDIT, AlarmWidget.ADD -> {
                val id = if (intent.action == AlarmWidget.EDIT) intent.getStringExtra("id") else null
                intent.action = Intent.ACTION_MAIN
                openWidgetAlarm(id)
            }
        }
    }
    override fun onPause() {
        super.onPause()
        // Includes sleep-goal changes, which do not write an alarm record.
        AlarmWidget.refresh(this)
    }
}

