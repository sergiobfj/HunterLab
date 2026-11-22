"""Desktop art pipeline. No app dependency. Python + Pillow + PyMuPDF + VTracer.

Only traces our generated artwork, NEVER the user's reference images.
Run from the repository: python tools/render_horse_art.py
"""
from pathlib import Path
from io import BytesIO
from tempfile import TemporaryDirectory
import math
import re
import xml.etree.ElementTree as ET
from PIL import Image, ImageDraw, ImageFont, ImageFilter
import pymupdf
import vtracer

ROOT = Path(__file__).resolve().parents[1] / 'docs/brand/horse-art'
NAMES = [('01-ink', 'Cavalo em tinta', 'Gesto livre. Contorno de pincel.'),
         ('02-graphic', 'Cavalo gráfico', 'Massa sólida. Leitura imediata.'),
         ('03-speed', 'Cavalo em velocidade', 'Galope aberto. Rastro de tinta.')]
BG, INK, LIME, RED = '#101416', '#E8ECEE', '#B7CF62', '#C72D27'
NS = {'s': 'http://www.w3.org/2000/svg'}


def font(size, bold=False):
    return ImageFont.truetype(str(Path('C:/Windows/Fonts') / ('segoeuib.ttf' if bold else 'segoeui.ttf')), size)


def svg_image(svg, w, h):
    with pymupdf.open(stream=svg.encode('utf-8'), filetype='svg') as doc:
        p = doc[0]
        pix = p.get_pixmap(matrix=pymupdf.Matrix(w / p.rect.width, h / p.rect.height), alpha=True)
        return Image.open(BytesIO(pix.tobytes('png'))).convert('RGBA')


def transform(d, tx, ty, scale, dx, dy):
    # VTracer spline mode emits absolute M/C/Z, hence all numbers are x/y pairs.
    assert set(re.findall('[A-Za-z]', d)) <= set('MCZ')
    index = 0

    def point(m):
        nonlocal index
        axis = index % 2
        index += 1
        value = (float(m[0]) + (tx if axis == 0 else ty)) * scale + (dx if axis == 0 else dy)
        return str(round(value, 3)).rstrip('0').rstrip('.') if value else '0'
    # Keep spaces/command separators; only numeric tokens change.
    return re.sub(r'-?\d+(?:\.\d+)?', point, d)


def geometry(path, bbox, width, height, max_width, max_height):
    x0, y0, x1, y1 = bbox
    scale = min(max_width / (x1-x0), max_height / (y1-y0))
    dx, dy = width/2 - (x0+x1)/2*scale, height/2 - (y0+y1)/2*scale
    out = []
    for el in ET.parse(path).getroot().findall('s:path', NS):
        tx, ty = map(float, re.findall(r'-?\d+(?:\.\d+)?', el.get('transform', 'translate(0,0)')))
        out.append(transform(el.get('d'), tx, ty, scale, dx, dy))
    return out


def mark(paths, fill):
    return ''.join(f'<path fill="{fill}" fill-rule="evenodd" d="{d}"/>' for d in paths)


def svg(paths, fill='#101416', background=None, accent=None, size=1000, height=700):
    rect = f'<rect width="100%" height="100%" fill="{background}"/>' if background else ''
    # A small hand-ink slash sits clear of the anatomy; no borrowed stars/compositions.
    stroke = f'<path fill="{accent}" d="M344 620 Q430 604 548 610 L637 617 Q508 620 376 633 Z"/>' if accent else ''
    return f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{height}" viewBox="0 0 {size} {height}">{rect}{mark(paths,fill)}{stroke}</svg>'


def write_svg(folder, name, content, png_size=None):
    (folder / (name+'.svg')).write_text(content, encoding='utf-8')
    w, h = png_size or (1000, 700)
    svg_image(content, w, h).save(folder/(name+'.png'))


def icon(paths, mode='graphite', px=432, mask='squircle'):
    bg, fg = (BG, INK) if mode == 'graphite' else ('#F3EFE5', BG) if mode == 'mono' else (RED, '#101416')
    # Android's 108dp layers include 18dp overscan on every side.
    # The launcher displays the central 72dp, not the entire 108dp layer.
    full = px*3//2
    frame = Image.new('RGBA', (full, full), bg)
    horse = svg_image(svg(paths, fg, size=108, height=108), full, full)
    frame.alpha_composite(horse)
    if mode != 'mono':
        d = ImageDraw.Draw(frame)
        # Discrete swash is outside body, confined to adaptive safe region.
        d.polygon([(full*.35,full*.73),(full*.48,full*.72),(full*.65,full*.735),(full*.44,full*.745)], fill=LIME if mode=='graphite' else '#E8ECEE')
    inset = (full-px)//2
    frame = frame.crop((inset,inset,inset+px,inset+px))
    alpha = Image.new('L', (px,px), 0); a = ImageDraw.Draw(alpha)
    if mask == 'circle':
        a.ellipse((0,0,px-1,px-1),fill=255)
    else:
        a.rounded_rectangle((0,0,px-1,px-1),radius=int(px*.26),fill=255)
    frame.putalpha(alpha)
    return frame


