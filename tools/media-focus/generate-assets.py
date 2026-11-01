import math, struct, subprocess, sys, wave
from pathlib import Path
import imageio_ffmpeg
p=Path(sys.argv[1]);p.mkdir(parents=True,exist_ok=True)
with wave.open(str(p/'music.wav'),'wb') as out:
    out.setnchannels(1);out.setsampwidth(2);out.setframerate(44100)
    out.writeframes(b''.join(struct.pack('<h',int(3000*math.sin(2*math.pi*220*i/44100))) for i in range(30*44100)))
subprocess.run([imageio_ffmpeg.get_ffmpeg_exe(),'-y','-loglevel','error','-f','lavfi','-i','testsrc2=size=320x180:rate=15','-f','lavfi','-i','sine=frequency=440:sample_rate=44100','-t','30','-c:v','libx264','-preset','ultrafast','-crf','32','-pix_fmt','yuv420p','-c:a','aac','-b:a','64k','-movflags','+faststart',str(p/'clip.mp4')],check=True)
