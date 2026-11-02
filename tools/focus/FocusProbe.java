package dev.orbit.clock.focusprobe;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import java.lang.reflect.*;

/** Same-key, separate test APK. No exported production test hooks. */
public class FocusProbe extends Instrumentation {
    private Context c;
    private Activity activity;
    private Class<?> store;
    private Object singleton;
    private int checks;
    private Object call(String method, Class<?>[] types, Object... args) throws Exception {
        return store.getMethod(method, types).invoke(singleton, args);
    }
    private JSONObject state() throws Exception {
        return new JSONObject(c.getSharedPreferences("focus",0).getString("session", "{}"));
    }
    private void require(boolean good, String label) { if (!good) throw new AssertionError(label); checks++; System.out.println("PASS " + label); }
    private void ui(Runnable action) { runOnMainSync(action); waitForIdleSync(); }
    private View find(View v, String text) {
        if(v instanceof TextView && text.equals(((TextView)v).getText().toString())) return v;
        if(v instanceof ViewGroup) for(int i=0;i<((ViewGroup)v).getChildCount();i++) { View f=find(((ViewGroup)v).getChildAt(i), text); if(f!=null)return f; }
        return null;
    }
    private void tap(String text) {
        ui(() -> { View v=find(activity.getWindow().getDecorView(),text); if(v==null)throw new AssertionError("Missing " + text); v.performClick(); });
    }
    private void act(String action) throws Exception { ui(() -> { try { call("act",new Class[]{Context.class,String.class},c,action); }catch(Exception e){throw new RuntimeException(e);} }); }
    private void rotate(float delta) {
        ui(() -> {
            MotionEvent.PointerProperties p=new MotionEvent.PointerProperties(); p.id=0;
            MotionEvent.PointerCoords q=new MotionEvent.PointerCoords(); q.setAxisValue(MotionEvent.AXIS_SCROLL,delta);
            long t=SystemClock.uptimeMillis();
            MotionEvent e=MotionEvent.obtain(t,t,MotionEvent.ACTION_SCROLL,1,new MotionEvent.PointerProperties[]{p},new MotionEvent.PointerCoords[]{q},0,0,1,1,0,0,InputDevice.SOURCE_ROTARY_ENCODER,0);
            activity.dispatchGenericMotionEvent(e); e.recycle();
        });
    }
    @Override public void onStart() {
        Bundle result=new Bundle();
        try {
            c=getTargetContext(); store=c.getClassLoader().loadClass("dev.orbit.clock.core.FocusStore"); singleton=store.getField("INSTANCE").get(null);
            c.getSharedPreferences("focus",0).edit().clear().commit();
            activity=startActivitySync(new Intent().setClassName(c,"dev.orbit.clock.core.FocusActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            SystemClock.sleep(1200); waitForIdleSync();
            boolean watch=c.getPackageManager().hasSystemFeature("android.hardware.type.watch");
            tap("Durations");
            // Dialog fields are tested by setting configuration through the production store after dismissing.
            sendKeyDownUpSync(KeyEvent.KEYCODE_BACK); SystemClock.sleep(300);
            ui(() -> { try { call("configure",new Class[]{Context.class,int.class,int.class,int.class},c,1,1,2); }catch(Exception e){throw new RuntimeException(e);} });
            if(watch) {
                rotate(-1); require("Ready".equals(state().getString("status")),"single detent cannot start");
                rotate(-2); require("Running".equals(state().getString("status")),"clockwise starts");
                rotate(6); require("Running".equals(state().getString("status")),"continued rotation latched");
                SystemClock.sleep(750); rotate(3); require("Paused".equals(state().getString("status")),"anticlockwise pauses");
                SystemClock.sleep(750); rotate(-3); require("Running".equals(state().getString("status")),"clockwise resumes");
            } else tap("Start focus");
            require("Running".equals(state().getString("status")),"starts running");
            SystemClock.sleep(1500); tap("Pause");
            long left=state().getLong("remaining"); require(left>0 && left<60000,"pause preserves remaining time");
            tap("Resume"); require("Running".equals(state().getString("status")),"touch resumes");
            tap("End session"); sendKeyDownUpSync(KeyEvent.KEYCODE_BACK); SystemClock.sleep(300);
            require("Running".equals(state().getString("status")),"cancel end keeps session");
            // Wait for a real one-minute AlarmManager deadline with the activity stopped.
            ui(() -> activity.moveTaskToBack(true));
            NotificationManager progress=c.getSystemService(NotificationManager.class);
            boolean pausedFromNotification=false;
            for(android.service.notification.StatusBarNotification n:progress.getActiveNotifications()) {
                if(n.getId()==9201 && n.getNotification().actions!=null) {
                    n.getNotification().actions[0].actionIntent.send(); SystemClock.sleep(400);
                    pausedFromNotification="Paused".equals(state().getString("status")); break;
                }
            }
            require(pausedFromNotification,"background notification pauses session");
            act("start");
            long limit=SystemClock.uptimeMillis()+80000;
            while(!"Complete".equals(state().optString("status")) && SystemClock.uptimeMillis()<limit) SystemClock.sleep(1000);
            require("Complete".equals(state().getString("status")),"background AlarmManager completion");
            require(state().getInt("completed")==1,"completion counts once");
            NotificationManager nm=c.getSystemService(NotificationManager.class);
            require(nm.getActiveNotifications().length>0,"completion notification delivered");
            activity=startActivitySync(new Intent().setClassName(c,"dev.orbit.clock.core.FocusActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            SystemClock.sleep(800); tap("Start break");
            require("Break".equals(state().getString("phase")),"manual break transition");
            tap("Pause"); long paused=state().getLong("remaining");
            ui(() -> activity.recreate()); SystemClock.sleep(1000);
            require("Paused".equals(state().getString("status")) && paused==state().getLong("remaining"),"recreation preserves pause");
            // Capture native display for internal inspection.
            java.io.File dir=new java.io.File(c.getFilesDir(),"focus-results"); dir.mkdirs();
            android.graphics.Bitmap bitmap=getUiAutomation().takeScreenshot();
            if(bitmap!=null) { java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(dir,"focus.png")); bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out); out.close(); }
            JSONObject old=state();
            JSONObject incoming=new JSONObject(old.toString()).put("revision",old.getLong("revision")+100).put("writer","remote-test").put("status","Paused").put("remaining",42000).put("deadline",0);
            call("merge",new Class[]{Context.class,String.class},c,incoming.toString());
            require(state().getLong("remaining")==42000,"new remote state accepted");
            call("merge",new Class[]{Context.class,String.class},c,old.toString());
            require(state().getLong("remaining")==42000,"stale remote state ignored");
            call("merge",new Class[]{Context.class,String.class},c,incoming.toString());
            require(state().getLong("revision")==incoming.getLong("revision"),"replayed remote state idempotent");
            act("end"); require("Ready".equals(state().getString("status")),"end resets cycle");
            require(state().getInt("focus")==1,"end keeps durations");
            result.putString("stream","PASS " + checks + " Focus runtime checks"); finish(Activity.RESULT_OK,result);
        } catch(Throwable failure) { result.putString("stream",android.util.Log.getStackTraceString(failure)); finish(Activity.RESULT_CANCELED,result); }
    }
    @Override public void onCreate(Bundle b) { super.onCreate(b); start(); }
}
