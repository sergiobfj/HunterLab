"""Original A2 artwork and review boards. Run from repository root; optional PyMuPDF desktop renderer.
A1 assets and the app launcher are read-only inputs. No Android runtime dependency.
"""
from pathlib import Path
from xml.sax.saxutils import escape
import xml.etree.ElementTree as ET
import re
import pymupdf as fitz
ROOT=Path.cwd();OUT=ROOT/'docs/brand/horse-refinement';OUT.mkdir(parents=True,exist_ok=True)
DARK='#101416';SURFACE='#1D2327';INK='#E8ECEE';MUTED='#A7B0B5';LIME='#B7CF62'
A2_PATH='M22,80 C23,62 30,49 44,40 L44,28 L52,33 L60,24 L64,40 C69,48 75,57 84,66 L85,72 L79,79 L71,78 L61,70 L57,63 C51,68 47,75 46,82 L39,84 L40,73 L48,50 L43,56 L35,73 L32,86 Z M63,46 Q65,45 67,47 L67,48 L63,49 Q62,48 63,46 Z'
A2_ACCENT='M26,71 L29,62 L33,64 L30,74 Z'
A2_SCALE=.79
original=(ROOT/'docs/brand/concepts/a-cavalo/symbol.svg').read_text(encoding='utf-8')
A1_BODY=original[original.index('>')+1:original.rfind('</svg>')]
def group(version,ink=INK,mono=False):
 accent='#6E8F45' if ink==DARK else LIME
 if version==1:
  s=A1_BODY.replace('#E8ECEE',ink).replace('#B7CF62',ink if mono else accent)
  return s
 return f'<g transform="translate(54 54) scale({A2_SCALE}) translate(-50 -50)"><path d="{A2_PATH}" fill="{ink}" fill-rule="evenodd"/><path d="{A2_ACCENT}" fill="{ink if mono else accent}"/></g>'
def svg(body,w,h,title):return f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}" role="img" aria-label="{escape(title)}">{body}</svg>\n'
def text(x,y,value,size=16,color=MUTED,weight='400'):
 return f'<text x="{x}" y="{y}" fill="{color}" font-family="sans-serif" font-size="{size}" font-weight="{weight}">{escape(value)}</text>'
def symbol(version,x,y,size,ink=INK,mono=False):return f'<g transform="translate({x} {y}) scale({size/108})">'+group(version,ink,mono)+'</g>'
def icon(version,x,y,size,shape='rounded',mono=False,light=False):
 bg='#DCE9CF' if mono and light else ('#DCE3E6' if light else DARK)
 ink=DARK if light else (LIME if mono else INK)
 mask=f'<circle cx="{x+size/2}" cy="{y+size/2}" r="{size/2}" fill="{bg}"/>' if shape=='circle' else f'<rect x="{x}" y="{y}" width="{size}" height="{size}" rx="{size*.29}" fill="{bg}"/>'
 return mask+f'<g transform="translate({x-size*.25} {y-size*.25}) scale({size/72})">'+group(version,ink,mono)+'</g>'
def render(content,path,scale=1):
 d=fitz.open(stream=content.encode(),filetype='svg');pdf=fitz.open(stream=d.convert_to_pdf(),filetype='pdf');pdf[0].get_pixmap(matrix=fitz.Matrix(scale,scale),alpha=True).save(path)
def write(name,body,w=108,h=108,title='Hunter Lab A2'):
 value=svg(body,w,h,title);path=OUT/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(value,encoding='utf-8');render(value,path.with_suffix('.png'));return value
# New A2 sources only. A1 files are never written by this script.
write('refined/symbol.svg',group(2))
write('refined/on-dark.svg',f'<rect width="108" height="108" fill="{DARK}"/>'+group(2))
write('refined/on-light.svg','<rect width="108" height="108" fill="#FFFFFF"/>'+group(2,DARK))
write('refined/monochrome.svg',group(2,DARK,True))
write('refined/wordmark.svg',symbol(2,0,0,240)+text(252,145,'Hunter Lab',70,INK,'600'),780,240,'Hunter Lab A2 — assinatura')
for version in [1,2]:
 write(f'icon48-a{version}.svg',icon(version,0,0,48),48,48,f'A{version} — 48 pixels reais')
# Flatten the affine transform into Android path coordinates; do not change existing launcher assets.
def transform(path):
 tokens=re.findall(r'[MLHVQCZ]|[-+]?(?:[0-9]*[.])?[0-9]+',path);counts={'M':2,'L':2,'H':1,'V':1,'Q':4,'C':6,'Z':0};out=[];i=0
 while i<len(tokens):
  cmd=tokens[i];i+=1;n=counts[cmd];values=[(float(x)-50)*A2_SCALE+54 for x in tokens[i:i+n]];i+=n
  out.append(cmd+' '.join(f'{x:.5f}'.rstrip('0').rstrip('.') if x else '0' for x in values))
 return ' '.join(out)
