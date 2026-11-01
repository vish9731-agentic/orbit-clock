"""Verify real Wear OS exact-alarm permission and timer delivery, including upgrades.

No exact-alarm grants are applied to the fixed APK. The separately signed test
instrumentation checks canScheduleExactAlarms under Orbit's own application UID.
"""
import argparse
import json
import re
import shlex
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

P = argparse.ArgumentParser()
P.add_argument('--apk', required=True)
P.add_argument('--baseline-apk', required=True)
P.add_argument('--probe-apk', required=True)
A = P.parse_args()
PKG = 'dev.orbit.clock'
ACTIVITY = PKG + '/dev.orbit.clock.wear.MainActivity'
OUT = Path('wear-permission-results')
OUT.mkdir(exist_ok=True)
checks = []


def adb(*args, binary=False, check=True, input=None):
    r = subprocess.run(['adb', *args], check=False, capture_output=True,
                       text=not binary, input=input, timeout=45)
    if check and r.returncode:
        print('ADB FAILURE:', args, r.stdout, r.stderr, flush=True)
        r.check_returncode()
    return r.stdout


def save(name, text):
    (OUT / name).write_text(text)


def passed(name):
    checks.append(name)
    print('WEAR PERMISSION PASS: ' + name, flush=True)


def probe(expected, name):
    result = adb('shell', 'am', 'instrument', '-w', '-r',
                 'dev.orbit.clock.permissionprobe/.AlarmPermissionProbe')
    save(name + '-permission-probe.txt', result)
    assert 'INSTRUMENTATION_CODE: -1' in result, result
    match = re.search(r'canScheduleExactAlarms=(true|false)', result)
    assert match and (match.group(1) == 'true') == expected, result


def tree():
    last = None
    for _ in range(4):
        try:
            adb('shell', 'rm', '-f', '/sdcard/orbit-permission.xml')
            adb('shell', 'uiautomator', 'dump', '/sdcard/orbit-permission.xml')
            raw = adb('shell', 'cat', '/sdcard/orbit-permission.xml')
            save('last-ui.xml', raw)
            return ET.fromstring(raw)
        except (subprocess.SubprocessError, ET.ParseError) as error:
            last = error
            time.sleep(.5)
    raise AssertionError('Cannot inspect Wear UI') from last


def screen_size():
    return tuple(map(int, re.search(r'(\d+)x(\d+)', adb('shell', 'wm', 'size')).groups()))


def node(text=None, desc=None):
    w, h = screen_size()
    for n in tree().iter('node'):
        a = n.attrib
        if not ((text is not None and a.get('text') == text) or
                (desc is not None and a.get('content-desc') == desc)):
            continue
        box = list(map(int, re.findall(r'\d+', a.get('bounds', ''))))
        if len(box) == 4:
            l, t, r, b = box
            x, y = (l+r)//2, (t+b)//2
            if r > l and b > t and 0 < x < w and 0 < y < h*.89:
                return n, (x, y)
    return None


