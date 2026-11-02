package dev.orbit.clock.widgetprobe;

import android.app.*; import android.appwidget.*; import android.content.*; import android.graphics.*;
import android.os.*; import android.view.*; import android.widget.*;
import java.io.*; import java.lang.reflect.*; import java.time.*; import java.time.temporal.*;
import java.util.*; import java.util.concurrent.*; import org.json.*;

/** Same-key native host and real scheduler tests. Never bundled in Orbit. */
public class WidgetProbe extends Instrumentation {
 Context c; Activity activity; Object companion,storeCompanion; Class<?> storeClass,alarmClass;
 AppWidgetHost host; AppWidgetManager manager; AppWidgetHostView square,wide,actualSquare,actualWide; boolean previewMode; int squareId,wideId;
 LinearLayout stage; File evidence; int checks; int squareDp=160,wideDp=340,heightDp=160;
 interface Check { boolean ok() throws Exception; }
 <T> T main(Callable<T> call)throws Exception {FutureTask<T> f=new FutureTask<>(call);runOnMainSync(f);return f.get();}
 void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 void waitFor(Check check,String message)throws Exception{
  long end=SystemClock.elapsedRealtime()+45000;
  while(SystemClock.elapsedRealtime()<end){if(check.ok())return;SystemClock.sleep(100);}
  throw new AssertionError(message);
 }
 void pass(String message){checks++;Bundle b=new Bundle();b.putString("stream","PASS "+message+"\n");sendStatus(0,b);}
 int id(String name){return c.getResources().getIdentifier(name,"id",c.getPackageName());}
 TextView text(View root,String name){return (TextView)root.findViewById(id(name));}
 Object snapshot(long now)throws Exception{return companion.getClass().getMethod("snapshot",Context.class,long.class).invoke(companion,c,now);}
 String kind(long now)throws Exception {Object s=snapshot(now);return (String)s.getClass().getMethod("getKind").invoke(s);}
 Object store()throws Exception{return storeClass.getConstructor(Context.class).newInstance(c);}
 JSONObject record(String id)throws Exception {
  JSONArray rows=new JSONArray(c.getSharedPreferences("alarms",0).getString("records","[]"));
  for(int i=0;i<rows.length();i++)if(rows.getJSONObject(i).getString("id").equals(id))return rows.getJSONObject(i);
  throw new AssertionError("Missing "+id);
 }
 void save(JSONObject a)throws Exception {main(()->{Object value=storeCompanion.getClass().getMethod("decode",JSONObject.class).invoke(storeCompanion,a);storeClass.getMethod("save",alarmClass).invoke(store(),value);return null;});}
 JSONObject alarm(String id,int h,int m,JSONArray days)throws Exception {
  return new JSONObject().put("id",id).put("hour",h).put("minute",m).put("label",id).put("timer",false)
   .put("days",days).put("enabled",true).put("sound",false).put("vibrate",false).put("snoozeMinutes",5)
   .put("snoozeUntil",0).put("skipThrough",0).put("deleted",false).put("revision",System.currentTimeMillis()).put("writer","widget-probe");
 }
 void remove(String id)throws Exception {save(record(id).put("deleted",true).put("enabled",false));}
 void renderAt(long now)throws Exception {
  Object s=snapshot(now);
  main(()->{Method v=companion.getClass().getMethod("viewsAtSize",Context.class,boolean.class,s.getClass(),long.class,int.class,int.class);
   boolean preview=Math.abs(now-System.currentTimeMillis())>60000;
   if(preview!=previewMode){
    stage.removeAllViews();square=preview?new AppWidgetHostView(c):actualSquare;wide=preview?new AppWidgetHostView(c):actualWide;
    if(preview){square.setAppWidget(squareId,manager.getAppWidgetInfo(squareId));wide.setAppWidget(wideId,manager.getAppWidgetInfo(wideId));}
    float d=c.getResources().getDisplayMetrics().density;
    stage.addView(square,new LinearLayout.LayoutParams((int)(160*d),(int)(160*d)));
    stage.addView(wide,new LinearLayout.LayoutParams((int)(340*d),(int)(160*d)));previewMode=preview;
   }
   RemoteViews sv=(RemoteViews)v.invoke(companion,c,false,s,now,squareDp,heightDp),wv=(RemoteViews)v.invoke(companion,c,true,s,now,wideDp,heightDp);
   if(preview){square.updateAppWidget(sv);wide.updateAppWidget(wv);}else{manager.updateAppWidget(squareId,sv);manager.updateAppWidget(wideId,wv);}return null;});
  waitFor(()->main(()->square.findViewById(id("widget_clock"))!=null),"Host did not inflate");SystemClock.sleep(300);
 }
 void checkSurface()throws Exception {
  main(()->{
   View event=wide.findViewById(id("widget_event"));require(event.getBackground()==null,"Reminder still owns a separate background");
   ImageView image=(ImageView)wide.findViewById(id("widget_backdrop"));require(image.getVisibility()==View.VISIBLE,"Missing full-widget backdrop");
   require(image.getWidth()>event.getWidth(),"Backdrop is confined to a half tile");
   Bitmap bitmap=((android.graphics.drawable.BitmapDrawable)image.getDrawable()).getBitmap();int w=bitmap.getWidth(),h=bitmap.getHeight();
   require(Color.alpha(bitmap.getPixel(w/4,h/2))==0,"Clock edge is not transparent");
   require(Color.alpha(bitmap.getPixel(w-3,h/2))>=250,"Reminder contrast too low");
   int previous=0;for(int x=0;x<w;x++){int alpha=Color.alpha(bitmap.getPixel(x,h/2));require(alpha>=previous,"Gradient reverses opacity");require(alpha-previous<=6,"Hard alpha seam");previous=alpha;}
   int x=(int)(w*.60f);require(Math.abs(Color.alpha(bitmap.getPixel(x,2))-Color.alpha(bitmap.getPixel(x,h/2)))<=2,"Interior rounded-card edge remains");
   return null;});
 }
 void refresh()throws Exception {main(()->{companion.getClass().getMethod("refresh",Context.class).invoke(companion,c);return null;});}
 void visible(boolean event)throws Exception {
  require(main(()->square.findViewById(id("widget_clock")).getVisibility()==(event?View.GONE:View.VISIBLE)),"Square must show one state");
  require(main(()->wide.findViewById(id("widget_clock")).getVisibility()==View.VISIBLE),"Wide lost clock");
  require(main(()->wide.findViewById(id("widget_event")).getVisibility()==(event?View.VISIBLE:View.INVISIBLE)),"Wide event visibility");
 }
 void capture(String name,int background)throws Exception {
  main(()->{if(name.contains("pattern"))stage.setBackground(new android.graphics.drawable.Drawable(){
    public void draw(Canvas canvas){Paint p=new Paint();int cell=36;for(int y=0;y<getBounds().height();y+=cell)for(int x=0;x<getBounds().width();x+=cell){p.setColor(((x/cell+y/cell)%2==0)?Color.rgb(247,241,223):Color.rgb(156,168,173));canvas.drawRect(x,y,x+cell,y+cell,p);}}
    public void setAlpha(int a){}public void setColorFilter(ColorFilter f){}public int getOpacity(){return android.graphics.PixelFormat.OPAQUE;}
   });else stage.setBackgroundColor(background);
   int height=stage.getChildAt(stage.getChildCount()-1).getBottom()+stage.getPaddingBottom();
   Bitmap b=Bitmap.createBitmap(stage.getWidth(),Math.min(stage.getHeight(),height),Bitmap.Config.ARGB_8888);stage.draw(new Canvas(b));
   try(FileOutputStream f=new FileOutputStream(new File(evidence,name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,f);}b.recycle();return null;});
 }
 boolean hasText(View view,String wanted){
  if(view instanceof TextView && ((TextView)view).getText().toString().equals(wanted))return true;
  if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++)if(hasText(g.getChildAt(i),wanted))return true;}return false;
 }
 View findText(View view,String wanted){
  if(view instanceof TextView && ((TextView)view).getText().toString().equals(wanted))return view;
  if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++){View v=findText(g.getChildAt(i),wanted);if(v!=null)return v;}}return null;
 }
 void click(View view)throws Exception{main(()->{require(view.performClick(),"Missing widget click");return null;});}
 AppWidgetHostView bind(String provider,int widgetId,int w,int h)throws Exception{
  ComponentName component=new ComponentName(c.getPackageName(),"dev.orbit.clock.phone."+provider);
  Bundle options=new Bundle();options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,w);options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,h);
  options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,w);options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,h);
  require(manager.bindAppWidgetIdIfAllowed(widgetId,component,options),"Widget bind permission missing");
  AppWidgetHostView view=host.createView(c,widgetId,manager.getAppWidgetInfo(widgetId));
  float density=c.getResources().getDisplayMetrics().density;
  stage.addView(view,new LinearLayout.LayoutParams((int)(w*density),(int)(h*density)));return view;
 }
 @Override public void onCreate(Bundle args){super.onCreate(args);start();}
 @Override public void onStart(){Bundle result=new Bundle();try{
  c=getTargetContext();ClassLoader loader=c.getClassLoader();
  companion=Class.forName("dev.orbit.clock.phone.AlarmWidget",true,loader).getField("Companion").get(null);
  storeClass=Class.forName("dev.orbit.clock.core.Store",true,loader);alarmClass=Class.forName("dev.orbit.clock.core.Alarm",true,loader);
  storeCompanion=storeClass.getField("Companion").get(null);
  evidence=new File(c.getExternalFilesDir(null),"widget-evidence");require(evidence.mkdirs()||evidence.isDirectory(),"Evidence folder");
  activity=startActivitySync(new Intent().setClassName(c.getPackageName(),"dev.orbit.clock.phone.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  main(()->{stage=new LinearLayout(activity);stage.setOrientation(LinearLayout.VERTICAL);stage.setPadding(16,40,16,16);
   ((android.widget.FrameLayout)activity.findViewById(android.R.id.content)).addView(stage,new android.widget.FrameLayout.LayoutParams(-1,-1));
   manager=AppWidgetManager.getInstance(c);host=new AppWidgetHost(c,8765);host.startListening();
   squareId=host.allocateAppWidgetId();wideId=host.allocateAppWidgetId();square=bind("AlarmWidget",squareId,160,160);wide=bind("WideAlarmWidget",wideId,340,160);actualSquare=square;actualWide=wide;return null;});
  refresh();renderAt(System.currentTimeMillis());visible(false);capture("01-clock-dark",Color.rgb(25,32,45));capture("02-clock-light",Color.rgb(230,225,210));
  pass("both real AppWidgetHost bindings, transparent idle clock only, stable empty right half");
  Calendar cal=Calendar.getInstance();cal.add(Calendar.MINUTE,10);
  save(alarm("Near alarm",cal.get(Calendar.HOUR_OF_DAY),cal.get(Calendar.MINUTE),new JSONArray()));
  waitFor(()->kind(System.currentTimeMillis()).equals("alarm"),"Alarm not near");renderAt(System.currentTimeMillis());visible(true);
  capture("03-alarm-dark",Color.rgb(25,32,45));capture("04-alarm-light",Color.rgb(240,235,220));capture("04b-alarm-pattern",0);
  checkSurface();pass("one full-width backdrop, no inner tile, continuous alpha and transparent clock edge");
  long now=System.currentTimeMillis();for(int minutes:new int[]{10,15,30,45,60,90}){
   c.getSharedPreferences("widget",0).edit().putInt("alarmWindow",minutes).commit();
   JSONObject a=record("Near alarm").put("snoozeUntil",now+(minutes+1)*60000L);save(a);
   require(kind(now).equals("clock"),"Outside "+minutes);save(a.put("snoozeUntil",now+minutes*60000L));require(kind(now).equals("alarm"),"Inside "+minutes);
  }
  c.getSharedPreferences("widget",0).edit().putInt("alarmWindow",15).commit();
  save(record("Near alarm").put("snoozeUntil",System.currentTimeMillis()+15*60000+6000));refresh();
  waitFor(()->main(()->square.findViewById(id("widget_clock")).getVisibility()==View.VISIBLE),"Outside-window clock");
  waitFor(()->main(()->square.findViewById(id("widget_clock")).getVisibility()==View.GONE),"Scheduled visual boundary did not enter reminder mode");
  pass("actual non-wakeup AlarmManager boundary switches clock to reminder without polling");
  save(record("Near alarm").put("snoozeUntil",System.currentTimeMillis()+5*60000));renderAt(System.currentTimeMillis());
  require(main(()->square.findViewById(id("widget_event")).getContentDescription().toString().startsWith("Snoozed")),"Snooze state");capture("05-snoozed",Color.rgb(75,85,67));
  save(alarm("Later alarm",8,0,new JSONArray()).put("snoozeUntil",System.currentTimeMillis()+45*60000));
  save(alarm("Timer fixture",8,0,new JSONArray()).put("timer",true).put("snoozeUntil",System.currentTimeMillis()+60000));
  Object selection=snapshot(System.currentTimeMillis());Object selected=selection.getClass().getMethod("getAlarm").invoke(selection);
  require(selected!=null && selected.getClass().getMethod("getId").invoke(selected).equals("Near alarm"),"Earliest clock alarm selection / timer exclusion");
  remove("Timer fixture");
  pass("all six reminder windows, exact boundaries, multiple alarms, timer exclusion and snooze deadline");
  // The child's PendingIntent is sent by the real host, not by a test-only application hook.
  click(square.findViewById(id("widget_toggle")));waitFor(()->!record("Near alarm").getBoolean("enabled"),"Toggle did not save");
  waitFor(()->main(()->square.findViewById(id("widget_clock")).getVisibility()==View.VISIBLE),"Store broadcast did not refresh host");
  require(record("Near alarm").getInt("snoozeMinutes")==5,"Toggle changed defaults");pass("native host toggle, saved state and automatic return to clock");
  require(record("Later alarm").getBoolean("enabled"),"Toggle changed another alarm");remove("Later alarm");
  remove("Near alarm");
  JSONObject wake=alarm("Wake up",7,0,new JSONArray(Arrays.asList(1,2,3,4,5)));save(wake);
  LocalDate monday=LocalDate.now().plusDays(14).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
  long bed=monday.atTime(23,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
  long before=bed-10*60000;
  require(kind(before).equals("bedtime"),"Saved sleep-goal bedtime");renderAt(before);visible(true);capture("06-bedtime",Color.rgb(25,32,45));capture("06b-bedtime-pattern",0);
  require(main(()->text(square,"widget_toggle").getVisibility()!=View.VISIBLE),"Bedtime has alarm control");
  require(kind(bed).equals("bednow"),"Bedtime arrival");renderAt(bed);capture("07-bednow",Color.rgb(25,32,45));
  click(square.findViewById(id("widget_event")));main(()->{stage.setVisibility(View.GONE);return null;});
  waitFor(()->main(()->hasText(activity.getWindow().getDecorView(),"A rhythm for your rest")),"Bedtime deep link");
  main(()->{stage.setVisibility(View.VISIBLE);return null;});
  require(kind(bed+15*60000).equals("clock"),"Bedtime grace expiry");
  long friday=monday.plusDays(4).atTime(23,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
  require(kind(friday-5*60000).equals("clock"),"No Saturday wake means no Friday bedtime");
  for(int minutes:new int[]{10,15,30,45,60,90}){
   c.getSharedPreferences("widget",0).edit().putInt("bedtimeWindow",minutes).commit();
   require(kind(bed-minutes*60000L).equals("bedtime"),"Bed window "+minutes);
   require(kind(bed-minutes*60000L-1).equals("clock"),"Bed outside "+minutes);
  }
  c.getSharedPreferences("widget",0).edit().putInt("bedtimeWindow",15).commit();
  c.getSharedPreferences("tools",0).edit().putInt("sleepGoal",7).commit();require(kind(before).equals("clock"),"Sleep goal did not move bedtime");
  c.getSharedPreferences("tools",0).edit().putInt("sleepGoal",8).commit();
  c.getSharedPreferences("widget",0).edit().putInt("bedtimeWindow",15).putBoolean("bedtimeEnabled",false).commit();require(kind(before).equals("clock"),"Bedtime opt out");
  c.getSharedPreferences("widget",0).edit().putBoolean("bedtimeEnabled",true).commit();
  TimeZone original=TimeZone.getDefault();try{
   TimeZone.setDefault(TimeZone.getTimeZone(original.getID().equals("Asia/Tokyo")?"America/New_York":"Asia/Tokyo"));
   require(kind(before).equals("clock"),"Bedtime did not follow local zone");
  }finally{TimeZone.setDefault(original);}remove("Wake up");
  pass("bedtime across midnight, repeat days, all windows, sleep-goal edits, 15-minute grace and opt out");
  // Real AlarmReceiver delivery, then the production snooze action.
  long at=System.currentTimeMillis()+12000;Calendar aCal=Calendar.getInstance();aCal.setTimeInMillis(at);
  save(alarm("Delivery",aCal.get(Calendar.HOUR_OF_DAY),aCal.get(Calendar.MINUTE),new JSONArray()).put("snoozeUntil",at));
  waitFor(()->kind(System.currentTimeMillis()).equals("ringing"),"Actual alarm not delivered");
  Class<?> scheduler=Class.forName("dev.orbit.clock.core.Scheduler",true,loader);Object schedulerObject=scheduler.getField("INSTANCE").get(null);
  main(()->{scheduler.getMethod("act",Context.class,String.class,long.class,boolean.class,Integer.class).invoke(schedulerObject,c,"Delivery",at,true,Integer.valueOf(5));return null;});
  waitFor(()->record("Delivery").getLong("snoozeUntil")>at,"Real snooze not saved");
  require(kind(System.currentTimeMillis()).equals("alarm"),"Snooze not upcoming");
  require(record("Delivery").getInt("snoozeMinutes")==5,"Saved default mutation");
  pass("actual AlarmManager receiver delivery, ringing priority and production snooze");
  // Verify direct alarm and bedtime links using real pending intents.
  main(()->{c.startActivity(new Intent().setClassName(c.getPackageName(),"dev.orbit.clock.phone.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP));return null;});
  waitFor(()->activity.hasWindowFocus(),"Main activity did not regain focus");
  renderAt(System.currentTimeMillis());
  main(()->{float d=c.getResources().getDisplayMetrics().density;square.setLayoutParams(new LinearLayout.LayoutParams((int)(130*d),(int)(130*d)));wide.setLayoutParams(new LinearLayout.LayoutParams((int)(250*d),(int)(130*d)));squareDp=130;wideDp=250;heightDp=130;return null;});
  renderAt(System.currentTimeMillis());
  SystemClock.sleep(400);checkSurface();
  capture("08-small",Color.rgb(240,235,220));
  require(main(()->{View event=square.findViewById(id("widget_event"));View count=square.findViewById(id("widget_countdown"));return count.getBottom()<=event.getHeight();}),"Small square clips countdown");
  require(main(()->{View event=wide.findViewById(id("widget_event"));View count=wide.findViewById(id("widget_countdown"));return count.getBottom()<=event.getHeight();}),"Small wide clips countdown");
  capture("08-small",Color.rgb(240,235,220));pass("both resized native hosts retain readable countdown geometry");
  click(square.findViewById(id("widget_event")));main(()->{stage.setVisibility(View.GONE);return null;});
  waitFor(()->{for(Window w:new Window[]{activity.getWindow()})if(main(()->hasText(w.getDecorView(),"Edit alarm")))return true;
   return getUiAutomation().getRootInActiveWindow()!=null && getUiAutomation().getRootInActiveWindow().findAccessibilityNodeInfosByText("Edit alarm").size()>0;},"Alarm deep link");
  remove("Delivery");pass("host alarm tap opens existing alarm editor");
  Activity settings=startActivitySync(new Intent().setClassName(c.getPackageName(),"dev.orbit.clock.phone.WidgetSettingsActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  require(main(()->hasText(settings.getWindow().getDecorView(),"Before alarms · 15 min")),"Default settings window");
  click(main(()->findText(settings.getWindow().getDecorView(),"Before alarms · 15 min")));
  waitFor(()->getUiAutomation().getRootInActiveWindow()!=null && !getUiAutomation().getRootInActiveWindow().findAccessibilityNodeInfosByText("90 minutes").isEmpty(),"90-minute choice missing");
  android.view.accessibility.AccessibilityNodeInfo node=getUiAutomation().getRootInActiveWindow().findAccessibilityNodeInfosByText("90 minutes").get(0);
  while(node!=null && !node.isClickable())node=node.getParent();require(node!=null && node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK),"90-minute selection");
  waitFor(()->c.getSharedPreferences("widget",0).getInt("alarmWindow",15)==90,"Settings did not persist 90");
  pass("actual settings UI, default15 and persisted90-minute selection");
  main(()->{settings.finish();return null;});
  result.putString("stream","WIDGET_RESULT_PASS checks="+checks+"\n");finish(Activity.RESULT_OK,result);
 }catch(Throwable error){result.putString("stream","WIDGET_FAILURE: "+android.util.Log.getStackTraceString(error));finish(Activity.RESULT_CANCELED,result);}
 finally{try{if(host!=null)main(()->{host.stopListening();host.deleteHost();return null;});}catch(Exception ignored){}}
 }
}
