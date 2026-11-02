"""Verify seven localized ringing atmospheres and real snoozes across eight hours."""
import argparse
import datetime as dt
import json
import io
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from PIL import Image

parser=argparse.ArgumentParser()
parser.add_argument('--start-hour',type=int,default=15)
parser.add_argument('--end-hour',type=int,default=19)
parser.add_argument('--skip-period-captures',action='store_true')
ARGS=parser.parse_args()
assert 0<=ARGS.start_hour<ARGS.end_hour<=23,'Expected a same-day increasing hour range'
OUT=Path('atmosphere-screenshots'); OUT.mkdir(exist_ok=True)
PKG='dev.orbit.clock'
result=[];hourly=[]
batch={'start_hour':ARGS.start_hour,'end_hour':ARGS.end_hour,
       'expected_snoozes':ARGS.end_hour-ARGS.start_hour,'completed_snoozes':0,
       'verified_period_captures':0,'live_refresh_passed':False,'crash_buffer_empty':None}
def checkpoint():
    # Preserve completed milestones even if the disposable guest later exits.
    (OUT/'captures.json').write_text(json.dumps(result,indent=2))
    (OUT/'hourly-snoozes.json').write_text(json.dumps(hourly,indent=2))
    (OUT/'batch-summary.json').write_text(json.dumps(batch,indent=2))
checkpoint()

def adb(*args, binary=False, timeout=40):
    r=subprocess.run(['adb',*args],capture_output=True,timeout=timeout)
    if r.returncode: raise RuntimeError('ADB '+str(args)+': '+r.stdout.decode(errors='replace')+' '+r.stderr.decode(errors='replace'))
    return r.stdout if binary else r.stdout.decode(errors='replace')

def tree(attempts=5,timeout=40):
    # Window transitions can temporarily return no root. Retry only fresh reads.
    last=''
    for attempt in range(attempts):
        try:
            adb('shell','rm','-f','/sdcard/orbit-atmosphere.xml',timeout=timeout)
            last=adb('shell','uiautomator','dump','/sdcard/orbit-atmosphere.xml',timeout=timeout)
            return ET.fromstring(adb('exec-out','cat','/sdcard/orbit-atmosphere.xml',timeout=timeout))
        except (RuntimeError, subprocess.SubprocessError, ET.ParseError) as error:
            if attempt==attempts-1: raise RuntimeError('No fresh UI hierarchy: '+last+' '+str(error)) from error
            time.sleep(.6)

def find(text=None,desc=None,res=None,optional=False,prefix=None):
    for _ in range(1 if optional else 4):
        for n in tree().iter('node'):
            a=n.attrib
            if ((text is not None and a.get('text')==text) or
                (desc is not None and a.get('content-desc')==desc) or
                (res is not None and a.get('resource-id')==res) or
                (prefix is not None and a.get('content-desc','').startswith(prefix))):
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
    else:raise AssertionError('UI bounds did not settle: '+str(text or desc or res))
    x1,y1,x2,y2=map(int,re.findall(r'\d+',bounds))
    adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2));time.sleep(.4)

def await_main_ready():
    until=time.monotonic()+35;waited=False;last=[]
    while time.monotonic()<until:
        try:doc=tree(attempts=1,timeout=8)
        except (RuntimeError,subprocess.SubprocessError):time.sleep(.3);continue
        nodes=list(doc.iter('node'));last=[n.attrib.get('text','') for n in nodes]
        # Only unrelated, explicitly identified system/launcher ANRs may be waited
        # through once. An Orbit ANR is a real failure and must never be dismissed.
        for text in last:
            lower=text.casefold()
            if 'orbit clock' in lower and 'responding' in lower:raise AssertionError('Orbit ANR: '+text)
        system_anr=any('responding' in text.casefold() and any(name in text.casefold()
                      for name in ['system ui','systemui','pixel launcher']) for text in last)
        if system_anr:
            assert not waited,('Repeated unrelated system ANR',last)
            button=next((n for n in nodes if n.attrib.get('resource-id')=='android:id/aerr_wait'
                         or n.attrib.get('text')=='Wait'),None)
            assert button is not None,('System ANR has no Wait action',last)
            bounds=list(map(int,re.findall(r'\d+',button.attrib.get('bounds',''))))
            assert len(bounds)==4
            x1,y1,x2,y2=bounds
            adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2),timeout=8)
            waited=True;print('READINESS: waited once for unrelated SystemUI/Pixel Launcher ANR',flush=True)
        elif any(n.attrib.get('content-desc')=='Add alarm' for n in nodes):return
        time.sleep(.3)
    raise AssertionError('Main screen did not become ready: '+str(last))

