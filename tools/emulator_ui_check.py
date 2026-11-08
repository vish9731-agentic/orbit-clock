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
parser.add_argument('--scope', choices=['full', 'alarm-list', 'ringing-motion', 'motion', 'guidance', 'visual-cue', 'notifications', 'media-focus'], default='full')
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

def stable_node(value=None, desc=None, edit=False):
    # Read-only settling: never repeat a tap against a moving sheet/IME layout.
    previous = None
    for _ in range(4):
        node = find(value, desc=desc, edit=edit)
        bounds = node.attrib['bounds']
        if bounds == previous: return node
        previous = bounds
        time.sleep(.35)
    raise AssertionError('Control did not settle: ' + str(value or desc or 'input'))

def click(value=None, desc=None): tap_xy(xy(stable_node(value, desc)))

def scroll_find(value=None, desc=None):
    for _ in range(5):
        n = find(value, desc=desc, optional=True)
        if n is not None: return n
        w, h = map(int, re.search(r'(\d+)x(\d+)', adb('shell', 'wm', 'size')).groups())
        adb('shell', 'input', 'swipe', str(w // 2), str(int(h * .73)), str(w // 2), str(int(h * .35)), '350')
        time.sleep(.3)
    raise AssertionError('Cannot scroll to ' + str(value or desc))

def fill(value, desc=None):
    node = stable_node(desc=desc) if desc else stable_node(edit=True)
    tap_xy(xy(node)); adb('shell', 'input', 'keyevent', '123')
    for _ in range(len(node.attrib.get('text', '')) + 2): adb('shell', 'input', 'keyevent', '67')
    # Pace hardware-style injection on busy hosted emulators; do not retry edits.
    for character in value:
        if character == ' ': adb('shell', 'input', 'keyevent', '62')
        else: adb('shell', 'input', 'text', character)
        time.sleep(.06)
    entered = find(desc=desc) if desc else find(edit=True)
    assert entered.attrib.get('text', '') == value, ('Injected text mismatch', value, entered.attrib.get('text'))

    for attempt in range(3):
        try:
            ime = adb('shell', 'dumpsys', 'input_method'); break
        except subprocess.CalledProcessError as error:
            if attempt == 2: raise RuntimeError('Keyboard-state probe failed: ' + str(error.stderr)[-1200:]) from error
            time.sleep(.5)
    if re.search(r'(?:mInputShown|mIsInputViewShown|isInputViewShown)=true', ime):
        adb('shell', 'input', 'keyevent', '4'); time.sleep(.8)
    stable_node(desc=desc) if desc else stable_node(edit=True)

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
    if 'No such file or directory' in xml: return {}  # An untouched SharedPreferences file is legitimately absent.
    return {n.attrib['name']: n.attrib.get('value', n.text or '') for n in ET.fromstring(xml)}

def records(): return json.loads(preferences('alarms').get('records', '[]'))

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

def open_real_ringing_timer(capture=False):
    # Use the app UI and the real AlarmManager/notification path; no exported test backdoor.
    # Bring the launcher screen itself forward instead of merely restoring its task.
    adb('shell', 'am', 'start', '-W', '-f', '0x14000000', '-n', PKG + '/dev.orbit.clock.phone.MainActivity')
    click('Timer'); click('1'); click('Start timer')
    candidates = [a for a in records() if a.get('timer') and a['enabled'] and not a['deleted']]
    alarm = max(candidates, key=lambda a: a['revision'])
    adb('shell', 'input', 'keyevent', '3')
    recorder = None
    until = time.monotonic() + 95
    while time.monotonic() < until:
        if capture and recorder is None and alarm['snoozeUntil'] - int(adb('shell', 'date', '+%s').strip()) * 1000 < 3500:
            recorder = subprocess.Popen(['adb', 'shell', 'screenrecord', '--bit-rate', '2500000', '--time-limit', '40', '/sdcard/orbit-cue.mp4'], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
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
    if recorder is not None:
        recorder.communicate(timeout=45)
        adb('pull', '/sdcard/orbit-cue.mp4', str(OUT / 'swipe-cue-internal.mp4'))
        assert int(preferences('visual_hints').get('ring_controls.shown', 0)) == 1
        passed('Finite visual swipe cue appears on the real ringing rail')
    return alarm

def slide_alarm(direction, short=False):
    thumb = find(desc='Alarm slider: swipe left to snooze, right to stop')
    x, y = xy(thumb)
    w = int(re.search(r'(\d+)x(\d+)', adb('shell', 'wm', 'size')).group(1))
    distance = int(w * (.04 if short else .36))
    adb('shell', 'input', 'swipe', str(x), str(y), str(x + direction * distance), str(y), '450')
    time.sleep(.5)

def motion_checks():
    # Inline Material captions must recover without covering the preset controls.
    click('Timer'); fill('0', desc='Timer minutes'); click('Start timer')
    find('Enter 1–1440 minutes'); assert find('Cancel timer', optional=True) is None
    screenshot('timer-inline-error-motion')
    click(desc='Timer preset: 1 min')
    assert find(desc='Timer minutes').attrib['text'] == '1'
    assert find('Enter 1–1440 minutes', optional=True) is None
    passed('Material inline error feedback clears on correction without blocking presets')
    # Static Timer -> paused Stopwatch: no ticking content can fake transition frames.
    target = xy(find('Stopwatch'))
    video = OUT / 'motion-internal.mp4'
    recorder = subprocess.Popen(['adb', 'shell', 'screenrecord', '--bit-rate', '2500000', '--time-limit', '12', '/sdcard/orbit-motion.mp4'], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
    try:
        time.sleep(.55)
        tap_xy(target)
        recorder.communicate(timeout=18)
    finally:
        if recorder.poll() is None: recorder.kill(); recorder.communicate()
    adb('pull', '/sdcard/orbit-motion.mp4', str(video))
    frame_dir = OUT / 'motion-frames'; frame_dir.mkdir(exist_ok=True)
    subprocess.run([imageio_ffmpeg.get_ffmpeg_exe(), '-y', '-loglevel', 'error', '-i', str(video), '-vf', 'fps=30,scale=270:-1', str(frame_dir / '%03d.png')], check=True, timeout=30)
    frames = [Image.open(p).convert('RGB') for p in sorted(frame_dir.glob('*.png'))]
    assert len(frames) >= 6, 'Missing motion video frames'
    # Static virtual displays can stop emitting frames once the transition settles.
    # Inspect the card region, excluding status/navigation bars and their native ripple.
    crops = [f.crop((0, int(f.height*.13), f.width, int(f.height*.83))) for f in frames]
    # Prove a real blend between source/destination, not a guest-dependent frame rate.
    # Mask only pixels that actually change between tools: input-focus fades cannot fake it.
    endpoint = ImageChops.difference(crops[0], crops[-1])
    mask = endpoint.convert('L').point(lambda value: 255 if value > 40 else 0)
    distance = sum(ImageStat.Stat(endpoint, mask=mask).mean)
    assert distance > 10, 'Recording missed the requested content change'
    intermediate = []
    for index, frame in enumerate(crops):
        from_start = sum(ImageStat.Stat(ImageChops.difference(frame, crops[0]), mask=mask).mean)
        from_end = sum(ImageStat.Stat(ImageChops.difference(frame, crops[-1]), mask=mask).mean)
        if from_start > distance * .03 and from_end > distance * .03: intermediate.append(index)
    assert intermediate, 'Transition has no visible intermediate content blend'
    assert all(sum(ImageStat.Stat(ImageChops.difference(f, crops[-1])).mean) < .8 for f in crops[-3:]), 'Transition did not settle'
    find('Start')  # The destination is the paused Stopwatch, not another Timer frame.
    first = intermediate[0]
    samples = [max(0, first-5), max(0,first-2), first, first+1, first+3, len(frames)-1]
    strip = Image.new('RGB', (frames[0].width*len(samples), frames[0].height))
    for i,index in enumerate(samples): strip.paste(frames[min(index,len(frames)-1)], (i*frames[0].width,0))
    strip.save(OUT / 'motion-strip.png')
    for path in frame_dir.glob('*.png'): path.unlink()
    frame_dir.rmdir()
    encoded = base64.b64encode((OUT / 'motion-strip.png').read_bytes()).decode()
    print('ORBIT_SCREENSHOT_BEGIN motion-strip', flush=True)
    for start in range(0,len(encoded),100): print(encoded[start:start+100], flush=True)
    print('ORBIT_SCREENSHOT_END motion-strip', flush=True)
    passed('Enabled animations include a visible intermediate content blend and settle')
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

def restart_app():
    adb('shell', 'am', 'force-stop', PKG)
    adb('shell', 'am', 'start', '-W', '-n', PKG + '/dev.orbit.clock.phone.MainActivity')
    time.sleep(.5)

def guidance_checks():
    # Visual affordances replace prose tips; exercise their real actions.
    banned = ['Tap an alarm to change its time or label.', 'Touch and hold a city to remove it.',
              'Tap a preset, or enter your own minutes.', 'Start, then tap Lap to record a split.',
              'Choose a wake-up time; bedtime follows your sleep goal.', 'Dismiss hint']
    for tab in ['Alarm', 'Clock', 'Timer', 'Stopwatch', 'Bedtime']:
        click(tab)
        xml = ET.tostring(tree(), encoding='unicode')
        assert not any(t in xml for t in banned), tab
    passed('All five tools omit explanatory hint rows and dismiss-tip controls')
    click('Clock')
    city = 'Paris' if find('Paris', optional=True) is not None else 'London'
    tap_xy(xy(scroll_find(desc='Remove ' + city)))
    find('Remove this city?'); click('Keep'); find(city)
    tap_xy(xy(scroll_find(desc='Remove ' + city))); click('Remove')
    assert find(city, optional=True) is None
    passed('Visible city remove icon supports cancel and confirmed deletion')
    click('Timer'); click(desc='Timer preset: 25 min')
    assert find(desc='Timer minutes').attrib['text'] == '25'
    passed('Visual preset guidance retains immediate selection and learning')
    # Status reports connection/request state, never invents a delivery acknowledgement.
    click(desc='Sync devices')
    until = time.monotonic() + 12
    while True:
        message = find(desc='Connection status').attrib['text']
        if message != 'Checking connection…': break
        assert time.monotonic() < until, message
        time.sleep(.3)
    assert 'synced' not in message.lower() and message
    screenshot('guidance-connection-status')
    passed('Connection feedback resolves and never claims an unconfirmed sync')
    # A canceled back gesture must keep the editor; committed back must not save a draft.
    click('Alarm'); before = {a['id'] for a in records()}
    click(desc='Add alarm'); find('New alarm')
    mode = adb('shell', 'settings', 'get', 'secure', 'navigation_mode').strip()
    if mode == '2':
        w, h = map(int, re.search(r'(\d+)x(\d+)', adb('shell', 'wm', 'size')).groups())
        for action, x in [('DOWN', 1), ('MOVE', int(w*.12)), ('MOVE', 1), ('UP', 1)]:
            adb('shell', 'input', 'motionevent', action, str(x), str(h//2)); time.sleep(.08)
        find('New alarm')
        passed('Canceled native back gesture preserves the editor draft')
    adb('shell', 'input', 'keyevent', '4'); time.sleep(.4)
    assert find('New alarm', optional=True) is None
    assert {a['id'] for a in records()} == before
    passed('Native back closes the editor without saving a draft')

def ringing_only_shimmer_checks():
    for tab in ['Alarm', 'Clock', 'Timer', 'Stopwatch', 'Bedtime']:
        click(tab)
    impressions = preferences('visual_hints')
    assert not any(k.endswith('.shown') and k != 'ring_controls.shown' for k in impressions), impressions
    passed('No shimmer is registered on any of the five main tools')

def heads_up_bounds(name):
    # Read actual banner pixels quickly; UiAutomator can outwait heads-up presentation.
    until = time.monotonic() + 8
    while time.monotonic() < until:
        path = OUT / (name + '.png')
        path.write_bytes(adb('exec-out', 'screencap', '-p', binary=True))
        im = Image.open(path).convert('RGB')
        # The dark green action rail must be in the upper part of another app.
        points = []
        for y in range(60, im.height // 2, 3):
            matches = [x for x in range(0, im.width, 3) if sum(abs(a-b) for a,b in zip(im.getpixel((x,y)), (16,58,53))) < 12]
            if len(matches) > im.width / 9: points.extend((x,y) for x in matches)
        if points:
            xs, ys = zip(*points)
            return min(xs), min(ys), max(xs), max(ys)
        time.sleep(.15)
    raise AssertionError('No actionable alarm rail appeared over the other app')

def open_heads_up_timer():
    adb('shell', 'am', 'start', '-W', '-f', '0x14000000', '-n', PKG + '/dev.orbit.clock.phone.MainActivity')
    click('Timer'); click('1'); click('Start timer')
    alarm = max([a for a in records() if a.get('timer') and a['enabled'] and not a['deleted']], key=lambda a:a['revision'])
    adb('shell', 'input', 'keyevent', '224'); adb('shell', 'wm', 'dismiss-keyguard')
    adb('shell', 'am', 'start', '-W', '-a', 'android.settings.SETTINGS')
    until = time.monotonic() + 95
    while time.monotonic() < until:
        if int(preferences('scheduled_occurrences').get(alarm['id'] + '.active', 0)) == alarm['snoozeUntil']: break
        time.sleep(.5)
    else: raise AssertionError('Timer was not delivered while another app was open')
    return alarm

def notification_checks():
    adb('shell', 'appops', 'set', PKG, 'USE_FULL_SCREEN_INTENT', 'ignore')
    first = open_heads_up_timer()
    l,t,r,b = heads_up_bounds('heads-up-other-app')
    activity = adb('shell', 'dumpsys', 'activity', 'activities')
    assert any('com.android.settings' in line for line in activity.splitlines() if 'ResumedActivity' in line or 'topResumedActivity' in line), activity[-3000:]
    passed('Custom heads-up action rail appears while Settings remains open')
    density = float(re.search(r'(\d+)', adb('shell', 'wm', 'density')).group(1)) / 160
    # Plus is 18dp from the rail end; its row is directly above the action rail.
    tap_xy((int(r - 18*density), int(t - 18*density)))
    until = time.monotonic() + 4
    while preferences('ring_selection').get(first['id'] + '.minutes') != '15':
        assert time.monotonic() < until, preferences('ring_selection')
        time.sleep(.1)
    passed('Heads-up plus adjusts occurrence snooze by five minutes without opening the app')
    before = int(adb('shell', 'date', '+%s').strip()) * 1000
    tap_xy((int(l + (r-l)*.24), int((t+b)/2)))
    after = int(adb('shell', 'date', '+%s').strip()) * 1000
    snoozed = record(first['id'])
    assert before + 15*60000 <= snoozed['snoozeUntil'] <= after + 15*60000 + 1000
    assert snoozed['snoozeMinutes'] == first['snoozeMinutes']
    assert_scheduled(first['id'], snoozed['snoozeUntil'])
    passed('Heads-up Snooze schedules the selected fifteen minutes and preserves the saved default')
    adb('shell', 'am', 'start', '-W', '-f', '0x14000000', '-n', PKG + '/dev.orbit.clock.phone.MainActivity')
    click('Timer'); tap_xy(xy(scroll_find('Cancel timer')))
    second = open_heads_up_timer()
    l,t,r,b = heads_up_bounds('heads-up-stop-other-app')
    tap_xy((int(l + (r-l)*.76), int((t+b)/2)))
    stopped = record(second['id'])
    assert not stopped['enabled'] and stopped['snoozeUntil'] == 0
    assert second['id'] + '.scheduled' not in preferences('scheduled_occurrences')
    passed('Heads-up Stop dismisses the ringing occurrence directly over another app')
    adb('shell', 'am', 'start', '-W', '-f', '0x14000000', '-n', PKG + '/dev.orbit.clock.phone.MainActivity')

def ringing_snooze_checks():
    adb('shell', 'settings', 'put', 'system', 'font_scale', '1.0')
    adb('shell', 'appops', 'set', PKG, 'USE_FULL_SCREEN_INTENT', 'allow')
    first = open_real_ringing_timer(capture=True)
    passed('Real alarm delivery opens the ringing snooze controls')
    assert find('Slide left to snooze · right to stop', optional=True) is None
    assert find('Either volume button also snoozes.', optional=True) is None
    passed('Ringing uses visual swipe guidance without instructional text')
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
    x, y = xy(find(desc='Alarm slider: swipe left to snooze, right to stop'))
    for distance in [30, -30]:
        adb('shell', 'input', 'swipe', str(x), str(y), str(x + distance), str(y), '100')
    time.sleep(.5)
    assert xy(find(desc='Alarm slider: swipe left to snooze, right to stop')) == (x, y)
    assert record(first['id'])['snoozeUntil'] == first['snoozeUntil']
    passed('Interrupted spring returns take new input without committing an alarm action')
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
    assert preferences('visual_hints').get('ring_controls.learned') == 'true'
    second = open_real_ringing_timer()
    assert find('Either volume button also snoozes.', optional=True) is None
    assert find('Slide left to snooze · right to stop', optional=True) is None
    passed('Successful touch action retires visual swipe guidance without adding text')
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
        if key == 25:
            assert find('Either volume button also snoozes.', optional=True) is None
            assert preferences('visual_hints').get('volume_snooze.learned') == 'true'
            passed('Using the hardware shortcut retires its hint on later alarms')
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
    if ARGS.scope in ['full', 'alarm-list']:
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
        if ARGS.scope == 'full':
            switch = find(desc='Morning routine enabled')
            assert switch.attrib.get('checked') == 'true'
            tap_xy(xy(switch)); find('Turn back on?'); click('Keep off')
            assert find(desc='Morning routine enabled').attrib.get('checked') == 'false'
            click(desc='Morning routine enabled'); passed('Alarm switch updates enabled state')
        adb('shell', 'am', 'force-stop', PKG)
        adb('shell', 'am', 'start', '-W', '-n', PKG + '/dev.orbit.clock.phone.MainActivity')
        find('Morning routine'); assert find(desc='Morning routine enabled').attrib.get('checked') == 'true'
        passed('Alarm survives process restart')
        screenshot('alarms-light')
        click('Morning routine'); find('Edit alarm')
        tap_xy(xy(scroll_find('Cancel'))); find('Morning routine')
        passed('Alarm card opens its editor directly without a separate Edit control')
        if ARGS.scope == 'alarm-list':
            screenshot('alarms-tap-to-edit')
            assert adb('shell', 'pidof', PKG).strip()
            crashes = adb('logcat', '-d', '-b', 'crash')
            (OUT / 'crashes.txt').write_text(crashes)
            assert not crashes.strip(), crashes
            (OUT / 'ui-checks.txt').write_text('\n'.join(checks) + '\n')
            print(f'UI RESULT: {len(checks)} alarm-list checks passed', flush=True)
            raise SystemExit(0)
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
    if ARGS.scope in ['full', 'guidance']: guidance_checks()
    if ARGS.scope in ['full', 'ringing-motion']:
        ringing_only_shimmer_checks()
        ringing_snooze_checks()
    if ARGS.scope in ['full', 'ringing-motion', 'notifications']: notification_checks()
    if ARGS.scope == 'visual-cue':
        alarm = open_real_ringing_timer(capture=True)
        assert find('Slide left to snooze · right to stop', optional=True) is None
        click('Stop'); assert not record(alarm['id'])['enabled']
        passed('Visual-only ringing cue leaves Stop immediately functional')
    if ARGS.scope == 'media-focus':
        import sys
        sys.path.insert(0,str(Path(__file__).parent/'media-focus'))
        from checks import run as run_media_focus
        run_media_focus(globals())
    if ARGS.scope not in ['guidance', 'visual-cue', 'media-focus']: motion_checks()
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
