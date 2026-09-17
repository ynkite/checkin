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
                '" style="width:' + Math.min(100, Math.max(6, Math.round(p.crowd))) + '%"></i></span>' : '') +
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
               '<i class="' + g.key + '">' + esc(p.crowdLabel || g.label) + '</i></span></dd></div>';
      }).join('') +
      /* 값이 어디서 왔는지 한 줄. 받은 값과 예시 값을 같은 얼굴로 두지 않는다 */
      '<p class="st-src">' + (w.__ckCrowdLive
        ? '한국관광공사 관광지 집중률 예측 · 0~100'
        : '예시 값입니다. 실제 값은 한국관광공사 집중률 예측을 씁니다.') + '</p>';
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
    mini.setZoomable(false);      /* 휠은 막는다. +/- 버튼으로만 */
    mapPins();
    fitMini();
  }

  /* 작은 지도 기본 크기 — 1·2·3 이 다 보여야 한다.
     level 을 숫자로 박으면 화면 폭이 달라질 때 어떤 곳은 밖으로 나간다.
     좌표에서 계산하게 두고 여백만 준다. */
  function fitMini() {
    if (!mini) return;
    var pts = route.filter(function (p) { return p.lat && p.lng; });
    if (pts.length < 2) return;
    var b = new w.kakao.maps.LatLngBounds();
    pts.forEach(function (p) { b.extend(new w.kakao.maps.LatLng(p.lat, p.lng)); });
    mini.relayout();
    /* 작은 판이라 여백을 적게. 핀 번호가 가장자리에 살짝 닿는 정도 */
    mini.setBounds(b, 22, 22, 22, 22);
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
  var camFitted = false;   /* 처음 한 번만 전체를 맞춘다 */
  /* 들어오는 장면이 도는 동안은 카메라를 그쪽이 독점한다.
     나레이션이 장면을 넘길 때마다 camTo 를 부르는데, 그게 비행을
     중간에 끊어서 배율이 튀었다(1.95 -> 1.14 -> 1.93). */
  var camLocked = false;

  /* 판이 무대를 덮고 남는 여유. 이 범위를 넘겨 밀면 무대 가장자리에
     바닥색이 드러난다 — 도시가 끊겨 보이는 그 자리다. */
  function camSlack() {
    var f = $('ck_frame');
    if (!f) return null;
    var st = f.parentElement;
    if (!st || !f.offsetWidth) return null;
    /* 덮는 범위를 조금 넘겨도 된다. 장면 바탕에 모형의 바닥색을
       깔아 뒀으므로(styles_stage.css) 드러나는 자리가 「잘린 곳」이
       아니라 「멀어진 바다」로 읽힌다. 이 여유가 없으면 가장자리에
       있는 장소의 이름표를 화면 안으로 끌어올 수 없다. */
    var slack = 92;
    return {
      w: f.offsetWidth, h: f.offsetHeight,
      x: Math.max(0, (f.offsetWidth * cam.z - st.clientWidth) / 2) + slack,
      y: Math.max(0, (f.offsetHeight * cam.z - st.clientHeight) / 2) + slack * .5
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
    if (camLocked) return;                      /* 들어오는 장면이 쥐고 있다 */
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

  function camReset(force) {
    if (camLocked && !force) return;
    cam.z = 1; cam.x = 0; cam.y = 0; cam.user = false;
    camApply();
  }

  /* 축소 하한은 「동선이 다 보이는 배율」이다. 고정값이 아니다.
     모형 전체를 축소해 보여 주면 1,100명 군중과 건물 700채가 한 화면에
     들어와 렉이 심해진다. 동선만 다 보이면 그게 이 화면의 일이다.
     camFit() 이 잴 때 여기에 적어 둔다. */
  var CAM_MIN = 1, CAM_MAX = 3.2;

  /* 세 정거장이 다 보이는 배율과 위치.
     판은 무대를 덮는 크기(cover)라 배율 1 이어도 양옆이 잘린다.
     핀이 차지하는 %범위를 실제 픽셀로 바꿔서, 그게 무대 안에 들어오는
     배율을 구한다. 여백은 8% 씩 둔다 — 핀 말풍선이 가장자리에 닿으면
     읽히지 않는다. */
  /* 동선이 다 보이는 배율과 위치.

     몇 번 불러도 같은 값이 나와야 한다. 그래서 「지금 보이는 크기」가
     아니라 배율 1 기준 좌표에서 잰다 —
       핀이 놓인 자리는 %로 알고 있다.
       이름표가 핀에서 얼마나 뻗는지만 화면에서 재고 배율로 나눈다.
     전에는 결과에 지금 배율을 다시 곱해서, render 가 여러 번 불릴 때마다
     배율이 곱해져 2.02 까지 올라갔다.

     이름표까지 재는 이유 — 말풍선이 핀보다 옆으로 150px 넘게 나간다.
     핀 좌표만 맞추면 장소 이름이 가장자리에서 잘린다. */
  function camFit(force) {
    if (camLocked && !force) return false;      /* 들어오는 장면이 쥐고 있다 */
    var f = $('ck_frame');
    var layer = $('ck_pinlayer');
    if (!f || !layer || !f.offsetWidth) return false;
    var st = f.parentElement;
    if (!st || !st.clientWidth) return false;

    var els = layer.querySelectorAll('.ck-pin:not(.ck-cand)');
    if (els.length < 2) return false;

    var fw = f.offsetWidth, fh = f.offsetHeight;
    var z0 = cam.z || 1;
    var x0 = Infinity, y0 = Infinity, x1 = -Infinity, y1 = -Infinity;
    var got = 0;

    for (var i = 0; i < els.length; i++) {
      var el = els[i];
      var r = el.getBoundingClientRect();
      if (!r.width) continue;

      /* 핀이 놓인 자리 — 배율과 무관하다 */
      var px = parseFloat(el.style.left) / 100 * fw;
      var py = parseFloat(el.style.top) / 100 * fh;
      if (!isFinite(px) || !isFinite(py)) continue;

      /* 이름표가 핀 자리에서 얼마나 뻗는가. 화면 값을 배율로 나눠
         배율 1 기준으로 되돌린다. .ck-pin 은 translate(-50%,-100%) 라
         핀 자리가 상자 안의 어디인지 비율로 잡는다 */
      var w0 = r.width / z0, h0 = r.height / z0;
      x0 = Math.min(x0, px - w0 * 0.5);
      x1 = Math.max(x1, px + w0 * 0.9);      /* 이름표는 대개 오른쪽으로 */
      y0 = Math.min(y0, py - h0);
      y1 = Math.max(y1, py + h0 * 0.3);
      got++;
    }
    if (got < 2 || !isFinite(x0) || x1 <= x0) return false;

    var pad = 40;   /* 이름표가 가장자리에 닿으면 안 읽힌다 */
    var needW = (x1 - x0) + pad * 2;
    var needH = (y1 - y0) + pad * 2;

    /* 동선 상자가 무대보다 크면 줄이고, 작으면 조금 키운다.
       .58 아래로는 안 내린다 — 모형 전체가 들어와 렉이 심해지고
       동선을 보는 데 도움도 안 된다.
       1.6 위로는 안 올린다 — 세 곳이 붙어 있어도 너무 당기지 않게 */
    var z = Math.min(st.clientWidth / needW, st.clientHeight / needH);
    z = Math.max(.58, Math.min(1.6, z));
    CAM_MIN = z;                             /* 축소 하한 = 이 자리 */
    cam.z = z;

    /* 상자 가운데를 무대 가운데로. 세로는 조금 위 —
       아래쪽에 경로 만들기 바가 있다 */
    var cx = (x0 + x1) / 2, cy = (y0 + y1) / 2;
    cam.x = st.clientWidth / 2 - cx * z;
    cam.y = st.clientHeight * .46 - cy * z;
    camApply();
    return true;
  }

  /* 배율은 버튼으로만 바꾼다.

     전에는 휠과 두 손가락으로도 됐다. 그런데 이 모형은 페이지 맨 위에
     화면을 꽉 채우고 있어서, 스크롤하려고 휠을 굴리면 페이지가 안 내려가고
     모형만 확대됐다. 내려갈 방법이 없어진다.
     실제 지도(카카오)는 지도 안에서만 휠을 먹으므로 그대로 둔다. */
  function initCamTools() {
    var rz = 0;
    w.addEventListener('resize', function () {
      clearTimeout(rz);
      rz = setTimeout(function () { if (!cam.user) camFit(); }, 180);
    });
  }

  function initTools() {
    initCamTools();
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
      cam.user = true; cam.z = Math.min(CAM_MAX, cam.z * 1.22); camApply();
    });
    if (zout) zout.addEventListener('click', function () {
      /* 하한은 「동선이 다 보이는 자리」다. 그보다 줄이면 모형 전체가
         들어와 렉이 심해지고 동선을 보는 데 도움도 안 된다 */
      cam.user = true; cam.z = Math.max(CAM_MIN, cam.z / 1.22); camApply();
    });
    if (zr) zr.addEventListener('click', function () {
      cam.user = false;
      if (!camFit()) camReset();
      fitMini();                 /* 작은 지도도 셋이 다 보이는 자리로 */
    });

    /* 작은 지도 — 여기도 버튼으로만. setZoomable(false) 로 휠은 막혀 있다.
       카카오는 숫자가 작을수록 확대다 */
    var mi = $('st_m_in'), mo = $('st_m_out');
    if (mi) mi.addEventListener('click', function () {
      if (mini) mini.setLevel(Math.max(1, mini.getLevel() - 1), { animate: true });
    });
    if (mo) mo.addEventListener('click', function () {
      if (mini) mini.setLevel(Math.min(12, mini.getLevel() + 1), { animate: true });
    });

    /* 작은 지도 접기 */
    var fold = $('st_fold'), box = $('st_mini');
    if (fold && box) {
      fold.addEventListener('click', function () {
        var open = fold.getAttribute('aria-expanded') === 'true';
        fold.setAttribute('aria-expanded', String(!open));
        fold.textContent = open ? '펼치기' : '접기';
        box.classList.toggle('fl-fold', open);
        if (!open) ready(function () { if (mini) { mini.relayout(); fitMini(); } });
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
  /* ── 들어오는 장면이 쓰는 손잡이 ──────────────────────
     app_home.js 가 연출을 맡고, 장면·지도·카메라는 여기 있다.
     둘을 한 파일에 합치면 서로를 못 읽는다. */

  /* 지도를 띄우고 세 곳이 다 들어오게 맞춘다.
     level 을 직접 주지 않는다 — 카카오가 좌표에서 계산하게 둔다.
     그래야 화면 폭이 달라도 세 곳이 안 잘린다. */
  function showWideMap() {
    var stage = d.querySelector('.ck-stage');
    var scene = stage && stage.querySelector('.ck-scene');
    var bmap = $('st_bigmap');
    if (!scene || !bmap) return false;

    scene.hidden = true;
    bmap.hidden = false;
    makeBig();
    if (!big) return false;

    var pts = route.filter(function (p) { return p.lat && p.lng; });
    if (pts.length < 2) return true;

    var b = new w.kakao.maps.LatLngBounds();
    pts.forEach(function (p) { b.extend(new w.kakao.maps.LatLng(p.lat, p.lng)); });
    big.relayout();
    /* 넉넉한 여백. 핀 번호가 가장자리에 닿으면 안 읽힌다 */
    big.setBounds(b, 120, 120, 120, 120);
    return true;
  }

  /* 지도를 한 곳으로 당긴다. 3D 로 넘어가기 직전에 쓴다 */
  function zoomMapTo(i) {
    var p = route[i];
    if (!big || !p || !p.lat) return;
    big.setLevel(3, { animate: { duration: 420 } });
    big.panTo(new w.kakao.maps.LatLng(p.lat, p.lng));
  }

  function showModel() {
    var stage = d.querySelector('.ck-stage');
    var scene = stage && stage.querySelector('.ck-scene');
    var bmap = $('st_bigmap');
    if (!scene || !bmap) return;
    bmap.hidden = true;
    scene.hidden = false;
    var mt = $('st_t_model'), mp = $('st_t_map');
    if (mt) mt.setAttribute('aria-pressed', 'true');
    if (mp) mp.setAttribute('aria-pressed', 'false');
  }

  /* 한 정거장을 화면 가운데로 — 그냥 옮긴다 */
  function camStop(i, zoom) {
    var f = $('ck_frame');
    var p = route[i];
    if (!p || !f || !f.offsetWidth) return;
    var st = f.parentElement;
    cam.user = false;
    cam.z = zoom || 1.95;
    cam.x = st.clientWidth / 2 - (p.x / 100 * f.offsetWidth) * cam.z;
    cam.y = st.clientHeight * .48 - (p.y / 100 * f.offsetHeight) * cam.z;
    camApply();
  }

  /* 한 정거장에서 다음으로 날아간다.

     같은 배율로 곧게 밀면 사진을 옆으로 미는 것처럼 보인다.
     실제로 「갔다」고 느끼게 하려면 물러나면서 옮기고 도착해서 당겨야 한다 —
     비행기에서 내려다보는 것과 같은 움직임이다. 그 물러남이 거리를 만든다.

     transform 하나만 계속 바꾼다. 벽시계로 진행하므로 탭이 뒤에 있어도
     중간에 굳지 않는다 — 굳으면 마지막 자리로 바로 간다. */
  var flyTimer = null;

  function camFly(i, ms, whenDone) {
    var f = $('ck_frame');
    var p = route[i];
    if (!p || !f || !f.offsetWidth) { if (whenDone) whenDone(); return; }
    var st = f.parentElement;

    var NEAR = 1.95;            /* 도착해서 당기는 배율 */
    var FAR = Math.max(.95, NEAR * .52);   /* 가는 동안 물러나는 배율 */
    var dur = ms || 1150;

    function place(z, t) {
      /* t=0 출발점, t=1 도착점. 가는 동안 좌표도 같이 옮긴다 */
      var from = route[Math.max(0, i - 1)] || p;
      var x = from.x + (p.x - from.x) * t;
      var y = from.y + (p.y - from.y) * t;
      cam.z = z;
      cam.x = st.clientWidth / 2 - (x / 100 * f.offsetWidth) * z;
      cam.y = st.clientHeight * .48 - (y / 100 * f.offsetHeight) * z;
      camApply();
    }

    cam.user = false;
    clearInterval(flyTimer);
    var t0 = Date.now();

    flyTimer = setInterval(function () {
      var e = Math.min(1, (Date.now() - t0) / dur);
      /* 배율은 가운데서 가장 멀어진다 — 물러났다가 다시 당긴다 */
      var arc = Math.sin(e * Math.PI);
      var z = NEAR + (FAR - NEAR) * arc;
      /* 좌표는 부드럽게 */
      var s = e < .5 ? 2 * e * e : 1 - Math.pow(-2 * e + 2, 2) / 2;
      place(z, s);
      if (e >= 1) {
        clearInterval(flyTimer);
        flyTimer = null;
        if (whenDone) whenDone();
      }
    }, 1000 / 50);
  }

  function camFlyStop() { clearInterval(flyTimer); flyTimer = null; }

  w.ckStage = {
    showWideMap: showWideMap,
    zoomMapTo: zoomMapTo,
    showModel: showModel,
    camStop: camStop,
    camFly: camFly,
    camFlyStop: camFlyStop,
    camLock: function (on) { camLocked = !!on; },
    camFit: function () { cam.user = false; if (!camFit(true)) camReset(true); },
    render: function (r, a, st) {
      route = r || []; alt = a || null;
      if (st) {
        if (st.order) state.order = st.order;
        if (st.hot != null) state.hot = st.hot;
        state.cand = !!st.cand;
      }
      path(); stops(); side();
      /* 처음에는 세 정거장이 다 보여야 한다. 한 곳만 크게 잡으면
         「여기서 저기로 간다」가 안 보인다. 한 번 맞춰 두고, 그 뒤
         장면이 바뀔 때만 그 장소로 옮긴다. */
      /* 첫 장면은 전체를 본다. 맞추기에 실패해도(판이 아직 없을 때)
         한 번 지나간 것으로 친다 — 아니면 장면이 바뀌어도 카메라가
         영영 안 움직인다. */
      if (!camFitted) { camFitted = true; camFit(); }
      else if (state.hot >= 0 && route[state.hot]) camTo(route[state.hot]);
      else if (!cam.user) camReset();
      /* 판이 아직 안 들어왔으면 offsetWidth 가 0 이라 맞출 수 없다.
         모형이 들어온 뒤에 한 번 더 시도한다. */
      ready(function () {
        makeMini(); mapPins();
        camFit();
      });
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
