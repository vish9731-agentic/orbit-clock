package dev.orbit.clock.phone

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch

/** Settings apply to both dynamic sizes; the launcher exposes this through Edit widget. */
class WidgetSettingsActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("widget", MODE_PRIVATE) }
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    override fun onCreate(state: Bundle?) {
        DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(state)
        widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        setResult(Activity.RESULT_CANCELED)
        val density = resources.displayMetrics.density
        fun dp(n: Int) = (n * density).toInt()
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(32), dp(24), dp(32))
        }
        body.addView(TextView(this).apply { text = "Dynamic widgets"; textSize = 28f })
        body.addView(TextView(this).apply {
            text = "Square shows one item at a time. Wide keeps the clock on the left. Both stay transparent and show only the time until a reminder is near."
            textSize = 16f; setPadding(0, dp(20), 0, dp(20))
        })
        val choices = intArrayOf(10, 15, 30, 45, 60, 90)
        fun window(key: String, title: String) {
            val button = MaterialButton(this).apply {
                isAllCaps = false
                fun update() { text = "$title · ${WidgetTiming.window(prefs.getInt(key, 15))} min" }
                update()
                setOnClickListener {
                    MaterialAlertDialogBuilder(this@WidgetSettingsActivity).setTitle(title)
                        .setSingleChoiceItems(choices.map { "$it minutes" }.toTypedArray(), choices.indexOf(WidgetTiming.window(prefs.getInt(key, 15)))) { dialog, index ->
                            prefs.edit().putInt(key, choices[index]).apply()
                            AlarmWidget.refresh(this@WidgetSettingsActivity)
                            update(); dialog.dismiss()
                        }.setNegativeButton("Cancel", null).show()
                }
            }
            body.addView(button, LinearLayout.LayoutParams(-1, dp(64)))
        }
        window("alarmWindow", "Before alarms")
        window("bedtimeWindow", "Before bedtime")
        body.addView(MaterialSwitch(this).apply {
            text = "Bedtime reminders"; isChecked = prefs.getBoolean("bedtimeEnabled", true)
            setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean("bedtimeEnabled", checked).apply(); AlarmWidget.refresh(this@WidgetSettingsActivity) }
        }, LinearLayout.LayoutParams(-1, dp(64)))
        body.addView(TextView(this).apply {
            text = "Bedtime follows your Wake up alarm and sleep goal in Orbit Clock. “It’s bedtime” stays for 15 minutes. Nearby alarms and snoozes take priority. These settings apply to all Orbit widgets.\n\nAndroid may delay visual changes while the phone is asleep. Transparent clock text is white with a shadow; choose a wallpaper with enough contrast."
            textSize = 14f; setPadding(0, dp(16), 0, dp(16))
        })
        fun action(title: String, action: String) {
            body.addView(MaterialButton(this).apply {
                text = title; isAllCaps = false
                setOnClickListener { startActivity(Intent(this@WidgetSettingsActivity, MainActivity::class.java).setAction(action)) }
            }, LinearLayout.LayoutParams(-1, dp(56)))
        }
        action("Add alarm", AlarmWidget.ADD)
        action("Set bedtime schedule", AlarmWidget.BEDTIME)
        body.addView(MaterialButton(this).apply {
            text = "Done"; isAllCaps = false
            setOnClickListener {
                AlarmWidget.refresh(this@WidgetSettingsActivity)
                setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
                finish()
            }
        }, LinearLayout.LayoutParams(-1, dp(56)))
        setContentView(ScrollView(this).apply { addView(body) })
    }
}