def prefs(name):
    last=''
    for attempt in range(12):
        try:
            raw=adb('exec-out','run-as',PKG,'cat','shared_prefs/'+name+'.xml')
            if 'No such file or directory' in raw:return {}
            return {n.attrib['name']:n.attrib.get('value',n.text or '') for n in ET.fromstring(raw)}
        except (RuntimeError,ET.ParseError) as error:
            last=str(error)
            if isinstance(error,RuntimeError):
                if 'No such file or directory' in last:return {}
                raise
            time.sleep(.08)
    raise AssertionError('No complete preferences snapshot: '+name+' '+last)

def records():return json.loads(prefs('alarms').get('records','[]'))
def record(alarm_id):return next(a for a in records() if a['id']==alarm_id)
def await_record(alarm_id,predicate,label):
    until=time.monotonic()+8
    while time.monotonic()<until:
        a=record(alarm_id)
        if predicate(a):return a
        time.sleep(.15)
    raise AssertionError(label+': '+str(a))
def await_alarm(alarm_id,at,timeout=55):
    until=time.monotonic()+timeout
    while time.monotonic()<until:
        if int(prefs('scheduled_occurrences').get(alarm_id+'.active',0))==at:return
        time.sleep(.3)
    (OUT/'alarm-debug.json').write_text(json.dumps({'expected':at,'state':prefs('scheduled_occurrences'),'records':records()},indent=2))
    (OUT/'alarm-manager.txt').write_text(adb('shell','dumpsys','alarm'))
    raise AssertionError('Alarm was not delivered: '+str(at))

def fresh_ring(alarm_id,at):
    # Rooted disposable device only: every launch still requires a valid occurrence.
    adb('shell','am','start','-f','0x10008000','-n',PKG+'/dev.orbit.clock.core.RingActivity',
        '--es','id',alarm_id,'--el','at',str(at))
    time.sleep(.5)
    find(desc='Alarm label: Alarm')

def heads_up_fraction(im):
    # The pinned API33 fixture uses light SystemUI. Its notification card is a
    # wide, near-white neutral patch; the app's localized glows never approach
    # this neutral brightness over a broad region. Stars/moon are only pinpoints.
    w,h=im.size
    points=[im.getpixel((int(w*x/100),int(h*y/100)))
            for y in range(4,21,2) for x in range(4,97,4)]
    return sum(min(p)>210 and max(p)-min(p)<24 for p in points)/len(points)

def capture(name):
    path=OUT/(name+'.png');until=time.monotonic()+20;reported=False
    while True:
        raw=adb('exec-out','screencap','-p',binary=True)
        im=Image.open(io.BytesIO(raw)).convert('RGB')
        fraction=heads_up_fraction(im)
        if fraction<.06:
            path.write_bytes(raw);assert_rendered_controls(path);return path
        if not reported:
            # UiAutomator may return the underlying app even while SystemUI's
            # heads-up is visible, so absence of notification nodes is insufficient.
            print('CAPTURE WAIT '+json.dumps({'file':path.name,'system_card_fraction':round(fraction,3)}),flush=True)
            reported=True
        if time.monotonic()>=until:
            path.write_bytes(raw)
            try:(OUT/(name+'-overlay.xml')).write_text(ET.tostring(tree(attempts=1,timeout=8),encoding='unicode'))
            except Exception as error:print('Overlay hierarchy:',error,flush=True)
            raise AssertionError('SystemUI heads-up still obscures the atmosphere after 20 seconds: '+str(path))
        # Observe its natural timeout. Do not dismiss the alarm or its notification.
        time.sleep(.5)

