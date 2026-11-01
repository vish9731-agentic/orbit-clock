"""Exercise the actual Material UI on a fresh phone emulator; screenshots are internal feedback."""
import base64
import argparse
import json
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path
from PIL import Image, ImageChops, ImageStat
import imageio_ffmpeg

parser = argparse.ArgumentParser()
parser.add_argument('--scope', choices=['full', 'ringing-motion'], default='full')
ARGS = parser.parse_args()

OUT = Path('emulator-results')
OUT.mkdir(exist_ok=True)
PKG = 'dev.orbit.clock'
checks = []

def adb(*args, binary=False):
    return subprocess.run(['adb', *args], check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                          timeout=35).stdout if binary else subprocess.run(
        ['adb', *args], check=True, capture_output=True, text=True, timeout=35).stdout

def tree():
    # Dumps can race a window/keyboard transition; never reuse an old hierarchy.
    last = ''
    for attempt in range(5):
        try:
            adb('shell', 'rm', '-f', '/sdcard/orbit-window.xml')
            last = adb('shell', 'uiautomator', 'dump', '/sdcard/orbit-window.xml')
            return ET.fromstring(adb('shell', 'cat', '/sdcard/orbit-window.xml'))
        except (subprocess.SubprocessError, ET.ParseError) as error:
            last += ' ' + str(getattr(error, 'stderr', '') or error)
            if attempt == 4: raise RuntimeError('No fresh UI hierarchy: ' + last[-1200:]) from error
            time.sleep(.6)

def find(value=None, desc=None, edit=False, optional=False):
    for attempt in range(1 if optional else 4):
        for node in tree().iter('node'):
            a = node.attrib
            match = (value is not None and a.get('text', '').casefold() == value.casefold()) or (desc is not None and a.get('content-desc') == desc) or (edit and 'EditText' in a.get('class', ''))
            bounds = re.findall(r'\d+', a.get('bounds', ''))
            if match and len(bounds) == 4 and int(bounds[2]) > int(bounds[0]) and int(bounds[3]) > int(bounds[1]): return node
        if not optional: time.sleep(.4)
    if optional: return None
    raise AssertionError(f'UI element not found: {value or desc or "editable field"}')

