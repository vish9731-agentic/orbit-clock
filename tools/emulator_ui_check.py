"""Exercise the actual Material UI on a fresh phone emulator; screenshots are internal feedback."""
import base64
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path
from PIL import Image

OUT = Path('emulator-results')
OUT.mkdir(exist_ok=True)
PKG = 'dev.orbit.clock'
checks = []

def adb(*args, binary=False):
    return subprocess.run(['adb', *args], check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                          timeout=35).stdout if binary else subprocess.run(
        ['adb', *args], check=True, capture_output=True, text=True, timeout=35).stdout

def tree():
    for attempt in range(3):
        try:
            adb('shell', 'uiautomator', 'dump', '/sdcard/orbit-window.xml')
            return ET.fromstring(adb('shell', 'cat', '/sdcard/orbit-window.xml'))
        except (subprocess.SubprocessError, ET.ParseError):
            if attempt == 2: raise
            time.sleep(.5)

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
    ime = adb('shell', 'dumpsys', 'input_method')
    if re.search(r'(?:mInputShown|mIsInputViewShown|isInputViewShown)=true', ime):
        adb('shell', 'input', 'keyevent', '4'); time.sleep(.3)

def passed(name):
    checks.append(name); print('UI PASS: ' + name, flush=True)

def screenshot(name):
    path = OUT / f'{name}.png'; path.write_bytes(adb('exec-out', 'screencap', '-p', binary=True))
    image = Image.open(path); image.thumbnail((540, 1200))
    small = OUT / f'{name}-feedback.png'; image.save(small)
    encoded = base64.b64encode(small.read_bytes()).decode()
    print('ORBIT_SCREENSHOT_BEGIN ' + name, flush=True)
    for start in range(0, len(encoded), 100): print(encoded[start:start+100], flush=True)
    print('ORBIT_SCREENSHOT_END ' + name, flush=True)

try:
    adb('shell', 'cmd', 'uimode', 'night', 'no')
    adb('shell', 'settings', 'put', 'system', 'font_scale', '1.0')
    adb('shell', 'settings', 'put', 'secure', 'show_ime_with_hard_keyboard', '1')
    adb('logcat', '-c')
    adb('shell', 'am', 'start', '-W', '-n', PKG + '/dev.orbit.clock.phone.MainActivity')
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
    click('Timer'); click('1'); click('Start timer'); time.sleep(.4)
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
    tap_xy(xy(scroll_find('Set wake-up alarm'))); tap_xy(xy(scroll_find('Save alarm')))
    passed('Wake-up schedule uses redesigned editor')
    click('Alarm'); find('Morning routine')
    adb('shell', 'cmd', 'uimode', 'night', 'yes'); time.sleep(2)
    find('Morning routine'); passed('Dark theme preserves alarm state')
    screenshot('alarms-dark')
    adb('shell', 'settings', 'put', 'system', 'font_scale', '1.3'); time.sleep(2)
    click(desc='Add alarm'); find('New alarm'); tap_xy(xy(scroll_find('Cancel')))
    passed('Alarm editor usable at 130 percent font scale')
    screenshot('alarms-large-type-dark')
    pid = adb('shell', 'pidof', PKG).strip(); assert pid
    crashes = adb('logcat', '-d', '-b', 'crash'); (OUT / 'crashes.txt').write_text(crashes)
    assert not crashes.strip(), crashes
    passed('App remains alive without crashes')
    (OUT / 'ui-checks.txt').write_text('\n'.join(checks) + '\n')
    print(f'UI RESULT: {len(checks)} scenario checks passed', flush=True)
except Exception:
    try:
        (OUT / 'failure-hierarchy.xml').write_text(ET.tostring(tree(), encoding='unicode'))
        (OUT / 'failure-logcat.txt').write_text(adb('logcat', '-d', '-t', '600'))
        screenshot('failure')
    except Exception as error: print('Evidence capture issue:', str(error), flush=True)
    raise
finally:
    adb('shell', 'settings', 'put', 'system', 'font_scale', '1.0')
