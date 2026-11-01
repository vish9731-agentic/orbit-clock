"""Verify seven localized ringing atmospheres and real snoozes across eight hours."""
import argparse
import datetime as dt
import json
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

def adb(*args, binary=False):
    r=subprocess.run(['adb',*args],capture_output=True,timeout=40)
    if r.returncode: raise RuntimeError('ADB '+str(args)+': '+r.stdout.decode(errors='replace')+' '+r.stderr.decode(errors='replace'))
    return r.stdout if binary else r.stdout.decode(errors='replace')

def tree():
    # Window transitions can temporarily return no root. Retry only fresh reads.
    last=''
    for attempt in range(5):
        try:
            adb('shell','rm','-f','/sdcard/orbit-atmosphere.xml')
            last=adb('shell','uiautomator','dump','/sdcard/orbit-atmosphere.xml')
            return ET.fromstring(adb('exec-out','cat','/sdcard/orbit-atmosphere.xml'))
        except (RuntimeError, subprocess.SubprocessError, ET.ParseError) as error:
            if attempt==4: raise RuntimeError('No fresh UI hierarchy: '+last+' '+str(error)) from error
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
def await_alarm(alarm_id,at):
    until=time.monotonic()+55
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

def capture(name):
    path=OUT/(name+'.png')
    path.write_bytes(adb('exec-out','screencap','-p',binary=True))
    assert_rendered_controls(path)
    return path

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
    for key in ['window_animation_scale','transition_animation_scale','animator_duration_scale']:
        adb('shell','settings','put','global',key,'1')
    # Start shortly before the fixture alarm, avoiding a next-day forward jump
    # and the emulator's unrelated overdue system-job catch-up during cold boot.
    epoch=int(adb('shell','date','+%s').strip())
    today=dt.datetime.fromtimestamp(epoch,dt.timezone.utc).replace(hour=6,minute=58,second=0,microsecond=0)
    set_time(int(today.timestamp()*1000),notify=False)
    adb('shell','input','keyevent','224');adb('shell','wm','dismiss-keyguard')
    adb('shell','am','start','-W','-n',PKG+'/dev.orbit.clock.phone.MainActivity')
    before={a['id'] for a in records()}
    tap(desc='Add alarm');find('New alarm');tap(desc='Alarm sound');tap('Save alarm')
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
    set_time(at-15000)
    await_alarm(alarm['id'],at)
    if find(desc='Alarm label: Alarm',optional=True) is None:fresh_ring(alarm['id'],at)
    find(desc='Snooze duration: 10 minutes')
    day=dt.datetime.fromtimestamp(at/1000,dt.timezone.utc).replace(hour=0,minute=0,second=0,microsecond=0)
    periods=[('early-morning',6),('sunshine',8),('noon',12),('afternoon',15),('evening',18),('late-evening',21),('night',1)]
    signatures={}
    for name,hour in ([] if ARGS.skip_period_captures else periods):
        set_time(int(day.replace(hour=hour).timestamp()*1000))
        fresh_ring(alarm['id'],at)
        find(desc=f'Alarm time: {hour:02d}:00');find(desc='Alarm label: Alarm')
        time.sleep(.3)
        path=capture(f'{name}-{hour:02d}00')
        signature=background_signature(path);signatures[name]=signature
        result.append({'period':name,'time':f'{hour:02d}:00','file':path.name,
                       'peak':signature['peak'],'colored_fraction':signature['colored_fraction']})
        batch['verified_period_captures']=len(result);checkpoint()
        print('CAPTURE '+json.dumps(result[-1]),flush=True)
    for index,(name,_) in enumerate([] if ARGS.skip_period_captures else periods):
        for other,_ in periods[:index]:
            difference=visual_difference(signatures[name],signatures[other])
            assert difference>.7,('Atmospheres look identical',name,other,difference)
    if not ARGS.skip_period_captures:print('ATMOSPHERE PASS: seven distinguishable localized corner treatments',flush=True)

    # Keep the same visible window while time changes. No recreation can hide a
    # failure to refresh at runtime or after a local timezone/time notification.
    set_time(int(day.replace(hour=15).timestamp()*1000))
    find(desc='Alarm time: 15:00')
    afternoon_live=background_signature(capture('live-afternoon'))
    set_time(int(day.replace(hour=23).timestamp()*1000))
    find(desc='Alarm time: 23:00')
    night_live=background_signature(capture('live-night'))
    assert visual_difference(afternoon_live,night_live)>1.0,'Visible window did not update with current time'
    batch['live_refresh_passed']=True;checkpoint()
    print('ATMOSPHERE PASS: already-visible ringing updates from afternoon to night',flush=True)

    # Independent guest batches cover 15->19 and 19->23: four genuine hourly
    # re-rings in each guest. The saved alarm remains 07:00 throughout, so the
    # current wall clock must choose appearance rather than the stored alarm hour.
    set_time(int(day.replace(hour=ARGS.start_hour).timestamp()*1000))
    fresh_ring(alarm['id'],at);find(desc=f'Alarm time: {ARGS.start_hour:02d}:00')
    plus=find(desc='Increase snooze by 5 minutes')
    x1,y1,x2,y2=map(int,re.findall(r'\d+',plus.attrib['bounds']))
    plus_xy=((x1+x2)//2,(y1+y2)//2)
    for hour in range(ARGS.start_hour+1,ARGS.end_hour+1):
        # A new occurrence restores the saved ten-minute default. Ten deliberate
        # plus taps choose 60 minutes; actions are never retried after dispatch.
        for _ in range(10):
            adb('shell','input','tap',str(plus_xy[0]),str(plus_xy[1]));time.sleep(.08)
        find(desc='Snooze duration: 60 minutes')
        previous=at
        before=int(adb('shell','date','+%s').strip())*1000
        tap('Snooze')
        snoozed=await_record(alarm['id'],lambda a:a['skipThrough']==previous and a['snoozeUntil']>before,'Snooze did not persist')
        assert snoozed['hour']==alarm['hour'] and snoozed['minute']==alarm['minute'],snoozed
        assert snoozed['snoozeMinutes']==alarm['snoozeMinutes']==10,snoozed
        at=snoozed['snoozeUntil']
        assert before+60*60000<=at<=int(adb('shell','date','+%s').strip())*1000+60*60000+1000
        until=time.monotonic()+6
        while time.monotonic()<until and int(prefs('scheduled_occurrences').get(alarm['id']+'.scheduled',0))!=at:time.sleep(.1)
        assert int(prefs('scheduled_occurrences').get(alarm['id']+'.scheduled',0))==at,'Snooze deadline not scheduled'
        # Leave restoration time to reinstall the still-future snooze after TIME_SET.
        set_time(at-15000);await_alarm(alarm['id'],at)
        fresh_ring(alarm['id'],at)
        actual=dt.datetime.fromtimestamp(int(adb('shell','date','+%s').strip()),dt.timezone.utc)
        assert actual.hour==hour,(hour,actual)
        find(prefix=f'Alarm time: {actual.hour:02d}:')
        find(desc='Snooze duration: 10 minutes')
        entry={'hour':hour,'occurrence':at,'saved_hour':snoozed['hour'],'default_snooze':snoozed['snoozeMinutes']}
        hourly.append(entry);batch['completed_snoozes']=len(hourly);checkpoint()
        print('SNOOZE '+json.dumps(entry),flush=True)
        if hour==23:
            refreshed=background_signature(capture('snoozed-from-afternoon-to-night'))
            assert visual_difference(afternoon_live,refreshed)>1.0,'Re-ringing retained afternoon appearance'
            assert visual_difference(night_live,refreshed)<1.2,'Re-ringing did not pick current night appearance'
    assert len(hourly)==ARGS.end_hour-ARGS.start_hour
    print(f'ATMOSPHERE PASS: {len(hourly)} real hourly snoozes re-ring from {ARGS.start_hour:02d}:00 through {ARGS.end_hour:02d}:00 using current time',flush=True)
    crash=adb('logcat','-d','-b','crash');(OUT/'crashes.txt').write_text(crash)
    assert not crash.strip(),crash
    batch['crash_buffer_empty']=True;checkpoint()
    print(f'CAPTURE RESULT: {len(result)} distinct period screenshots, live time refresh, {len(hourly)} real hourly snoozes and no crashes',flush=True)
except Exception:
    checkpoint()
    # Collect non-graphics diagnostics first; a stalled compositor must not
    # prevent retaining the app/system logs needed to diagnose a failed launch.
    for name,args in [('failure-crashes.txt',('logcat','-d','-b','crash')),
                      ('failure-logcat.txt',('logcat','-d','-t','500'))]:
        try:(OUT/name).write_text(adb(*args))
        except Exception as error:print('Diagnostic capture:',name,error,flush=True)
    try:
        (OUT/'failure.xml').write_text(ET.tostring(tree(),encoding='unicode'))
        (OUT/'failure.png').write_bytes(adb('exec-out','screencap','-p',binary=True))
    except Exception as e:print('Evidence capture:',e,flush=True)
    raise
