# -*- coding: utf-8 -*-
"""전국 시군구를 굽는다. 오래 걸리므로 이어서 할 수 있게 만든다.

Overpass 는 공용 서버라 잇달아 부르면 429·504 를 준다.
이미 구운 것은 건너뛴다 — 중간에 끊겨도 다시 돌리면 이어서 한다.
실패한 것은 bake_fail.txt 에 남긴다. 「전부 됐다」고 말하지 않기 위해서다.
"""
import io, os, subprocess, sys, time, json, importlib.util

here = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location('m', os.path.join(here, 'massing.py'))
m = importlib.util.module_from_spec(spec); sys.modules['m'] = m; spec.loader.exec_module(m)

keys = [k for k in m.SCENES if k.startswith('sig_')]
todo = [k for k in keys if not os.path.exists(os.path.join(here, 'mass_%s.json' % k))]
# 일꾼을 여럿 붙일 수 있게 구간을 나눈다.
#
#   python bake.py            앞에서부터 전부 (혼자 할 때만)
#   python bake.py 120 200    sig_120 ~ sig_199 만
#
# 「뒤에서부터 전부」를 두었다가 걷어냈다. 가운데서 만나면 멈출 줄 알았는데,
# 할 목록을 시작할 때 한 번만 세기 때문에 그 뒤로 남이 구운 것을 모른다.
# 그대로 두면 남의 구간을 끝까지 파고들어 같은 곳을 두 번 굽는다.
# Overpass 는 공용 서버라 그만큼 서로의 한도를 깎는 셈이다.
# 여럿이 할 때는 구간을 명시해라.
if len(sys.argv) > 2:
    lo, hi = int(sys.argv[1]), int(sys.argv[2])
    todo = [k for k in todo if lo <= int(k.split('_')[1]) < hi]
    print('구간 sig_%03d ~ sig_%03d · %d곳' % (lo, hi - 1, len(todo)), flush=True)
print('전체 %d · 이미 있음 %d · 할 것 %d' % (len(keys), len(keys)-len(todo), len(todo)), flush=True)

GAP = 22          # 장면 사이 쉬는 시간. 75 는 너무 길다 — 251개면 5시간이 여기서만 간다
fails = []
t0 = time.time()
for i, k in enumerate(todo):
    # 시작할 때 센 목록이라 그 뒤에 남이 구운 것은 모른다. 매번 다시 본다 —
    # 여럿이 나눠 할 때 구간이 살짝 겹쳐도 두 번 굽지 않는다
    if os.path.exists(os.path.join(here, 'mass_%s.json' % k)):
        continue
    ok = False
    for attempt, wait in enumerate((0, 45, 120)):
        if wait: time.sleep(wait)
        r = subprocess.run([sys.executable, 'massing.py', k], cwd=here,
                           capture_output=True, text=True, encoding='utf-8', errors='replace',
                           env=dict(os.environ, PYTHONIOENCODING='utf-8'))
        out = (r.stdout or '') + (r.stderr or '')
        if os.path.exists(os.path.join(here, 'mass_%s.json' % k)) and '실패' not in out:
            ok = True; break
    name = m.SIGUNGU_NAME.get(k, k)
    if not ok:
        fails.append('%s %s' % (k, name))
        io.open(os.path.join(here,'bake_fail.txt'),'w',encoding='utf-8').write('\n'.join(fails))
    done = i + 1
    if done % 5 == 0 or not ok:
        el = time.time() - t0
        left = (el / done) * (len(todo) - done)
        print('%3d/%d  %-14s %s · 남은 시간 약 %d분 · 실패 %d'
              % (done, len(todo), name, '됨' if ok else '실패', left/60, len(fails)), flush=True)
    time.sleep(GAP)

print('끝 — 실패 %d개' % len(fails), flush=True)
if fails: print('\n'.join(fails), flush=True)
