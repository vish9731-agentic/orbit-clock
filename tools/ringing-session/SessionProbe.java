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
 Map<String,String> lastPlayerSnapshot=Collections.emptyMap();
 final StringBuilder mediaTrace=new StringBuilder();
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
  lastPlayerSnapshot=result;return result;
 }
 Map<String,String> player()throws Exception{Exception last=null;for(int i=0;i<12;i++)try{return readPlayer();}catch(org.xmlpull.v1.XmlPullParserException|IOException error){last=error;SystemClock.sleep(80);}throw last;}
 boolean playing()throws Exception{return "true".equals(player().get("playing"));}
 int plays()throws Exception{String e=player().get("events");return e==null?0:e.split("media_play;",-1).length-1;}
 boolean nativeMediaActive(){
  for(android.media.AudioPlaybackConfiguration configuration:c.getSystemService(AudioManager.class).getActivePlaybackConfigurations()){
   int usage=configuration.getAudioAttributes().getUsage();
   if(usage==android.media.AudioAttributes.USAGE_MEDIA||usage==android.media.AudioAttributes.USAGE_UNKNOWN)return true;
  }
  return false;
 }
 void startPlayer(String mode)throws Exception{
  shell("am force-stop dev.orbit.focusprobe");
  shell("am start -W -f 0x10008000 -n dev.orbit.focusprobe/.ProbeActivity --es mode "+mode);
  waitFor(()->playing() && mode.equals(player().get("mode")),15000,"Media fixture did not start "+mode);
  waitFor(()->nativeMediaActive(),10000,"No active native media playback configuration");
  traceMedia("player-ready:"+mode);
 }
 void paused()throws Exception{waitFor(()->!playing() && player().get("events").contains("media_pause;"),10000,"Alarm did not pause media");traceMedia("alarm-paused-player");}
 void resumed(int before)throws Exception{
  traceMedia("expecting-resume");
  waitFor(()->playing(),10000,"Previously playing media did not resume");
  require(plays()>before,"No explicit Play delivered for the interrupted player");int position=Integer.parseInt(player().get("position"));
  waitFor(()->playing() && Integer.parseInt(player().get("position"))!=position,5000,"Resumed media did not advance");
  traceMedia("playback-resumed");
 }
 // Cheap observations use the last existing player read, without extra shell I/O
 // between pause and a button tap that could accidentally hide a timing race.
 void traceMedia(String stage){
  mediaTrace.append(SystemClock.uptimeMillis()).append(' ').append(stage).append(' ');
  try{AudioManager manager=c.getSystemService(AudioManager.class);mediaTrace.append("isMusicActive=").append(manager.isMusicActive()).append(" playback=").append(manager.getActivePlaybackConfigurations());}catch(Throwable error){mediaTrace.append(error);}
  mediaTrace.append(" cachedPlayer=").append(lastPlayerSnapshot).append('\n');
 }
 void dumpFailureDiagnostics(){
  traceMedia("failure");StringBuilder out=new StringBuilder(mediaTrace);
  try{out.append("\nPLAYER_NOW\n").append(player());}catch(Throwable error){out.append("\nPLAYER_READ_ERROR ").append(error);}
  try{out.append("\nORBIT_RECORDS\n").append(c.getSharedPreferences("alarms",0).getAll()).append("\nOCCURRENCES\n").append(state().getAll());}catch(Throwable error){out.append("\nORBIT_STATE_ERROR ").append(error);}
  for(String command:new String[]{"dumpsys media_session","dumpsys audio","dumpsys activity services dev.orbit.clock"})try{out.append("\nCOMMAND ").append(command).append('\n').append(shell(command));}catch(Throwable error){out.append("\nDUMP_ERROR ").append(error);}
  try{File dir=new File(c.getFilesDir(),"session-feedback");dir.mkdirs();try(FileOutputStream file=new FileOutputStream(new File(dir,"failure-diagnostics-"+scale+".txt"))){file.write(out.toString().getBytes("UTF-8"));}}catch(Throwable ignored){}
  Bundle status=new Bundle();status.putString("stream","MEDIA_TRACE\n"+mediaTrace);sendStatus(0,status);
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
 double luminance(int color){
  double value=0;double[] weights={.2126,.7152,.0722};int[] channels={Color.red(color),Color.green(color),Color.blue(color)};
  for(int i=0;i<3;i++){double c=channels[i]/255.;value+=weights[i]*(c<=.04045?c/12.92:Math.pow((c+.055)/1.055,2.4));}return value;
 }
 double contrast(int a,int b){double x=luminance(a),y=luminance(b);return(Math.max(x,y)+.05)/(Math.min(x,y)+.05);}
 AccessibilityNodeInfo findPrefix(AccessibilityNodeInfo node,String prefix){if(node==null)return null;CharSequence d=node.getContentDescription();if(d!=null&&d.toString().startsWith(prefix))return node;for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo found=findPrefix(node.getChild(i),prefix);if(found!=null)return found;}return null;}
 Rect bounds(AccessibilityNodeInfo node,String name){require(node!=null,"Missing popup element: "+name);Rect r=new Rect();node.getBoundsInScreen(r);require(!r.isEmpty()&&node.isVisibleToUser(),"Invisible popup element: "+name);return r;}
 void assertPopupHierarchy()throws Exception{
  verifyPopupHierarchy(10);
  pass("approved black popup has64dp grouped adjustment targets, large clock, balanced action columns/fallback and readable full label at "+scale+"% text");
 }
 void verifyPopupHierarchy(int minutes)throws Exception{verifyPopupHierarchy(minutes,true);}
 void verifyPopupHierarchy(int minutes,boolean shortLabel)throws Exception{
  AccessibilityNodeInfo root=popup();Rect minus=bounds(find(root,"Decrease snooze by 5 minutes"),"minus"),plus=bounds(find(root,"Increase snooze by 5 minutes"),"plus");
  Rect duration=bounds(find(root,"Snooze duration: "+minutes+" minutes"),"duration"),time=bounds(findPrefix(root,"Alarm time:"),"clock");
  Rect snooze=bounds(find(root,"Snooze"),"Snooze"),stop=bounds(find(root,"Stop"),"Stop"),label=bounds(findPrefix(root,"Alarm label:"),"label");
  float density=c.getResources().getDisplayMetrics().density;
  require(Math.min(minus.width(),minus.height())>=63*density&&Math.min(plus.width(),plus.height())>=63*density,"Duration endzones need at least64dp touch targets");
  require(Math.min(snooze.height(),stop.height())>=47*density,"Snooze/Stop touch targets below48dp");
  require(minus.centerY()==plus.centerY()&&duration.centerX()>minus.centerX()&&duration.centerX()<plus.centerX(),"Duration group is not centered between matching controls");
  int controlsBottom=Math.max(duration.bottom,Math.max(minus.bottom,plus.bottom));
  boolean twoColumns=snooze.left>=plus.right+6*density&&stop.left>=plus.right+6*density;
  boolean bottomActions=snooze.top>=controlsBottom+6*density&&stop.top>=controlsBottom+6*density;
  require(Math.min(Math.min(minus.top,plus.top),snooze.top)>=time.bottom-2&&label.top>=Math.max(controlsBottom,Math.max(snooze.bottom,stop.bottom))-2,"Popup must place clock above controls/actions and full label below");
  require(twoColumns||bottomActions,"Actions neither form the approved right column nor an unclipped narrow fallback");
  if(twoColumns)require(stop.top>=snooze.bottom+6*density&&Math.abs(snooze.left-stop.left)<=2*density,"Right action column spacing/alignment is incorrect");
  else require(Math.abs(snooze.top-stop.top)<=2*density&&stop.left>=snooze.right+6*density,"Narrow action row spacing/alignment is incorrect");
  Bitmap display=getUiAutomation().takeScreenshot();require(display!=null,"No native display for popup fit/typography");
  try{
   Rect screen=new Rect(0,0,display.getWidth(),display.getHeight());
   Rect panel=bounds(root,"panel");float maxHeight=Float.parseFloat(scale)>110?340:300;
   if(shortLabel)require(panel.height()<=maxHeight*density,"Short-label popup is not compact: "+panel.height()/density+"dp exceeds "+maxHeight+"dp at "+scale+"% text");
   for(Rect element:new Rect[]{panel,time,minus,duration,plus,snooze,stop,label})require(screen.contains(element),"Popup element is cut off by actual display "+screen+": "+element);
   Rect clockInk=inkBounds(display,time),plusInk=inkBounds(display,centralGlyphBounds(plus,display)),minusInk=inkBounds(display,centralGlyphBounds(minus,display)),durationInk=inkBounds(display,duration);int durationNumberHeight=firstInkBandHeight(display,duration);
   require(clockInk.height()>durationNumberHeight*1.4&&clockInk.height()>plusInk.height()*1.1,"Clock is not the largest rendered typography: clock="+clockInk.height()+" duration="+durationNumberHeight+" plus="+plusInk.height());
   int labelLineHeight=firstInkBandHeight(display,label);Rect labelInk=inkBounds(display,label);
   require(clockInk.left<=time.left+12*density&&labelInk.left<=label.left+12*density&&labelLineHeight<clockInk.height()*.6,"Clock must stay left-aligned and visually dominate the readable footer label");
   int leftGap=durationInk.left-minusInk.right,rightGap=plusInk.left-durationInk.right;
   require(leftGap>=12*density&&rightGap>=12*density&&Math.abs(leftGap-rightGap)<=10*density,"Grouped glyphs lack balanced breathing room around the readout: "+leftGap+" / "+rightGap);
   require(Math.abs(minus.width()-plus.width())<=2*density&&Math.abs(durationInk.centerX()-(minus.left+plus.right)/2)<=5*density,"Grouped endzones/readout are not symmetric");
   int black=display.getPixel(Math.min(display.getWidth()-1,panel.left+Math.max(2,(int)(8*density))),panel.centerY());
   require(Math.max(Color.red(black),Math.max(Color.green(black),Color.blue(black)))<35&&Math.abs(Color.red(black)-Color.blue(black))<8,"Approved popup black surface was replaced by a tinted surface");
  }finally{display.recycle();}
 }
 Rect centralGlyphBounds(Rect control,Bitmap display){int radius=Math.max(5,(int)(16*c.getResources().getDisplayMetrics().density));return new Rect(Math.max(0,control.centerX()-radius),Math.max(0,control.centerY()-radius),Math.min(display.getWidth(),control.centerX()+radius),Math.min(display.getHeight(),control.centerY()+radius));}
 int dominantColor(Bitmap display,Rect area){Map<Integer,Integer> colors=new HashMap<>();for(int y=area.top;y<area.bottom;y++)for(int x=area.left;x<area.right;x++){int color=display.getPixel(x,y);colors.put(color,colors.getOrDefault(color,0)+1);}return Collections.max(colors.entrySet(),Map.Entry.comparingByValue()).getKey();}
 Rect inkBounds(Bitmap display,Rect area){int background=dominantColor(display,area);Rect ink=new Rect(area.right,area.bottom,area.left,area.top);for(int y=area.top;y<area.bottom;y++)for(int x=area.left;x<area.right;x++)if(contrast(background,display.getPixel(x,y))>=3){ink.left=Math.min(ink.left,x);ink.right=Math.max(ink.right,x+1);ink.top=Math.min(ink.top,y);ink.bottom=Math.max(ink.bottom,y+1);}require(!ink.isEmpty(),"No contrasting glyph pixels in "+area);return ink;}
 int firstInkBandHeight(Bitmap display,Rect area){
  int background=dominantColor(display,area),first=-1,last=-1,blank=0;int gap=Math.max(2,(int)(2*c.getResources().getDisplayMetrics().density));
  for(int y=area.top;y<area.bottom;y++){int ink=0;for(int x=area.left;x<area.right;x++)if(contrast(background,display.getPixel(x,y))>=3)ink++;
   if(ink>=2){if(first<0)first=y;last=y;blank=0;}else if(first>=0&&++blank>=gap)break;
  }
  require(first>=0&&last>=first,"No duration numeral ink in "+area);return last-first+1;
 }
 void foregroundPausedMusicFixture(String id)throws Exception{
  int before=plays();String previousEvents=player().get("events");
  // Back above intentionally finishes the music activity. Bring that rotatable
  // activity back without killing/restarting its existing paused MusicService.
  shell("am start -W -f 0x10000000 -n dev.orbit.focusprobe/.ProbeActivity --es mode music");
  waitFor(()->{for(AccessibilityWindowInfo window:getUiAutomation().getWindows()){AccessibilityNodeInfo root=window.getRoot();if(window.isFocused()&&root!=null&&"dev.orbit.focusprobe".equals(String.valueOf(root.getPackageName())))return true;}return false;},10000,"Rotatable music fixture is not the focused app beneath the popup");
  popupVisible();require(active(id)&&!playing()&&plays()==before&&player().get("events").startsWith(previousEvents),"Bringing music activity forward restarted playback or replaced its paused session");
  traceMedia("rotatable-fixture-foreground-with-existing-paused-session");
 }
 void rotationFailureDiagnostics(boolean landscape){
  StringBuilder out=new StringBuilder("Requested "+(landscape?"landscape":"portrait")+"\n");
  try{Display display=c.getSystemService(WindowManager.class).getDefaultDisplay();Point size=new Point();display.getRealSize(size);Rect panel=new Rect();AccessibilityNodeInfo popup=popup();if(popup!=null)popup.getBoundsInScreen(panel);
   out.append("displayRotation=").append(display.getRotation()).append(" realSize=").append(size).append(" resourceOrientation=").append(c.getResources().getConfiguration().orientation).append(" popupBounds=").append(panel).append('\n');
   for(AccessibilityWindowInfo window:getUiAutomation().getWindows()){AccessibilityNodeInfo root=window.getRoot();out.append("window focused=").append(window.isFocused()).append(" package=").append(root==null?null:root.getPackageName()).append('\n');}
  }catch(Throwable error){out.append("METRICS_ERROR ").append(error);}
  for(String command:new String[]{"dumpsys window displays","dumpsys activity activities"})try{out.append("\nCOMMAND ").append(command).append('\n').append(shell(command));}catch(Throwable error){out.append("\nDUMP_ERROR ").append(error);}
  try{File dir=new File(c.getFilesDir(),"session-feedback");dir.mkdirs();try(FileOutputStream file=new FileOutputStream(new File(dir,"rotation-failure-"+(landscape?"landscape":"portrait")+"-"+scale+".txt"))){file.write(out.toString().getBytes("UTF-8"));}}catch(Throwable ignored){}
  // Capture before the outer cleanup restores portrait, retaining the failed state.
  try{capture("rotation-timeout-"+(landscape?"landscape":"portrait"));}catch(Throwable ignored){}
  Bundle status=new Bundle();status.putString("stream","ROTATION_DIAGNOSTICS\n"+out);sendStatus(0,status);
 }
 void rotatePopup(String id,int rotation,boolean landscape,int minutes)throws Exception{rotatePopup(id,rotation,landscape,minutes,true,"persistent-popup");}
 void rotatePopup(String id,int rotation,boolean landscape,int minutes,boolean shortLabel,String capturePrefix)throws Exception{
  try{
  require(getUiAutomation().setRotation(rotation),"UiAutomation could not rotate the native display");
  final Rect[] last={null};final int[] stable={0};
  waitFor(()->{
   Point size=new Point();c.getSystemService(WindowManager.class).getDefaultDisplay().getRealSize(size);
   int wanted=landscape?android.content.res.Configuration.ORIENTATION_LANDSCAPE:android.content.res.Configuration.ORIENTATION_PORTRAIT;
   if((size.x>size.y)!=landscape||c.getResources().getConfiguration().orientation!=wanted){stable[0]=0;return false;}
   AccessibilityNodeInfo node=popup();if(node==null||find(node,"Snooze duration: "+minutes+" minutes")==null){stable[0]=0;return false;}
   Rect current=new Rect();node.getBoundsInScreen(current);if(current.isEmpty()||!new Rect(0,0,size.x,size.y).contains(current)){stable[0]=0;return false;}
   stable[0]=last[0]!=null&&last[0].equals(current)?stable[0]+1:0;last[0]=current;return stable[0]>=3;
  },15000,"Popup did not settle fully on the "+(landscape?"landscape":"portrait")+" native display");
  require(active(id)&&!playing(),"Rotation resolved the alarm or resumed media");verifyPopupHierarchy(minutes,shortLabel);assertAdjustmentGlyphs();capture(capturePrefix+(landscape?"-landscape":"-return-portrait"));
  }catch(Throwable error){rotationFailureDiagnostics(landscape);if(error instanceof Exception)throw(Exception)error;if(error instanceof Error)throw(Error)error;throw new RuntimeException(error);}
 }
 void assertRenderedFullLabel(String expected)throws Exception{
  AccessibilityNodeInfo label=findPrefix(popup(),"Alarm label:");Rect labelBounds=bounds(label,"full alarm label");
  require(label.getText()!=null&&expected.contentEquals(label.getText()),"Actual label TextView does not contain the complete saved label");
  String key=AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_KEY;
  require(label.getAvailableExtraData().contains(key),"TextView does not expose native character-layout feedback");
  Bundle request=new Bundle();request.putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_START_INDEX,0);request.putInt(AccessibilityNodeInfo.EXTRA_DATA_TEXT_CHARACTER_LOCATION_ARG_LENGTH,expected.length());
  require(label.refreshWithExtraData(key,request),"Could not obtain actual label character locations");
  Parcelable[] locations=label.getExtras().getParcelableArray(key);require(locations!=null&&locations.length==expected.length(),"Missing character locations for the complete alarm label");
  Bitmap display=getUiAutomation().takeScreenshot();require(display!=null,"No display for full-label glyph verification");
  try{int background=dominantColor(display,labelBounds),visible=0;Set<Integer> lines=new HashSet<>();RectF labelArea=new RectF(labelBounds);labelArea.inset(-1,-1);RectF screen=new RectF(0,0,display.getWidth(),display.getHeight());
   for(int i=0;i<expected.length();i++){if(Character.isWhitespace(expected.charAt(i)))continue;require(locations[i] instanceof RectF,"No rendered bounds for label character "+i);RectF box=(RectF)locations[i];
    require(Float.isFinite(box.left)&&Float.isFinite(box.top)&&Float.isFinite(box.right)&&Float.isFinite(box.bottom)&&box.width()>0&&box.height()>0&&labelArea.contains(box)&&screen.contains(box),"Label character "+i+" is ellipsized, clipped or off-screen: "+box);
    int ink=0;for(int y=Math.max(0,(int)Math.floor(box.top));y<Math.min(display.getHeight(),(int)Math.ceil(box.bottom));y++)for(int x=Math.max(0,(int)Math.floor(box.left));x<Math.min(display.getWidth(),(int)Math.ceil(box.right));x++)if(contrast(background,display.getPixel(x,y))>=3)ink++;
    require(ink>0,"Label character "+i+" has layout bounds but no visible contrasting glyph pixels");visible++;lines.add(Math.round(box.centerY()));
   }
   require(visible>60&&lines.size()>=2,"Long alarm label did not wrap into fully rendered readable lines");
  }finally{display.recycle();}
 }
 void longLabelCase()throws Exception{
  String fullLabel="Morning run by the river with friends, then breakfast before starting the work day";
  shell("appops set dev.orbit.clock SYSTEM_ALERT_WINDOW allow");startPlayer("music");String id=fixture(fullLabel,7000);delivered(id);paused();popupVisible();foregroundPausedMusicFixture(id);
  verifyPopupHierarchy(10,false);assertRenderedFullLabel(fullLabel);capture("long-label-portrait");
  try{
   rotatePopup(id,UiAutomation.ROTATION_FREEZE_90,true,10,false,"long-label");assertRenderedFullLabel(fullLabel);
   rotatePopup(id,UiAutomation.ROTATION_FREEZE_0,false,10,false,"long-label");assertRenderedFullLabel(fullLabel);
   clickPopup("Stop");waitFor(()->!row(id).getBoolean("enabled"),7000,"Long-label popup Stop did not commit");resumed(0);popupGone();clean(id);
   pass("complete long label wraps without ellipsis: native character locations and hardware glyph pixels verified in portrait/landscape/portrait; Stop resumes media at "+scale+"% text");
  }finally{getUiAutomation().setRotation(UiAutomation.ROTATION_FREEZE_0);}
 }
 void assertAdjustmentGlyphs()throws Exception{
  Bitmap display=getUiAutomation().takeScreenshot();require(display!=null,"No hardware display for glyph check");
  try{for(String label:new String[]{"Decrease snooze by 5 minutes","Increase snooze by 5 minutes"}){
   AccessibilityNodeInfo button=find(popup(),label);require(button!=null,"Missing adjustment button "+label);Rect bounds=new Rect();button.getBoundsInScreen(bounds);
   int radius=Math.max(5,(int)(16*c.getResources().getDisplayMetrics().density));
   Rect inner=new Rect(Math.max(0,bounds.centerX()-radius),Math.max(0,bounds.centerY()-radius),Math.min(display.getWidth(),bounds.centerX()+radius),Math.min(display.getHeight(),bounds.centerY()+radius));
   Map<Integer,Integer> colors=new HashMap<>();for(int y=inner.top;y<inner.bottom;y++)for(int x=inner.left;x<inner.right;x++){int color=display.getPixel(x,y);colors.put(color,colors.getOrDefault(color,0)+1);}
   int background=Collections.max(colors.entrySet(),Map.Entry.comparingByValue()).getKey();
   int ink=0;Rect inkBounds=new Rect(inner.right,inner.bottom,inner.left,inner.top);
   for(int y=inner.top;y<inner.bottom;y++)for(int x=inner.left;x<inner.right;x++)if(contrast(background,display.getPixel(x,y))>=3){ink++;inkBounds.left=Math.min(inkBounds.left,x);inkBounds.right=Math.max(inkBounds.right,x+1);inkBounds.top=Math.min(inkBounds.top,y);inkBounds.bottom=Math.max(inkBounds.bottom,y+1);}
   require(ink>=12&&colors.get(background)>=40&&inkBounds.width()>=5&&inkBounds.height()>=2,"Adjustment glyph lacks visible3:1 contrast in actual display: "+label+" pixels="+ink+" bounds="+inkBounds);
  }}finally{display.recycle();}
  pass("both adjustment glyphs are visible in actual hardware-display pixels at "+scale+"% text");
 }
 void expectMinutes(int minutes)throws Exception{waitFor(()->find(popup(),"Snooze duration: "+minutes+" minutes")!=null,6000,"Expected occurrence duration "+minutes+" minutes");}
 void endZoneTap(String description,int corner)throws Exception{
  Rect target=bounds(find(popup(),description),description);float inset=3*c.getResources().getDisplayMetrics().density;
  tap((corner&1)==0?target.left+inset:target.right-inset,(corner&2)==0?target.top+inset:target.bottom-inset);
 }
 void groupedTargetCases(String id)throws Exception{
  ActivityMonitor noOpen=addMonitor("dev.orbit.clock.core.RingActivity",null,false);
  try{
   for(int corner=0;corner<4;corner++){endZoneTap("Increase snooze by 5 minutes",corner);expectMinutes(20);endZoneTap("Decrease snooze by 5 minutes",corner);expectMinutes(15);}
   clickPopup("Decrease snooze by 5 minutes");expectMinutes(10);clickPopup("Decrease snooze by 5 minutes");expectMinutes(5);
   require(!find(popup(),"Decrease snooze by 5 minutes").isEnabled(),"Minimum endpoint is not exposed as disabled");
   for(int corner=0;corner<4;corner++)endZoneTap("Decrease snooze by 5 minutes",corner);expectMinutes(5);
   for(int minutes=10;minutes<=60;minutes+=5){clickPopup("Increase snooze by 5 minutes");expectMinutes(minutes);}
   require(!find(popup(),"Increase snooze by 5 minutes").isEnabled(),"Maximum endpoint is not exposed as disabled");
   for(int corner=0;corner<4;corner++)endZoneTap("Increase snooze by 5 minutes",corner);expectMinutes(60);
   for(int minutes=55;minutes>=15;minutes-=5){clickPopup("Decrease snooze by 5 minutes");expectMinutes(minutes);}
   require(noOpen.getHits()==0&&active(id)&&!playing(),"An active/disabled endzone leaked to card navigation or resolved the alarm");
   require(row(id).getInt("snoozeMinutes")==10,"Endzone adjustments changed the saved default");
   pass("all four corners of64dp endzones adjust, disabled5/60 endpoints consume touches, and neither opens/snoozes/stops the active alarm");
  }finally{removeMonitor(noOpen);}
 }
 void narrowPopupCase(String id)throws Exception{
  String sizeState=shell("wm size");java.util.regex.Matcher original=java.util.regex.Pattern.compile("Override size: (\\d+x\\d+)").matcher(sizeState);String restore=original.find()?original.group(1):"reset";
  Point before=new Point();c.getSystemService(WindowManager.class).getDefaultDisplay().getRealSize(before);
  try{
   shell("wm size 560x1244");waitFor(()->{Point size=new Point();c.getSystemService(WindowManager.class).getDefaultDisplay().getRealSize(size);return size.x==560&&size.y==1244&&popup()!=null;},15000,"Narrow portrait display did not apply");
   expectMinutes(15);waitFor(()->{AccessibilityNodeInfo root=popup();if(root==null)return false;AccessibilityNodeInfo p=find(root,"Increase snooze by 5 minutes"),s=find(root,"Snooze");if(p==null||s==null)return false;Rect plus=new Rect(),snooze=new Rect();p.getBoundsInScreen(plus);s.getBoundsInScreen(snooze);return snooze.top>=plus.bottom+5*c.getResources().getDisplayMetrics().density;},15000,"Narrow fallback controls did not finish layout");verifyPopupHierarchy(15);assertAdjustmentGlyphs();
   Rect plus=bounds(find(popup(),"Increase snooze by 5 minutes"),"narrow plus"),snooze=bounds(find(popup(),"Snooze"),"narrow Snooze");
   require(snooze.top>=plus.bottom,"Narrow portrait did not use the readable bottom action fallback");
   require(active(id)&&!playing(),"Narrow fallback resolved alarm or resumed media");capture("approved-popup-narrow");
   pass("actual320dp portrait fallback retains64dp targets, selected duration, readable actions and active paused-media state at "+scale+"% text");
  }catch(Throwable error){rotationFailureDiagnostics(false);if(error instanceof Exception)throw(Exception)error;if(error instanceof Error)throw(Error)error;throw new RuntimeException(error);
  }finally{shell("wm size "+restore);waitFor(()->{Point size=new Point();c.getSystemService(WindowManager.class).getDefaultDisplay().getRealSize(size);AccessibilityNodeInfo root=popup();if(!size.equals(before)||root==null)return false;AccessibilityNodeInfo p=find(root,"Increase snooze by 5 minutes"),s=find(root,"Snooze");if(p==null||s==null)return false;Rect plus=new Rect(),snooze=new Rect();p.getBoundsInScreen(plus);s.getBoundsInScreen(snooze);return snooze.left>=plus.right+5*c.getResources().getDisplayMetrics().density;},15000,"Original two-column display layout did not restore");expectMinutes(15);}
 }
 void scrollingLabelCase()throws Exception{
  StringBuilder full=new StringBuilder();for(int i=0;i<45;i++)full.append("Morning run by the river, breakfast with friends and a relaxed start to the day. ");
  shell("appops set dev.orbit.clock SYSTEM_ALERT_WINDOW allow");startPlayer("music");String id=fixture(full.toString(),7000);delivered(id);paused();popupVisible();foregroundPausedMusicFixture(id);
  ActivityMonitor noOpen=addMonitor("dev.orbit.clock.core.RingActivity",null,false);
  try{
   AccessibilityNodeInfo label=findPrefix(popup(),"Alarm label:");Rect visible=bounds(label,"scrollable label"),window=new Rect();
   for(AccessibilityWindowInfo candidate:getUiAutomation().getWindows())if(find(candidate.getRoot(),"Persistent alarm controls")!=null){candidate.getBoundsInScreen(window);break;}
   require(!window.isEmpty()&&visible.intersect(window)&&visible.height()>50*c.getResources().getDisplayMetrics().density,"Long label lacks a visible drag area");
   float density=c.getResources().getDisplayMetrics().density,x=visible.centerX(),start=visible.bottom-8*density,end=Math.max(visible.top+8*density,start-100*density);
   AccessibilityEvent scrolled=getUiAutomation().executeAndWaitForEvent(()->{try{swipe(x,start,x,end);}catch(Exception error){throw new RuntimeException(error);}},event->event.getEventType()==AccessibilityEvent.TYPE_VIEW_SCROLLED&&c.getPackageName().equals(String.valueOf(event.getPackageName()))&&event.getScrollY()>0,7000);
   require(scrolled!=null&&noOpen.getHits()==0&&active(id)&&!playing(),"Dragging the long label opened/resolved the alarm instead of scrolling");scrolled.recycle();capture("approved-popup-scrolled-label");
   action(id,"stop");resumed(0);popupGone();clean(id);pass("real long-label drag scrolls natively without opening the card or resolving the active alarm");
  }finally{removeMonitor(noOpen);}
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
  waitFor(()->!nativeMediaActive(),5000,"A media playback configuration is still active");traceMedia("pre-paused-native-baseline");before=plays();a=fixture("Already paused",7000);delivered(a);action(a,"stop");SystemClock.sleep(1200);
  require(!playing() && plays()==before,"Alarm started previously paused media");clean(a);pass("already-paused media is never started");
 }
 void popupCases()throws Exception{
  shell("appops set dev.orbit.clock SYSTEM_ALERT_WINDOW allow");startPlayer("music");String id=fixture("Persistent alarm test",7000);delivered(id);paused();popupVisible();SystemClock.sleep(200);capture("persistent-popup");assertAdjustmentGlyphs();assertPopupHierarchy();
  Rect bounds=new Rect();popup().getBoundsInScreen(bounds);float density=c.getResources().getDisplayMetrics().density;
  swipe(bounds.left+24*density,bounds.top+20*density,bounds.right-24*density,bounds.top+20*density);popupVisible();require(active(id) && !playing(),"Swipe dismissed popup or resumed playback");
  shell("input keyevent BACK");popupVisible();require(active(id),"Back outside alarm dismissed popup");
  pass("granted persistent popup survives horizontal swipe and Back without resolving the alarm");
  foregroundPausedMusicFixture(id);
  // Popup selection is shared with notification and full-screen controls.
  clickPopup("Increase snooze by 5 minutes");waitFor(()->find(popup(),"Snooze duration: 15 minutes")!=null,6000,"Popup duration did not update");
  require(active(id)&&!playing(),"Plus must adjust only, without snoozing or resuming media");
  clickPopup("Decrease snooze by 5 minutes");waitFor(()->find(popup(),"Snooze duration: 10 minutes")!=null,6000,"Popup minus did not update");require(active(id)&&!playing(),"Minus must adjust only, without snoozing or resuming media");
  clickPopup("Increase snooze by 5 minutes");waitFor(()->find(popup(),"Snooze duration: 15 minutes")!=null,6000,"Popup plus did not restore selection");
  try{
   rotatePopup(id,UiAutomation.ROTATION_FREEZE_90,true,15);
   clickPopup("Decrease snooze by 5 minutes");waitFor(()->find(popup(),"Snooze duration: 10 minutes")!=null,6000,"Landscape minus did not adjust selection");require(active(id)&&!playing(),"Landscape minus snoozed instead of adjusting");
   clickPopup("Increase snooze by 5 minutes");waitFor(()->find(popup(),"Snooze duration: 15 minutes")!=null,6000,"Landscape plus did not adjust selection");require(active(id)&&!playing(),"Landscape plus snoozed instead of adjusting");
   rotatePopup(id,UiAutomation.ROTATION_FREEZE_0,false,15);
   pass("real portrait/landscape/portrait rotation preserves selected duration, clock-first layout, visible targets and adjustment-only behavior at "+scale+"% text");
  }finally{getUiAutomation().setRotation(UiAutomation.ROTATION_FREEZE_0);}
  groupedTargetCases(id);narrowPopupCase(id);
  clickPopup("Snooze");final String snoozeId=id;waitFor(()->!active(snoozeId),7000,"Persistent popup Snooze did not act");resumed(0);popupGone();
  require(row(id).getInt("snoozeMinutes")==10,"Popup adjusted saved default");clean(id);pass("persistent popup plus/Snooze resumes music with occurrence-only duration");
  startPlayer("music");id=fixture("Permission revoked",7000);delivered(id);paused();popupVisible();shell("appops set dev.orbit.clock SYSTEM_ALERT_WINDOW deny");popupGone();
  require(active(id) && !playing(),"Permission revocation silenced or resolved alarm");action(id,"stop");resumed(0);clean(id);pass("permission revocation safely falls back to notification; Stop resumes media");
  shell("appops set dev.orbit.clock SYSTEM_ALERT_WINDOW allow");startPlayer("music");id=fixture("Popup Stop",7000);delivered(id);paused();popupVisible();traceMedia("before-popup-stop");clickPopup("Stop");
  final String stopId=id;waitFor(()->!row(stopId).getBoolean("enabled"),7000,"Popup Stop did not stop");resumed(0);popupGone();clean(id);pass("persistent popup Stop commits and resumes previous music");
 }
 boolean hasProductionSlider(View view){if(view.getClass().getName().equals("dev.orbit.clock.core.AlarmSlideControl"))return true;if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)if(hasProductionSlider(((ViewGroup)view).getChildAt(i)))return true;return false;}
 int fullScreenMinutes()throws Exception{return main(()->{Field field=ringing.getClass().getDeclaredField("selectedMinutes");field.setAccessible(true);return field.getInt(ringing);});}
 void requireOpenAction(AccessibilityNodeInfo node,String name){require(node!=null&&node.isClickable(),"No clickable non-action region: "+name);boolean found=false;for(AccessibilityNodeInfo.AccessibilityAction action:node.getActionList())if(action.getId()==AccessibilityNodeInfo.ACTION_CLICK&&"Open full-screen alarm".contentEquals(action.getLabel()==null?"":action.getLabel()))found=true;require(found,"Missing accessible Open full-screen action on "+name);}
 void openPopupRegion(String id,String region)throws Exception{
  AccessibilityNodeInfo root=popup(),target=region.equals("card")?root:region.equals("time")?findPrefix(root,"Alarm time:"):region.equals("label")?findPrefix(root,"Alarm label:"):find(root,"Snooze duration: 15 minutes");
  requireOpenAction(target,region);Rect rect=bounds(target,region);float x=rect.centerX(),y=rect.centerY();
  if(region.equals("card")){float density=c.getResources().getDisplayMetrics().density;x=rect.left+8*density;y=rect.top+32*density;}
  if(region.equals("time")){Bitmap display=getUiAutomation().takeScreenshot();require(display!=null,"No display for actual clock tap");try{Rect ink=inkBounds(display,rect);x=ink.centerX();y=ink.centerY();}finally{display.recycle();}}
  long at=occurrence(id);ActivityMonitor monitor=addMonitor("dev.orbit.clock.core.RingActivity",null,false);
  try{tap(x,y);ringing=monitor.waitForActivityWithTimeout(15000);require(ringing!=null,"Tapping "+region+" did not open actual RingActivity");}finally{removeMonitor(monitor);}
  waitFor(()->main(()->ringing.hasWindowFocus()&&!ringing.isFinishing()),10000,"Opened RingActivity did not gain focus");popupGone();
  require(main(()->ringing.getClass().getName().equals("dev.orbit.clock.core.RingActivity")&&id.equals(ringing.getIntent().getStringExtra("id"))&&ringing.getIntent().getLongExtra("at",0)==at&&hasProductionSlider(ringing.getWindow().getDecorView())),"Popup opened a substitute screen or the wrong alarm occurrence");
  require(fullScreenMinutes()==15&&active(id)&&!playing(),"Popup opening changed duration, resolved alarm or resumed media");
  capture("popup-open-"+region);pass("native "+region+" tap opens actual locked-screen RingActivity/slider with exact occurrence,15min selection and paused media");
 }
 void navigationCases()throws Exception{
  shell("appops set dev.orbit.clock SYSTEM_ALERT_WINDOW allow");startPlayer("music");String id=fixture("Open from popup",7000);delivered(id);paused();popupVisible();clickPopup("Increase snooze by 5 minutes");expectMinutes(15);
  String[] regions={"card","time","label","duration"};long previousOpen=0;
  for(int i=0;i<regions.length;i++){
   long wait=previousOpen+900-SystemClock.uptimeMillis();if(wait>0)SystemClock.sleep(wait);
   openPopupRegion(id,regions[i]);previousOpen=SystemClock.uptimeMillis();
   sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);SystemClock.sleep(250);require(main(()->ringing.hasWindowFocus()&&!ringing.isFinishing())&&active(id),"Back dismissed the opened ringing screen");
   if(i<regions.length-1){shell("input keyevent HOME");popupVisible();require(active(id)&&!playing(),"Home resolved alarm or resumed media");foregroundPausedMusicFixture(id);expectMinutes(15);}
  }
  sendKeyDownUpSync(KeyEvent.KEYCODE_VOLUME_UP);waitFor(()->!active(id),7000,"Actual ringing-screen Snooze shortcut did not resolve occurrence");resumed(0);popupGone();
  require(row(id).getInt("snoozeMinutes")==10&&row(id).getLong("snoozeUntil")>System.currentTimeMillis()+14*60000,"Opening/Snooze lost selected15min or changed saved default");
  AlarmManager.AlarmClockInfo next=c.getSystemService(AlarmManager.class).getNextAlarmClock();require(next!=null&&next.getTriggerTime()==row(id).getLong("snoozeUntil"),"Opened ringing-screen Snooze was not actually scheduled");clean(id);
  pass("all non-action regions preserve Back blocking/Home handoff; actual full-screen Snooze uses selected15min and resumes interrupted media");
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
  require(scope.equals("all") || scope.equals("popup") || scope.equals("screen") || scope.equals("navigation"),"Unknown test scope "+scope);
  Bundle scopeStep=new Bundle();scopeStep.putString("stream","STEP scope="+scope+" api="+Build.VERSION.SDK_INT+" text="+scale+"%\n");sendStatus(0,scopeStep);
  if(scope.equals("all") || scope.equals("popup")){mediaCases();popupCases();longLabelCase();scrollingLabelCase();}
  if(scope.equals("all") || scope.equals("navigation"))navigationCases();
  if(scope.equals("all") || scope.equals("screen"))screenCase();
  shell("am force-stop dev.orbit.focusprobe");
  result.putString("stream","RINGING_SESSION_RESULT_PASS scope="+scope+" api="+Build.VERSION.SDK_INT+" at "+scale+"% text\n");finish(Activity.RESULT_OK,result);
 }catch(Throwable error){dumpFailureDiagnostics();try{capture("failure");}catch(Throwable ignored){}result.putString("stream","RINGING_SESSION_FAILURE scope="+scope+" api="+Build.VERSION.SDK_INT+": "+android.util.Log.getStackTraceString(error));finish(Activity.RESULT_CANCELED,result);}}
}