def assert_rendered_controls(path):
    # Semantic nodes alone cannot detect dropped text drawing. On this fixed CI
    # phone viewport, verify actual clock glyphs, label, stepper and both actions.
    im=Image.open(path).convert('RGB');w,h=im.size
    def bright(x1,y1,x2,y2):
        return sum(min(im.getpixel((x,y)))>100
                   for y in range(int(h*y1),int(h*y2))
                   for x in range(int(w*x1),int(w*x2)))
    columns=[any(min(im.getpixel((x,y)))>110 for y in range(int(h*.40),int(h*.57)))
             for x in range(int(w*.05),int(w*.95))]
    groups=[];start=None
    for i,present in enumerate(columns+[False]):
        if present and start is None:start=i
        if not present and start is not None:
            if i-start>2:groups.append(i-start)
            start=None
    assert len(groups)==5,('Rendered clock must contain four digits and colon',path,groups)
    checks=[('alarm label',bright(.30,.57,.70,.65),200),
            ('minus',bright(.25,.66,.38,.72),15),
            ('plus',bright(.62,.66,.75,.72),30),
            ('Snooze',bright(.08,.73,.36,.84),200),
            ('Stop',bright(.72,.73,.90,.84),150)]
    for name,pixels,minimum in checks:assert pixels>=minimum,('Rendered control missing',path,name,pixels)
    print('RENDER '+json.dumps({'file':path.name,'clock_glyphs':len(groups),
                               'control_pixels':{name:pixels for name,pixels,_ in checks}}),flush=True)

def background_signature(path):
    # Before the clock text: sample the actual rendered corner artwork, avoiding
    # status/navigation chrome. Bright source colors are permitted only locally.
    im=Image.open(path).convert('RGB');w,h=im.size
    points=[im.getpixel((int(w*x/100),int(h*y/100))) for y in range(3,35,2) for x in range(3,98,2)]
    peak=max(max(p) for p in points)
    visible=sum(max(p)>=24 and max(p)-min(p)>=10 for p in points)/len(points)
    center=[im.getpixel((int(w*x/100),int(h*y/100))) for y in range(38,80,2) for x in [2,4,96,98]]
    assert peak>=28,('Corner glow is imperceptible',path,peak)
    assert .008<=visible<=.48,('Glow must remain localized',path,visible)
    assert sum(max(p)<=15 for p in center)/len(center)>.85,('Main field must remain black',path)
    return {'peak':peak,'colored_fraction':round(visible,3),'samples':points}

def visual_difference(a,b):
    return sum(sum(abs(x-y) for x,y in zip(p,q)) for p,q in zip(a['samples'],b['samples']))/(len(a['samples'])*3)


