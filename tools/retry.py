# -*- coding: utf-8 -*-
"""실패한 장면만 간격을 두고 다시 만든다.

Overpass 는 공용 서버라 잇달아 부르면 429(너무 많음)·504(시간초과)를 준다.
한 장 만들고 쉬었다 다음 장을 만든다. 실패하면 더 오래 쉬고 두 번 더 해 본다.
"""
import subprocess, sys, time, os

WANT = sys.argv[1:] or ['gamcheon', 'nampo', 'gyeongbokgung', 'bukchon', 'namsan']
GAP = 75          # 장면 사이 쉬는 시간(초)
BACKOFF = [0, 120, 240]

for name in WANT:
    done = False
    for i, wait in enumerate(BACKOFF):
        if wait:
            print('   %d초 쉬었다 다시' % wait, flush=True)
            time.sleep(wait)
        r = subprocess.run([sys.executable, 'massing.py', name],
                           capture_output=True, text=True, encoding='utf-8', errors='replace',
                           env=dict(os.environ, PYTHONIOENCODING='utf-8'))
        out = (r.stdout or '') + (r.returncode and (r.stderr or '') or '')
        tail = [l for l in out.splitlines() if l.strip()][-2:]
        print('%s 시도%d: %s' % (name, i + 1, ' | '.join(tail)), flush=True)
        if os.path.exists('mass_%s.json' % name) and '실패' not in out:
            done = True
            break
    if not done:
        print('%s — 세 번 다 실패. bbox 를 줄이거나 나중에 다시.' % name, flush=True)
    time.sleep(GAP)
print('끝', flush=True)
