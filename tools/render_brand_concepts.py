"""Render original SVG concepts; optional desktop dependencies: PyMuPDF. No Android dependency.
Run from repository root: python tools/render_brand_concepts.py
All source geometry is original, editable below. References are not embedded.
"""
from pathlib import Path
import pymupdf as fitz
root=Path.cwd()
DARK='#101416';INK='#E8ECEE';GREEN='#B7CF62'
concepts={
'a-cavalo':{'name':'A · Cavalo de rua','scale':.8,'paths':[
('fill','M22,80 C23,61 31,47 44,39 L43,25 L51,32 L59,23 L63,38 C68,45 72,53 82,64 L85,69 Q85,73 81,76 L76,80 L65,76 L58,67 L54,76 L39,84 Z M62,47 Q65,45 67,48 Q65,51 62,50 Z'),
('accent','M43,25 L51,32 L48,37 L44,34 Z')]},
'b-roadster':{'name':'B · Roadster','scale':.70,'paths':[
('stroke','M38,66 A13,13 0 1,1 12,66 A13,13 0 1,1 38,66 Z M89,66 A13,13 0 1,1 63,66 A13,13 0 1,1 89,66 Z'),
('fill','M34,45 C37,34 50,32 57,40 L63,48 L35,48 Z'),
('stroke','M18,43 H34 M29,49 L46,65 H57 L62,49 M61,29 H65 L76,66'),
('stroke','M75,33 A5,5 0 1,1 65,33 A5,5 0 1,1 75,33 Z'),
('accentStroke','M19,43 H32')]},

'c-hibrida':{'name':'C · Elo mecânico','scale':.74,'paths':[
('stroke','M24,78 V46 Q24,25 46,25 H50 L57,18 L61,33 L78,52 L73,59 L59,53 V78 M24,58 H59'),
('accentStroke','M42,58 H52')]}

}
def group(c,ink=INK,accent=GREEN,mono=False):
 out=[]
 if ink == DARK and not mono: accent='#6E8F45'
 for kind,path in c['paths']:
  col=ink if mono or 'accent' not in kind else accent
  if 'stroke' in kind.lower():out.append(f'<path d="{path}" fill="none" stroke="{col}" stroke-width="{(7 if c["name"].startswith("C") else 4)/c["scale"]}" stroke-linecap="round" stroke-linejoin="round"/>')
  else:out.append(f'<path d="{path}" fill="{col}" fill-rule="evenodd"/>')
 return f'<g transform="translate(54 54) scale({c["scale"]}) translate(-50 -50)">'+''.join(out)+'</g>'
def svg(body,w=108,h=108,title='Hunter Lab'):
 return f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}" role="img" aria-label="{title}">{body}</svg>\n'
def render(content,path,scale=3):
 d=fitz.open(stream=content.encode(),filetype='svg');pdf=fitz.open(stream=d.convert_to_pdf(),filetype='pdf');pdf[0].get_pixmap(matrix=fitz.Matrix(scale,scale),alpha=True).save(path)

from xml.sax.saxutils import escape
import xml.etree.ElementTree as ET
base=root/'docs/brand/concepts';base.mkdir(parents=True,exist_ok=True)
def text(x,y,label,size=16,color='#A7B0B5',weight='400'):
 return f'<text x="{x}" y="{y}" fill="{color}" font-family="sans-serif" font-size="{size}" font-weight="{weight}">{escape(label)}</text>'
def symbol(c,x,y,size,ink=INK,mono=False):
 return f'<g transform="translate({x} {y}) scale({size/108})">'+group(c,ink=ink,mono=mono)+'</g>'
def icon(c,x,y,size,shape='rounded',mono=False,light=False):
 bg='#DCE9CF' if light else DARK
 ink=DARK if light else (GREEN if mono else INK)
 backdrop=f'<circle cx="{x+size/2}" cy="{y+size/2}" r="{size/2}" fill="{bg}"/>' if shape=='circle' else f'<rect x="{x}" y="{y}" width="{size}" height="{size}" rx="{size*.29}" fill="{bg}"/>'
 # Geometry stays inside the safe circle, so clipping cannot remove a symbol pixel.
 return '<g>'+backdrop+f'<g transform="translate({x-size*.25} {y-size*.25}) scale({size/72})">'+group(c,ink=ink,mono=mono)+'</g></g>'
def wordmark(c,light=False):
 ink=DARK if light else INK
 return svg(symbol(c,0,0,240,ink=ink)+text(252,145,'Hunter Lab',70,ink,'600'),780,240,'Hunter Lab — assinatura horizontal')
