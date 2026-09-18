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
    /* 핀이 있나로 본다. firstElementChild 로 보면 drawLine 이 넣은
       선 svg 가 첫 자식이라, 선을 먼저 그은 뒤에는 핀이 영영 안 생긴다. */
    if (!layer.querySelector('.ck-pin')) {
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

  /* 핀에 붙는 혼잡도. 모형 위에서 바로 읽혀야 한다 —
     옆 판을 봐야 알 수 있으면 모형이 데이터가 아니라 그림이 된다. */
  function crowdChip(p) {
    if (p.crowd == null) return '';
    var g = (window.crowd && window.crowd(p.crowd)) || null;
    if (!g) return '';
    return '<i class="ck-cw cw-' + g.key + '">' + Math.round(p.crowd) + '</i>';
  }

  function pinHTML(p, i, extra) {
    /* 오른쪽 절반에 있는 핀은 이름표를 왼쪽으로 돌린다.
       55% 는 가장 긴 이름표(「해운대 해수욕장 14:20」 약 150px)가
       1440px 무대에서 오른쪽 끝을 안 넘는 자리다. */
    if (p.x > 55) extra += ' ck-lbl-left';
    /* 모형 밖에 있는 곳. 가장자리에 붙이고 거리를 같이 적는다 —
       화면 밖으로 나가는 선이 「멀리 왔다갔다」를 그대로 보여 준다.
       없는 건물을 지어 넣는 것보다 정직하다. */
    if (p.off) extra += ' ck-far';
    return '<div class="ck-pin' + extra + '" style="--i:' + i +
           ';left:' + p.x.toFixed(2) + '%;top:' + p.y.toFixed(2) + '%">' +
           '<b class="ck-no">' + (p.no || i + 1) + '</b>' +
           '<span class="ck-bub">' + esc(p.name) +
           (p.dist ? '<i class="ck-dist">' + esc(p.dist) + '</i>' : '') +
           (p.note ? '<em>' + esc(p.note) + '</em>' : '') +
           crowdChip(p) + '</span>' +
           '</div>';
  }

  /* 지금 보고 있는 한 곳만 남긴다.

     모형은 동네마다 한 장이다. 해운대 모형 위에 남포동 핀을 찍으면
     그 자리는 해운대 안의 어딘가일 뿐이다 — 「코앞 바다가 바뀌는 것
     같다」는 말이 나온 곳이 여기다. 그래서 그 장소의 모형을 보고 있을
     때는 그 핀 하나만 둔다. 선이 장소까지 안 닿는 문제도 같이 없어진다.

     동선 전체는 지도에서 보여 준다. 거기서는 좌표가 실제 위경도라
     선이 핀 가운데에 정확히 닿는다. */
  function soloPin(p, no) {
    var layer = $('ck_pinlayer');
    if (!layer) return;
    layer.innerHTML = pinHTML({
      name: p.name, note: p.note, dist: p.dist,
      crowd: p.crowd, crowdLabel: p.crowdLabel, no: no,
      x: (p.sx != null ? p.sx : p.x),
      y: (p.sy != null ? p.sy : p.y)
    }, 0, ' ck-solo');
  }

  /* 전체로 돌아간다. 세 곳을 실제 방위대로 놓고 선으로 잇는다.
     자리는 경도를 가로, 위도를 세로로 편 값이라 순서와 방향이 실제와
     같다. 이름표가 겹치는 만큼만 벌렸고, 실제 거리는 핀에 글자로
     적는다 — 가까워 보이는 두 곳이 몇 km 인지 화면에 못 박아 둔다. */
  function allPins(route, alt) {
    var layer = $('ck_pinlayer');
    if (!layer) return;
    layer.innerHTML =
      route.map(function (p, i) { return pinHTML(p, i, ''); }).join('') +
      (alt ? pinHTML(alt, route.length, ' ck-cand') : '');
    drawLine(route);
  }

  /* ─────────────── 1-b. 들어오는 장면 ───────────────
     선이 그어지고 → 판이 들어오고 → 지금 시각·날씨로 돌아온다.
     핀의 %좌표를 그대로 쓰므로 모형이 바뀌어도 따라간다. */

  /* ── 기계가 버티는지 한 번 재고 정한다 ──────────────────
     모션을 고정으로 줄이면 좋은 기계에서도 심심해진다.
     반대로 다 켜 두면 버거운 기계에서 뚝뚝 끊긴다.
     그래서 들어온 뒤 한 번 재고 그 값을 이 창에서 계속 쓴다.
     계속 재면 그게 또 비용이다.

     기준 — 1초 동안 33ms(30프레임)를 넘긴 프레임이 넷 이상이면 버겁다.
     하나둘은 다른 탭이 뭘 했을 수도 있으니 넘긴다. */
  function gradeMotion() {
    var r = document.documentElement;
    if (reduce) { r.setAttribute('data-motion', 'lite'); return; }

    /* 이 창에서 이미 정했으면 그대로 쓴다 */
    try {
      var seen = sessionStorage.getItem('ckMotion');
      if (seen) { r.setAttribute('data-motion', seen); return; }
    } catch (e) {}

    r.setAttribute('data-motion', 'full');

    var t0 = performance.now(), last = t0, slow = 0, n = 0;
    function step(now) {
      var dt = now - last; last = now; n++;
      if (dt > 33) slow++;
      if (now - t0 < 1000) { requestAnimationFrame(step); return; }

      /* 프레임이 아예 안 돌았으면(탭이 숨어 있었다) 판단하지 않는다 */
      if (n < 20) return;

      var g = slow >= 4 ? 'lite' : 'full';
      r.setAttribute('data-motion', g);
      try { sessionStorage.setItem('ckMotion', g); } catch (e) {}
      if (g === 'lite') {
        console.info('[체크인] 프레임이 버거워서 움직임을 낮췄습니다 ' +
                     '(' + slow + '/' + n + ' 프레임 지연). ' +
                     'sessionStorage 의 ckMotion 을 지우면 다시 잽니다.');
      }
    }
    requestAnimationFrame(step);
  }

  var WX_KO = { clear: '맑음', cloudy: '흐림', rain: '비', snow: '눈' };

  /* 기상청 하늘 상태를 우리 네 가지로 줄인다.
     화면이 그릴 수 있는 것만 남긴다 — 「구름많음」과 「흐림」을
     따로 그려 봐야 눈으로 구분이 안 된다. */
  function wxKind(d) {
    if (!d) return 'clear';
    if (d.rainExpected) {
      var t = (d.tempMax != null ? d.tempMax : 10);
      return t <= 2 ? 'snow' : 'rain';
    }
    var s = String(d.sky || '');
    if (s.indexOf('눈') >= 0) return 'snow';
    if (s.indexOf('비') >= 0) return 'rain';
    if (s.indexOf('흐') >= 0 || s.indexOf('구름') >= 0) return 'cloudy';
    return 'clear';
  }

  function setWx(kind) {
    document.documentElement.setAttribute('data-wx', kind || 'clear');
  }

  /* 지금 시각·지금 날씨를 적는다. 배경이 왜 이 색인지 말해 주지 않으면
     그냥 색이 이상한 화면이 된다. */
  function tellNow(kind, temp) {
    var box = document.getElementById('st_wx');
    if (!box) return;
    var d = new Date();
    var hh = String(d.getHours()).padStart(2, '0');
    var mm = String(d.getMinutes()).padStart(2, '0');
    box.hidden = false;
    box.innerHTML =
      '<b>' + hh + ':' + mm + '</b>' +
      '<span>' + (WX_KO[kind] || '맑음') +
        (temp != null ? ' ' + Math.round(temp) + '°' : '') + '</span>' +
      '<i>지금 시각과 날씨로 배경색을 맞췄습니다</i>';
  }

  /* 오늘 이 지역 날씨. 못 받으면 맑음으로 두고 조용히 넘어간다 */
  async function liveWeather() {
    try {
      var d = new Date();
      var iso = d.getFullYear() + '-' +
                String(d.getMonth() + 1).padStart(2, '0') + '-' +
                String(d.getDate()).padStart(2, '0');
      var r = await fetch('/api/maps/weather/day?region=' +
                          encodeURIComponent(HERO_REGION) + '&date=' + iso);
      var j = await r.json();
      var w = (j && j.success && j.data) || null;
      return { kind: wxKind(w), temp: w && w.tempMax };
    } catch (e) {
      return { kind: 'clear', temp: null };
    }
  }

  /* 동선을 잇는 선. 핀과 같은 좌표계(%)를 쓰므로 정거장을 어디로
     옮겨도 끝점이 정확히 맞는다. 모형에 구워진 선은 CSS 로 숨겼다 —
     그건 모형을 만들 때의 지점을 이은 것이라 지금은 안 닿는다.

     두 겹이다. 바깥 흰 테가 있어야 도시 위에 올라가도 선이 안 묻힌다.
     지나온 구간은 채우고, 아직 안 간 구간은 점선으로 흐른다. */
  function drawLine(route, upto) {
    var layer = $('ck_pinlayer');
    if (!layer) return;
    var pts = route.filter(function (p) { return isFinite(p.x) && isFinite(p.y); });
    if (pts.length < 2) return;

    var ns = 'http://www.w3.org/2000/svg';
    var svg = layer.querySelector('.ck-draw');
    if (!svg) {
      svg = document.createElementNS(ns, 'svg');
      svg.setAttribute('class', 'ck-draw');
      svg.setAttribute('viewBox', '0 0 100 100');
      svg.setAttribute('preserveAspectRatio', 'none');
      layer.insertBefore(svg, layer.firstChild);
    }

    function pl(cls, from, to) {
      var seg = pts.slice(from, to + 1);
      if (seg.length < 2) return '';
      return '<polyline class="' + cls + '" points="' +
             seg.map(function (p) { return p.x.toFixed(2) + ',' + p.y.toFixed(2); })
                .join(' ') + '" />';
    }

    /* upto 가 없으면 전체를 「지나온 것」으로 그린다 */
    var n = pts.length - 1;
    var k = (upto == null) ? n : Math.max(0, Math.min(n, upto));

    svg.innerHTML =
      pl('ck-ln-case', 0, n) +
      pl('ck-ln-done', 0, k) +
      (k < n ? pl('ck-ln-todo', k, n) : '');
  }

  /* 들어오는 장면. 한 번만 본다 — 같은 연출을 매번 보면 기다리는 시간이 된다.

       0.0s  지도    세 곳과 선. 해운대에서 감천이 18km 라는 게 여기서 보인다
       1.9s  1번     해운대 모형. 그 곳 핀 하나만 남고 카메라가 멈춘다
       3.5s  2번     남포동 모형으로 컷
       5.1s  3번     감천 모형으로 컷
       6.7s  갈래1   길이 막혀서 — 지도로 컷, 선이 새 순서로 다시 그려진다
       9.2s  갈래2   사람이 몰려서 — 해운대에서 광안리 모형으로 컷
      11.7s  갈래3   비가 와서 — 비가 내리고 감천에서 실내로 컷
      14.2s  정리    전체로 물러나고 판이 들어온다

     갈래를 왜 이렇게 나눠 보여 주는가 —
       순서를 바꾸는 것은 지도에서만 보인다. 모형에서는 어느 쪽이 먼저인지
       안 보이니, 선이 다시 그려지는 지도로 넘긴다.
       장소를 바꾸는 것은 반대다. 모형이 통째로 바뀌는 게 제일 크게 보인다.

     끊어 가는 이유 — 미끄러지면 「코앞에서 조금 움직인」 것으로 보인다.
     끊으면 「다른 데로 갔다」로 읽힌다.

     단계는 전부 벽시계(setTimeout)로 넘어간다. requestAnimationFrame 은
     탭이 뒤에 있으면 멈춰서 화면이 중간에 굳는다 — 한 번 겪었다. */
  function intro(route, done) {
    var stage = document.querySelector('.ck-stage');
    var reduce2 = matchMedia('(prefers-reduced-motion: reduce)').matches;
    var seen = false;
    try { seen = sessionStorage.getItem('ckIntroSeen') === '1'; } catch (e) {}

    var S = window.ckStage;
    var alt = window.__ckAlt || null;

    if (reduce2 || seen || !stage || !S || route.length < 2) {
      allPins(route, alt);             /* 연출을 건너뛰어도 선은 있어야 한다 */
      if (S) S.camFit();
      done();
      return;
    }
    try { sessionStorage.setItem('ckIntroSeen', '1'); } catch (e) {}

    var host = $('ck_hero');
    var cap = $('ck_fix');
    var fixes = window.__ckFixes || [];
    var timers = [];
    var ended = false;

    function at(ms, fn) { timers.push(setTimeout(fn, ms)); }

    /* 화면을 한 번 덮었다 걷는다. 그 사이에 바꾸면 미끄러지지 않고
       「바뀌었다」로 읽힌다 */
    function cut(fn) {
      if (!host) { fn(); return; }
      host.classList.add('ck-cut');
      at(190, function () {
        fn();
        at(80, function () { host.classList.remove('ck-cut'); });
      });
    }

    /* 갈래 자막. 왜 · 어떻게 · 얼마를 화면 안에서 말한다.
       옆 판을 봐야 알면 모형이 데이터가 아니라 그림이 된다. */
    function say(f) {
      if (!cap || !f) return;
      cap.hidden = false;
      cap.innerHTML =
        '<b>' + esc(f.label) + '</b>' +
        '<span>' + esc(f.why) + '</span>' +
        '<span class="ck-fix-how">' + esc(f.how) + '</span>' +
        '<em>' + esc(f.gain) + '<i>' + esc(f.cost) + '</i></em>';
      cap.classList.remove('ck-fix-in');
      void cap.offsetWidth;                 /* 다시 처음부터 재생되게 */
      cap.classList.add('ck-fix-in');
    }

    /* 그 장소의 시각으로 하늘을 맞춘다. 장면마다 한 번만 — 매 프레임
       고치면 :root 변수 쓰기가 문서 전체 재계산을 부른다 */
    function skyAt(note) {
      if (!window.ckSky) return;
      var m = /(\d{1,2}):(\d{2})/.exec(note || '');
      if (!m) return;
      var when = new Date();
      when.setHours(+m[1], +m[2], 0, 0);
      skyHold = true;
      window.ckSky(when);
    }

    /* 한 장소로 들어간다. 모형을 갈아 끼우고, 그 핀 하나만 두고,
       카메라를 그 자리에 맞춘다 */
    function goPlace(p, no) {
      cut(function () {
        S.showModel();
        function aim() {
          soloPin(p, no);
          if (S.camAt) {
            S.camAt(p.sx != null ? p.sx : p.x,
                    p.sy != null ? p.sy : p.y, 1.5);
          }
        }
        if (p.scene && S.loadScene) S.loadScene(p.scene, aim);
        else aim();
        skyAt(p.note);
      });
    }

    function finish() {
      if (ended) return;
      ended = true;
      timers.forEach(clearTimeout);
      if (S.camFlyStop) S.camFlyStop();
      document.removeEventListener('visibilitychange', onHide);
      if (host) { host.classList.remove('ck-intro'); host.classList.remove('ck-cut'); }
      if (cap) { cap.hidden = true; cap.innerHTML = ''; }
      setWx((window.__ckWx && window.__ckWx.kind) || 'clear');
      skyHold = false;
      if (window.ckSky) window.ckSky();

      function rest() {
        S.showModel();
        allPins(route, alt);
        if (S.camLock) S.camLock(false);
        S.camFit();
        if (window.ckHeroHold) window.ckHeroHold(false);
        done();
      }
      if (route[0] && route[0].scene && S.loadScene) S.loadScene(route[0].scene, rest);
      else rest();
    }
    function onHide() { if (document.hidden) finish(); }
    document.addEventListener('visibilitychange', onHide);

    /* 건너뛰기 — 연출은 14초다. 기다릴 사람은 보고, 아닌 사람은 넘긴다 */
    window.ckSkipIntro = finish;
    var skipBtn = $('ck_skipin');
    if (skipBtn) skipBtn.addEventListener('click', finish, { once: true });
    /* 판을 누르는 것도 「그만 보고 쓰겠다」는 뜻이다. 연출을 끝내고
       그 클릭이 원래 하려던 일을 하게 둔다 — 막아 두면 화면은 멀쩡해
       보이는데 아무것도 안 눌리는 상태가 된다. */
    if (host) host.addEventListener('pointerdown', finish, { once: true });

    if (S.camLock) S.camLock(true);
    if (window.ckHeroHold) window.ckHeroHold(true);   /* 나레이션을 세운다 */
    if (host) host.classList.add('ck-intro');

    /* ① 지도 — 세 곳과 선 */
    if (!S.showWideMap(route)) { finish(); return; }

    var MAP = 1900, STOP = 1600, FIX = 2500;
    var T = MAP;

    /* 넘어가기 직전에 1번 쪽으로 당기고, 첫 모형을 미리 받아 둔다 */
    at(MAP - 520, function () {
      S.zoomMapTo(0);
      if (S.preloadScene && route[0]) S.preloadScene(route[0].scene);
    });

    /* ②③④ 장소마다 자기 모형으로 */
    route.forEach(function (p, i) {
      at(T + STOP * i, function () {
        var nx = route[i + 1];
        if (nx && nx.scene && S.preloadScene) S.preloadScene(nx.scene);
        goPlace(p, i + 1);
      });
    });
    T += STOP * route.length;

    /* ⑤ 세 갈래 — 순서대로. 누르지 않는다 */
    fixes.forEach(function (f, i) {
      at(T + FIX * i, function () {
        say(f);
        if (f.kind === 'reorder') {
          var re = (f.order || []).map(function (k) { return route[k]; })
                   .filter(Boolean);
          cut(function () { S.showWideMap(re); });
        } else if (f.to) {
          if (f.key === 'rain') setWx('rain');
          goPlace(f.to, f.at + 1);
        }
      });
    });
    T += FIX * Math.max(1, fixes.length);

    /* ⑥ 정리 */
    at(T, function () { cut(finish); });
    at(T + 1600, finish);           /* 아무리 늦어도 여기서는 끝낸다 */
  }

  /* 밖에서 부를 수 있게 — 「다시 보기」 버튼이 쓴다 */
  window.ckReplayIntro = function () {
    try { sessionStorage.removeItem('ckIntroSeen'); } catch (e) {}
    location.reload();
  };

  /* ─────────────── 2. 나레이션 ───────────────
     세 장면을 순서대로 읽으면 이 제품이 하는 일이 한 문장씩 나온다.
       ① 오늘 순서  ② 어디가 얼마나 혼잡하고 언제 풀리는지  ③ 무엇을 바꿀 수 있는지
     혼잡도 등급은 crowd.js 가 정한다 (매우 혼잡 · 혼잡 · 정상 · 한적 · 매우 한적).

     at  — 재생 바에서 지금 어디쯤인지 (0~1)
     now — 재생 바 오른쪽에 적는 지금 자리 */
  var SCENE = [
    { at: 0,  now: '미포',
      line: '부산 첫날 오후 일정입니다',
      why: '미포에서 출발해 해수욕장, 동백섬 순으로 갈 계획이었습니다.',
      hot: -1, live: 1, opts: 0 },
    { at: .5, now: '해운대 해수욕장',
      line: '해운대 해수욕장이 지금 <em>매우 혼잡</em>합니다',
      why: '평소 이 시각의 1.4배입니다. 17시 이후에는 지금보다 한산할 것으로 봅니다.',
      hot: 1, live: 1, opts: 0 },
    { at: 1,  now: '동백섬',
      line: '바꾸는 방법은 <em>세 가지</em>입니다',
      why: '하나씩 보여 드립니다. 누르면 그 상태로 멈춥니다.',
      hot: 1, live: 1, opts: 1 }
  ];

  /* 갈래는 mass_hero.json 의 fixes 가 갖는다. 문구와 바꿀 내용이
     한 곳에 있어야 화면과 데이터가 어긋나지 않는다 — 전에는 버튼 문구는
     마크업에, 바뀌는 동작은 이 파일에 있어서 한쪽만 고쳐지면 어긋났다.

       reorder  순서를 바꾼다. 핀 번호와 오른쪽 판의 시각이 같이 바뀐다
       swap     장소를 바꾼다. 그 핀이 대체지로 옮겨 가고 혼잡도가 바뀐다

     셋 다 화면에서 결과가 달라야 고른 값이 있다. */
  function fixList() { return window.__ckFixes || []; }

  function initHero() {
    var tl = $('ck_tl'), tw = $('ck_tw'), hero = $('ck_hero'), box = $('ck_opts');
    if (!tl) return;

    var bars = ['ck_p1', 'ck_p2', 'ck_p3'].map($).filter(Boolean);
    var opts = box ? [].slice.call(box.querySelectorAll('.ck-opt')) : [];
    var route = [], alt = null;
    var step = -1, timer = null, pick = 0, paused = false;
    /* hold — 들어오는 장면이 도는 동안. paused 와 다르다.
       paused 는 사람이 멈춘 것이고 hold 는 연출이 쥐고 있는 것이다.
       섞으면 연출이 끝난 뒤에 멈춰 둔 것이 저절로 돌아간다. */
    var hold = false;
    var btn = $('ck_playbtn'), play = $('ck_play');

    /* 고른 갈래를 모형과 판에 반영한다. 장면 3에서만 부른다. */
    function applyOpt(n) {
      var fx = fixList(), f = fx[n];
      pick = n;
      opts.forEach(function (b, k) {
        b.setAttribute('aria-pressed', k === n ? 'true' : 'false');
      });
      if (!f || !route.length) return;
      var layer = $('ck_pinlayer');
      var base = (window.__ckWx && window.__ckWx.kind) || 'clear';

      if (f.kind === 'reorder') {
        /* 길이 막혀서 — 가는 곳은 그대로고 순서만 바뀐다 */
        setWx(base);
        var ord = f.order || [0, 1, 2];
        var re = ord.map(function (k) { return route[k]; }).filter(Boolean);
        allPins(route, alt);
        if (layer) {
          layer.classList.remove('ck-cand-on');
          var pins = layer.querySelectorAll('.ck-pin:not(.ck-cand)');
          for (var i = 0; i < pins.length; i++) {
            var no = pins[i].querySelector('.ck-no');
            var slot = ord.indexOf(i);
            if (no && slot >= 0) no.textContent = slot + 1;
            /* 자리가 바뀐 핀만 표시한다. 무엇이 움직였는지가 요점이다 */
            if (slot >= 0 && slot !== i) pins[i].classList.add('ck-moved');
          }
        }
        drawLine(re);
        if (window.ckStage && window.ckStage.bigDraw) window.ckStage.bigDraw(re);
        stage({ order: ord, hot: 0, cand: false });

      } else if (f.to) {
        /* 사람이 몰려서 · 비가 와서 — 그 자리를 다른 곳으로 */
        setWx(f.key === 'rain' ? 'rain' : base);
        var mix = route.slice();
        mix[f.at] = f.to;
        allPins(mix, null);
        if (layer) {
          var all = layer.querySelectorAll('.ck-pin');
          /* 바뀐 핀을 표시하고, 원래 있던 곳은 흐린 핀으로 남긴다 —
             「이거 대신 이거」가 한 화면에 같이 있어야 바뀐 게 보인다 */
          if (all[f.at]) all[f.at].classList.add('ck-new');
          var was = route[f.at];
          layer.insertAdjacentHTML('beforeend',
            pinHTML({ name: was.name, x: was.x, y: was.y, no: f.at + 1 },
                    route.length, ' ck-was'));
        }
        drawLine(mix);
        if (window.ckStage && window.ckStage.bigDraw) window.ckStage.bigDraw(mix);
        stage({ order: [0, 1, 2], hot: f.at, cand: false });
      }
    }

    /* 갈래 버튼을 데이터로 만든다. 없으면 절을 감춘다 —
       고를 수 없는 버튼을 세 개 두는 것보다 없는 편이 낫다 */
    function drawOpts() {
      if (!box) return;
      var fx = fixList();
      box.innerHTML = fx.map(function (f, i) {
        return '<button type="button" class="ck-opt" data-o="' + i + '" ' +
               'aria-pressed="' + (i === pick ? 'true' : 'false') + '">' +
               '<b>' + esc(f.label) + '</b>' +
               '<span>' + esc(f.why) + '</span>' +
               '<em>' + esc(f.gain) + '<i>' + esc(f.cost) + '</i></em>' +
               '</button>';
      }).join('');
      opts = [].slice.call(box.querySelectorAll('.ck-opt'));
      opts.forEach(function (b, k) {
        b.addEventListener('click', function () {
          /* 고르는 순간 장면이 넘어가 있으면 문구와 고른 것이 어긋난다.
             고를 수 있는 장면으로 고정하고 재생을 멈춘다. */
          clearTimeout(timer); timer = null;
          paused = true;
          pick = k; step = SCENE.length - 1; draw(step);
          if (play) play.classList.add('ck-paused');
          if (btn) btn.setAttribute('aria-label', '순서 따라가기 시작');
          applyOpt(k);
        });
      });
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
      if (box) {
        if (s.opts && !opts.length) drawOpts();
        box.hidden = !s.opts || !opts.length;
      }
      if (route.length) {
        drawPins(route, s.hot, alt);
        if (s.opts) {
          applyOpt(pick);
        } else {
          resetScene();
          /* 「비가 와서」 갈래를 보여 준 뒤 이 장면으로 넘어오면 비가
             계속 내리고 있었다. 갈래를 접었으면 날씨도 지금 날씨로
             돌려놓는다 — 화면이 자기 데이터와 어긋나는 자리였다. */
          setWx((window.__ckWx && window.__ckWx.kind) || 'clear');
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
        pins[i].classList.remove('ck-moved');
        pins[i].classList.remove('ck-new');
      }
      /* 대체지를 켜 둔 흐린 핀도 걷는다 */
      var was = layer.querySelectorAll('.ck-was');
      for (var k = 0; k < was.length; k++) was[k].remove();
    }

    /* 갈래는 저절로 넘어간다. 「사용자가 클릭하는게 아닌 순서대로 쭉」 —
       세 가지를 다 보여 준 뒤에 처음 장면으로 돌아간다. */
    function tick() {
      if (hold) return;
      var last = SCENE.length - 1;
      if (step === last && pick < fixList().length - 1) {
        pick++;
        applyOpt(pick);
        timer = setTimeout(tick, 2600);
        return;
      }
      step = (step + 1) % SCENE.length;
      if (step === 0) pick = 0;          /* 한 바퀴 돌면 처음 갈래로 */
      draw(step);
      timer = setTimeout(tick, 2600);
    }

    /* 들어오는 장면이 도는 동안은 나레이션을 세운다. 안 세우면 장면이
       넘어갈 때마다 핀 번호와 오른쪽 판을 다시 써서 연출과 부딪힌다. */
    window.ckHeroHold = function (on) {
      hold = !!on;
      if (hold) { clearTimeout(timer); timer = null; }
      else if (!paused && !timer) tick();
    };

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
          setCrowdDensity();
        })
        .catch(function () { /* 못 받으면 json 값을 그대로 쓴다 */ });
    }

    /* 모형의 사람 수를 지금 보는 곳의 혼잡도에 맞춘다.
       해변 군중은 해수욕장 값을 따른다 — 그 자리에 있는 사람들이다. */
    function setCrowdDensity() {
      var beach = null;
      route.forEach(function (p) {
        if (p.crowd == null) return;
        if (!beach || p.crowd > beach.crowd) beach = p;
      });
      if (!beach) return;
      var x = Math.max(0, Math.min(1, beach.crowd / 100));
      /* 장면 상자에만 넣는다. 안쪽 무리·파라솔은 CSS 가 이 값을 물려받아
         각자의 문턱으로 판단한다 — SVG 가 늦게 들어와도 따라온다. */
      var scene = document.getElementById('ck_scene') || document.getElementById('ck_frame');
      if (scene) scene.style.setProperty('--cw', x.toFixed(3));
      window.__ckCw = x;
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
      /* 등급은 윗줄에 있다. 여기서는 숫자만 */
      SCENE[1].why = '집중률 ' + worst.crowd + (busy ? '' : ' · 순서는 그대로 둬도 됩니다');

      /* 대체 후보의 등급도 받은 값으로 */
      if (alt && alt.crowd != null) {
        var ag = (window.crowd && window.crowd(alt.crowd)) || null;
        var cell = document.querySelector('.ck-opt[data-o="1"] em');
        if (cell) {
          cell.innerHTML = esc(alt.crowdLabel || (ag && ag.label) || '') +
                           '<i>집중률 ' + alt.crowd + '</i>';
        }
        /* 문구는 마크업에 적어 둔 것을 쓴다.
           「원래 광안리였어요. 8.4km, 차로 52분」처럼 왜 옮기는지를
           말해야 하는데, 여기서 이름만 갈아 끼우면 그 이유가 사라진다.
           전에는 「해수욕장 대신 동백섬으로」로 덮어써서, 거리 이야기가
           화면에서 없어졌다. */
      }
    }

    fetch(HERO_JSON, { cache: 'no-cache' })
      .then(function (r) { return r.ok ? r.json() : Promise.reject(r.status); })
      .then(function (j) {
        route = (j && j.route) || []; alt = (j && j.alt) || null;
        window.__ckRoute = route;        /* 들어오는 장면이 이걸 쓴다 */
        window.__ckScenes = (j && j.scenes) || {};   /* 장소별 모형 */
        window.__ckFixes = (j && j.fixes) || [];     /* 세 갈래 */
        window.__ckAlt = alt;
      })
      .catch(function () { route = []; window.__ckRoute = []; })
      .then(liveCrowd)
      .then(function () {
        stage({ order: [0, 1, 2], hot: -1, cand: false });
        if (reduce) { draw(SCENE.length - 1); return; }
        tick();
        if (hero && window.IntersectionObserver) {
          new IntersectionObserver(function (es) {
            es.forEach(function (e) {
              if (e.isIntersecting) { if (!timer && !paused && !hold) tick(); }
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
                             DOW[when.getDay()] + '요일';
        }
        var hd = sec && sec.querySelector('.ck-hd');
        if (hd) hd.setAttribute('data-label', '한국관광공사 집중률 예측');

        /* 구가 전부 같으면 카드마다 같은 줄이 붙는다. 다를 때만 적는다 */
        var shown = quiet.concat(busy && busy.rate >= 70 ? [busy] : []);
        var oneGu = shown.every(function (x) { return x.sigunguName === shown[0].sigunguName; });

        function card(x, hot) {
          var g = (window.crowd && window.crowd(x.rate)) || { key: 'mid', label: '정상' };
          var k = 'cw-' + g.key;
          return '<article class="ck-spot' + (hot ? ' ck-hot' : '') + '">' +
            '<div class="ck-nm">' + esc(x.placeName) + '</div>' +
            (!oneGu && x.sigunguName ? '<div class="ck-why">' + esc(x.sigunguName) + '</div>' : '') +
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
        nt.textContent = picked.length ? picked.join(' · ') + ' 맞춤' : '';
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

  /* 혼잡도 눈금 — 접어 둔다. 늘 펼쳐 두면 화면 절반이 설명이 된다 */
  function initCwKey() {
    var b = $('st_cwkey_t'), body = $('st_cwkey_b');
    if (!b || !body) return;
    b.addEventListener('click', function () {
      var on = b.getAttribute('aria-expanded') === 'true';
      b.setAttribute('aria-expanded', String(!on));
      body.hidden = on;
    });
  }

  var skyHold = false;        /* 인트로가 하늘을 쥐고 있는 동안 참 */

  function initSky() {
    if (typeof window.ckSky !== 'function') return;
    var timer = null;
    function loop() {
      if (!skyHold) window.ckSky();
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
    initCwKey();
    /* 날씨는 먼저 걸어 둔다 — 인트로가 끝난 뒤에 지금 날씨로 남는다 */
    liveWeather().then(function (w) {
      setWx(w.kind);
      window.__ckWx = w;
    });

    /* 모형이 들어온 뒤에 동선을 그린다. 판보다 모형이 먼저 있어야
       선이 어디를 지나는지가 보인다. */
    function afterScene() {
      injectScene().then(function () {
        var tries = 0;
        (function wait() {
          var r = window.__ckRoute;
          /* 핀이 아직 안 선 상태면 그릴 대상이 없다. 20번(약 2초)까지 기다린다 */
          var pinned = document.querySelectorAll('#ck_pinlayer .ck-pin').length;
          if ((!r || !r.length || !pinned) && tries++ < 20) {
            setTimeout(wait, 100);
            return;
          }
          intro(r || [], function () {
            var w = window.__ckWx || { kind: 'clear', temp: null };
            tellNow(w.kind, w.temp);
            /* 인트로가 끝난 뒤에 잰다. 인트로 중에 재면 인트로를 보고
               판단해 버린다 — 그건 한 번만 도는 것이다 */
            setTimeout(gradeMotion, 400);
          });
        })();
      });
    }
    if ('requestIdleCallback' in window) requestIdleCallback(afterScene, { timeout: 1200 });
    else setTimeout(afterScene, 200);
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
