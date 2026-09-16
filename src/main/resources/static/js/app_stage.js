/* 체크인 — 메인 무대 부품 (app_stage.js)
 *
 * 히어로의 나레이션은 app_home.js 가 갖고, 이 파일은 그 상태를 받아
 * 무대 주변 판을 맞춘다.
 *
 *   정거장 알약 · 오른쪽 판(오늘 순서 / 지금 상황) · 작은 지도 핀
 *   모형 <-> 지도 전환 · 시점 도구 · 사람 움직임 끄기
 *
 * app_home.js 는 ckStage.render(route, alt, state) 하나만 부른다.
 * 카카오맵은 처음 필요할 때 만든다. 미리 만들면 첫 화면이 무거워진다.
 */
(function (w, d) {
  'use strict';

  var $ = function (id) { return d.getElementById(id); };
  var esc = function (s) {
    return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  };

  var state = { order: [0, 1, 2], hot: -1, cand: false };
  var route = [], alt = null;
  var mini = null, big = null, pins = [];      /* 카카오 지도와 오버레이 */

  /* ── 정거장 알약 ─────────────────────────────────── */
  function stops() {
    var box = $('st_stops');
    if (!box) return;
    var list = state.order.map(function (i, n) {
      var p = (state.cand && i === 1) ? alt : route[i];
      if (!p) return '';
      var hot = !state.cand && i === state.hot;
      return '<button type="button" class="fl-pill' + (hot ? ' fl-hot' : '') +
             '" data-s="' + i + '"><b>' + (n + 1) + '</b>' + esc(p.name) + '</button>';
    });
    box.innerHTML = list.join('');
  }

  /* ── 오른쪽 판 ───────────────────────────────────── */
  function side() {
    var plan = $('st_plan'), cw = $('st_crowd');
    if (plan) {
      /* 시각은 자리에 붙는다. 순서를 바꾸면 시각도 따라 바뀐다 —
         이 제품이 「시각은 나오는 값」이라고 말하는 근거다. */
      var times = route.map(function (p) { return p.note; });
      plan.innerHTML = state.order.map(function (i, n) {
        var p = (state.cand && i === 1) ? alt : route[i];
        if (!p) return '';
        return '<div class="fl-row"><dt>' + esc(times[n] || '') + '</dt>' +
               '<dd><b>' + esc(p.name) + '</b>' +
               (p.crowd != null ? '<span class="st-cwbar"><i class="' + lv(p.crowd).key +
                '" style="width:' + Math.min(100, Math.round(p.crowd / 1.8)) + '%"></i></span>' : '') +
               '</dd></div>';
      }).join('');
    }
    if (cw) {
      var all = route.slice();
      if (alt) all.push(alt);
      cw.innerHTML = all.map(function (p) {
        if (p.crowd == null) return '';
        var g = lv(p.crowd);
        return '<div class="fl-row"><dt>' + esc(p.name.slice(0, 4)) + '</dt>' +
               '<dd><span class="st-cw"><b>' + p.crowd + '</b>' +
               '<i class="' + g.key + '">' + g.label + '</i></span></dd></div>';
      }).join('');
    }
  }

  function lv(n) {
    var c = w.crowd && w.crowd(n);
    return c ? { key: 'cw-' + c.key, label: c.label } : { key: 'cw-mid', label: '정상' };
  }

  /* ── 머리 판의 이동 경로 ─────────────────────────── */
  function path() {
    var el = $('st_path');
    if (!el) return;
    el.innerHTML = state.order.map(function (i) {
      var p = (state.cand && i === 1) ? alt : route[i];
      return p ? esc(p.name) : '';
    }).filter(Boolean).join(' <i>&rarr;</i> ');
  }

  /* ── 작은 지도 ───────────────────────────────────── */
  function ready(fn) {
    if (!w.kakao || !w.kakao.maps) return false;
    w.kakao.maps.load(fn);
    return true;
  }

  function center() {
    var la = 0, ln = 0, n = 0;
    route.forEach(function (p) {
      if (p.lat) { la += p.lat; ln += p.lng; n++; }
    });
    return n ? new w.kakao.maps.LatLng(la / n, ln / n) : null;
  }

  function makeMini() {
    var host = $('st_minimap');
    if (!host || mini || !host.clientWidth) return;
    var c = center();
    if (!c) return;
    mini = new w.kakao.maps.Map(host, { center: c, level: 6, draggable: false });
    mini.setZoomable(false);
    mapPins();
  }

  /* 핀은 다시 만들지 않는다. 다시 만들면 옮겨지는 순간이 안 보인다. */
  function mapPins() {
    if (!mini) return;
    if (!pins.length) {
      var all = route.concat(alt ? [alt] : []);
      all.forEach(function (p, i) {
        if (!p.lat) return;
        var el = d.createElement('div');
        el.className = 'fl-mp';
        el.textContent = i + 1;
        var ov = new w.kakao.maps.CustomOverlay({
          position: new w.kakao.maps.LatLng(p.lat, p.lng),
          content: el, yAnchor: .5, xAnchor: .5, zIndex: 3
        });
        ov.setMap(mini);
        pins.push({ el: el, ov: ov, i: i });
      });
    }
    pins.forEach(function (pin) {
      var isAlt = pin.i >= route.length;
      var slot = state.order.indexOf(pin.i);
      if (isAlt) {
        pin.el.style.display = state.cand ? 'grid' : 'none';
        pin.el.textContent = 2;
        pin.el.className = 'fl-mp fl-now';
        return;
      }
      pin.el.style.display = (state.cand && pin.i === 1) ? 'none' : 'grid';
      pin.el.textContent = slot >= 0 ? slot + 1 : pin.i + 1;
      pin.el.className = 'fl-mp' + (pin.i === state.hot && !state.cand ? ' fl-now' : '');
    });
  }

  function makeBig() {
    var host = $('st_bigmap');
    if (!host || big || !host.clientWidth) return;
    var c = center();
    if (!c) return;
    big = new w.kakao.maps.Map(host, { center: c, level: 5 });
    route.forEach(function (p, i) {
      if (!p.lat) return;
      var el = d.createElement('div');
      el.className = 'fl-mp' + (i === state.hot ? ' fl-now' : '');
      el.textContent = i + 1;
      new w.kakao.maps.CustomOverlay({
        position: new w.kakao.maps.LatLng(p.lat, p.lng),
        content: el, yAnchor: .5, xAnchor: .5
      }).setMap(big);
    });
  }

  /* ── 카메라 ──────────────────────────────────────────
     레퍼런스는 정거장마다 카메라가 그 장소로 옮겨 간다.
     우리 모형은 SVG 한 장이라 실제 카메라가 없다. 대신 그 장소가
     화면 가운데로 오도록 판을 옮기고 조금 당긴다.
     transform 하나만 바꾸므로 합성으로 끝난다. */
  var cam = { z: 1, x: 0, y: 0, user: false };

  /* 판이 무대를 덮고 남는 여유. 이 범위를 넘겨 밀면 무대 가장자리에
     바닥색이 드러난다 — 도시가 끊겨 보이는 그 자리다. */
  function camSlack() {
    var f = $('ck_frame');
    if (!f) return null;
    var st = f.parentElement;
    if (!st || !f.offsetWidth) return null;
    return {
      w: f.offsetWidth, h: f.offsetHeight,
      x: Math.max(0, (f.offsetWidth * cam.z - st.clientWidth) / 2),
      y: Math.max(0, (f.offsetHeight * cam.z - st.clientHeight) / 2)
    };
  }

  function camApply() {
    var f = $('ck_frame');
    if (!f) return;
    var s = camSlack();
    if (s) {
      cam.x = Math.max(-s.x, Math.min(s.x, cam.x));
      cam.y = Math.max(-s.y, Math.min(s.y, cam.y));
    }
    f.style.setProperty('--st-z', cam.z.toFixed(3));
    f.style.setProperty('--st-x', Math.round(cam.x) + 'px');
    f.style.setProperty('--st-y', Math.round(cam.y) + 'px');
  }

  /* 핀의 %좌표를 화면 가운데로 보내는 이동량.
     가운데를 45% 로 두는 이유 — 아래쪽에 경로 만들기 바가 있다.
     덮는 범위를 넘으면 camApply 가 잘라 낸다. 정거장이 정확히 가운데
     오지 않더라도 도시가 끊기는 쪽이 더 나쁘다. */
  function camTo(p, zoom) {
    if (!p || cam.user) return;                 /* 사람이 시점을 만졌으면 건드리지 않는다 */
    /* 좁은 화면에서는 장면 상자가 330px 밖에 안 된다. 당기면 핀이 화면 밖으로
       밀려난다. 모형 전체가 보이는 쪽이 낫다. */
    if (w.innerWidth <= 1180) { camReset(); return; }
    var f = $('ck_frame');
    if (!f || !f.offsetWidth) return;
    cam.z = zoom || 1.32;
    /* 변형이 안 걸린 레이아웃 크기로 잰다. getBoundingClientRect 를 쓰면
       지난번 확대가 값에 섞여 장면을 넘길수록 이동량이 부푼다. */
    cam.x = (50 - p.x) / 100 * f.offsetWidth * cam.z;
    cam.y = (45 - p.y) / 100 * f.offsetHeight * cam.z;
    camApply();
  }

  function camReset() {
    cam.z = 1; cam.x = 0; cam.y = 0; cam.user = false;
    camApply();
  }

  /* ── 도구 ────────────────────────────────────────── */
  function initTools() {
    var stage = d.querySelector('.ck-stage');
    var model = $('st_t_model'), map = $('st_t_map'), walk = $('st_t_walk');
    var scene = stage && stage.querySelector('.ck-scene'), bmap = $('st_bigmap');

    function show(which) {
      if (!scene || !bmap) return;
      var isMap = which === 'map';
      scene.hidden = isMap;
      bmap.hidden = !isMap;
      if (model) model.setAttribute('aria-pressed', String(!isMap));
      if (map) map.setAttribute('aria-pressed', String(isMap));
      var t = $('st_mini_ttl');
      if (t) t.textContent = isMap ? '작은 모형' : '지금 위치 지도';
      if (isMap) ready(function () { makeBig(); if (big) big.relayout(); });
    }
    if (model) model.addEventListener('click', function () { show('model'); });
    if (map) map.addEventListener('click', function () { show('map'); });

    if (walk) {
      walk.addEventListener('click', function () {
        var on = walk.getAttribute('aria-pressed') !== 'true';
        walk.setAttribute('aria-pressed', String(on));
        if (stage) stage.classList.toggle('st-still', !on);
      });
    }

    /* 시점 도구 — 카메라와 같은 변수를 쓴다.
       사람이 한 번 만지면 장면이 넘어가도 카메라가 따라 움직이지 않는다.
       보고 있는 자리를 화면이 제멋대로 옮기면 안 된다. */
    var zin = $('st_v_in'), zout = $('st_v_out'), zr = $('st_v_reset');
    if (zin) zin.addEventListener('click', function () {
      cam.user = true; cam.z = Math.min(2.4, cam.z * 1.25); camApply();
    });
    if (zout) zout.addEventListener('click', function () {
      cam.user = true; cam.z = Math.max(.8, cam.z / 1.25); camApply();
    });
    if (zr) zr.addEventListener('click', camReset);

    /* 작은 지도 접기 */
    var fold = $('st_fold'), box = $('st_mini');
    if (fold && box) {
      fold.addEventListener('click', function () {
        var open = fold.getAttribute('aria-expanded') === 'true';
        fold.setAttribute('aria-expanded', String(!open));
        fold.textContent = open ? '펼치기' : '접기';
        box.classList.toggle('fl-fold', open);
        if (!open) ready(function () { if (mini) mini.relayout(); });
      });
    }

    /* 오른쪽 판 탭 */
    var td = $('st_tab_day'), tn = $('st_tab_now');
    var pd = $('st_pane_day'), pn = $('st_pane_now');
    function tab(day) {
      if (!pd || !pn) return;
      pd.hidden = !day; pn.hidden = day;
      if (td) { td.setAttribute('aria-pressed', String(day)); td.classList.toggle('on', day); }
      if (tn) { tn.setAttribute('aria-pressed', String(!day)); tn.classList.toggle('on', !day); }
    }
    if (td) td.addEventListener('click', function () { tab(true); });
    if (tn) tn.addEventListener('click', function () { tab(false); });

    /* 정거장을 누르면 그 정거장이 지금이 된다 */
    var sb = $('st_stops');
    if (sb) {
      sb.addEventListener('click', function (e) {
        var b = e.target.closest ? e.target.closest('.fl-pill') : null;
        if (!b) return;
        var i = parseInt(b.getAttribute('data-s'), 10);
        if (isNaN(i)) return;
        state.hot = i;
        stops(); mapPins();
        cam.user = false;                        /* 눌러서 고른 것이므로 카메라가 따라간다 */
        camTo(route[i]);
      });
    }
  }

  /* ── app_home.js 가 부른다 ───────────────────────── */
  w.ckStage = {
    render: function (r, a, st) {
      route = r || []; alt = a || null;
      if (st) {
        if (st.order) state.order = st.order;
        if (st.hot != null) state.hot = st.hot;
        state.cand = !!st.cand;
      }
      path(); stops(); side();
      /* 장면이 바뀌면 카메라가 그 장소로 간다. 붐비는 곳이 없으면 전체를 본다. */
      if (state.hot >= 0 && route[state.hot]) camTo(route[state.hot]);
      else if (!cam.user) camReset();
      ready(function () { makeMini(); mapPins(); });
    }
  };

  function start() {
    initTools();
    /* 지도는 무대가 눈에 들어올 때 만든다 */
    var stage = d.querySelector('.ck-stage');
    if (stage && w.IntersectionObserver) {
      var io = new IntersectionObserver(function (es) {
        es.forEach(function (e) {
          if (!e.isIntersecting) return;
          io.disconnect();
          ready(function () { makeMini(); mapPins(); });
        });
      }, { threshold: .05 });
      io.observe(stage);
    } else {
      ready(function () { makeMini(); mapPins(); });
    }
  }

  if (d.readyState === 'loading') d.addEventListener('DOMContentLoaded', start);
  else start();
})(window, document);