def launcher(c):
 b='<rect width="360" height="720" fill="#101416"/>'
 b+=text(28,48,'Launcher Samsung · simulação',13)
 b+=text(28,128,'Hunter Lab',34,INK,'600')
 b+='<path d="M360,185 L360,540 L0,650 L0,520 Z" fill="#1D2327"/>'
 b+=text(28,178,'Proposta para aprovação',13)
 b+=icon(c,28,505,64)+text(30,592,'Hunter Lab',12,INK)
 b+='<rect x="126" y="505" width="64" height="64" rx="19" fill="#2C3439"/>'+text(143,548,'12',25,INK)+text(137,592,'Agenda',12,INK)
 b+=text(28,674,'Máscara aproximada de One UI.',11)+text(28,695,'Sem validação no aparelho.',11)
 return svg(b,360,720,c['name']+' — launcher simulado')
def transformed_path(path,scale):
 # All source paths use absolute commands. Bake the affine transform into coordinates.
 import re
 tokens=re.findall(r'[MLHVQCAZ]|[-+]?(?:[0-9]*[.])?[0-9]+',path)
 counts={'M':2,'L':2,'H':1,'V':1,'Q':4,'C':6,'A':7,'Z':0}
 result=[];i=0
 def coord(v):return (v-50)*scale+54
 while i<len(tokens):
  command=tokens[i];i+=1;n=counts[command];v=list(map(float,tokens[i:i+n]));i+=n
  if command=='A':v=[v[0]*scale,v[1]*scale,v[2],v[3],v[4],coord(v[5]),coord(v[6])]
  else:v=[coord(x) for x in v]
  result.append(command+' '.join(f'{x:.5f}'.rstrip('0').rstrip('.') if x else '0' for x in v))
 return ' '.join(result)
def vector(c,mono=False):
 n='http://schemas.android.com/apk/res/android';ET.register_namespace('android',n)
 e=ET.Element('vector',{f'{{{n}}}width':'108dp',f'{{{n}}}height':'108dp',f'{{{n}}}viewportWidth':'108',f'{{{n}}}viewportHeight':'108'})
 for kind,path in c['paths']:
  col=INK if mono or 'accent' not in kind else GREEN
  attrs={f'{{{n}}}pathData':transformed_path(path,c['scale']),f'{{{n}}}fillType':'evenOdd'}
  if 'stroke' in kind.lower():attrs.update({f'{{{n}}}fillColor':'#00000000',f'{{{n}}}strokeColor':col,f'{{{n}}}strokeWidth':str(7 if c['name'].startswith('C') else 4),f'{{{n}}}strokeLineCap':'round',f'{{{n}}}strokeLineJoin':'round'})
  else:attrs[f'{{{n}}}fillColor']=col
  ET.SubElement(e,'path',attrs)
 ET.indent(e,space='    ')
 return '<?xml version="1.0" encoding="utf-8"?>\n<!-- Draft concept; requires identity approval before main integration. -->\n'+ET.tostring(e,encoding='unicode')+'\n'
