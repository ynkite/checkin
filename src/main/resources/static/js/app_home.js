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

  /* 경로는 SVG 안에 이미 들어 있다.

     mass_hero.svg 는 같은 두 지점을 잇는 길 두 개를 갖고 있다 —
     해변을 따라가는 길(rt-*)과 안쪽 도로로 도는 길(rb-*). 둘 다 실제
     도로 모양이다. 여기서 %좌표로 직선을 하나 더 그으면 선이 세 겹이
     되고, 그중 하나만 도로를 안 따라간다. 그래서 긋지 않는다.

     장면에 따라 어느 길이 살아 있는지만 바꾼다.
     .rt-* / .rb-* 는 styles_massing.css 에서 이미 opacity 전환을 갖는다. */
  function drawTrail(route, liveIdx) {
    var host = $('ck_scene');
    if (!host) return;
    /* SVG 요소가 아니라 담는 div 에 표시한다. injectScene 이 svg 를
       나중에 갈아 끼우므로 svg 에 붙인 클래스는 날아간다. */
    host.classList.toggle('ck-alt', liveIdx >= 2);
  }

  /* 핀도 마찬가지다. 다시 만들면 들어오는 애니메이션이 매번 처음부터
     돌고, 붐비는 곳이 바뀌는 순간이 안 보인다. 클래스만 바꾼다. */
  function drawPins(route, hotIdx) {
    var layer = $('ck_pinlayer');
    if (!layer) return;
    if (!layer.firstElementChild) {
      /* 도쿄 앱 프레임에서 읽은 모양 — 번호가 든 동그라미가 지점에 박히고
         이름표는 위가 아니라 옆에 붙는다. 번호가 있어야 순서가 보인다. */
      layer.innerHTML = route.map(function (p, i) {
        return '<div class="ck-pin" style="--i:' + i +
               ';left:' + p.x.toFixed(2) + '%;top:' + p.y.toFixed(2) + '%">' +
               '<b class="ck-no">' + (p.no || i + 1) + '</b>' +
               '<span class="ck-bub">' + esc(p.name) +
               (p.note ? '<em>' + esc(p.note) + '</em>' : '') + '</span>' +
               '</div>';
      }).join('');
    }
    var pins = layer.children;
    for (var i = 0; i < pins.length; i++) {
      pins[i].classList.toggle('ck-hot', i === hotIdx);
    }
  }

  /* ─────────────── 2. 나레이션 ───────────────
     장면 위의 핀과 아래 문구가 같은 장면을 설명한다.
     3장면: 원래 순서 -> 붐빔 감지 -> 순서 교체 */

  /* at  — 재생 바에서 지금 어디쯤인지 (0~1)
     now — 재생 바 오른쪽에 적는 지금 자리 */
  var SCENE = [
    { at: 0,   now: '남포동',
      line: '부산 1일차, 오후 순서입니다',
      why: '남포동에서 점심을 먹고 해운대로 갈 계획이었습니다.',
      hot: -1, live: 1, chips: 0, sw: 0 },
    { at: .5,  now: '해변 한가운데',
      line: '해운대가 지금 <em>집중률 142</em>',
      why: '평소 이 시각의 1.4배입니다. 두 시간 뒤에도 비슷할 것으로 봅니다. 광안리는 71, 한가한 편입니다.',
      hot: 1, live: 1, chips: 0, sw: 0 },
    { at: 1,   now: '해변 서쪽 끝',
      line: '광안리를 <em>먼저 가는 쪽으로</em> 바꿨습니다',
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

      var tr = $('ck_track'), nw = $('ck_playnow');
      if (tr) tr.style.setProperty('--at', (s.at * 100).toFixed(1) + '%');
      if (nw) nw.textContent = s.now;
    }

    function tick() {
      step = (step + 1) % SCENE.length;
      draw(step);
      timer = setTimeout(tick, step === SCENE.length - 1 ? 4200 : 2800);
    }

    /* 재생 바 — 멈춤은 사람이 누른 것이고, 화면 밖으로 나가 멈춘 것과 다르다.
       둘을 섞으면 다시 스크롤했을 때 멈춰 둔 것이 저절로 돌아간다. */
    var paused = false;
    var btn = $('ck_playbtn'), play = $('ck_play');
    if (btn) {
      btn.addEventListener('click', function () {
        paused = !paused;
        if (play) play.classList.toggle('ck-paused', paused);
        btn.setAttribute('aria-label', paused ? '순서 따라가기 시작' : '순서 따라가기 멈춤');
        if (paused) { clearTimeout(timer); timer = null; }
        else if (!timer) tick();
      });
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
              if (e.isIntersecting) { if (!timer && !paused) tick(); }
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
          : '고른 조건에 안 맞는 곳은 아예 후보에서 뺍니다.';
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
     ckSky 가 oklab 에서 섞어 :root 에 넣는다. 탭이 숨으면 멈춘다. */

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

  /* ─────────────── 6. 숫자가 차오른다 ───────────────
     집중률과 확정 비율은 이 제품이 파는 것이다. 다 그려진 채로
     스크롤에 들어오면 그냥 인쇄물이다. 화면에 들어올 때 0 에서
     실제 값까지 올린다.

     값은 HTML 에 이미 적혀 있다. 0 으로 내리는 것은 화면 밖에
     있을 때만 한다. 관찰자가 없거나 움직임을 끄면 손대지 않는다. */

  function countUp(el, to, ms) {
    var t0 = 0;
    function step(now) {
      if (!t0) t0 = now;
      var k = Math.min(1, (now - t0) / ms);
      k = 1 - Math.pow(1 - k, 3);                 /* 끝에서 부드럽게 선다 */
      var v = String(Math.round(to * k));
      if (el.nodeType === 3) el.nodeValue = v; else el.textContent = v;
      if (k < 1) requestAnimationFrame(step);
    }
    requestAnimationFrame(step);
  }

  function initCounters() {
    if (reduce || !window.IntersectionObserver) return;

    var groups = [];
    ['ck_free', 'ck_budget'].forEach(function (id) {
      var sec = $(id);
      if (!sec) return;

      var items = [];
      sec.querySelectorAll('.ck-fig').forEach(function (b) {
        var n = parseInt((b.textContent || '').replace(/[^0-9]/g, ''), 10);
        if (!isNaN(n) && n > 0 && b.children.length === 0) {
          items.push({ el: b, to: n });
        }
      });
      /* 확정 비율은 「80<span>%</span>」 이라 자식이 있다. 앞 숫자만 센다 */
      sec.querySelectorAll('.ck-big').forEach(function (b) {
        var first = b.firstChild;
        if (!first || first.nodeType !== 3) return;
        var n = parseInt(first.nodeValue.replace(/[^0-9]/g, ''), 10);
        if (!isNaN(n) && n > 0) items.push({ el: first, to: n, text: true });
      });
      var bars = [];
      sec.querySelectorAll('.ck-bar > i').forEach(function (i) {
        var w = i.style.width, f = i.style.flexBasis || i.style.flex;
        if (w) bars.push({ el: i, prop: 'width', to: w });
        else if (f && f.indexOf('%') > -1) bars.push({ el: i, prop: 'flexBasis', to: f });
      });
      if (!items.length && !bars.length) return;

      /* 화면 안에 이미 들어와 있으면 건드리지 않는다 */
      var r = sec.getBoundingClientRect();
      if (r.top < window.innerHeight * 0.9) return;

      items.forEach(function (x) {
        if (x.text) x.el.nodeValue = '0'; else x.el.textContent = '0';
      });
      bars.forEach(function (x) { x.el.style[x.prop] = '0%'; });
      groups.push({ sec: sec, items: items, bars: bars });
    });

    if (!groups.length) return;

    var io = new IntersectionObserver(function (es) {
      es.forEach(function (e) {
        if (!e.isIntersecting) return;
        var g = groups.filter(function (x) { return x.sec === e.target; })[0];
        if (!g) return;
        io.unobserve(e.target);
        g.bars.forEach(function (x, i) {
          setTimeout(function () { x.el.style[x.prop] = x.to; }, i * 70);
        });
        g.items.forEach(function (x, i) {
          setTimeout(function () { countUp(x.el, x.to, 620); }, i * 70);
        });
      });
    }, { threshold: 0.25 });

    groups.forEach(function (g) { io.observe(g.sec); });
  }

  /* 상단 바 — 맨 위에서는 비워 두고 스크롤하면 바탕이 생긴다.
     흰 띠가 전면 미니어처를 가로지르지 않게. */
  function initNav() {
    var nav = document.querySelector('nav');
    if (!nav) return;
    /* 전면 미니어처가 있는 메인에서만 맨 위를 비운다.
       다른 화면은 흰 바탕이라 처음부터 바탕을 깔아야 경계가 보인다. */
    var overHero = !!$('ck_hero');
    function sync() { nav.classList.toggle('nav-solid', !overHero || window.scrollY > 12); }
    sync();
    if (overHero) window.addEventListener('scroll', sync, { passive: true });
  }

  function start() {
    initNav();                          // 상단 바는 모든 화면에 있다
    if (!$('ck_hero')) return;          // 나머지는 메인페이지에서만
    initHero();
    initForm();
    initMine();
    initSky();
    initCounters();
    if ('requestIdleCallback' in window) requestIdleCallback(injectScene, { timeout: 1200 });
    else setTimeout(injectScene, 200);
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', start);
  else start();
})();

/* 「많이 담긴 곳」 탭 — 네 종류를 한 절에 넣고 하나씩 보여 준다.
   그리드는 initMainPage() 가 모두 채운다. 여기서는 보이기만 바꾼다. */
(function () {
  /* 탭 두 벌 — 바깥은 일정/장소, 안쪽은 맛집·숙소·관광지·카페.
     둘 다 「고른 것만 보인다」라 한 곳에서 처리한다. */
  function initKindTabs() {
    var sec = $('ck_pop');
    if (!sec) return;

    sec.addEventListener('click', function (e) {
      var b = e.target.closest('.ck-tabs button[data-pane], .ck-tabs button[data-pop]');
      if (!b) return;
      var bar = b.parentElement;
      bar.querySelectorAll('button').forEach(function (x) {
        x.setAttribute('aria-selected', String(x === b));
      });

      if (b.dataset.pane) {
        ['ck_pane_trip', 'ck_pane_place'].forEach(function (id) {
          var p = $(id);
          if (p) p.hidden = (id !== b.dataset.pane);
        });
      } else {
        sec.querySelectorAll('.ck-pop').forEach(function (g) {
          g.hidden = (g.id !== 'popular-' + b.dataset.pop + '-grid');
        });
      }
    });
  }
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initKindTabs);
  } else {
    initKindTabs();
  }
})();
