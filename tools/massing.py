# -*- coding: utf-8 -*-
"""OSM → 축측투상 3D 매싱 SVG.

지붕 8계열 · 박공 · 나무 2종 · 해변 파라솔 · 차량 · 옥상설비 · 파도선 · 군중 밀도.
성능: 깊이를 밴드로 양자화해 같은 클래스를 한 path로 병합한다. 필터는 쓰지 않는다.
색은 전부 CSS 클래스로만 내보내 앱에서 낮/노을/밤 팔레트를 갈아끼운다.
실제 서비스도 이 스크립트로 관광지별 SVG를 만들어 캐시한다.
"""
import urllib.request, urllib.parse, json, math, io, sys, os, hashlib, random
from collections import OrderedDict

sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')
UA = {'User-Agent': 'checkin-student-mockup/1.0 (2024001910@gsuite.induk.ac.kr)'}
CACHE = 'osm_cache'
os.makedirs(CACHE, exist_ok=True)
ISO_C, ISO_S = math.cos(math.radians(30)), math.sin(math.radians(30))
FAM = ['a', 'b', 'c', 'd', 'e', 'f', 'g', 'h']
BANDS = 190


def overpass(q):
    p = os.path.join(CACHE, hashlib.sha1(q.encode()).hexdigest()[:16] + '.json')
    if os.path.exists(p):
        return json.load(open(p, encoding='utf-8'))
    d = urllib.request.urlopen(urllib.request.Request(
        os.environ.get('OVERPASS', 'https://overpass-api.de/api/interpreter'),
        data=urllib.parse.urlencode({'data': q}).encode(), headers=UA), timeout=300).read()
    j = json.loads(d)
    json.dump(j, open(p, 'w', encoding='utf-8'))
    return j


def fetch(bbox):
    b = '%f,%f,%f,%f' % bbox
    q = """
[out:json][timeout:300];
(
  way["building"](%s);
  relation["building"](%s);
  way["highway"~"^(motorway|trunk|primary|secondary|tertiary|residential|unclassified|living_street|pedestrian|footway|service)$"](%s);
  way["natural"~"^(beach|water|coastline|wood|scrub|sand)$"](%s);
  way["landuse"~"^(grass|forest|recreation_ground|cemetery|village_green)$"](%s);
  way["leisure"~"^(park|pitch|garden|golf_course|playground)$"](%s);
  way["waterway"="river"](%s);
  node["natural"="tree"](%s);
);
out geom tags;
""" % ((b,) * 8)
    return overpass(q)


def hgt(t):
    for k in ('height', 'building:height'):
        v = t.get(k)
        if v:
            try:
                return max(2.5, float(str(v).replace('m', '').strip()))
            except ValueError:
                pass
    for k in ('building:levels', 'levels'):
        v = t.get(k)
        if v:
            try:
                return max(2.5, float(str(v).split(';')[0]) * 3.1)
            except ValueError:
                pass
    return {'house': 6.0, 'detached': 6.0, 'bungalow': 3.8, 'hut': 3.0, 'shed': 3.0,
            'garage': 3.0, 'garages': 3.2, 'roof': 3.4, 'kiosk': 3.2, 'retail': 8.5,
            'commercial': 15.0, 'office': 21.0, 'hotel': 34.0, 'apartments': 40.0,
            'residential': 17.0, 'school': 13.0, 'church': 13.0,
            'industrial': 9.5, 'warehouse': 9.5}.get(t.get('building', 'yes'), 9.5)


def family(t, h, sd):
    """지붕 색 계열. a·d 붉은 기와 / b·e 청회 / c·f 크림·모래 / g 파란 슬레이트 / h 갈색"""
    b = t.get('building', 'yes')
    if b in ('house', 'detached', 'bungalow', 'terrace'):
        return ('a', 'd', 'a', 'g', 'a', 'h', 'd', 'a')[sd % 8]
    if b in ('hut', 'shed', 'garage', 'garages'):
        return ('a', 'h', 'g', 'd')[sd % 4]
    if b in ('apartments', 'residential') or h >= 30:
        return ('b', 'a', 'e', 'a', 'b', 'd', 'e', 'a')[sd % 8]
    if b in ('retail', 'commercial', 'kiosk', 'supermarket'):
        return ('c', 'a', 'f', 'd', 'c', 'h')[sd % 6]
    if b == 'hotel':
        return 'd'
    if b in ('school', 'church', 'public', 'civic', 'government', 'hospital'):
        return ('e', 'g')[sd % 2]
    if b in ('industrial', 'warehouse', 'roof'):
        return ('e', 'a', 'g', 'f')[sd % 4]
    return ('a', 'd', 'c', 'a', 'f', 'h', 'g', 'a')[sd % 8]


def dedup(pts, eps=0.35):
    out = []
    for p in pts:
        if not out or abs(p[0] - out[-1][0]) > eps or abs(p[1] - out[-1][1]) > eps:
            out.append(p)
    if len(out) > 2 and abs(out[0][0] - out[-1][0]) < eps and abs(out[0][1] - out[-1][1]) < eps:
        out.pop()
    return out


