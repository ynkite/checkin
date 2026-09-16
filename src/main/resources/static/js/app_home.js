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
  /* 히어로가 보여 주는 곳. 혼잡도를 이 지역으로 묻는다 */
  var HERO_REGION = '부산 해운대구';

  /* 보여 주는 날 — 다가오는 토요일.
     평일 값을 띄우면 「매우 혼잡합니다」라는 문구와 옆의 68(정상)이 싸운다.
     주말은 실제로 붐비고, 사람이 여행 가는 날도 주말이다. */
  function heroDate() {
    var d = new Date();
    d.setHours(0, 0, 0, 0);
    var add = (6 - d.getDay() + 7) % 7;      /* 6 = 토요일 */
    d.setDate(d.getDate() + add);
    return d;
  }

  function iso(d) {
    return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') +
           '-' + String(d.getDate()).padStart(2, '0');
  }

  var DOW = ['일', '월', '화', '수', '목', '금', '토'];

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
  function drawPins(route, hotIdx, alt) {
    var layer = $('ck_pinlayer');
    if (!layer) return;
    if (!layer.firstElementChild) {
      /* 도쿄 앱 프레임에서 읽은 모양 — 번호가 든 동그라미가 지점에 박히고
         이름표는 위가 아니라 옆에 붙는다. 번호가 있어야 순서가 보인다. */
      var html = route.map(function (p, i) { return pinHTML(p, i, ''); }).join('');
      /* 대체 후보는 같이 만들어 두고 CSS 로 숨긴다. 고를 때 새로 만들면
         들어오는 순간이 안 보인다. */
      if (alt) html += pinHTML(alt, route.length, ' ck-cand');
      layer.innerHTML = html;
    }
    var pins = layer.children;
    for (var i = 0; i < route.length; i++) {
      pins[i].classList.toggle('ck-hot', i === hotIdx);
    }
  }

  function pinHTML(p, i, extra) {
    /* 오른쪽 절반에 있는 핀은 이름표를 왼쪽으로 돌린다.
       55% 는 가장 긴 이름표(「해운대 해수욕장 14:20」 약 150px)가
       1440px 무대에서 오른쪽 끝을 안 넘는 자리다. */
    if (p.x > 55) extra += ' ck-lbl-left';
    return '<div class="ck-pin' + extra + '" style="--i:' + i +
           ';left:' + p.x.toFixed(2) + '%;top:' + p.y.toFixed(2) + '%">' +
           '<b class="ck-no">' + (p.no || i + 1) + '</b>' +
           '<span class="ck-bub">' + esc(p.name) +
           (p.note ? '<em>' + esc(p.note) + '</em>' : '') + '</span>' +
           '</div>';
  }

  /* ─────────────── 2. 나레이션 ───────────────
     세 장면을 순서대로 읽으면 이 제품이 하는 일이 한 문장씩 나온다.
       ① 오늘 순서  ② 어디가 얼마나 혼잡하고 언제 풀리는지  ③ 무엇을 바꿀 수 있는지
     혼잡도 등급은 crowd.js 가 정한다 (매우 혼잡 · 혼잡 · 정상 · 한적 · 매우 한적).

     at  — 재생 바에서 지금 어디쯤인지 (0~1)
     now — 재생 바 오른쪽에 적는 지금 자리 */
  var SCENE = [
    { at: 0,  now: '미포',
      line: '부산 1일차, 오후 순서입니다',
      why: '미포에서 출발해 해수욕장, 동백섬 순으로 갈 계획이었습니다.',
      hot: -1, live: 1, opts: 0 },
    { at: .5, now: '해운대 해수욕장',
      line: '해운대 해수욕장이 지금 <em>매우 혼잡</em>합니다',
      why: '평소 이 시각의 1.4배입니다. 17시 이후에는 지금보다 한산할 것으로 봅니다.',
      hot: 1, live: 1, opts: 0 },
    { at: 1,  now: '동백섬',
      line: '바꾸는 방법은 <em>세 가지</em>입니다',
      why: '고르면 그 자리에서 순서와 시각을 다시 잡고, 일행에게도 같은 화면이 갑니다.',
      hot: 1, live: 1, opts: 1 }
  ];

  /* 세 갈래. 고른 것이 모형 위에서 각각 다르게 보여야 고른 값이 있다.
       swap  순서를 바꾸면 핀 번호가 바뀐다
       cand  다른 곳으로 가면 후보 핀이 켜지고 원래 핀이 흐려진다
       live  가는 길을 바꾸면 해변길(1)에서 안쪽 도로(2)로 선이 넘어간다 */
  var OPTS = [
    { swap: 1, cand: 0, live: 1, order: [0, 2, 1] },
    { swap: 0, cand: 1, live: 1, order: [0, 1, 2] },
    { swap: 0, cand: 0, live: 2, order: [0, 1, 2] }
  ];

  function initHero() {
    var tl = $('ck_tl'), tw = $('ck_tw'), hero = $('ck_hero'), box = $('ck_opts');
    if (!tl) return;

    var bars = ['ck_p1', 'ck_p2', 'ck_p3'].map($).filter(Boolean);
    var opts = box ? [].slice.call(box.querySelectorAll('.ck-opt')) : [];
    var route = [], alt = null;
    var step = -1, timer = null, pick = 0, paused = false;
    var btn = $('ck_playbtn'), play = $('ck_play');

    /* 고른 방법을 모형에 반영한다. 장면 3에서만 부른다. */
    function applyOpt(n) {
      pick = n;
      opts.forEach(function (b, k) { b.setAttribute('aria-pressed', k === n ? 'true' : 'false'); });
      var o = OPTS[n], layer = $('ck_pinlayer');
      if (layer) {
        layer.classList.toggle('ck-cand-on', !!o.cand);
        var pins = layer.querySelectorAll('.ck-pin:not(.ck-cand)');
        for (var i = 0; i < pins.length; i++) {
          var no = pins[i].querySelector('.ck-no');
          /* 순서를 바꾸면 2번과 3번이 자리를 맞바꾼다 */
          if (no) no.textContent = o.swap && i >= 1 ? (i === 1 ? 3 : 2) : i + 1;
          pins[i].classList.toggle('ck-off', !!o.cand && i === 1);
        }
      }
      drawTrail(route, o.live);
      stage({ order: o.order, hot: 1, cand: !!o.cand });
    }

    /* 무대 주변 판(정거장 알약·오른쪽 판·작은 지도)은 app_stage.js 가 맡는다.
       없어도 나레이션은 돌아야 하므로 있을 때만 부른다. */
    function stage(st) {
      if (window.ckStage) window.ckStage.render(route, alt, st);
    }

    function draw(i) {
      var s = SCENE[i];
      tl.innerHTML = s.line;
      if (tw) tw.textContent = s.why;
      bars.forEach(function (b, k) {
        b.className = k < i ? 'ck-done' : (k === i ? 'ck-on' : '');
      });
      if (box) box.hidden = !s.opts;
      if (route.length) {
        drawPins(route, s.hot, alt);
        if (s.opts) {
          applyOpt(pick);
        } else {
          resetScene();
          drawTrail(route, s.live);
          stage({ order: [0, 1, 2], hot: s.hot, cand: false });
        }
      }

      var tr = $('ck_track'), nw = $('ck_playnow');
      if (tr) tr.style.setProperty('--at', (s.at * 100).toFixed(1) + '%');
      if (nw) nw.textContent = s.now;
    }

    function resetScene() {
      var layer = $('ck_pinlayer');
      if (!layer) return;
      layer.classList.remove('ck-cand-on');
      var pins = layer.querySelectorAll('.ck-pin:not(.ck-cand)');
      for (var i = 0; i < pins.length; i++) {
        var no = pins[i].querySelector('.ck-no');
        if (no) no.textContent = i + 1;
        pins[i].classList.remove('ck-off');
      }
    }

    function tick() {
      step = (step + 1) % SCENE.length;
      if (step === 0) pick = 0;          /* 한 바퀴 돌면 처음 고른 것으로 */
      draw(step);
      timer = setTimeout(tick, step === SCENE.length - 1 ? 6200 : 2800);
    }

    /* 방법을 고르면 재생은 멈춘다. 고른 것을 보고 있는데 화면이 넘어가면
       무엇을 고른 건지 사라진다. */
    opts.forEach(function (b, k) {
      b.addEventListener('click', function () {
        clearTimeout(timer); timer = null;
        paused = true;
        /* 고르는 순간 장면이 넘어가 있으면 문구와 고른 것이 어긋난다.
           방법을 고를 수 있는 장면으로 고정한다. */
        pick = k; step = SCENE.length - 1; draw(step);
        if (play) play.classList.add('ck-paused');
        if (btn) btn.setAttribute('aria-label', '순서 따라가기 시작');
        applyOpt(k);
      });
    });

    /* 재생 바 — 멈춤은 사람이 누른 것이고, 화면 밖으로 나가 멈춘 것과 다르다.
       둘을 섞으면 다시 스크롤했을 때 멈춰 둔 것이 저절로 돌아간다. */
    if (btn) {
      btn.addEventListener('click', function () {
        paused = !paused;
        if (play) play.classList.toggle('ck-paused', paused);
        btn.setAttribute('aria-label', paused ? '순서 따라가기 시작' : '순서 따라가기 멈춤');
        if (paused) { clearTimeout(timer); timer = null; }
        else if (!timer) tick();
      });
    }

    /* 히어로의 혼잡도를 실제 값으로 덮는다.
       json 에 박힌 값은 못 받을 때 쓰는 자리다. 메인은 제품이 무엇을 하는지
       보여 주는 자리라 여기 숫자가 꾸민 값이면 나머지도 그렇게 보인다. */
    function liveCrowd() {
      var names = route.map(function (p) { return p.name; });
      if (alt) names.push(alt.name);
      if (!names.length) return Promise.resolve();

      var when = heroDate();
      return fetch('/api/crowd/day?region=' + encodeURIComponent(HERO_REGION) +
                   '&date=' + iso(when) +
                   '&places=' + encodeURIComponent(names.join(',')))
        .then(function (r) { return r.ok ? r.json() : Promise.reject(r.status); })
        .then(function (j) {
          var list = (j && j.success && j.data) || [];
          var got = 0;
          list.forEach(function (f) {
            if (f.rate == null) return;
            var hit = route.filter(function (p) { return p.name === f.placeName; })[0];
            if (!hit && alt && alt.name === f.placeName) hit = alt;
            if (!hit) return;
            hit.crowd = Math.round(f.rate);
            hit.crowdLabel = f.levelLabel;
            got++;
          });
          if (got) { window.__ckCrowdLive = true; retellHero(when); }
        })
        .catch(function () { /* 못 받으면 json 값을 그대로 쓴다 */ });
    }

    /* 받은 값으로 문구를 다시 쓴다. 붐비지 않으면 붐빈다고 말하지 않는다 */
    function retellHero(when) {
      var day = (when.getMonth() + 1) + '월 ' + when.getDate() + '일 ' +
                DOW[when.getDay()] + '요일';
      var eye = document.querySelector('.st-head .fl-eye');
      if (eye) eye.textContent = '부산 해운대 · ' + day + ' 오후';

      /* 오른쪽 판의 날짜도 같은 날이어야 한다. 전에는 「9월 19일 금요일」이
         템플릿에 적혀 있었고 그 날은 토요일이었다. */
      var whenEl = document.getElementById('st_when');
      if (whenEl) whenEl.innerHTML = day + '<i>부산 해운대</i>';

      var worst = null;
      route.forEach(function (p) {
        if (p.crowd != null && (!worst || p.crowd > worst.crowd)) worst = p;
      });
      if (!worst) return;

      var g = (window.crowd && window.crowd(worst.crowd)) || null;
      var grade = worst.crowdLabel || (g && g.label) || '';
      var busy = worst.crowd >= 70;

      SCENE[1].now = worst.name;
      SCENE[1].line = esc(worst.name) + '이 그 날 <em>' + esc(grade) + '</em>합니다';
      SCENE[1].why = busy
        ? '집중률 ' + worst.crowd + '입니다. 0~100 눈금에서 70 위가 붐비는 자리입니다.'
        : '집중률 ' + worst.crowd + '입니다. 붐비지는 않지만 순서를 바꿔 볼 수 있습니다.';

      /* 대체 후보의 등급도 받은 값으로 */
      if (alt && alt.crowd != null) {
        var ag = (window.crowd && window.crowd(alt.crowd)) || null;
        var cell = document.querySelector('.ck-opt[data-o="1"] em');
        if (cell) {
          cell.innerHTML = esc(alt.crowdLabel || (ag && ag.label) || '') +
                           '<i>집중률 ' + alt.crowd + '</i>';
        }
        var sub = document.querySelector('.ck-opt[data-o="1"] span');
        if (sub) sub.textContent = route[1]
          ? (route[1].name + ' 대신 ' + alt.name + '으로') : alt.name + '으로';
      }
    }

    fetch(HERO_JSON, { cache: 'no-cache' })
      .then(function (r) { return r.ok ? r.json() : Promise.reject(r.status); })
      .then(function (j) { route = (j && j.route) || []; alt = (j && j.alt) || null; })
      .catch(function () { route = []; })
      .then(liveCrowd)
      .then(function () {
        stage({ order: [0, 1, 2], hot: -1, cand: false });
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

  /* ──────────────────────── 2.5 지금 한가한 곳 ────────────────────
     64 · 71 · 78 · 142 가 템플릿에 적혀 있었다. 손으로 적은 값이다.
     같은 지역에서 그 날 한적한 곳을 받아 채운다. 못 받으면 절을 감춘다 —
     지어낸 숫자를 두는 것보다 없는 편이 낫다. */

  function initQuiet() {
    var box = document.querySelector('#ck_free .ck-free') || document.querySelector('.ck-free');
    if (!box) return;
    var sec = box.closest('section');

    var when = heroDate();
    fetch('/api/crowd/quiet?region=' + encodeURIComponent(HERO_REGION) +
          '&date=' + iso(when) + '&limit=8')
      .then(function (r) { return r.ok ? r.json() : Promise.reject(r.status); })
      .then(function (j) {
        var list = (j && j.success && j.data) || [];
        list = list.filter(function (x) { return x.rate != null; });

        /* 「한가한 곳」에는 한가한 곳만 넣는다. 가장 낮은 순으로 와도
           그것들이 70 을 넘으면 붐비는 곳이다 — 71 을 「여유 있습니다」로
           적어 두면 가서 줄을 선다. */
        var quiet = list.filter(function (x) { return x.rate < 70; }).slice(0, 3);
        var busy = list[list.length - 1];        /* 가장 붐비는 곳 하나를 같이 */
        if (quiet.length < 2) { if (sec) sec.hidden = true; return; }

        var side = document.querySelector('#ck_free .ck-side') ||
                   (sec && sec.querySelector('.ck-side'));
        if (side) {
          side.textContent = (when.getMonth() + 1) + '월 ' + when.getDate() + '일 ' +
                             DOW[when.getDay()] + '요일 기준. 0~100 눈금입니다.';
        }
        var hd = sec && sec.querySelector('.ck-hd');
        if (hd) hd.setAttribute('data-label', '한국관광공사 집중률 예측');

        function card(x, hot) {
          var g = (window.crowd && window.crowd(x.rate)) || { key: 'mid', label: '정상' };
          var k = 'cw-' + g.key;
          return '<article class="ck-spot' + (hot ? ' ck-hot' : '') + '">' +
            '<div class="ck-nm">' + esc(x.placeName) + '</div>' +
            '<div class="ck-why">' + esc(x.sigunguName || '') +
              (hot ? ' · 다른 날을 보세요' : ' · 그 날 여유 있습니다') + '</div>' +
            '<div class="ck-vv"><b class="ck-fig">' + Math.round(x.rate) + '</b>' +
              '<i class="' + k + '">' + esc(x.levelLabel || g.label) + '</i></div>' +
            '<div class="ck-bar"><i class="' + k + '" style="width:' +
              Math.min(100, Math.max(6, Math.round(x.rate))) + '%"></i></div>' +
          '</article>';
        }

        box.innerHTML = quiet.map(function (x) { return card(x, false); }).join('') +
                        (busy && busy.rate >= 70 ? card(busy, true) : '');
      })
      .catch(function () { if (sec) sec.hidden = true; });
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
     혼잡도와 확정 비율은 이 제품이 파는 것이다. 다 그려진 채로
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
    initQuiet();
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
  /* $ 가 위 IIFE 안에만 있어서 이 블록이 통째로 죽어 있었다. 탭이 안 먹던 원인이다. */
  var $ = function (id) { return document.getElementById(id); };

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
