# -*- coding: utf-8 -*-
"""여수만 한참 쉬었다 만든다.

504(질의가 무겁다)는 CK_TILES 로 쪼개면 풀린다. 그런데 잇달아 두드려서
429(너무 많음)까지 겹쳤다. 공용 서버라 기다리는 것 말고 방법이 없다.
"""
import subprocess, sys, os, time
for i, wait in enumerate((600, 900, 1200)):
    print('%d초 쉰다' % wait, flush=True)
    time.sleep(wait)
    r = subprocess.run([sys.executable, 'massing.py', 'yeosu'],
                       capture_output=True, text=True, encoding='utf-8', errors='replace',
                       env=dict(os.environ, PYTHONIOENCODING='utf-8', CK_TILES='3'))
    out = (r.stdout or '') + (r.stderr or '')
    print('시도%d: %s' % (i + 1, ' | '.join([l for l in out.splitlines() if l.strip()][-2:])), flush=True)
    if os.path.exists('mass_yeosu.json') and '실패' not in out:
        print('됐다', flush=True); break
else:
    print('세 번 다 실패. 나중에 다시.', flush=True)
