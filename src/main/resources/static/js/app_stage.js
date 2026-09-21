/* 도시 모형은 한 장소, 지도는 전체 동선. 두 좌표계를 섞지 않는다. */
(function (w, d) {
  'use strict';
  var $ = function (id) { return d.getElementById(id); };
  var esc = function (s) { return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) {
    return { '&':'&amp;', '<':'&lt;', '>':'&gt;', '"':'&quot;', "'":'&#39;' }[c];
  }); };
  var route = [], alt = null, order = [], hot = 0, selected = null;
  var mini = null, big = null, miniPins = [], bigPins = [], bigLine = null;
  var ghostPins = [], ghostLine = null, newLine = null, moveLine = null, callouts = [], changeRaf = 0, lastChange = null;
  var view = 'model', revision = 0, sceneNow = '', cache = {}, pending = {};
  var cam = { z:1, x:0, y:0 }, CAM_MIN = 1, CAM_MAX = 2.4;
  var reduced = matchMedia('(prefers-reduced-motion: reduce)').matches;
  var sdkPromise;

  function sdk() {
    if (!sdkPromise) sdkPromise = new Promise(function (resolve) {
      var end = false;
      var timeout = setTimeout(function () { if (!end) { end = true; sdkPromise = null; resolve(false); } }, 5000);
      if (w.kakao && w.kakao.maps) w.kakao.maps.load(function () {
        if (end) return;
        end = true; clearTimeout(timeout); resolve(true);
      });
    });
    return sdkPromise;
  }
  function list() { return order.map(function (i) { return route[i]; }).filter(Boolean); }
  function status(message) { if ($('st_scene_label')) $('st_scene_label').textContent = message; }
  function buttons() {
    if ($('st_t_model')) $('st_t_model').setAttribute('aria-pressed', String(view === 'model'));
    if ($('st_t_map')) $('st_t_map').setAttribute('aria-pressed', String(view === 'map'));
    if ($('st_mini_ttl')) $('st_mini_ttl').textContent = '전체 동선 지도';
  }
  function stops() {
    if ($('st_stops')) $('st_stops').innerHTML = order.map(function (i, n) {
      var p = route[i];
      return p ? '<button type="button" class="fl-pill' + (i === hot ? ' fl-hot' : '') +
        '" data-s="' + i + '" aria-pressed="' + (i === hot) + '"><b>' + (n + 1) +
        '</b><span>' + esc(p.name) + '</span></button>' : '';
    }).join('');
    if ($('st_path')) $('st_path').textContent = list().map(function (p) { return p.name; }).join(' → ');
  }
  function side() {
    var times = (w.__ckRoute || route).map(function (p) { return p.note; });
    if ($('st_plan')) $('st_plan').innerHTML = list().map(function (p, n) {
      return '<div class="fl-row"><dt>' + esc(times[n]) + '</dt><dd><b>' + esc(p.name) +
        '</b><span>' + esc(p.cat || '') + '</span></dd></div>';
    }).join('');
    if ($('st_crowd')) $('st_crowd').innerHTML = route.concat(alt && !route.some(function (p) { return p.name === alt.name; }) ? [alt] : []).map(function (p) {
      var g = w.crowd && p.crowd != null ? w.crowd(p.crowd) : null;
      return '<div class="fl-row"><dt>' + esc(p.name) + '</dt><dd class="st-cw"><b>' +
        (p.crowd == null ? '—' : Math.round(p.crowd)) + '</b><span>' + esc(g ? g.label : '미수신') +
        '</span><small>' + (p.crowdLive ? '예측값' : (p.crowd == null ? '' : '예시')) + '</small></dd></div>';
    }).join('') + '<p class="st-src">한국관광공사 집중률 예측 · 0–100<br>예시는 실제 혼잡 상황이 아닙니다.</p>';
  }
  function bounds(map, points, pad, top, bot) {
    if (!map || !points.length) return;
    map.relayout();
    var b = new w.kakao.maps.LatLngBounds();
    points.forEach(function (p) { b.extend(new w.kakao.maps.LatLng(p.lat, p.lng)); });
    if (points.length === 1) { map.setCenter(new w.kakao.maps.LatLng(points[0].lat, points[0].lng)); map.setLevel(4); }
    else map.setBounds(b, top == null ? pad : top, pad, bot == null ? pad : bot, pad);
  }
  function pins(map, pool, points) {
    while (pool.length < points.length) {
      var el = d.createElement('div'); el.className = 'fl-mp';
      pool.push({ el:el, ov:new w.kakao.maps.CustomOverlay({ content:el, xAnchor:.5, yAnchor:.5, zIndex:3 }) });
    }
    pool.forEach(function (pin, i) {
      var p = points[i]; pin.ov.setMap(p ? map : null); if (!p) return;
      pin.ov.setPosition(new w.kakao.maps.LatLng(p.lat, p.lng));
      pin.el.textContent = i + 1; pin.el.title = p.name;
      pin.el.className = 'fl-mp' + (p === selected ? ' fl-now' : '');
    });
  }
  function createMap(host) {
    if (!host || !host.clientWidth || !route.length) return null;
    var map = new w.kakao.maps.Map(host, { center:new w.kakao.maps.LatLng(route[0].lat, route[0].lng), level:6 });
    map.setZoomable(false); return map;
  }
  async function miniUpdate() {
    if (!(await sdk())) return;
    var host = $('st_minimap'); if (!host || !host.clientWidth) return;
    if (!mini) mini = createMap(host);
    if (mini) { pins(mini, miniPins, list()); bounds(mini, list(), 28); }
  }
  function bigDraw(points) {
    if (!big) return;
    pins(big, bigPins, points);
    var path = points.map(function (p) { return new w.kakao.maps.LatLng(p.lat, p.lng); });
    if (!bigLine) {
      bigLine = new w.kakao.maps.Polyline({ path:path, strokeWeight:4,
        strokeColor:getComputedStyle(d.documentElement).getPropertyValue('--ink').trim(), strokeOpacity:.85 });
      bigLine.setMap(big);
    } else { bigLine.setPath(path); bigLine.setMap(big); }
  }
  async function showWideMap(points) {
    var ticket = ++revision;
    view = 'map'; buttons();
    var scene = d.querySelector('.ck-scene'), host = $('st_bigmap');
    if (!scene || !host) return false;
    scene.hidden = true; host.hidden = false;
    status('전체 동선 · 선은 방문 순서입니다');
    if (!(await sdk()) || ticket !== revision) {
      if (ticket === revision) status('지도를 불러오지 못했습니다. 모형으로 장소를 볼 수 있어요.');
      return false;
    }
    if (!big) big = createMap(host); if (!big) return false;
    clearChange();
    var p = points || list();
    bigDraw(p); bounds(big, p, 10); return true;
  }

  /* ── 동선이 바뀌는 순간 ──────────────────────────
     메인 세 갈래(길이 막혀서 · 사람이 몰려서 · 비가 와서)가 하는 일은
     동선을 바꾸는 것이다. 그런데 전에는 순서를 바꾸는 갈래만 지도를 켰고,
     장소를 바꾸는 두 갈래는 바로 동네 모형으로 넘어갔다 —
     정작 「경로가 이렇게 바뀐다」를 보여 주는 화면이 없었다.

     이제 셋 다 지도에서 먼저 보여 준다.
     원래 동선은 회색 점선으로 그대로 두고, 바꾼 동선을 그 위에 그린다.
     선이 한 번에 툭 바뀌면 무엇이 달라졌는지 안 보이니 출발점부터
     끝까지 그려 나가고, 핀은 선이 닿을 때 하나씩 선다.
     바뀐 자리에는 말풍선을 세운다. */

  function token(name) { return getComputedStyle(d.documentElement).getPropertyValue(name).trim(); }
  function latlngs(points) { return points.map(function (p) { return new w.kakao.maps.LatLng(p.lat, p.lng); }); }
  /* 경도 1도는 위도 1도보다 짧다. 부산 위도에서 0.82 배 —
     안 곱하면 동서로 긴 구간이 실제보다 길게 자란다 */
  function span(a, b) {
    var dy = b.lat - a.lat, dx = (b.lng - a.lng) * .82;
    return Math.sqrt(dx * dx + dy * dy);
  }
  function grown(points, t) {
    if (points.length < 2 || t >= 1) return latlngs(points);
    var seg = [], total = 0, i;
    for (i = 1; i < points.length; i++) { var len = span(points[i-1], points[i]); seg.push(len); total += len; }
    if (!total) return latlngs(points.slice(0, 1));
    var want = total * t, acc = 0, out = [new w.kakao.maps.LatLng(points[0].lat, points[0].lng)];
    for (i = 0; i < seg.length; i++) {
      if (acc + seg[i] <= want) { acc += seg[i]; out.push(new w.kakao.maps.LatLng(points[i+1].lat, points[i+1].lng)); continue; }
      var k = seg[i] ? (want - acc) / seg[i] : 0;
      out.push(new w.kakao.maps.LatLng(points[i].lat + (points[i+1].lat - points[i].lat) * k,
                                       points[i].lng + (points[i+1].lng - points[i].lng) * k));
      break;
    }
    return out;
  }
  /* 각 지점이 전체 길이의 어디쯤인지. 핀을 선이 닿을 때 세우려고 쓴다 */
  function reach(points) {
    var out = [0], total = 0, acc = [], i;
    for (i = 1; i < points.length; i++) { total += span(points[i-1], points[i]); acc.push(total); }
    acc.forEach(function (v) { out.push(total ? v / total : 1); });
    return out;
  }
  function pool(bag, map, points, cls, z) {
    while (bag.length < points.length) {
      var el = d.createElement('div');
      bag.push({ el:el, ov:new w.kakao.maps.CustomOverlay({ content:el, xAnchor:.5, yAnchor:.5, zIndex:z }) });
    }
    bag.forEach(function (pin, i) {
      var p = points[i]; pin.ov.setMap(p ? map : null); if (!p) return;
      pin.ov.setPosition(new w.kakao.maps.LatLng(p.lat, p.lng));
      pin.ov.setZIndex(z); pin.el.textContent = i + 1; pin.el.title = p.name; pin.el.className = cls;
    });
  }
  /* 말풍선은 핀 위에 세운다. 다만 판 꼭대기에 붙은 핀 위에는 자리가 없으니
     그때는 아래로 내린다 — 안 그러면 이름이 잘려서 안 읽힌다.
     한 줄로 짓는 것도 같은 이유다. 무대가 낮으면 두 줄짜리가 판 밖으로 나간다 */
  function callout(p, cap, tone, below) {
    var el = d.createElement('div');
    el.className = 'st-callout st-callout-' + tone;
    el.innerHTML = '<b>' + esc(cap) + '</b><span>' + esc(p.name) + '</span>';
    var ov = new w.kakao.maps.CustomOverlay({ content:el, position:new w.kakao.maps.LatLng(p.lat, p.lng),
      xAnchor:.5, yAnchor:below ? -.75 : 1.75, zIndex:7 });
    ov.setMap(big); callouts.push(ov);
  }
  function legend(label) {
    var el = $('st_scene_label'); if (!el) return;
    el.innerHTML = '<b class="st-lg-t">' + esc(label || '동선이 이렇게 바뀝니다') + '</b>' +
      '<span class="st-lg"><i class="st-lg-was"></i>원래 동선<i class="st-lg-new"></i>바꾼 동선</span>';
  }
  function clearChange() {
    if (changeRaf) { cancelAnimationFrame(changeRaf); changeRaf = 0; }
    [ghostLine, newLine, moveLine].forEach(function (l) { if (l) l.setMap(null); });
    ghostLine = newLine = moveLine = null;
    ghostPins.forEach(function (pin) { pin.ov.setMap(null); });
    callouts.forEach(function (ov) { ov.setMap(null); }); callouts = [];
    bigPins.forEach(function (pin) { pin.el.classList.remove('fl-wait'); });
    lastChange = null;
    var host = $('st_bigmap');
    if (host && host.parentElement) host.parentElement.classList.remove('st-changing');
  }
  async function showChange(before, after, mark) {
    if (!(before || []).length || !(after || []).length) return false;
    mark = mark || {};
    var ticket = ++revision;
    view = 'map'; buttons();
    var scene = d.querySelector('.ck-scene'), host = $('st_bigmap');
    if (!scene || !host) return false;
    scene.hidden = true; host.hidden = false;
    status((mark.label ? mark.label + ' · ' : '') + '동선을 바꾸는 중');
    if (!(await sdk()) || ticket !== revision) {
      if (ticket === revision) status('지도를 불러오지 못했습니다. 모형으로 장소를 볼 수 있어요.');
      return false;
    }
    if (!big) big = createMap(host);
    if (!big) return false;
    clearChange();
    lastChange = { before:before, after:after, mark:mark };
    if (host.parentElement) host.parentElement.classList.add('st-changing');
    if (bigLine) bigLine.setMap(null);
    var narrow = w.innerWidth < 600;
    /* 여백을 조금만 준다. 카카오맵은 배율을 단계로만 올리는데 여백이 14px 만
       넘어도 한 단계 멀어진다 — 실측. 한 단계면 동선이 절반 크기로 줄어서,
       시청부터 송정까지 나오고 정작 바뀐 자리가 안 보인다 */
    var all = before.concat(after);
    /* 좁은 판에서는 아래쪽 이름표가 동선 끝을 가린다. 그만큼 위로 올린다 */
    bounds(big, all, 10, 10, narrow ? 46 : 10);
    var lo = Math.min.apply(null, all.map(function (p) { return p.lat; }));
    var hi = Math.max.apply(null, all.map(function (p) { return p.lat; }));
    var wlo = Math.min.apply(null, all.map(function (p) { return p.lng; }));
    var whi = Math.max.apply(null, all.map(function (p) { return p.lng; }));
    function topish(p) { return hi > lo && (p.lat - lo) / (hi - lo) > .62; }
    /* 바뀐 두 자리가 가까우면 말풍선이 서로를 덮는다 —
       영화의거리와 시드니앤솔트는 600m 밖에 안 떨어져 있다.
       그럴 때는 위쪽 자리의 말풍선을 아래로, 아래쪽 자리의 것을 위로 보낸다.
       반대로 하면 둘이 가운데에서 다시 만난다 */
    function crowdedPair() {
      if (!mark.was || !mark.now || !(hi > lo) || !(whi > wlo)) return false;
      return Math.abs(mark.was.lat - mark.now.lat) / (hi - lo) < .22 &&
             Math.abs(mark.was.lng - mark.now.lng) / (whi - wlo) < .35;
    }

    ghostLine = new w.kakao.maps.Polyline({ map:big, path:latlngs(before), strokeWeight:4,
      strokeColor:token('--ink-3'), strokeOpacity:.75, strokeStyle:'shortdash', zIndex:1 });
    pool(ghostPins, big, before, 'fl-mp fl-ghost', 2);

    var changed = [];
    after.forEach(function (p, i) { if (!before[i] || before[i].name !== p.name) changed.push(i); });
    newLine = new w.kakao.maps.Polyline({ map:big, path:grown(after, 0), strokeWeight:narrow ? 6 : 8,
      strokeColor:token('--terra'), strokeOpacity:.95, zIndex:4 });
    pool(bigPins, big, after, 'fl-mp fl-fresh', 5);
    bigPins.forEach(function (pin, i) {
      if (i >= after.length) return;
      if (changed.indexOf(i) >= 0) pin.el.classList.add('fl-swap');
      pin.el.classList.add('fl-wait');
    });
    legend(mark.label);

    var stops = reach(after), lite = d.documentElement.getAttribute('data-motion') === 'lite';
    var dur = reduced ? 0 : (lite ? 700 : 1300), t0 = 0;
    function finish() {
      var tight = crowdedPair(), up = tight && mark.was.lat > mark.now.lat;
      if (mark.was) callout(mark.was, mark.wasCap || '여기 대신', 'was', tight ? up : topish(mark.was));
      if (mark.now) callout(mark.now, mark.nowCap || '이곳으로', 'new', tight ? !up : topish(mark.now));
      if (mark.was && mark.now) moveLine = new w.kakao.maps.Polyline({ map:big, path:latlngs([mark.was, mark.now]),
        strokeWeight:3, strokeColor:token('--terra'), strokeOpacity:.9, strokeStyle:'shortdot', zIndex:6 });
    }
    function frame(now) {
      if (ticket !== revision) { changeRaf = 0; return; }
      if (!t0) t0 = now;
      var t = dur ? Math.min(1, (now - t0) / dur) : 1, e = 1 - Math.pow(1 - t, 3);
      newLine.setPath(grown(after, e));
      bigPins.forEach(function (pin, i) {
        if (i < after.length && stops[i] <= e + .001) pin.el.classList.remove('fl-wait');
      });
      if (t < 1) { changeRaf = requestAnimationFrame(frame); return; }
      changeRaf = 0; finish();
    }
    if (dur) changeRaf = requestAnimationFrame(frame); else frame(0);
    return true;
  }
  function fetchScene(name) {
    var def = (w.__ckScenes || {})[name];
    if (!def) return Promise.resolve(null);
    if (cache[name]) return Promise.resolve(cache[name]);
    if (pending[name]) return pending[name];
    var controller = new AbortController();
    var timeout = setTimeout(function () { controller.abort(); }, 7000);
    pending[name] = fetch(def.svg, { cache:'force-cache', signal:controller.signal })
      .then(function (r) { if (!r.ok) throw Error('scene'); return r.text(); })
      .then(function (s) { cache[name] = s; return s; })
      .catch(function () { return null; })
      .finally(function () { clearTimeout(timeout); delete pending[name]; });
    return pending[name];
  }
  function camSlack() {
    var f = $('ck_frame'); if (!f || !f.offsetWidth) return null;
    var st = f.parentElement;
    CAM_MIN = Math.max(1, st.clientWidth / f.offsetWidth, st.clientHeight / f.offsetHeight);
    cam.z = Math.max(CAM_MIN, Math.min(CAM_MAX, cam.z));
    return { x:Math.max(0, (f.offsetWidth * cam.z - st.clientWidth) / 2),
             y:Math.max(0, (f.offsetHeight * cam.z - st.clientHeight) / 2) };
  }
  function camApply() {
    var f = $('ck_frame'), slack = camSlack(); if (!f || !slack) return;
    cam.x = Math.max(-slack.x, Math.min(slack.x, cam.x));
    cam.y = Math.max(-slack.y, Math.min(slack.y, cam.y));
    f.style.setProperty('--st-z', cam.z.toFixed(3));
    f.style.setProperty('--st-x', Math.round(cam.x) + 'px');
    f.style.setProperty('--st-y', Math.round(cam.y) + 'px');
  }
  function camAt(x, y, zoom) {
    var f = $('ck_frame'); if (!f) return;
    cam.z = zoom || 1.18;
    cam.x = (.5 - x / 100) * f.offsetWidth * cam.z;
    cam.y = (.5 - y / 100) * f.offsetHeight * cam.z; camApply();
  }
  function camFit() { cam.z = 1; cam.x = 0; cam.y = 0; camApply(); }
  async function showPlace(p, no) {
    if (!p) return false;
    selected = p;
    if (!p.scene || !(w.__ckScenes || {})[p.scene]) {
      var ok = await showWideMap([p]);
      if (selected === p) status(p.name + ' · 실제 위치'); return ok;
    }
    var ticket = ++revision, host = $('ck_scene'), hero = $('ck_hero');
    if (!host) return false;
    clearChange();

    /* 덮개(.ck-cut)는 그림이 바뀌는 순간을 가리려고 두는 것이지,
       기다리는 동안 보여 주는 것이 아니다. 전에는 받기 전에 먼저 덮었는데
       장면 하나가 300~400KB 라, 느린 망에서는 단색 판만 몇 초 떠 있었다.
       「아무것도 없는 화면」이 그것이다.

       이미 받아 둔 장면이면 바로 덮고 바꾼다 — 덮개는 220ms 만 보인다.
       아직 못 받았으면 앞 장면을 그대로 두고 기다린다.
       앞 장면이라도 보이는 편이 빈 판보다 낫다. */
    var warmed = !!cache[p.scene];
    if (warmed && hero) hero.classList.add('ck-cut');
    status(p.name + (warmed ? ' · 동네 모형' : ' · 모형 불러오는 중'));
    var result = await Promise.all([fetchScene(p.scene), new Promise(function (resolve) { setTimeout(resolve, reduced ? 0 : 220); })]);
    if (!warmed && hero) hero.classList.add('ck-cut');
    if (ticket !== revision) return false;
    if (!result[0]) { if (hero) hero.classList.remove('ck-cut'); return showWideMap([p]); }
    if (sceneNow !== p.scene) {
      host.innerHTML = result[0]; var svg = host.querySelector('svg');
      if (svg) {
        svg.classList.add('mass'); svg.setAttribute('aria-hidden','true');
        /* 판 모양과 상관없이 가운데를 기준으로 잘라서 채운다.
           무대 높이가 화면따라 변해도 모형이 안 눌린다 */
        svg.setAttribute('preserveAspectRatio','xMidYMid slice');
      }
      sceneNow = p.scene;
    }
    $('ck_frame').style.setProperty('--st-ar', w.__ckScenes[p.scene].ar);
    view = 'model'; buttons();
    $('st_bigmap').hidden = true; d.querySelector('.ck-scene').hidden = false;
    if (w.ckSoloPin) w.ckSoloPin(p, no || 1);
    host.style.setProperty('--cw', p.crowd == null ? 0 : Math.max(0,Math.min(1,p.crowd / 100)));
    camAt(p.sx, p.sy, w.innerWidth < 600 ? 1 : 1.18);
    status(p.name + ' · 동네 모형');
    if (hero) hero.classList.remove('ck-cut'); return true;
  }
  function pause() { if (w.ckSkipIntro) w.ckSkipIntro(); if (w.ckHeroPause) w.ckHeroPause(); }
  function initTools() {
    function on(id, fn) { if ($(id)) $(id).addEventListener('click', fn); }
    on('st_t_map', function () { pause(); showWideMap(); });
    on('st_t_model', function () { pause(); showPlace(selected && selected.scene ? selected : (w.__ckRoute || route)[hot] || route[0], hot + 1); });
    on('st_t_walk', function () {
      var on = $('st_t_walk').getAttribute('aria-pressed') !== 'true';
      $('st_t_walk').setAttribute('aria-pressed', String(on)); $('ck_hero').classList.toggle('st-still', !on);
    });
    on('st_v_in', function () { pause(); if (view === 'map' && big) big.setLevel(Math.max(1,big.getLevel()-1)); else { cam.z *= 1.2; camApply(); } });
    on('st_v_out', function () { pause(); if (view === 'map' && big) big.setLevel(Math.min(14,big.getLevel()+1)); else { cam.z /= 1.2; camApply(); } });
    on('st_v_reset', function () { pause(); if (view === 'map') showWideMap(); else camFit(); miniUpdate(); });
    on('st_m_in', function () { if (mini) mini.setLevel(Math.max(1,mini.getLevel()-1)); });
    on('st_m_out', function () { if (mini) mini.setLevel(Math.min(14,mini.getLevel()+1)); });
    on('st_fold', function () {
      var open = $('st_fold').getAttribute('aria-expanded') === 'true';
      $('st_fold').setAttribute('aria-expanded', String(!open)); $('st_fold').textContent = open ? '펼치기' : '접기';
      $('st_mini').classList.toggle('fl-fold', open); if (!open) miniUpdate();
    });
    function tab(day) {
      $('st_pane_day').hidden = !day; $('st_pane_now').hidden = day;
      ['st_tab_day','st_tab_now'].forEach(function (id,i) {
        $(id).setAttribute('aria-pressed', String(day === (i === 0))); $(id).classList.toggle('on',day === (i === 0));
      });
    }
    on('st_tab_day',function () { tab(true); }); on('st_tab_now',function () { tab(false); });
    on('st_stops',function (e) {
      var b = e.target.closest('[data-s]'); if (!b) return;
      pause(); hot = Number(b.dataset.s); selected = route[hot]; stops(); miniUpdate(); showPlace(selected, order.indexOf(hot) + 1);
    });
    var resizeTimer;
    w.addEventListener('resize',function () {
      clearTimeout(resizeTimer); resizeTimer = setTimeout(function () {
        camApply(); miniUpdate();
        /* 창 크기가 바뀌었다고 바꾼 동선을 지우지 않는다. 그대로 다시 그린다 */
        if (lastChange) { var c = lastChange; showChange(c.before, c.after, c.mark); }
        else if (view === 'map') showWideMap();
      },180);
    });
  }
  /* 동선에 나오는 장면을 미리 받아 둔다. 한 번에 다 부르면 첫 화면이 느려지니
     하나씩, 앞의 것이 끝난 뒤에 다음 것을 부른다. 실패해도 그냥 넘어간다 —
     미리 받기는 있으면 좋은 것이지 없으면 안 되는 것이 아니다. */
  function warm(names) {
    var list = (names || []).filter(function (n,i,a) { return n && a.indexOf(n) === i && !cache[n]; });
    (function next() {
      var n = list.shift();
      if (!n) return;
      fetchScene(n).then(next, next);
    })();
  }

  w.ckStage = {
    showWideMap:showWideMap, showPlace:showPlace, showChange:showChange, preloadScene:fetchScene, warm:warm,
    camAt:camAt, camFit:camFit, bigDraw:bigDraw,
    cancel:function () { revision++; if ($('ck_hero')) $('ck_hero').classList.remove('ck-cut'); },
    render:function (r,a,st) {
      route = r || []; alt = a || null; st = st || {};
      order = st.order || route.map(function (_,i) { return i; }); hot = st.hot == null ? 0 : st.hot;
      selected = route[hot] || route[0]; stops(); side(); miniUpdate();
    }
  };
  if (d.readyState === 'loading') d.addEventListener('DOMContentLoaded',initTools); else initTools();
})(window, document);
