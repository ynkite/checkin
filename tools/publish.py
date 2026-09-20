# -*- coding: utf-8 -*-
"""구운 장면을 화면이 읽는 자리로 옮기고 목록을 다시 만든다.

tools/ 는 .gitignore 라 저장소에 안 들어간다. 화면은 static/img 를 읽는다.
굽는 중에도 여러 번 돌릴 수 있다 — 새로 구운 것만 옮긴다.
"""
import os, shutil, glob, subprocess, sys
here = os.path.dirname(os.path.abspath(__file__))
IMG = os.path.join(here, '..', 'src', 'main', 'resources', 'static', 'img')
moved = 0
for svg in glob.glob(os.path.join(here, 'mass_*.svg')):
    key = os.path.basename(svg)[5:-4]
    js = os.path.join(here, 'mass_%s.json' % key)
    if not os.path.exists(js):
        continue                       # json 이 없으면 좌표를 못 얹는다. 반쪽은 옮기지 않는다
    dst_svg = os.path.join(IMG, os.path.basename(svg))
    dst_js = os.path.join(IMG, os.path.basename(js))
    if os.path.exists(dst_svg) and os.path.getmtime(dst_svg) >= os.path.getmtime(svg):
        continue
    shutil.copy2(svg, dst_svg); shutil.copy2(js, dst_js); moved += 1
print('옮긴 장면 %d개' % moved)
subprocess.run([sys.executable, os.path.join(here, 'make_index.py')], cwd=here)
