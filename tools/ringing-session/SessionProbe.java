package dev.orbit.clock.sessionprobe;

import android.app.*;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.*;
import android.graphics.*;
import android.media.AudioManager;
import android.os.*;
import android.view.*;
import android.view.accessibility.*;
import org.json.*;
import org.xmlpull.v1.XmlPullParser;
import android.util.Xml;
import java.io.*;
import java.lang.reflect.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/** Same-key instrumentation and a separate real media app. Never ships in Orbit. */
public class SessionProbe extends Instrumentation {
 Context c; Class<?> storeClass,alarmClass,controlsClass; Object storeCompanion,controls;
 Activity ringing; String scale,scope;
 interface Check{boolean ok()throws Exception;}
 <T>T main(Callable<T> action)throws Exception{
  FutureTask<T> f=new FutureTask<>(action);
  require(new Handler(Looper.getMainLooper()).post(f),"Main queue rejected test action");
  return f.get(15,TimeUnit.SECONDS);
 }
 void idle()throws Exception{
  CountDownLatch ready=new CountDownLatch(1);
  require(new Handler(Looper.getMainLooper()).post(()->Looper.myQueue().addIdleHandler(()->{ready.countDown();return false;})),"Main queue rejected idle observer");
  require(ready.await(10,TimeUnit.SECONDS),"Main queue did not become idle within10s");
 }
 void require(boolean b,String message){if(!b)throw new AssertionError(message);}
 void waitFor(Check condition,long limit,String message)throws Exception{long end=SystemClock.uptimeMillis()+limit;while(SystemClock.uptimeMillis()<end){if(condition.ok())return;SystemClock.sleep(100);}throw new AssertionError(message);}
 String shell(String command)throws Exception{try(ParcelFileDescriptor fd=getUiAutomation().executeShellCommand(command);InputStream input=new ParcelFileDescriptor.AutoCloseInputStream(fd)){ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;while((n=input.read(buffer))!=-1)bytes.write(buffer,0,n);return bytes.toString("UTF-8");}}
 void pass(String message){Bundle out=new Bundle();out.putString("stream","PASS "+message+"\n");sendStatus(0,out);}
 Object store()throws Exception{return storeClass.getConstructor(Context.class).newInstance(c);}
 Object alarm(JSONObject json)throws Exception{return storeCompanion.getClass().getMethod("decode",JSONObject.class).invoke(storeCompanion,json);}
 Object save(JSONObject json)throws Exception{return main(()->storeClass.getMethod("save",alarmClass).invoke(store(),alarm(json)));}
 JSONObject row(String id)throws Exception{JSONArray rows=new JSONArray(c.getSharedPreferences("alarms",0).getString("records","[]"));for(int i=0;i<rows.length();i++)if(id.equals(rows.getJSONObject(i).getString("id")))return rows.getJSONObject(i);throw new AssertionError("Missing "+id);}
 SharedPreferences state(){return c.getSharedPreferences("scheduled_occurrences",0);}
 long occurrence(String id)throws Exception{return row(id).getLong("snoozeUntil");}
 boolean active(String id)throws Exception{long current=state().getLong(id+".active",0);return current>0 && current==occurrence(id);}
 String fixture(String label,int delay)throws Exception{
  String id="session-probe-"+UUID.randomUUID();long at=System.currentTimeMillis()+delay;
  ZonedDateTime wall=Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault());
  save(new JSONObject().put("id",id).put("hour",wall.getHour()).put("minute",wall.getMinute()).put("label",label)
   .put("timer",true).put("days",new JSONArray()).put("enabled",true).put("sound",false).put("vibrate",false)
   .put("snoozeMinutes",10).put("snoozeUntil",at).put("skipThrough",0).put("deleted",false).put("revision",System.currentTimeMillis()).put("writer","session-probe"));
  return id;
 }
 void delivered(String id)throws Exception{waitFor(()->active(id),35000,"Real alarm delivery missing");}
 Map<String,String> readPlayer()throws Exception{
  String xml=shell("run-as dev.orbit.focusprobe cat shared_prefs/probe.xml");Map<String,String> result=new HashMap<>();
  XmlPullParser parser=Xml.newPullParser();parser.setInput(new StringReader(xml));int type;
  while((type=parser.next())!=XmlPullParser.END_DOCUMENT)if(type==XmlPullParser.START_TAG){String name=parser.getAttributeValue(null,"name");if(name!=null){String value=parser.getAttributeValue(null,"value");if(value==null)value=parser.nextText();result.put(name,value);}}
  if(!result.containsKey("playing") || !result.containsKey("events"))throw new IOException("Incomplete player snapshot");
  return result;
 }
 Map<String,String> player()throws Exception{Exception last=null;for(int i=0;i<12;i++)try{return readPlayer();}catch(org.xmlpull.v1.XmlPullParserException|IOException error){last=error;SystemClock.sleep(80);}throw last;}
 boolean playing()throws Exception{return "true".equals(player().get("playing"));}
 int plays()throws Exception{String e=player().get("events");return e==null?0:e.split("media_play;",-1).length-1;}
 void startPlayer(String mode)throws Exception{
  shell("am force-stop dev.orbit.focusprobe");
  shell("am start -W -f 0x10008000 -n dev.orbit.focusprobe/.ProbeActivity --es mode "+mode);
  waitFor(()->playing() && mode.equals(player().get("mode")),15000,"Media fixture did not start "+mode);
  waitFor(()->c.getSystemService(AudioManager.class).isMusicActive(),10000,"Native media stream not active");
 }
 void paused()throws Exception{waitFor(()->!playing() && player().get("events").contains("media_pause;"),10000,"Alarm did not pause media");}
 void resumed(int before)throws Exception{
  waitFor(()->playing(),10000,"Previously playing media did not resume");
  require(plays()>before,"No explicit Play delivered for the interrupted player");int position=Integer.parseInt(player().get("position"));
  waitFor(()->playing() && Integer.parseInt(player().get("position"))!=position,5000,"Resumed media did not advance");
 }
 void action(String id,String action)throws Exception{
  long at=occurrence(id);Object saved=storeClass.getMethod("get",String.class).invoke(store(),id);
  PendingIntent pending=(PendingIntent)controlsClass.getMethod("pending",Context.class,alarmClass,long.class,String.class).invoke(controls,c,saved,at,action);
  pending.send();waitFor(()->!active(id),7000,"Alarm action not committed");
 }
 void clean(String id)throws Exception{save(row(id).put("enabled",false).put("deleted",true));}
 AccessibilityNodeInfo find(AccessibilityNodeInfo node,String wanted){if(node==null)return null;CharSequence d=node.getContentDescription(),t=node.getText();if(wanted.contentEquals(d==null?"":d)||wanted.contentEquals(t==null?"":t))return node;for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo got=find(node.getChild(i),wanted);if(got!=null)return got;}return null;}
 AccessibilityNodeInfo popup(){for(AccessibilityWindowInfo w:getUiAutomation().getWindows()){AccessibilityNodeInfo got=find(w.getRoot(),"Persistent alarm controls");if(got!=null)return got;}return null;}
 void popupVisible()throws Exception{waitFor(()->popup()!=null,10000,"Persistent controls are not visible");}
 void popupGone()throws Exception{waitFor(()->popup()==null,7000,"Persistent controls remained after action");}
 void clickPopup(String label)throws Exception{
  final Rect[] settled=new Rect[1];final int[] stable={0};
  waitFor(()->{AccessibilityNodeInfo button=find(popup(),label);if(button==null || !button.isVisibleToUser() || !button.isEnabled()){stable[0]=0;return false;}
   Rect now=new Rect();button.getBoundsInScreen(now);if(now.isEmpty()){stable[0]=0;return false;}
   boolean same=settled[0]!=null && Math.abs(now.left-settled[0].left)<=1 && Math.abs(now.top-settled[0].top)<=1 && Math.abs(now.right-settled[0].right)<=1 && Math.abs(now.bottom-settled[0].bottom)<=1;
   stable[0]=same?stable[0]+1:0;settled[0]=now;return stable[0]>=2;
  },7000,"Popup action did not settle: "+label);
  Rect b=settled[0];tap(b.centerX(),b.centerY());
 }
 void assertAdjustmentGlyphs()throws Exception{
  Bitmap display=getUiAutomation().takeScreenshot();require(display!=null,"No hardware display for glyph check");
  try{for(String label:new String[]{"Decrease snooze by 5 minutes","Increase snooze by 5 minutes"}){
   AccessibilityNodeInfo button=find(popup(),label);require(button!=null,"Missing adjustment button "+label);Rect bounds=new Rect();button.getBoundsInScreen(bounds);
   int radius=Math.max(5,(int)(16*c.getResources().getDisplayMetrics().density));
   Rect inner=new Rect(Math.max(0,bounds.centerX()-radius),Math.max(0,bounds.centerY()-radius),Math.min(display.getWidth(),bounds.centerX()+radius),Math.min(display.getHeight(),bounds.centerY()+radius));
   int ink=0,background=0;for(int y=inner.top;y<inner.bottom;y++)for(int x=inner.left;x<inner.right;x++){
    int color=display.getPixel(x,y),red=Color.red(color),green=Color.green(color),blue=Color.blue(color);
    if(red>100 && green>145 && blue>105)ink++;
    else if(green>red+15 && green>blue+5)background++;
   }
   require(ink>=8 && background>=30,"Adjustment glyph missing/clipped in actual display: "+label+" ink="+ink+" background="+background);
  }}finally{display.recycle();}
  pass("both adjustment glyphs are visible in actual hardware-display pixels at "+scale+"% text");
 }

 void tap(float x,float y)throws Exception{long now=SystemClock.uptimeMillis();for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP}){MotionEvent e=MotionEvent.obtain(now,SystemClock.uptimeMillis(),action,x,y,0);e.setSource(InputDevice.SOURCE_TOUCHSCREEN);getUiAutomation().injectInputEvent(e,true);e.recycle();}idle();}
 void swipe(float x,float y,float endX,float endY)throws Exception{long down=SystemClock.uptimeMillis();for(int i=0;i<=12;i++){int action=i==0?MotionEvent.ACTION_DOWN:i==12?MotionEvent.ACTION_UP:MotionEvent.ACTION_MOVE;float f=i/12f;MotionEvent e=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,x+(endX-x)*f,y+(endY-y)*f,0);e.setSource(InputDevice.SOURCE_TOUCHSCREEN);getUiAutomation().injectInputEvent(e,true);e.recycle();SystemClock.sleep(25);}idle();}
 void capture(String name)throws Exception{Bitmap b=getUiAutomation().takeScreenshot();require(b!=null,"No display capture");File dir=new File(c.getFilesDir(),"session-feedback");dir.mkdirs();try(FileOutputStream out=new FileOutputStream(new File(dir,name+"-"+scale+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
 void mediaCases()throws Exception{
  shell("appops set dev.orbit.clock SYSTEM_ALERT_WINDOW deny");startPlayer("music");
  String a=fixture("First overlap",7000),b=fixture("Second overlap",10500);delivered(a);paused();delivered(b);paused();
  int before=plays();action(b,"stop");SystemClock.sleep(600);require(!playing() && plays()==before,"First overlapping Stop resumed media");
  action(a,"stop");resumed(before);require(popup()==null,"No-permission alarm created an overlay");clean(a);clean(b);
  pass("real music pause; no-overlay permission fallback; first overlapping Stop stays paused; last Stop resumes playback");
  startPlayer("video");a=fixture("Video snooze",7000);delivered(a);paused();before=plays();long old=occurrence(a);action(a,"snooze");resumed(before);
  require(row(a).getLong("skipThrough")==old && row(a).getLong("snoozeUntil")>System.currentTimeMillis()+9*60000,"Snooze did not preserve deadline/default");
  AlarmManager.AlarmClockInfo installed=c.getSystemService(AlarmManager.class).getNextAlarmClock();
  require(installed!=null && installed.getTriggerTime()==row(a).getLong("snoozeUntil"),"Actual snooze was not scheduled");
  require(row(a).getInt("snoozeMinutes")==10,"Saved snooze default changed");clean(a);pass("real foreground video resumes after notification Snooze, including explicit media Play fallback");
  startPlayer("music");shell("cmd media_session dispatch pause");waitFor(()->!playing(),8000,"Pre-paused fixture did not pause");
  waitFor(()->!c.getSystemService(AudioManager.class).isMusicActive(),5000,"Paused stream still active");before=plays();a=fixture("Already paused",7000);delivered(a);action(a,"stop");SystemClock.sleep(1200);
  require(!playing() && plays()==before,"Alarm started previously paused media");clean(a);pass("already-paused media is never started");
 }
 void popupCases()throws Exception{
  shell("appops set dev.orbit.clock SYSTEM_ALERT_WINDOW allow");startPlayer("music");String id=fixture("Persistent alarm test",7000);delivered(id);paused();popupVisible();SystemClock.sleep(200);capture("persistent-popup");assertAdjustmentGlyphs();
  Rect bounds=new Rect();popup().getBoundsInScreen(bounds);float density=c.getResources().getDisplayMetrics().density;
  swipe(bounds.left+24*density,bounds.top+20*density,bounds.right-24*density,bounds.top+20*density);popupVisible();require(active(id) && !playing(),"Swipe dismissed popup or resumed playback");
  shell("input keyevent BACK");popupVisible();require(active(id),"Back outside alarm dismissed popup");
  pass("granted persistent popup survives horizontal swipe and Back without resolving the alarm");
  // Popup selection is shared with notification and full-screen controls.
  clickPopup("Increase snooze by 5 minutes");waitFor(()->find(popup(),"Snooze duration: 15 minutes")!=null,6000,"Popup duration did not update");
  clickPopup("Snooze");final String snoozeId=id;waitFor(()->!active(snoozeId),7000,"Persistent popup Snooze did not act");resumed(0);popupGone();
  require(row(id).getInt("snoozeMinutes")==10,"Popup adjusted saved default");clean(id);pass("persistent popup plus/Snooze resumes music with occurrence-only duration");
  startPlayer("music");id=fixture("Permission revoked",7000);delivered(id);paused();popupVisible();shell("appops set dev.orbit.clock SYSTEM_ALERT_WINDOW deny");popupGone();
  require(active(id) && !playing(),"Permission revocation silenced or resolved alarm");action(id,"stop");resumed(0);clean(id);pass("permission revocation safely falls back to notification; Stop resumes media");
  shell("appops set dev.orbit.clock SYSTEM_ALERT_WINDOW allow");startPlayer("music");id=fixture("Popup Stop",7000);delivered(id);paused();popupVisible();clickPopup("Stop");
  final String stopId=id;waitFor(()->!row(stopId).getBoolean("enabled"),7000,"Popup Stop did not stop");resumed(0);popupGone();clean(id);pass("persistent popup Stop commits and resumes previous music");
 }
 void screenCase()throws Exception{
  shell("appops set dev.orbit.clock SYSTEM_ALERT_WINDOW allow");startPlayer("music");
  String id=fixture("Full screen Back and Home test",7000);delivered(id);paused();popupVisible();
  ActivityMonitor ringingMonitor=addMonitor("dev.orbit.clock.core.RingActivity",null,false);
  try {
   PendingIntent launch=null;
   for(android.service.notification.StatusBarNotification notification:c.getSystemService(NotificationManager.class).getActiveNotifications())
    if(notification.getId()==1){launch=notification.getNotification().contentIntent;break;}
   require(launch!=null && c.getPackageName().equals(launch.getCreatorPackage()),"Actual ringing notification content intent missing");
   Bundle launchStep=new Bundle();launchStep.putString("stream","STEP sending actual ringing notification content intent\n");sendStatus(0,launchStep);
   launch.send();
   launchStep.putString("stream","STEP notification content intent sent; waiting for RingActivity\n");sendStatus(0,launchStep);
   ringing=ringingMonitor.waitForActivityWithTimeout(15000);
   require(ringing!=null,"Ringing notification did not launch RingActivity within15s");
   launchStep.putString("stream","STEP RingActivity matched by bounded monitor\n");sendStatus(0,launchStep);
  } finally { removeMonitor(ringingMonitor); }
  idle();waitFor(()->main(()->ringing.hasWindowFocus()),7000,"Ringing screen not focused");popupGone();
  sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);SystemClock.sleep(400);require(main(()->!ringing.isFinishing() && ringing.hasWindowFocus()) && active(id),"Back dismissed active ringing screen");
  capture("full-screen-back-blocked");pass("full ringing screen blocks Back while the occurrence is active");
  shell("input keyevent HOME");popupVisible();require(active(id) && !playing(),"Home resolved alarm or resumed media");
  clickPopup("Increase snooze by 5 minutes");waitFor(()->find(popup(),"Snooze duration: 15 minutes")!=null,6000,"Popup duration did not update");
  clickPopup("Snooze");waitFor(()->!active(id),7000,"Home popup Snooze did not act");resumed(0);popupGone();
  require(row(id).getInt("snoozeMinutes")==10,"Popup adjusted saved default");clean(id);
  pass("independent full-screen fixture: actual notification launch, Back blocking, Home handoff and occurrence-only popup Snooze resume");
 }
 @Override public void onCreate(Bundle arguments){super.onCreate(arguments);scale=arguments.getString("scale","100");scope=arguments.getString("scope","all");start();}
 @Override public void onStart(){Bundle result=new Bundle();try{
  c=getTargetContext();ClassLoader loader=c.getClassLoader();storeClass=Class.forName("dev.orbit.clock.core.Store",true,loader);alarmClass=Class.forName("dev.orbit.clock.core.Alarm",true,loader);
  controlsClass=Class.forName("dev.orbit.clock.core.RingControls",true,loader);
  storeCompanion=storeClass.getField("Companion").get(null);controls=controlsClass.getField("INSTANCE").get(null);
  AccessibilityServiceInfo info=getUiAutomation().getServiceInfo();info.flags|=AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;getUiAutomation().setServiceInfo(info);
  require(Math.abs(c.getResources().getConfiguration().fontScale-Float.parseFloat(scale)/100f)<.04f,"Wrong actual text scale");
  require(scope.equals("all") || scope.equals("popup") || scope.equals("screen"),"Unknown test scope "+scope);
  Bundle scopeStep=new Bundle();scopeStep.putString("stream","STEP scope="+scope+" api="+Build.VERSION.SDK_INT+" text="+scale+"%\n");sendStatus(0,scopeStep);
  if(scope.equals("all") || scope.equals("popup")){mediaCases();popupCases();}
  if(scope.equals("all") || scope.equals("screen"))screenCase();
  shell("am force-stop dev.orbit.focusprobe");
  result.putString("stream","RINGING_SESSION_RESULT_PASS scope="+scope+" api="+Build.VERSION.SDK_INT+" at "+scale+"% text\n");finish(Activity.RESULT_OK,result);
 }catch(Throwable error){try{capture("failure");}catch(Throwable ignored){}result.putString("stream","RINGING_SESSION_FAILURE scope="+scope+" api="+Build.VERSION.SDK_INT+": "+android.util.Log.getStackTraceString(error));finish(Activity.RESULT_CANCELED,result);}}
}
