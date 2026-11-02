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

 View thumbPart(String name) throws Exception {
  return main(()->{Field f=sliderClass.getDeclaredField(name);f.setAccessible(true);return (View)f.get(rail);});
 }
 float surfaceStretch() throws Exception {
  return main(()->{Field owner=sliderClass.getDeclaredField("thumbSurface");owner.setAccessible(true);
   Object surface=owner.get(rail);Field stretch=surface.getClass().getDeclaredField("stretch");
   stretch.setAccessible(true);return ((Number)stretch.get(surface)).floatValue();});
 }
 void unscaledLayers() throws Exception {
  View icon=thumbPart("thumbIcon");
  require(main(()->thumb.getScaleX()==1f && thumb.getScaleY()==1f &&
    icon.getScaleX()==1f && icon.getScaleY()==1f && icon.getParent()==thumb),
    "Thumb container/icon must be separate unscaled layers");
  require(main(()->icon.getWidth()==dp(24) && icon.getHeight()==dp(24)),
    "Phone glyph's layout dimensions changed");
 }
 int[] hardwareGlyph(String name) throws Exception {
  SystemClock.sleep(40); // Allow the hardware display list to present after the input frame.
  Bitmap shot=getUiAutomation().takeScreenshot();
  require(shot!=null,"Hardware screenshot unavailable: "+name);
  Bitmap image=shot.copy(Bitmap.Config.ARGB_8888,false);
  int[] bounds=main(()->{int[] xy=new int[2];rail.getLocationOnScreen(xy);
    Point display=new Point();activity.getWindowManager().getDefaultDisplay().getRealSize(display);
    return new int[]{xy[0],xy[1],rail.getWidth(),rail.getHeight(),display.x,display.y};});
  float sx=image.getWidth()/(float)bounds[4],sy=image.getHeight()/(float)bounds[5];
  int x1=Math.max(0,Math.round(bounds[0]*sx)),y1=Math.max(0,Math.round(bounds[1]*sy));
  int x2=Math.min(image.getWidth(),Math.round((bounds[0]+bounds[2])*sx));
  int y2=Math.min(image.getHeight(),Math.round((bounds[1]+bounds[3])*sy));
  int width=x2-x1,height=y2-y1;
  require(width>0 && height>0,"Empty hardware rail bounds: "+Arrays.toString(bounds));
  int[] columns=new int[width];int rawPixels=0;
  for(int y=y1;y<y2;y++)for(int x=x1;x<x2;x++){
   int color=image.getPixel(x,y);
   if(Color.red(color)<160 && Color.green(color)>180 && Color.blue(color)>140){columns[x-x1]++;rawPixels++;}
  }
  // LCD text antialiasing contributes isolated cyan-looking fringe pixels. Locate
  // the dense icon-sized region first; the icon remains separate from both labels.
  int window=Math.min(width,Math.max(1,Math.round(dp(36)*sx)));
  int sum=0;for(int x=0;x<window;x++)sum+=columns[x];
  int best=sum,bestStart=0;
  for(int start=1;start<=width-window;start++){
   sum+=columns[start+window-1]-columns[start-1];
   if(sum>best){best=sum;bestStart=start;}
  }
  int windowX=x1+bestStart;
  boolean[] mask=new boolean[window*height];int windowPixels=0;
  for(int y=0;y<height;y++)for(int x=0;x<window;x++){
   int color=image.getPixel(windowX+x,y1+y);
   if(Color.red(color)<160 && Color.green(color)>180 && Color.blue(color)>140){mask[y*window+x]=true;windowPixels++;}
  }
  int left=x2,right=-1,top=y2,bottom=-1,pixels=0;
  int[] queue=new int[mask.length];
  for(int seed=0;seed<mask.length;seed++)if(mask[seed]){
   int head=0,tail=0,count=0,l=window,r=-1,t=height,bot=-1;
   queue[tail++]=seed;mask[seed]=false;
   while(head<tail){
    int point=queue[head++],x=point%window,y=point/window;
    count++;l=Math.min(l,x);r=Math.max(r,x);t=Math.min(t,y);bot=Math.max(bot,y);
    for(int dy=-1;dy<=1;dy++)for(int dx=-1;dx<=1;dx++){
     int nx=x+dx,ny=y+dy;
     if(nx>=0 && nx<window && ny>=0 && ny<height && mask[ny*window+nx]){
      mask[ny*window+nx]=false;queue[tail++]=ny*window+nx;
     }
    }
   }
   // Preserve separate clock-ring, hand and bell components; reject only tiny
   // text-fringe specks (the observed false positives were 1-3 pixels each).
   if(count>=4){left=Math.min(left,windowX+l);right=Math.max(right,windowX+r);
    top=Math.min(top,y1+t);bottom=Math.max(bottom,y1+bot);pixels+=count;}
  }
  save("hardware-"+name,image);
  require(pixels>20,"No hardware-rendered alarm glyph: "+name+" display="+Arrays.toString(bounds));
  report("HARDWARE GLYPH "+name+" width="+(right-left+1)+" height="+(bottom-top+1)+" pixels="+pixels+
    " rawCyan="+rawPixels+" iconWindowCyan="+windowPixels+" rejected="+(rawPixels-pixels));
  return new int[]{right-left+1,bottom-top+1,pixels};
 }
 void sameHardwareGlyph(int[] base,int[] actual,String name) {
  float ratio=(actual[0]/(float)actual[1])/(base[0]/(float)base[1]);
  require(Math.abs(actual[0]-base[0])<=2 && Math.abs(actual[1]-base[1])<=2 &&
    Math.abs(ratio-1f)<.06f,
    "Actual screen glyph stretched during "+name+": "+Arrays.toString(base)+" -> "+Arrays.toString(actual));
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
  waitFor(()->Math.abs(surfaceStretch())<.012f,2200,"Pill surface did not settle");
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
  install(false);unscaledLayers();
  Bitmap base=render();int[] before=glyph(base,base.getWidth()/2f);save("rest",base);
  int[] hardwareBase=hardwareGlyph("rest");
  float limit=travel();press();move(limit*.45f,120);SystemClock.sleep(180);
  float shape=surfaceStretch();
  require(shape>.035f && shape<.25f,"Pill surface must stretch, got "+shape);
  unscaledLayers();
  require(Math.abs(main(()->thumb.getRotation()))<.001f,"Thumb must not tumble");
  require(Math.abs(x()-limit*.45f)<2f,"Thumb must track the finger");
  require(main(()->{Field f=sliderClass.getDeclaredField("stopLabel");f.setAccessible(true);return ((View)f.get(rail)).getAlpha();})<.03f,
    "Destination letters remain visible beneath the stretched thumb");
  Bitmap stretched=render();int[] after=glyph(stretched,stretched.getWidth()/2f+x());save("stretch",stretched);
  require(before[0]>dp(8) && before[1]>dp(8),"No alarm glyph in baseline");
  require(Math.abs(after[0]-before[0])<=2 && Math.abs(after[1]-before[1])<=2,
   "Software alarm glyph changed: "+Arrays.toString(before)+" -> "+Arrays.toString(after));
  sameHardwareGlyph(hardwareBase,hardwareGlyph("slow-drag"),"slow drag");
  move(limit*.59f,12);
  sameHardwareGlyph(hardwareBase,hardwareGlyph("fast-drag"),"fast drag");
  SystemClock.sleep(250);
  sameHardwareGlyph(hardwareBase,hardwareGlyph("held"),"held stretch");
  unscaledLayers();
  end(limit*.59f,false);SystemClock.sleep(60);
  sameHardwareGlyph(hardwareBase,hardwareGlyph("return"),"spring return");
  save("return",render());rested();noAction("Partial release");
  report("PASS surface-only stretch, separate unit-scale glyph, hardware glyph invariance at rest/slow/fast/hold/return");
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
  install(false);final float displaced=travel()*.85f;
  main(()->{thumb.setTranslationX(displaced);return null;});
  press();end(0,false);rested();noAction("Tap catching an off-center thumb");
  report("PASS catching tap cannot commit from inertial displacement");
  install(false);float distance=travel()*.85f;press();move(distance,100);end(distance,true);rested();noAction("Canceled threshold crossing");
  install(false);press();move(travel()*.85f,100);
  main(()->{rail.onWindowFocusChanged(false);return null;});
  require(Math.abs(x())<.01f,"Focus loss must reset immediately");end(0,false);noAction("Focus loss");
  install(false);press();move(travel()*.4f,80);
  main(()->{stage.removeView(rail);return null;});require(Math.abs(x())<.01f,"Detach did not reset");noAction("Detach");
  report("PASS left/right exactly once, cancellation, focus loss and detach");
 }
 void visualEdgeCatch() throws Exception {
  install(false);
  main(()->{
   Field owner=sliderClass.getDeclaredField("thumbSurface");owner.setAccessible(true);
   View surface=(View)owner.get(rail);
   Field stretch=surface.getClass().getDeclaredField("stretch");stretch.setAccessible(true);
   stretch.setFloat(surface,.18f);surface.invalidate();
   float edge=thumb.getRight()+thumb.getWidth()*.09f-2f;
   float middle=thumb.getTop()+thumb.getHeight()/2f;
   require(edge>thumb.getRight(),"Catch point must lie outside the fixed thumb frame");
   long at=SystemClock.uptimeMillis();
   MotionEvent downEvent=MotionEvent.obtain(at,at,MotionEvent.ACTION_DOWN,edge,middle,0);
   downEvent.setSource(InputDevice.SOURCE_TOUCHSCREEN);
   rail.dispatchTouchEvent(downEvent);downEvent.recycle();
   require((Boolean)sliderClass.getMethod("getTouchActive").invoke(rail),
     "Expanded visual edge did not capture touch outside the fixed frame");
   MotionEvent upEvent=MotionEvent.obtain(at,at+1,MotionEvent.ACTION_UP,edge,middle,0);
   upEvent.setSource(InputDevice.SOURCE_TOUCHSCREEN);
   rail.dispatchTouchEvent(upEvent);upEvent.recycle();
   return null;
  });
  rested();noAction("Catch at expanded visual edge");
  report("PASS ViewGroup dispatch catches stretched edge outside fixed frame without action fall-through");
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
   require(main(()->thumb.getScaleX())==1f && main(()->thumb.getScaleY())==1f && Math.abs(surfaceStretch())<.001f,"Reduced motion stretched");
   end(travel()*.4f,false);require(Math.abs(x())<.01f,"Reduced motion animated return");noAction("Reduced motion");
   report("PASS reduced motion");
  } finally {shell("settings put global animator_duration_scale 1");}
 }
 Bitmap sweep(Drawable d,Method phase,float value) throws Exception {
  phase.invoke(d,value);Bitmap b=Bitmap.createBitmap(600,100,Bitmap.Config.ARGB_8888);d.draw(new Canvas(b));return b;
 }
 int peak(Bitmap b,int from,int to){return peakAt(b,from,to,50);}
 int peakAt(Bitmap b,int from,int to,int row){
  int best=from,alpha=-1;for(int x=from;x<to;x++)if(Color.alpha(b.getPixel(x,row))>alpha){best=x;alpha=Color.alpha(b.getPixel(x,row));}return best;
 }
 void shimmer() throws Exception {
  Class<?> type=Class.forName("dev.orbit.clock.core.VisualHints$Sweep",true,target.getClassLoader());
  Constructor<?> ctor=type.getDeclaredConstructor(boolean.class);ctor.setAccessible(true);
  Drawable drawable=(Drawable)ctor.newInstance(true);drawable.setBounds(0,0,600,100);
  Method phase=type.getDeclaredMethod("setPhase",float.class);phase.setAccessible(true);
  Bitmap a=sweep(drawable,phase,.10f),b=sweep(drawable,phase,.14f),end=sweep(drawable,phase,1f);
  int left=peak(a,0,300),right=peak(a,300,600);
  require(left<270 && right>330 && Color.alpha(a.getPixel(left,50))>25 && Color.alpha(a.getPixel(right,50))>25,"Missing two expanding fronts");
  int leftTop=peakAt(a,0,300,20),leftBottom=peakAt(a,0,300,80);
  int rightTop=peakAt(a,300,600,20),rightBottom=peakAt(a,300,600,80);
  require(leftTop-left>=3 && leftBottom-left>=3 && right-rightTop>=3 && right-rightBottom>=3,
    "Fronts are vertical bands, not curved semicircles: left="+left+" top="+leftTop+" bottom="+leftBottom+
      " right="+right+" top="+rightTop+" bottom="+rightBottom);
  require(Math.abs(leftTop-leftBottom)<=2 && Math.abs(rightTop-rightBottom)<=2,
    "Semicircular fronts are not symmetric around the thumb centreline");
  require(Color.alpha(a.getPixel(leftTop,20))>15 && Color.alpha(a.getPixel(rightTop,20))>15,
    "Arc curvature must come from visible rims, not empty rows");
  require(peak(b,0,300)<left && peak(b,300,600)>right,"Semicircular fronts must expand outward");
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
  phase.invoke(drawable,.10f);drawable.draw(new Canvas(composite));save("shimmer-on-rail",composite);
  phase.invoke(drawable,.14f);Bitmap later=render();drawable.draw(new Canvas(later));save("shimmer-on-rail-later",later);
  report("PASS curved expanding semicircular fronts, hollow/finite drawing, fresh cue despite old learning, immediate touch cancellation");
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
   shimmer();stretch();inertia();actions();visualEdgeCatch();compact();reduced();
   result.putString("stream","RUBBER_RESULT_PASS: native views, stretch/glyph rendering, inertia, safe actions, interruption, bezel and reduced motion\n");
   finish(Activity.RESULT_OK,result);
  }catch(Throwable e){result.putString("stream","RUBBER_FAILURE: "+android.util.Log.getStackTraceString(e));finish(Activity.RESULT_CANCELED,result);}
 }
}
