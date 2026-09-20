/* 메인: 실제 위치 지도, 한 장소 모형, 세 가지 변경 장면. */
(function () {
  'use strict';
  var $ = function (id) { return document.getElementById(id); };
  var reduce = matchMedia('(prefers-reduced-motion: reduce)').matches;
  var HERO_REGION = '부산 해운대구';
  var DOW = ['일','월','화','수','목','금','토'];
  function heroDate() {
    var d = new Date(); d.setHours(0,0,0,0); d.setDate(d.getDate() + (6-d.getDay()+7)%7); return d;
  }
  function iso(d) { return d.getFullYear()+'-'+String(d.getMonth()+1).padStart(2,'0')+'-'+String(d.getDate()).padStart(2,'0'); }
  function soloPin(p, no) {
    var layer = $('ck_pinlayer');
    if (!layer) return;
    if (!p.scene || !isFinite(p.sx) || !isFinite(p.sy)) { layer.innerHTML = ''; return; }
    layer.innerHTML = '<div class="ck-pin ck-solo ck-new" style="left:'+p.sx+'%;top:'+p.sy+'%">'+
      '<b class="ck-no">'+no+'</b><span class="ck-bub"><strong>'+esc(p.name)+'</strong>'+
      '<em>'+esc(p.note || '')+'</em>'+(p.crowd == null ? '' : '<span class="ck-cw">혼잡도 <b>'+Math.round(p.crowd)+
      '</b><small>'+(p.crowdLive ? '예측' : '예시')+'</small></span>')+'</span></div>';
  }
  window.ckSoloPin = soloPin;

  /* 호출 한 번마다 제한 시간을 둔다. 느린 API가 여행 입력을 막지 않는다. */
  async function readJSON(url) {
    var controller = new AbortController(), timeout = setTimeout(function () { controller.abort(); },6500);
    try { var r = await fetch(url,{signal:controller.signal,cache:'no-cache'}); if (!r.ok) throw Error('load'); return await r.json(); }
    finally { clearTimeout(timeout); }
  }

  async function initHero() {
    var hero = $('ck_hero'), cap = $('ck_fix'), S = window.ckStage;
    if (!hero || !S) return;
    var route = [], fixes = [], alt, epoch = 0, timer, resolveWait, playing = false, introducing = false;
    var current = -1, touched = false;
    /* 기본 순서를 [0,1,2] 로 박아 두었더니 동선을 네 곳으로 늘리자
       마지막 한 곳이 안 그려졌다. 들어온 만큼 센다. */
    function render(r,order,hot) {
      S.render(r,alt,{order:order || (r || []).map(function (_,i) { return i; }),hot:hot || 0});
    }
    function setPlay(on) {
      playing = on;
      $('ck_play').classList.toggle('ck-paused',!on);
      $('ck_playbtn').setAttribute('aria-label',on ? '자동 재생 멈춤' : '세 가지 변경 자동 재생');
    }
    function stop() {
      epoch++; clearTimeout(timer);
      if (resolveWait) { resolveWait(); resolveWait = null; }
      S.cancel(); setPlay(false);
    }
    function wait(ms) { return new Promise(function (resolve) { resolveWait = resolve; timer = setTimeout(function () { resolveWait = null; resolve(); },ms); }); }
    function restoreWeather() {
      skyHold = false; if (window.ckSky) window.ckSky();
      var weather = window.__ckWx || {kind:'clear',temp:null}; setWx(weather.kind); tellNow(weather.kind,weather.temp);
    }
    function caption(title, body) { $('ck_tl').textContent = title; $('ck_tw').textContent = body; }
    function mark(n) {
      current = n;
      ['ck_p1','ck_p2','ck_p3'].forEach(function (id,i) { $(id).className = i < n ? 'ck-done' : i === n ? 'ck-on' : ''; });
      $('ck_opts').querySelectorAll('button').forEach(function (b,i) { b.setAttribute('aria-pressed',String(i === n)); });
    }
    var symbols = {
      traffic:'<path d="M5 21V3m14 18V3M12 3v4m0 10v4M8 11h8v4H8z"/>',
      crowd:'<circle cx="12" cy="6" r="2"/><circle cx="5" cy="9" r="2"/><circle cx="19" cy="9" r="2"/><path d="M8 21v-7a4 4 0 0 1 8 0v7M2 21v-6m20 6v-6"/>',
      rain:'<path d="M6 14a4 4 0 1 1 1-8 5 5 0 0 1 10 0 4 4 0 0 1 1 8H6m1 3-2 4m8-4-2 4m8-4-2 4"/>'
    };
    function say(f,n) {
      mark(n);
      var from = f.kind === 'reorder' ? route.map(function (p) { return p.name; }).join(' → ') : route[f.at].name;
      var to = f.kind === 'reorder' ? f.order.map(function (i) { return route[i].name; }).join(' → ') : f.to.name;
      var metric = f.gain, source = '상황을 가정한 변경 예시';
      if (f.key === 'crowd') {
        var a = route[f.at], b = f.to;
        metric = (a.crowd == null ? '—' : a.crowd) + ' → ' + (b.crowd == null ? '—' : b.crowd);
        source = a.crowdLive && b.crowdLive ? '한국관광공사 집중률 예측 · 붐빌 때의 변경 예시' : '혼잡도 예시 · 실제 상황이 아닙니다';
      }
      caption(f.label, f.why);
      cap.hidden = false;
      cap.innerHTML = '<div class="ck-fix-heading"><span class="ck-fix-step">'+(n+1)+' / '+fixes.length+'</span>'+
        '<svg class="ck-reason" viewBox="0 0 24 24" aria-hidden="true">'+symbols[f.key]+'</svg><h3>'+esc(f.label)+'</h3></div>'+
        '<div class="ck-comparison"><div class="ck-was"><small>원래 계획</small><b>'+esc(from)+'</b></div>'+
        '<svg class="ck-change-arrow" viewBox="0 0 32 24" aria-label="변경"><path d="M2 12h26m-8-8 8 8-8 8"/></svg>'+
        '<div class="ck-new"><small>바꾼 계획</small><b>'+esc(to)+'</b></div></div>'+
        '<div class="ck-result"><strong>'+esc(metric)+'</strong><span>'+esc(f.how)+'</span></div>'+
        '<p class="ck-fix-source">'+esc(source)+'</p>';
      cap.classList.remove('ck-fix-in'); void cap.offsetWidth; cap.classList.add('ck-fix-in');
    }
    async function applyOpt(n) {
      var f = fixes[n]; if (!f) return;
      say(f,n); setWx(f.key === 'rain' ? 'rain' : (window.__ckWx || {}).kind, f.key === 'rain');
      if (f.kind === 'reorder') {
        render(route,f.order,0); await S.showWideMap(f.order.map(function (i) { return route[i]; }));
      } else {
        var mix = route.slice(); mix[f.at] = f.to; render(mix,null,f.at);
        await S.showPlace(f.to,f.at+1);
      }
    }
    function rest() {
      introducing = false; hero.classList.remove('ck-intro'); restoreWeather();
      render(route,null,0); S.showPlace(route[0],1);
      caption('부산, 오늘은 이렇게 둘러볼까요', '장소를 누르면 동네 모형으로 이동합니다. 아래에서 바꾸는 방법도 살펴보세요.');
      mark(-1);
      cap.innerHTML = '<p class="ck-opening">지금은 원래 동선입니다.<br><strong>길·혼잡·날씨 중 하나를 골라<br>어떻게 바뀌는지 보세요.</strong></p>';
    }
    function skip() {
      if (!introducing) return;
      stop(); introducing = false; hero.classList.remove('ck-intro'); restoreWeather();
      render(route,null,0); S.showPlace(route[0],1);
      caption('부산의 하루를 둘러보세요','장소를 누르면 그 동네 모형을 볼 수 있습니다.');
    }
    async function cycle() {
      stop(); setPlay(true); var ticket = epoch;
      for (var i=0;i<fixes.length;i++) {
        if (ticket !== epoch) return;
        await applyOpt(i); if (ticket !== epoch) return;
        await wait(4400);
      }
      if (ticket === epoch) { setPlay(false); rest(); }
    }
    async function intro() {
      stop(); introducing = true; setPlay(true); var ticket = epoch;
      hero.classList.add('ck-intro'); mark(-1);
      try { sessionStorage.setItem('ckIntroSeen','1'); } catch (e) {}
      caption('먼저, 하루 동선을 한눈에','해운대에서 남포동, 감천문화마을로 이어집니다.');
      cap.innerHTML = '<p class="ck-opening">지도에서 하루를 보고,<br><strong>도시 안으로 들어갑니다.</strong></p>';
      render(route,null,0);
      S.preloadScene(route[0].scene);
      await S.showWideMap(route); if (ticket !== epoch) return;
      await wait(2400); if (ticket !== epoch) return;
      for (var i=0;i<route.length;i++) {
        if (ticket !== epoch) return;
        var p=route[i]; if (route[i+1]) S.preloadScene(route[i+1].scene);
        caption((i+1)+'번째, '+p.name, p.cat+' · '+p.note+' 방문 예시');
        render(route,null,i); await S.showPlace(p,i+1); if (ticket !== epoch) return;
        if (window.ckSky) { var when = new Date(), parts=p.note.split(':'); when.setHours(+parts[0],+parts[1],0,0); skyHold=true; window.ckSky(when); }
        await wait(1900);
      }
      for (var n=0;n<fixes.length;n++) {
        if (ticket !== epoch) return;
        if (fixes[n+1] && fixes[n+1].to) S.preloadScene(fixes[n+1].to.scene);
        await applyOpt(n); if (ticket !== epoch) return;
        await wait(4600);
      }
      if (ticket === epoch) { setPlay(false); rest(); }
    }
    window.ckHeroPause = function () { touched=true; stop(); if (introducing) { introducing=false; hero.classList.remove('ck-intro'); restoreWeather(); } };
    window.ckSkipIntro = skip;
    window.ckReplayIntro = function () { if (!reduce && route.length) intro(); };
    $('ck_skipin').addEventListener('click',skip);
    $('ck_replay').addEventListener('click',window.ckReplayIntro);
    $('ck_playbtn').addEventListener('click',function () { if (playing) { skip(); stop(); } else if (!reduce) cycle(); });
    hero.querySelector('.st-viewport').addEventListener('pointerdown',function (e) { if (!e.target.closest('button')) skip(); });
    hero.querySelector('.st-form').addEventListener('focusin',function () { touched=true; skip(); });
    document.addEventListener('visibilitychange',function () { if (document.hidden) { skip(); stop(); restoreWeather(); } });
    if (window.IntersectionObserver) new IntersectionObserver(function (entries) {
      if (!entries[0].isIntersecting && playing) { skip(); stop(); restoreWeather(); }
    },{threshold:0}).observe(hero);

    async function liveCrowd() {
      var places = route.concat(alt ? [alt] : [],fixes.filter(function (f) { return f.to; }).map(function (f) { return f.to; }));
      var names = Array.from(new Set(places.map(function (p) { return p.name; })));
      try {
        var j = await readJSON('/api/crowd/day?region='+encodeURIComponent(HERO_REGION)+'&date='+iso(heroDate())+'&places='+encodeURIComponent(names.join(',')));
        var data = j && j.success && Array.isArray(j.data) ? j.data : [];
        data.forEach(function (v) {
          if (v.rate == null || !isFinite(Number(v.rate))) return;
          places.forEach(function (p) { if (p.name === v.placeName) { p.crowd=Math.round(Number(v.rate)); p.crowdLabel=v.levelLabel; p.crowdLive=true; } });
        });
      } catch (e) {}
    }
    try {
      var j = await readJSON('/img/mass_hero.json');
      route = j.route || []; fixes = j.fixes || []; alt = j.alt;
      if (!route.length || !fixes.length) throw Error('route');
      window.__ckRoute = route; window.__ckFixes=fixes; window.__ckScenes=j.scenes || {}; window.__ckAlt=alt;
      $('ck_opts').hidden=false;
      $('ck_opts').innerHTML=fixes.map(function (f,i) { return '<button class="ck-opt" type="button" data-o="'+i+'" aria-pressed="false"><b>'+(i+1)+'</b><span>'+esc(f.label)+'</span></button>'; }).join('');
      $('ck_opts').addEventListener('click',function (e) {
        var b=e.target.closest('[data-o]'); if (!b) return;
        window.ckHeroPause(); applyOpt(Number(b.dataset.o));
      });
      var day=heroDate(); $('st_when').textContent=(day.getMonth()+1)+'월 '+day.getDate()+'일 '+DOW[day.getDay()]+'요일 · 방문 시각은 예시';
      render(route,null,0);
      await liveCrowd();
      var seen=false; try { seen=sessionStorage.getItem('ckIntroSeen')==='1'; } catch (e) {}
      if (reduce || seen || touched || document.hidden) { setPlay(false); rest(); }
      else intro();
      if (reduce) { $('ck_replay').textContent='동작 줄이기 사용 중'; $('ck_replay').disabled=true; $('ck_playbtn').disabled=true; }
      setTimeout(gradeMotion,400);
    } catch (e) {
      caption('동선을 불러오지 못했습니다','여행 조건을 입력해 경로를 직접 만들 수 있습니다.');
      cap.textContent='잠시 뒤 새로고침하면 동선을 다시 불러옵니다.'; setPlay(false);
    }
  }
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

  var rainEnd;
  /* 메인 화면의 비·눈 연출은 「비가 와서」 갈래를 보여 줄 때만 켠다.
     시간대에 따라 밝아지고 어두워지는 것([data-sky])은 그대로 둔다 —
     그건 지금 몇 시인지를 말해 주는 것이라 늘 맞다.
     반면 실제로 비가 온다고 첫 화면부터 빗줄기를 내리면, 세 갈래 중
     세 번째에서 「비가 와서 실내로」를 보여 줄 때 달라지는 것이 없어진다.

     force 를 준 호출만 비·눈을 켤 수 있다. 그 밖에는 흐림까지만 간다.
     동선을 만들 때 쓰는 실제 날씨는 서버가 따로 본다. 여기와 무관하다. */
  function setWx(kind, force) {
    if (!force && (kind === 'rain' || kind === 'snow')) kind = 'cloudy';
    return _setWxRaw(kind);
  }

  function _setWxRaw(kind) {
    kind = kind || 'clear';
    document.documentElement.setAttribute('data-wx', kind);
    var layer = $('ck_wx'); if (!layer) return;
    clearTimeout(rainEnd);
    var leaving = layer.classList.contains('is-raining') && kind !== 'rain';
    layer.classList.toggle('is-raining',kind === 'rain');
    layer.classList.toggle('is-cloudy',kind === 'cloudy');
    layer.classList.toggle('is-snow',kind === 'snow');
    layer.classList.toggle('is-clearing',leaving);
    if (leaving) rainEnd=setTimeout(function () { layer.classList.remove('is-clearing'); },1200);
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
        if (!quiet.length) { box.innerHTML='<p class="ck-empty">지금은 한적한 곳의 예측값이 없습니다.</p>'; return; }

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
      .catch(function () { box.innerHTML='<p class="ck-empty">혼잡도 예측을 가져오지 못했습니다. 잠시 뒤 다시 확인해 주세요.</p>'; });
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
    initNav(); if (!$('ck_hero')) return;
    initForm(); initMine(); initQuiet(); initSky(); initCwKey(); initHero();
    liveWeather().then(function (w) {
      window.__ckWx=w;
      if (!$('ck_hero').classList.contains('ck-intro')) setWx(w.kind);
      tellNow(w.kind,w.temp);
    });
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded',start); else start();
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
