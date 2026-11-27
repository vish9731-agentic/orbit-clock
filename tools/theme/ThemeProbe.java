package dev.orbit.clock.themeprobe;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.*;
import android.view.*;
import android.view.inspector.WindowInspector;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Native mounted-screen verification, signed separately. Never ships in Orbit. */
public class ThemeProbe extends Instrumentation {
 Context c; Activity current; String module,night,scale; File evidence; int checks;
 interface Check { boolean ok() throws Exception; }
 <T>T main(Callable<T> action)throws Exception {
  FutureTask<T> f=new FutureTask<>(action);
  if(!new Handler(Looper.getMainLooper()).post(f))throw new AssertionError("Main queue rejected action");
  return f.get(15,TimeUnit.SECONDS);
 }
 void require(boolean value,String message){if(!value)throw new AssertionError(message);}
 void waitFor(Check action,String message)throws Exception {
  long deadline=SystemClock.uptimeMillis()+15000;
  while(SystemClock.uptimeMillis()<deadline){if(action.ok())return;SystemClock.sleep(100);}
  throw new AssertionError(message);
 }
 void pass(String message){checks++;Bundle b=new Bundle();b.putString("stream","PASS "+message+"\n");sendStatus(0,b);}
 void views(View root,List<View> out){out.add(root);if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++)views(((ViewGroup)root).getChildAt(i),out);}
 List<View> all()throws Exception{return main(()->{List<View> out=new ArrayList<>();for(View root:WindowInspector.getGlobalWindowViews())if(root.isShown())views(root,out);return out;});}
 View find(String value)throws Exception {
  return main(()->{List<View> out=new ArrayList<>();for(View root:WindowInspector.getGlobalWindowViews())if(root.isShown())views(root,out);
   for(int i=out.size()-1;i>=0;i--){View v=out.get(i);if(!v.isShown())continue;
    if(value.equals(String.valueOf(v.getContentDescription())) || v instanceof TextView&&value.equals(((TextView)v).getText().toString()))return v;
   }return null;});
 }
 void show(View view)throws Exception {
  require(view!=null,"Requested control absent");
  main(()->{view.requestRectangleOnScreen(new Rect(0,0,view.getWidth(),view.getHeight()),false);return null;});
  SystemClock.sleep(450);
  waitFor(()->main(()->{Rect r=new Rect();return view.isShown()&&view.getGlobalVisibleRect(r)&&r.height()>0&&r.width()>0;}),"Requested control not reachable");
 }
 void click(String value)throws Exception {
  View view=find(value);require(view!=null,"No native control: "+value);show(view);
  main(()->{require(view.isEnabled()&&view.performClick(),"Control did not handle click: "+value);return null;});
  SystemClock.sleep(500);
 }
 void back()throws Exception {sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);SystemClock.sleep(400);}
 void capture(String name)throws Exception {
  Bitmap b=getUiAutomation().takeScreenshot();require(b!=null,"No native display capture");
  try(FileOutputStream out=new FileOutputStream(new File(evidence,name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}finally{b.recycle();}
 }
 double linear(int v){double s=v/255.0;return s<=.04045?s/12.92:Math.pow((s+.055)/1.055,2.4);}
 double luminance(int color){return .2126*linear(Color.red(color))+.7152*linear(Color.green(color))+.0722*linear(Color.blue(color));}
 double contrast(int a,int b){double x=luminance(a),y=luminance(b);return (Math.max(x,y)+.05)/(Math.min(x,y)+.05);}
 boolean amber(int color){return Color.red(color)>Color.blue(color)+20&&Color.green(color)>Color.blue(color)+12&&Color.red(color)>Color.green(color);}
 int dominant(Bitmap bitmap,Rect area){Map<Integer,Integer> colors=new HashMap<>();for(int y=Math.max(0,area.top);y<Math.min(bitmap.getHeight(),area.bottom);y+=2)for(int x=Math.max(0,area.left);x<Math.min(bitmap.getWidth(),area.right);x+=2){int color=bitmap.getPixel(x,y);colors.put(color,colors.getOrDefault(color,0)+1);}require(!colors.isEmpty(),"No pixels in visible region");return Collections.max(colors.entrySet(),Map.Entry.comparingByValue()).getKey();}
 void screen(String name,boolean requireAccent)throws Exception {
  SystemClock.sleep(550);
  Bitmap b=getUiAutomation().takeScreenshot();require(b!=null,"Missing actual display");
  try{
   int dark=0,total=0,accents=0;Rect content=new Rect();main(()->{current.findViewById(android.R.id.content).getGlobalVisibleRect(content);return null;});
   require(!content.isEmpty(),"No mounted content area");
   for(int y=Math.max(0,content.top);y<Math.min(b.getHeight(),content.bottom);y+=3)for(int x=Math.max(0,content.left);x<Math.min(b.getWidth(),content.right);x+=3){int p=b.getPixel(x,y);total++;if(Math.max(Color.red(p),Math.max(Color.green(p),Color.blue(p)))<80)dark++;if(amber(p))accents++;}
   require(total>100&&dark>total*.50,"Black/charcoal screen lost under system "+night+": "+name+" dark="+dark+"/"+total);
   if(requireAccent)require(accents>12,"Actual amber accents missing: "+name);
   final Bitmap display=b;
   int readable=main(()->{int count=0;List<View> mounted=new ArrayList<>();views(current.getWindow().getDecorView(),mounted);
    for(View v:mounted)if(v instanceof TextView&&v.isShown()&&v.getAlpha()>.95f){TextView t=(TextView)v;Rect r=new Rect();if(t.getText().length()==0||!t.getGlobalVisibleRect(r)||r.width()<8||r.height()<8)continue;
     // A partially scrolled row is intentionally clipped by its viewport.
     if(r.height()<t.getHeight()-2)continue;
     int bg=dominant(display,r),fg=t.getCurrentTextColor();double ratio=contrast(fg,bg);
     if(t.isEnabled())require(ratio>=(t.getTextSize()/c.getResources().getDisplayMetrics().scaledDensity>=20?2.8:3.8),"Low native text contrast "+name+": "+t.getText()+" ratio="+ratio);
     if(t.getLayout()!=null){int needed=t.getLayout().getLineBottom(t.getLayout().getLineCount()-1)+t.getCompoundPaddingTop()+t.getCompoundPaddingBottom();
      if(t.getMaxLines()>1&&t.getEllipsize()==null)require(needed<=t.getHeight()+2,"Text content vertically clipped: "+t.getText());}
     count++;
    }return count;});
   require(readable>=2,"Insufficient visible native text feedback: "+name);
   try(FileOutputStream out=new FileOutputStream(new File(evidence,name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}
  }finally{b.recycle();}
  pass(name+": actual dark display, amber accents and readable mounted text under system "+night);
 }
 int hatchCount(View root)throws Exception {
  return main(()->{List<View> mounted=new ArrayList<>();views(root,mounted);int found=0;
   for(View v:mounted){Drawable d=v.getBackground();if(d==null||!d.getClass().getName().equals("dev.orbit.clock.core.OrbitHatchDrawable")||v.getWidth()<32||v.getHeight()<32)continue;
    int w=v.getWidth(),h=v.getHeight();Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);Rect old=new Rect(d.getBounds());
    try{d.setBounds(0,0,w,h);d.draw(new Canvas(b));int base=b.getPixel(w/3,h/2),left=0,right=0;
     for(int y=h/5;y<h*4/5;y++)for(int x=w/8;x<w*7/8;x++){int p=b.getPixel(x,y);if(Color.alpha(p)>0&&(Math.abs(Color.red(p)-Color.red(base))+Math.abs(Color.green(p)-Color.green(base))+Math.abs(Color.blue(p)-Color.blue(base)))>=4){if(x<w/2)left++;else right++;}}
     require(right>5,"Mounted hatch has no visible raster detail");require(right>left*1.2,"Hatch does not fade toward the right edge");found++;
    }finally{d.setBounds(old);b.recycle();}
   }return found;});
 }
 void seedAlarm()throws Exception {
  ClassLoader l=c.getClassLoader();Class<?> store=Class.forName("dev.orbit.clock.core.Store",true,l),alarm=Class.forName("dev.orbit.clock.core.Alarm",true,l);Object companion=store.getField("Companion").get(null);
  ZonedDateTime at=ZonedDateTime.now().plusHours(3);
  JSONObject row=new JSONObject().put("id","theme-probe-alarm").put("hour",at.getHour()).put("minute",at.getMinute()).put("label","Morning run").put("timer",false).put("days",new JSONArray(Arrays.asList(1,2,3,4,5,6,7))).put("enabled",true).put("sound",false).put("vibrate",false).put("snoozeMinutes",10).put("snoozeUntil",0).put("skipThrough",0).put("deleted",false).put("revision",System.currentTimeMillis()).put("writer","theme-probe");
  Object value=companion.getClass().getMethod("decode",JSONObject.class).invoke(companion,row);
  main(()->{store.getMethod("save",alarm).invoke(store.getConstructor(Context.class).newInstance(c),value);return null;});
 }
 Activity launch(String tab)throws Exception {
  if(current!=null)main(()->{current.finish();return null;});
  String cls=module.equals("wear")?"dev.orbit.clock.wear.MainActivity":"dev.orbit.clock.phone.MainActivity";
  current=startActivitySync(new Intent().setClassName(c.getPackageName(),cls).putExtra("orbit_tab",tab).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  waitFor(()->main(()->current.hasWindowFocus()&&!current.isFinishing()),"Main tool failed to gain focus: "+tab);SystemClock.sleep(350);return current;
 }
 void editor()throws Exception {
  View alarmCard=main(()->current.getWindow().getDecorView().findViewWithTag("item:alarm:theme-probe-alarm"));
  require(alarmCard!=null,"Actual seeded alarm tile missing");show(alarmCard);
  main(()->{require(alarmCard.isClickable()&&alarmCard.performClick(),"Alarm tile did not open its native editor");return null;});
  waitFor(()->find("Edit alarm")!=null,"Native editor missing");capture("alarm-editor");
  View time=find("Set alarm time");require(time!=null,"No time picker control");click("Set alarm time");
  String expectedTitle=module.equals("wear")?"Set time":"Set alarm time";
  waitFor(()->main(()->{List<View> nativeViews=new ArrayList<>();for(View root:WindowInspector.getGlobalWindowViews())if(root.isShown())views(root,nativeViews);for(View v:nativeViews)if(v instanceof TextView&&v.isShown()&&expectedTitle.equals(((TextView)v).getText().toString()))return true;return false;}),"Native time picker title did not appear");capture("alarm-time-picker");
  // Inspect the actual dialog's Material color roles in the mounted window.
  List<View> roots=main(()->WindowInspector.getGlobalWindowViews());View dialog=roots.get(roots.size()-1);
  int attr=c.getResources().getIdentifier("colorSurface","attr",c.getPackageName());
  if(attr!=0){android.util.TypedValue value=new android.util.TypedValue();require(dialog.getContext().getTheme().resolveAttribute(attr,value,true),"Dialog surface role missing");require(luminance(value.data)<.08,"Dialog has a bright surface under system "+night);}
  back();back();waitFor(()->main(()->current.hasWindowFocus()),"Editor did not close cleanly");pass("real alarm editor and native time picker open in dark theme");
 }
 void focus()throws Exception {
  launch("Timer");ActivityMonitor monitor=addMonitor("dev.orbit.clock.core.FocusActivity",null,false);
  try{
   click("Focus mode");Activity found=monitor.waitForActivityWithTimeout(15000);
   if(found==null){for(View r:main(()->WindowInspector.getGlobalWindowViews())){Context context=r.getContext();while(context instanceof ContextWrapper){if(context instanceof Activity&&context.getClass().getName().equals("dev.orbit.clock.core.FocusActivity")){found=(Activity)context;break;}context=((ContextWrapper)context).getBaseContext();}if(found!=null)break;}}
   require(found!=null,"Focus mode did not open its actual activity");current=found;
  }finally{removeMonitor(monitor);}
  screen("focus",true);click("Durations");waitFor(()->find("Focus durations")!=null,"Focus durations dialog absent");capture("focus-durations");back();
  pass("actual Focus screen and durations dialog use the shared theme");
 }
 Activity visibleActivity(String className)throws Exception {
  return main(()->{for(View root:WindowInspector.getGlobalWindowViews())if(root.isShown()){
   Context context=root.getContext();Set<Context> seen=Collections.newSetFromMap(new IdentityHashMap<Context,Boolean>());
   while(context instanceof ContextWrapper&&seen.add(context)){
    if(context instanceof Activity&&context.getClass().getName().equals(className)){
     Activity candidate=(Activity)context;if(candidate.hasWindowFocus()&&!candidate.isFinishing()&&!candidate.isDestroyed())return candidate;
    }
    context=((ContextWrapper)context).getBaseContext();
   }
  }return null;});
 }
 void phoneRotation()throws Exception {
  if(module.equals("wear"))return;
  launch("Alarms");getUiAutomation().setRotation(UiAutomation.ROTATION_FREEZE_90);
  try{
   waitFor(()->{Activity recreated=visibleActivity("dev.orbit.clock.phone.MainActivity");if(recreated==null)return false;
    if(recreated.getResources().getConfiguration().orientation!=android.content.res.Configuration.ORIENTATION_LANDSCAPE)return false;
    current=recreated;return true;},"Visible native landscape Activity did not recreate");
   screen("alarms-landscape",true);
  }finally{
   getUiAutomation().setRotation(UiAutomation.ROTATION_FREEZE_0);
   waitFor(()->{Activity recreated=visibleActivity("dev.orbit.clock.phone.MainActivity");if(recreated==null)return false;
    if(recreated.getResources().getConfiguration().orientation!=android.content.res.Configuration.ORIENTATION_PORTRAIT)return false;
    current=recreated;return true;},"Visible native portrait Activity did not restore");
  }
 }
 @Override public void onCreate(Bundle arguments){super.onCreate(arguments);module=arguments.getString("module","phone");night=arguments.getString("night","no");scale=arguments.getString("scale","1.0");start();}
 @Override public void onStart(){Bundle result=new Bundle();try{
  c=getTargetContext();evidence=new File(c.getFilesDir(),"theme-feedback/"+module+"-"+scale+"-"+night);require(evidence.mkdirs()||evidence.isDirectory(),"No feedback folder");
  require(Math.abs(c.getResources().getConfiguration().fontScale-Float.parseFloat(scale))<.04,"Wrong actual font scale");
  require(c.getSystemService(UiModeManager.class).getNightMode()==(night.equals("yes")?UiModeManager.MODE_NIGHT_YES:UiModeManager.MODE_NIGHT_NO),"System night mode did not apply");
  require(c.getPackageManager().hasSystemFeature("android.hardware.type.watch")==module.equals("wear"),"Wrong device variant");
  seedAlarm();int textured=0;
  for(String tab:new String[]{"Alarms","Clock","Timer","Stopwatch","Bedtime"}){launch(tab);screen(tab.toLowerCase(Locale.ROOT),true);textured+=hatchCount(current.getWindow().getDecorView());if(tab.equals("Alarms"))editor();}
  require(textured>=3,"Hatch accents absent across the mounted tools");pass("actual mounted hatch drawables render bounded edge-faded diagonal detail across tools");
  focus();phoneRotation();
  result.putString("stream","THEME_RESULT_PASS module="+module+" scale="+scale+" systemNight="+night+" checks="+checks+"\n");finish(Activity.RESULT_OK,result);
 }catch(Throwable error){try{capture("failure");}catch(Throwable ignored){}result.putString("stream","THEME_FAILURE module="+module+": "+android.util.Log.getStackTraceString(error));finish(Activity.RESULT_CANCELED,result);}
 finally{try{getUiAutomation().setRotation(UiAutomation.ROTATION_UNFREEZE);}catch(Throwable ignored){}}}
}
