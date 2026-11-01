"""Capture the real ringing UI at six emulator-local times; no production UI changes."""
import datetime as dt
import json
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

OUT=Path('atmosphere-screenshots'); OUT.mkdir(exist_ok=True)
PKG='dev.orbit.clock'

def adb(*args, binary=False):
    r=subprocess.run(['adb',*args],check=True,capture_output=True,timeout=40)
    return r.stdout if binary else r.stdout.decode(errors='replace')

def tree():
    adb('shell','rm','-f','/sdcard/orbit-atmosphere.xml')
    adb('shell','uiautomator','dump','/sdcard/orbit-atmosphere.xml')
    return ET.fromstring(adb('exec-out','cat','/sdcard/orbit-atmosphere.xml'))

def find(text=None,desc=None,res=None,optional=False):
    for _ in range(1 if optional else 4):
        for n in tree().iter('node'):
            a=n.attrib
            if ((text is not None and a.get('text')==text) or
                (desc is not None and a.get('content-desc')==desc) or
                (res is not None and a.get('resource-id')==res)):
                bounds=list(map(int,re.findall(r'\d+',a.get('bounds',''))))
                if len(bounds)==4 and bounds[2]>bounds[0] and bounds[3]>bounds[1]: return n
        time.sleep(.35)
    if not optional: raise AssertionError('Missing UI: '+str(text or desc or res))

def tap(text=None,desc=None,res=None):
    previous=None
    for _ in range(4):
        n=find(text,desc,res); bounds=n.attrib['bounds']
        if bounds==previous: break
        previous=bounds; time.sleep(.35)
    x1,y1,x2,y2=map(int,re.findall(r'\d+',bounds))
    adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2));time.sleep(.4)

def prefs(name):
    raw=adb('exec-out','run-as',PKG,'cat','shared_prefs/'+name+'.xml')
    if 'No such file or directory' in raw:return {}
    return {n.attrib['name']:n.attrib.get('value',n.text or '') for n in ET.fromstring(raw)}

def set_time(milliseconds):
    adb('shell','cmd','alarm','set-time',str(milliseconds))

try:
    print(adb('root'),flush=True);adb('wait-for-device')
    adb('shell','settings','put','global','auto_time','0')
    adb('shell','settings','put','global','auto_time_zone','0')
    print(adb('shell','cmd','alarm','help'),flush=True)
    adb('shell','cmd','alarm','set-timezone','Etc/UTC')
    adb('shell','settings','put','system','time_12_24','24')
    adb('shell','settings','put','system','font_scale','1.0')
    for key in ['window_animation_scale','transition_animation_scale','animator_duration_scale']:
        adb('shell','settings','put','global',key,'1')
    adb('shell','input','keyevent','224');adb('shell','wm','dismiss-keyguard')
    adb('shell','am','start','-W','-n',PKG+'/dev.orbit.clock.phone.MainActivity')
    tap(desc='Add alarm');find('New alarm');tap('Save alarm')
    alarms=json.loads(prefs('alarms')['records'])
    alarm=max([a for a in alarms if a['enabled'] and not a.get('timer') and not a['deleted']],key=lambda a:a['revision'])
    at=int(prefs('scheduled_occurrences')[alarm['id']+'.scheduled'])
    adb('shell','input','keyevent','3')
    set_time(at-15000)
    until=time.monotonic()+70
    while time.monotonic()<until:
        if int(prefs('scheduled_occurrences').get(alarm['id']+'.active',0))==at:break
        time.sleep(.5)
    else:raise AssertionError('Alarm was not delivered')
    if find(desc='Snooze duration: 10 minutes',optional=True) is None:
        adb('shell','cmd','statusbar','expand-notifications')
        tap(res=PKG+':id/ring_notification_label')
    find(desc='Snooze duration: 10 minutes')
    day=dt.datetime.fromtimestamp(at/1000,dt.timezone.utc).replace(hour=0,minute=0,second=0,microsecond=0)
    periods=[('early-morning',6),('noon',12),('afternoon',15),('evening',18),('late-evening',21),('night',1)]
    result=[]
    for name,hour in periods:
        set_time(int(day.replace(hour=hour).timestamp()*1000))
        # Native configuration recreation makes RingActivity read the new local clock/color.
        adb('shell','settings','put','system','font_scale','1.01');time.sleep(.4)
        adb('shell','settings','put','system','font_scale','1.0');time.sleep(1)
        find(desc=f'Alarm time: {hour:02d}:00');find(desc='Alarm label: Alarm')
        time.sleep(.5)
        path=OUT/f'{name}-{hour:02d}00.png'
        path.write_bytes(adb('exec-out','screencap','-p',binary=True))
        result.append({'period':name,'time':f'{hour:02d}:00','file':path.name})
        print('CAPTURE '+json.dumps(result[-1]),flush=True)
    (OUT/'captures.json').write_text(json.dumps(result,indent=2))
    crash=adb('logcat','-d','-b','crash');(OUT/'crashes.txt').write_text(crash)
    assert not crash.strip(),crash
    print('CAPTURE RESULT: six real app screenshots',flush=True)
except Exception:
    try:
        (OUT/'failure.png').write_bytes(adb('exec-out','screencap','-p',binary=True))
        (OUT/'failure.xml').write_text(ET.tostring(tree(),encoding='unicode'))
        (OUT/'failure-logcat.txt').write_text(adb('logcat','-d','-t','400'))
    except Exception as e: print('Evidence capture:',e,flush=True)
    raise
