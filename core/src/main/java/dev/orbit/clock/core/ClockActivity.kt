package dev.orbit.clock.core

import android.app.NotificationManager
import android.content.*
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.*
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.text.format.DateFormat
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.transition.TransitionManager
import androidx.transition.ChangeBounds
import com.google.android.material.transition.MaterialSharedAxis
import com.google.android.material.transition.FadeProvider
import com.google.android.material.snackbar.Snackbar
import com.google.android.gms.wearable.Wearable
import androidx.core.view.doOnLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Material 3 components on phone; the same tools adapt to round Wear OS screens. */
open class ClockActivity : AppCompatActivity() {
    open val watch = false
    private lateinit var colors: OrbitPalette
    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var scroll: ScrollView
    private var tab = "Alarms"
    private var renderedTab: String? = null
    private var navigationView: BottomNavigationView? = null
    private lateinit var titleView: TextView
    private lateinit var bodyFrame: FrameLayout
    private lateinit var syncStatusView: TextView
    private var syncButton: MaterialButton? = null
    private var addButton: FloatingActionButton? = null
    private var syncMessage: String? = null
    private var syncInProgress = false
    private var syncToken = 0
    private var pendingFeedback: String? = null
    private var syncTimeout: Runnable? = null
    private val sections = listOf("Alarms", "Clock", "Timer", "Stopwatch", "Bedtime")
    private val icons = listOf(R.drawable.ic_alarm, R.drawable.ic_clock, R.drawable.ic_timer, R.drawable.ic_stopwatch, R.drawable.ic_bedtime)
    private val handler = Handler(Looper.getMainLooper())
    private var ticker: (() -> Unit)? = null
    private var foreground = false
    private val refresh = Runnable { if (foreground && !isFinishing && !isDestroyed) render() }
    private val tick = object : Runnable {
        override fun run() { ticker?.invoke(); handler.postDelayed(this, if (tab == "Stopwatch" && prefs.getBoolean("running", false)) 16 else 1000) }
    }
    private val prefs by lazy { getSharedPreferences("tools", MODE_PRIVATE) }
    private val changes = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (foreground && tab in listOf("Alarms", "Bedtime", "Timer")) {
                handler.removeCallbacks(refresh)
                handler.postDelayed(refresh, if (OrbitMotion.enabled()) 180 else 0)
            }
        }
    }
    override fun onCreate(state: Bundle?) {
        if (watch) setTheme(R.style.Theme_OrbitClock_Ringing)
        else DynamicColors.applyToActivityIfAvailable(this)
        super.onCreate(state)
        colors = OrbitPalette(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        tab = state?.getString("tab") ?: "Alarms"
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(changes, IntentFilter(Store.CHANGED), RECEIVER_NOT_EXPORTED)
        else registerReceiver(changes, IntentFilter(Store.CHANGED))
        Sync.refresh(this)
    }
    override fun onResume() {
        super.onResume(); foreground = true; Scheduler.restore(this)
        render()
        handler.removeCallbacks(tick); handler.post(tick)
    }
    override fun onPause() { foreground = false; handler.removeCallbacks(tick); handler.removeCallbacks(refresh); super.onPause() }
    override fun onSaveInstanceState(out: Bundle) { out.putString("tab", tab); super.onSaveInstanceState(out) }
    override fun onDestroy() { syncTimeout?.let { handler.removeCallbacks(it) }; unregisterReceiver(changes); if (::bodyFrame.isInitialized) TransitionManager.endTransitions(bodyFrame); super.onDestroy() }
    private fun dp(n: Int) = (resources.displayMetrics.density * n).toInt()
    private fun column() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun space(parent: LinearLayout, height: Int = 16) { parent.addView(Space(this), LinearLayout.LayoutParams(1, dp(height))) }
    private fun text(value: String, size: Float = 16f, color: Int = colors.onSurface) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); includeFontPadding = false
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        fontFeatureSettings = "tnum"
        setLineSpacing(dp(3).toFloat(), 1f)
    }
    private fun button(label: String, tonal: Boolean = false, icon: Int? = null, action: () -> Unit) = MaterialButton(this).apply {
        text = label; isAllCaps = false; textSize = 14f; minimumHeight = dp(52); cornerRadius = dp(28)
        insetTop = 0; insetBottom = 0
        backgroundTintList = ColorStateList.valueOf(if (tonal) colors.secondaryContainer else colors.primary)
        setTextColor(if (tonal) colors.onSecondaryContainer else colors.onPrimary)
        icon?.let { setIconResource(it); iconTint = ColorStateList.valueOf(if (tonal) colors.onSecondaryContainer else colors.onPrimary) }
        setOnClickListener { OrbitMotion.tick(this); action() }
        OrbitMotion.bind(this)

    }
    private fun card(parent: LinearLayout = content, color: Int = colors.container, padding: Int = if (watch) 16 else 24): LinearLayout {
        val card = MaterialCardView(this).apply {
            radius = dp(if (watch) 24 else 32).toFloat(); cardElevation = 0f; strokeWidth = 0
            setCardBackgroundColor(color)
        }
        val inner = column().apply { setPadding(dp(padding), dp(padding), dp(padding), dp(padding)) }
        card.addView(inner)
        parent.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
        return inner
    }
    private fun currentTitle() = if (watch) "$tab  ▾" else tab
    private fun ensureChrome() {
        if (::root.isInitialized) return
        root = column().apply { setBackgroundColor(colors.surface) }
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, insets.getInsets(WindowInsetsCompat.Type.ime()).bottom))
            WindowInsetsCompat.CONSUMED
        }
        WindowCompat.getInsetsController(window, root).apply {
            isAppearanceLightStatusBars = com.google.android.material.color.MaterialColors.isColorLight(colors.surface)
            isAppearanceLightNavigationBars = isAppearanceLightStatusBars
        }
        val header = row().apply { setPadding(dp(if (watch) 26 else 24), dp(if (watch) 16 else 22), dp(if (watch) 26 else 16), dp(20)) }
        titleView = text(currentTitle(), if (watch) 23f else 36f).apply {
            if (watch) { contentDescription = "Choose clock tool"; setOnClickListener { chooseWatchTool(this) } }
        }
        header.addView(titleView, LinearLayout.LayoutParams(0, -2, 1f))
        if (!watch) {
            syncButton = MaterialButton(this, null, com.google.android.material.R.attr.materialIconButtonStyle).apply {
                setIconResource(R.drawable.ic_sync); contentDescription = "Sync devices"; tooltipText = "Sync devices"
                iconTint = ColorStateList.valueOf(colors.muted); iconPadding = 0; text = ""
                setOnClickListener {
                    OrbitMotion.tick(this); OrbitMotion.pulse(this); rotation = 0f
                    if (OrbitMotion.enabled()) animate().rotation(180f).setStartDelay(0).setDuration(300).start()
                    requestSync()
                }
            }
            header.addView(syncButton, LinearLayout.LayoutParams(dp(48), dp(48)))
        }
        root.addView(header)
        syncStatusView = text("", 12f, colors.muted).apply {
            visibility = View.GONE; contentDescription = "Connection status"
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            setPadding(dp(24), 0, dp(24), dp(12))
        }
        root.addView(syncStatusView)
        bodyFrame = FrameLayout(this)
        root.addView(bodyFrame, LinearLayout.LayoutParams(-1, 0, 1f))
        if (!watch) {
            navigationView = BottomNavigationView(this).apply {
                labelVisibilityMode = com.google.android.material.navigation.NavigationBarView.LABEL_VISIBILITY_LABELED
                setBackgroundColor(colors.container); elevation = 0f
                sections.forEachIndexed { i, name -> menu.add(0, i + 1, i, if (i == 0) "Alarm" else name).setIcon(icons[i]) }
                setOnItemSelectedListener { item ->
                    val target = sections[item.itemId - 1]
                    if (tab != target) { OrbitMotion.tick(this); tab = target; render() }
                    true
                }
            }
            root.addView(navigationView, LinearLayout.LayoutParams(-1, dp(80)))
            addButton = FloatingActionButton(this).apply {
                visibility = View.GONE; setImageResource(R.drawable.ic_add); customSize = dp(64); compatElevation = dp(3).toFloat()
                backgroundTintList = ColorStateList.valueOf(colors.primaryContainer); imageTintList = ColorStateList.valueOf(colors.onPrimaryContainer)
                setOnClickListener { OrbitMotion.tick(this); if (tab == "Alarms") editAlarm(Alarm()) else addCity() }
            }
            bodyFrame.addView(addButton, FrameLayout.LayoutParams(dp(64), dp(64), Gravity.BOTTOM or Gravity.END).apply { marginEnd = dp(24); bottomMargin = dp(20) })
        }
        OrbitMotion.bindTree(root); ViewCompat.requestApplyInsets(root)
    }
    private fun render() {
        val previousTab = renderedTab
        val tabChanged = previousTab != tab
        val forward = previousTab == null || sections.indexOf(tab) > sections.indexOf(previousTab)
        val previousScroll = if (!tabChanged && ::scroll.isInitialized) scroll.scrollY else 0
        val positions = mutableMapOf<String, Float>()
        if (!tabChanged && ::content.isInitialized) for (i in 0 until content.childCount) {
            val child = content.getChildAt(i); val key = child.tag as? String
            if (key?.startsWith("item:") == true) positions[key] = child.top + child.translationY
        }
        val oldScroll = if (::scroll.isInitialized) scroll else null
        ensureChrome(); renderedTab = tab; ticker = null
        titleView.text = currentTitle()
        scroll = ScrollView(this).apply { isFillViewport = true; isFocusableInTouchMode = true; clipToPadding = false }
        content = column().apply { setPadding(dp(if (watch) 22 else 24), 0, dp(if (watch) 22 else 24), dp(if (watch) 40 else 100)) }
        scroll.addView(content)
        if (tabChanged && oldScroll != null && bodyFrame.isLaidOut && OrbitMotion.enabled()) {
            val motion = MaterialSharedAxis(MaterialSharedAxis.X, forward).apply {
                duration = 240; secondaryAnimatorProvider = FadeProvider()
                addTarget(oldScroll); addTarget(scroll)
            }
            TransitionManager.beginDelayedTransition(bodyFrame, motion)
        }
        if (!tabChanged) TransitionManager.endTransitions(bodyFrame)
        if (oldScroll != null) bodyFrame.removeView(oldScroll)
        bodyFrame.addView(scroll, 0, FrameLayout.LayoutParams(-1, -1))
        when (tab) { "Alarms" -> alarms(); "Clock" -> clocks(); "Timer" -> timers(); "Stopwatch" -> stopwatch(); "Bedtime" -> bedtime() }
        if (watch && (tab == "Alarms" || tab == "Clock")) {
            content.addView(button(if (tab == "Alarms") "Add alarm" else "Add city", icon = R.drawable.ic_add) { if (tab == "Alarms") editAlarm(Alarm()) else addCity() })
        }
        addButton?.let { fab ->
            val label = if (tab == "Alarms") "Add alarm" else "Add city"
            fab.contentDescription = label; fab.tooltipText = label
            if (tab == "Alarms" || tab == "Clock") fab.show() else fab.hide()
        }
        navigationView?.let { if (it.selectedItemId != sections.indexOf(tab) + 1) it.selectedItemId = sections.indexOf(tab) + 1 }
        OrbitMotion.bindTree(content)
        if (previousTab == null) OrbitMotion.content(content)
        if (tabChanged && previousTab != null) OrbitMotion.enter(titleView, direction = if (forward) 1 else -1)
        if (!tabChanged) {
            val renderedContent = content
            content.doOnLayout {
                if (content !== renderedContent) return@doOnLayout
                scroll.scrollTo(0, previousScroll)
                for (i in 0 until content.childCount) {
                    val child = content.getChildAt(i); val key = child.tag as? String
                    if (key?.startsWith("item:") == true && child.bottom >= previousScroll && child.top <= previousScroll + scroll.height) {
                        val oldTop = positions[key]
                        if (oldTop == null) OrbitMotion.enter(child) else OrbitMotion.reposition(child, oldTop - child.top)
                    }
                }
            }
        }
        updateSyncStatus()
        pendingFeedback?.let { message ->
            pendingFeedback = null
            if (watch) Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            else Snackbar.make(root, message, Snackbar.LENGTH_SHORT).apply { navigationView?.let { setAnchorView(it) } }.show()
        }
        scroll.requestFocus(); handler.removeCallbacks(tick); if (foreground) handler.post(tick)
    }
    private fun chooseWatchTool(anchor: View) {
        PopupMenu(this, anchor).apply {
            sections.forEachIndexed { i, s -> menu.add(0, i, i, s) }
            setOnMenuItemClickListener { OrbitMotion.tick(anchor); tab = sections[it.itemId]; render(); true }; show()
        }
    }
    private fun updateSyncStatus() {
        if (!::syncStatusView.isInitialized) return
        if (root.isLaidOut && OrbitMotion.enabled() && syncStatusView.text.toString() != syncMessage.orEmpty()) {
            TransitionManager.beginDelayedTransition(root, ChangeBounds().apply { duration = 160; addTarget(syncStatusView); addTarget(bodyFrame) })
        }
        syncStatusView.text = syncMessage.orEmpty()
        syncStatusView.stateDescription = syncMessage
        syncStatusView.visibility = if (syncMessage == null) View.GONE else View.VISIBLE
        syncButton?.apply { isEnabled = !syncInProgress; alpha = if (syncInProgress) .55f else 1f }
    }
    private fun finishSync(token: Int, message: String) {
        if (isDestroyed || token != syncToken) return
        syncTimeout?.let { handler.removeCallbacks(it) }; syncInProgress = false; syncMessage = message
        if (foreground) updateSyncStatus()
    }
    private fun requestSync() {
        if (syncInProgress) return
        syncInProgress = true; val token = ++syncToken
        syncMessage = "Checking connection…"
        updateSyncStatus(); Sync.refresh(applicationContext)
        val weak = java.lang.ref.WeakReference(this)
        val peer = if (watch) "Phone" else "Watch"
        syncTimeout = Runnable { finishSync(token, "Connection check timed out. Changes remain saved here.") }.also { handler.postDelayed(it, 8000) }
        Wearable.getNodeClient(applicationContext).connectedNodes
            .addOnSuccessListener { nodes -> weak.get()?.finishSync(token, if (nodes.isEmpty()) "No ${peer.lowercase()} connected. Changes remain saved here." else "$peer connected. Sync requested.") }
            .addOnFailureListener { weak.get()?.finishSync(token, "$peer connection unavailable. Changes remain saved here.") }
    }
    private fun time(hour: Int, minute: Int): String = LocalTime.of(hour, minute).format(DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(this)) "HH:mm" else "h:mm", Locale.getDefault()))
    private fun period(hour: Int): String = if (DateFormat.is24HourFormat(this)) "" else LocalTime.of(hour, 0).format(DateTimeFormatter.ofPattern("a", Locale.getDefault()))
    private fun alarms() {
        val all = Store(this).all().filterNot { it.deleted || it.timer }.sortedWith(compareBy({ it.hour }, { it.minute }))
        val next = all.mapNotNull { it.next() }.minOrNull()
        if (!Scheduler.allowed(this) || notificationsMissing() || fullScreenMissing()) {
            card(color = colors.container, padding = 20).apply {
                addView(text("Finish alarm setup", 16f, colors.onSurface)); space(this, 6)
                addView(text("Allow alarms and notifications to ring reliably.", 13f, colors.muted)); space(this, 12)
                addView(button("Review permissions") { permissions() })
            }
        }
        if (next != null) {
            card(color = colors.primaryContainer, padding = 20).apply {
                addView(text("NEXT ALARM", 11f, colors.onPrimaryContainer).apply { letterSpacing = .12f }); space(this, 8)
                val duration = Duration.between(Instant.now(), next).toMinutes().coerceAtLeast(1)
                val remaining = buildList {
                    val days = duration / 1440; val hours = duration % 1440 / 60; val minutes = duration % 60
                    if (days > 0) add("$days ${if (days == 1L) "day" else "days"}")
                    if (hours > 0) add("$hours hr")
                    if (minutes > 0) add("$minutes min")
                }.joinToString(" ")
                addView(text("In $remaining", if (watch) 24f else 30f, colors.onPrimaryContainer)); space(this, 6)
                addView(text(next.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEEE, d MMM · HH:mm", Locale.getDefault())), 13f, colors.onPrimaryContainer))
            }
        } else if (all.isNotEmpty()) {
            content.addView(text("All alarms off", 14f, colors.muted)); space(content)
        }
        if (all.isEmpty()) {
            card(color = colors.container).apply {
                gravity = Gravity.CENTER_HORIZONTAL; space(this, 20)
                addView(OrbitDial(this@ClockActivity, colors), LinearLayout.LayoutParams(dp(if (watch) 100 else 176), dp(if (watch) 100 else 176)))
                space(this, 28); addView(text("No alarms yet", if (watch) 22f else 28f)); space(this, 8)
                space(this, 12)
            }
        }
        all.forEach { alarm ->
            val inner = card(color = if (alarm.enabled) colors.containerHigh else colors.container)
            (inner.parent as View).tag = "item:alarm:${alarm.id}"
            val line = row()
            val clock = text(time(alarm.hour, alarm.minute), if (watch) 36f else 54f, if (alarm.enabled) colors.onSurface else colors.muted).apply {
                typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
                maxLines = 1; setAutoSizeTextTypeUniformWithConfiguration(24, if (watch) 36 else 54, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
                contentDescription = "Edit ${alarm.label} at ${time(alarm.hour, alarm.minute)} ${period(alarm.hour)}"
                setOnClickListener { OrbitMotion.tick(this); editAlarm(alarm) }
            }
            if (!watch) {
                clock.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.ic_edit, 0)
                clock.compoundDrawablePadding = dp(10)
                clock.compoundDrawablesRelative[2]?.setTint(colors.muted)
            }
            line.addView(clock, LinearLayout.LayoutParams(0, dp(if (watch) 52 else 72), 1f))
            line.addView(MaterialSwitch(this).apply {
                isChecked = alarm.enabled; contentDescription = "${alarm.label} enabled"
                setOnCheckedChangeListener { view, enabled -> OrbitMotion.tick(view); Store(this@ClockActivity).save(alarm.copy(enabled = enabled, snoozeUntil = 0, skipThrough = 0)) }
            })
            inner.addView(line)
            if (period(alarm.hour).isNotEmpty()) inner.addView(text(period(alarm.hour), 12f, colors.muted))
            space(inner, 6); inner.addView(text(alarm.label, 17f)); space(inner, 6)
            inner.addView(text(if (alarm.days.isEmpty()) "Once" else alarm.days.sorted().joinToString(" · ") { DayOfWeek.of(it).getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault()) }, 13f, colors.muted))
            (inner.parent as View).apply { isClickable = true; contentDescription = "Edit ${alarm.label}"; setOnClickListener { OrbitMotion.tick(this); editAlarm(alarm) } }
        }
        if (watch) content.addView(button("Sync devices", tonal = true) { requestSync() })
    }
    private fun notificationsMissing() = Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
    private fun fullScreenMissing() = Build.VERSION.SDK_INT >= 34 && !getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
    private fun permissions() {
        val sheet = BottomSheetDialog(this)
        val layout = column().apply { setPadding(dp(24), dp(24), dp(24), dp(32)) }
        layout.addView(text("Ready to ring", 26f)); space(layout, 12)
        layout.addView(text("Choose the permissions you still need below.", 14f, colors.muted)); space(layout)
        if (!Scheduler.allowed(this)) layout.addView(button("Allow precise alarms") { sheet.dismiss(); startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, android.net.Uri.parse("package:$packageName"))) })
        if (notificationsMissing()) { space(layout, 8); layout.addView(button("Allow notifications") { sheet.dismiss(); requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1) }) }
        if (fullScreenMissing()) { space(layout, 8); layout.addView(button("Allow ringing screen") { sheet.dismiss(); startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, android.net.Uri.parse("package:$packageName"))) }) }
        sheet.setContentView(layout); sheet.show()
    }
    private fun input(parent: LinearLayout, label: String, value: String, numeric: Boolean = false): TextInputEditText {
        val box = TextInputLayout(this, null, com.google.android.material.R.attr.textInputOutlinedStyle).apply { hint = label }
        val edit = TextInputEditText(box.context).apply { setText(value); isSingleLine = true; if (numeric) inputType = android.text.InputType.TYPE_CLASS_NUMBER }
        box.addView(edit); parent.addView(box, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) })
        edit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { if (box.error != null) box.error = null }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        return edit
    }
    private fun inputError(field: TextInputEditText, message: String) {
        var parent = field.parent
        while (parent is View) {
            if (parent is TextInputLayout) { parent.error = message; OrbitMotion.reject(field); return }
            parent = parent.parent
        }
        field.error = message; OrbitMotion.reject(field)
    }
    /** Used by the phone's explicit widget links after the activity has resumed. */
    protected fun openWidgetAlarm(id: String?) {
        tab = "Alarms"
        render()
        val alarm = if (id == null) Alarm() else Store(this).get(id)
        if (alarm != null && !alarm.deleted && !alarm.timer) editAlarm(alarm)
    }
    protected fun openWidgetBedtime() { tab = "Bedtime"; render() }
    private fun editAlarm(original: Alarm) {
        var hour = original.hour; var minute = original.minute
        val exists = Store(this).get(original.id) != null
        if (exists) VisualHints.learn(this, "alarm_edit")
        val sheet = BottomSheetDialog(this)
        val body = column().apply { setPadding(dp(if (watch) 20 else 24), dp(24), dp(if (watch) 20 else 24), dp(32)) }
        body.addView(text(if (exists) "Edit alarm" else "New alarm", 24f)); space(body, 16)
        val pickerButton = button("${time(hour, minute)} ${period(hour)}", tonal = true) {
            if (watch) chooseWatchTime(hour, minute) { h, m ->
                hour = h; minute = m
                OrbitMotion.text(body.findViewWithTag<MaterialButton>("alarm_time"), "${time(hour, minute)} ${period(hour)}")
            } else {
            val picker = MaterialTimePicker.Builder().setHour(hour).setMinute(minute)
                .setTimeFormat(if (DateFormat.is24HourFormat(this)) TimeFormat.CLOCK_24H else TimeFormat.CLOCK_12H)
                .setTitleText("Set alarm time").build()
            picker.addOnPositiveButtonClickListener { hour = picker.hour; minute = picker.minute; OrbitMotion.text(body.findViewWithTag<MaterialButton>("alarm_time"), "${time(hour, minute)} ${period(hour)}") }
            picker.show(supportFragmentManager, "alarm_time")
            }
        }.apply { tag = "alarm_time"; backgroundTintList = ColorStateList.valueOf(colors.containerHigh); setTextColor(colors.primary); textSize = if (watch) 28f else 42f; contentDescription = "Set alarm time"; minimumHeight = dp(96) }
        body.addView(pickerButton, LinearLayout.LayoutParams(-1, dp(104))); space(body, 24)
        val label = input(body, "Label", original.label)
        body.addView(text("Repeat", 14f, colors.muted)); space(body, 8)
        val selected = original.days.toMutableSet()
        val days = if (watch) column() else row()
        var dayRow = days
        (1..7).forEach { day ->
            if (watch && (day - 1) % 3 == 0) { dayRow = row(); days.addView(dayRow) }
            val name = DayOfWeek.of(day).getDisplayName(if (watch) java.time.format.TextStyle.SHORT else java.time.format.TextStyle.NARROW, Locale.getDefault())
            val b = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = name; contentDescription = DayOfWeek.of(day).name.lowercase().replaceFirstChar(Char::uppercaseChar)
                minimumWidth = 0; minWidth = 0; setPadding(0, 0, 0, 0); cornerRadius = dp(24)
                fun update(animate: Boolean = false) { isSelected = day in selected; val bg = if (isSelected) colors.primary else colors.surface; val fg = if (isSelected) colors.onPrimary else colors.onSurface; if (animate) OrbitMotion.selection(this, bg, fg) else { backgroundTintList = ColorStateList.valueOf(bg); setTextColor(fg) } }
                update(); setOnClickListener { OrbitMotion.tick(this); if (!selected.add(day)) selected.remove(day); update(true) }
            }
            dayRow.addView(b, LinearLayout.LayoutParams(0, dp(48), 1f))
        }
        body.addView(days); space(body, 20)
        fun option(title: String, checked: Boolean): MaterialSwitch {
            val line = row(); line.addView(text(title), LinearLayout.LayoutParams(0, -2, 1f))
            val switch = MaterialSwitch(this).apply { isChecked = checked; contentDescription = title; setOnCheckedChangeListener { view, _ -> OrbitMotion.tick(view) } }
            line.addView(switch); body.addView(line, LinearLayout.LayoutParams(-1, dp(56))); return switch
        }
        val sound = option("Alarm sound", original.sound); val vibrate = option("Vibration", original.vibrate)
        space(body); val snooze = input(body, "Snooze · minutes", original.snoozeMinutes.toString(), numeric = true)
        val actions = row()
        actions.addView(button("Cancel", tonal = true) { sheet.dismiss() }, LinearLayout.LayoutParams(0, dp(52), 1f).apply { marginEnd = dp(12) })
        actions.addView(button("Save alarm") {
            val length = snooze.text.toString().toIntOrNull()
            if (length == null || length !in 1..60) { inputError(snooze, "Enter 1–60 minutes"); return@button }
            pendingFeedback = if (exists) "Alarm updated" else "Alarm saved"
            Store(this).save(original.copy(hour = hour, minute = minute, label = label.text.toString().trim().ifEmpty { "Alarm" }, days = selected.toSet(), sound = sound.isChecked, vibrate = vibrate.isChecked, snoozeMinutes = length, enabled = true, snoozeUntil = 0, skipThrough = 0))
            sheet.dismiss()
        }, LinearLayout.LayoutParams(0, dp(52), 1.4f))
        body.addView(actions)
        if (exists) { space(body, 12); body.addView(button("Delete alarm", tonal = true) {
            MaterialAlertDialogBuilder(this).setTitle("Delete this alarm?").setMessage("This also removes it from your paired device when connected.")
                .setNegativeButton("Keep", null).setPositiveButton("Delete") { _, _ -> pendingFeedback = "Alarm deleted"; Store(this).save(original.copy(enabled = false, deleted = true)); sheet.dismiss() }.show()
        }) }
        sheet.setContentView(ScrollView(this).apply { addView(body) }); sheet.show()
        sheet.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
        OrbitMotion.bindTree(body); OrbitMotion.enter(body)
    }
    private fun chooseWatchTime(hour: Int, minute: Int, onPicked: (Int, Int) -> Unit) {
        val sheet = BottomSheetDialog(this)
        val body = column().apply { setPadding(dp(18), dp(20), dp(18), dp(28)) }
        body.addView(text("Set time", 22f)); space(body, 8)
        fun picker(max: Int, value: Int, label: String) = NumberPicker(this).apply {
            minValue = 0; maxValue = max; this.value = value
            setFormatter { String.format(Locale.getDefault(), "%02d", it) }
            contentDescription = label; descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
        }
        val hours = picker(23, hour, "Hour, 24-hour time")
        val minutes = picker(59, minute, "Minute")
        val pickers = row()
        pickers.addView(hours, LinearLayout.LayoutParams(0, dp(100), 1f))
        pickers.addView(text(":", 24f))
        pickers.addView(minutes, LinearLayout.LayoutParams(0, dp(100), 1f))
        body.addView(pickers); space(body, 12)
        body.addView(button("Set time") { onPicked(hours.value, minutes.value); sheet.dismiss() }, LinearLayout.LayoutParams(-1, dp(48)))
        sheet.setContentView(ScrollView(this).apply { addView(body) }); sheet.show()
        OrbitMotion.bindTree(body); OrbitMotion.enter(body)
    }
    private fun clocks() {
        val hero = card(color = colors.container)
        hero.gravity = Gravity.CENTER_HORIZONTAL
        val dial = OrbitDial(this, colors); hero.addView(dial, LinearLayout.LayoutParams(dp(if (watch) 100 else 160), dp(if (watch) 100 else 160)))
        space(hero, 12)
        val now = text("", if (watch) 36f else 60f).apply { typeface = Typeface.create("sans-serif-light", Typeface.NORMAL) }
        val date = text("", 14f, colors.muted); hero.addView(now); space(hero, 8); hero.addView(date)
        val zones = prefs.getStringSet("zones", setOf("Europe/London", "America/New_York", "Asia/Tokyo"))!!.sorted()
        val displays = zones.map { id ->
            val c = card(padding = 20); (c.parent as View).tag = "item:city:$id"; val line = row().apply { if (watch) orientation = LinearLayout.VERTICAL }; val city = column()
            city.addView(text(id.substringAfterLast('/').replace('_', ' '), 18f)); space(city, 8)
            val info = text("", 12f, colors.muted); city.addView(info); line.addView(city, if (watch) LinearLayout.LayoutParams(-1, -2) else LinearLayout.LayoutParams(0, -2, 1f))
            val value = text("", if (watch) 24f else 30f); line.addView(value); c.addView(line)
            (c.parent as View).stateDescription = "Touch and hold to remove city"
            fun removeCity() {
                MaterialAlertDialogBuilder(this).setTitle("Remove this city?").setNegativeButton("Keep", null).setPositiveButton("Remove") { _, _ -> VisualHints.learn(this@ClockActivity, "city_remove"); pendingFeedback = "City removed"; prefs.edit().putStringSet("zones", zones.filterNot { it == id }.toSet()).apply(); render() }.show()
            }
            (c.parent as View).setOnLongClickListener { removeCity(); true }
            val removeIcon = MaterialButton(this, null, com.google.android.material.R.attr.materialIconButtonStyle).apply {
                setIconResource(R.drawable.ic_close); iconTint = ColorStateList.valueOf(colors.muted)
                text = ""; iconPadding = 0; contentDescription = "Remove ${id.substringAfterLast('/').replace('_', ' ')}"
                setOnClickListener { OrbitMotion.tick(this); removeCity() }
            }
            val removeParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply { gravity = Gravity.END }
            if (watch) c.addView(removeIcon, removeParams) else line.addView(removeIcon, removeParams)
            Triple(id, value, info)
        }
        ticker = {
            val current = ZonedDateTime.now(); now.text = "${time(current.hour, current.minute)} ${period(current.hour)}".trim()
            date.text = current.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault()))
            dial.hour = current.hour; dial.minute = current.minute; dial.second = current.second; dial.invalidate()
            displays.forEach { (id, value, info) ->
                val city = ZonedDateTime.now(ZoneId.of(id)); value.text = "${time(city.hour, city.minute)} ${period(city.hour)}".trim()
                val diff = (city.offset.totalSeconds - current.offset.totalSeconds) / 60L
                info.text = if (diff == 0L) "Same time" else "${if (diff > 0) "+" else "−"}${kotlin.math.abs(diff) / 60}h${if (kotlin.math.abs(diff) % 60 != 0L) " ${kotlin.math.abs(diff) % 60}m" else ""} · ${city.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault())}"
            }
        }; ticker?.invoke()
    }
    private fun addCity() {
        val ids = ZoneId.getAvailableZoneIds().filter { '/' in it }.sorted()
        val layout = column().apply { setPadding(dp(24), dp(8), dp(24), 0) }
        val query = input(layout, "Search city or timezone", "")
        val list = ListView(this); var matches = ids
        fun update() { matches = ids.filter { it.replace('_', ' ').contains(query.text.toString(), ignoreCase = true) }; list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, matches.map { it.replace('_', ' ').replace("/", " · ") }) }
        layout.addView(list, LinearLayout.LayoutParams(-1, dp(300)))
        val dialog = MaterialAlertDialogBuilder(this).setTitle("Add a city").setView(layout).setNegativeButton("Cancel", null).create()
        list.setOnItemClickListener { _, view, position, _ ->
            OrbitMotion.tick(view)
            val zones = prefs.getStringSet("zones", setOf("Europe/London", "America/New_York", "Asia/Tokyo"))!!.toSet() + matches[position]
            prefs.edit().putStringSet("zones", zones).apply(); dialog.dismiss(); render()
        }
        query.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { update() }
            override fun afterTextChanged(s: Editable?) {}
        }); update(); dialog.show(); OrbitMotion.bindTree(layout); OrbitMotion.enter(layout)
    }
    private fun timers() {
        content.addView(button("Focus mode", icon = R.drawable.ic_timer) { startActivity(Intent(this, FocusActivity::class.java)) })
        val setup = card(color = colors.container); setup.addView(text("Set a timer", if (watch) 22f else 26f)); space(setup, 20)
        val minutes = input(setup, "Minutes", "5", numeric = true).apply { textSize = 42f; gravity = Gravity.CENTER; contentDescription = "Timer minutes" }
        val presets = row()
        listOf(1, 5, 10, 25).forEach { m -> presets.addView(button("$m", tonal = true) { OrbitMotion.text(minutes, m.toString()); minutes.setSelection(minutes.text?.length ?: 0) }.apply { contentDescription = "Timer preset: $m min" }, LinearLayout.LayoutParams(0, dp(48), 1f).apply { marginEnd = dp(4) }) }
        fun selectPreset(animate: Boolean) {
            for (i in 0 until presets.childCount) {
                val b = presets.getChildAt(i) as MaterialButton
                val selected = b.text.toString() == minutes.text.toString()
                if (b.isSelected != selected || !animate) {
                    b.isSelected = selected
                    val bg = if (selected) colors.primary else colors.secondaryContainer
                    val fg = if (selected) colors.onPrimary else colors.onSecondaryContainer
                    if (animate) OrbitMotion.selection(b, bg, fg) else { b.backgroundTintList = ColorStateList.valueOf(bg); b.setTextColor(fg) }
                }
            }
        }
        selectPreset(false)
        minutes.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { selectPreset(true) }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        setup.addView(presets); space(setup, 20)
        setup.addView(button("Start timer", icon = R.drawable.ic_play) {
            val count = minutes.text.toString().toLongOrNull()
            if (count == null || count !in 1..1440) { inputError(minutes, "Enter 1–1440 minutes"); return@button }
            if (!Scheduler.allowed(this)) { permissions(); return@button }
            pendingFeedback = "Timer started"
            Store(this).save(Alarm(timer = true, label = "Timer · $count min", snoozeUntil = System.currentTimeMillis() + count * 60_000))
        }, LinearLayout.LayoutParams(-1, dp(56)))
        val labels = Store(this).all().filter { !it.deleted && it.enabled && it.timer }.map { alarm ->
            val c = card(color = colors.primaryContainer); (c.parent as View).tag = "item:timer:${alarm.id}"; c.addView(text(alarm.label, 14f, colors.onPrimaryContainer)); space(c, 12)
            val countdown = text("", if (watch) 28f else 42f, colors.onPrimaryContainer).apply { typeface = Typeface.create("sans-serif-light", Typeface.NORMAL) }
            c.addView(countdown); space(c, 16); c.addView(button("Cancel timer", tonal = true) { pendingFeedback = "Timer canceled"; Store(this).save(alarm.copy(enabled = false, deleted = true)) }); alarm to countdown
        }
        ticker = { labels.forEach { (alarm, view) ->
            val seconds = ((alarm.snoozeUntil - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
            val value = String.format(Locale.getDefault(), "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
            if (view.text.toString() != value) view.text = value
        } }; ticker?.invoke()
    }
    private fun stopwatch() {
        val boot = Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, 0)
        if (prefs.getInt("stopwatchBoot", boot) != boot) prefs.edit().putBoolean("running", false).putLong("elapsed", 0).putString("laps", "").apply()
        prefs.edit().putInt("stopwatchBoot", boot).apply()
        fun elapsed() = prefs.getLong("elapsed", 0) + if (prefs.getBoolean("running", false)) SystemClock.elapsedRealtime() - prefs.getLong("started", SystemClock.elapsedRealtime()) else 0
        val dialSize = dp(if (watch) 144 else 276)
        val face = FrameLayout(this).apply { tag = "stopwatch_face" }
        val dial = OrbitDial(this, colors).apply { progress = 0f }
        face.addView(dial, FrameLayout.LayoutParams(-1, -1))
        val display = text("", if (watch) 28f else 46f, colors.onPrimaryContainer).apply { gravity = Gravity.CENTER; typeface = Typeface.create("sans-serif-light", Typeface.NORMAL); contentDescription = "Stopwatch elapsed time" }
        face.addView(display, FrameLayout.LayoutParams(-1, -1))
        content.addView(face, LinearLayout.LayoutParams(dialSize, dialSize).apply { gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(16); bottomMargin = dp(32) })
        ticker = {
            val ms = elapsed()
            val value = String.format(Locale.getDefault(), "%02d:%02d.%02d", ms / 60_000, ms / 1000 % 60, ms / 10 % 100)
            if (display.text.toString() != value) display.text = value
            val fraction = (ms % 60_000) / 60_000f
            if (dial.progress != fraction) { dial.progress = fraction; dial.invalidate() }
        }; ticker?.invoke()
        val running = prefs.getBoolean("running", false)
        content.addView(button(if (running) "Pause" else "Start", icon = if (running) R.drawable.ic_pause else R.drawable.ic_play) {
            if (running) prefs.edit().putLong("elapsed", elapsed()).putBoolean("running", false).apply()
            else prefs.edit().putLong("started", SystemClock.elapsedRealtime()).putBoolean("running", true).apply()
            render(); content.findViewWithTag<View>("stopwatch_face")?.let { OrbitMotion.pulse(it) }
        }, LinearLayout.LayoutParams(-1, dp(64)))
        space(content, 12)
        content.addView(button(if (running) "Lap" else "Reset", tonal = true) {
            if (running) { VisualHints.learn(this, "stopwatch_laps"); prefs.edit().putString("laps", "${elapsed()}|" + prefs.getString("laps", "")).apply() }
            else prefs.edit().putLong("elapsed", 0).putString("laps", "").apply()
            render(); content.findViewWithTag<View>("stopwatch_face")?.let { OrbitMotion.pulse(it) }
        }, LinearLayout.LayoutParams(-1, dp(52)))
        space(content, 24)
        val laps = prefs.getString("laps", "")!!.split('|').filter { it.isNotBlank() }
        laps.take(100).forEachIndexed { index, value ->
            val ms = value.toLong(); val line = row()
            line.addView(text("Lap ${laps.size - index}", 14f, colors.muted), LinearLayout.LayoutParams(0, -2, 1f))
            line.addView(text(String.format(Locale.getDefault(), "%02d:%02d.%02d", ms / 60_000, ms / 1000 % 60, ms / 10 % 100), 18f))
            val lap = card(padding = 20); (lap.parent as View).tag = "item:lap:$value"; lap.addView(line)
        }
    }
    private fun bedtime() {
        val alarm = Store(this).all().firstOrNull { it.label == "Wake up" && !it.deleted }
        val goal = prefs.getInt("sleepGoal", 8)
        card(color = colors.primaryContainer).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            addView(ImageView(this@ClockActivity).apply { setImageResource(R.drawable.ic_bedtime); imageTintList = ColorStateList.valueOf(colors.onPrimaryContainer) }, LinearLayout.LayoutParams(dp(64), dp(64)))
            space(this, 20); addView(text("A rhythm for your rest", if (watch) 21f else 26f, colors.onPrimaryContainer).apply { gravity = Gravity.CENTER }); space(this, 12)
            addView(text("$goal hours of sleep", 16f, colors.onPrimaryContainer)); space(this, 16)
            if (alarm != null && alarm.enabled) {
                val bed = LocalTime.of(alarm.hour, alarm.minute).minusHours(goal.toLong())
                addView(text("${time(bed.hour, bed.minute)} → ${time(alarm.hour, alarm.minute)}", if (watch) 24f else 32f, colors.onPrimaryContainer))
            } else addView(text("Choose a wake-up time to get started.", 14f, colors.onPrimaryContainer).apply { gravity = Gravity.CENTER })
        }
        card().apply {
            addView(text("Sleep goal", 20f)); space(this, 8); addView(text("Make room for a consistent night's rest.", 14f, colors.muted)); space(this, 16)
            addView(button("$goal hours", tonal = true) {
                val choices = (4..12).map { "$it hours" }.toTypedArray()
                MaterialAlertDialogBuilder(this@ClockActivity).setTitle("Sleep goal").setSingleChoiceItems(choices, goal - 4) { dialog, index -> VisualHints.learn(this@ClockActivity, "bedtime_schedule"); prefs.edit().putInt("sleepGoal", index + 4).apply(); dialog.dismiss(); render(); OrbitMotion.pulse(content.getChildAt(0)) }.setNegativeButton("Cancel", null).show()
            })
        }
        card().apply {
            addView(text("Wake-up schedule", 20f)); space(this, 8)
            addView(text(if (alarm != null) "${time(alarm.hour, alarm.minute)} ${period(alarm.hour)}" else "Start the day your way", 14f, colors.muted)); space(this, 16)
            addView(button(if (alarm == null) "Set wake-up alarm" else "Edit wake-up alarm", icon = R.drawable.ic_alarm) { VisualHints.learn(this@ClockActivity, "bedtime_schedule"); editAlarm(alarm ?: Alarm(label = "Wake up", days = setOf(1, 2, 3, 4, 5))) })
        }
    }
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (watch && event.action == MotionEvent.ACTION_SCROLL && event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
            scroll.smoothScrollBy(0, (-event.getAxisValue(MotionEvent.AXIS_SCROLL) * dp(40)).toInt()); return true
        }
        return super.onGenericMotionEvent(event)
    }
}

