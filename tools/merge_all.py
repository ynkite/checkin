# -*- coding: utf-8 -*-
"""팀원들이 올린 장면을 합친 뒤 한 번만 부르는 마무리.

각자 --no-index 로 올리므로 목록이 없다. 다 받은 뒤 여기서 한 번 만든다.
빠진 곳과 이상한 곳을 같이 센다 — 「전부 됐다」를 눈으로 확인하려고.
"""
import io, json, os, subprocess, sys, glob

here = os.path.dirname(os.path.abspath(__file__))
IMG = os.path.join(here, '..', 'src', 'main', 'resources', 'static', 'img')
coords = json.load(io.open(os.path.join(here, 'coords.json'), encoding='utf-8'))

missing, broken, ok = [], [], 0
for i, a in enumerate(coords):
    key = 'sig_%03d' % i
    js = os.path.join(IMG, 'mass_%s.json' % key)
    svg = os.path.join(IMG, 'mass_%s.svg' % key)
    if not os.path.exists(js) or not os.path.exists(svg):
        missing.append((i, a['key'])); continue
    try:
        j = json.load(io.open(js, encoding='utf-8'))
    except Exception:
        broken.append((i, a['key'], '읽히지 않음')); continue
    if not j.get('fit'):
        broken.append((i, a['key'], 'fit 없음 — 핀을 못 찍는다')); continue
    if os.path.getsize(svg) < 5 * 1024:
        broken.append((i, a['key'], '%dB — 건물을 못 받았다' % os.path.getsize(svg))); continue
    ok += 1

print('시군구 %d곳 중 쓸 수 있는 것 %d곳' % (len(coords), ok))
if missing:
    print('아직 안 구운 곳 %d: %s' % (len(missing), ' '.join('%d(%s)' % m for m in missing[:20])))
if broken:
    print('이상한 곳 %d:' % len(broken))
    for b in broken[:20]:
        print('   %d %s — %s' % b)

subprocess.run([sys.executable, os.path.join(here, 'make_index.py')], cwd=here)

tot = sum(os.path.getsize(f) for f in glob.glob(os.path.join(IMG, 'mass_*.svg')))
print('장면 파일 전체 %.1fMB' % (tot / 1024.0 / 1024.0))
