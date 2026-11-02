"""Small inline CI feedback avoids relying on private ZIP artifact downloads."""
from pathlib import Path
import base64
import io
from PIL import Image, ImageDraw

root=Path('wear-bezel-results')
frames=sorted(p for p in root.rglob('*.png') if p.name[:2].isdigit())
actual={p.name:p for p in root.rglob('display-*.png')}
preferred={'01-idle.png':'display-idle.png', '06-large-type-60.png':'display-large-type.png'}
frames=[actual.get(preferred.get(p.name,''),p) for p in frames]
failures=sorted(root.rglob('failure.png'))
frames.extend(failures)
if frames:
    width=600
    cell=200
    rows=(len(frames)+2)//3
    sheet=Image.new('RGB',(width,rows*(cell+24)), '#111111')
    draw=ImageDraw.Draw(sheet)
    for i,path in enumerate(frames):
        frame=Image.open(path).convert('RGB')
        frame.thumbnail((cell,cell))
        x=(i%3)*cell+(cell-frame.width)//2
        y=(i//3)*(cell+24)+24
        sheet.paste(frame,(x,y))
        draw.text(((i%3)*cell+4,y-20),path.stem,fill='white')
    encoded=io.BytesIO()
    sheet.save(encoded,format='PNG',optimize=True)
    data=encoded.getvalue()
    assert len(data)<200_000, f'Contact sheet too large: {len(data)} bytes'
    (root/'contact-sheet.png').write_bytes(data)
    print('BEZEL_CONTACT_SHEET_BASE64:'+base64.b64encode(data).decode(),flush=True)
else:
    print('BEZEL_CONTACT_SHEET: no frames captured',flush=True)