def click(text=None, desc=None, scroll=False):
    w, h = screen_size()
    for _ in range(10 if scroll else 4):
        found = node(text, desc)
        if found:
            adb('shell', 'input', 'tap', *map(str, found[1]))
            time.sleep(.5)
            return
        if scroll:
            adb('shell', 'input', 'swipe', str(w//2), str(int(h*.78)),
                str(w//2), str(int(h*.34)), '350')
        time.sleep(.4)
    raise AssertionError('Cannot find Wear control: ' + str(text or desc))


def prefs(name):
    raw = adb('shell', 'run-as', PKG, 'cat', 'shared_prefs/' + name + '.xml', check=False)
    try:
        return {n.attrib['name']: n.attrib.get('value', n.text or '') for n in ET.fromstring(raw)}
    except ET.ParseError:
        if not raw.strip():
            return {}
        raise


def records():
    return json.loads(prefs('alarms').get('records', '[]'))


def launch():
    adb('shell', 'input', 'keyevent', '224')
    adb('shell', 'wm', 'dismiss-keyguard', check=False)
    result = adb('shell', 'am', 'start', '-W', '-f', '0x14000000', '-n', ACTIVITY)
    print('WEAR LAUNCH:', result, flush=True)
    save('launch.txt', result)
    time.sleep(1)


def capture(name):
    (OUT / (name + '.png')).write_bytes(adb('exec-out', 'screencap', '-p', binary=True))
    save(name + '-package.txt', adb('shell', 'dumpsys', 'package', PKG))
    save(name + '-alarms.txt', adb('shell', 'dumpsys', 'alarm'))
    save(name + '-services.txt', adb('shell', 'dumpsys', 'activity', 'services', PKG))
    save(name + '-crashes.txt', adb('logcat', '-d', '-b', 'crash'))


def notifications():
    # Notification permission is independent of exact-alarm authorization.
    adb('shell', 'pm', 'grant', PKG, 'android.permission.POST_NOTIFICATIONS')


def test_real_timer(name):
    launch()
    assert node('Finish alarm setup') is None, 'Watch still asks for alarm setup'
    click(desc='Choose clock tool')
    click('Timer', scroll=True)
    click(desc='Timer preset: 1 min', scroll=True)
    before = {a['id'] for a in records()}
    click('Start timer', scroll=True)
    timers = [a for a in records() if a['id'] not in before and a.get('timer') and a['enabled']]
    assert len(timers) == 1, timers
    alarm = timers[0]
    save(name + '-timer.json', json.dumps(alarm, indent=2))
    deadline = alarm['snoozeUntil']
    until = time.monotonic() + 8
    while time.monotonic() < until:
        if int(prefs('scheduled_occurrences').get(alarm['id'] + '.scheduled', 0)) == deadline:
            break
        time.sleep(.2)
    else:
        raise AssertionError('Timer not scheduled with automatic permission')
    dump = adb('shell', 'dumpsys', 'alarm')
    save(name + '-timer-scheduled.txt', dump)
    assert PKG in dump, 'No Orbit entry in real AlarmManager state'
    passed(name + ': one-minute timer scheduled through real watch UI')
    adb('shell', 'input', 'keyevent', '3')
    until = time.monotonic() + 95
    while time.monotonic() < until:
        if int(prefs('scheduled_occurrences').get(alarm['id'] + '.active', 0)) == deadline:
            break
        time.sleep(1)
    else:
        raise AssertionError('Real watch timer did not fire')
    until = time.monotonic() + 8
    while time.monotonic() < until:
        service = adb('shell', 'dumpsys', 'activity', 'services', PKG)
        if 'dev.orbit.clock.core.RingingService' in service:
            break
        time.sleep(.2)
    else:
        raise AssertionError('Timer delivered but RingingService did not run')
    crash = adb('logcat', '-d', '-b', 'crash')
    assert 'Process: ' + PKG not in crash, crash
    capture(name + '-ringing')
    passed(name + ': real AlarmManager delivery starts RingingService without an app crash')


def seed_existing_alarm():
    # A stable user record makes upgrade preservation deterministic without changing
    # production code. Timer scheduling and delivery are exercised via the UI above.
    device_seconds = int(adb('shell', 'date', '+%s').strip())
    hour, minute = map(int, adb('shell', 'date', '+%H:%M').strip().split(':'))
    alarm = dict(id='upgrade-preserved-alarm', hour=(hour + (minute+10)//60)%24,
                 minute=(minute+10)%60, label='Kept upgrade alarm', timer=False,
                 days=[], enabled=True, vibrate=True, sound=True, snoozeMinutes=10,
                 snoozeUntil=0, skipThrough=0, deleted=False,
                 revision=device_seconds*1000, writer='permission-upgrade-test')
    root = ET.Element('map')
    ET.SubElement(root, 'string', name='device').text = 'permission-upgrade-test'
    ET.SubElement(root, 'string', name='records').text = json.dumps([alarm], separators=(',', ':'))
    raw = ET.tostring(root, encoding='unicode', xml_declaration=True)
    adb('shell', 'am', 'force-stop', PKG)
    command = 'run-as ' + PKG + ' sh -c ' + shlex.quote('mkdir -p shared_prefs; cat > shared_prefs/alarms.xml')
    adb('shell', command, input=raw)
    return alarm


try:
    assert int(adb('shell', 'getprop', 'ro.build.version.sdk').strip()) == 33, 'This scenario expects Wear OS 4/API33'
    assert 'android.hardware.type.watch' in adb('shell', 'pm', 'list', 'features'), 'A real Wear OS image is required'
    adb('uninstall', PKG, check=False)
    adb('install', '-r', A.apk)
    adb('install', '-r', A.probe_apk)
    notifications()
    adb('logcat', '-c')
    probe(True, 'fresh')
    package = adb('shell', 'dumpsys', 'package', PKG)
    assert re.search(r'android.permission.USE_EXACT_ALARM:\s+granted=true', package), package
    passed('Fresh watch install automatically allows exact alarms without Settings or an appop grant')
    # Verify the upgrade independently before exercising watch UI navigation.

    adb('uninstall', PKG)
    adb('install', '-r', A.baseline_apk)
    notifications()
    adb('shell', 'appops', 'set', PKG, 'SCHEDULE_EXACT_ALARM', 'deny')
    preserved = seed_existing_alarm()
    probe(False, 'baseline-denied')
    launch()
    assert node('Finish alarm setup') is not None, 'Baseline did not expose the denied-permission problem'
    assert records() == [preserved]
    assert not prefs('scheduled_occurrences').get(preserved['id'] + '.scheduled')
    capture('baseline-denied')
    passed('Previous watch APK with denied exact-alarm access cannot schedule the saved alarm')
    adb('install', '-r', A.apk)
    probe(True, 'upgraded')
    launch()
    assert records() == [preserved], 'In-place upgrade changed saved alarm records'
    assert node('Finish alarm setup') is None, 'Fixed upgrade still requests Settings permission'
    until = time.monotonic() + 8
    while time.monotonic() < until:
        if int(prefs('scheduled_occurrences').get(preserved['id'] + '.scheduled', 0)) > 0:
            break
        time.sleep(.2)
    else:
        raise AssertionError('Preserved alarm not restored after automatic permission grant')
    capture('upgraded-preserved')
    passed('In-place update gains exact-alarm access and restores the unchanged saved alarm')
    test_real_timer('upgraded')
except Exception:
    try:
        print('WEAR FAILURE UI:', ET.tostring(tree(), encoding='unicode'), flush=True)
        print('WEAR FAILURE ACTIVITIES:', adb('shell', 'dumpsys', 'activity', 'activities'), flush=True)
        print('WEAR FAILURE LOGCAT:', adb('logcat', '-d', '-t', '200'), flush=True)
    except Exception as error:
        print('Evidence capture failed:', error, flush=True)
    raise
finally:
    capture('final')
    save('checks.json', json.dumps(checks, indent=2))