def xy(node):
    l, t, r, b = map(int, re.findall(r'\d+', node.attrib['bounds']))
    return ((l + r) // 2, (t + b) // 2)

def tap_xy(point):
    adb('shell', 'input', 'tap', str(point[0]), str(point[1])); time.sleep(.3)

def click(value=None, desc=None): tap_xy(xy(find(value, desc)))

def scroll_find(value):
    for _ in range(5):
        n = find(value, optional=True)
        if n is not None: return n
        w, h = map(int, re.search(r'(\d+)x(\d+)', adb('shell', 'wm', 'size')).groups())
        adb('shell', 'input', 'swipe', str(w // 2), str(int(h * .73)), str(w // 2), str(int(h * .35)), '350')
        time.sleep(.3)
    raise AssertionError('Cannot scroll to ' + value)

def fill(value, desc=None):
    node = find(desc=desc) if desc else find(edit=True)
    tap_xy(xy(node)); adb('shell', 'input', 'keyevent', '123')
    for _ in range(len(node.attrib.get('text', '')) + 2): adb('shell', 'input', 'keyevent', '67')
    adb('shell', 'input', 'text', value.replace(' ', '%s')); time.sleep(.3)
    for attempt in range(3):
        try:
            ime = adb('shell', 'dumpsys', 'input_method'); break
        except subprocess.CalledProcessError as error:
            if attempt == 2: raise RuntimeError('Keyboard-state probe failed: ' + str(error.stderr)[-1200:]) from error
            time.sleep(.5)
    if re.search(r'(?:mInputShown|mIsInputViewShown|isInputViewShown)=true', ime):
        adb('shell', 'input', 'keyevent', '4'); time.sleep(.3)

def passed(name):
    checks.append(name); print('UI PASS: ' + name, flush=True)
    print('::notice title=Orbit UI check::' + name, flush=True)

def screenshot(name):
    path = OUT / f'{name}.png'; path.write_bytes(adb('exec-out', 'screencap', '-p', binary=True))
    image = Image.open(path); image.thumbnail((540, 1200))
    small = OUT / f'{name}-feedback.png'; image.save(small)
    encoded = base64.b64encode(small.read_bytes()).decode()
    print('ORBIT_SCREENSHOT_BEGIN ' + name, flush=True)
    for start in range(0, len(encoded), 100): print(encoded[start:start+100], flush=True)
    print('ORBIT_SCREENSHOT_END ' + name, flush=True)

def preferences(name):
    xml = adb('exec-out', 'run-as', PKG, 'cat', 'shared_prefs/' + name + '.xml')
    return {n.attrib['name']: n.attrib.get('value', n.text or '') for n in ET.fromstring(xml)}

def records(): return json.loads(preferences('alarms')['records'])

def record(alarm_id): return next(a for a in records() if a['id'] == alarm_id)

def assert_scheduled(alarm_id, expected):
    # Returning to the main activity reinstalls alarms: its remove/install pair is transient.
    # Wait for the authoritative result; a permanently absent or wrong deadline still fails.
    until = time.monotonic() + 5
    last = None
    while time.monotonic() < until:
        last = preferences('scheduled_occurrences').get(alarm_id + '.scheduled')
        if last is not None and int(last) == expected: return
        time.sleep(.1)
    raise AssertionError(f'Expected scheduled deadline {expected}, got {last} for {alarm_id}')

def open_real_ringing_timer():
    # Use the app UI and the real AlarmManager/notification path; no exported test backdoor.
    # Bring the launcher screen itself forward instead of merely restoring its task.
    adb('shell', 'am', 'start', '-W', '-f', '0x14000000', '-n', PKG + '/dev.orbit.clock.phone.MainActivity')
    click('Timer'); click('1'); click('Start timer')
    candidates = [a for a in records() if a.get('timer') and a['enabled'] and not a['deleted']]
    alarm = max(candidates, key=lambda a: a['revision'])
    adb('shell', 'input', 'keyevent', '3')
    until = time.monotonic() + 95
    while time.monotonic() < until:
        active = int(preferences('scheduled_occurrences').get(alarm['id'] + '.active', 0))
        if active == alarm['snoozeUntil']: break
        time.sleep(1)
    else: raise AssertionError('Real timer was not delivered to the ringing service')
    time.sleep(.5)
    adb('shell', 'input', 'keyevent', '224')
    adb('shell', 'wm', 'dismiss-keyguard')
    if find(desc='Snooze duration: 10 minutes', optional=True) is None:
        adb('shell', 'cmd', 'statusbar', 'expand-notifications')
        click('Timer · 1 min')
    find(desc='Snooze duration: 10 minutes')
    return alarm

def slide_alarm(direction, short=False):
    thumb = find(desc='Alarm slider: swipe left to snooze, right to stop')
    x, y = xy(thumb)
    w = int(re.search(r'(\d+)x(\d+)', adb('shell', 'wm', 'size')).group(1))
    distance = int(w * (.04 if short else .36))
    adb('shell', 'input', 'swipe', str(x), str(y), str(x + direction * distance), str(y), '450')
    time.sleep(.5)

def motion_checks():
    # Static Timer -> paused Stopwatch: no ticking content can fake transition frames.
    click('Timer')
    target = xy(find('Stopwatch'))
    video = OUT / 'motion-internal.mp4'
    recorder = subprocess.Popen(['adb', 'shell', 'screenrecord', '--bit-rate', '2500000', '--time-limit', '3', '/sdcard/orbit-motion.mp4'], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    try:
        time.sleep(.55)
        tap_xy(target)
        recorder.communicate(timeout=8)
    finally:
        if recorder.poll() is None: recorder.kill(); recorder.communicate()
    adb('pull', '/sdcard/orbit-motion.mp4', str(video))
    frame_dir = OUT / 'motion-frames'; frame_dir.mkdir(exist_ok=True)
    subprocess.run([imageio_ffmpeg.get_ffmpeg_exe(), '-loglevel', 'error', '-i', str(video), '-vf', 'fps=30,scale=270:-1', str(frame_dir / '%03d.png')], check=True, timeout=30)
    frames = [Image.open(p).convert('RGB') for p in sorted(frame_dir.glob('*.png'))]
    assert len(frames) >= 30, 'Missing motion video frames'
    # Inspect the card region, excluding status/navigation bars and their native ripple.
    crops = [f.crop((0, int(f.height*.13), f.width, int(f.height*.83))) for f in frames]
    changes = [ImageStat.Stat(ImageChops.difference(a, b)).mean for a, b in zip(crops, crops[1:])]
    assert sum(sum(v) > .8 for v in changes) >= 4, 'Transition jumped between static screens'
    # Pick six frames around the first visible content movement for review.
    first = next(i for i,v in enumerate(changes) if sum(v) > .8)
    samples = [max(0, first-1), first+1, first+3, first+5, first+8, min(len(frames)-1, first+18)]
    strip = Image.new('RGB', (frames[0].width*len(samples), frames[0].height))
    for i,index in enumerate(samples): strip.paste(frames[min(index,len(frames)-1)], (i*frames[0].width,0))
    strip.save(OUT / 'motion-strip.png')
    for path in frame_dir.glob('*.png'): path.unlink()
    frame_dir.rmdir()
    encoded = base64.b64encode((OUT / 'motion-strip.png').read_bytes()).decode()
    print('ORBIT_SCREENSHOT_BEGIN motion-strip', flush=True)
    for start in range(0,len(encoded),100): print(encoded[start:start+100], flush=True)
    print('ORBIT_SCREENSHOT_END motion-strip', flush=True)
    passed('Enabled animations produce multiple intermediate content frames and settle')
    # Rapid tab changes must leave the last selected tool usable, with no stale callbacks.
    positions = {name: xy(find(desc=name)) for name in ['Alarm', 'Clock', 'Timer', 'Stopwatch', 'Bedtime']}
    for name in ['Alarm', 'Bedtime', 'Clock', 'Timer', 'Stopwatch', 'Timer']:
        adb('shell', 'input', 'tap', *map(str, positions[name]))
    find('Start timer'); find(desc='Timer minutes')
    passed('Rapid animated navigation settles on the final tool')
    # Reduced motion: selections, dialogs, navigation and input feedback remain usable.
    for setting in ['window_animation_scale', 'transition_animation_scale', 'animator_duration_scale']:
        adb('shell', 'settings', 'put', 'global', setting, '0')
    time.sleep(1)
    click('25'); assert find(desc='Timer minutes').attrib['text'] == '25'
    click('1'); assert find(desc='Timer minutes').attrib['text'] == '1'
    click('Alarm'); click(desc='Add alarm'); find('New alarm'); tap_xy(xy(scroll_find('Cancel')))
    click('Stopwatch')
    start_position = xy(find('Start')); reset_position = xy(find('Reset'))
    # A live stopwatch deliberately changes too often for UiAutomator's idle wait.
    tap_xy(start_position); time.sleep(.3); tap_xy(start_position)
    find('Start'); tap_xy(reset_position); find('00:00.00')
    passed('Animations-off mode keeps presets, editor and stopwatch actions responsive')
    for setting in ['window_animation_scale', 'transition_animation_scale', 'animator_duration_scale']:
        adb('shell', 'settings', 'put', 'global', setting, '1')

def ringing_snooze_checks():
    adb('shell', 'settings', 'put', 'system', 'font_scale', '1.0')
    adb('shell', 'appops', 'set', PKG, 'USE_FULL_SCREEN_INTENT', 'allow')
    first = open_real_ringing_timer()
    passed('Real alarm delivery opens the ringing snooze controls')
    find(desc='Alarm label: Timer · 1 min')
    find(desc='Alarm slider: swipe left to snooze, right to stop')
    adb('shell', 'cmd', 'uimode', 'night', 'no'); time.sleep(1)
    find('Snooze'); find('Stop'); screenshot('ringing-reference-light-setting')
    light = Image.open(OUT / 'ringing-reference-light-setting.png').convert('RGB').getpixel((10, 200))
    adb('shell', 'cmd', 'uimode', 'night', 'yes'); time.sleep(1)
    screenshot('ringing-reference-dark-setting')
    dark = Image.open(OUT / 'ringing-reference-dark-setting.png').convert('RGB').getpixel((10, 200))
    assert light == dark and sum(dark) < 150, (light, dark)
    passed('Reference ringing screen keeps its dark green background in both system themes')
    adb('shell', 'settings', 'put', 'system', 'font_scale', '1.3'); time.sleep(1)
    nodes = [find('Snooze'), find('Stop'), find(desc='Decrease snooze by 5 minutes'), find(desc='Increase snooze by 5 minutes')]
    w, h = map(int, re.search(r'(\d+)x(\d+)', adb('shell', 'wm', 'size')).groups())
    for node in nodes:
        l, t, r, b = map(int, re.findall(r'\d+', node.attrib['bounds']))
        assert 0 <= l < r <= w and 0 <= t < b <= h, node.attrib
        assert node.attrib['clickable'] == 'true'
    snooze_bounds, dismiss_bounds = [list(map(int, re.findall(r'\d+', n.attrib['bounds']))) for n in nodes[:2]]
    assert snooze_bounds[2] <= dismiss_bounds[0]
    screenshot('ringing-reference-large-type')
    passed('Ringing actions remain visible and separate at 130 percent font scale')
    adb('shell', 'settings', 'put', 'system', 'font_scale', '1.0'); time.sleep(1)
    minus = xy(find(desc='Decrease snooze by 5 minutes'))
    plus = xy(find(desc='Increase snooze by 5 minutes'))
    for point in [plus, plus, plus, minus, minus, minus]:
        adb('shell', 'input', 'tap', *map(str, point))
    find(desc='Snooze duration: 10 minutes')
    passed('Rapid duration changes keep the exact value while feedback is interrupted')
    tap_xy(minus); find(desc='Snooze duration: 5 minutes')
    assert find(desc='Decrease snooze by 5 minutes').attrib.get('enabled') == 'false'
    tap_xy(minus); find(desc='Snooze duration: 5 minutes')
    passed('Ringing snooze minimum is five with minus disabled')
    for _ in range(11): tap_xy(plus)
    find(desc='Snooze duration: 60 minutes')
    assert find(desc='Increase snooze by 5 minutes').attrib.get('enabled') == 'false'
    tap_xy(plus); find(desc='Snooze duration: 60 minutes')
    passed('Five-minute increases stop at sixty with plus disabled')
    screenshot('ringing-snooze-max')
    for _ in range(7): tap_xy(minus)
    find(desc='Snooze duration: 25 minutes')
    current = record(first['id'])
    assert current['snoozeUntil'] == first['snoozeUntil'] and current['skipThrough'] == 0
    assert current['snoozeMinutes'] == first['snoozeMinutes']
    passed('Changing ringing duration does not edit or reschedule the alarm')
    adb('shell', 'settings', 'put', 'system', 'font_scale', '1.1'); time.sleep(1)
    find(desc='Snooze duration: 25 minutes')
    adb('shell', 'settings', 'put', 'system', 'font_scale', '1.0'); time.sleep(1)
    find(desc='Snooze duration: 25 minutes')
    passed('Selected snooze duration survives activity recreation')
    screenshot('ringing-snooze-selected')
    slide_alarm(-1, short=True)
    assert record(first['id'])['snoozeUntil'] == first['snoozeUntil']
    find(desc='Snooze duration: 25 minutes')
    passed('Short slider drag resets without snoozing or stopping')
    center_before = xy(find(desc='Alarm slider: swipe left to snooze, right to stop'))
    adb('shell', 'settings', 'put', 'global', 'animator_duration_scale', '0'); time.sleep(.5)
    slide_alarm(1, short=True)
    assert xy(find(desc='Alarm slider: swipe left to snooze, right to stop')) == center_before
    assert record(first['id'])['snoozeUntil'] == first['snoozeUntil']
    adb('shell', 'settings', 'put', 'global', 'animator_duration_scale', '1'); time.sleep(.5)
    passed('Reduced-motion slider cancellation restores the centered control without action')
    before = int(adb('shell', 'date', '+%s').strip()) * 1000
    slide_alarm(-1)
    after = int(adb('shell', 'date', '+%s').strip()) * 1000
    snoozed = record(first['id'])
    assert before + 25 * 60000 <= snoozed['snoozeUntil'] <= after + 25 * 60000 + 1000
    assert snoozed['snoozeMinutes'] == first['snoozeMinutes']
    assert_scheduled(first['id'], snoozed['snoozeUntil'])
    passed('Snooze actually schedules the selected twenty-five minutes')
    second = open_real_ringing_timer()
    click(desc='Increase snooze by 5 minutes'); find(desc='Snooze duration: 15 minutes')
    slide_alarm(1)
    dismissed = record(second['id'])
    assert not dismissed['enabled'] and dismissed['snoozeUntil'] == 0
    assert second['id'] + '.scheduled' not in preferences('scheduled_occurrences')
    assert_scheduled(first['id'], snoozed['snoozeUntil'])
    passed('Dismiss ignores duration and leaves another snoozed alarm intact')
    # Multiple out-of-phase countdowns can prevent UiAutomator from reaching idle.
    # Remove the already verified fixture before inspecting the next occurrence.
    adb('shell', 'am', 'start', '-W', '-f', '0x14000000', '-n', PKG + '/dev.orbit.clock.phone.MainActivity')
    click('Timer'); tap_xy(xy(scroll_find('Cancel timer')))
    for key, target, hold in [(24, 15, True), (25, 5, False)]:
        if key == 25:
            adb('shell', 'settings', 'put', 'global', 'animator_duration_scale', '0'); time.sleep(.5)
        volume_alarm = open_real_ringing_timer()
        click(desc='Increase snooze by 5 minutes' if target > 10 else 'Decrease snooze by 5 minutes')
        find(desc=f'Snooze duration: {target} minutes')
        before = int(adb('shell', 'date', '+%s').strip()) * 1000
        if hold: adb('shell', 'input', 'keyevent', '--longpress', str(key))
        else: adb('shell', 'input', 'keyevent', str(key))
        after = int(adb('shell', 'date', '+%s').strip()) * 1000
        snoozed_by_key = record(volume_alarm['id'])
        assert before + target * 60000 <= snoozed_by_key['snoozeUntil'] <= after + target * 60000 + 1000
        assert snoozed_by_key['enabled'] and snoozed_by_key['skipThrough'] == volume_alarm['snoozeUntil']
        assert snoozed_by_key['snoozeMinutes'] == volume_alarm['snoozeMinutes']
        assert_scheduled(volume_alarm['id'], snoozed_by_key['snoozeUntil'])
        time.sleep(1)
        assert find(desc=f'Snooze duration: {target} minutes', optional=True) is None, 'Volume snooze did not close ringing screen'
        passed(('Held volume up' if key == 24 else 'Volume down with animations off') + ' snoozes for the selected duration')
        if key == 25: adb('shell', 'settings', 'put', 'global', 'animator_duration_scale', '1')
        adb('shell', 'am', 'start', '-W', '-f', '0x14000000', '-n', PKG + '/dev.orbit.clock.phone.MainActivity')
        click('Timer'); tap_xy(xy(scroll_find('Cancel timer')))

try:
    for setting in ['window_animation_scale', 'transition_animation_scale', 'animator_duration_scale']:
        adb('shell', 'settings', 'put', 'global', setting, '1')
    time.sleep(1)
    adb('shell', 'cmd', 'uimode', 'night', 'no')
    adb('shell', 'settings', 'put', 'system', 'font_scale', '1.0')
    adb('shell', 'settings', 'put', 'secure', 'show_ime_with_hard_keyboard', '1')
    adb('logcat', '-c')
    adb('shell', 'am', 'start', '-W', '-n', PKG + '/dev.orbit.clock.phone.MainActivity')
    if ARGS.scope == 'full':
        find('No alarms yet'); passed('Material alarm empty state')
        screenshot('alarms-empty-light')
        click(desc='Add alarm'); find('New alarm')
        click(desc='Set alarm time'); find('Set alarm time'); click('OK')
        passed('Material time picker opens and confirms')
        fill('Morning routine')
        for day in ['Monday', 'Wednesday', 'Friday']: click(desc=day)
        screenshot('alarm-editor-light')
        tap_xy(xy(scroll_find('Save alarm'))); time.sleep(.5)
        find('Morning routine'); passed('Create labeled repeating alarm')
        switch = find(desc='Morning routine enabled')
        assert switch.attrib.get('checked') == 'true'
        tap_xy(xy(switch)); assert find(desc='Morning routine enabled').attrib.get('checked') == 'false'
        click(desc='Morning routine enabled'); passed('Alarm switch updates enabled state')
        adb('shell', 'am', 'force-stop', PKG)
        adb('shell', 'am', 'start', '-W', '-n', PKG + '/dev.orbit.clock.phone.MainActivity')
        find('Morning routine'); assert find(desc='Morning routine enabled').attrib.get('checked') == 'true'
        passed('Alarm survives process restart')
        screenshot('alarms-light')
        click('Clock'); find('London'); passed('World clocks navigation')
        click(desc='Add city'); fill('Paris'); click('Europe · Paris')
        scroll_find('Paris'); passed('Search and add a world clock')
        screenshot('clocks-light')
        click('Timer'); click('25'); assert find(desc='Timer preset: 25 min').attrib['selected'] == 'true'
        click('1'); assert find(desc='Timer preset: 1 min').attrib['selected'] == 'true'
        passed('Timer preset selection follows the animated value change')
        fill('0', desc='Timer minutes'); click('Start timer')
        assert not any(a.get('timer') and a['enabled'] and not a['deleted'] for a in records())
        assert find(desc='Timer minutes').attrib['text'] == '0'
        screenshot('timer-validation-motion')
        click('1'); assert find(desc='Timer minutes').attrib['text'] == '1'
        passed('Invalid timer feedback leaves input editable and recovers with a preset')
        click('Start timer'); time.sleep(.4)
        scroll_find('Timer · 1 min'); passed('Create a timer through Material controls')
        screenshot('timer-light')
        tap_xy(xy(scroll_find('Cancel timer')))
        assert find('Timer · 1 min', optional=True) is None
        passed('Cancel timer removes it')
        click('Stopwatch'); start = xy(find('Start')); lap = xy(find('Reset'))
        tap_xy(start); time.sleep(.6); tap_xy(lap); tap_xy(start)
        find('Start'); find('Lap 1')
        assert not any(n.attrib.get('text') == '00:00.00' for n in tree().iter('node'))
        passed('Stopwatch start, lap and pause')
        screenshot('stopwatch-light')
        click('Reset'); find('00:00.00'); passed('Stopwatch reset')
        click('Bedtime'); click('8 hours'); click('9 hours'); find('9 hours of sleep')
        passed('Sleep-goal dialog and updated summary')
        screenshot('bedtime-light')
        tap_xy(xy(scroll_find('Set wake-up alarm'))); tap_xy(xy(scroll_find('Save alarm')))
        passed('Wake-up schedule uses redesigned editor')
        click('Alarm'); find('Morning routine')
        adb('shell', 'cmd', 'uimode', 'night', 'yes'); time.sleep(2)
        find('Morning routine'); passed('Dark theme preserves alarm state')
        screenshot('alarms-dark')
        adb('shell', 'settings', 'put', 'system', 'font_scale', '1.3'); time.sleep(2)
        click(desc='Add alarm'); find('New alarm'); tap_xy(xy(scroll_find('Cancel')))
        passed('Alarm editor usable at 130 percent font scale')
        time.sleep(1.2)
        find('Alarms'); find('Stopwatch')
        passed('Header and navigation remain visible with large text')
        screenshot('alarms-large-type-dark')
    ringing_snooze_checks()
    motion_checks()
    pid = adb('shell', 'pidof', PKG).strip(); assert pid
    crashes = adb('logcat', '-d', '-b', 'crash'); (OUT / 'crashes.txt').write_text(crashes)
    assert not crashes.strip(), crashes
    passed('App remains alive without crashes')
    (OUT / 'ui-checks.txt').write_text('\n'.join(checks) + '\n')
    print(f'UI RESULT: {len(checks)} scenario checks passed', flush=True)
except Exception:
    try:
        hierarchy = ET.tostring(tree(), encoding='unicode')
        (OUT / 'failure-hierarchy.xml').write_text(hierarchy)
        print('UI_FAILURE_HIERARCHY ' + hierarchy, flush=True)
        (OUT / 'failure-logcat.txt').write_text(adb('logcat', '-d', '-t', '600'))
        screenshot('failure')
    except Exception as error: print('Evidence capture issue:', str(error), flush=True)
    raise
finally:
    adb('shell', 'settings', 'put', 'system', 'font_scale', '1.0')
    for setting in ['window_animation_scale', 'transition_animation_scale', 'animator_duration_scale']:
        adb('shell', 'settings', 'put', 'global', setting, '1')
