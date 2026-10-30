package dev.orbit.clock.core

import android.app.*
import android.content.*
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.*
import java.time.*
import java.time.format.DateTimeFormatter

/** Shared native UI; the watch has a compact layout and rotary scrolling. */
open class ClockActivity : Activity() {
    open val watch = false
    private val bg=0xff111318.toInt()
    private val panel=0xff202127.toInt()
    private val ink=0xffe8e1ef.toInt()
    private val accent=0xffd0bcff.toInt()
    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var scroll: ScrollView
    private var tab="Alarm"
    private val handler=Handler(Looper.getMainLooper())
    private var ticker: (() -> Unit)? = null
    private val tick = object : Runnable { override fun run() { ticker?.invoke(); handler.postDelayed(this, 100) } }
    private val changes=object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { if(tab=="Alarm" || tab=="Bedtime") render() }
    }
    private val prefs by lazy { getSharedPreferences("tools", MODE_PRIVATE) }
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.statusBarColor=bg; window.navigationBarColor=bg
        tab=state?.getString("tab") ?: "Alarm"
        if(Build.VERSION.SDK_INT>=33) registerReceiver(changes, IntentFilter(Store.CHANGED), RECEIVER_NOT_EXPORTED)
        else registerReceiver(changes, IntentFilter(Store.CHANGED))
        render(); Sync.refresh(this)
    }
    override fun onResume() { super.onResume(); Scheduler.restore(this); if(::root.isInitialized) render(); handler.removeCallbacks(tick); handler.post(tick) }
    override fun onPause() { handler.removeCallbacks(tick); super.onPause() }
    override fun onSaveInstanceState(out: Bundle) { out.putString("tab",tab); super.onSaveInstanceState(out) }
    override fun onDestroy() { unregisterReceiver(changes); super.onDestroy() }
    private fun dp(n: Int)=(resources.displayMetrics.density*n).toInt()
    private fun shape(color: Int, radius: Float=28f)=GradientDrawable().apply { setColor(color); cornerRadius=dp(radius.toInt()).toFloat() }
    private fun text(value: String, size: Float=16f, color: Int=ink)=TextView(this).apply {
        text=value; textSize=size; setTextColor(color); setPadding(0,dp(6),0,dp(6))
    }
    private fun button(label: String, action: () -> Unit)=Button(this).apply {
        text=label; isAllCaps=false; setTextColor(bg); backgroundTintList=android.content.res.ColorStateList.valueOf(accent)
        setOnClickListener { action() }
    }
    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation=LinearLayout.VERTICAL; background=shape(panel); setPadding(dp(20),dp(12),dp(20),dp(12))
        content.addView(this, LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(12) })
    }
    private fun render() {
        ticker=null
        root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(bg); setPadding(dp(if(watch) 22 else 24),dp(if(watch) 22 else 12),dp(if(watch) 22 else 24),dp(if(watch) 22 else 8)) }
        setContentView(root)
        if(!watch) root.addView(text("ORBIT CLOCK",12f,accent).apply { letterSpacing=.22f })
        root.addView(text(if(tab=="Alarm") "Alarms" else tab,if(watch) 24f else 38f).apply { typeface=Typeface.create("sans-serif",Typeface.NORMAL) })
        scroll=ScrollView(this).apply { isFillViewport=true; clipToPadding=false; isFocusableInTouchMode=true }
        content=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        scroll.addView(content); root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        when(tab) { "Alarm" -> alarms(); "Clock" -> clocks(); "Timer" -> timers(); "Stopwatch" -> stopwatch(); "Bedtime" -> bedtime() }
        val nav=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER }
        val navScroll=HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled=false; addView(nav) }
        listOf("Alarm","Clock","Timer","Stopwatch","Bedtime").forEach { name ->
            nav.addView(TextView(this).apply {
                text=name; textSize=if(watch) 12f else 13f; gravity=Gravity.CENTER; setTextColor(if(name==tab) accent else 0xffa5a0ad.toInt())
                setPadding(dp(10),dp(18),dp(10),dp(12)); contentDescription="$name tab"
                setOnClickListener { tab=name; render() }
            })
        }
        root.addView(navScroll); scroll.requestFocus()
    }
    private fun alarms() {
        if(!Scheduler.allowed(this)) card().apply {
            addView(text("Allow precise alarms",18f,accent)); addView(text("Required for alarms to ring on time."))
            addView(button("Open settings") { startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, android.net.Uri.parse("package:$packageName"))) })
        }
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED) {
            content.addView(button("Allow alarm notifications") { requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),1) })
        }
        if(Build.VERSION.SDK_INT>=34 && !getSystemService(NotificationManager::class.java).canUseFullScreenIntent()) {
            content.addView(button("Allow ringing screen") { startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,android.net.Uri.parse("package:$packageName"))) })
        }
        val all=Store(this).all().filterNot { it.deleted || it.timer }.sortedWith(compareBy({it.hour},{it.minute}))
        val next=all.mapNotNull { it.next() }.minOrNull()
        content.addView(text(next?.let { "Next alarm ${it.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEE, HH:mm"))}" } ?: "Your next good morning starts here",14f,accent))
        if(all.isEmpty()) card().apply { addView(text("Rest easy",28f)); addView(text("Add an alarm. It will sync with your paired device.")) }
        all.forEach { a -> card().apply {
            val row=LinearLayout(this@ClockActivity).apply { gravity=Gravity.CENTER_VERTICAL }
            row.addView(text(String.format("%02d:%02d",a.hour,a.minute),if(watch) 34f else 52f).apply { setOnClickListener { edit(a) } },LinearLayout.LayoutParams(0,-2,1f))
            row.addView(Switch(this@ClockActivity).apply { isChecked=a.enabled; contentDescription="${a.label} enabled"; setOnCheckedChangeListener { _, on -> Store(this@ClockActivity).save(a.copy(enabled=on,snoozeUntil=0,skipThrough=0)) } })
            addView(row); addView(text(a.label,16f)); addView(text(if(a.days.isEmpty()) "Once" else a.days.sorted().joinToString(" · ") { DayOfWeek.of(it).name.take(3).lowercase().replaceFirstChar(Char::uppercaseChar) },12f,accent))
            setOnClickListener { edit(a) }
        } }
        content.addView(button("+  Add alarm") { edit(Alarm()) })
        content.addView(button("Sync devices") { Sync.refresh(this); Toast.makeText(this,"Sync requested. Both apps must use the same signing key.",Toast.LENGTH_LONG).show() })
        getSharedPreferences("sync",0).getString("error",null)?.let { content.addView(text(it,12f)) }
    }
    private fun edit(a: Alarm) {
        val body=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(16),0,dp(16),0) }
        val time=TimePicker(this).apply { setIs24HourView(true); hour=a.hour; minute=a.minute }
        body.addView(time)
        val label=EditText(this).apply { setText(a.label); hint="Label" }; body.addView(label)
        val selected=a.days.toMutableSet()
        val weekdays=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        (1..7).forEach { d -> weekdays.addView(CheckBox(this).apply {
            text=DayOfWeek.of(d).name.lowercase().replaceFirstChar(Char::uppercaseChar); isChecked=d in selected
            setOnCheckedChangeListener { _, checked -> if(checked) selected.add(d) else selected.remove(d) }
        }) }; body.addView(weekdays)
        val sound=CheckBox(this).apply { text="Alarm sound"; isChecked=a.sound }; body.addView(sound)
        val vibration=CheckBox(this).apply { text="Vibrate"; isChecked=a.vibrate }; body.addView(vibration)
        body.addView(text("Snooze minutes (1–60)"))
        val snooze=EditText(this).apply { inputType=2; setText(a.snoozeMinutes.toString()) }; body.addView(snooze)
        AlertDialog.Builder(this).setTitle("Alarm").setView(ScrollView(this).apply { addView(body) })
            .setPositiveButton("Save") { _,_ -> Store(this).save(a.copy(hour=time.hour,minute=time.minute,label=label.text.toString().ifBlank { "Alarm" },days=selected,
                sound=sound.isChecked,vibrate=vibration.isChecked,snoozeMinutes=(snooze.text.toString().toIntOrNull() ?: 10).coerceIn(1,60),enabled=true,snoozeUntil=0,skipThrough=0)) }
            .setNegativeButton("Cancel",null).setNeutralButton("Delete") { _,_ -> Store(this).save(a.copy(deleted=true,enabled=false)) }.show()
    }
    private fun clocks() {
        val local=card(); val now=text("",if(watch) 36f else 62f); val date=text("",16f,accent)
        local.addView(now); local.addView(date)
        val zones=prefs.getStringSet("zones",setOf("Europe/London","America/New_York","Asia/Tokyo"))!!.sorted()
        val displays=zones.map { id ->
            val c=card(); c.addView(text(id.substringAfterLast('/').replace('_',' '),20f)); val value=text("",28f); c.addView(value)
            c.setOnLongClickListener { prefs.edit().putStringSet("zones",zones.filterNot { it==id }.toSet()).apply(); render(); true }
            id to value
        }
        ticker={
            val current=ZonedDateTime.now(); now.text=current.format(DateTimeFormatter.ofPattern("HH:mm:ss")); date.text=current.format(DateTimeFormatter.ofPattern("EEEE, d MMMM"))
            displays.forEach { (id, v) -> v.text=ZonedDateTime.now(ZoneId.of(id)).format(DateTimeFormatter.ofPattern("HH:mm · EEE")) }
        }; ticker?.invoke()
        content.addView(button("+  Add city") {
            val ids=ZoneId.getAvailableZoneIds().filter { '/' in it }.sorted().toTypedArray()
            AlertDialog.Builder(this).setTitle("World clock").setItems(ids) { _,index -> prefs.edit().putStringSet("zones",zones.toSet()+ids[index]).apply(); render() }.show()
        })
    }
    private fun timers() {
        val minutes=EditText(this).apply { hint="Minutes"; inputType=2; setText("5"); setTextColor(ink); setHintTextColor(accent) }
        card().apply { addView(text("Make time for a moment",20f)); addView(minutes); addView(button("Start timer") {
            val count=minutes.text.toString().toLongOrNull()
            if(count==null || count !in 1..1440) { minutes.error="Enter 1–1440 minutes"; return@button }
            if(!Scheduler.allowed(this@ClockActivity)) { Toast.makeText(this@ClockActivity,"Allow precise alarms in the Alarm tab first",Toast.LENGTH_LONG).show(); return@button }
            Store(this@ClockActivity).save(Alarm(timer=true,label="Timer · $count min",snoozeUntil=System.currentTimeMillis()+count*60_000)); render()
        }) }
        val labels=Store(this).all().filter { !it.deleted && it.enabled && it.timer }.map { a ->
            val c=card(); c.addView(text(a.label,20f)); val countdown=text("",36f); c.addView(countdown)
            c.addView(button("Cancel") { Store(this).save(a.copy(enabled=false,deleted=true)); render() }); a to countdown
        }
        ticker={ labels.forEach { (a,v) -> val seconds=((a.snoozeUntil-System.currentTimeMillis())/1000).coerceAtLeast(0); v.text=String.format("%02d:%02d:%02d",seconds/3600,seconds/60%60,seconds%60) } }; ticker?.invoke()
    }
    private fun stopwatch() {
        val boot=Settings.Global.getInt(contentResolver,Settings.Global.BOOT_COUNT,0)
        if(prefs.getInt("stopwatchBoot",boot)!=boot) prefs.edit().putBoolean("running",false).putLong("elapsed",0).putString("laps", "").apply()
        prefs.edit().putInt("stopwatchBoot",boot).apply()
        fun elapsed(): Long = prefs.getLong("elapsed",0)+if(prefs.getBoolean("running",false)) SystemClock.elapsedRealtime()-prefs.getLong("started",SystemClock.elapsedRealtime()) else 0
        val display=text("",if(watch) 32f else 58f).apply { gravity=Gravity.CENTER; typeface=Typeface.MONOSPACE }; card().addView(display)
        ticker={ val ms=elapsed(); display.text=String.format("%02d:%02d.%02d",ms/60_000,ms/1000%60,ms/10%100) }; ticker?.invoke()
        val running=prefs.getBoolean("running",false)
        content.addView(button(if(running) "Pause" else "Start") {
            if(running) prefs.edit().putLong("elapsed",elapsed()).putBoolean("running",false).apply()
            else prefs.edit().putLong("started",SystemClock.elapsedRealtime()).putBoolean("running",true).apply()
            render()
        })
        content.addView(button(if(running) "Lap" else "Reset") {
            if(running) prefs.edit().putString("laps","${elapsed()}|"+prefs.getString("laps","")).apply()
            else prefs.edit().putLong("elapsed",0).putString("laps","").apply()
            render()
        })
        prefs.getString("laps","")!!.split('|').filter { it.isNotBlank() }.take(100).forEachIndexed { index, value ->
            val ms=value.toLong(); content.addView(text("Lap ${index+1}     ${String.format("%02d:%02d.%02d",ms/60_000,ms/1000%60,ms/10%100)}"))
        }
    }
    private fun bedtime() {
        card().apply {
            addView(text("A little more rest",26f)); addView(text("Set a regular wake-up alarm and keep your evenings consistent."))
            addView(text("Sleep goal: ${prefs.getInt("sleepGoal",8)} hours",18f,accent))
            addView(button("Set sleep goal") { val choices=(4..12).map { "$it hours" }.toTypedArray(); AlertDialog.Builder(this@ClockActivity).setItems(choices) { _, i -> prefs.edit().putInt("sleepGoal",i+4).apply(); render() }.show() })
            addView(button("Set wake-up schedule") { edit(Store(this@ClockActivity).all().firstOrNull { it.label=="Wake up" && !it.deleted } ?: Alarm(label="Wake up",days=setOf(1,2,3,4,5))) })
            Store(this@ClockActivity).all().firstOrNull { it.label=="Wake up" && !it.deleted && it.enabled }?.let { a ->
                val bedtime=LocalTime.of(a.hour,a.minute).minusHours(prefs.getInt("sleepGoal",8).toLong())
                addView(text("Aim for bed at ${bedtime.format(DateTimeFormatter.ofPattern("HH:mm"))}",20f,accent))
            }
        }
    }
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if(watch && event.action==MotionEvent.ACTION_SCROLL && event.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) {
            scroll.smoothScrollBy(0,(-event.getAxisValue(MotionEvent.AXIS_SCROLL)*dp(40)).toInt()); return true
        }
        return super.onGenericMotionEvent(event)
    }
}
