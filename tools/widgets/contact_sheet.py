from pathlib import Path
import base64
import io
import math
from PIL import Image, ImageDraw

paths = sorted(Path('widget-frames').glob('*.png'))
if not paths:
    print('No widget frames captured')
else:
    width, height = 400, 370
    sheet = Image.new('RGB', (width * 2, height * math.ceil(len(paths) / 2)), '#303030')
    draw = ImageDraw.Draw(sheet)
    for i, path in enumerate(paths):
        frame = Image.open(path).convert('RGB')
        frame.thumbnail((width - 12, height - 30))
        x, y = (i % 2) * width, (i // 2) * height
        sheet.paste(frame, (x + 6, y + 24))
        draw.text((x + 6, y + 5), path.stem, fill='white')
    buf = io.BytesIO()
    sheet.save(buf, format='PNG', optimize=True)
    assert len(buf.getvalue()) < 400000
    print('WIDGET_CONTACT_BASE64=' + base64.b64encode(buf.getvalue()).decode())