def area2(r):
    a = 0.0
    for i in range(len(r)):
        x1, y1 = r[i]; x2, y2 = r[(i + 1) % len(r)]
        a += x1 * y2 - x2 * y1
    return a / 2


def inside(pt, ring):
    x, y = pt; c = False; n = len(ring)
    for i in range(n):
        x1, y1 = ring[i]; x2, y2 = ring[(i - 1) % n]
        if (y1 > y) != (y2 > y) and x < (x2 - x1) * (y - y1) / (y2 - y1 + 1e-12) + x1:
            c = not c
    return c


def dist(a, b):
    return math.hypot(a[0] - b[0], a[1] - b[1])


def scatter(rings, count, gap, seed, to_screen, clusters=0, spread=60.0, keep=None):
    rng = random.Random(seed)
    xs = [p[0] for r in rings for p in r]; ys = [p[1] for r in rings for p in r]
    if not xs:
        return []
    x0, x1, y0, y1 = min(xs), max(xs), min(ys), max(ys)
    cen = []
    if clusters:
        guard = 0
        while len(cen) < clusters and guard < clusters * 500:
            guard += 1
            p = (rng.uniform(x0, x1), rng.uniform(y0, y1))
            if any(inside(p, r) for r in rings) and (keep is None or keep(p)):
                cen.append((p[0], p[1], rng.uniform(.55, 1.0)))
        if not cen:
            return []
    grid, put, tries = {}, [], 0
    while len(put) < count and tries < count * 300:
        tries += 1
        if cen:
            cx, cy, wgt = cen[rng.randrange(len(cen))]
            if rng.random() > wgt:
                continue
            p = (rng.gauss(cx, spread), rng.gauss(cy, spread * .62))
        else:
            p = (rng.uniform(x0, x1), rng.uniform(y0, y1))
        if not any(inside(p, r) for r in rings):
            continue
        if keep is not None and not keep(p):
            continue
        sx, sy = to_screen(p)
        gx, gy = int(sx / gap), int(sy / gap)
        ok = True
        for i in (-1, 0, 1):
            for k in (-1, 0, 1):
                for q in grid.get((gx + i, gy + k), ()):
                    if (q[0] - sx) ** 2 + (q[1] - sy) ** 2 < gap * gap:
                        ok = False; break
                if not ok: break
            if not ok: break
        if not ok:
            continue
        grid.setdefault((gx, gy), []).append((sx, sy))
        put.append((p, sx, sy))
    return put


