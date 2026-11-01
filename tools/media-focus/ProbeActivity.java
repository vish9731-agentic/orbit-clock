package dev.orbit.focusprobe;
import android.app.*;
import android.content.*;
import android.media.*;
import android.os.*;
import android.widget.*;
public class ProbeActivity extends Activity {
 private VideoView video;private ProbeController controller;
 @Override public void onCreate(Bundle b){super.onCreate(b);String mode=getIntent().getStringExtra("mode");
  getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
  if("stop".equals(mode)){stopService(new Intent(this,MusicService.class));finish();return;}
  if(!"video".equals(mode)){
   TextView text=new TextView(this);text.setText("Background music playback fixture");text.setTextSize(24);setContentView(text);
   startForegroundService(new Intent(this,MusicService.class));return;
  }
  video=new VideoView(this);setContentView(video);video.setAudioFocusRequest(AudioManager.AUDIOFOCUS_NONE);
  video.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build());
  video.setOnPreparedListener(mp->{mp.setLooping(true);mp.setVolume(.03f,.03f);
   controller=new ProbeController(this,new ProbeController.Player(){
    public void start(){mp.setVolume(.03f,.03f);video.start();}public void pause(){video.pause();}
    public boolean playing(){return video.isPlaying();}public int position(){return video.getCurrentPosition();}public void duck(){mp.setVolume(.003f,.003f);}
   },"video");
  });
  try{video.setVideoPath(ProbeController.asset(this,"clip.mp4").getAbsolutePath());}catch(Exception e){throw new RuntimeException(e);}
 }
 @Override public void onDestroy(){if(controller!=null)controller.close();if(video!=null)video.stopPlayback();super.onDestroy();}
}
