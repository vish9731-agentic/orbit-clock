package dev.orbit.clock.bezelprobe;

import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Test-only instrumentation: uses actual alarm delivery and real rotary MotionEvents. */
public final class BezelUiProbe extends Instrumentation {
    private static final String PKG = "dev.orbit.clock";
    private static final String MAIN = "dev.orbit.clock.wear.MainActivity";
    private static final String RING = "dev.orbit.clock.core.RingActivity";
    private static final String EXIT = "dev.orbit.clock.core.WatchExitActivity";
    private volatile Activity resumed;
    private Context target;
    private File out;
    private final List<String> checks = new ArrayList<>();
    private float density;
    private long injectedDownTime;
    private String fixtureId = "";
    private long fixtureAt;
    private final List<JSONArray> exitMotions = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final List<Bitmap> exitFrames = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final List<Bitmap> faceFrames = new ArrayList<>();
    private void observeExit(Activity activity) {
        View overlay=find(activity.getWindow().getDecorView(),null,"watch_exit_reveal");
        if(overlay==null) return;
        JSONArray samples=new JSONArray(); exitMotions.add(samples);
        final boolean[] captured={false,false}, drawing={false};
        overlay.getViewTreeObserver().addOnDrawListener(() -> {
            if(drawing[0]) return;
            try {
                float p=((Number)field(overlay,"revealProgress")).floatValue();
                float radius=((Number)field(overlay,"revealRadius")).floatValue();
                float alpha=((Number)field(overlay,"sceneAlpha")).floatValue();
                samples.put(new JSONArray().put(p).put(radius).put(alpha));
                if(exitFrames.size()<2 && ((!captured[0] && p>.22f) || (!captured[1] && p>.60f))) {
                    if(p>.60f) captured[1]=true; else captured[0]=true;
                    drawing[0]=true;
                    Bitmap image=Bitmap.createBitmap(overlay.getWidth(),overlay.getHeight(),Bitmap.Config.ARGB_8888);
                    activity.getWindow().getDecorView().draw(new Canvas(image)); exitFrames.add(image);
                    drawing[0]=false;
                }
            } catch(Exception failure) { throw new AssertionError(failure); }
        });
    }
    private void homeAfterAction() throws Exception {
        android.content.pm.ResolveInfo home=target.getPackageManager().resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),android.content.pm.PackageManager.MATCH_DEFAULT_ONLY);
        require(home!=null,"System HOME component was not resolved");
        String homeComponent=new android.content.ComponentName(home.activityInfo.packageName,home.activityInfo.name).flattenToShortString();
        waitUntil(() -> {
            if(android.animation.ValueAnimator.areAnimatorsEnabled() && faceFrames.size()<2) {
                boolean visible=ui(() -> {
                    if(resumed==null || !resumed.getClass().getName().equals(EXIT)) return false;
                    View reveal=find(resumed.getWindow().getDecorView(),null,"watch_exit_reveal");
                    if(reveal==null) return false;
                    float progress=((Number)field(reveal,"revealProgress")).floatValue();
                    return progress>.18f && progress<.80f;
                });
                if(visible) { Bitmap frame=getUiAutomation().takeScreenshot(); if(frame!=null) faceFrames.add(frame); }
            }
            String activities=shell("dumpsys activity activities");
            for(String line:activities.split("\\n")) if(line.contains("topResumedActivity=") && line.contains(homeComponent)) return true;
            return false;
        },7000,"Completed action did not reveal the resolved system watch face: "+homeComponent);
        require(ui(() -> resumed==null || find(resumed.getWindow().getDecorView(),null,"watch_exit_reveal")==null || resumed.isDestroyed()),"Exit overlay remained above the watch face");
    }

    private interface Task<T> { T call() throws Exception; }
    private interface Condition { boolean ready() throws Exception; }
    private <T> T ui(Task<T> task) {
        if (android.os.Looper.myLooper()==android.os.Looper.getMainLooper()) {
            try { return task.call(); } catch(Exception failure) { throw new AssertionError("UI operation failed",failure); }
        }
        AtomicReference<T> value = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        runOnMainSync(() -> { try { value.set(task.call()); } catch (Throwable t) { failure.set(t); } });
        if (failure.get() != null) throw new AssertionError("UI operation failed", failure.get());
        return value.get();
    }
    private void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private void waitUntil(Condition condition, long millis, String message) throws Exception {
        long until = SystemClock.elapsedRealtime() + millis;
        while (SystemClock.elapsedRealtime() < until) {
            if (condition.ready()) return;
            SystemClock.sleep(25);
        }
        throw new AssertionError(message);
    }
    private void pass(String value) {
        checks.add(value);
        Bundle status = new Bundle(); status.putString("stream", "BEZEL PASS: " + value + "\n"); sendStatus(0, status);
    }
    private Object field(Object object, String name) throws Exception {
        Field f = object.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(object);
    }
    private SharedPreferences prefs(String name) { return target.getSharedPreferences(name, Context.MODE_PRIVATE); }
    private JSONArray records() throws Exception { return new JSONArray(prefs("alarms").getString("records", "[]")); }
    private JSONObject record(String id) throws Exception {
        JSONArray all = records();
        for (int i = 0; i < all.length(); i++) if (all.getJSONObject(i).getString("id").equals(id)) return all.getJSONObject(i);
        throw new AssertionError("Missing record " + id);
    }
    private View find(View root, String desc, String tag) {
        if ((desc != null && desc.contentEquals(root.getContentDescription() == null ? "" : root.getContentDescription())) ||
            (tag != null && tag.equals(root.getTag()))) return root;
        if (root instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) { View found = find(group.getChildAt(i), desc, tag); if (found != null) return found; }
        }
        return null;
    }
    private View control(Activity a, String desc) { View result = ui(() -> find(a.getWindow().getDecorView(), desc, null)); require(result != null, "Missing control " + desc); return result; }
    private View actions(Activity a) { View result = ui(() -> find(a.getWindow().getDecorView(), null, "watch_ring_slider")); require(result != null, "Missing watch-specific action component"); return result; }
    private int progress(View actions) { return ui(() -> ((Number) field(actions, "currentProgress")).intValue()); }
    private boolean snoozing(View actions) { return ui(() -> (Boolean) field(actions, "currentSnoozing")); }
    private void selected(Activity a, int minutes) { control(a, "Snooze duration: " + minutes + " minutes"); }
    private void click(View v) {
        require(ui(v::isEnabled), "Disabled control was selected");
        int[] point = ui(() -> { int[] p = new int[2]; v.getLocationOnScreen(p); return new int[]{p[0] + v.getWidth()/2, p[1] + v.getHeight()/2}; });
        long now = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, point[0], point[1], 0);
        MotionEvent up = MotionEvent.obtain(now, now + 40, MotionEvent.ACTION_UP, point[0], point[1], 0);
        down.setSource(InputDevice.SOURCE_TOUCHSCREEN); up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        sendPointerSync(down); sendPointerSync(up); down.recycle(); up.recycle(); waitForIdleSync();
    }
    private void rotate(Activity a, float amount) {
        ui(() -> {
            MotionEvent.PointerProperties properties = new MotionEvent.PointerProperties(); properties.id = 0; properties.toolType = MotionEvent.TOOL_TYPE_UNKNOWN;
            MotionEvent.PointerCoords coords = new MotionEvent.PointerCoords(); coords.setAxisValue(MotionEvent.AXIS_SCROLL, amount);
            long now = SystemClock.uptimeMillis();
            MotionEvent event = MotionEvent.obtain(now, now, MotionEvent.ACTION_SCROLL, 1,
                new MotionEvent.PointerProperties[]{properties}, new MotionEvent.PointerCoords[]{coords},
                0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_ROTARY_ENCODER, 0);
            require(a.dispatchGenericMotionEvent(event), "Rotary event was not handled"); event.recycle(); return null;
        });
    }
    private Bitmap componentBitmap(View view) { return ui(() -> { Bitmap b = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888); view.draw(new Canvas(b)); return b; }); }
    private float changedRadius(Bitmap baseline, Bitmap changed, View container, View child, boolean left) {
        Rect targetRect = ui(() -> { Rect r = new Rect(); child.getDrawingRect(r); ((ViewGroup) container).offsetDescendantRectToMyCoords(child, r); return r; });
        int count = 0; float furthest = 0;
        for (int y = 0; y < changed.getHeight(); y++) for (int x = 0; x < changed.getWidth(); x++) {
            if ((x < changed.getWidth()/2) != left) continue;
            // Measure horizontal aura wings, avoiding the separate curved-arrow tip.
            if (Math.abs(y-targetRect.exactCenterY())>3*density) continue;
            int p = baseline.getPixel(x,y), q = changed.getPixel(x,y);
            int difference = Math.max(Math.abs(((p>>16)&255)-((q>>16)&255)), Math.max(Math.abs(((p>>8)&255)-((q>>8)&255)),Math.abs((p&255)-(q&255))));
            if (difference > 4) { count++; furthest = Math.max(furthest, (float)Math.hypot(x-targetRect.exactCenterX(), y-targetRect.exactCenterY())); }
        }
        require(count > 20, "No growing aura pixels on selected side"); return furthest;
    }
    private void noChangedPixels(Bitmap baseline, Bitmap changed, boolean left) {
        int count = 0;
        for (int y=0;y<changed.getHeight();y++) for(int x=0;x<changed.getWidth();x++) {
            if ((x<changed.getWidth()/2)!=left) continue;
            int a=baseline.getPixel(x,y), b=changed.getPixel(x,y);
            if (Math.abs(((a>>16)&255)-((b>>16)&255))>4 || Math.abs(((a>>8)&255)-((b>>8)&255))>4 || Math.abs((a&255)-(b&255))>4) count++;
        }
        require(count < 12, "Inactive action unexpectedly grew: " + count + " changed pixels");
    }
    private List<String> visibleTexts(View view) {
        List<String> texts = new ArrayList<>();
        if (view.getVisibility()!=View.VISIBLE) return texts;
        if (view instanceof TextView && ((TextView)view).getText().length()>0) texts.add(((TextView)view).getText().toString());
        if(view instanceof ViewGroup) for(int i=0;i<((ViewGroup)view).getChildCount();i++) texts.addAll(visibleTexts(((ViewGroup)view).getChildAt(i)));
        return texts;
    }
    private void displayScreenshot(String name) throws Exception {
        Bitmap bitmap=getUiAutomation().takeScreenshot(); require(bitmap!=null,"Screenshot unavailable");
        try(FileOutputStream stream=new FileOutputStream(new File(out,name+".png"))) { bitmap.compress(Bitmap.CompressFormat.PNG,100,stream); }
        bitmap.recycle();
    }
    private void screenshot(String name) throws Exception {
        // Draw the running activity's real views while progress is current. Surface
        // screenshot/compression delays must not race the intentional 1500ms reset.
        Bitmap bitmap=ui(() -> { View root=resumed.getWindow().getDecorView(); Bitmap b=Bitmap.createBitmap(root.getWidth(),root.getHeight(),Bitmap.Config.ARGB_8888); root.draw(new Canvas(b)); return b; });
        try(FileOutputStream stream=new FileOutputStream(new File(out,name+".png"))) { bitmap.compress(Bitmap.CompressFormat.PNG,100,stream); }
        bitmap.recycle();
    }
    private void save(String name, String text) throws Exception { try(FileOutputStream stream=new FileOutputStream(new File(out,name))) { stream.write(text.getBytes("UTF-8")); } }
    private String shell(String command) throws Exception {
        try(ParcelFileDescriptor descriptor=getUiAutomation().executeShellCommand(command); InputStream input=new ParcelFileDescriptor.AutoCloseInputStream(descriptor)) {
            java.io.ByteArrayOutputStream buffer=new java.io.ByteArrayOutputStream(); byte[] chunk=new byte[8192]; int size;
            while((size=input.read(chunk))!=-1) buffer.write(chunk,0,size); return buffer.toString("UTF-8");
        }
    }
    private String bounded(String text, int maximum) { return text.length()<=maximum ? text : text.substring(0,maximum)+"\n[truncated]\n"; }
    private String failureDiagnostics() {
        StringBuilder detail=new StringBuilder("\nBEZEL DIAGNOSTICS\n");
        detail.append("wallMillis=").append(System.currentTimeMillis()).append(" wallDate=").append(new java.util.Date()).append(" elapsedMillis=").append(SystemClock.elapsedRealtime()).append('\n');
        detail.append("fixtureId=").append(fixtureId).append(" fixtureAt=").append(fixtureAt).append('\n');
        detail.append("records=").append(bounded(prefs("alarms").getString("records","[]"),12000)).append('\n');
        detail.append("scheduled_occurrences=").append(bounded(prefs("scheduled_occurrences").getAll().toString(),12000)).append('\n');
        try {
            String alarm=shell("dumpsys alarm"); save("failure-alarm.txt",alarm);
            String[] lines=alarm.split("\\n"); boolean[] include=new boolean[lines.length];
            for(int i=0;i<Math.min(30,lines.length);i++) include[i]=true;
            for(int i=0;i<lines.length;i++) if(lines[i].contains(PKG)||lines[i].contains("bezel-"))
                for(int j=Math.max(0,i-8);j<Math.min(lines.length,i+9);j++) include[j]=true;
            detail.append("ORBIT ALARM SEGMENTS\n"); int emitted=0;
            for(int i=0;i<lines.length && emitted<140;i++) if(include[i]) { detail.append(lines[i]).append('\n'); emitted++; }
        } catch(Exception failure) { detail.append("Alarm dump failed: ").append(failure).append('\n'); }
        try {
            detail.append("TOP ACTIVITIES\n");
            for(String line:shell("dumpsys activity activities").split("\\n"))
                if(line.contains("topResumedActivity")||line.contains("ResumedActivity:")||line.contains("mFocusedApp=")||line.contains("mFocusedWindow=")||line.contains("state=RESUMED")) detail.append(line).append('\n');
        } catch(Exception failure) { detail.append("Activity dump failed: ").append(failure).append('\n'); }
        try { detail.append("RECENT LOGCAT\n").append(bounded(shell("logcat -d -v threadtime -t 160"),24000)).append('\n'); }
        catch(Exception failure) { detail.append("Logcat failed: ").append(failure).append('\n'); }
        return bounded(detail.toString(),50000);
    }
    private Activity delivered(String id) throws Exception {
        // Complete cold-start/configuration work before choosing a short deadline.
        // Scheduling through production Store.save is the same path used by the editor.
        ui(() -> {
            if(resumed==null || !resumed.getClass().getName().equals(MAIN) || resumed.isFinishing() || !resumed.hasWindowFocus())
                target.startActivity(new Intent().setClassName(PKG,MAIN).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP));
            return null;
        });
        long readinessStarted=SystemClock.elapsedRealtime();
        long stableSince=SystemClock.elapsedRealtime();
        String stableConfiguration="";
        // Fresh Wear OS images show TrayInitializationOverlay ("Starting...")
        // after boot_completed. Wait for OS readiness before choosing a deadline.
        long until=SystemClock.elapsedRealtime()+90000;
        while(SystemClock.elapsedRealtime()<until) {
            String configuration=ui(() -> resumed!=null && resumed.getClass().getName().equals(MAIN) && !resumed.isFinishing() && resumed.hasWindowFocus() && resumed.getWindow().getDecorView().isLaidOut()
                ? System.identityHashCode(resumed)+":"+resumed.getResources().getConfiguration().toString() : "");
            if(configuration.isEmpty() || !configuration.equals(stableConfiguration)) { stableConfiguration=configuration; stableSince=SystemClock.elapsedRealtime(); }
            else if(SystemClock.elapsedRealtime()-stableSince>=2000) break;
            SystemClock.sleep(100);
        }
        require(!stableConfiguration.isEmpty() && SystemClock.elapsedRealtime()-stableSince>=2000,"Wear OS startup did not release a focused, stable MainActivity");
        Bundle setup=new Bundle(); setup.putString("stream","BEZEL SETUP Main ready after "+(SystemClock.elapsedRealtime()-readinessStarted)+" ms\n"); sendStatus(0,setup);
        long at=ui(() -> {
            long deadline=System.currentTimeMillis()+20000;
            fixtureId=id; fixtureAt=deadline;
            JSONObject alarm=new JSONObject().put("id",id).put("hour",7).put("minute",0).put("label","Watch alarm").put("timer",true)
                .put("days",new JSONArray()).put("enabled",true).put("vibrate",false).put("sound",false).put("snoozeMinutes",10)
                .put("snoozeUntil",deadline).put("skipThrough",0).put("deleted",false).put("revision",System.currentTimeMillis()).put("writer","bezel-ui-probe");
            ClassLoader loader=target.getClassLoader();
            Class<?> storeClass=Class.forName("dev.orbit.clock.core.Store",true,loader);
            Class<?> alarmClass=Class.forName("dev.orbit.clock.core.Alarm",true,loader);
            Object companion=storeClass.getField("Companion").get(null);
            Object decoded=companion.getClass().getMethod("decode",JSONObject.class).invoke(companion,alarm);
            Object store=storeClass.getConstructor(Context.class).newInstance(target);
            storeClass.getMethod("save",alarmClass).invoke(store,decoded);
            return deadline;
        });
        waitUntil(() -> prefs("scheduled_occurrences").getLong(id+".scheduled",0)==at,10000,"Real timer was not scheduled");
        waitUntil(() -> prefs("scheduled_occurrences").getLong(id+".active",0)==at,30000,"Real AlarmReceiver did not accept timer");
        ui(() -> { target.startActivity(new Intent().setClassName(PKG,RING).putExtra("id",id).putExtra("at",at).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP)); return null; });
        waitUntil(() -> ui(() -> resumed!=null && resumed.getClass().getName().equals(RING) && id.equals(resumed.getIntent().getStringExtra("id")) && !resumed.isFinishing() && resumed.hasWindowFocus()),10000,"RingActivity did not become focused");
        // An automatic full-screen intent may already be opening/recreating the
        // same occurrence. Wait for that transaction before retaining the view.
        SystemClock.sleep(300);
        waitUntil(() -> ui(() -> resumed!=null && resumed.getClass().getName().equals(RING) && id.equals(resumed.getIntent().getStringExtra("id")) && !resumed.isFinishing() && resumed.hasWindowFocus()),10000,"Ringing transaction did not settle");
        Activity result=resumed; waitUntil(() -> ui(() -> actions(result).getWidth()>0),3000,"Action component not laid out");
        return result;
    }
    private Activity recreate(Activity old) throws Exception {
        resumed=null; ui(() -> { old.recreate(); return null; });
        waitUntil(() -> ui(() -> resumed!=null && resumed!=old && resumed.getClass().getName().equals(RING) && !resumed.isFinishing() && resumed.hasWindowFocus()),10000,"Ringing recreation did not resume");
        Activity result=resumed; waitUntil(() -> ui(() -> actions(result).getWidth()>0),3000,"Recreated actions not laid out"); return result;
    }

    private View sliderThumb(Activity a) { return ui(() -> (View) field(actions(a), "thumb")); }
    private float position(View thumb) { return ui(thumb::getTranslationX); }
    private float travel(View rail, View thumb) { return ui(() -> ((Number)field(rail,"radius")).floatValue()); }
    private float arcOffset(float span,float fraction) { return span*(float)Math.sin(fraction*Math.PI/2); }
    private void settleAt(View thumb, float target) throws Exception {
        waitUntil(() -> Math.abs(position(thumb)-target)<density,1200,"Slider did not settle at its input target");
    }
    private void motionSamples(View thumb, float from, float target, String name) throws Exception {
        JSONArray samples=new JSONArray();
        android.view.ViewTreeObserver.OnDrawListener observer=() -> { try { samples.put(thumb.getTranslationX()); } catch(org.json.JSONException failure) { throw new AssertionError(failure); } };
        ui(() -> { thumb.getViewTreeObserver().addOnDrawListener(observer); return null; });
        SystemClock.sleep(1100);
        ui(() -> { if(thumb.getViewTreeObserver().isAlive()) thumb.getViewTreeObserver().removeOnDrawListener(observer); return null; });
        java.util.Set<Integer> intermediate=new java.util.HashSet<>();
        for(int i=0;i<samples.length();i++) {
            float value=(float)samples.getDouble(i);
            if(Math.abs(value-from)>.5f && Math.abs(value-target)>.5f) intermediate.add(Math.round(value*2));
        }
        save(name+".json",samples.toString());
        require(ui(() -> thumb.getRotation()==0f),"Glyph must stay upright while the vector pill follows the arc");
        require(intermediate.size()>=2,"Arc did not render multiple intermediate positions: "+samples);
        settleAt(thumb,target);
    }
    private void pointer(int action,float x,float y) {
        long now=SystemClock.uptimeMillis();
        if(action==MotionEvent.ACTION_DOWN) injectedDownTime=now;
        MotionEvent e=MotionEvent.obtain(injectedDownTime,now,action,x,y,0);
        e.setSource(InputDevice.SOURCE_TOUCHSCREEN); sendPointerSync(e); e.recycle();
    }
    private void partialSwipe(Activity a, boolean cancel) throws Exception {
        View thumb=sliderThumb(a), rail=actions(a);
        float[] p=ui(() -> { int[] xy=new int[2]; thumb.getLocationOnScreen(xy); return new float[]{xy[0]+thumb.getWidth()/2f,xy[1]+thumb.getHeight()/2f,thumb.getTranslationX(),((Number)field(rail,"fraction")).floatValue()}; });
        float x=p[0],y=p[1]; pointer(MotionEvent.ACTION_DOWN,x,y);
        require(Math.abs(position(thumb)-p[2])<5*density,"Catching the moving arc thumb jumped");
        SystemClock.sleep(45); float span=travel(rail,thumb);
        pointer(MotionEvent.ACTION_MOVE,x+span*.20f,y);
        require(Math.abs(position(thumb)-arcOffset(span,Math.min(1f,p[3]+.20f)))<3*density,"Arc touch fallback did not follow input");
        SystemClock.sleep(45); pointer(cancel?MotionEvent.ACTION_CANCEL:MotionEvent.ACTION_UP,x+span*.20f,y);
    }
    private View findClock(View root) {
        String desc=root.getContentDescription()==null?"":root.getContentDescription().toString();
        if(desc.startsWith("Alarm time: ")) return root;
        if(root instanceof ViewGroup) for(int i=0;i<((ViewGroup)root).getChildCount();i++) {
            View found=findClock(((ViewGroup)root).getChildAt(i)); if(found!=null) return found;
        }
        return null;
    }
    private void layout(Activity a) {
        View rail=actions(a), thumb=sliderThumb(a);
        require(rail.getClass().getName().equals("dev.orbit.clock.core.WatchArcControl"),"Semicircular watch control missing");
        for(String desc:new String[]{"Decrease snooze by 5 minutes","Increase snooze by 5 minutes"}) {
            View v=control(a,desc);
            require(ui(() -> v.getWidth()>=55.5f*density && v.getHeight()>=55.5f*density),"Duration tap target shrank");
        }
        TextView clock=(TextView)ui(() -> findClock(a.getWindow().getDecorView()));
        TextView label=(TextView)control(a,"Alarm label: Watch alarm");
        TextView minutes=(TextView)ui(() -> findDuration(a.getWindow().getDecorView()));
        require(clock!=null && minutes!=null,"Clock or duration missing");
        require(ui(() -> clock.getTextSize()>label.getTextSize()*1.6f && label.getTextSize()>minutes.getTextSize()*1.2f),"Time/label/duration visual hierarchy is incorrect");
        require(ui(() -> {
            int[] c=new int[2],l=new int[2],m=new int[2]; clock.getLocationOnScreen(c); label.getLocationOnScreen(l); minutes.getLocationOnScreen(m);
            return c[1]+clock.getHeight()<=l[1]+density && l[1]+label.getHeight()<=m[1]+density;
        }),"Time, label and adjustment row overlap");
        require(ui(() -> thumb.getTranslationY()<=density),"Arc thumb left the lower semicircle");
        control(a,"Snooze"); control(a,"Dismiss");
    }
    private View findDuration(View root) {
        String desc=root.getContentDescription()==null?"":root.getContentDescription().toString();
        if(desc.startsWith("Snooze duration: ")) return root;
        if(root instanceof ViewGroup) for(int i=0;i<((ViewGroup)root).getChildCount();i++) {
            View found=findDuration(((ViewGroup)root).getChildAt(i)); if(found!=null) return found;
        }
        return null;
    }
    private void exercise() throws Exception {
        require(target.getPackageManager().hasSystemFeature("android.hardware.type.watch"),"Wear OS required");
        require(android.animation.ValueAnimator.areAnimatorsEnabled(),"Motion verification requires animations enabled");
        Activity a=delivered("slider-snooze");
        layout(a); selected(a,10);
        click(control(a,"Increase snooze by 5 minutes")); selected(a,15);
        a=recreate(a); selected(a,15); layout(a);
        require(record("slider-snooze").getInt("snoozeMinutes")==10,"Duration changed saved default");
        pass("Time dominates label and compact duration; 56dp adjustment targets, semicircular rail and recreated selection fit watch");
        displayScreenshot("display-idle"); screenshot("01-idle");
        View rail=actions(a), thumb=sliderThumb(a);
        float span=travel(rail,thumb);
        rotate(a,1);
        motionSamples(thumb,0,arcOffset(span,-1f/3f),"left-motion");
        require(record("slider-snooze").getLong("snoozeUntil")<System.currentTimeMillis(),"A partial bezel turn fired an action");
        require(ui(() -> sliderThumb(resumed).getTranslationY() < -2*density),"Thumb did not follow the curved rail");
        screenshot("02-left-partial");
        float from=position(thumb); rotate(a,-1);
        motionSamples(thumb,from,arcOffset(span,1f/6f),"reverse-motion");
        screenshot("03-reversal");
        pass("Bezel detents produce continuous intermediate positions and interruptible directional reversal");
        SystemClock.sleep(2200); settleAt(thumb,0);
        require(record("slider-snooze").getLong("snoozeUntil")<System.currentTimeMillis(),"Spring inertia snoozed alarm");
        partialSwipe(a,false); SystemClock.sleep(60); partialSwipe(a,true);
        SystemClock.sleep(1200); settleAt(thumb,0);
        require(record("slider-snooze").getBoolean("enabled") && record("slider-snooze").getLong("snoozeUntil")<System.currentTimeMillis(),"Partial swipe/return unexpectedly fired an action");
        pass("Idle and partial touch releases settle gracefully; moving thumb can be caught without jumping; inertia never acts");
        SystemClock.sleep(1700);
        rotate(a,1); rotate(a,1);
        require(record("slider-snooze").getLong("snoozeUntil")<System.currentTimeMillis(),"Snooze fired before three units");
        long before=System.currentTimeMillis(); rotate(a,1); long after=System.currentTimeMillis();
        final long earliest=before+15*60000L;
        waitUntil(() -> record("slider-snooze").getLong("snoozeUntil")>=earliest,3000,"Anticlockwise snooze failed");
        JSONObject changed=record("slider-snooze");
        require(changed.getLong("snoozeUntil")<=after+15*60000L+1000 && changed.getInt("snoozeMinutes")==10,"Selected snooze duration not preserved");
        homeAfterAction();
        pass("Anticlockwise threshold commits selected snooze, then translucent expansion returns to watch face");
        a=delivered("slider-dismiss"); rail=actions(a); thumb=sliderThumb(a); span=travel(rail,thumb);
        rotate(a,-3); settleAt(thumb,arcOffset(span,.5f)); screenshot("04-right-partial");
        require(record("slider-dismiss").getBoolean("enabled"),"Dismiss fired early");
        rotate(a,-2); require(record("slider-dismiss").getBoolean("enabled"),"Dismiss fired before six units");
        rotate(a,-1);
        waitUntil(() -> !record("slider-dismiss").getBoolean("enabled"),3000,"Clockwise six-unit dismiss failed");
        homeAfterAction();
        pass("Clockwise follows arc, retains six-unit dismissal and transitions to watch face");
        a=delivered("slider-limits");
        click(control(a,"Decrease snooze by 5 minutes")); selected(a,5);
        require(!ui(control(a,"Decrease snooze by 5 minutes")::isEnabled),"Minimum control enabled");
        for(int i=0;i<11;i++)click(control(a,"Increase snooze by 5 minutes"));
        selected(a,60); require(!ui(control(a,"Increase snooze by 5 minutes")::isEnabled),"Maximum control enabled");
        shell("settings put system font_scale 1.3");
        waitUntil(() -> ui(() -> resumed!=null && resumed.getClass().getName().equals(RING) && resumed.hasWindowFocus() && resumed.getResources().getConfiguration().fontScale>=1.29f),8000,"Large type not applied");
        a=resumed; selected(a,60); layout(a);
        displayScreenshot("display-large-type"); screenshot("06-large-type-60");
        click(control(a,"Dismiss"));
        waitUntil(() -> !record("slider-limits").getBoolean("enabled"),3000,"Touch Stop failed");
        homeAfterAction();
        pass("Duration limits and 130 percent text fit watch; native Stop fallback transitions to watch face");
        shell("settings put system font_scale 1.0"); SystemClock.sleep(600);
        a=delivered("slider-touch-snooze"); click(control(a,"Increase snooze by 5 minutes")); selected(a,15);
        shell("settings put global animator_duration_scale 0");
        waitUntil(() -> !android.animation.ValueAnimator.areAnimatorsEnabled(),3000,"Reduced motion not applied");
        rotate(a,.5f); settleAt(sliderThumb(a),arcOffset(travel(actions(a),sliderThumb(a)),-1f/6f));
        click(control(a,"Snooze"));
        require(record("slider-touch-snooze").getLong("snoozeUntil")>System.currentTimeMillis()+14*60000L,"Touch Snooze failed");
        homeAfterAction();
        require(ui(() -> find(resumed.getWindow().getDecorView(),null,"watch_exit_reveal")==null),"Reduced motion left a reveal overlay");
        require(exitMotions.size()>=2,"Native completion transitions were not observed");
        for(int i=0;i<exitMotions.size();i++) {
            JSONArray samples=exitMotions.get(i); save("exit-motion-"+i+".json",samples.toString());
            require(samples.length()>=3,"Transition did not produce intermediate frames");
            float firstRadius=(float)samples.getJSONArray(0).getDouble(1);
            float lastRadius=(float)samples.getJSONArray(samples.length()-1).getDouble(1);
            float lastAlpha=(float)samples.getJSONArray(samples.length()-1).getDouble(2);
            require(lastRadius>firstRadius+50*density && lastAlpha<.05f,"Expansion/transparency did not progress to home");
        }
        for(int i=0;i<exitFrames.size();i++) try(FileOutputStream stream=new FileOutputStream(new File(out,"07-exit-"+i+".png"))) { exitFrames.get(i).compress(Bitmap.CompressFormat.PNG,100,stream); exitFrames.get(i).recycle(); }
        for(int i=0;i<faceFrames.size();i++) try(FileOutputStream stream=new FileOutputStream(new File(out,"08-actual-face-"+i+".png"))) { faceFrames.get(i).compress(Bitmap.CompressFormat.PNG,100,stream); faceFrames.get(i).recycle(); }
        pass("Native Snooze fallback, rendered expansion/fade frames, exact system HOME destination, cleanup and reduced-motion immediate watch-face return");
    }
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    @Override public void onStart() {
        target=getTargetContext(); density=target.getResources().getDisplayMetrics().density;
        out=new File(target.getExternalFilesDir(null),"bezel-results"); out.mkdirs();
        Application app=(Application)target.getApplicationContext();
        app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            public void onActivityCreated(Activity a,Bundle s) { }
            public void onActivityStarted(Activity a) { }
            public void onActivityResumed(Activity a) { resumed=a; }
            public void onActivityPostResumed(Activity a) { if(a.getClass().getName().equals(EXIT)) observeExit(a); }
            public void onActivityPaused(Activity a) { }
            public void onActivityStopped(Activity a) { }
            public void onActivitySaveInstanceState(Activity a,Bundle s) { }
            public void onActivityDestroyed(Activity a) { }
        });
        Bundle result=new Bundle(); int code=Activity.RESULT_CANCELED;
        try { exercise(); result.putBoolean("bezelChecksPassed",true); result.putInt("checks",checks.size()); code=Activity.RESULT_OK; }
        catch(Throwable failure) {
            java.io.StringWriter trace=new java.io.StringWriter(); failure.printStackTrace(new java.io.PrintWriter(trace));
            result.putString("stream","BEZEL FAILURE: "+trace+"\n"+failureDiagnostics());
            try { displayScreenshot("failure"); save("failure.txt",trace.toString()); save("failure-activities.txt",shell("dumpsys activity activities")); save("failure-logcat.txt",shell("logcat -d -t 400")); } catch(Exception ignored) { }
        }
        finally {
            try { save("checks.json",new JSONArray(checks).toString(2)); save("alarms.json",records().toString(2)); save("crashes.txt",shell("logcat -d -b crash")); shell("settings put system font_scale 1.0"); shell("settings put global animator_duration_scale 1"); } catch(Exception ignored) { }
        }
        finish(code,result);
    }
}