def build(name, bbox, rot_deg, px_per_m, marks=(), kz=0.74, min_area=26.0, out_w=1600,
          crowd_on=('beach',), crowd_target=1150, tree_target=560,
          parasols=150, cars=140, route=(), route2=(), benches=170, bushes=180,
          seed=11):
    j = fetch(bbox)
    s, w, n, e = bbox
    lat0, lon0 = (s + n) / 2, (w + e) / 2
    mx = 111320.0 * math.cos(math.radians(lat0)); my = 110540.0
    ct, st = math.cos(math.radians(rot_deg)), math.sin(math.radians(rot_deg))

    def plan(lat, lon):
        x = (lon - lon0) * mx; y = -(lat - lat0) * my
        return (x * ct - y * st, x * st + y * ct)

    def scr(p, h=0.0):
        return ((p[0] - p[1]) * ISO_C * px_per_m,
                (p[0] + p[1]) * ISO_S * px_per_m - h * kz * px_per_m)

    bldgs, grounds, roads, shore, tnodes = [], [], [], [], []
    for el in j['elements']:
        t = el.get('tags', {})
        if el.get('type') == 'node':
            if t.get('natural') == 'tree':
                tnodes.append(plan(el['lat'], el['lon']))
            continue
        g = el.get('geometry') or []
        if len(g) < 2:
            continue
        pts = dedup([plan(p['lat'], p['lon']) for p in g])
        if 'building' in t:
            if len(pts) < 3 or abs(area2(pts)) < min_area:
                continue
            if area2(pts) < 0:
                pts = pts[::-1]
            h = hgt(t)
            sd = int(hashlib.md5(('%.1f_%.1f' % (pts[0][0], pts[0][1])).encode()).hexdigest()[:6], 16)
            bldgs.append([pts, h, family(t, h, sd), False, abs(area2(pts))])
        elif 'highway' in t:
            roads.append((pts, t['highway']))
        else:
            if t.get('natural') == 'coastline':
                shore.append(pts); continue
            kind = ('water' if t.get('natural') == 'water' or t.get('waterway') == 'river'
                    else 'beach' if t.get('natural') in ('beach', 'sand')
                    else 'green')
            if len(pts) >= 3:
                grounds.append((pts, kind))

    hits = []
    for mk in marks:
        p = plan(mk['lat'], mk['lon'])
        found = None
        for b in bldgs:
            if inside(p, b[0]):
                found = b; break
        if found is None:
            r2 = mk.get('snap', 70) ** 2
            near = [b for b in bldgs
                    if (sum(q[0] for q in b[0]) / len(b[0]) - p[0]) ** 2
                    + (sum(q[1] for q in b[0]) / len(b[0]) - p[1]) ** 2 <= r2]
            if near:
                found = max(near, key=lambda b: b[4])
        if found is not None and not mk.get('ground'):
            found[3] = True
            hits.append((mk, found))
        else:
            hits.append((mk, None))

    allp = []
    for b in bldgs:
        for p in b[0]:
            allp.append(scr(p, 0)); allp.append(scr(p, b[1]))
    for rt in (route, route2):
        for r in rt:
            allp.append(scr(plan(r['lat'], r['lon']), 0))
    for mk_ in marks:
        allp.append(scr(plan(mk_['lat'], mk_['lon']), 0))
    if not allp:
        raise SystemExit('no buildings: ' + name)
    xs = [p[0] for p in allp]; ys = [p[1] for p in allp]
    x0, x1, y0, y1 = min(xs), max(xs), min(ys), max(ys)
    pad = 30.0
    sc = out_w / (x1 - x0 + pad * 2)
    W = out_w; H = (y1 - y0 + pad * 2) * sc

    def T(p):
        return ((p[0] - x0 + pad) * sc, (p[1] - y0 + pad) * sc)

    def poly(q):
        return 'M' + 'L'.join('%.1f %.1f' % (a, b) for a, b in q) + 'Z'

    def ring(pts, h=0.0):
        return poly([T(scr(p, h)) for p in pts])

    def line(pts):
        return 'M' + 'L'.join('%.1f %.1f' % T(scr(p)) for p in pts)

    def circ(cx, cy, r):
        return ('M%.1f %.1fa%.1f %.1f 0 1 0 %.1f 0a%.1f %.1f 0 1 0 -%.1f 0Z'
                % (cx - r, cy, r, r, r * 2, r, r, r * 2))

    def ellip(cx, cy, rx, ry):
        return ('M%.1f %.1fa%.1f %.1f 0 1 0 %.1f 0a%.1f %.1f 0 1 0 -%.1f 0Z'
                % (cx - rx, cy, rx, ry, rx * 2, rx, ry, rx * 2))

    rng = random.Random(seed)
    out = ['<rect x="0" y="0" width="%.0f" height="%.0f" class="plate"/>' % (W, H)]

    # ── 바다: 해안선을 프레임 아래로 닫는다 + 파도선
    sea, waves = [], {1: [], 2: [], 3: []}
    for p in shore:
        if len(p) < 4:
            continue
        q = [T(scr(v)) for v in p]
        if math.hypot(q[0][0] - q[-1][0], q[0][1] - q[-1][1]) < 12:
            continue
        if abs(q[-1][0] - q[0][0]) < W * .22:
            continue
        d = 'M' + 'L'.join('%.1f %.1f' % v for v in q)
        sea.append(d + 'L%.1f %.1fL%.1f %.1fL%.1f %.1fL%.1f %.1fZ'
                   % (W + 60, q[-1][1], W + 60, H + 60, -60, H + 60, -60, q[0][1]))
        for k, (dx, dy) in enumerate(((9, 6), (20, 13), (33, 22)), start=1):
            waves[k].append('M' + 'L'.join('%.1f %.1f' % (a + dx, b + dy) for a, b in q))
    if sea:
        out.append('<path class="g-water sea" d="%s"/>' % ''.join(sea))

    for pts, kind in sorted(grounds, key=lambda g: -abs(area2(g[0]))):
        out.append('<path class="g-%s" d="%s"/>' % (kind, ring(pts)))

    gr = [pts for pts, k in grounds if k == 'green']
    bch = [pts for pts, k in grounds if k == 'beach']
    if gr:
        tuft = []
        for _q, sx, sy in scatter(gr, 520, 11.0, seed + 61, lambda q: T(scr(q, 0))):
            tuft.append('M%.1f %.1fl1.4 -2.7M%.1f %.1fl-1.3 -2.4M%.1f %.1fl0 -3.1'
                        % (sx, sy, sx, sy, sx, sy))
        if tuft:
            out.append('<path class="gt" d="%s"/>' % ''.join(tuft))
    if bch:
        spk = []
        for _q, sx, sy in scatter(bch, 700, 9.0, seed + 67, lambda q: T(scr(q, 0))):
            spk.append('M%.1f %.1fh.01' % (sx, sy))
        if spk:
            out.append('<path class="ss" d="%s"/>' % ''.join(spk))
    edges = [ring(pts) for pts, k in grounds if k in ('green', 'beach')]
    if edges:
        out.append('<path class="ge" d="%s"/>' % ''.join(edges))
    for k in (3, 2, 1):
        if waves[k]:
            out.append('<path class="wv w%d" d="%s"/>' % (k, ''.join(waves[k])))
    if shore:
        out.append('<path class="cl" d="%s"/>' % ''.join(line(p) for p in shore if len(p) > 1))

    ROAD_BIG = ('motorway', 'trunk', 'primary', 'secondary')
    ROAD_MID = ('tertiary', 'residential', 'unclassified', 'living_street')
    parkrings = [pts for pts, k in grounds if k == 'green']

    def in_park(pts):
        m = pts[len(pts) // 2]
        return any(inside(m, r) for r in parkrings)

    big = [line(p) for p, c in roads if c in ROAD_BIG]
    mid = [line(p) for p, c in roads if c in ROAD_MID]
    walk = [(p, c) for p, c in roads if c in ('footway', 'pedestrian', 'service')]
    sml = [line(p) for p, c in walk if not in_park(p)]
    pth = [line(p) for p, c in walk if in_park(p)]
    for cls, ds in (('rdc', big + mid), ('rd1', big), ('rd2', mid), ('rd3', sml),
                    ('rdp', pth), ('rdm', big)):
        ds = [d for d in ds if d]
        if ds:
            out.append('<path class="%s" d="%s"/>' % (cls, ''.join(ds)))

    # ── 나무
    def mk(r):
        return (r, 'p' if rng.random() < .28 else 'r', 1 if rng.random() < .42 else 2)

    tr = [(p, mk(rng.uniform(5.4, 7.4))) for p in tnodes]
    CELL = 45.0
    bgrid = {}
    for b in bldgs:
        bx = [q[0] for q in b[0]]; by = [q[1] for q in b[0]]
        for gx in range(int(min(bx) // CELL), int(max(bx) // CELL) + 1):
            for gy in range(int(min(by) // CELL), int(max(by) // CELL) + 1):
                bgrid.setdefault((gx, gy), []).append(b[0])

    def on_building(p):
        for r in bgrid.get((int(p[0] // CELL), int(p[1] // CELL)), ()):
            if inside(p, r):
                return True
        return False

    park = [pts for pts, k in grounds if k == 'green']
    if park and tree_target:
        tot = sum(abs(area2(r)) for r in park) or 1.0
        for r in park:
            a = abs(area2(r)); share = a / tot
            cnt = min(64, max(3, int(tree_target * (share ** .58))))
            for p, _sx, _sy in scatter([r], cnt, 22.0 if a > 20000 else 17.0,
                                       seed + 3 + int(share * 1000), lambda q: T(scr(q, 0))):
                tr.append((p, mk(rng.uniform(5.2, 8.0))))
    STEP, OFF = 46.0, 7.5
    for pts, cls in roads:
        if cls not in ROAD_MID + ('secondary', 'pedestrian'):
            continue
        acc, side = rng.uniform(0, STEP), 1
        for i in range(len(pts) - 1):
            a, b = pts[i], pts[i + 1]
            seg = math.hypot(b[0] - a[0], b[1] - a[1])
            if seg < 1e-6:
                continue
            ux, uy = (b[0] - a[0]) / seg, (b[1] - a[1]) / seg
            t = STEP - acc
            while t < seg:
                p = (a[0] + ux * t - uy * OFF * side, a[1] + uy * t + ux * OFF * side)
                if not on_building(p):
                    tr.append((p, mk(rng.uniform(4.4, 6.6))))
                side = -side
                t += STEP
            acc = (acc + seg) % STEP

    # ── 해변 파라솔 (물가 가까이)
    shore_pts = [v for p in shore for v in p]
    def near_shore(p, lim=92.0):
        return any((v[0] - p[0]) ** 2 + (v[1] - p[1]) ** 2 < lim * lim for v in shore_pts[::3])

    beach = [pts for pts, k in grounds if k == 'beach']
    pars = []
    if beach and parasols:
        for p, _sx, _sy in scatter(beach, parasols, 15.0, seed + 31, lambda q: T(scr(q, 0)),
                                   clusters=14, spread=110.0, keep=near_shore):
            pars.append((p, rng.uniform(5.6, 7.4), rng.choice((1, 1, 2, 3))))

    # ── 벤치: 산책로·인도 따라
    bench = []
    for pts, c in walk:
        if len(pts) < 2 or len(bench) >= benches:
            continue
        acc = rng.uniform(0, 34.0)
        for i in range(len(pts) - 1):
            a, b = pts[i], pts[i + 1]
            seg = math.hypot(b[0] - a[0], b[1] - a[1])
            if seg < 1e-6:
                continue
            ux, uy = (b[0] - a[0]) / seg, (b[1] - a[1]) / seg
            t = 34.0 - acc
            while t < seg and len(bench) < benches:
                sd = 1 if rng.random() < .5 else -1
                q = (a[0] + ux * t - uy * 3.0 * sd, a[1] + uy * t + ux * 3.0 * sd)
                if not on_building(q):
                    bench.append((q, (ux, uy)))
                t += 34.0
            acc = (acc + seg) % 34.0

    # ── 관목
    bush = []
    if parkrings and bushes:
        for _q, sx, sy in scatter(parkrings, bushes, 15.0, seed + 91,
                                  lambda q: T(scr(q, 0))):
            bush.append((_q, rng.uniform(3.2, 4.8)))

    # ── 바다 보트
    boats = []
    for pl in [x for x in shore if len(x) >= 8][:3]:
        step = max(3, len(pl) // 7)
        for i in range(6, len(pl) - 2, step):
            if len(boats) >= 9:
                break
            v = pl[i]
            off = rng.uniform(90, 320)
            boats.append(((v[0] + off * .62, v[1] + off * .78),
                          rng.uniform(6.0, 9.5), rng.choice((1, 2))))

    # ── 차량 (큰 길 위)
    cs = []
    if cars:
        CSTEP = 96.0
        for pts, cls in roads:
            if cls not in ROAD_BIG:
                continue
            acc = rng.uniform(0, CSTEP)
            for i in range(len(pts) - 1):
                a, b = pts[i], pts[i + 1]
                seg = math.hypot(b[0] - a[0], b[1] - a[1])
                if seg < 1e-6:
                    continue
                ux, uy = (b[0] - a[0]) / seg, (b[1] - a[1]) / seg
                t = CSTEP - acc
                while t < seg and len(cs) < cars:
                    sd = 1 if rng.random() < .5 else -1
                    p = (a[0] + ux * t - uy * 3.4 * sd, a[1] + uy * t + ux * 3.4 * sd)
                    if not on_building(p):
                        cs.append((p, (ux, uy), rng.choice((1, 1, 2, 3)),
                                   1.9 if rng.random() < .16 else 1.0))
                    t += CSTEP
                acc = (acc + seg) % CSTEP

    # ── 옥상 설비 (넓고 높은 평지붕)
    units = []
    for b in bldgs:
        if b[3] or b[4] < 520 or b[1] < 12:
            continue
        cx = sum(q[0] for q in b[0]) / len(b[0]); cy = sum(q[1] for q in b[0]) / len(b[0])
        sz = rng.uniform(3.4, 6.2)          # 미터
        uh = rng.uniform(2.2, 3.6)
        foot = [(cx - sz, cy - sz), (cx + sz, cy - sz), (cx + sz, cy + sz), (cx - sz, cy + sz)]
        units.append((foot, b[1], uh))

    # ── 그림자: 필터 없이 2겹 오프셋
    def off(d, dx, dy):
        import re as _re
        return _re.sub(r'(-?\d+\.?\d*) (-?\d+\.?\d*)',
                       lambda m: '%.1f %.1f' % (float(m.group(1)) + dx, float(m.group(2)) + dy), d)

    sh1, sh2 = [], []
    for b in bldgs:
        d = ring(b[0])
        sh1.append(off(d, 2.4, 1.9)); sh2.append(off(d, 5.4, 4.2))
    for p, (r, _k, _t) in tr:
        bx, by = T(scr(p, 0))
        sh1.append(ellip(bx + 1.6, by + 1.2, r * .82, r * .48))
    for p, r, _c in pars:
        bx, by = T(scr(p, 0))
        sh1.append(ellip(bx + 1.4, by + 1.0, r * .9, r * .5))
    if sh2:
        out.append('<path class="sh2" d="%s"/>' % ''.join(sh2))
    if sh1:
        out.append('<path class="sh1" d="%s"/>' % ''.join(sh1))

    # ── 깊이 밴드로 병합
    items = []
    for b in bldgs:
        items.append((sum(p[0] + p[1] for p in b[0]) / len(b[0]), 0, 'b', b))
    for p, t in tr:
        items.append((p[0] + p[1], 1, 't', (p, t)))
    for p, r, c in pars:
        items.append((p[0] + p[1], 1, 'u', (p, r, c)))
    for p, u, c, ln in cs:
        items.append((p[0] + p[1], 1, 'v', (p, u, c, ln)))
    for p, r, c in boats:
        items.append((p[0] + p[1], 1, 'z', (p, r, c)))
    for q, u in bench:
        items.append((q[0] + q[1], 1, 'e', (q, u)))
    for q, r in bush:
        items.append((q[0] + q[1], 1, 'x', (q, r)))
    for foot, h, uh in units:
        cxy = (sum(q[0] for q in foot) / 4, sum(q[1] for q in foot) / 4)
        items.append((cxy[0] + cxy[1], 1, 'w', (foot, h, uh)))
    if not items:
        raise SystemExit('empty')
    dmin = min(i[0] for i in items); dmax = max(i[0] for i in items) or 1.0
    span = (dmax - dmin) or 1.0
    for it in items:
        pass
    buckets = [[] for _ in range(BANDS + 1)]
    for d, pri, kind, obj in items:
        buckets[min(BANDS, int((d - dmin) / span * BANDS))].append((pri, kind, obj))

    faces = 0
    for bucket in buckets:
        if not bucket:
            continue
        acc = OrderedDict()

        def add(cls, d):
            acc.setdefault(cls, []).append(d)

        for pri, kind, obj in sorted(bucket, key=lambda z: z[0]):
            if kind == 'b':
                pts, h, fam, mark, _a = obj
                f = 'm' if mark else fam
                m = len(pts)
                for i in range(m):
                    a, b = pts[i], pts[(i + 1) % m]
                    nx, ny = (b[1] - a[1]), -(b[0] - a[0])
                    if nx + ny <= 0:
                        continue
                    q = [T(scr(a, 0)), T(scr(b, 0)), T(scr(b, h)), T(scr(a, h))]
                    add(('s1 f%s' % f) if nx >= ny else ('s2 f%s' % f), poly(q))
                    faces += 1
                gabled = False
                if m == 4 and h <= 9.5 and not mark:
                    d0, d1 = dist(pts[0], pts[1]), dist(pts[1], pts[2])
                    if min(d0, d1) > 2.5:
                        if d0 >= d1:
                            A, B, C, D, short = pts[0], pts[1], pts[2], pts[3], d1
                        else:
                            A, B, C, D, short = pts[1], pts[2], pts[3], pts[0], d0
                        rise = min(3.0, short * .32)
                        r1 = ((A[0] + D[0]) / 2, (A[1] + D[1]) / 2)
                        r2 = ((B[0] + C[0]) / 2, (B[1] + C[1]) / 2)
                        Rt1, Rt2 = T(scr(r1, h + rise)), T(scr(r2, h + rise))
                        add('s2 f%s' % f, poly([T(scr(B, h)), T(scr(C, h)), Rt2]))
                        add('s2 f%s' % f, poly([T(scr(D, h)), T(scr(A, h)), Rt1]))
                        pl1 = [T(scr(A, h)), T(scr(B, h)), Rt2, Rt1]
                        pl2 = [T(scr(C, h)), T(scr(D, h)), Rt1, Rt2]
                        dep1 = (A[0] + A[1] + B[0] + B[1]) / 2
                        dep2 = (C[0] + C[1] + D[0] + D[1]) / 2
                        f1, f2 = (pl1, pl2) if dep1 <= dep2 else (pl2, pl1)
                        add('rk f%s' % f, poly(f1))
                        add('rf f%s' % f, poly(f2))
                        gabled = True
                if not gabled:
                    add('rf f%s' % f, ring(pts, h))
            elif kind == 't':
                p, (r, tk_, tn) = obj
                bx, by = T(scr(p, 0))
                trunk = r * (1.30 if tk_ == 'p' else 1.02)
                add('tk', 'M%.1f %.1fl0 -%.1f' % (bx, by, trunk))
                cy = by - trunk - r * .58
                if tk_ == 'p':
                    add('tp t%d' % tn, poly([(bx, cy - r * 1.62), (bx - r * .88, cy + r * .58),
                                             (bx + r * .88, cy + r * .58)]))
                    add('tp t%d' % tn, poly([(bx, cy - r * .62), (bx - r * 1.02, cy + r * .96),
                                             (bx + r * 1.02, cy + r * .96)]))
                else:
                    add('tc t%d' % tn, circ(bx, cy, r))
                    add('tc td', circ(bx - r * .30, cy + r * .30, r * .62))
            elif kind == 'u':
                p, r, c = obj
                bx, by = T(scr(p, 0))
                top = by - r * 1.42
                add('uk', 'M%.1f %.1fl0 -%.1f' % (bx, by, r * 1.42))
                add('us u%d' % c, 'M%.1f %.1fa%.1f %.1f 0 0 1 %.1f 0Z'
                    % (bx - r, top, r, r * .82, r * 2))
            elif kind == 'e':
                q, (ux, uy) = obj
                bx, by = T(scr(q, 0))
                sx1 = (ux - uy) * ISO_C * px_per_m * sc
                sy1 = (ux + uy) * ISO_S * px_per_m * sc
                nl = math.hypot(sx1, sy1) or 1.0
                ax, ay = sx1 / nl * 3.4, sy1 / nl * 3.4
                add('bn', 'M%.1f %.1fL%.1f %.1f'
                    % (bx - ax, by - ay - 1.4, bx + ax, by + ay - 1.4))
                add('bnb', 'M%.1f %.1fL%.1f %.1f'
                    % (bx - ax * .9, by - ay * .9 - 3.6, bx + ax * .9, by + ay * .9 - 3.6))
            elif kind == 'x':
                q, r = obj
                bx, by = T(scr(q, 0))
                add('bu', circ(bx, by - r * .42, r))
                add('bu2', circ(bx - r * .48, by - r * .1, r * .62))
            elif kind == 'z':
                p, r, c = obj
                bx, by = T(scr(p, 0))
                add('btw', 'M%.1f %.1fl%.1f %.1f'
                    % (bx - r * 2.1, by + r * .36, r * 1.6, r * .22))
                add('bt k%d' % c, poly([(bx - r, by), (bx + r, by - r * .18),
                                        (bx + r * .72, by + r * .44),
                                        (bx - r * .78, by + r * .44)]))
                add('bs', 'M%.1f %.1fl0 -%.1f' % (bx + r * .12, by - r * .1, r * 1.2))
            elif kind == 'v':
                p, (ux, uy), c, ln = obj
                bx, by = T(scr(p, 0))
                sx1, sy1 = (ux - uy) * ISO_C * px_per_m * sc, (ux + uy) * ISO_S * px_per_m * sc
                nl = math.hypot(sx1, sy1) or 1.0
                ax, ay = sx1 / nl * 5.2 * ln, sy1 / nl * 5.2 * ln
                px_, py_ = -ay * .62, ax * .62
                add('cb c%d' % c, poly([(bx - ax + px_, by - ay + py_), (bx + ax + px_, by + ay + py_),
                                        (bx + ax - px_, by + ay - py_), (bx - ax - px_, by - ay - py_)]))
                add('ct', poly([(bx - ax * .45 + px_ * .7, by - ay * .45 + py_ * .7 - 2.4),
                                (bx + ax * .45 + px_ * .7, by + ay * .45 + py_ * .7 - 2.4),
                                (bx + ax * .45 - px_ * .7, by + ay * .45 - py_ * .7 - 2.4),
                                (bx - ax * .45 - px_ * .7, by - ay * .45 - py_ * .7 - 2.4)]))
            elif kind == 'w':
                foot, h, uh = obj
                for i in range(4):
                    a, b = foot[i], foot[(i + 1) % 4]
                    nx, ny = (b[1] - a[1]), -(b[0] - a[0])
                    if nx + ny <= 0:
                        continue
                    add('ru2', poly([T(scr(a, h)), T(scr(b, h)),
                                     T(scr(b, h + uh)), T(scr(a, h + uh))]))
                add('ru', ring(foot, h + uh))

        for cls, ds in acc.items():
            out.append('<path class="%s" d="%s"/>' % (cls, ''.join(ds)))

    # ── 군중
    crings = [pts for pts, kind in grounds if kind in crowd_on]
    if crings and crowd_target:
        pp = scatter(crings, crowd_target, 8.4, seed + 7, lambda q: T(scr(q, 0)),
                     clusters=16, spread=90.0)
        r2 = random.Random(seed + 21)
        bands = {}
        for _p, sx, sy in pp:
            kid = r2.random() < .18
            bands.setdefault(r2.randint(1, 5), []).append(
                (sx + r2.uniform(-.6, .6), sy,
                 r2.uniform(5.6, 6.6) if kid else r2.uniform(7.4, 9.2),
                 r2.randint(1, 5)))
        parts = []
        for b in sorted(bands):
            mine = bands[b]
            g = ['<g class="cw b%d">' % b]
            def fig_leg(x, y, hh):
                w = hh * .17
                return ('M%.1f %.1fL%.1f %.1fM%.1f %.1fL%.1f %.1f'
                        % (x - w * .55, y - hh * .34, x - w * .85, y,
                           x + w * .55, y - hh * .34, x + w * .85, y))

            def fig_body(x, y, hh):
                w = hh * .21
                yt = y - hh * .74
                yb = y - hh * .30
                return ('M%.1f %.1fL%.1f %.1fL%.1f %.1fL%.1f %.1fZ'
                        % (x - w, yt, x + w, yt, x + w * .66, yb, x - w * .66, yb))

            def fig_head(x, y, hh):
                r = hh * .175
                cy = y - hh * .84
                return ('M%.1f %.1fa%.1f %.1f 0 1 0 %.1f 0a%.1f %.1f 0 1 0 -%.1f 0Z'
                        % (x - r, cy, r, r, r * 2, r, r, r * 2))

            g.append('<path class="leg" d="%s"/>'
                     % ''.join(fig_leg(x, y, hh) for x, y, hh, _c in mine))
            for col in range(1, 6):
                sel = [m for m in mine if m[3] == col]
                if not sel:
                    continue
                g.append('<path class="tor k%d" d="%s"/>'
                         % (col, ''.join(fig_body(x, y, hh) for x, y, hh, _c in sel)))
            g.append('<path class="hed" d="%s"/>'
                     % ''.join(fig_head(x, y, hh) for x, y, hh, _c in mine))
            g.append('</g>')
            parts.append(''.join(g))
        if parts:
            out.append('<g class="crowd">%s</g>' % ''.join(parts))

    def emit_route(rt, pfx):
        if not rt:
            return []
        pr = [T(scr(plan(r['lat'], r['lon']), 0)) for r in rt]
        segs = {'done': [], 'todo': [], 'hot': []}
        for i in range(len(pr) - 1):
            k = rt[i + 1].get('seg', 'todo')
            segs.setdefault(k, []).append('M%.1f %.1fL%.1f %.1f'
                                          % (pr[i][0], pr[i][1], pr[i + 1][0], pr[i + 1][1]))
        allseg = [d for v in segs.values() for d in v]
        if allseg:
            out.append('<path class="%scase" d="%s"/>' % (pfx, ''.join(allseg)))
        for k in ('done', 'todo', 'hot'):
            if segs.get(k):
                out.append('<path class="%s%s" d="%s"/>' % (pfx, k, ''.join(segs[k])))
        pins, no = [], 0
        for i, r in enumerate(rt):
            if not r.get('pin', True):
                continue
            no += 1
            pins.append(dict(no=no, name=r['name'], note=r.get('note', ''),
                             kind=r.get('kind', 'stop'),
                             x=pr[i][0] / W * 100, y=pr[i][1] / H * 100,
                             dy=r.get('dy', 0)))
        return pins

    rpins2 = emit_route(route2, 'rb-')
    rpins = []
    if route:
        pr = [T(scr(plan(r['lat'], r['lon']), 0)) for r in route]
        segs = {'done': [], 'todo': [], 'hot': []}
        for i in range(len(pr) - 1):
            kseg = route[i + 1].get('seg', 'todo')
            segs.setdefault(kseg, []).append('M%.1f %.1fL%.1f %.1f'
                                           % (pr[i][0], pr[i][1], pr[i + 1][0], pr[i + 1][1]))
        allseg = [d for v in segs.values() for d in v]
        if allseg:
            out.append('<path class="rt-case" d="%s"/>' % ''.join(allseg))
        for k in ('done', 'todo', 'hot'):
            if segs.get(k):
                out.append('<path class="rt-%s" d="%s"/>' % (k, ''.join(segs[k])))
        no = 0
        for i, r in enumerate(route):
            if not r.get('pin', True):
                continue
            no += 1
            rpins.append(dict(no=no, name=r['name'], note=r.get('note', ''),
                              kind=r.get('kind', 'stop'),
                              x=pr[i][0] / W * 100, y=pr[i][1] / H * 100,
                              dy=r.get('dy', 0)))

    svg = ('<svg class="mass" viewBox="0 0 %.0f %.0f" xmlns="http://www.w3.org/2000/svg" '
           'shape-rendering="geometricPrecision" aria-hidden="true">%s</svg>'
           % (W, H, ''.join(out)))
    open('mass_%s.svg' % name, 'w', encoding='utf-8').write(svg)

    anchors = []
    for mk_, b in hits:
        if b is None:
            p = T(scr(plan(mk_['lat'], mk_['lon']), 0))
            anchors.append(dict(name=mk_['name'], note=mk_.get('note', ''), kind='ground',
                                x=p[0] / W * 100, y=p[1] / H * 100, h=0, dy=mk_.get('dy', 0)))
            continue
        pts, h = b[0], b[1]
        top = min((T(scr(p, h)) for p in pts), key=lambda q: q[1])
        anchors.append(dict(name=mk_['name'], note=mk_.get('note', ''), kind=mk_.get('kind', 'bldg'),
                            x=top[0] / W * 100, y=top[1] / H * 100, h=round(h), dy=mk_.get('dy', 0)))
    json.dump(dict(w=round(W), h=round(H), ar=round(W / H, 4), anchors=anchors,
                   route=rpins, route2=rpins2),
              open('mass_%s.json' % name, 'w', encoding='utf-8'), ensure_ascii=False, indent=1)

    import re as _re
    nodes = len(_re.findall(r'<\w+', svg))
    print('   벤치=%d 관목=%d 보트=%d' % (len(bench), len(bush), len(boats)))
    print('%-9s bldg=%-4d faces=%-5d tree=%-4d 파라솔=%-4d 차량=%-4d 옥상=%-4d  '
          '%.0fx%.0f ar=%.4f  %dKB  노드=%d'
          % (name, len(bldgs), faces, len(tr), len(pars), len(cs), len(units),
             W, H, W / H, len(svg) // 1024, nodes))
    for a in anchors:
        print('   · %-18s %-6s h=%-4s  %.1f%%, %.1f%%' % (a['name'], a['kind'], a['h'], a['x'], a['y']))
    return svg


HAEUNDAE_MARKS = [
    dict(name='두산위브더제니스', note='300m · 80층', lat=35.15690, lon=129.14605, snap=90),
    dict(name='파라다이스 호텔', note='오늘 숙소', lat=35.15878, lon=129.16090, snap=130, kind='stay'),
    dict(name='해운대해수욕장', note='집중률 142', lat=35.15760, lon=129.15760, ground=True, kind='hot', dy=-46),
]

if __name__ == '__main__':
    build('haeundae', (35.1505, 129.1440, 35.1625, 129.1665), 16, 0.74, marks=HAEUNDAE_MARKS)
