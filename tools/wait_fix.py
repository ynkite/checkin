# -*- coding: utf-8 -*-
"""bbox 를 고친 장면을 한참 쉬었다 다시 만든다.

성산과 태종대는 랜드마크가 화면 밖(119%, -8%)으로 나가서 bbox 를 옮겼다.
bbox 가 바뀌면 캐시 열쇠도 바뀌어 Overpass 를 다시 타야 한다.
공용 서버라 잇달아 부르면 429 가 온다. 길게 쉰다.
"""
import subprocess, sys, os, time
for name in ('seongsan', 'taejongdae'):
    for i, wait in enumerate((0, 900, 1500)):
        if wait:
            print('%s — %d초 쉰다' % (name, wait), flush=True)
            time.sleep(wait)
        r = subprocess.run([sys.executable, 'massing.py', name],
                           capture_output=True, text=True, encoding='utf-8', errors='replace',
                           env=dict(os.environ, PYTHONIOENCODING='utf-8', CK_TILES='3'))
        out = (r.stdout or '') + (r.stderr or '')
        print('%s 시도%d: %s' % (name, i + 1,
              ' | '.join([l for l in out.splitlines() if l.strip()][-2:])), flush=True)
        if '실패' not in out and 'bldg=' in out:
            break
    time.sleep(600)
print('끝', flush=True)
