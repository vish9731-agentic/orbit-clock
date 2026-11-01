package dev.orbit.focusprobe;
import android.content.*;
import android.media.*;
import android.media.session.*;
import android.os.*;
import java.io.*;

/** Independent native media app fixture; never packaged in either Orbit APK. */
final class ProbeController {
 interface Player { void start(); void pause(); boolean playing(); int position(); void duck(); }
 final MediaSession session;
 private final Context context;
 private final Player player;
 private final AudioManager manager;
 private final AudioFocusRequest request;
 private final Handler handler=new Handler(Looper.getMainLooper());
 private String events="";
 private boolean closed=false;
 private Boolean lastPlaying=null;
 private final Runnable sample=new Runnable(){public void run(){if(!closed){write();handler.postDelayed(this,250);}}};
 ProbeController(Context c,Player p,String mode) {
  context=c;player=p;manager=c.getSystemService(AudioManager.class);
  c.getSharedPreferences("probe",0).edit().clear().putString("mode",mode).commit();
  session=new MediaSession(c,"Orbit independent "+mode+" player");
  session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS|MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
  session.setMetadata(new MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE,mode+" focus test").build());
  session.setCallback(new MediaSession.Callback(){
   @Override public void onPause(){player.pause();event("media_pause");}
   @Override public void onPlay(){player.start();event("media_play");}
  },handler);
  session.setActive(true);
  AudioAttributes attributes=new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
   .setContentType(mode.equals("video")?AudioAttributes.CONTENT_TYPE_MOVIE:AudioAttributes.CONTENT_TYPE_MUSIC).build();
  request=new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attributes)
   .setWillPauseWhenDucked(false).setOnAudioFocusChangeListener(change->{
    if(closed)return;
    if(change==AudioManager.AUDIOFOCUS_GAIN) player.start();
    else if(change==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK)player.duck();
    else player.pause();
    event("focus:"+change);
   },handler).build();
  int result=manager.requestAudioFocus(request);
  if(result==AudioManager.AUDIOFOCUS_REQUEST_GRANTED)player.start();
  event("request:"+result); handler.post(sample);
 }
 private void event(String e){events+=e+";";write();}
 private void write(){
  boolean playing=player.playing();int position=player.position();
  context.getSharedPreferences("probe",0).edit().putBoolean("playing",playing).putInt("position",position).putString("events",events).apply();
  if(lastPlaying==null || lastPlaying!=playing){
   session.setPlaybackState(new PlaybackState.Builder().setActions(PlaybackState.ACTION_PLAY|PlaybackState.ACTION_PAUSE|PlaybackState.ACTION_PLAY_PAUSE)
    .setState(playing?PlaybackState.STATE_PLAYING:PlaybackState.STATE_PAUSED,position,playing?1:0).build());
   lastPlaying=playing;
  }
 }
 void close(){if(closed)return;closed=true;handler.removeCallbacks(sample);player.pause();event("closed");session.setActive(false);session.release();manager.abandonAudioFocusRequest(request);}
 static File asset(Context c,String name)throws IOException{
  File file=new File(c.getFilesDir(),name);
  try(InputStream in=c.getAssets().open(name);FileOutputStream out=new FileOutputStream(file)){
   byte[] data=new byte[32768];int n;while((n=in.read(data))!=-1)out.write(data,0,n);
  }return file;
 }
}