def android(folder, paths):
    out = folder/'android-candidate'; out.mkdir(exist_ok=True)
    attrs = '\n'.join(f'    <path android:fillColor="#FFE8ECEE" android:fillType="evenOdd" android:pathData="{p}"/>' for p in paths)
    head = '<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="108dp" android:height="108dp" android:viewportWidth="108" android:viewportHeight="108">\n'
    swash = '<path android:fillColor="#FFB7CF62" android:pathData="M37.8,78.84 L51.84,77.76 L70.2,79.38 L47.52,80.46 Z"/>\n'
    (out/'ic_horse_foreground.xml').write_text(head+attrs+'\n'+swash+'</vector>\n',encoding='utf-8')
    (out/'ic_horse_monochrome.xml').write_text(head+attrs.replace('#FFE8ECEE','#FFFFFFFF')+'\n</vector>\n',encoding='utf-8')
    (out/'horse_candidate_colors.xml').write_text('<resources><color name="horse_art_background">#FF101416</color></resources>\n',encoding='utf-8')
    wrapper = '<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">\n<background android:drawable="@color/horse_art_background"/>\n<foreground android:drawable="@drawable/ic_horse_foreground"/>\n<monochrome android:drawable="@drawable/ic_horse_monochrome"/>\n</adaptive-icon>\n'
    for name in ['ic_launcher.xml','ic_launcher_round.xml']:
        (out/name).write_text(wrapper,encoding='utf-8')
    # A real PNG 48x48, plus enlargement to inspect pixels separately.
    for mode in ['graphite','reference','mono']:
        large = icon(paths,mode)
        large.save(folder/f'icon-{mode}.png')
        small = large.resize((48,48),Image.Resampling.LANCZOS)
        small.save(folder/f'icon48-{mode}.png')
        small.resize((288,288),Image.Resampling.NEAREST).save(folder/f'icon48-{mode}-6x.png')
    for mask in ['circle','squircle']:
        icon(paths,'graphite',mask=mask).save(folder/f'adaptive-{mask}.png')
    write_svg(folder,'icon-foreground',svg(paths,INK,size=108,height=108),(432,432))
    write_svg(folder,'icon-monochrome',svg(paths,'#FFFFFF',size=108,height=108),(432,432))


def phone(paths, mode, title):
    p=Image.new('RGB',(360,780),'#090C0E'); d=ImageDraw.Draw(p)
    dark=mode!='mono'; wall='#1D2327' if dark else '#ECEEE9'; text=INK if dark else BG
    d.rounded_rectangle((10,10,350,770),radius=40,fill=wall)
    d.text((31,30),'09:41',font=font(13,True),fill=text)
    d.text((272,30),'5G  84%',font=font(11),fill=text)
    d.ellipse((176,22,184,30),fill='#090C0E')
    d.text((32,115),'domingo',font=font(17),fill=text)
    d.text((29,137),'22',font=font(94),fill=text)
    d.text((34,248),'novembro',font=font(17),fill=text)
    d.rounded_rectangle((30,354,330,397),radius=22,fill='#30383B' if dark else '#DDE2DB')
    d.text((49,364),'Pesquisar',font=font(14),fill='#A7B0B5' if dark else '#566158')
    positions=[(33,437),(116,437),(199,437),(282,437)]
    for j,(x,y) in enumerate(positions):
        if j==0: p.paste(icon(paths,mode,px=48),(x,y),icon(paths,mode,px=48))
        else:
            d.rounded_rectangle((x,y,x+48,y+48),radius=13,fill=['#596675','#846E54','#657D6E'][j-1])
            # Neutral placeholders, not copied app trademarks.
            d.ellipse((x+17,y+17,x+31,y+31),outline='#ECEEE9',width=2)
        label=['Hunter Lab','Fotos','Notas','Arquivos'][j]
        d.text((x+24,y+57),label,font=font(10),fill=text,anchor='mt')
    for x in [39,121,203,285]:
        d.rounded_rectangle((x,660,x+42,702),radius=12,fill='#36413E' if dark else '#C5CEC0')
    d.rounded_rectangle((138,741,222,745),radius=2,fill=text)
    return p


