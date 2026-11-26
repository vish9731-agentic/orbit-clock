package dev.orbit.clock.tileprobe;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.os.*;
import android.text.format.DateFormat;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.lang.reflect.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** Same-key native verification; no probe classes or test entry points ship in Orbit. */
public final class TileProbe extends Instrumentation {
 Context c;Activity activity;Class<?> storeClass,alarmClass,clockClass;Object storeCompanion;Method formatter;
 String scale;final List<String> fixtures=new ArrayList<>();
 volatile int storeChanges;volatile long lastStoreChange;
 final List<String> observations=Collections.synchronizedList(new ArrayList<>());
 boolean observing;
 final BroadcastReceiver storeObserver=new BroadcastReceiver(){@Override public void onReceive(Context context,Intent intent){storeChanges++;lastStoreChange=SystemClock.uptimeMillis();observe("Store.CHANGED #"+storeChanges);}};
 interface Check{boolean ok()throws Exception;}
 void require(boolean value,String message){if(!value)throw new AssertionError(message);}
 <T>T main(Callable<T> action)throws Exception{FutureTask<T> task=new FutureTask<>(action);require(new Handler(Looper.getMainLooper()).post(task),"Main queue rejected fixture");return task.get(15,TimeUnit.SECONDS);}
 void waitFor(Check check,long timeout,String message)throws Exception{long end=SystemClock.uptimeMillis()+timeout;while(SystemClock.uptimeMillis()<end){if(check.ok())return;SystemClock.sleep(100);}throw new AssertionError(message);}
 String shell(String command)throws Exception{try(ParcelFileDescriptor fd=getUiAutomation().executeShellCommand(command);InputStream input=new ParcelFileDescriptor.AutoCloseInputStream(fd)){ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[4096];for(int n;(n=input.read(buffer))!=-1;)bytes.write(buffer,0,n);return bytes.toString("UTF-8");}}
 void pass(String value){Bundle out=new Bundle();out.putString("stream","PASS "+value+"\n");sendStatus(0,out);}
 void observe(String value){observations.add("wall="+System.currentTimeMillis()+" uptime="+SystemClock.uptimeMillis()+" "+value);}
 boolean renderPending()throws Exception{return main(()->{Field handlerField=clockClass.getDeclaredField("handler"),refreshField=clockClass.getDeclaredField("refresh");handlerField.setAccessible(true);refreshField.setAccessible(true);return((Handler)handlerField.get(activity)).hasCallbacks((Runnable)refreshField.get(activity));});}
 TextView settledStatus(String id)throws Exception{
  final TextView[] last={null};final long[] stableSince={0};
  waitFor(()->{TextView current=status(id);long uptime=SystemClock.uptimeMillis();
   if(current==null||text(id).isEmpty()||renderPending()||uptime-lastStoreChange<800){last[0]=null;stableSince[0]=0;return false;}
   if(current!=last[0]){last[0]=current;stableSince[0]=uptime;}
   return uptime-stableSince[0]>=800;
  },15000,"Alarm tile did not settle after queued Store renders");
  return last[0];
 }
 TextView verifyMinuteUpdate(String id,JSONObject row)throws Exception{
  long deadline=SystemClock.uptimeMillis()+90000;
  while(SystemClock.uptimeMillis()<deadline){
   TextView baseline=settledStatus(id);int generation=storeChanges;long minute=System.currentTimeMillis()/60000;String before=text(id);
   observe("minute baseline view="+System.identityHashCode(baseline)+" minute="+minute+" store="+generation+" text="+before);
   while(SystemClock.uptimeMillis()<deadline){
    if(storeChanges!=generation){observe("legitimate Store update before minute assertion; wait for its render");break;}
    String current=text(id);
    if(System.currentTimeMillis()/60000>minute&&!current.isEmpty()&&!current.equals(before)){
     TextView after=status(id);if(storeChanges!=generation||renderPending()){observe("Store render arrived during minute observations; settle a fresh baseline");break;}
     observe("minute observation view="+System.identityHashCode(after)+" store="+storeChanges+" text="+current);
     require(after==baseline,"Quiescent minute refresh rebuilt the tile instead of updating in place; observations="+observations);
     require(current.equals(format(row,Instant.now(),ZoneId.systemDefault())),"Minute tick displays stale duration");
     return baseline;
    }
    SystemClock.sleep(100);
   }
  }
  throw new AssertionError("No Store-quiescent real minute update within90s; observations="+observations);
 }
 void stopObserving(){if(observing){c.unregisterReceiver(storeObserver);observing=false;}}
 Object store()throws Exception{return storeClass.getConstructor(Context.class).newInstance(c);}
 Object decode(JSONObject json)throws Exception{return storeCompanion.getClass().getMethod("decode",JSONObject.class).invoke(storeCompanion,json);}
 Object save(JSONObject json)throws Exception{return main(()->storeClass.getMethod("save",alarmClass).invoke(store(),decode(json)));}
 JSONObject fixture(String label,int hour,int minute,boolean enabled,int...days)throws Exception{
  JSONArray week=new JSONArray();for(int day:days)week.put(day);String id="tile-probe-"+UUID.randomUUID();fixtures.add(id);
  return new JSONObject().put("id",id).put("hour",hour).put("minute",minute).put("label",label).put("timer",false)
   .put("days",week).put("enabled",enabled).put("sound",false).put("vibrate",false).put("snoozeMinutes",10)
   .put("snoozeUntil",0).put("skipThrough",0).put("deleted",false).put("revision",System.currentTimeMillis()).put("writer","tile-probe");
 }
 String format(JSONObject json,Instant now,ZoneId zone)throws Exception{return main(()->(String)formatter.invoke(activity,decode(json),now,zone));}
 Instant next(JSONObject json,Instant now,ZoneId zone)throws Exception{return(Instant)alarmClass.getMethod("next",Instant.class,ZoneId.class).invoke(decode(json),now,zone);}
 View tagged(String tag)throws Exception{return main(()->activity.getWindow().getDecorView().findViewWithTag(tag));}
 TextView status(String id)throws Exception{return main(()->{
  View wrapper=activity.getWindow().getDecorView().findViewWithTag("alarm_next_status:"+id);
  if(!(wrapper instanceof ViewGroup))return null;return firstText(wrapper);
 });}
 TextView firstText(View view){if(view instanceof TextView)return(TextView)view;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++){TextView result=firstText(((ViewGroup)view).getChildAt(i));if(result!=null)return result;}return null;}
 String text(String id)throws Exception{TextView view=status(id);return view==null?"":main(()->view.getText().toString());}
 boolean containsText(View view,String wanted){if(view instanceof TextView&&wanted.contentEquals(((TextView)view).getText()))return true;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)if(containsText(((ViewGroup)view).getChildAt(i),wanted))return true;return false;}
 void showStatus(String id)throws Exception{
  TextView value=status(id);require(value!=null,"No per-tile next status for "+id);
  main(()->{value.requestRectangleOnScreen(new Rect(0,0,value.getWidth(),value.getHeight()),true);return null;});
  SystemClock.sleep(250);require(main(()->{Rect visible=new Rect();return value.getGlobalVisibleRect(visible)&&visible.height()>=value.getHeight()-2;}),"Next status is clipped at "+scale+"% text");
  require(main(()->value.getLayout()!=null&&value.getLayout().getEllipsisCount(value.getLayout().getLineCount()-1)==0),"Next status is ellipsized");
 }
 void capture(String name)throws Exception{Bitmap bitmap=getUiAutomation().takeScreenshot();require(bitmap!=null,"No native display capture");File dir=new File(c.getFilesDir(),"tile-feedback");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,name+"-"+scale+".png"))){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();}
 void open()throws Exception{
  Intent launch=c.getPackageManager().getLaunchIntentForPackage(c.getPackageName());require(launch!=null&&launch.getComponent()!=null,"No app launcher");
  if(activity==null){ActivityMonitor monitor=addMonitor(launch.getComponent().getClassName(),null,false);try{
   shell("am start -W -n "+launch.getComponent().flattenToShortString()+" --es orbit_tab Alarms");activity=monitor.waitForActivityWithTimeout(15000);
  }finally{removeMonitor(monitor);}}
  else shell("am start -W -n "+launch.getComponent().flattenToShortString()+" --es orbit_tab Alarms");
  require(activity!=null,"Alarm screen did not launch");waitFor(()->main(()->activity.hasWindowFocus()),10000,"Alarm screen not focused");
 }
 void formatterCases()throws Exception{
  ZoneId utc=ZoneId.of("UTC");Instant now=Instant.parse("2026-11-24T10:00:00Z");
  JSONObject a=fixture("Policy today",11,30,true);require(next(a,now,utc).equals(Instant.parse("2026-11-24T11:30:00Z")),"Today scheduling policy changed");String value=format(a,now,utc);require(value.startsWith("Today at 11:30")&&value.contains("in 1 hr 30 min"),"Wrong today tile: "+value);
  a=fixture("Policy tomorrow",9,0,true);require(next(a,now,utc).equals(Instant.parse("2026-11-25T09:00:00Z")),"Tomorrow scheduling policy changed");value=format(a,now,utc);require(value.startsWith("Tomorrow at 09:00"),"Wrong tomorrow tile: "+value);
  a=fixture("Policy weekdays",6,0,true,1,2,3,4,5).put("skipThrough",Instant.parse("2026-11-25T06:00:00Z").toEpochMilli());
  require(next(a,now,utc).equals(Instant.parse("2026-11-26T06:00:00Z")),"Skipped weekday policy changed");value=format(a,now,utc);require(value.startsWith("Resumes ")&&value.contains("26")&&value.contains("06:00")&&!value.contains("25 Nov"),"Skipped date is displayed as next: "+value);
  JSONObject weekend=fixture("Policy weekend",7,0,true,6,7);Instant friday=Instant.parse("2026-11-27T22:00:00Z");require(next(weekend,friday,utc).equals(Instant.parse("2026-11-28T07:00:00Z")),"Weekend scheduling policy changed");require(format(weekend,friday,utc).startsWith("Tomorrow at 07:00"),"Weekend next occurrence is wrong");
  a=fixture("Policy snoozed",8,0,true).put("snoozeUntil",Instant.parse("2026-11-24T10:05:00Z").toEpochMilli()).put("skipThrough",Instant.parse("2026-11-24T08:00:00Z").toEpochMilli());require(next(a,now,utc).equals(Instant.parse("2026-11-24T10:05:00Z")),"Snooze scheduling policy changed");value=format(a,now,utc);require(value.startsWith("Snoozed until today at 10:05")&&value.contains("in 5 min"),"Snooze tile uses original alarm time: "+value);
  require("Off".equals(format(a.put("enabled",false),now,utc)),"Disabled alarm advertises a next ring");
  JSONObject zoned=fixture("Policy zone",9,0,true);ZoneId india=ZoneId.of("Asia/Kolkata");Instant morning=Instant.parse("2026-11-24T02:00:00Z");require(next(zoned,morning,india).equals(Instant.parse("2026-11-24T03:30:00Z")),"Local-zone scheduling policy changed");require(format(zoned,morning,india).startsWith("Today at 09:00"),"Explicit local zone is ignored");
  JSONObject active=fixture("Policy active one-shot",8,0,true);String activeId=active.getString("id");Object record=decode(active);Class<?> scheduler=Class.forName("dev.orbit.clock.core.Scheduler",true,c.getClassLoader());Method identity=scheduler.getDeclaredMethod("identity",alarmClass);identity.setAccessible(true);
  String fingerprint=(String)identity.invoke(scheduler.getField("INSTANCE").get(null),record);SharedPreferences state=c.getSharedPreferences("scheduled_occurrences",0);
  try{require(state.edit().putLong(activeId+".active",now.toEpochMilli()).putString(activeId+".identity",fingerprint).commit(),"Active fixture state did not persist");require("Ringing now".equals(format(active,now,utc)),"Active one-shot falsely advertises tomorrow");Method upcoming=clockClass.getDeclaredMethod("nextAlarmOccurrence",alarmClass,Instant.class,ZoneId.class);upcoming.setAccessible(true);require(main(()->upcoming.invoke(activity,record,now,utc))==null,"Active one-shot contributes a false next-day aggregate ring");}finally{state.edit().remove(activeId+".active").remove(activeId+".identity").commit();}
  pass("calendar display matches independently checked today/tomorrow, weekday skip, weekend, snooze, disabled, active one-shot and local-zone deadlines");
 }
 void tileCases()throws Exception{
  ZonedDateTime now=ZonedDateTime.now();ZonedDateTime later=now.plusMinutes(25);JSONObject today=fixture("Run",later.getHour(),later.getMinute(),true),tomorrow=fixture("Morning",0,0,true),off=fixture("Spare",6,30,false);
  JSONObject snooze=fixture("Snoozed run",7,0,true).put("snoozeUntil",System.currentTimeMillis()+45*60000).put("skipThrough",System.currentTimeMillis()-5*60000);
  JSONObject skipped=fixture("Weekday wake",6,0,true,1,2,3,4,5).put("skipThrough",now.plusDays(2).toInstant().toEpochMilli());
  List<JSONObject> rows=Arrays.asList(today,tomorrow,off,snooze,skipped);for(JSONObject row:rows)save(row);
  for(JSONObject row:rows){String id=row.getString("id");waitFor(()->status(id)!=null&&!text(id).isEmpty(),10000,"No native status in tile "+row.getString("label"));
   View tile=tagged("item:alarm:"+id),wrapper=tagged("alarm_next_status:"+id);require(tile!=null&&wrapper!=null,"Missing own tile wrapper");
   require(main(()->{ViewParent p=wrapper.getParent();while(p instanceof View){if(p==tile)return true;p=p.getParent();}return false;}),"Next ring text belongs to another tile/top banner");
   require(main(()->tile.getContentDescription().toString().contains(textUnchecked(statusUnchecked(id)))) ,"Card accessibility omits next ring");
   String actual=text(id);String expected=format(row,Instant.now(),ZoneId.systemDefault());require(actual.equals(expected),"Native tile does not show its current occurrence: "+actual+" expected "+expected);
   if(row==off)require("Off".equals(actual),"Disabled tile falsely claims a ring");if(row==snooze)require(actual.startsWith("Snoozed until "),"Snooze not identified");if(row==skipped)require(actual.startsWith("Resumes "),"Temporary skip not identified");
   showStatus(id);
  }
  require(main(()->containsText(activity.getWindow().getDecorView(),"NEXT ALARM")),"Aggregate green next-alarm banner was removed");
  showStatus(today.getString("id"));capture("alarm-tiles");pass("each real alarm tile owns accurate next-ring text, off/snooze/resume states, accessible descriptions and unclipped "+scale+"% layout; aggregate banner retained");
  String id=today.getString("id");TextView same=settledStatus(id);
  if(scale.equals("100")){same=verifyMinuteUpdate(id,today);pass("real Store-quiescent minute boundary updates the existing tile countdown in place");}
  shell("settings put system time_12_24 12");waitFor(()->main(()->!DateFormat.is24HourFormat(activity)),5000,"12-hour preference did not apply");
  waitFor(()->text(id).equals(format(today,Instant.now(),ZoneId.systemDefault())),5000,"Time-format change left stale tile text");require(status(id)==same,"Time-format refresh rebuilt the tile");
  shell("settings put system time_12_24 24");waitFor(()->main(()->DateFormat.is24HourFormat(activity)),5000,"24-hour preference did not restore");waitFor(()->text(id).equals(format(today,Instant.now(),ZoneId.systemDefault())),5000,"24-hour text is stale");
  TimeZone previous=TimeZone.getDefault();try{TimeZone.setDefault(TimeZone.getTimeZone("Pacific/Honolulu"));waitFor(()->text(id).equals(format(today,Instant.now(),ZoneId.systemDefault())),6000,"Process-zone refresh left stale next occurrence");require(status(id)==same,"Zone refresh rebuilt the tile");}finally{TimeZone.setDefault(previous);}
  waitFor(()->text(id).equals(format(today,Instant.now(),ZoneId.systemDefault())),6000,"Original zone display did not restore");pass("12/24-hour preference and process time-zone changes refresh next-ring text in place");
  save(today.put("enabled",false));waitFor(()->"Off".equals(text(id)),10000,"Store change did not update tile to Off");
  shell("input keyevent HOME");open();waitFor(()->"Off".equals(text(id)),10000,"Foreground resume restores stale enabled deadline");pass("store updates and Home/foreground resume preserve accurate per-tile Off state");
 }
 TextView statusUnchecked(String id){View wrapper=activity.getWindow().getDecorView().findViewWithTag("alarm_next_status:"+id);return firstText(wrapper);}
 String textUnchecked(TextView view){return view==null?"":view.getText().toString();}
 @Override public void onCreate(Bundle arguments){super.onCreate(arguments);scale=arguments.getString("scale","100");start();}
 @Override public void onStart(){Bundle result=new Bundle();try{
  c=getTargetContext();ClassLoader loader=c.getClassLoader();storeClass=Class.forName("dev.orbit.clock.core.Store",true,loader);alarmClass=Class.forName("dev.orbit.clock.core.Alarm",true,loader);clockClass=Class.forName("dev.orbit.clock.core.ClockActivity",true,loader);storeCompanion=storeClass.getField("Companion").get(null);
  formatter=clockClass.getDeclaredMethod("alarmNextText",alarmClass,Instant.class,ZoneId.class);formatter.setAccessible(true);
  lastStoreChange=SystemClock.uptimeMillis();IntentFilter storeFilter=new IntentFilter("dev.orbit.clock.CHANGED");if(Build.VERSION.SDK_INT>=33)c.registerReceiver(storeObserver,storeFilter,Context.RECEIVER_NOT_EXPORTED);else c.registerReceiver(storeObserver,storeFilter);observing=true;
  require(Math.abs(c.getResources().getConfiguration().fontScale-Float.parseFloat(scale)/100f)<.04f,"Wrong actual text scale");
  require(DateFormat.is24HourFormat(c),"Tile fixture requires24-hour system format");open();formatterCases();tileCases();
  stopObserving();result.putString("stream","ALARM_TILE_INFO_RESULT_PASS at "+scale+"% text\nOBSERVATIONS "+observations+"\n");finish(Activity.RESULT_OK,result);
 }catch(Throwable error){try{capture("failure");}catch(Throwable ignored){}try{stopObserving();}catch(Throwable ignored){}result.putString("stream","ALARM_TILE_INFO_FAILURE: "+android.util.Log.getStackTraceString(error)+"\nOBSERVATIONS "+observations);finish(Activity.RESULT_CANCELED,result);}}
}
