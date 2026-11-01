"""Exercise real independent media playback against real Orbit alarm deliveries."""
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

PROBE='dev.orbit.focusprobe'

def run(ui):
    adb,click,find,xy,tap,records,record,preferences,passed,assert_scheduled=[ui[k] for k in
        ['adb','click','find','xy','tap_xy','records','record','preferences','passed','assert_scheduled']]
    pkg=ui['PKG'];out=ui['OUT']
    def player():
        # SharedPreferences is rewritten frequently by the independent player.
        # Retry only reads: an empty/transient XML snapshot is not a playback result.
        last=''
        for attempt in range(12):
            try:
                raw=adb('exec-out','run-as',PROBE,'cat','shared_prefs/probe.xml')
                return {n.attrib['name']:n.attrib.get('value',n.text or '') for n in ET.fromstring(raw)}
            except subprocess.CalledProcessError as error:
                if 'No such file or directory' not in (error.stderr or ''):raise
                last=error.stderr
            except ET.ParseError as error:
                last=str(error)
            time.sleep(.08)
        raise AssertionError('No complete playback status snapshot: '+last)
    def await_player(condition,label):
        until=time.monotonic()+12;last={}
        while time.monotonic()<until:
            last=player()
            if condition(last):return last
            time.sleep(.15)
        raise AssertionError(label+': '+str(last))
    def main():adb('shell','am','start','-W','-f','0x14000000','-n',pkg+'/dev.orbit.clock.phone.MainActivity')
    def start_player(mode):
        adb('shell','am','start','-W','-n',PROBE+'/.ProbeActivity','--es','mode',mode)
        await_player(lambda p:p.get('mode')==mode and p.get('playing')=='true',mode+' did not play')
    def advancing(mode):
        before=player();time.sleep(.65);after=player()
        assert after.get('playing')=='true' and int(after.get('position','0'))!=int(before.get('position','0')), (before,after)
        passed(('Background music' if mode=='music' else 'Foreground video')+' plays and advances before the alarm')
    def create_timer():
        main();click('Timer');click('1')
        one=xy(find(desc='Timer preset: 1 min'));start=xy(find('Start timer'))
        before={a['id'] for a in records()};tap(start)
        a=next(a for a in records() if a['id'] not in before and a.get('timer') and a['enabled'])
        return a,one,start
    def await_alarm(a):
        until=time.monotonic()+100
        while time.monotonic()<until:
            if int(preferences('scheduled_occurrences').get(a['id']+'.active',0))==a['snoozeUntil']:return
            time.sleep(.4)
        raise AssertionError('Alarm did not ring: '+a['id'])
    def paused(mode):
        p=await_player(lambda p:p.get('mode')==mode and p.get('playing')=='false' and 'focus:-2;' in p.get('events',''),mode+' did not pause on transient focus loss')
        assert 'media_pause;' in p['events'], p
        before=int(p['position']);time.sleep(.65);after=player()
        assert after.get('playing')=='false' and abs(int(after['position'])-before)<150,(p,after)
        (out/(mode+'-paused.txt')).write_text(str(after))
        (out/(mode+'-audio-focus.txt')).write_text(adb('shell','dumpsys','audio'))
        passed(('Music' if mode=='music' else 'Video')+' pauses on alarm focus loss and receives the native Pause command')
    def stop_banner(snooze=False):
        adb('shell','cmd','statusbar','expand-notifications')
        click(desc='Snooze alarm for 10 minutes' if snooze else 'Stop alarm')
        adb('shell','cmd','statusbar','collapse')
    def resumed(mode):
        p=await_player(lambda p:p.get('mode')==mode and p.get('playing')=='true' and 'focus:1;' in p.get('events',''),mode+' did not regain focus')
        (out/(mode+'-resumed.txt')).write_text(str(p))

    adb('shell','appops','set',pkg,'USE_FULL_SCREEN_INTENT','ignore')
    first,one,start=create_timer()
    start_player('music')
    adb('shell','am','start','-W','-a','android.settings.SETTINGS')
    advancing('music')
    # A second real timer follows while music stays in a foreground playback service.
    main();click('Timer');time.sleep(.7);before={a['id'] for a in records()};tap(one);tap(start)
    second=next(a for a in records() if a['id'] not in before and a.get('timer') and a['enabled'])
    assert 0<second['snoozeUntil']-first['snoozeUntil']<45000,(first,second)
    adb('shell','am','start','-W','-a','android.settings.SETTINGS')
    await_alarm(first);paused('music')
    await_alarm(second);time.sleep(.5)
    p=player();assert p['playing']=='false' and p['events'].count('media_pause;')==1 and 'focus:1;' not in p['events'],p
    passed('Overlapping alarms share focus without resuming or repeatedly pausing the player')
    stop_banner();assert not record(second['id'])['enabled']
    time.sleep(.6);p=player()
    assert p['playing']=='false' and 'focus:1;' not in p['events'],p
    assert int(preferences('scheduled_occurrences').get(first['id']+'.active',0))==first['snoozeUntil']
    passed('Stopping one overlapping alarm keeps media paused while the other rings')
    stop_banner();assert not record(first['id'])['enabled'];resumed('music')
    passed('Stopping the last alarm releases audio focus to the previous music player')
    adb('shell','am','start','-W','-n',PROBE+'/.ProbeActivity','--es','mode','stop')
    await_player(lambda p:p.get('playing')=='false' and 'closed;' in p.get('events',''),'Music fixture did not stop')
    video,_,_=create_timer();start_player('video');advancing('video')
    await_alarm(video);paused('video')
    before=int(adb('shell','date','+%s').strip())*1000
    stop_banner(snooze=True)
    snoozed=record(video['id']);assert snoozed['snoozeMinutes']==video['snoozeMinutes']
    assert before+10*60000<=snoozed['snoozeUntil']<=int(adb('shell','date','+%s').strip())*1000+10*60000+1000
    assert_scheduled(video['id'],snoozed['snoozeUntil']);resumed('video')
    passed('Snoozing releases focus to the video player and preserves the alarm default')
    adb('shell','am','force-stop',PROBE)
    main()