def set_time(milliseconds,notify=True):
    # Disposable rooted API33 emulator: IAlarmManager.aidl transaction2 is setTime.
    # Use the framework clock path so the kernel alarm driver rebatches RTC alarms
    # and Android sends its real TIME_SET broadcast. Changing toybox date alone
    # can leave previously-converted elapsed deadlines unchanged on this image.
    result=adb('shell','service','call','alarm','2','i64',str(milliseconds))
    assert '00000001' in result,('Framework clock update failed',result)
    actual=int(adb('shell','date','+%s').strip())
    assert abs(actual-milliseconds//1000)<8,('Clock did not change',actual,milliseconds//1000)
    print('CLOCK '+dt.datetime.fromtimestamp(actual,dt.timezone.utc).isoformat(),flush=True)

try:
    print(adb('root'),flush=True);adb('wait-for-device')
    assert adb('shell','getprop','ro.build.version.sdk').strip()=='33','Framework clock transaction is pinned to the API33 AIDL contract'
    adb('shell','settings','put','global','auto_time','0')
    adb('shell','settings','put','global','auto_time_zone','0')
    adb('shell','setprop','persist.sys.timezone','Etc/UTC')
    adb('shell','settings','put','system','time_12_24','24')
    adb('shell','settings','put','system','font_scale','1.0')
    adb('shell','cmd','uimode','night','no')
    for key in ['window_animation_scale','transition_animation_scale','animator_duration_scale']:
        adb('shell','settings','put','global',key,'1')
    # Start shortly before the fixture alarm, avoiding a next-day forward jump
    # and the emulator's unrelated overdue system-job catch-up during cold boot.
    epoch=int(adb('shell','date','+%s').strip())
    today=dt.datetime.fromtimestamp(epoch,dt.timezone.utc).replace(hour=6,minute=58,second=0,microsecond=0)
    set_time(int(today.timestamp()*1000),notify=False)
    adb('shell','input','keyevent','224');adb('shell','wm','dismiss-keyguard')
    adb('shell','am','start','-n',PKG+'/dev.orbit.clock.phone.MainActivity')
    await_main_ready()
    before={a['id'] for a in records()}
    tap(desc='Add alarm');find('New alarm');tap(desc='Alarm sound');tap(desc='Vibration');tap('Save alarm')
    until=time.monotonic()+8;alarm=None
    while time.monotonic()<until:
        added=[a for a in records() if a['id'] not in before and a['enabled'] and not a.get('timer') and not a['deleted']]
        if added:alarm=added[0];break
        time.sleep(.15)
    assert alarm is not None,'Saved alarm did not persist'
    until=time.monotonic()+6;at=0
    while time.monotonic()<until:
        at=int(prefs('scheduled_occurrences').get(alarm['id']+'.scheduled',0))
        if at:break
        time.sleep(.1)
    assert at,'Saved alarm was not scheduled'
    # Let the first cold-start alarm fire naturally, without a nearby TIME_SET
    # restoration competing with initial app and system startup.
    assert not alarm['sound'] and not alarm['vibrate'],'Visual fixture must not use emulated audio/haptics'
    await_alarm(alarm['id'],at,timeout=180)
    if find(desc='Alarm label: Alarm',optional=True) is None:fresh_ring(alarm['id'],at)
    find(desc='Snooze duration: 10 minutes')

    for index, manual in enumerate([False, True, False]):
        tap(desc='Decrease snooze by 5 minutes')
        find(desc='Snooze duration: 5 minutes')
        previous=at
        observed=time.monotonic()
        if manual:tap('Snooze')
        until=time.monotonic()+45
        changed=None
        while time.monotonic()<until:
            candidate=record(alarm['id'])
            if candidate['skipThrough']==previous and candidate['snoozeUntil']>previous:
                changed=candidate;break
            time.sleep(.2)
        assert changed is not None,('No automatic/manual snooze',index,record(alarm['id']))
        expected=(previous//60000)*60000+300000
        assert changed['snoozeUntil']==expected,(index,expected,changed)
        assert changed['snoozeMinutes']==10,'Saved default was changed'
        assert int(prefs('scheduled_occurrences').get(alarm['id']+'.active',0))!=previous
        print('ALIGNED SNOOZE PASS',index,'manual' if manual else 'automatic',
              'deadline',expected,'observed_wait',time.monotonic()-observed,flush=True)
        at=expected
        if index<2:
            set_time(at-15000);await_alarm(alarm['id'],at)
            fresh_ring(alarm['id'],at)
            find(desc='Snooze duration: 10 minutes')
    crash=adb('logcat','-d','-b','crash')
    assert not crash.strip(),crash
    print('AUTO SNOOZE RESULT: two automatic snoozes, manual snooze, real re-rings, aligned five-minute deadlines, unchanged defaults, no crashes',flush=True)
except Exception:
    for name,args in [('failure-logcat.txt',('logcat','-d','-t','500')),('failure-crashes.txt',('logcat','-d','-b','crash'))]:
        try:(OUT/name).write_text(adb(*args))
        except Exception:pass
    raise
