/*
 * 체크인 — 메인페이지 (app_home.js)
 *
 * 하는 일 넷
 *   1. 히어로 나레이션 — 붐빔을 감지해 순서를 바꾸는 과정을 3장면으로 보여준다
 *   2. 미니어처 SVG 주입 — 인라인하지 않고 fetch 로 받는다 (페이지 550KB 방지)
 *   3. 내 여행 — 로그인한 계정에서만 보인다
 *   4. 히어로 폼 → 실제 플래너로 값 전달
 *
 * 클래스·id 에 ck 접두어가 붙어 있다. 시안의 .hero/.card/.form/.st 가
 * styles_main.css 의 같은 이름과 충돌하기 때문이다. 떼지 말 것.
 */
(function () {
  'use strict';

  var $ = function (id) { return document.getElementById(id); };
  var reduce = matchMedia('(prefers-reduced-motion: reduce)').matches;

  /* ────────────────────────────── 1. 나레이션 ───────────────────────────── */

  var SCENE = [
    { line: '부산 1일차, 오후 순서입니다',
      why: '남포동에서 점심을 먹고 해운대로 갈 계획이었습니다.',
      stH: '14:30 도착 예정<br>집중률 재는 중', gH: 40, busyH: 0,
      stG: '17:30 도착 예정<br>집중률 재는 중', gG: 40, busyG: 0,
      sw: 0, chips: 0, ordH: '1', ordG: '2', lead: 'H' },
    { line: '해운대가 지금 <em>집중률 142</em>',
      why: '평소 이 시각의 1.4배입니다. 두 시간 뒤에도 비슷할 것으로 봅니다. 광안리는 71, 한가한 편입니다.',
      stH: '14:30 도착 예정<br><strong>집중률 142</strong> · 평소의 1.4배', gH: 95, busyH: 1,
      stG: '17:30 도착 예정<br>집중률 71 · 평소의 71%', gG: 47, busyG: 0,
      sw: 0, chips: 0, ordH: '1', ordG: '2', lead: 'H' },
    { line: '광안리를 <em>먼저 가는 쪽으로</em> 바꿨습니다',
      why: '해운대는 17시 40분으로 미뤘습니다. 그 시각이면 96까지 내려갑니다.',
      stH: '17:40으로 미룸<br>그 시각 집중률 96', gH: 64, busyH: 0,
      stG: '14:20으로 당김<br>집중률 71 · 한가함', gG: 47, busyG: 0,
      sw: 1, chips: 1, ordH: '2', ordG: '1', lead: 'G' }
  ];

  function initTell() {
    var tl = $('ck_tl'), tw = $('ck_tw'), plans = $('ck_plans'), places = $('ck_places');
    var cardH = $('ck_cardH'), cardG = $('ck_cardG'), hero = $('ck_hero');
    if (!tl || !cardH) return;

    var chips = ['ck_c1', 'ck_c2', 'ck_c3'].map($);
    var bars = ['ck_p1', 'ck_p2', 'ck_p3'].map($);
    var step = -1, timer = null;

    function draw(i) {
      var s = SCENE[i];
      tl.innerHTML = s.line;
      tw.textContent = s.why;
      $('ck_stH').innerHTML = s.stH;
      $('ck_stG').innerHTML = s.stG;
      $('ck_gH').style.width = s.gH + '%';
      $('ck_gG').style.width = s.gG + '%';
      cardH.classList.toggle('ck-busy', !!s.busyH);
      cardG.classList.toggle('ck-busy', !!s.busyG);
      cardH.classList.toggle('ck-lead', s.lead === 'H');
      cardG.classList.toggle('ck-lead', s.lead === 'G');
      $('ck_ordH').textContent = s.ordH;
      $('ck_ordG').textContent = s.ordG;
      plans.classList.toggle('ck-sw', !!s.sw);
      places.classList.toggle('ck-sw', !!s.sw);
      chips.forEach(function (c) { c.classList.toggle('ck-on', !!s.chips); });
      bars.forEach(function (b, k) {
        b.className = k < i ? 'ck-done' : (k === i ? 'ck-on' : '');
      });
    }

    function tick() {
      step = (step + 1) % SCENE.length;
      draw(step);
      timer = setTimeout(tick, step === SCENE.length - 1 ? 3800 : 2600);
    }

    if (reduce) { draw(2); return; }
    tick();
    /* 화면에서 벗어나면 멈춘다 — 모바일에서 배터리·CPU 를 계속 쓰지 않도록 */
    if (hero && window.IntersectionObserver) {
      new IntersectionObserver(function (es) {
        es.forEach(function (e) {
          if (e.isIntersecting) { if (!timer) tick(); }
          else { clearTimeout(timer); timer = null; }
        });
      }, { threshold: 0.08 }).observe(hero);
    }
  }

  /* ──────────────────── 2. 미니어처 SVG 주입 ────────────────────
     인라인하면 페이지가 550KB 가 된다. fetch 로 받아 넣으면 SVG 는 따로
     캐시되고, 클래스만 들어 있으니 tokens.css 의 [data-sky] 가 그대로 먹는다. */

  var CARDS = [
    { host: 'ck_cardH', url: '/img/mass_card_haeundae.svg' },
    { host: 'ck_cardG', url: '/img/mass_card_gwangalli.svg' }
  ];

  function injectMass() {
    CARDS.forEach(function (c) {
      var card = $(c.host);
      if (!card) return;
      var slot = card.querySelector('.ck-win .ck-in');
      if (!slot || slot.firstElementChild) return;
      fetch(c.url, { cache: 'force-cache' })
        .then(function (r) { return r.ok ? r.text() : Promise.reject(r.status); })
        .then(function (svg) {
          slot.innerHTML = svg;
          var el = slot.querySelector('svg');
          if (el) { el.classList.add('mass'); el.setAttribute('aria-hidden', 'true'); }
        })
        .catch(function () { /* 못 받으면 빈 판이 남는다. 화면은 깨지지 않는다 */ });
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

    var cc = $('ck_cc'), nt = $('ck_nt');
    if (cc && nt) {
      cc.addEventListener('click', function (e) {
        var b = e.target.closest('button');
        if (!b) return;
        b.setAttribute('aria-pressed', String(b.getAttribute('aria-pressed') !== 'true'));
        var picked = [].slice.call(cc.querySelectorAll('[aria-pressed="true"]'))
                       .map(function (x) { return x.textContent.trim(); });
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
    initTell();
    initForm();
    initMine();
    initSky();
    if ('requestIdleCallback' in window) requestIdleCallback(injectMass, { timeout: 1200 });
    else setTimeout(injectMass, 200);
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', start);
  else start();
})();
