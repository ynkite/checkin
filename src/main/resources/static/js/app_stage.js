/* 도시 모형은 한 장소, 지도는 전체 동선. 두 좌표계를 섞지 않는다. */
(function (w, d) {
  'use strict';
  var $ = function (id) { return d.getElementById(id); };
  var esc = function (s) { return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) {
    return { '&':'&amp;', '<':'&lt;', '>':'&gt;', '"':'&quot;', "'":'&#39;' }[c];
  }); };
  var route = [], alt = null, order = [], hot = 0, selected = null;
  var mini = null, big = null, miniPins = [], bigPins = [], bigLine = null;
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
  function bounds(map, points, pad) {
    if (!map || !points.length) return;
    map.relayout();
    var b = new w.kakao.maps.LatLngBounds();
    points.forEach(function (p) { b.extend(new w.kakao.maps.LatLng(p.lat, p.lng)); });
    if (points.length === 1) { map.setCenter(new w.kakao.maps.LatLng(points[0].lat, points[0].lng)); map.setLevel(4); }
    else map.setBounds(b, pad, pad, pad, pad);
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
    } else bigLine.setPath(path);
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
    var p = points || list();
    bigDraw(p); bounds(big, p, w.innerWidth < 600 ? 46 : 70); return true;
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
    status(p.name + ' · 모형 불러오는 중');
    if (hero) hero.classList.add('ck-cut');
    var result = await Promise.all([fetchScene(p.scene), new Promise(function (resolve) { setTimeout(resolve, reduced ? 0 : 220); })]);
    if (ticket !== revision) return false;
    if (!result[0]) { if (hero) hero.classList.remove('ck-cut'); return showWideMap([p]); }
    if (sceneNow !== p.scene) {
      host.innerHTML = result[0]; var svg = host.querySelector('svg');
      if (svg) { svg.classList.add('mass'); svg.setAttribute('aria-hidden','true'); } sceneNow = p.scene;
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
      clearTimeout(resizeTimer); resizeTimer = setTimeout(function () { camApply(); miniUpdate(); if (view === 'map') showWideMap(); },180);
    });
  }
  w.ckStage = {
    showWideMap:showWideMap, showPlace:showPlace, preloadScene:fetchScene,
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