def main():
    boards=[]
    for slug,title,caption in NAMES:
        folder=ROOT/slug
        source=Image.open(folder/'source-generated.png').convert('L')
        mask=source.point(lambda p:255 if p<128 else 0)
        bbox=mask.getbbox()
        paths=geometry(folder/'trace.svg',bbox,1000,700,900,490)
        for name,fg,bg,accent in [
            ('symbol',BG,None,None),('on-light',BG,'#FAF8F1',RED),
            ('on-dark',INK,BG,RED),('graphite',INK,BG,LIME),
            ('reference',BG,RED,None),('monochrome',BG,'#FFFFFF',None)]:
            write_svg(folder,name,svg(paths,fg,bg,accent))
        # Variant for small sizes: close only hairline cuts at 384px before tracing.
        mini=source.copy(); mini.thumbnail((384,384),Image.Resampling.LANCZOS)
        mini=mini.point(lambda p:0 if p<128 else 255).filter(ImageFilter.MinFilter(3)).filter(ImageFilter.MaxFilter(3)).convert('RGB')
        with TemporaryDirectory(prefix='hunter-horse-vector-') as tmp:
            trace_input=Path(tmp)/'small.png'
            mini.save(trace_input)
            vtracer.convert_image_to_svg_py(str(trace_input),str(folder/'icon-trace.svg'),colormode='binary',mode='spline',filter_speckle=10,corner_threshold=70,length_threshold=4.,splice_threshold=45,path_precision=2)
        mb=mini.convert('L').point(lambda p:255 if p<128 else 0).getbbox()
        ip=geometry(folder/'icon-trace.svg',mb,108,108,62,43)
        # Verify actual silhouette is within the Android 66dp safe circle.
        pix=svg_image(svg(ip,'#FFFFFF',size=108,height=108),432,432).getchannel('A')
        mx=max(math.hypot((x+.5)/4-54,(y+.5)/4-54) for y in range(432) for x in range(432) if pix.getpixel((x,y))>127)
        if mx>32.5:
            ip=[transform(s,0,0,32.5/mx,54*(1-32.5/mx),54*(1-32.5/mx)) for s in ip]
        android(folder,ip)
        for mode in ['graphite','reference','mono']:
            phone(ip,mode,title).save(folder/f'launcher-{mode}.png')
        c=Image.new('RGB',(600,1530),'#F4F1E8'); d=ImageDraw.Draw(c)
        d.text((28,23),slug[:2],font=font(15,True),fill='#6E7778')
        d.text((28,55),title,font=font(29,True),fill=BG)
        d.text((28,99),caption,font=font(18),fill='#566165')
        hero=Image.open(folder/('on-light.png' if slug=='01-ink' else 'on-dark.png' if slug=='02-graphic' else 'reference.png')).convert('RGB').resize((600,420),Image.Resampling.LANCZOS)
        c.paste(hero,(0,146))
        for row,(label,fn) in enumerate([('Graphite Grey','graphite.png'),('Preto / branco / vermelho','on-dark.png'),('Monocromática','monochrome.png')]):
            y=594+row*220
            d.text((28,y),label,font=font(16,True),fill=BG)
            img=Image.open(folder/fn).convert('RGB').resize((270,189),Image.Resampling.LANCZOS)
            c.paste(img,(28,y+25))
            mod=['graphite','reference','mono'][row]
            ic=Image.open(folder/f'icon-{mod}.png').resize((130,130),Image.Resampling.LANCZOS)
            c.paste(ic,(394,y+52),ic)
        d.text((28,1281),'48 × 48 px reais',font=font(17,True),fill=BG)
        small=Image.open(folder/'icon48-graphite.png'); c.paste(small,(29,1324),small)
        zoom=small.resize((144,144),Image.Resampling.NEAREST); c.paste(zoom,(128,1313),zoom)
        d.text((128,1468),'ampliação 3×, sem suavização',font=font(13),fill='#566165')
        boards.append(c)
    sheet=Image.new('RGB',(1800,1700),'#F4F1E8'); d=ImageDraw.Draw(sheet)
    d.text((28,17),'HUNTER LAB',font=font(21,True),fill=BG)
    d.text((28,48),'Três cavalos. Três gestos.',font=font(34,True),fill=BG)
    d.text((28,101),'Estudos originais a partir da linguagem artística das suas referências • candidatos para aprovação',font=font(18),fill='#566165')
    for i,b in enumerate(boards): sheet.paste(b,(i*600,145))
    sheet.save(ROOT/'comparison.png')
    launch=Image.new('RGB',(1260,1810),'#F4F1E8'); d=ImageDraw.Draw(launch)
    d.text((30,23),'Hunter Lab · estudo de launcher',font=font(28,True),fill=BG)
    d.text((30,67),'Simulações inspiradas em Samsung One UI. Não são capturas do aparelho.',font=font(17),fill='#566165')
    for i,(slug,title,_) in enumerate(NAMES):
        x=30+i*420; d.text((x,115),title,font=font(22,True),fill=BG)
        for row,mode in enumerate(['graphite','mono']):
            launch.paste(Image.open(ROOT/slug/f'launcher-{mode}.png'),(x,160+row*820))
    launch.save(ROOT/'launchers.png')
    print('Rendered 3 original concepts, 18 SVG applications, Android candidates, true 48px icons and launcher simulations.')


if __name__=='__main__':
    main()