labels=['Perfil baixo. Massa sólida.','Postura compacta de roadster.','Tubo contínuo. H implícito.']
board='<rect width="1260" height="1070" fill="#101416"/>'+text(42,58,'Hunter Lab / estudos de marca',32,INK,'600')+text(42,94,'Três direções originais · Graphite Grey · aguardam aprovação',17)
for i,(key,c) in enumerate(concepts.items()):
 dest=base/key;dest.mkdir(exist_ok=True)
 files={'symbol.svg':svg(group(c),title=c['name']), 'on-dark.svg':svg('<rect width="108" height="108" fill="#101416"/>'+group(c),title=c['name']), 'on-light.svg':svg('<rect width="108" height="108" fill="#FFFFFF"/>'+group(c,ink=DARK),title=c['name']), 'monochrome.svg':svg(group(c,ink=DARK,mono=True),title=c['name']), 'wordmark.svg':wordmark(c), 'wordmark-light.svg':wordmark(c,True), 'android.svg':svg(icon(c,0,0,108),title=c['name']), 'launcher.svg':launcher(c), 'splash.svg':svg('<rect width="360" height="720" fill="#101416"/>'+symbol(c,60,240,240),360,720,c['name']+' — abertura ilustrativa')}
 for name,value in files.items():
  (dest/name).write_text(value,encoding='utf-8')
  if name in ['on-dark.svg','on-light.svg','monochrome.svg','wordmark.svg','android.svg','launcher.svg','splash.svg']:render(value,dest/(name[:-4]+'.png'),2 if name!='wordmark.svg' else 1)
 render(svg(icon(c,0,0,48),48,48,c['name']+' 48 px'),dest/'size48.png',1)
 (dest/'android-foreground.xml').write_text(vector(c),encoding='utf-8');(dest/'android-monochrome.xml').write_text(vector(c,True),encoding='utf-8')
 # Per-concept sheet, eight requested contexts, without reference images.
 b='<rect width="1000" height="1400" fill="#101416"/>'+text(40,58,c['name'],32,INK,'600')+text(40,94,'ESTUDO / identidade ainda não aprovada',14)
 b+=text(40,155,'Símbolo isolado',15)+symbol(c,25,170,280)
 b+=text(390,155,'Assinatura horizontal',15)+symbol(c,370,238,120)+text(494,311,'Hunter Lab',42,INK,'600')
 b+='<rect x="40" y="490" width="280" height="240" fill="#FFFFFF"/>'+text(58,522,'Fundo claro',14,DARK)+symbol(c,75,530,200,ink=DARK)
 b+=text(370,522,'Monocromática',14)+symbol(c,380,530,200,mono=True)
 b+=text(680,522,'Android / recortes',14)+icon(c,684,563,105,'circle')+icon(c,823,563,105)
 b+=text(40,790,'Escala real de 48 × 48 px',15)+icon(c,40,815,48)+icon(c,116,815,48,'circle',True,True)
 b+=text(390,790,'Geometria sólida. Sem efeitos.',15)+text(390,824,labels[i],18,INK)
 launch=launcher(c);lb=launch[launch.index('>')+1:launch.rfind('</svg>')]
 b+='<g transform="translate(40 905) scale(.61)">'+lb+'</g>'
 b+=text(340,932,'Launcher ilustrativo',18,INK)+text(340,967,'A máscara final depende do One UI.',14)+text(340,992,'Nenhuma captura ou teste em aparelho.',14)
 b+=text(340,1070,'Abertura',18,INK)+symbol(c,520,1060,190)
 b+=text(340,1310,'Não oficial. Sem marca Royal Enfield.',14)
 sheet=svg(b,1000,1400,c['name']+' — aplicações');(dest/'presentation.svg').write_text(sheet,encoding='utf-8');render(sheet,dest/'presentation.png',1)
 x=42+i*407
 if i:board+=f'<path d="M{x-24},135 V1010" stroke="#2C3439"/>'
 board+=text(x,154,c['name'],21,INK,'600')+symbol(c,x+40,176,274)+text(x,468,'Hunter Lab',28,INK,'600')+text(x,504,labels[i],16)
 board+=f'<rect x="{x}" y="550" width="350" height="170" fill="#FFFFFF"/>'+symbol(c,x+12,560,145,ink=DARK)+symbol(c,x+186,560,145,ink=DARK,mono=True)
 board+=text(x,757,'Android / monocromático / 48 px',13)+icon(c,x,784,80,'circle')+icon(c,x+115,784,80,'rounded',True,True)+icon(c,x+235,800,48)
 note=['Recomendada para aprofundar.','Associação mais direta com a moto.','Alternativa de assinatura mecânica.'][i]
 board+=text(x,920,note,15,INK)+text(x,957,['Cavalo sem brasão ou pose heráldica.','Síntese artística; não desenho técnico.','Leitura intencionalmente mais abstrata.'][i],13)
board+=text(42,1035,'Estudos vetoriais autorais. Pesquisa de similaridade preliminar; não é liberação jurídica.',14)
comparison=svg(board,1260,1070,'Comparação Hunter Lab A B C');(base/'comparison.svg').write_text(comparison,encoding='utf-8');render(comparison,base/'comparison.png',1)
# Three real Android vector candidates for previews. Only A is wired in this draft branch.
res=root/'app/src/main/res/drawable'
for (key,c),name in zip(concepts.items(),['ic_launcher_foreground','ic_candidate_roadster','ic_candidate_hybrid']):
 (res/(name+'.xml')).write_text(vector(c),encoding='utf-8')
 mono='ic_launcher_monochrome' if name=='ic_launcher_foreground' else name+'_monochrome'
 (res/(mono+'.xml')).write_text(vector(c,True),encoding='utf-8')
print('Rendered three concepts and Android vector candidates; recommendation A remains unapproved.')

# Compact selector for the review conversation; full applications remain in comparison/presentation.
selector='<rect width="1080" height="440" fill="#101416"/>'+text(32,46,'Hunter Lab / três direções',28,INK,'600')
for i,(key,c) in enumerate(concepts.items()):
 x=32+i*354
 selector+=text(x,102,c['name'],21,INK,'600')+symbol(c,x+6,115,290)
selector+=text(32,421,'Propostas para aprovação. A recomendação é A; nenhuma substitui a marca da main.',14)
selector=svg(selector,1080,440,'Hunter Lab — seleção de conceitos');(base/'selection.svg').write_text(selector,encoding='utf-8');render(selector,base/'selection.png',1)
