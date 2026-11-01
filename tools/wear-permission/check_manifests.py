"""Check the merged manifests, including permissions inherited from core."""
from pathlib import Path
import xml.etree.ElementTree as ET

ANDROID = '{http://schemas.android.com/apk/res/android}'
for module in ('phone', 'wear'):
    manifests = list(Path(module).glob('build/intermediates/merged_manifest*/**/AndroidManifest.xml'))
    assert manifests, f'No merged {module} manifest found'
    for manifest in manifests:
        permissions = {p.get(ANDROID + 'name'): p for p in ET.parse(manifest).getroot().findall('uses-permission')}
        schedule = permissions['android.permission.SCHEDULE_EXACT_ALARM']
        if module == 'wear':
            assert 'android.permission.USE_EXACT_ALARM' in permissions, str(manifest)
            assert schedule.get(ANDROID + 'maxSdkVersion') == '32', str(manifest)
        else:
            assert 'android.permission.USE_EXACT_ALARM' not in permissions, str(manifest)
            assert schedule.get(ANDROID + 'maxSdkVersion') is None, str(manifest)
        print(f'PERMISSION MANIFEST PASS: {manifest}', flush=True)
