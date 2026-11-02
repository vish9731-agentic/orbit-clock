from pathlib import Path
import base64
import io
from PIL import Image, ImageDraw

paths = sorted(Path('widget-frames').glob('*.png'))
if not paths:
    print('No widget frames captured')
else:
    sheet = Image.new('RGB', (240 * len(paths), 420), '#303030')
    draw = ImageDraw.Draw(sheet)
    for i, path in enumerate(paths):
        frame = Image.open(path).convert('RGB')
        frame.thumbnail((232, 388))
        sheet.paste(frame, (i * 240, 24))
        draw.text((i * 240 + 4, 4), path.stem, fill='white')
    buf = io.BytesIO()
    sheet.save(buf, format='PNG', optimize=True)
    assert len(buf.getvalue()) < 180000
    print('WIDGET_CONTACT_BASE64=' + base64.b64encode(buf.getvalue()).decode())
