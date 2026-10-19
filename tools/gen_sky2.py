# -*- coding: utf-8 -*-
"""tokens.css 의 하늘 앵커(day·dusk·night 블록)를 읽어 static/js/sky-anchors.js 를 만든다.

사이 시각의 색은 index.html 의 ckSky 가 이 표를 oklab 에서 섞어 :root 에 인라인으로 넣는다.
dawn 블록은 밤->낮 보간이 덮으므로 표에 넣지 않는다.
tokens.css 는 읽기만 한다. 앵커를 고쳤으면 이 스크립트를 다시 돌린다.

    python tools/gen_sky2.py
"""
import io, json, os, re, sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src', 'main', 'resources', 'static')
CSS = os.path.join(ROOT, 'css', 'tokens.css')
JS = os.path.join(ROOT, 'js', 'sky-anchors.js')

BLOCK = re.compile(r'\[data-sky="(day|dusk|night)"\]\s*\{(.*?)\n\}', re.S)
COLOR = re.compile(r'^(#[0-9A-Fa-f]{3,8}|rgba?\()')

text = io.open(CSS, encoding='utf-8-sig').read()
blocks = {m.group(1): m.group(2) for m in BLOCK.finditer(text)}
if len(blocks) != 3:
    raise SystemExit('하늘 블록 3개를 못 찾았다: %s' % list(blocks))

order, anchors = [], {}
for name, body in blocks.items():
    anchors[name] = {}
    for decl in re.sub(r'/\*.*?\*/', '', body, flags=re.S).split(';'):
        k, _, v = decl.strip().partition(':')
        if not k.startswith('--'):
            continue
        if k not in order:
            order.append(k)
        anchors[name][k] = v.strip()

mixable = [k for k in order if all(COLOR.match(anchors[s].get(k, '')) for s in anchors)]
stepwise = [k for k in order if k not in mixable]

table = lambda s: json.dumps({k: anchors[s][k] for k in mixable}, ensure_ascii=False)
js = ('/*\n'
      ' * 하늘 앵커 — 생성 파일. 손으로 고치지 마라.\n'
      ' * tools/gen_sky2.py 가 tokens.css 에서 만든다.\n'
      ' *\n'
      ' * 섞을 수 있는 색 %d개. 나머지 %d개는 색이 아니라 계단으로 둔다.\n'
      ' */\n'
      'window.CK_SKY = {\n'
      '  keys: %s,\n'
      '  day: %s,\n'
      '  dusk: %s,\n'
      '  night: %s\n'
      '};\n') % (len(mixable), len(stepwise), json.dumps(mixable, ensure_ascii=False),
                table('day'), table('dusk'), table('night'))
io.open(JS, 'w', encoding='utf-8', newline='\n').write(js)

out = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
out.write('앵커 변수 %d개 · 섞는 것 %d개 · 계단 %s\n' % (len(order), len(mixable), ', '.join(stepwise) or '없음'))
out.write('sky-anchors.js %d bytes\n' % os.path.getsize(JS))
