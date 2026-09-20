# -*- coding: utf-8 -*-
"""시군구마다 「사람들이 제일 많이 가는 곳」 좌표를 모은다.

관광공사 거점 관광지 1위를 쓴다. 그 지역에서 실제로 방문이 몰리는 자리라
동선도 그 근처에 생긴다 — 시청 좌표보다 쓸모 있다.
없으면 출발지 검색으로 「<시군구>청」을 찾아 대신 쓴다.
"""
import json, io, os, sys, time, urllib.request, urllib.parse

B = os.environ.get('CK', 'http://localhost:8070')
TOK = None

def call(path, t=90):
    r = urllib.request.Request(B+path)
    if TOK: r.add_header('Authorization','Bearer '+TOK)
    try:
        with urllib.request.urlopen(r, timeout=t) as f:
            return json.loads(f.read().decode('utf-8'))
    except Exception as e:
        return {'success': False, 'why': type(e).__name__}

def login():
    global TOK
    d=json.dumps({'username':'openapi','password':'2026openapi!'}).encode()
    r=urllib.request.Request(B+'/api/auth/login', data=d)
    r.add_header('Content-Type','application/json')
    with urllib.request.urlopen(r, timeout=60) as f:
        TOK=json.load(f)['data']['accessToken']

login()
want = json.load(io.open('sigungu.json', encoding='utf-8'))
done = {}
if os.path.exists('coords.json'):
    done = {d['key']: d for d in json.load(io.open('coords.json', encoding='utf-8'))}

out = list(done.values())
for i, a in enumerate(want):
    key = a['sido'] + ' ' + a['sigungu']
    if key in done: continue
    q = urllib.parse.urlencode({'destination': key})
    d = call('/api/tour/area-info?' + q)
    lat = lng = None; src = None
    hubs = ((d.get('data') or {}).get('hubs') or {}).get('items') or []
    for h in hubs:
        if h.get('lat') and h.get('lng'):
            lat, lng, src = h['lat'], h['lng'], '거점:' + (h.get('name') or '')
            break
    if lat is None:
        s2 = call('/api/tour/origin-search?' + urllib.parse.urlencode({'q': a['sigungu'] + '청'}))
        it = ((s2.get('data') or {}).get('items') or [])
        if it and it[0].get('lat'):
            lat, lng, src = it[0]['lat'], it[0]['lng'], '관청:' + (it[0].get('name') or '')
    out.append({'key': key, 'sido': a['sido'], 'sigungu': a['sigungu'],
                'lat': lat, 'lng': lng, 'src': src})
    if (i+1) % 10 == 0 or lat is None:
        io.open('coords.json','w',encoding='utf-8').write(json.dumps(out,ensure_ascii=False,indent=0))
        print('%3d/%d  %-14s %s' % (i+1, len(want), key, src or '못 찾음'), flush=True)
io.open('coords.json','w',encoding='utf-8').write(json.dumps(out,ensure_ascii=False,indent=0))
ok=[o for o in out if o['lat']]
print('끝 — 좌표 %d/%d개' % (len(ok), len(out)), flush=True)
