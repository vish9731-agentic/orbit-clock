package dev.orbit.clock.autoprobe;
import android.app.*; import android.content.*; import android.os.*;
import org.json.*; import java.util.*; import java.util.concurrent.*;
/** Same-key instrumentation fixture, never bundled in Orbit. */
public class AutoSnoozeProbe extends Instrumentation {
 Context c; Class<?> storeClass,alarmClass,controlsClass,schedulerClass; Object companion;
 interface Check { boolean ok() throws Exception; }
 <T> T main(Callable<T> action) throws Exception {
  FutureTask<T> task=new FutureTask<>(action);runOnMainSync(task);return task.get();
 }
 void require(boolean good,String message) {if(!good)throw new AssertionError(message);}
 void waitFor(Check check,long timeout,String label) throws Exception {
  long end=SystemClock.elapsedRealtime()+timeout;
  while(SystemClock.elapsedRealtime()<end){if(check.ok())return;SystemClock.sleep(100);}
  throw new AssertionError(label);
 }
 android.content.SharedPreferences state(){return c.getSharedPreferences("scheduled_occurrences",0);}
 Object store() throws Exception{return storeClass.getConstructor(Context.class).newInstance(c);}
 void save(JSONObject json) throws Exception {
  main(()->{Object alarm=companion.getClass().getMethod("decode",JSONObject.class).invoke(companion,json);
   storeClass.getMethod("save",alarmClass).invoke(store(),alarm);return null;});
 }
 JSONObject record(String id) throws Exception {
  JSONArray rows=new JSONArray(c.getSharedPreferences("alarms",0).getString("records","[]"));
  for(int i=0;i<rows.length();i++)if(rows.getJSONObject(i).getString("id").equals(id))return rows.getJSONObject(i);
  throw new AssertionError("Missing fixture "+id);
 }
 void report(String text){Bundle b=new Bundle();b.putString("stream",text+"\n");sendStatus(0,b);}
 void scenario(boolean automatic,int selected,boolean duplicate) throws Exception {
  String id="auto-probe-"+UUID.randomUUID();long at=System.currentTimeMillis()+12000;
  Calendar wall=Calendar.getInstance();wall.setTimeInMillis(at);
  JSONObject a=new JSONObject().put("id",id).put("hour",wall.get(Calendar.HOUR_OF_DAY)).put("minute",wall.get(Calendar.MINUTE))
   .put("label","Auto snooze test").put("timer",false).put("days",new JSONArray()).put("enabled",true)
   .put("sound",false).put("vibrate",false).put("snoozeMinutes",5).put("snoozeUntil",at).put("skipThrough",0)
   .put("deleted",false).put("revision",System.currentTimeMillis()).put("writer","auto-snooze-probe");
  try {
   save(a);
   main(()->{controlsClass.getMethod("select",Context.class,String.class,long.class,int.class)
    .invoke(controlsClass.getField("INSTANCE").get(null),c,id,at,selected);return null;});
   waitFor(()->state().getLong(id+".scheduled",0)==at,8000,"Clock fixture not scheduled");
   waitFor(()->state().getLong(id+".active",0)==at,45000,"Real AlarmReceiver did not accept clock fixture");
   long ringing=SystemClock.elapsedRealtime();
   if(duplicate){
    SystemClock.sleep(15000);
    main(()->{c.startForegroundService(new Intent().setClassName(c.getPackageName(),"dev.orbit.clock.core.RingingService")
      .putExtra("id",id).putExtra("at",at));return null;});
   }
   if(!automatic){
    SystemClock.sleep(1200);
    main(()->{schedulerClass.getMethod("act",Context.class,String.class,long.class,boolean.class,Integer.class)
     .invoke(schedulerClass.getField("INSTANCE").get(null),c,id,at,true,Integer.valueOf(selected));return null;});
   }
   waitFor(()->record(id).getLong("skipThrough")==at,automatic?45000:8000,"Snooze did not occur");
   long elapsed=SystemClock.elapsedRealtime()-ringing;
   if(automatic)require(elapsed>=28000 && elapsed<=42000,"Expected approximately 30 seconds, got "+elapsed);
   long expected=(at/60000)*60000+selected*60000L;
   JSONObject changed=record(id);
   require(changed.getLong("snoozeUntil")==expected,"Deadline drift: "+changed+" expected "+expected);
   require(changed.getInt("snoozeMinutes")==5,"Saved default changed");
   waitFor(()->state().getLong(id+".scheduled",0)==expected && state().getLong(id+".active",0)!=at,
      8000,"Snooze not installed or ringing still active");
   report("PASS "+(automatic?"automatic":"manual")+" selected="+selected+" elapsedMs="+elapsed+
      " next="+expected+" duplicate="+duplicate+" savedDefault=5");
  } finally {JSONObject end=record(id);end.put("enabled",false).put("deleted",true);save(end);}
 }
 @Override public void onCreate(Bundle args){super.onCreate(args);start();}
 @Override public void onStart(){
  Bundle result=new Bundle();
  try {
   c=getTargetContext();ClassLoader loader=c.getClassLoader();
   storeClass=Class.forName("dev.orbit.clock.core.Store",true,loader);
   alarmClass=Class.forName("dev.orbit.clock.core.Alarm",true,loader);
   controlsClass=Class.forName("dev.orbit.clock.core.RingControls",true,loader);
   schedulerClass=Class.forName("dev.orbit.clock.core.Scheduler",true,loader);
   companion=storeClass.getField("Companion").get(null);
   scenario(true,5,true);scenario(true,15,false);scenario(false,5,false);
   result.putString("stream","AUTO_SNOOZE_RESULT_PASS: real clock alarms, 30s automatic and manual snooze, aligned deadlines, selection override and duplicate-start guard\n");
   finish(Activity.RESULT_OK,result);
  }catch(Throwable error){result.putString("stream","AUTO_SNOOZE_FAILURE: "+android.util.Log.getStackTraceString(error));finish(Activity.RESULT_CANCELED,result);}
 }
}
