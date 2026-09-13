/*
 * 체크인 — 메인페이지 (app_home.js)
 *
 * 하는 일 다섯
 *   1. 무대 — mass_hero.svg 를 fetch 로 넣는다 (인라인하면 페이지가 500KB)
 *   2. 핀·경로 — mass_hero.json 의 좌표(%)로 장면 위에 얹고, 3장면으로 움직인다
 *   3. 내 여행 — 로그인한 계정에서만 보인다
 *   4. 히어로 폼 → 실제 플래너로 값 전달
 *   5. 하늘 — 시각에 따라 풍경이 연속으로 어두워진다
 *
 * 클래스·id 에 ck 접두어가 붙어 있다. 시안의 .hero/.card/.form/.st 가
 * styles_main.css 의 같은 이름과 충돌하기 때문이다. 떼지 말 것.
 */
(function () {
  'use strict';

  var $ = function (id) { return document.getElementById(id); };
  var reduce = matchMedia('(prefers-reduced-motion: reduce)').matches;

  /* ─────────────── 1. 무대 · 핀 · 경로 ───────────────
     미니어처를 배경 장식이 아니라 무대로 쓴다.
     장면은 fetch 로 넣고(인라인하면 페이지가 500KB 가 된다),
     핀은 mass_hero.json 의 좌표(%)로 장면 위에 얹는다. */

  var HERO_SVG = '/img/mass_hero.svg';
  var HERO_JSON = '/img/mass_hero.json';

  function injectScene() {
    var host = $('ck_scene');
    if (!host || host.firstElementChild) return Promise.resolve(null);
    return fetch(HERO_SVG, { cache: 'force-cache' })
      .then(function (r) { return r.ok ? r.text() : Promise.reject(r.status); })
      .then(function (svg) {
        host.innerHTML = svg;
        var el = host.querySelector('svg');
        if (el) { el.classList.add('mass'); el.setAttribute('aria-hidden', 'true'); }
        return el;
      })
      .catch(function () { return null; });   /* 못 받아도 글은 읽힌다 */
  }

  /* 경로를 점선으로 잇는다. 좌표가 %라 viewBox 0 0 100 100 에 그대로 들어간다. */
  function drawTrail(route, liveIdx) {
    var svg = $('ck_trail');
    if (!svg || route.length < 2) return;
    var d = route.map(function (p, i) {
      return (i ? 'L' : 'M') + p.x.toFixed(2) + ' ' + p.y.toFixed(2);
    }).join(' ');
    var live = '';
    if (liveIdx > 0 && route[liveIdx - 1] && route[liveIdx]) {
      var a = route[liveIdx - 1], b = route[liveIdx];
      live = '<path class="ck-live" d="M' + a.x.toFixed(2) + ' ' + a.y.toFixed(2) +
             ' L' + b.x.toFixed(2) + ' ' + b.y.toFixed(2) + '"/>';
    }
    svg.innerHTML = '<path d="' + d + '"/>' + live;
  }

  function drawPins(route, hotIdx) {
    var layer = $('ck_pinlayer');
    if (!layer) return;
    layer.innerHTML = route.map(function (p, i) {
      var hot = i === hotIdx;
      return '<div class="ck-pin' + (hot ? ' ck-hot' : '') + '"' +
             ' style="left:' + p.x.toFixed(2) + '%;top:' + p.y.toFixed(2) + '%">' +
             '<span class="ck-bub">' + esc(p.name) +
             (p.note ? '<em>' + esc(p.note) + '</em>' : '') + '</span>' +
             '<span class="ck-dot"></span>' +
             '</div>';
    }).join('');
  }

  /* ─────────────── 2. 나레이션 ───────────────
     장면 위의 핀과 아래 문구가 같은 장면을 설명한다.
     3장면: 원래 순서 -> 붐빔 감지 -> 순서 교체 */

  var SCENE = [
    { line: '부산 1일차, 오후 순서입니다',
      why: '남포동에서 점심을 먹고 해운대로 갈 계획이었습니다.',
      hot: -1, live: 1, chips: 0, sw: 0 },
    { line: '해운대가 지금 <em>집중률 142</em>',
      why: '평소 이 시각의 1.4배입니다. 두 시간 뒤에도 비슷할 것으로 봅니다. 광안리는 71, 한가한 편입니다.',
      hot: 1, live: 1, chips: 0, sw: 0 },
    { line: '광안리를 <em>먼저 가는 쪽으로</em> 바꿨습니다',
      why: '해운대는 17시 40분으로 미뤘습니다. 그 시각이면 96까지 내려갑니다.',
      hot: 1, live: 2, chips: 1, sw: 1 }
  ];

  function initHero() {
    var tl = $('ck_tl'), tw = $('ck_tw'), plans = $('ck_plans'), hero = $('ck_hero');
    if (!tl) return;

    var chips = ['ck_c1', 'ck_c2', 'ck_c3'].map($).filter(Boolean);
    var bars = ['ck_p1', 'ck_p2', 'ck_p3'].map($).filter(Boolean);
    var route = [];
    var step = -1, timer = null;

    function draw(i) {
      var s = SCENE[i];
      tl.innerHTML = s.line;
      if (tw) tw.textContent = s.why;
      if (plans) plans.classList.toggle('ck-sw', !!s.sw);
      chips.forEach(function (c) { c.classList.toggle('ck-on', !!s.chips); });
      bars.forEach(function (b, k) {
        b.className = k < i ? 'ck-done' : (k === i ? 'ck-on' : '');
      });
      if (route.length) { drawPins(route, s.hot); drawTrail(route, s.live); }
    }

    function tick() {
      step = (step + 1) % SCENE.length;
      draw(step);
      timer = setTimeout(tick, step === SCENE.length - 1 ? 4200 : 2800);
    }

    fetch(HERO_JSON, { cache: 'force-cache' })
      .then(function (r) { return r.ok ? r.json() : Promise.reject(r.status); })
      .then(function (j) { route = (j && j.route) || []; })
      .catch(function () { route = []; })
      .then(function () {
        if (reduce) { draw(SCENE.length - 1); return; }
        tick();
        if (hero && window.IntersectionObserver) {
          new IntersectionObserver(function (es) {
            es.forEach(function (e) {
              if (e.isIntersecting) { if (!timer) tick(); }
              else { clearTimeout(timer); timer = null; }
            });
          }, { threshold: 0.05 }).observe(hero);
        }
      });
  }

  /* ────────────────────────── 3. 내 여행 ──────────────────────────
     로그인한 계정에서만 보인다. 비로그인은 섹션 자체가 렌더되지 않는다. */

  function token() {
    try {
      if (typeof Token !== 'undefined' && Token.getAccess && Token.getAccess()) return Token.getAccess();
    } catch (e) {}
    return localStorage.getItem('accessToken') || localStorage.getItem('access_token');
  }

  function esc(s) {
    return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }

  function isOngoing(t) {
    if (!t.startDate || !t.endDate) return false;
    var today = new Date().toISOString().slice(0, 10);
    return t.startDate <= today && today <= t.endDate;
  }

  function renderMine(list) {
    var row = $('ck_mine_row');
    if (!row) return;
    if (!list.length) {
      row.innerHTML = '<div class="ck-empty">아직 없습니다. 위에서 지역과 기간을 고르면 첫 경로가 생깁니다.</div>';
      return;
    }
    /* 여행 중 → 다가오는 → 지난 순. 각 묶음 안에서는 최근 수정순 */
    var rank = function (t) { return isOngoing(t) ? 0 : (t.status === 'UPCOMING' ? 1 : 2); };
    list.sort(function (a, b) {
      return rank(a) - rank(b) ||
             String(b.updatedAt || '').localeCompare(String(a.updatedAt || ''));
    });

    row.innerHTML = list.slice(0, 6).map(function (t) {
      var live = isOngoing(t)
        ? '<span class="ck-live">여행 중</span>'
        : '';
      var when = t.status === 'UPCOMING' ? '다가오는 여행' : '지난 여행';
      return '<button type="button" class="ck-trip" onclick="ckOpenTrip(' + Number(t.id) + ')">' +
             live +
             '<span class="ck-when ck-fig">' + esc(when) + '</span>' +
             '<span class="ck-nm">' + esc(t.title || t.destination || '제목 없는 여행') + '</span>' +
             '<span class="ck-why">' + esc(t.meta || '') +
             (t.budget ? ' · ' + esc(t.budget) : '') + '</span>' +
             '</button>';
    }).join('');
  }

  function initMine() {
    var sec = $('ck_mine');
    if (!sec) return;
    var tk = token();
    if (!tk) return;                       // 비로그인 — 섹션을 띄우지 않는다
    sec.classList.add('ck-on');

    fetch('/api/trips', { headers: { Authorization: 'Bearer ' + tk } })
      .then(function (r) { return r.ok ? r.json() : Promise.reject(r.status); })
      .then(function (j) {
        var list = Array.isArray(j) ? j : (j && j.data) || [];
        renderMine(list);
      })
      .catch(function () {
        var row = $('ck_mine_row');
        if (row) row.innerHTML = '<div class="ck-empty">목록을 가져오지 못했습니다. 잠시 뒤에 다시 열어 보세요.</div>';
      });
  }

  window.ckOpenTrip = function (id) {
    try { sessionStorage.setItem('openTripId', String(id)); } catch (e) {}
    if (typeof go === 'function') go('map');
    else location.hash = '#map';
  };

  /* ──────────────── 4. 히어로 폼 → 실제 플래너 ────────────────
     여기서 경로를 만들지 않는다. 고른 값을 넘기고 플래너 1단계로 보낸다.
     플래너에는 14개 필드가 있고 여기는 4개뿐이라, 나머지는 그쪽에서 채운다. */

  var NIGHTS = { '당일': 0, '1박 2일': 1, '2박 3일': 2, '3박 4일': 3 };

  function initForm() {
    var form = $('ck_mk');
    if (!form) return;

    var cc = $('ck_cc'), nt = $('ck_nt'), cb = $('ck_condbtn'), cn = $('ck_condn');

    /* 조건 칩은 기본으로 접어 둔다. 한 줄에 컨트롤 열 개를 늘어놓지 않는다. */
    if (cb && cc) {
      cb.addEventListener('click', function () {
        var open = cb.getAttribute('aria-expanded') === 'true';
        cb.setAttribute('aria-expanded', String(!open));
        cc.classList.toggle('ck-open', !open);
      });
    }

    if (cc && nt) {
      cc.addEventListener('click', function (e) {
        var b = e.target.closest('button');
        if (!b) return;
        b.setAttribute('aria-pressed', String(b.getAttribute('aria-pressed') !== 'true'));
        var picked = [].slice.call(cc.querySelectorAll('[aria-pressed="true"]'))
                       .map(function (x) { return x.textContent.trim(); });
        if (cn) cn.textContent = picked.length ? picked.length : '';
        nt.textContent = picked.length
          ? picked.join(' · ') + ' 조건에 맞는 곳만 후보에 올립니다.'
          : '조건에 안 맞는 곳은 후보에서 빠집니다.';
      });
    }

    form.addEventListener('submit', function (e) {
      e.preventDefault();
      var dest = $('ck_f1') ? $('ck_f1').value : '';
      var span = $('ck_f2') ? $('ck_f2').value : '';
      var start = $('ck_f3') ? $('ck_f3').value : '';
      var pax = $('ck_f4') ? parseInt($('ck_f4').value, 10) || 2 : 2;
      var nights = NIGHTS[span] != null ? NIGHTS[span] : 2;

      var end = start;
      if (start) {
        var d = new Date(start + 'T00:00:00');
        d.setDate(d.getDate() + nights);
        end = d.toISOString().slice(0, 10);
      }

      var companions = cc
        ? [].slice.call(cc.querySelectorAll('[aria-pressed="true"]'))
              .map(function (x) { return x.textContent.trim(); })
        : [];

      try {
        sessionStorage.setItem('ckHeroPick', JSON.stringify({
          destination: dest, startDate: start, endDate: end,
          nights: nights, pax: pax, companions: companions
        }));
      } catch (err) {}

      if (typeof goNewPlanner === 'function') goNewPlanner();
      else if (typeof go === 'function') go('planner');
    });
  }

  /* 플래너가 열릴 때 히어로에서 고른 값을 채운다.
     플래너 쪽 JS 를 고치지 않기 위해 여기서 필드에 직접 넣고 이벤트를 발생시킨다. */
  window.ckApplyHeroPick = function () {
    var raw;
    try { raw = sessionStorage.getItem('ckHeroPick'); } catch (e) { return; }
    if (!raw) return;
    var p;
    try { p = JSON.parse(raw); } catch (e) { return; }

    var s = document.getElementById('s1-date-start');
    var t = document.getElementById('s1-date-end');
    if (s && p.startDate) { s.value = p.startDate; if (typeof onStartDateChange === 'function') onStartDateChange(); }
    if (t && p.endDate) { t.value = p.endDate; if (typeof onEndDateChange === 'function') onEndDateChange(); }
    try { sessionStorage.removeItem('ckHeroPick'); } catch (e) {}
  };

  /* ─────────────── 5. 하늘을 분 단위로 따라가게 한다 ───────────────
     index.html 의 head 에서 한 번 정해지고, 여기서 계속 갱신한다.
     --mix 한 값만 바뀌므로 비용이 없다. 탭이 숨으면 멈춘다. */

  function initSky() {
    if (typeof window.ckSky !== 'function') return;
    var timer = null;
    function loop() {
      window.ckSky();
      timer = setTimeout(loop, 60000);
    }
    loop();
    document.addEventListener('visibilitychange', function () {
      if (document.hidden) { clearTimeout(timer); timer = null; }
      else if (!timer) loop();
    });
  }

  /* ─────────────────────────── 시작 ─────────────────────────── */

  function start() {
    if (!$('ck_hero')) return;          // 메인페이지가 아니면 아무것도 하지 않는다
    initHero();
    initForm();
    initMine();
    initSky();
    if ('requestIdleCallback' in window) requestIdleCallback(injectScene, { timeout: 1200 });
    else setTimeout(injectScene, 200);
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', start);
  else start();
})();
