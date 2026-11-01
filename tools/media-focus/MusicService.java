package dev.orbit.focusprobe;
import android.app.*;
import android.content.*;
import android.media.*;
import android.os.*;
public class MusicService extends Service {
 private MediaPlayer player;private ProbeController controller;
 @Override public int onStartCommand(Intent i,int flags,int id){
  NotificationManager n=getSystemService(NotificationManager.class);
  n.createNotificationChannel(new NotificationChannel("probe","Playback fixture",NotificationManager.IMPORTANCE_LOW));
  startForeground(91,new Notification.Builder(this,"probe").setSmallIcon(android.R.drawable.ic_media_play).setContentTitle("Music focus probe").build());
  if(player==null)try{
   player=new MediaPlayer();player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build());
   player.setDataSource(ProbeController.asset(this,"music.wav").getAbsolutePath());player.setLooping(true);player.setVolume(.03f,.03f);player.prepare();
   controller=new ProbeController(this,new ProbeController.Player(){
    public void start(){player.setVolume(.03f,.03f);player.start();}public void pause(){if(player.isPlaying())player.pause();}
    public boolean playing(){return player.isPlaying();}public int position(){return player.getCurrentPosition();}public void duck(){player.setVolume(.003f,.003f);}
   },"music");
  }catch(Exception e){throw new RuntimeException(e);}return START_NOT_STICKY;
 }
 @Override public void onDestroy(){if(controller!=null)controller.close();if(player!=null)player.release();super.onDestroy();}
 @Override public IBinder onBind(Intent i){return null;}
}
