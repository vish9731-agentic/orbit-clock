package dev.orbit.clock.rubberprobe;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Runs only in a same-key test APK. No alarm records or production test entry points. */
public class RubberProbe extends Instrumentation {
 Activity activity; FrameLayout stage; View rail,thumb; Class<?> sliderClass;
 Context target; Object unit; Class<?> function;
 AtomicInteger snoozes=new AtomicInteger(),stops=new AtomicInteger();
 long down; float origin; float density;
 interface Check { boolean ok() throws Exception; }
 <T> T main(Callable<T> action) throws Exception {
  FutureTask<T> task=new FutureTask<>(action);runOnMainSync(task);return task.get();
 }
 void require(boolean good,String label){if(!good)throw new AssertionError(label);}
 void waitFor(Check check,long ms,String label) throws Exception {
  long end=SystemClock.uptimeMillis()+ms;
  while(SystemClock.uptimeMillis()<end){if(check.ok())return;SystemClock.sleep(15);}
  throw new AssertionError(label);
 }
 int dp(float x){return Math.round(x*density);}
 void report(String text){Bundle b=new Bundle();b.putString("stream",text+"\n");sendStatus(0,b);}
 Object callback(AtomicInteger count) {
  return java.lang.reflect.Proxy.newProxyInstance(function.getClassLoader(),new Class<?>[]{function},(proxy,method,args)->{
   if(method.getName().equals("invoke")){count.incrementAndGet();return unit;}
   if(method.getName().equals("hashCode"))return System.identityHashCode(proxy);
   if(method.getName().equals("equals"))return proxy==args[0];
   if(method.getName().equals("toString"))return "Test-only alarm action";
   return unit;
  });
 }
 View getThumb(View view) throws Exception {
  Field f=sliderClass.getDeclaredField("thumb");f.setAccessible(true);return (View)f.get(view);
 }
 float travel() throws Exception {
  return main(()->{Method m=sliderClass.getDeclaredMethod("travel");m.setAccessible(true);return ((Number)m.invoke(rail)).floatValue();});
 }
 void install(boolean compact) throws Exception {
  snoozes.set(0);stops.set(0);
  main(()->{
   stage.removeAllViews();
   rail=(View)sliderClass.getConstructor(Context.class,function,function,boolean.class)
    .newInstance(activity,callback(snoozes),callback(stops),compact);
   thumb=getThumb(rail);
   stage.addView(rail,new FrameLayout.LayoutParams(compact?dp(160):stage.getWidth()-dp(32),dp(compact?64:80),Gravity.CENTER));
   return null;
  });
  waitFor(()->main(()->rail.getWidth()>0 && thumb.getWidth()>0 && rail.isAttachedToWindow() && rail.hasWindowFocus()),10000,"Rail not visible/focused");
 }
 void event(int action,float x,long at){
  MotionEvent e=MotionEvent.obtain(down,at,action,x,thumb.getHeight()/2f,0);
  thumb.dispatchTouchEvent(e);e.recycle();
 }
 void press() throws Exception {
  main(()->{down=SystemClock.uptimeMillis();origin=thumb.getWidth()/2f;event(MotionEvent.ACTION_DOWN,origin,down);return null;});
 }
 void move(float offset,long delay) throws Exception {
  SystemClock.sleep(delay);
  main(()->{event(MotionEvent.ACTION_MOVE,origin+offset,SystemClock.uptimeMillis());return null;});
 }
 void end(float offset,boolean cancel) throws Exception {
  main(()->{event(cancel?MotionEvent.ACTION_CANCEL:MotionEvent.ACTION_UP,origin+offset,SystemClock.uptimeMillis());return null;});
 }
 float x() throws Exception{return main(()->thumb.getTranslationX());}
 void noAction(String label){require(snoozes.get()==0 && stops.get()==0,label+" triggered an alarm action");}
 void rested() throws Exception {
  waitFor(()->main(()->Math.abs(thumb.getTranslationX())<1f && Math.abs(thumb.getScaleX()-1f)<.012f &&
    Math.abs(thumb.getScaleY()-1f)<.012f && Math.abs(thumb.getRotation())<.001f),2200,"Thumb did not settle");
 }
 Bitmap render() throws Exception {
  return main(()->{Bitmap b=Bitmap.createBitmap(rail.getWidth(),rail.getHeight(),Bitmap.Config.ARGB_8888);rail.draw(new Canvas(b));return b;});
 }
 void save(String name,Bitmap b) throws Exception {
  File dir=new File(target.getFilesDir(),"rubber-feedback");dir.mkdirs();
  try(FileOutputStream out=new FileOutputStream(new File(dir,name+".png"))){b.compress(Bitmap.CompressFormat.PNG,100,out);}
 }
 int[] glyph(Bitmap b,float center) {
  int left=b.getWidth(),right=-1,top=b.getHeight(),bottom=-1;
  for(int y=Math.max(0,b.getHeight()/2-dp(18));y<Math.min(b.getHeight(),b.getHeight()/2+dp(18));y++)
   for(int x=Math.max(0,(int)center-dp(24));x<Math.min(b.getWidth(),center+dp(24));x++){
    int c=b.getPixel(x,y);
    if(Color.red(c)<160 && Color.green(c)>180 && Color.blue(c)>140){left=Math.min(left,x);right=Math.max(right,x);top=Math.min(top,y);bottom=Math.max(bottom,y);}
   }
  return new int[]{right-left+1,bottom-top+1};
 }
 void stretch() throws Exception {
  install(false);Bitmap base=render();int[] before=glyph(base,base.getWidth()/2f);save("rest",base);
  float limit=travel();press();move(limit*.45f,100);SystemClock.sleep(180);
  float scale=main(()->thumb.getScaleX());
  require(scale>1.04f && scale<1.27f,"Thumb must stretch, got "+scale);
  require(main(()->thumb.getScaleY())<.99f,"Thumb must thin gently");
  require(Math.abs(main(()->thumb.getRotation()))<.001f,"Thumb must not tumble");
  require(Math.abs(x()-limit*.45f)<2f,"Thumb must track the finger");
  require(main(()->{Field f=sliderClass.getDeclaredField("stopLabel");f.setAccessible(true);return ((View)f.get(rail)).getAlpha();})<.03f,
    "Destination letters remain visible beneath the stretched thumb");
  Bitmap stretched=render();int[] after=glyph(stretched,stretched.getWidth()/2f+x());save("stretch",stretched);
  require(before[0]>dp(8) && before[1]>dp(8),"No alarm glyph in baseline");
  require(Math.abs(after[0]-before[0])<=dp(2) && Math.abs(after[1]-before[1])<=dp(2),
   "Alarm glyph was distorted with pill: "+Arrays.toString(before)+" -> "+Arrays.toString(after));
  end(limit*.45f,false);SystemClock.sleep(60);save("return",render());rested();noAction("Partial release");
  report("PASS horizontal stretch, no rotation, crisp glyph, direct tracking and settled return");
 }
 void inertia() throws Exception {
  install(false);final float limit=travel();final float released=limit*.58f;
  main(()->{
   down=SystemClock.uptimeMillis();origin=thumb.getWidth()/2f;
   event(MotionEvent.ACTION_DOWN,origin,down);
   event(MotionEvent.ACTION_MOVE,origin+limit*.10f,down+10);
   event(MotionEvent.ACTION_MOVE,origin+released,down+20);
   event(MotionEvent.ACTION_UP,origin+released,down+21);return null;
  });
  float peak=released;
  for(int i=0;i<20;i++){SystemClock.sleep(12);peak=Math.max(peak,x());}
  require(peak>released+dp(2),"Release lost inertia: "+released+" -> "+peak);
  rested();noAction("Inertial overshoot");
  report("PASS release momentum retained without triggering actions, release="+released+" peak="+peak);
  install(false);press();move(limit*.5f,80);end(limit*.5f,false);SystemClock.sleep(50);
  float caught=main(()->{
   float before=thumb.getTranslationX();down=SystemClock.uptimeMillis();origin=thumb.getWidth()/2f;
   event(MotionEvent.ACTION_DOWN,origin,down);
   require(Math.abs(thumb.getTranslationX()-before)<1.5f,"Catching return jumped at touch-down");
   return before;
  });
  move(dp(6),30);require(Math.abs(x()-(caught+dp(6)))<2f,"Caught thumb stopped following finger");
  end(dp(6),true);rested();noAction("Interrupted return");
  report("PASS catch/reverse/cancel continuity");
 }
 void actions() throws Exception {
  for(int sign:new int[]{-1,1}){
   install(false);float distance=travel()*.79f*sign;press();move(distance,100);end(distance,false);
   require(snoozes.get()==(sign<0?1:0) && stops.get()==(sign>0?1:0),"Wrong physical swipe action");
   main(()->{event(MotionEvent.ACTION_UP,origin+distance,SystemClock.uptimeMillis());return null;});
   require(snoozes.get()+stops.get()==1,"Repeated action");
  }
  install(false);float distance=travel()*.85f;press();move(distance,100);end(distance,true);rested();noAction("Canceled threshold crossing");
  install(false);press();move(travel()*.85f,100);
  main(()->{rail.onWindowFocusChanged(false);return null;});
  require(Math.abs(x())<.01f,"Focus loss must reset immediately");end(0,false);noAction("Focus loss");
  install(false);press();move(travel()*.4f,80);
  main(()->{stage.removeView(rail);return null;});require(Math.abs(x())<.01f,"Detach did not reset");noAction("Detach");
  report("PASS left/right exactly once, cancellation, focus loss and detach");
 }
 void compact() throws Exception {
  install(true);float limit=travel();
  main(()->{sliderClass.getMethod("setBezelProgress",boolean.class,float.class).invoke(rail,true,.65f);return null;});
  SystemClock.sleep(70);float intermediate=x();
  require(intermediate<0 && intermediate>-limit*.65f,"Bezel did not have an intermediate spring frame");
  main(()->{sliderClass.getMethod("setBezelProgress",boolean.class,float.class).invoke(rail,false,.6f);return null;});
  SystemClock.sleep(90);save("compact",render());
  require(Math.abs(main(()->thumb.getRotation()))<.001f,"Compact thumb rotated");
  main(()->{sliderClass.getMethod("releaseBezel").invoke(rail);return null;});rested();noAction("Bezel visual progress");
  report("PASS compact bezel smoothing, reversal and action-free return");
 }
 void shell(String command) throws Exception {
  try(InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(getUiAutomation().executeShellCommand(command))) {byte[] b=new byte[1024];while(in.read(b)!=-1){}}
 }
 void reduced() throws Exception {
  shell("settings put global animator_duration_scale 0");
  try {
   Class<?> motion=Class.forName("dev.orbit.clock.core.OrbitMotion",true,target.getClassLoader());
   waitFor(()->!(Boolean)motion.getMethod("enabled").invoke(motion.getField("INSTANCE").get(null)),6000,"Reduced motion not applied");
   install(false);press();move(travel()*.4f,50);
   require(main(()->thumb.getScaleX())==1f && main(()->thumb.getScaleY())==1f,"Reduced motion stretched");
   end(travel()*.4f,false);require(Math.abs(x())<.01f,"Reduced motion animated return");noAction("Reduced motion");
   report("PASS reduced motion");
  } finally {shell("settings put global animator_duration_scale 1");}
 }
 Bitmap sweep(Drawable d,Method phase,float value) throws Exception {
  phase.invoke(d,value);Bitmap b=Bitmap.createBitmap(600,100,Bitmap.Config.ARGB_8888);d.draw(new Canvas(b));return b;
 }
 int peak(Bitmap b,int from,int to){int best=from,alpha=-1;for(int x=from;x<to;x++)if(Color.alpha(b.getPixel(x,50))>alpha){best=x;alpha=Color.alpha(b.getPixel(x,50));}return best;}
 void shimmer() throws Exception {
  Class<?> type=Class.forName("dev.orbit.clock.core.VisualHints$Sweep",true,target.getClassLoader());
  Constructor<?> ctor=type.getDeclaredConstructor(boolean.class);ctor.setAccessible(true);
  Drawable drawable=(Drawable)ctor.newInstance(true);drawable.setBounds(0,0,600,100);
  Method phase=type.getDeclaredMethod("setPhase",float.class);phase.setAccessible(true);
  Bitmap a=sweep(drawable,phase,.25f),b=sweep(drawable,phase,.30f),end=sweep(drawable,phase,1f);
  int left=peak(a,0,300),right=peak(a,300,600);
  require(left<180 && right>420 && Color.alpha(a.getPixel(left,50))>40 && Color.alpha(a.getPixel(right,50))>40,"Missing two glints");
  require(peak(b,0,300)<left && peak(b,300,600)>right,"Glints must move outward");
  require(Color.alpha(a.getPixel(300,50))==0 && Color.alpha(a.getPixel(0,0))==0,"Shimmer must stay localized and clipped");
  for(int x=0;x<600;x++)require(Color.alpha(end.getPixel(x,50))==0,"Shimmer must finish cleanly");
  save("shimmer",a);save("shimmer-outward",b);
  target.getSharedPreferences("visual_hints",0).edit().putBoolean("ring_controls.learned",true).putInt("ring_controls.shown",99).commit();
  install(false);
  Class<?> hints=Class.forName("dev.orbit.clock.core.VisualHints",true,target.getClassLoader());
  Field active=hints.getDeclaredField("active");active.setAccessible(true);
  waitFor(()->main(()->((Map<?,?>)active.get(null)).containsKey(rail)),2000,"Old learning hid new ringing cue");
  press();require(!main(()->((Map<?,?>)active.get(null)).containsKey(rail)),"Touch did not stop shimmer");
  end(0,true);rested();noAction("Shimmer");
  Bitmap composite=render();drawable.setBounds(0,0,composite.getWidth(),composite.getHeight());
  phase.invoke(drawable,.25f);drawable.draw(new Canvas(composite));save("shimmer-on-rail",composite);
  report("PASS two outward glints, clipped/finite drawing, fresh cue despite old learning, immediate touch cancellation");
 }
 @Override public void onCreate(Bundle args){super.onCreate(args);start();}
 @Override public void onStart(){
  Bundle result=new Bundle();
  try {
   target=getTargetContext();density=target.getResources().getDisplayMetrics().density;
   ClassLoader loader=target.getClassLoader();sliderClass=Class.forName("dev.orbit.clock.core.AlarmSlideControl",true,loader);
   function=Class.forName("kotlin.jvm.functions.Function0",true,loader);unit=Class.forName("kotlin.Unit",true,loader).getField("INSTANCE").get(null);
   activity=startActivitySync(new Intent().setClassName(target.getPackageName(),"dev.orbit.clock.phone.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
   waitFor(()->main(()->activity.hasWindowFocus()),60000,"MainActivity never focused");
   main(()->{stage=new FrameLayout(activity);stage.setBackgroundColor(0xff030405);
     activity.addContentView(stage,new android.view.ViewGroup.LayoutParams(-1,-1));return null;});
   waitFor(()->main(()->stage.getWidth()>0),5000,"Stage not laid out");
   shimmer();stretch();inertia();actions();compact();reduced();
   result.putString("stream","RUBBER_RESULT_PASS: native views, stretch/glyph rendering, inertia, safe actions, interruption, bezel and reduced motion\n");
   finish(Activity.RESULT_OK,result);
  }catch(Throwable e){result.putString("stream","RUBBER_FAILURE: "+android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,result);}
 }
}
