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
    private volatile Activity activity;
    private Class<?> store;
    private Object singleton;
    private int checks;
    private Object call(String method, Class<?>[] types, Object... args) throws Exception {
        return store.getMethod(method, types).invoke(singleton, args);
    }
    private JSONObject state() throws Exception {
        return new JSONObject(c.getSharedPreferences("focus",0).getString("session", "{}"));
    }
    private void require(boolean good, String label) { if (!good) throw new AssertionError(label); checks++; Bundle progress=new Bundle(); progress.putString("stream","PASS " + label + "\n"); sendStatus(0,progress); }
    private void ui(Runnable action) {
        java.util.concurrent.atomic.AtomicReference<Throwable> error=new java.util.concurrent.atomic.AtomicReference<>();
        runOnMainSync(() -> { try { action.run(); } catch(Throwable failure) { error.set(failure); } });
        if(error.get()!=null) throw new AssertionError("UI action failed",error.get());
        waitForIdleSync();
    }
    private View find(View v, String text) {
        if(v instanceof TextView && text.equals(((TextView)v).getText().toString())) return v;
        if(v instanceof ViewGroup) for(int i=0;i<((ViewGroup)v).getChildCount();i++) { View f=find(((ViewGroup)v).getChildAt(i), text); if(f!=null)return f; }
        return null;
    }
    private void tap(String text) {
        long limit=SystemClock.uptimeMillis()+6000;
        boolean[] clicked={false};
        while(!clicked[0] && SystemClock.uptimeMillis()<limit) {
            ui(() -> { View v=find(activity.getWindow().getDecorView(),text); if(v!=null) { v.performClick(); clicked[0]=true; } });
            if(!clicked[0]) SystemClock.sleep(100);
        }
        if(!clicked[0]) throw new AssertionError("Missing " + text);
    }
    private void tapDialog(String text) {
        long limit=SystemClock.uptimeMillis()+6000;
        boolean[] clicked={false};
        while(!clicked[0] && SystemClock.uptimeMillis()<limit) {
            ui(() -> {
                for(View root:android.view.inspector.WindowInspector.getGlobalWindowViews()) {
                    if(root==activity.getWindow().getDecorView() || !root.isShown()) continue;
                    View v=find(root,text);
                    if(v!=null) { v.performClick(); clicked[0]=true; break; }
                }
            });
            if(!clicked[0]) SystemClock.sleep(100);
        }
        if(!clicked[0]) throw new AssertionError("Missing dialog action " + text);
    }
    private void capture(String name) throws Exception {
        java.io.File dir=new java.io.File(c.getFilesDir(),"focus-results"); dir.mkdirs();
        android.graphics.Bitmap bitmap=getUiAutomation().takeScreenshot();
        if(bitmap!=null) {
            java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(dir,name+".png"));
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out); out.close();
        }
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
    private String shell(String command) throws Exception {
        try(ParcelFileDescriptor fd=getUiAutomation().executeShellCommand(command);
            java.io.FileInputStream in=new java.io.FileInputStream(fd.getFileDescriptor());
            java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()) {
            byte[] buffer=new byte[4096]; int count;
            while((count=in.read(buffer))!=-1) out.write(buffer,0,count);
            return out.toString("UTF-8");
        }
    }
    private void awaitReady(boolean watch) throws Exception {
        // Match the established Wear probe: boot_completed precedes the Wear OS
        // TrayInitializationOverlay closing, sometimes by more than a minute.
        long until=SystemClock.uptimeMillis()+(watch?90000:45000);
        long stableSince=SystemClock.uptimeMillis(); String previous="";
        while(SystemClock.uptimeMillis()<until) {
            String[] current={""};
            ui(() -> {
                if(activity!=null && !activity.isFinishing() && activity.hasWindowFocus() && activity.getWindow().getDecorView().isLaidOut())
                    current[0]=System.identityHashCode(activity)+":"+activity.getResources().getConfiguration();
            });
            if(current[0].isEmpty() || !current[0].equals(previous)) { previous=current[0]; stableSince=SystemClock.uptimeMillis(); }
            else if(SystemClock.uptimeMillis()-stableSince>=2000) break;
            SystemClock.sleep(100);
        }
        require(!previous.isEmpty() && SystemClock.uptimeMillis()-stableSince>=2000,"OS startup released a stable Focus screen");
        shell("appops set dev.orbit.clock SCHEDULE_EXACT_ALARM allow");
        long permissionLimit=SystemClock.uptimeMillis()+10000;
        AlarmManager alarms=c.getSystemService(AlarmManager.class);
        while(!alarms.canScheduleExactAlarms() && SystemClock.uptimeMillis()<permissionLimit) SystemClock.sleep(100);
        require(alarms.canScheduleExactAlarms(),"exact alarm permission fixture is ready");
    }
    private Object handoff(String method,Class<?>[] types,Object... args) throws Exception {
        Class<?> type=c.getClassLoader().loadClass("dev.orbit.clock.core.FocusHandoff");
        return type.getMethod(method,types).invoke(type.getField("INSTANCE").get(null),args);
    }
    private JSONObject acceptSync(JSONObject record) throws Exception {
        JSONObject packet=new JSONObject().put("nonce",java.util.UUID.randomUUID().toString()).put("record",record.toString());
        Class<?> type=c.getClassLoader().loadClass("dev.orbit.clock.core.FocusSync");
        byte[] reply=(byte[])type.getMethod("accept",Context.class,byte[].class).invoke(type.getField("INSTANCE").get(null),c,packet.toString().getBytes("UTF-8"));
        if(reply==null) throw new AssertionError("Valid sync packet rejected");
        JSONObject decoded=new JSONObject(new String(reply,"UTF-8"));
        require(packet.getString("nonce").equals(decoded.getString("nonce")),"sync receipt is correlated with its exact request");
        return decoded;
    }
    private void syncChecks(boolean watch) throws Exception {
        String peer=watch?"phone":"watch";
        JSONObject remote=state().put("id",java.util.UUID.randomUUID().toString()).put("phase","Focus").put("status","Paused")
            .put("remaining",45000).put("deadline",0).put("completed",0).put("revision",state().getLong("revision")+100)
            .put("writer",java.util.UUID.randomUUID().toString());
        remote.put("owner",remote.getString("writer"));
        require(acceptSync(remote).getBoolean("matched") && state().getString("id").equals(remote.getString("id")),peer+" snapshot immediately updates the local session");
        JSONObject running=new JSONObject(remote.toString()).put("status","Running").put("deadline",System.currentTimeMillis()+60000).put("remaining",60000).put("revision",remote.getLong("revision")+1);
        acceptSync(running);
        long limit=SystemClock.uptimeMillis()+3000; boolean[] rendered={false};
        while(!rendered[0] && SystemClock.uptimeMillis()<limit) {
            ui(() -> rendered[0]=find(activity.getWindow().getDecorView(),"Focus · Running")!=null && (((activity.getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)!=0)==watch));
            if(!rendered[0]) SystemClock.sleep(100);
        }
        require(rendered[0],watch?"incoming phone timer renders and keeps the watch display awake":"incoming watch timer renders without keeping the phone awake");
        JSONObject stale=acceptSync(remote);
        require(!stale.getBoolean("matched") && new JSONObject(stale.getString("record")).getLong("revision")==running.getLong("revision"),"stale peer snapshot returns newer local state instead of resetting the timer");
        JSONObject paused=new JSONObject(running.toString()).put("status","Paused").put("deadline",0).put("remaining",40000).put("revision",running.getLong("revision")+1);
        acceptSync(paused); long pauseLimit=SystemClock.uptimeMillis()+3000; boolean[] released={false};
        while(!released[0] && SystemClock.uptimeMillis()<pauseLimit) {
            ui(() -> released[0]=(activity.getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)==0);
            if(!released[0]) SystemClock.sleep(100);
        }
        require(released[0],"pause releases the display hold");
        require(acceptSync(paused).getBoolean("matched") && state().getLong("revision")==paused.getLong("revision"),"replayed matching snapshot is idempotent");
        act("end");
    }
    private void handoffChecks(boolean watch) throws Exception {
        if(!watch) {
            act("start"); JSONObject before=state();
            handoff("open",new Class[]{Context.class,c.getClassLoader().loadClass("dev.orbit.clock.core.FocusSession")},c,call("read",new Class[]{Context.class},c));
            require(state().getLong("revision")==before.getLong("revision") && state().getLong("deadline")==before.getLong("deadline"),"Open on watch retry never restarts the phone timer");
            handoff("receive",new Class[]{Context.class,String.class,String.class,byte[].class},c,"unrelated-node","/orbit/focus/handoff/visible",new JSONObject().put("nonce",java.util.UUID.randomUUID().toString()).toString().getBytes("UTF-8"));
            SystemClock.sleep(250);
            require(!"opened".equals(handoff("statusCode",new Class[]{Context.class},c)),"unmatched visibility acknowledgment cannot claim watch opened");
            act("end"); return;
        }
        int capability=c.getResources().getIdentifier("android_wear_capabilities","array",c.getPackageName());
        require(capability!=0 && java.util.Arrays.asList(c.getResources().getStringArray(capability)).contains("orbit_focus_handoff_v1"),"watch advertises the versioned Focus handoff capability");
        JSONObject remote=state().put("id",java.util.UUID.randomUUID().toString()).put("phase","Focus").put("status","Running")
            .put("remaining",60000).put("deadline",System.currentTimeMillis()+60000).put("completed",0)
            .put("revision",state().getLong("revision")+1000).put("writer",java.util.UUID.randomUUID().toString());
        remote.put("owner",remote.getString("writer"));
        ui(() -> activity.moveTaskToBack(true)); SystemClock.sleep(800);
        call("merge",new Class[]{Context.class,String.class},c,remote.toString());
        SystemClock.sleep(300);
        boolean[] hidden={false}; ui(() -> hidden[0]=!activity.hasWindowFocus());
        require(hidden[0],"ordinary delayed state sync does not steal the watch screen");
        String nonce=java.util.UUID.randomUUID().toString();
        JSONObject packet=new JSONObject().put("nonce",nonce).put("sentAt",System.currentTimeMillis()).put("record",remote.toString());
        JSONObject reply=new JSONObject(new String((byte[])handoff("prepare",new Class[]{Context.class,String.class,byte[].class},c,"probe-phone",packet.toString().getBytes("UTF-8")),"UTF-8"));
        require(reply.getBoolean("accepted") && state().getString("id").equals(remote.getString("id")),"live handoff acknowledges the exact saved phone session before launch");
        android.net.Uri uri=(android.net.Uri)handoff("launchUri",new Class[]{String.class},nonce);
        shell("am start -W -a android.intent.action.VIEW -c android.intent.category.BROWSABLE -d "+uri);
        long visibleLimit=SystemClock.uptimeMillis()+10000; boolean[] shown={false};
        while(!shown[0] && SystemClock.uptimeMillis()<visibleLimit) {
            ui(() -> shown[0]=activity.hasWindowFocus() && nonce.equals(activity.getIntent().getStringExtra("focus_handoff_nonce")));
            if(!shown[0]) SystemClock.sleep(100);
        }
        require(shown[0],"prepared remote URI opens the native Focus screen from watch home");
        require(state().getLong("deadline")==remote.getLong("deadline") && state().getString("owner").equals(remote.getString("owner")),"watch launch preserves the phone deadline and completion owner");
        require(handoff("consumeLaunch",new Class[]{Context.class,android.net.Uri.class},c,uri)==null,"remote launch URI is one use");
        require(handoff("consumeLaunch",new Class[]{Context.class,android.net.Uri.class},c,android.net.Uri.parse("dev.orbit.clock.focus://handoff/open?nonce=unprepared"))==null,"unprepared external URI cannot authorize a watch launch");
        JSONObject stale=new JSONObject(remote.toString()).put("revision",remote.getLong("revision")-1);
        JSONObject oldPacket=new JSONObject().put("nonce",java.util.UUID.randomUUID().toString()).put("sentAt",System.currentTimeMillis()).put("record",stale.toString());
        JSONObject oldReply=new JSONObject(new String((byte[])handoff("prepare",new Class[]{Context.class,String.class,byte[].class},c,"probe-phone",oldPacket.toString().getBytes("UTF-8")),"UTF-8"));
        require(!oldReply.getBoolean("accepted"),"stale handoff cannot replace a newer watch record");
        JSONObject expired=new JSONObject(packet.toString()).put("nonce",java.util.UUID.randomUUID().toString()).put("sentAt",System.currentTimeMillis()-120000);
        JSONObject expiredReply=new JSONObject(new String((byte[])handoff("prepare",new Class[]{Context.class,String.class,byte[].class},c,"probe-phone",expired.toString().getBytes("UTF-8")),"UTF-8"));
        require(!expiredReply.getBoolean("accepted"),"late handoff cannot unexpectedly reopen the watch");
        String cancelNonce=java.util.UUID.randomUUID().toString();
        JSONObject cancelPacket=new JSONObject(packet.toString()).put("nonce",cancelNonce).put("sentAt",System.currentTimeMillis());
        handoff("prepare",new Class[]{Context.class,String.class,byte[].class},c,"probe-phone",cancelPacket.toString().getBytes("UTF-8"));
        act("pause");
        require(handoff("consumeLaunch",new Class[]{Context.class,android.net.Uri.class},c,handoff("launchUri",new Class[]{String.class},cancelNonce))==null,"a pause invalidates an outstanding launch ticket");
        act("end");
    }
    @Override public void onStart() {
        Bundle result=new Bundle();
        try {
            c=getTargetContext(); store=c.getClassLoader().loadClass("dev.orbit.clock.core.FocusStore"); singleton=store.getField("INSTANCE").get(null);
            ((Application)c.getApplicationContext()).registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                public void onActivityCreated(Activity a,Bundle b) { }
                public void onActivityStarted(Activity a) { }
                public void onActivityResumed(Activity a) { if(a.getClass().getName().equals("dev.orbit.clock.core.FocusActivity")) activity=a; }
                public void onActivityPaused(Activity a) { }
                public void onActivityStopped(Activity a) { }
                public void onActivitySaveInstanceState(Activity a,Bundle b) { }
                public void onActivityDestroyed(Activity a) { }
            });
            c.getSharedPreferences("focus",0).edit().clear().commit();
            Bundle launch=new Bundle(); launch.putString("stream","Launching Focus activity\n"); sendStatus(0,launch);
            activity=startActivitySync(new Intent().setClassName(c,"dev.orbit.clock.core.FocusActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            SystemClock.sleep(1200); waitForIdleSync();
            boolean watch=c.getPackageManager().hasSystemFeature("android.hardware.type.watch");
            awaitReady(watch);
            // The watch IME can launch a separate startup Activity after opening an
            // editor. Configure the short timer through the production store so
            // bezel scenarios begin on the Focus screen, without that unrelated IME.
            if(!watch) { tap("Durations"); tapDialog("Cancel"); SystemClock.sleep(300); }
            ui(() -> { try { call("configure",new Class[]{Context.class,int.class,int.class,int.class},c,1,1,2); }catch(Exception e){throw new RuntimeException(e);} });
            if(watch) {
                long focusLimit=SystemClock.uptimeMillis()+6000;
                boolean[] focused={false};
                while(!focused[0] && SystemClock.uptimeMillis()<focusLimit) {
                    ui(() -> focused[0]=activity.hasWindowFocus());
                    if(!focused[0]) SystemClock.sleep(100);
                }
                String active="Orbit";
                if(!focused[0]) {
                    android.view.accessibility.AccessibilityNodeInfo node=getUiAutomation().getRootInActiveWindow();
                    active=node==null?"unknown":String.valueOf(node.getPackageName());
                }
                require(focused[0],"Focus screen owns rotary input; active window="+active);
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
            tap("End session"); tapDialog("Keep session"); SystemClock.sleep(300);
            require("Running".equals(state().getString("status")),"cancel end keeps session");
            // Wait for a real one-minute AlarmManager deadline with the activity stopped.
            ui(() -> activity.moveTaskToBack(true));
            NotificationManager progress=c.getSystemService(NotificationManager.class);
            boolean pausedFromNotification=false;
            long notifyLimit=SystemClock.uptimeMillis()+8000;
            String actionSummary="none";
            while(SystemClock.uptimeMillis()<notifyLimit && !pausedFromNotification) {
                for(android.service.notification.StatusBarNotification n:progress.getActiveNotifications()) {
                    if(n.getId()==9201 && n.getNotification().actions!=null) {
                        actionSummary=n.getNotification().actions[0].title.toString();
                        if("Pause".equals(actionSummary)) {
                            n.getNotification().actions[0].actionIntent.send();
                            long deliveryLimit=SystemClock.uptimeMillis()+4000;
                            while(SystemClock.uptimeMillis()<deliveryLimit && !"Paused".equals(state().getString("status"))) SystemClock.sleep(100);
                            pausedFromNotification="Paused".equals(state().getString("status"));
                            break;
                        }
                    }
                }
                if(!pausedFromNotification) SystemClock.sleep(200);
            }
            require(pausedFromNotification,"background notification pauses session; action="+actionSummary+" state="+state());
            act("start");
            long limit=SystemClock.uptimeMillis()+80000;
            while(!"Complete".equals(state().optString("status")) && SystemClock.uptimeMillis()<limit) SystemClock.sleep(1000);
            require("Complete".equals(state().getString("status")),"background AlarmManager completion");
            require(state().getInt("completed")==1,"completion counts once");
            NotificationManager nm=c.getSystemService(NotificationManager.class);
            require(nm.getActiveNotifications().length>0,"completion notification delivered");
            // NEW_TASK alone reuses the background task and startActivitySync then waits
            // forever for a new Activity. Clear that task to verify durable restoration.
            activity=startActivitySync(new Intent().setClassName(c,"dev.orbit.clock.core.FocusActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
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
            syncChecks(watch);
            handoffChecks(watch);
            result.putString("stream","PASS " + checks + " Focus runtime checks"); finish(Activity.RESULT_OK,result);
        } catch(Throwable failure) {
            try { capture("failure"); } catch(Throwable ignored) { }
            result.putString("stream",android.util.Log.getStackTraceString(failure)); finish(Activity.RESULT_CANCELED,result);
        }
    }
    @Override public void onCreate(Bundle b) { super.onCreate(b); start(); }
}
