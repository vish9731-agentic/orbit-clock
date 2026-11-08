package dev.orbit.clock.resumeprobe;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.lang.reflect.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** Same-signing-key native verification only; never bundled in the app. */
public class ResumeProbe extends Instrumentation {
 Context c; Activity activity; Class<?> storeClass,alarmClass,planClass,optionClass,resumeClass,schedulerClass;
 Object companion,resume,scheduler; Bundle args; String scale; final List<String> fixtures=new ArrayList<>();
 final ZoneId utc=ZoneId.of("UTC"); static final String PERSIST="resume-probe-process-restart";
 interface Check { boolean ok() throws Exception; }
 <T> T main(Callable<T> action) throws Exception {
  FutureTask<T> task=new FutureTask<>(action);runOnMainSync(task);return task.get();
 }
 void require(boolean good,String reason){if(!good)throw new AssertionError(reason);}
 void waitFor(Check check,long ms,String reason) throws Exception {
  long until=SystemClock.uptimeMillis()+ms;
  while(SystemClock.uptimeMillis()<until){if(check.ok())return;SystemClock.sleep(50);}
  throw new AssertionError(reason);
 }
 void report(String s){Bundle out=new Bundle();out.putString("stream",s+"\n");sendStatus(0,out);}
 Object get(Object value,String property) throws Exception {
  return value.getClass().getMethod("get"+Character.toUpperCase(property.charAt(0))+property.substring(1)).invoke(value);
 }
 Object store() throws Exception{return storeClass.getConstructor(Context.class).newInstance(c);}
 Object decode(JSONObject row) throws Exception{return companion.getClass().getMethod("decode",JSONObject.class).invoke(companion,row);}
 JSONObject encode(Object alarm) throws Exception{return (JSONObject)companion.getClass().getMethod("encode",alarmClass).invoke(companion,alarm);}
 Object save(JSONObject row) throws Exception{return main(()->storeClass.getMethod("save",alarmClass).invoke(store(),decode(row)));}
 JSONObject row(String id) throws Exception {
  JSONArray rows=new JSONArray(c.getSharedPreferences("alarms",0).getString("records","[]"));
  for(int i=0;i<rows.length();i++)if(id.equals(rows.getJSONObject(i).getString("id")))return rows.getJSONObject(i);
  throw new AssertionError("No stored alarm "+id);
 }
 android.content.SharedPreferences state(){return c.getSharedPreferences("scheduled_occurrences",0);}
 JSONObject fixture(String name,int hour,int minute,int... days) throws Exception {
  String id="resume-probe-"+UUID.randomUUID();fixtures.add(id);JSONArray repeat=new JSONArray();
  for(int day:days)repeat.put(day);
  return new JSONObject().put("id",id).put("hour",hour).put("minute",minute).put("label",name)
   .put("timer",false).put("days",repeat).put("enabled",true).put("sound",false).put("vibrate",false)
   .put("snoozeMinutes",5).put("snoozeUntil",0).put("skipThrough",0).put("deleted",false)
   .put("revision",System.currentTimeMillis()).put("writer","resume-probe");
 }
 Object disable(String id,Instant now,ZoneId zone) throws Exception {
  return main(()->storeClass.getMethod("disableWithSuggestions",String.class,Instant.class,ZoneId.class,Long.class,String.class)
   .invoke(store(),id,now,zone,null,null));
 }
 @SuppressWarnings("unchecked") List<Object> options(Object plan) throws Exception{return (List<Object>)get(plan,"options");}
 Instant at(Object option) throws Exception{return (Instant)get(option,"at");}
 Object resume(Object plan,Object option,Instant now,ZoneId zone) throws Exception {
  return main(()->storeClass.getMethod("resumeSuggestion",planClass,optionClass,Instant.class,ZoneId.class)
   .invoke(store(),plan,option,now,zone));
 }
 void clean(String id) throws Exception {JSONObject end=row(id);end.put("enabled",false).put("deleted",true);save(end);}
 void scheduled(String id,long expected) throws Exception {
  waitFor(()->state().getLong(id+".scheduled",0)==expected,7000,"Scheduler did not install "+expected+" for "+id+"; state="+state().getAll());
  AlarmManager.AlarmClockInfo info=c.getSystemService(AlarmManager.class).getNextAlarmClock();
  require(info!=null && info.getTriggerTime()==expected,"Actual AlarmManager next alarm differs from persisted deadline");
 }
 void policy(String label,String nowText,int[] days,String expected,String extraKind,String extraAt) throws Exception {
  JSONObject original=fixture(label,6,0,days);String id=original.getString("id");save(original);
  Instant now=Instant.parse(nowText);Object plan=disable(id,now,utc);
  require(plan!=null,"Missing suggestions: "+label);
  require(!row(id).getBoolean("enabled") && state().getLong(id+".scheduled",0)==0,"Turning off was not immediate: "+label);
  List<Object> choices=options(plan);require(!choices.isEmpty(),"No date choices: "+label);
  require(at(choices.get(0)).equals(Instant.parse(expected)),label+" primary was "+at(choices.get(0)));
  Set<Instant> unique=new HashSet<>();for(Object choice:choices)require(unique.add(at(choice)),"Duplicate date choice");
  if(extraKind!=null){boolean found=false;for(Object choice:choices)if(extraKind.equals(get(choice,"kind")) && Instant.parse(extraAt).equals(at(choice)))found=true;
   require(found,label+" missing meaningful "+extraKind);}
  Object updated=resume(plan,choices.get(0),now,utc);require(updated!=null,"Could not resume "+label);
  JSONObject current=row(id);require(current.getBoolean("enabled") && current.getLong("snoozeUntil")==0,"Resume state incorrect");
  require(current.getJSONArray("days").toString().equals(original.getJSONArray("days").toString()),"Repeat days changed");
  require(current.getLong("skipThrough")==Instant.parse(expected).toEpochMilli()-1,"Resume did not use explicit cutoff");
  // Tests use injected UTC dates; the actual scheduler uses the guest zone (configured UTC).
  scheduled(id,Instant.parse(expected).toEpochMilli());
  main(()->{schedulerClass.getMethod("restore",Context.class).invoke(scheduler,c);return null;});
  scheduled(id,Instant.parse(expected).toEpochMilli());
  String encoded=(String)resumeClass.getMethod("encode",planClass).invoke(resume,plan);
  Object restored=resumeClass.getMethod("decode",String.class).invoke(resume,encoded);
  require(restored!=null && at(options(restored).get(0)).equals(at(choices.get(0))),"Plan recreation codec changed date");
  require(resume(plan,choices.get(0),now,utc)==null,"A selected dialog could act twice");
  clean(id);report("PASS "+label+" next="+expected+" immediate-off/immediate-schedule/repeat-preserved/restore");
 }
 void calendarCases() throws Exception {
  policy("Tuesday night skips Wednesday resumes Thursday","2030-01-08T22:00:00Z",new int[]{1,2,3,4,5},"2030-01-10T06:00:00Z",null,null);
  policy("daily Monday to Tuesday","2030-01-07T05:00:00Z",new int[]{1,2,3,4,5,6,7},"2030-01-08T06:00:00Z",null,null);
  policy("weekday Friday to Monday","2030-01-11T05:00:00Z",new int[]{1,2,3,4,5},"2030-01-14T06:00:00Z",null,null);
  policy("weekend Sunday to Saturday","2030-01-13T05:00:00Z",new int[]{6,7},"2030-01-19T06:00:00Z",null,null);
  policy("weekly 13-day cutoff","2030-01-08T05:00:00Z",new int[]{1},"2030-01-21T06:00:00Z",null,null);
  policy("custom repeat preserves Monday Thursday","2030-01-08T05:00:00Z",new int[]{1,4},"2030-01-14T06:00:00Z",null,null);
  policy("daily Friday offers weekend break","2030-01-11T05:00:00Z",new int[]{1,2,3,4,5,6,7},"2030-01-12T06:00:00Z","after_weekend","2030-01-14T06:00:00Z");
  policy("one-shot Friday offers Monday","2030-01-11T05:00:00Z",new int[]{},"2030-01-12T06:00:00Z","next_weekday","2030-01-14T06:00:00Z");
  policy("one-shot Monday offers weekend","2030-01-07T05:00:00Z",new int[]{},"2030-01-08T06:00:00Z","next_weekend","2030-01-12T06:00:00Z");
 }
 void leaseAndSyncCases() throws Exception {
  Instant now=Instant.parse("2030-01-07T05:00:00Z");
  JSONObject original=fixture("Edited safely",6,0,1,2,3,4,5);String id=original.getString("id");save(original);
  Object plan=disable(id,now,utc),option=options(plan).get(0);JSONObject edit=row(id).put("label","Changed elsewhere");save(edit);
  require(resume(plan,option,now,utc)==null && row(id).getString("label").equals("Changed elsewhere") && !row(id).getBoolean("enabled"),"Stale sheet overwrote edit");
  storeClass.getMethod("enable",String.class).invoke(store(),id);plan=disable(id,now,utc);option=options(plan).get(0);
  JSONObject deleted=row(id).put("deleted",true);save(deleted);require(resume(plan,option,now,utc)==null && row(id).getBoolean("deleted"),"Stale sheet resurrected deletion");
  clean(id);
  original=fixture("Elapsed safely",6,0,1,2,3,4,5);id=original.getString("id");save(original);plan=disable(id,now,utc);option=options(plan).get(0);
  require(resume(plan,option,at(option).plusSeconds(1),utc)==null,"Elapsed choice was accepted");
  require(resume(plan,option,now,ZoneId.of("Asia/Kolkata"))==null,"Changed zone reused old wall-time target");
  Object changed=resume(plan,option,now,utc);require(changed!=null,"Fresh choice failed");JSONObject encoded=encode(changed);
  JSONObject peer=new JSONObject(encoded.toString()).put("revision",encoded.getLong("revision")+1000).put("writer","remote-peer");
  final JSONObject incoming=peer;main(()->{storeClass.getMethod("merge",alarmClass).invoke(store(),decode(incoming));return null;});
  require(row(id).getLong("skipThrough")==encoded.getLong("skipThrough") && row(id).getString("writer").equals("remote-peer"),"Sync codec/merge lost temporary skip");
  scheduled(id,at(option).toEpochMilli());clean(id);report("PASS exact-revision lease, deletion/edit safety, elapsed/time-zone rejection and sync round-trip");
 }
 void snoozeAndActive() throws Exception {
  Instant now=Instant.parse("2030-01-07T06:15:00Z");JSONObject alarm=fixture("Snoozed original wall time",6,0,1,2,3,4,5);
  String id=alarm.getString("id");alarm.put("snoozeUntil",Instant.parse("2030-01-07T06:20:00Z").toEpochMilli());save(alarm);
  Object plan=disable(id,now,utc),option=options(plan).get(0);require(at(option).equals(Instant.parse("2030-01-08T06:00:00Z")),"Snoozed suggestion drifted from original wall time");
  require(resume(plan,option,now,utc)!=null,"Snoozed alarm did not resume");scheduled(id,at(option).toEpochMilli());clean(id);
  long occurrence=System.currentTimeMillis()+12000;ZonedDateTime wall=Instant.ofEpochMilli(occurrence).atZone(ZoneId.systemDefault());
  alarm=fixture("Real active ringing",wall.getHour(),wall.getMinute(),1,2,3,4,5,6,7).put("snoozeUntil",occurrence);
  id=alarm.getString("id");final String activeId=id;save(alarm);
  waitFor(()->state().getLong(activeId+".active",0)==occurrence,40000,"Real receiver did not activate silent alarm");
  plan=disable(id,Instant.now(),ZoneId.systemDefault());require(((Instant)get(plan,"skipped")).toEpochMilli()==occurrence,"Active occurrence was not the one skipped");
  require(!row(id).getBoolean("enabled") && state().getLong(id+".active",0)==0,"Turning off did not clear active alarm");
  option=options(plan).get(0);require(resume(plan,option,Instant.now(),ZoneId.systemDefault())!=null,"Active alarm did not resume later");
  scheduled(id,at(option).toEpochMilli());clean(id);SystemClock.sleep(6500);report("PASS snoozed wall-time recovery and actual silent receiver/ringing cancellation");
 }
 View find(View root,String tag) {
  if(tag.equals(root.getTag()))return root;
  if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){View got=find(((ViewGroup)root).getChildAt(i),tag);if(got!=null)return got;}
  return null;
 }
 Dialog sheet() throws Exception {
  Field field=Class.forName("dev.orbit.clock.core.ClockActivity",true,c.getClassLoader()).getDeclaredField("resumeSheet");field.setAccessible(true);return (Dialog)field.get(activity);
 }
 View dialogView(String tag) throws Exception {Dialog dialog=sheet();return dialog==null||!dialog.isShowing()?null:find(dialog.getWindow().getDecorView(),tag);}
 void click(View view) throws Exception {
  require(view!=null,"Missing click target");int[] bounds=main(()->{view.requestRectangleOnScreen(new Rect(0,0,view.getWidth(),view.getHeight()),true);int[] xy=new int[2];view.getLocationOnScreen(xy);return new int[]{xy[0],xy[1],view.getWidth(),view.getHeight()};});
  SystemClock.sleep(120);long time=SystemClock.uptimeMillis();float x=bounds[0]+bounds[2]/2f,y=bounds[1]+bounds[3]/2f;
  MotionEvent down=MotionEvent.obtain(time,time,MotionEvent.ACTION_DOWN,x,y,0),up=MotionEvent.obtain(time,time+70,MotionEvent.ACTION_UP,x,y,0);
  down.setSource(InputDevice.SOURCE_TOUCHSCREEN);up.setSource(InputDevice.SOURCE_TOUCHSCREEN);sendPointerSync(down);sendPointerSync(up);down.recycle();up.recycle();waitForIdleSync();
 }
 void screenshot(String name) throws Exception {
  SystemClock.sleep(250);Bitmap bitmap=getUiAutomation().takeScreenshot();require(bitmap!=null,"Missing display capture");
  File directory=new File(c.getFilesDir(),"resume-feedback");directory.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(directory,name+"-"+scale+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
 }
 void showPrompt(String id) throws Exception {
  waitFor(()->main(()->find(activity.getWindow().getDecorView(),"alarm_toggle:"+id)!=null),8000,"Alarm toggle not rendered");
  View toggle=main(()->find(activity.getWindow().getDecorView(),"alarm_toggle:"+id));click(toggle);
  waitFor(()->main(()->dialogView("alarm_resume_option_0")!=null),7000,"Turn-back-on prompt absent");
  require(!row(id).getBoolean("enabled") && state().getLong(id+".scheduled",0)==0,"Prompt did not immediately turn alarm off");
 }
 void optionFits(View view) throws Exception {
  require(main(()->{Rect visible=new Rect();return view.getGlobalVisibleRect(visible) && visible.width()>=view.getWidth()-2 && visible.height()>=view.getHeight()-2;}),"Primary date option clipped at "+scale+"% text");
  if(view instanceof TextView)require(main(()->{TextView text=(TextView)view;android.text.Layout layout=text.getLayout();if(layout==null)return false;for(int i=0;i<layout.getLineCount();i++)if(layout.getEllipsisCount(i)>0)return false;return true;}),"Date option ellipsized");
 }
 void recreatePrompt() throws Exception {
  Activity old=activity;ActivityMonitor monitor=addMonitor("dev.orbit.clock.phone.MainActivity",null,false);
  try{main(()->{old.recreate();return null;});Activity next=monitor.waitForActivityWithTimeout(12000);require(next!=null && next!=old,"Activity recreation did not finish");activity=next;
   waitFor(()->main(()->dialogView("alarm_resume_option_0")!=null),8000,"Prompt was lost on recreation");
  }finally{removeMonitor(monitor);}
 }
 void uiCases() throws Exception {
  Intent intent=new Intent().setClassName(c.getPackageName(),"dev.orbit.clock.phone.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
  activity=startActivitySync(intent);waitForIdleSync();
  ZonedDateTime wall=Instant.now().atZone(ZoneId.systemDefault()).plusHours(2);JSONObject alarm=fixture("UI resume test",wall.getHour(),wall.getMinute(),1,2,3,4,5,6,7);
  String id=alarm.getString("id");save(alarm);showPrompt(id);optionFits(main(()->dialogView("alarm_resume_option_0")));screenshot("prompt");
  recreatePrompt();click(main(()->dialogView("alarm_resume_option_0")));
  waitFor(()->row(id).getBoolean("enabled"),7000,"Choosing resume did not enable alarm");long target=row(id).getLong("skipThrough")+1;scheduled(id,target);
  waitFor(()->main(()->find(activity.getWindow().getDecorView(),"alarm_resume_status:"+id)!=null),7000,"Future resume status absent");screenshot("resumed");
  showPrompt(id);click(main(()->dialogView("alarm_resume_keep_off")));require(!row(id).getBoolean("enabled"),"Keep off re-enabled alarm");
  main(()->{storeClass.getMethod("enable",String.class).invoke(store(),id);return null;});showPrompt(id);sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
  waitFor(()->main(()->sheet()==null || !sheet().isShowing()),5000,"Back did not dismiss prompt");require(!row(id).getBoolean("enabled"),"Back re-enabled alarm");
  clean(id);report("PASS real alarm-list touch, prompt/date selection, future status, activity recreation, Keep off/back at "+scale+"% text");
 }
 View mountWidget(Object alarm) throws Exception {
  Class<?> widget=Class.forName("dev.orbit.clock.phone.AlarmWidget",true,c.getClassLoader()),snapshot=Class.forName("dev.orbit.clock.phone.AlarmWidget$Snapshot",true,c.getClassLoader());
  Object owner=widget.getField("Companion").get(null),state=snapshot.getConstructor(String.class,alarmClass,long.class,long.class).newInstance("alarm",alarm,System.currentTimeMillis()+600000,0L);
  RemoteViews remote=(RemoteViews)owner.getClass().getMethod("views",Context.class,boolean.class,snapshot,long.class).invoke(owner,activity,true,state,System.currentTimeMillis());
  return main(()->{FrameLayout host=new FrameLayout(activity);View view=remote.apply(activity,host);host.addView(view,new FrameLayout.LayoutParams(-1,-1));
   FrameLayout decor=activity.findViewById(android.R.id.content);FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-1,(int)(100*activity.getResources().getDisplayMetrics().density),Gravity.TOP);decor.addView(host,p);return host;});
 }
 void widgetCases() throws Exception {
  ZonedDateTime wall=Instant.now().atZone(ZoneId.systemDefault()).plusHours(2);JSONObject json=fixture("Widget resume test",wall.getHour(),wall.getMinute(),1,2,3,4,5);String id=json.getString("id");Object saved=save(json);View host=mountWidget(saved);
  int toggleId=c.getResources().getIdentifier("widget_toggle","id",c.getPackageName());View toggle=main(()->host.findViewById(toggleId));
  click(toggle);waitFor(()->main(()->dialogView("alarm_resume_option_0")!=null),8000,"Widget direct PendingIntent did not open resume prompt");
  require(!row(id).getBoolean("enabled"),"Widget prompt did not turn alarm off");click(main(()->dialogView("alarm_resume_option_0")));waitFor(()->row(id).getBoolean("enabled"),6000,"Widget choice did not enable");scheduled(id,row(id).getLong("skipThrough")+1);
  main(()->{((ViewGroup)host.getParent()).removeView(host);return null;});
  Object fresh=storeClass.getMethod("get",String.class).invoke(store(),id);View staleHost=mountWidget(fresh);save(row(id).put("label","Edited after widget render"));
  main(()->{staleHost.findViewById(toggleId).performClick();return null;});SystemClock.sleep(750);waitForIdleSync();
  require(row(id).getBoolean("enabled") && row(id).getString("label").equals("Edited after widget render"),"Stale widget overwrote edited alarm");
  require(main(()->sheet()==null || !sheet().isShowing()),"Stale widget opened an actionable prompt");
  main(()->{((ViewGroup)staleHost.getParent()).removeView(staleHost);return null;});clean(id);report("PASS actual RemoteViews toggle PendingIntent, immediate resume and stale-widget revision guard");
 }
 void persistForRestart() throws Exception {
  JSONObject json=fixture("Process restart resume",6,0,1,2,3,4,5,6,7).put("id",PERSIST);save(json);
  Instant now=Instant.parse("2030-01-07T05:00:00Z");Object plan=disable(PERSIST,now,utc);Object option=options(plan).get(0);require(resume(plan,option,now,utc)!=null,"Restart fixture resume failed");
  scheduled(PERSIST,at(option).toEpochMilli());report("PASS prepared persisted temporary skip for actual next-process verification");
 }
 void bedtimeSkip() throws Exception {
  for(boolean repeating:new boolean[]{true,false}){
  JSONObject json=fixture("Wake up",6,0,repeating?new int[]{1,2,3,4,5,6,7}:new int[]{});String id=json.getString("id");save(json);
  Instant now=Instant.parse("2030-01-07T05:00:00Z");Object plan=disable(id,now,utc);Object option=options(plan).get(0);require(resume(plan,option,now,utc)!=null,"Wake-up fixture resume failed");
  c.getSharedPreferences("tools",0).edit().putInt("sleepGoal",8).commit();
  c.getSharedPreferences("widget",0).edit().putBoolean("bedtimeEnabled",true).putInt("bedtimeWindow",15).commit();
  Class<?> widget=Class.forName("dev.orbit.clock.phone.AlarmWidget",true,c.getClassLoader());Object owner=widget.getField("Companion").get(null);
  Method snapshot=owner.getClass().getMethod("snapshot",Context.class,long.class);
  Object skipped=snapshot.invoke(owner,c,Instant.parse("2030-01-06T21:50:00Z").toEpochMilli());
  require("clock".equals(get(skipped,"kind")),"Widget still suggested bedtime before a skipped wake-up occurrence");
  Object resumed=snapshot.invoke(owner,c,Instant.parse("2030-01-07T21:50:00Z").toEpochMilli());
  require("bedtime".equals(get(resumed,"kind")) && ((Number)get(resumed,"at")).longValue()==Instant.parse("2030-01-07T22:00:00Z").toEpochMilli(),"Widget did not restore bedtime before the resumed wake-up");
  if(!repeating){save(row(id).put("snoozeUntil",Instant.parse("2030-01-08T06:15:00Z").toEpochMilli()));
   Object snoozed=snapshot.invoke(owner,c,Instant.parse("2030-01-07T21:50:00Z").toEpochMilli());
   require("clock".equals(get(snoozed,"kind")),"Snoozed one-shot created another bedtime reminder");}
  clean(id);report("PASS "+(repeating?"repeat":"one-shot")+" bedtime widget ignores skipped night and resumes before selected wake-up");
  }
 }
 void verifyRestart() throws Exception {
  JSONObject prior=row(PERSIST);require(prior.getBoolean("enabled") && prior.getLong("skipThrough")==Instant.parse("2030-01-08T06:00:00Z").toEpochMilli()-1,"Process restart lost resume record");
  main(()->{schedulerClass.getMethod("restore",Context.class).invoke(scheduler,c);return null;});scheduled(PERSIST,prior.getLong("skipThrough")+1);clean(PERSIST);report("PASS actual process restart keeps temporary skip and restores exact future AlarmManager deadline");
 }
 @Override public void onCreate(Bundle args){super.onCreate(args);this.args=args;start();}
 @Override public void onStart(){Bundle result=new Bundle();try{
  c=getTargetContext();scale=args.getString("scale","100");ClassLoader loader=c.getClassLoader();storeClass=Class.forName("dev.orbit.clock.core.Store",true,loader);alarmClass=Class.forName("dev.orbit.clock.core.Alarm",true,loader);
  planClass=Class.forName("dev.orbit.clock.core.AlarmResume$Plan",true,loader);optionClass=Class.forName("dev.orbit.clock.core.AlarmResume$Option",true,loader);resumeClass=Class.forName("dev.orbit.clock.core.AlarmResume",true,loader);
  schedulerClass=Class.forName("dev.orbit.clock.core.Scheduler",true,loader);companion=storeClass.getField("Companion").get(null);resume=resumeClass.getField("INSTANCE").get(null);scheduler=schedulerClass.getField("INSTANCE").get(null);
  require(Math.abs(c.getResources().getConfiguration().fontScale-Float.parseFloat(scale)/100f)<.04f,"Actual target font scale differs from requested "+scale+"%");
  require(ZoneId.systemDefault().getRules().getOffset(Instant.parse("2030-01-07T05:00:00Z")).getTotalSeconds()==0,"Fixture guest must use UTC");
  if(args.getString("checkRestart","false").equals("true"))verifyRestart();
  calendarCases();leaseAndSyncCases();snoozeAndActive();uiCases();bedtimeSkip();
  if(scale.equals("100"))persistForRestart();
  main(()->{if(activity!=null)activity.finish();return null;});
  result.putString("stream","ALARM_RESUME_RESULT_PASS: calendar suggestions, installed deadlines, safe leases, active/snoozed cases, native prompt/recreation/bedtime interactions at "+scale+"% text\n");finish(Activity.RESULT_OK,result);
 }catch(Throwable error){try{screenshot("failure");}catch(Throwable ignored){}result.putString("stream","ALARM_RESUME_FAILURE: "+android.util.Log.getStackTraceString(error));finish(Activity.RESULT_CANCELED,result);}}
}
