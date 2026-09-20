# -*- coding: utf-8 -*-
"""장면 목록 하나로 묶는다.

전에는 page_map.html 에 장면을 한 줄씩 적어 두고, 화면이 장면마다 json 을
따로 받아 bbox 를 봤다. 열여덟 개일 때는 됐다. 이제 이백예순아홉 개다 —
화면을 한 번 열 때 요청이 269번 나간다.

목록 하나에 bbox 와 fit 까지 담아 둔다. 화면은 이 파일 하나만 받고,
고른 장면의 svg 만 더 받는다. 요청이 2번으로 끝난다.
"""
import io, json, os, re, glob, importlib.util, sys

here = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location('m', os.path.join(here, 'massing.py'))
m = importlib.util.module_from_spec(spec); sys.modules['m'] = m; spec.loader.exec_module(m)

# 손으로 지은 장면의 한글 이름은 page_map.html 에 이미 적혀 있다. 거기서 읽는다
html = io.open(os.path.join(here, '..', 'src', 'main', 'resources', 'templates', 'page_map.html'),
               encoding='utf-8').read()
HAND = dict(re.findall(r"\{ key: '([a-z_]+)',\s*label: '([^']+)'", html))

# massing.py 의 SCENES 줄 끝 주석에 한글 이름이 적혀 있다.
#   'daegu_dongseong': ((...), 16, 0.74, 26.0, True),  # 대구 동성로 35.8688,128.595
# 이름을 두 곳에 적지 않으려고 거기서 읽는다. 없으면 열쇠를 그대로 쓴다 —
# 화면에 영문 열쇠가 나오면 눈에 띄어서 빠진 것을 바로 안다.
PY_SRC = io.open(os.path.join(here, 'massing.py'), encoding='utf-8').read()
for k, ko in re.findall(r"'([a-z_0-9]+)':\s*\(\(.*?#\s*([가-힣][^\d\n]*?)\s*[\d.]+,", PY_SRC):
    HAND.setdefault(k, ko.strip())

IMG = os.path.join(here, '..', 'src', 'main', 'resources', 'static', 'img')
out = []
for f in sorted(glob.glob(os.path.join(IMG, 'mass_*.json'))):
    key = os.path.basename(f)[5:-5]
    if key.startswith('card_') or key in ('hero', 'index'):
        continue                      # 메인 전용 그림과 목록 파일 자신은 건너뛴다
    try:
        j = json.load(io.open(f, encoding='utf-8'))
    except Exception:
        continue
    if not j.get('bbox') or not j.get('fit'):
        continue                      # fit 이 없으면 좌표를 얹을 수 없다 — 목록에서 뺀다
    out.append({
        'key': key,
        'label': HAND.get(key) or m.SIGUNGU_NAME.get(key) or key,
        'svg': '/img/mass_%s.svg' % key,
        'json': '/img/mass_%s.json' % key,
        'bbox': j['bbox'], 'fit': j['fit'],
    })

io.open(os.path.join(IMG, 'mass_index.json'), 'w', encoding='utf-8').write(
    json.dumps(out, ensure_ascii=False, separators=(',', ':')))
print('장면 %d개 · 손으로 지은 것 %d개'
      % (len(out), sum(1 for o in out if not o['key'].startswith('sig_'))))