def vector(mono):
 n='http://schemas.android.com/apk/res/android';ET.register_namespace('android',n);e=ET.Element('vector',{f'{{{n}}}width':'108dp',f'{{{n}}}height':'108dp',f'{{{n}}}viewportWidth':'108',f'{{{n}}}viewportHeight':'108'})
 for path,color in [(A2_PATH,INK),(A2_ACCENT,INK if mono else LIME)]:ET.SubElement(e,'path',{f'{{{n}}}pathData':transform(path),f'{{{n}}}fillColor':color,f'{{{n}}}fillType':'evenOdd'})
 ET.indent(e,space='    ');return '<?xml version="1.0" encoding="utf-8"?>\n<!-- A2 draft; no main identity approval. -->\n'+ET.tostring(e,encoding='unicode')+'\n'
for mono,name in [(False,'ic_horse_refined'),(True,'ic_horse_refined_monochrome')]:
 value=vector(mono);(ROOT/'app/src/main/res/drawable'/f'{name}.xml').write_text(value,encoding='utf-8');(OUT/'refined'/f'{name}.xml').write_text(value,encoding='utf-8')
# Light-ground previews use the same geometry with a deliberate ink/accent role change.
light=vector(False).replace(INK,DARK).replace(LIME,'#6E8F45')
(ROOT/'app/src/main/res/drawable/ic_horse_refined_light.xml').write_text(light,encoding='utf-8')
a1light=(ROOT/'app/src/main/res/drawable/ic_launcher_foreground.xml').read_text(encoding='utf-8').replace('#E8ECEE',DARK).replace('#B7CF62','#6E8F45')
(ROOT/'app/src/main/res/drawable/ic_horse_original_light.xml').write_text(a1light,encoding='utf-8')
# Before/after: same scale, frames, colors and roles.
b=f'<rect width="1080" height="1140" fill="{DARK}"/>'+text(36,52,'Cavalo de rua / refinamento',32,INK,'600')+text(36,87,'A1 preservada · A2 candidata · aprovação visual pendente',17)
for version,x in [(1,36),(2,570)]:
 b+=text(x,150,'A1 / proposta anterior' if version==1 else 'A2 / chanfro e respiro',23,INK,'600')+symbol(version,x+28,170,380)
 b+=text(x,560,'Massa contínua · detalhe na orelha' if version==1 else 'Pescoço aberto · detalhe lateral',16)
 b+=f'<rect x="{x}" y="600" width="474" height="210" fill="#FFFFFF"/>'+text(x+18,632,'Fundo claro / monocromática',14,DARK)
 b+=symbol(version,x+28,628,186,DARK)+symbol(version,x+260,628,186,DARK,True)
 b+=text(x,858,'Monocromática / recortes Android',15)+icon(version,x,886,96,'circle',True)+icon(version,x+142,886,96,'rounded',True,True)
 b+=text(x,1036,'48 × 48 px reais',15)+icon(version,x+292,1004,48)+icon(version,x+380,1004,48,'circle',True,True)
b+=text(36,1113,'Desenhos vetoriais originais. Recortes aproximados; sem validação no aparelho.',14)
write('comparison.svg',b,1080,1140,'Hunter Lab A1 e A2 — comparação')
# Compact image for visual approval.
b=f'<rect width="1080" height="480" fill="{DARK}"/>'+text(36,48,'Hunter Lab / Cavalo de rua',28,INK,'600')
for version,x in [(1,36),(2,574)]:b+=text(x,103,'A1 / anterior' if version==1 else 'A2 / refinada',21,INK,'600')+symbol(version,x+45,108,350)
b+=text(36,458,'Comparação no mesmo enquadramento. A2 é candidata, não identidade definitiva.',14)
write('selection.svg',b,1080,480,'A1 anterior e A2 refinada')
def launcher(version,mode):
 light=mode=='light';mono=mode=='mono';bg='#F2F4F5' if light else DARK;ink=DARK if light else INK;secondary='#4D5A61' if light else MUTED
 b=f'<rect width="360" height="720" fill="{bg}"/>'+text(26,42,'Samsung / simulação de launcher',12,secondary)+text(26,114,'Hunter Lab',34,ink,'600')+text(26,151,f'A{version} / '+('anterior' if version==1 else 'refinada'),16,secondary)
 b+=f'<path d="M360,240 V530 L0,642 V506 Z" fill="{("#E2E7EA" if light else SURFACE)}"/>'
 b+=icon(version,26,514,64,'rounded',mono,light or mono)+text(28,602,'Hunter Lab',12,ink)
 b+=text(26,668,'Máscara aproximada. Sem aparelho.',11,secondary)+text(26,693,'A identidade continua pendente de aprovação.',11,secondary)
 return b
b=f'<rect width="1080" height="1430" fill="{DARK}"/>'+text(36,54,'Launcher / antes e depois',30,INK,'600')+text(36,88,'A1 à esquerda · A2 à direita · composição ilustrativa',16)
for row,mode in enumerate(['dark','light','mono']):
 y=130+row*420;b+=text(36,y+18,{'dark':'Fundo escuro','light':'Fundo claro','mono':'Ícone temático / monocromático'}[mode],18,INK,'600')
 for version,x in [(1,120),(2,640)]:b+=f'<g transform="translate({x} {y+36}) scale(.5)">'+launcher(version,mode)+'</g>'
write('launchers.svg',b,1080,1430,'A1 e A2 — launchers escuro claro monocromático')
for mode in ['dark','light','mono']:
 for version in [1,2]:write(f'launcher-{mode}-a{version}.svg',launcher(version,mode),360,720,f'A{version} launcher {mode}')
print('A1 read-only; A2 SVG, vectors and before/after boards rendered.')
