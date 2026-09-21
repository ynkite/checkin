/* 주행 안내 — 화면 전체를 쓰는 내비게이션.
 *
 * page_live 의 「길 안내」가 window.startDrive({lat, lng, name}) 로 연다.
 *
 * 무엇을 보고 만들었나 —
 *   티맵·네이버 지도 내비게이션. 운전자가 흘깃 보고 읽어야 하는 화면이라
 *   맨 위에 「다음에 무엇을 하나」가 제일 크고, 그 아래 차선, 가운데 지도,
 *   맨 아래 남은 것. 안전 안내(어린이보호구역·과속방지턱·단속카메라)는
 *   지도 위에 띄우고 소리로도 읽는다.
 *
 * 운전 중에는 단추를 못 누른다 —
 *   시속 10km 를 넘으면 body 에 dv-driving 이 붙는다. 그때는 작은 단추들이
 *   접히고 「말로 하기」 하나만 화면 아래를 넓게 차지한다. 조준하지 않고
 *   눌리게 하려는 것이다. 안내 종료는 그때 길게 눌러야 먹는다 —
 *   운전 중 스쳐서 꺼지면 안 된다.
 *
 * 뒤로 —
 *   열 때 히스토리를 한 칸 쌓는다. 브라우저 뒤로가기로도 닫힌다.
 *
 * 제한속도·안전 지점은 서버(/api/route/navi)가 주는 만큼만 보여 준다.
 *   없으면 그 자리를 비운다. 지어내지 않는다.
 *
 * 자체검증: 콘솔에서 _navCheck()
 */
(function () {
  'use strict';

  var OFF_METERS = 50;              // 경로선에서 이 이상 벗어나면
  var OFF_STREAK = 3;               // 이만큼 연속이면 이탈 (GPS 튐 방지)
  var SPEAK_AT = [800, 300, 100];   // 갈림길까지 이 거리에서 한 번씩 읽는다
  var ALERT_SPEAK_AT = [300, 100];  // 안전 지점은 이 거리에서
  var ALERT_SHOW = 700;             // 이 거리 안에 들면 화면에 띄운다
  var ARRIVE_M = 35;                // 남은 거리가 이 아래면 도착
  var DRIVE_KMH = 10;               // 이 속도를 넘으면 주행 중 (조작 접기)
  var OVER_KMH = 7;                 // 제한속도를 이만큼 넘으면 알린다

  var st = null;                    // 주행 상태

  /* ══ 순수 기하 — 자체검증 대상 ══════════════════════════ */
  function haversine(aLat, aLng, bLat, bLng) {
    var R = 6371000, toR = Math.PI / 180;
    var dLat = (bLat - aLat) * toR, dLng = (bLng - aLng) * toR;
    var la1 = aLat * toR, la2 = bLat * toR;
    var h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
          + Math.cos(la1) * Math.cos(la2) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
    return 2 * R * Math.asin(Math.min(1, Math.sqrt(h)));
  }
  /* 점을 선분에 내린다. 등거리 근사 — 도 단위를 m 로 환산해서 평면으로 푼다.
     돌려주는 along 은 선분 시작점부터 내린 발까지의 거리(m)다.
     이게 있어야 「경로의 몇 미터 지점에 있나」를 알고, 그걸 알아야
     앞에 나올 과속방지턱까지 몇 미터인지 셀 수 있다. */
  function projSeg(pLat, pLng, aLat, aLng, bLat, bLng) {
    var toR = Math.PI / 180, latM = 111320, lngM = 111320 * Math.cos(pLat * toR);
    var px = pLng * lngM, py = pLat * latM;
    var ax = aLng * lngM, ay = aLat * latM;
    var bx = bLng * lngM, by = bLat * latM;
    var dx = bx - ax, dy = by - ay, len2 = dx * dx + dy * dy;
    var t = len2 === 0 ? 0 : ((px - ax) * dx + (py - ay) * dy) / len2;
    t = Math.max(0, Math.min(1, t));
    var cx = ax + t * dx, cy = ay + t * dy;
    return { dist: Math.hypot(px - cx, py - cy), along: t * Math.sqrt(len2) };
  }
  function distToSeg(pLat, pLng, aLat, aLng, bLat, bLng) {
    return projSeg(pLat, pLng, aLat, aLng, bLat, bLng).dist;
  }
  function distToLine(pLat, pLng, line) {
    if (!line || line.length === 0) return Infinity;
    if (line.length === 1) return haversine(pLat, pLng, line[0][0], line[0][1]);
    var min = Infinity;
    for (var i = 0; i < line.length - 1; i++) {
      var d = distToSeg(pLat, pLng, line[i][0], line[i][1], line[i + 1][0], line[i + 1][1]);
      if (d < min) min = d;
    }
    return min;
  }
  /* 경로선의 각 점까지의 누적 거리 */
  function cumulative(line) {
    var acc = [0];
    for (var i = 1; i < line.length; i++) {
      acc.push(acc[i - 1] + haversine(line[i - 1][0], line[i - 1][1], line[i][0], line[i][1]));
    }
    return acc;
  }
  /* 지금 자리가 경로의 몇 미터 지점인가 */
  function snap(pLat, pLng, line, acc) {
    if (!line || line.length < 2) return { dist: Infinity, meter: 0, seg: 0 };
    var best = { dist: Infinity, meter: 0, seg: 0 };
    for (var i = 0; i < line.length - 1; i++) {
      var r = projSeg(pLat, pLng, line[i][0], line[i][1], line[i + 1][0], line[i + 1][1]);
      if (r.dist < best.dist) best = { dist: r.dist, meter: acc[i] + r.along, seg: i };
    }
    return best;
  }
  function bearing(aLat, aLng, bLat, bLng) {
    var toR = Math.PI / 180;
    var y = Math.sin((bLng - aLng) * toR) * Math.cos(bLat * toR);
    var x = Math.cos(aLat * toR) * Math.sin(bLat * toR)
          - Math.sin(aLat * toR) * Math.cos(bLat * toR) * Math.cos((bLng - aLng) * toR);
    return (Math.atan2(y, x) * 180 / Math.PI + 360) % 360;
  }
  function offStreak(dist, prevStreak) {
    var s = dist > OFF_METERS ? prevStreak + 1 : 0;
    return { streak: s, off: s >= OFF_STREAK };
  }
  function kmh(speedMs) { return speedMs == null || speedMs < 0 ? 0 : speedMs * 3.6; }
  function isDriving(speedMs) { return kmh(speedMs) >= DRIVE_KMH; }

  /* ══ 갈림길 종류 ════════════════════════════════════════
     티맵 turnType 코드표를 들고 다니느니 안내 문구로 가른다 —
     실제 응답이 「2시 방향 우회전 후 해운대해변로298번길을 따라 199m 이동」
     처럼 오기 때문에 말이 코드보다 정확하다. 코드는 거들기만 한다. */
  function turnKind(desc, turnType) {
    var t = String(desc || '');
    if (turnType === 200 || /^출발/.test(t)) return 'depart';
    if (turnType === 201 || /도착|목적지/.test(t)) return 'arrive';
    if (/유턴/.test(t)) return 'uturn';
    if (/회전교차로|로터리/.test(t)) return 'round';
    if (/6시 방향/.test(t)) return 'uturn';
    if (/고가도로|지하차도|터널/.test(t) && !/회전/.test(t)) return 'straight';
    if (/좌회전/.test(t)) {
      if (/[78]시/.test(t)) return 'sharpL';
      if (/1[01]시/.test(t)) return 'slightL';
      return 'left';
    }
    if (/우회전/.test(t)) {
      if (/[45]시/.test(t)) return 'sharpR';
      if (/[12]시/.test(t)) return 'slightR';
      return 'right';
    }
    if (/왼쪽/.test(t)) return 'slightL';
    if (/오른쪽/.test(t)) return 'slightR';
    if (/진출|출구|나가/.test(t)) return 'exit';
    if (/진입|램프|고속도로/.test(t)) return 'ramp';
    return 'straight';
  }

  /* 어느 차선으로 가면 좋은가.
     차선 수를 아는 지도가 없으니 「앞으로 나올 갈림길 순서」로 셈한다.
     지금 갈림길이 직진이면 그다음 갈림길이 가까울 때 미리 그쪽 차선을 권한다.
     실제 차선 수와 다를 수 있다는 말을 화면에도 적는다. */
  function laneAdvice(kind, nextKind, metersToNext) {
    var L = { left: '왼쪽', right: '오른쪽', center: '가운데' };
    function pick(k) {
      if (k === 'left' || k === 'slightL' || k === 'sharpL' || k === 'uturn') return 'left';
      if (k === 'right' || k === 'slightR' || k === 'sharpR' || k === 'exit' || k === 'ramp') return 'right';
      if (k === 'round') return 'right';
      return null;
    }
    var side = pick(kind);
    if (side) return { side: side, text: L[side] + ' 차선', early: false };
    if (nextKind && metersToNext != null && metersToNext <= 600) {
      var nx = pick(nextKind);
      if (nx) return { side: nx, text: '미리 ' + L[nx] + ' 차선', early: true };
    }
    return { side: 'center', text: '가운데 차선', early: false };
  }

  /* ══ 말하기 ════════════════════════════════════════════
     운전 중에 말이 겹치면 둘 다 못 알아듣는다. 급한 것(갈림길)이 오면
     덜 급한 것(안전 안내)을 끊는다. */
  function speak(text, urgent) {
    try {
      if (!('speechSynthesis' in window) || !text) return;
      if (urgent) window.speechSynthesis.cancel();
      var u = new SpeechSynthesisUtterance(text);
      u.lang = 'ko-KR'; u.rate = 1.05;
      window.speechSynthesis.speak(u);
    } catch (e) { /* 소리가 안 되면 화면 글자로만 간다 */ }
  }
  /* 아이폰은 사용자가 누른 그 순간의 speak() 만 허용한다.
     나중에 「300미터 앞 우회전」을 읽으려 하면 이미 늦어서 씹힌다.
     빈 소리를 한 번 내보내 그 자리에서 오디오를 열어 둔다. */
  function wakeAudio() {
    try {
      if (!('speechSynthesis' in window)) return;
      var u = new SpeechSynthesisUtterance(' ');
      u.lang = 'ko-KR'; u.volume = 0;
      window.speechSynthesis.speak(u);
    } catch (e) {}
  }

  /* ══ 화면 꺼짐 막기 ═════════════════════════════════════ */
  function acquireWake() {
    if (!('wakeLock' in navigator)) return;
    navigator.wakeLock.request('screen').then(function (l) {
      if (!st) { try { l.release(); } catch (e) {} return; }
      st.wake = l;
      l.addEventListener('release', function () { if (st) st.wake = null; });
    }).catch(function () { /* 권한 없으면 넘어간다 */ });
  }
  function onVisible() {
    if (st && document.visibilityState === 'visible' && !st.wake) acquireWake();
  }

  /* ══ 브라우저 뒤로가기 ══════════════════════════════════
     안내를 켜면 히스토리를 한 칸 쌓는다. 뒤로가기를 누르면 그 칸이 빠지면서
     안내가 닫히고 보던 화면이 그대로 나온다. 화면 안 「뒤로」 단추도
     history.back() 을 불러 같은 길로 닫는다 — 닫는 길이 둘이면 어긋난다. */
  function pushHistory() {
    try {
      var page = sessionStorage.getItem('currentPage') || 'live';
      history.pushState({ page: page, ckDrive: 1 }, '', location.href);
      st.pushed = true;
    } catch (e) { st.pushed = false; }
  }
  function onPop() {
    if (st) stopDrive(true);
  }

  /* ══ 경로 받기 ═════════════════════════════════════════ */
  function loadRoute(cb) {
    var body = {
      startLat: st.pos.lat, startLng: st.pos.lng,
      endLat: st.dest.lat, endLng: st.dest.lng,
      startName: '현재 위치', endName: st.dest.name || '목적지'
    };
    var headers = { 'Content-Type': 'application/json' };
    if (window._authToken) headers['Authorization'] = 'Bearer ' + window._authToken;
    fetch('/api/route/navi', { method: 'POST', headers: headers, body: JSON.stringify(body) })
      .then(function (r) { return r.json(); })
      .then(function (j) {
        var d = j && j.data;
        if (!d || !d.ready || !d.line || d.line.length === 0) {
          setTurn('straight', null, '경로를 불러오지 못했습니다', '');
          return;
        }
        st.route = d;
        st.acc = cumulative(d.line);
        st.guideIdx = 0; st.spoken = {}; st.alerted = {}; st.overSpoken = {};
        st.arrived = false; st.meter = 0; st.sectionIdx = -1;
        nv.drawn = false;
        nvOnPos();
        renderMeta();
        /* 경로를 받자마자 첫 갈림길을 그린다. 다음 좌표가 올 때까지
           「17분 4.9km」만 띄워 두면 무엇을 해야 하는지가 빈 채로 있다 */
        if (st.pos) {
          st.meter = snap(st.pos.lat, st.pos.lng, d.line, st.acc).meter;
          paintTurn(); paintAlerts(); paintLimit();
        } else {
          setTurn('depart', null, headLine(d), st.dest.name || '');
        }
        speak(headSpeech(d), true);
        paintHelp();
        if (cb) cb();
      })
      .catch(function () { setTurn('straight', null, '경로를 불러오지 못했습니다', ''); });
  }
  function headLine(d) {
    return Math.round(d.totalSeconds / 60) + '분 · ' + (d.totalMeters / 1000).toFixed(1) + 'km';
  }
  function headSpeech(d) {
    return Math.round(d.totalSeconds / 60) + '분, ' + (d.totalMeters / 1000).toFixed(1)
         + '킬로미터 남았습니다. 안내를 시작합니다.';
  }

  /* ══ 위치가 올 때마다 ═══════════════════════════════════ */
  function onPos(p) {
    st.pos = { lat: p.coords.latitude, lng: p.coords.longitude };
    st.speedKmh = kmh(p.coords.speed);
    var driving = isDriving(p.coords.speed);
    document.body.classList.toggle('dv-driving', driving);
    paintSpeed();
    nvOnPos();
    if (!st.route) return;

    var line = st.route.line;
    var here = snap(st.pos.lat, st.pos.lng, line, st.acc);
    st.meter = here.meter;
    if (line[here.seg + 1]) {
      st.heading = bearing(line[here.seg][0], line[here.seg][1],
                           line[here.seg + 1][0], line[here.seg + 1][1]);
    }

    /* 이탈 판정 */
    var r = offStreak(here.dist, st.offStreak);
    st.offStreak = r.streak;
    if (r.off) {
      st.offStreak = 0;
      speak('경로를 벗어나 다시 안내합니다.', true);
      setTurn('straight', null, '경로를 다시 찾는 중', '');
      loadRoute();
      return;
    }

    /* 도착 */
    var leftM = Math.max(0, st.route.totalMeters - st.meter);
    if (!st.arrived && leftM <= ARRIVE_M) {
      st.arrived = true;
      setTurn('arrive', null, '도착했습니다', st.dest.name || '');
      speak((st.dest.name || '목적지') + '에 도착했습니다.', true);
      paintAlert(null); paintLane(null);
      renderMeta();
      return;
    }
    if (st.arrived) return;

    paintTurn();
    paintAlerts();
    paintLimit();
    renderMeta();
  }

  /* 다음 갈림길 — 화면과 소리 */
  function paintTurn() {
    var guides = st.route.guides || [];
    while (st.guideIdx < guides.length) {
      var g = guides[st.guideIdx];
      var gd = haversine(st.pos.lat, st.pos.lng, g.lat, g.lng);
      if (gd <= 18) { st.guideIdx++; continue; }
      var kind = turnKind(g.description, g.turnType);
      setTurn(kind, gd, turnText(g), roadOf(g));

      /* 그다음 갈림길 미리보기 */
      var nx = guides[st.guideIdx + 1];
      var nxKind = nx ? turnKind(nx.description, nx.turnType) : null;
      setThen(nx, nxKind, gd);

      /* 차선 */
      var lane = laneAdvice(kind, nxKind, nx ? haversine(g.lat, g.lng, nx.lat, nx.lng) : null);
      paintLane(lane);

      /* 소리 */
      for (var i = 0; i < SPEAK_AT.length; i++) {
        var th = SPEAK_AT[i], key = st.guideIdx + ':' + th;
        if (gd <= th && !st.spoken[key]) {
          st.spoken[key] = true;
          var msg = speechFor(g, th);
          if (lane && lane.side !== 'center' && th <= 300) msg += '. ' + lane.text + '으로 가세요';
          speak(msg, true);
          break;
        }
      }
      return;
    }
    /* 남은 갈림길이 없다 — 목적지까지 직진 */
    setTurn('arrive', Math.max(0, st.route.totalMeters - st.meter), '목적지까지', st.dest.name || '');
    setThen(null, null, 0);
    paintLane(null);
  }

  /* 티맵 안내문은 「…에서 …로 우회전 후 …를 따라 199m 이동」처럼 길다.
     운전 중에 읽을 수 있는 길이가 아니라 앞부분만 남긴다. */
  function shortDesc(desc) {
    var t = String(desc || '').trim();
    var cut = t.indexOf(' 후 ');
    if (cut > 0) t = t.slice(0, cut);
    /* 「일반도로를 따라 74m 이동」 — 조사가 을/를 둘 다 온다. 실측 */
    return t.replace(/[을를] 따라.*$/, '').replace(/ 이동$/, '').trim() || '직진';
  }
  /* 화면에는 「무엇을 하나」와 「어느 길로」를 갈라 놓는다.
     「일반도로를 따라 74m 이동」을 그대로 띄우면 뭘 하라는 건지 안 읽힌다 —
     하는 일은 직진이고, 일반도로는 올라탈 길 이름이다. */
  function turnText(g) {
    var t = shortDesc(g && g.description);
    return /회전|유턴|진입|진출|출구|합류|도착|방면|나가/.test(t) ? t : '직진';
  }
  function roadOf(g) {
    if (g && g.nextRoadName) return g.nextRoadName;
    var m = String((g && g.description) || '').match(/([^\s]+)[을를] 따라/);
    return m ? m[1] : '';
  }
  /* 받침이 있으면 「을」, 없으면 「를」. 소리로 읽을 때 어색하지 않게 */
  function eul(w) {
    var c = String(w || '').charCodeAt(String(w || '').length - 1);
    if (!(c >= 0xAC00 && c <= 0xD7A3)) return '을';
    return ((c - 0xAC00) % 28) === 0 ? '를' : '을';
  }
  function speechFor(g, meters) {
    var what = turnText(g), road = roadOf(g);
    if (what === '직진') {
      return meters + '미터 앞, ' + (road ? road + eul(road) + ' 따라 ' : '') + '직진하세요';
    }
    return meters + '미터 앞, ' + what;
  }

  /* ══ 안전 안내 ═════════════════════════════════════════
     서버가 alerts 를 줄 때만 뜬다. 안 주면 이 자리는 비어 있는다. */
  var ALERT_KO = {
    school: '어린이보호구역', silver: '노인보호구역', bump: '과속방지턱',
    camera: '단속카메라', tunnel: '터널', bridge: '교량',
    underpass: '지하차도', overpass: '고가도로'
  };
  function alertSpeech(a, meters) {
    var ko = ALERT_KO[a.kind] || a.name || '안내 구간';
    var near = meters <= 120;
    var head = near ? '곧 ' : Math.round(meters / 100) * 100 + '미터 앞 ';
    if (a.kind === 'school' || a.kind === 'silver') {
      return head + ko + '입니다. ' + (a.speedLimit ? '시속 ' + a.speedLimit + '킬로미터 이하로 주행하세요.' : '속도를 줄이세요.');
    }
    if (a.kind === 'bump') return head + '과속방지턱이 있습니다. 속도를 줄이세요.';
    if (a.kind === 'camera') return head + '단속카메라입니다.' + (a.speedLimit ? ' 제한속도 시속 ' + a.speedLimit + '킬로미터입니다.' : '');
    if (a.kind === 'tunnel') return head + '터널입니다. 전조등을 켜세요.';
    return head + ko + '입니다.';
  }
  function paintAlerts() {
    var list = st.route.alerts || [];
    if (!list.length) { paintAlert(null); return; }
    var shown = null;
    for (var i = 0; i < list.length; i++) {
      var a = list[i];
      var away = a.meterFromStart - st.meter;
      if (away < -25) continue;
      if (away > ALERT_SHOW) break;
      if (!shown) shown = { a: a, away: Math.max(0, away) };
      for (var k = 0; k < ALERT_SPEAK_AT.length; k++) {
        var th = ALERT_SPEAK_AT[k], key = i + ':' + th;
        if (away <= th && away > -25 && !st.alerted[key]) {
          st.alerted[key] = true;
          speak(alertSpeech(a, Math.max(0, away)));
          break;
        }
      }
    }
    paintAlert(shown);
  }

  /* 지금 달리는 구간의 제한속도. 서버가 sections 를 줄 때만 있다 */
  function currentSection() {
    var secs = st.route.sections || [];
    for (var i = 0; i < secs.length; i++) {
      if (st.meter >= secs[i].fromMeter && st.meter < secs[i].toMeter) return { s: secs[i], i: i };
    }
    return null;
  }
  function paintLimit() {
    var cur = currentSection();
    var box = document.getElementById('navLimit');
    if (!box) return;
    if (!cur || cur.s.speedLimit == null) { box.hidden = true; return; }
    box.hidden = false;
    box.textContent = cur.s.speedLimit;
    box.title = '제한속도 ' + cur.s.speedLimit + 'km/h · ' + (cur.s.speedSource || '');
    box.classList.toggle('dv-limit-guess', cur.s.speedSource === '도로등급 추정');
    if (st.speedKmh > cur.s.speedLimit + OVER_KMH && !st.overSpoken[cur.i]) {
      st.overSpoken[cur.i] = true;
      speak('제한속도 시속 ' + cur.s.speedLimit + '킬로미터입니다.');
    }
    var sp = document.getElementById('navSpeed');
    if (sp) sp.classList.toggle('dv-over', st.speedKmh > cur.s.speedLimit + OVER_KMH);
  }

  /* ══ 화면 그리기 ═══════════════════════════════════════ */
  var TURN_D = {
    straight: 'M12 21V5M6 11l6-6 6 6',
    left:     'M18 21v-6a5 5 0 0 0-5-5H6M10 5 5 10l5 5',
    right:    'M6 21v-6a5 5 0 0 1 5-5h7M14 5l5 5-5 5',
    slightL:  'M16 21v-6.2a5 5 0 0 0-1.4-3.5L8.5 5M8 11V4.5h6.5',
    slightR:  'M8 21v-6.2a5 5 0 0 1 1.4-3.5L15.5 5M16 11V4.5H9.5',
    sharpL:   'M17 21v-4.5a5 5 0 0 0-5-5H6.5M11 6.5 6 11.5l5 5',
    sharpR:   'M7 21v-4.5a5 5 0 0 1 5-5h5.5M13 6.5l5 5-5 5',
    uturn:    'M17 21v-9a5 5 0 0 0-10 0v5M3 13.5 7 17.5l4-4',
    round:    'M12 21v-4.2M12 5.2a3.6 3.6 0 1 0 0 7.2 3.6 3.6 0 0 0 0-7.2M15.2 6.6 19 3.4M15.6 3h3.6v3.6',
    exit:     'M7 21V8M7 8 4 11M7 8l3 3M13 6h7v7M20 6l-7 7',
    ramp:     'M8 21v-7a6 6 0 0 1 6-6h4M14.5 3.5 19 8l-4.5 4.5',
    depart:   'M12 21V8M7.5 12.5 12 8l4.5 4.5M5 5h14',
    arrive:   'M12 21c4.2-5.1 6.3-8.6 6.3-11.3A6.3 6.3 0 0 0 5.7 9.7C5.7 12.4 7.8 15.9 12 21zM12 7.5v4.6M9.7 9.8h4.6'
  };
  function turnSvg(kind) {
    var d = TURN_D[kind] || TURN_D.straight;
    return '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="' + d + '"/></svg>';
  }
  function fmtM(m) {
    if (m == null) return '';
    return m >= 1000 ? (m / 1000).toFixed(1) + 'km' : Math.round(m / 10) * 10 + 'm';
  }
  function setTurn(kind, meters, what, road) {
    var a = document.getElementById('navArrow');
    if (a) a.innerHTML = turnSvg(kind);
    var d = document.getElementById('navDist');
    if (d) d.textContent = meters == null ? '' : fmtM(meters);
    var w = document.getElementById('navWhat');
    if (w) w.textContent = what || '';
    var r = document.getElementById('navRoad');
    if (r) { r.textContent = road || ''; r.hidden = !road; }
  }
  function setThen(nx, nxKind, gdNow) {
    var box = document.getElementById('navThen');
    if (!box) return;
    if (!nx) { box.hidden = true; return; }
    box.hidden = false;
    box.innerHTML = '<span class="dv-then-l">이어서</span>' +
      '<span class="dv-then-i">' + turnSvg(nxKind) + '</span>' +
      '<b>' + nvEsc(turnText(nx)) + '</b>' +
      (roadOf(nx) ? '<span class="dv-then-r">' + nvEsc(roadOf(nx)) + '</span>' : '');
  }
  /* 몇 차선 도로인지는 티맵 도로정보가 준다(sections[].lanes).
     그 수만큼 칸을 그리고 갈 자리를 칠한다. 모르면 세 칸짜리 그림으로 둔다 —
     그때는 「몇 번째 차선」이 아니라 「어느 쪽」이라는 말이다. */
  function laneOn(side, n) {
    if (side === 'left')  return n >= 4 ? [0, 1] : [0];
    if (side === 'right') return n >= 4 ? [n - 2, n - 1] : [n - 1];
    return n % 2 ? [(n - 1) / 2] : [n / 2 - 1, n / 2];
  }
  function paintLane(lane) {
    var box = document.getElementById('navLane');
    if (!box) return;
    if (!lane) { box.hidden = true; return; }
    var cur = st.route ? currentSection() : null;
    var known = cur && cur.s.lanes > 0 && cur.s.lanes <= 8 ? cur.s.lanes : 0;
    var n = known || 3;
    var on = laneOn(lane.side, n);
    box.hidden = false;
    box.className = 'dv-lane dv-lane-' + lane.side;
    var bars = '';
    for (var i = 0; i < n; i++) {
      bars += '<i class="dv-ln' + (on.indexOf(i) >= 0 ? ' on' : '') + '"></i>';
    }
    box.innerHTML = '<span class="dv-lane-bars">' + bars + '</span>' +
      '<b>' + nvEsc(lane.text) + '</b>' +
      '<span class="dv-lane-why">' +
      (known ? '이 길은 ' + known + '차선 · 티맵 도로정보'
             : '차선 수를 모르는 길입니다') +
      ' · 갈림길 순서로 고른 자리' + '</span>';
  }
  function paintAlert(shown) {
    var box = document.getElementById('navAlert');
    if (!box) return;
    if (!shown) { box.hidden = true; return; }
    var a = shown.a;
    box.hidden = false;
    box.className = 'dv-alert dv-alert-' + (a.kind || 'etc');
    box.innerHTML =
      '<span class="dv-alert-k">' + nvEsc(ALERT_KO[a.kind] || '안내') + '</span>' +
      '<b class="dv-alert-d">' + fmtM(shown.away) + '</b>' +
      (a.name ? '<span class="dv-alert-n">' + nvEsc(a.name) + '</span>' : '') +
      (a.speedLimit ? '<em class="dv-alert-s">' + a.speedLimit + '</em>' : '');
  }
  function paintSpeed() {
    var e = document.getElementById('navKmh');
    if (e) e.textContent = Math.round(st.speedKmh || 0);
  }
  function renderMeta() {
    if (!st || !st.route) return;
    var leftM = Math.max(0, st.route.totalMeters - (st.meter || 0));
    var ratio = st.route.totalMeters ? leftM / st.route.totalMeters : 1;
    var leftS = Math.round(st.route.totalSeconds * ratio);
    var eta = new Date(Date.now() + leftS * 1000);
    var two = function (n) { return (n < 10 ? '0' : '') + n; };
    var set = function (id, t) { var e = document.getElementById(id); if (e) e.textContent = t; };
    set('navLeft', Math.max(1, Math.round(leftS / 60)) + '분');
    set('navDistLeft', (leftM / 1000).toFixed(1) + 'km');
    set('navEta', two(eta.getHours()) + ':' + two(eta.getMinutes()) + ' 도착');
    set('navTo', st.dest.name || '다음 장소');
  }
  /* 처음 몇 초만 — 이 화면이 무엇으로 만들어졌는지 정직하게 적는다 */
  function paintHelp() {
    var src = (st.route && st.route.alertSource) || '';
    var has = ((st.route && st.route.alerts) || []).length;
    var msg = has
      ? '안전 안내 ' + has + '곳 · ' + (src || '출처 미표기')
      : '이 구간은 안전 안내 데이터가 없습니다. 갈림길 안내는 그대로 됩니다.';
    nvNote(msg);
    setTimeout(function () { if (st) nvNote(''); }, 6000);
  }

  /* ══ 오버레이 ══════════════════════════════════════════ */
  function ensureOverlay() {
    var el = document.getElementById('navDrive');
    if (el) return el;
    el = document.createElement('div');
    el.id = 'navDrive';
    el.className = 'dv';
    el.setAttribute('role', 'region');
    el.setAttribute('aria-label', '길 안내');
    el.innerHTML =
      '<div class="dv-top">' +
        '<div class="dv-bar">' +
          '<button type="button" class="dv-ic" id="navBack" aria-label="뒤로">' +
            '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M15 5l-7 7 7 7"/></svg>' +
          '</button>' +
          '<button type="button" class="dv-ic dv-ic-w" id="navPlan">' +
            '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 6h16M4 12h16M4 18h10"/></svg>일정' +
          '</button>' +
          '<span class="dv-clock" id="navClock"></span>' +
        '</div>' +
        '<div class="dv-turn">' +
          '<span class="dv-arrow" id="navArrow"></span>' +
          '<div class="dv-turn-t">' +
            '<b class="dv-dist" id="navDist"></b>' +
            '<p class="dv-what" id="navWhat">현재 위치를 잡는 중</p>' +
            '<p class="dv-road" id="navRoad" hidden></p>' +
          '</div>' +
        '</div>' +
        '<div class="dv-then" id="navThen" hidden></div>' +
        '<div class="dv-lane" id="navLane" hidden></div>' +
      '</div>' +
      '<div class="dv-stage" id="navStage">' +
        '<div class="dv-canvas" id="navMap2d"></div>' +
        '<div class="dv-canvas dv-model" id="navMap3d" hidden>' +
          '<div class="dv-model-frame" id="navModelFrame"></div>' +
          '<div class="dv-model-layer" id="navModelLayer"></div>' +
        '</div>' +
        '<div class="dv-alert" id="navAlert" hidden></div>' +
        '<div class="dv-speed" id="navSpeed">' +
          '<b id="navKmh">0</b><span>km/h</span>' +
          '<em class="dv-limit" id="navLimit" hidden></em>' +
        '</div>' +
        '<div class="dv-view" role="group" aria-label="지도 보기 방식">' +
          '<button type="button" class="dv-v on" id="navV2">2D 지도</button>' +
          '<button type="button" class="dv-v" id="navV3">3D 모형</button>' +
        '</div>' +
        '<button type="button" class="dv-traffic on" id="navTraffic">실시간 교통</button>' +
        /* 지도를 손으로 밀면 따라가기를 멈춘다. 다시 붙고 싶을 때 누른다 */
        '<button type="button" class="dv-recenter" id="navRecenter" hidden>현재 위치</button>' +
        /* 주행 중 자동 제안 — 먼저 말해 주되, 바꾸는 것은 사람이 누른다 */
        '<div class="dv-sug" id="navSug" hidden role="status" aria-live="polite">' +
          '<p class="dv-sug-t" id="navSugT"></p>' +
          '<div class="dv-sug-b">' +
            '<button type="button" class="dv-sug-go" id="navSugSwap">순서 바꾸기</button>' +
            '<button type="button" class="dv-sug-go" id="navSugQuiet">다른 곳으로</button>' +
            '<button type="button" class="dv-sug-x" id="navSugNo">그대로 간다</button>' +
          '</div>' +
        '</div>' +
        '<p class="dv-note" id="navNote" hidden></p>' +
      '</div>' +
      '<div class="dv-bottom">' +
        '<div class="dv-meta">' +
          '<b id="navLeft">—</b>' +
          '<span id="navDistLeft"></span><i></i>' +
          '<span id="navEta"></span><i></i>' +
          '<span id="navTo"></span>' +
        '</div>' +
        '<div class="dv-acts">' +
          '<button type="button" class="dv-b dv-b-say" id="navSay">말로 하기</button>' +
          '<button type="button" class="dv-b" id="navSwap">순서 바꾸기</button>' +
          '<button type="button" class="dv-b" id="navQuiet">다른 곳으로</button>' +
          '<button type="button" class="dv-b dv-b-off" id="navStop">' +
            '<span class="dv-hold" aria-hidden="true"></span><span class="dv-b-t">안내 종료</span>' +
          '</button>' +
        '</div>' +
        '<p class="dv-tip">달리는 동안은 단추가 접힙니다. 「말로 하기」만 크게 남습니다.</p>' +
      '</div>';
    document.body.appendChild(el);
    document.body.classList.add('dv-open');

    var on = function (id, fn) { var b = document.getElementById(id); if (b) b.addEventListener('click', fn); };
    on('navBack', function () { closeDrive(); });
    on('navPlan', function () {
      closeDrive();
      if (window.go) window.go('live');
    });
    /* 실시간 화면의 갈래를 그대로 부른다 — 주행 중에도 동선을 바꿀 수 있다는 것이
       이 제품의 주장이다. 안내 화면에서 못 하면 주장이 아니라 말뿐이다 */
    on('navSwap',  function () { if (window.rlDo) window.rlDo('swap');  else if (window.go) window.go('map'); });
    on('navQuiet', function () { if (window.rlDo) window.rlDo('quiet'); else if (window.go) window.go('map'); });
    on('navSay',   function () {
      if (window.rlVoiceToggle) window.rlVoiceToggle();
      else if (window.startVoice) window.startVoice();
    });
    on('navRecenter', function () { nvFollowOn(); });
    on('navSugSwap',  function () { nvSugHide(); if (window.rlDo) window.rlDo('swap'); });
    on('navSugQuiet', function () { nvSugHide(); if (window.rlDo) window.rlDo('quiet'); });
    on('navSugNo',    function () { nvSugHide(); });
    on('navV2',    function () { nvMode('2d'); });
    on('navV3',    function () { nvMode('3d'); });
    on('navTraffic', function () {
      nv.traffic = !nv.traffic;
      var b = document.getElementById('navTraffic');
      if (b) b.classList.toggle('on', nv.traffic);
      nvTrafficApply();
    });
    bindStop();
    tickClock();
    return el;
  }

  /* 안내 종료 — 달리는 중에는 길게 눌러야 먹는다.
     스쳐서 꺼지면 길 한복판에서 안내가 사라진다 */
  function bindStop() {
    var b = document.getElementById('navStop');
    if (!b) return;
    var timer = null;
    function driving() { return document.body.classList.contains('dv-driving'); }
    function begin(e) {
      if (!driving()) return;
      e.preventDefault();
      b.classList.add('dv-holding');
      timer = setTimeout(function () { b.classList.remove('dv-holding'); stopDrive(); }, 1100);
    }
    function end() {
      if (timer) { clearTimeout(timer); timer = null; }
      b.classList.remove('dv-holding');
    }
    b.addEventListener('click', function () { if (!driving()) closeDrive(); });
    b.addEventListener('pointerdown', begin);
    b.addEventListener('pointerup', end);
    b.addEventListener('pointerleave', end);
    b.addEventListener('pointercancel', end);
  }
  function tickClock() {
    var e = document.getElementById('navClock');
    if (!e || !st) return;
    var d = new Date(), two = function (n) { return (n < 10 ? '0' : '') + n; };
    e.textContent = two(d.getHours()) + ':' + two(d.getMinutes());
    st.clock = setTimeout(tickClock, 20000);
  }

  /* ── 주행 중 자동 제안 ──────────────────────────────────
     지금까지는 사람이 눌러야 바뀌었다. 가는 중에 다음 목적지가 갑자기 붐비거나
     비가 오면 먼저 말해 준다. 바꾸는 것은 여전히 사람이 누른다 —
     운전 중에 화면이 제멋대로 바뀌면 그게 더 위험하다.

     값은 실시간 화면이 이미 받아 둔 것을 읽는다. 같은 것을 두 번 묻지 않는다. */
  var SUG_EVERY = 90000;      /* 이만큼마다 한 번 살핀다 */
  var SUG_BUSY = 70;          /* 집중률이 이 이상이면 붐빈다고 본다 */

  function nvSugHide() {
    var b = document.getElementById('navSug');
    if (b) b.hidden = true;
  }
  function nvSugShow(text) {
    var b = document.getElementById('navSug'), t = document.getElementById('navSugT');
    if (!b || !t) return;
    t.textContent = text;
    b.hidden = false;
  }
  function nvSuggestTick() {
    if (!st) return;
    var snap = (typeof window.rlSnapshot === 'function') ? window.rlSnapshot() : null;
    if (!snap || !snap.next || !snap.next.name) return;
    var name = snap.next.name;
    var c = snap.crowd || {}, w = snap.weather || {};
    var kind = null, say = null;
    if (c.rate != null && Number(c.rate) >= SUG_BUSY) {
      kind = 'busy';
      say = name + ' 이 붐빕니다. 순서를 바꾸거나 가까운 다른 곳을 찾을 수 있습니다.';
    } else if (String(w.summary || w.text || '').indexOf('비') >= 0) {
      kind = 'rain';
      say = name + ' 에 비 소식이 있습니다. 실내로 바꾸거나 순서를 미룰 수 있습니다.';
    }
    if (!kind) { nvSugHide(); return; }
    var key = kind + ':' + name;
    st.suggested = st.suggested || {};
    if (st.suggested[key]) return;      /* 같은 말을 두 번 하지 않는다 */
    st.suggested[key] = true;
    nvSugShow(say);
    speak(say);
  }
  function nvSuggestStart() {
    if (!st || st.sugTimer) return;
    st.sugTimer = setInterval(function () {
      try {
        if (typeof window.rlAgain === 'function') window.rlAgain();
      } catch (e) {}
      setTimeout(nvSuggestTick, 1500);   /* 새 값이 들어올 틈을 준다 */
    }, SUG_EVERY);
  }

  /* ══ 지도 ══════════════════════════════════════════════
     2D 는 카카오 지도에 실시간 교통정보를 얹는다.
     3D 는 우리가 구워 둔 건물 모형을 쓴다 — 전국 251곳이 이미 있다.
     어느 쪽을 보든 경로와 지금 자리는 같은 값으로 그린다. */
  var nv = { follow: true, map: null, line: null, halo: null, me: null, dest: null,
             mode: '2d', traffic: true, idx: null, scene: null, meta: null, drawn: false };

  function nvToken(name, fb) {
    try {
      var v = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
      return v || fb;
    } catch (e) { return fb; }
  }
  function nvEsc(t) {
    return String(t == null ? '' : t)
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  }
  function nvNote(t) {
    var e = document.getElementById('navNote');
    if (!e) return;
    if (!t) { e.hidden = true; return; }
    e.hidden = false; e.textContent = t;
  }
  function nvKakaoReady(cb) {
    if (typeof kakao === 'undefined' || !kakao.maps) return false;
    if (kakao.maps.Map) { cb(); return true; }
    try { kakao.maps.load(cb); return true; } catch (e) { return false; }
  }
  function nvInit2d() {
    var box = document.getElementById('navMap2d');
    if (!box || nv.map || !st || !st.pos) return;
    var ok = nvKakaoReady(function () {
      nv.map = new kakao.maps.Map(box, {
        /* 휴대폰은 화면이 좁다. 실제 내비처럼 바짝 당겨야 다음 갈림길이 읽힌다.
           넓은 화면은 한 단계 물려 앞뒤를 같이 본다 */
        center: new kakao.maps.LatLng(st.pos.lat, st.pos.lng),
        level: (window.innerWidth <= 520 ? 2 : 3)
      });
      /* 손으로 밀면 그 자리에 둔다. 우리가 옮기는 setCenter 는 dragstart 를 내지 않는다 */
      try { kakao.maps.event.addListener(nv.map, 'dragstart', nvFollowOff); } catch (e) {}
      nvTrafficApply();
      nvDrawRoute();
      nvFollow();
    });
    if (!ok) nvNote('지도를 불러오지 못했습니다. 안내는 그대로 됩니다.');
  }
  function nvTrafficApply() {
    if (!nv.map || typeof kakao === 'undefined' || !kakao.maps.MapTypeId) return;
    try {
      if (nv.traffic) nv.map.addOverlayMapTypeId(kakao.maps.MapTypeId.TRAFFIC);
      else nv.map.removeOverlayMapTypeId(kakao.maps.MapTypeId.TRAFFIC);
    } catch (e) { /* 교통정보를 못 얹어도 안내는 된다 */ }
  }
  function nvDrawRoute() {
    if (!nv.map || !st || !st.route || !st.route.line || !st.route.line.length) return;
    var path = st.route.line.map(function (c) { return new kakao.maps.LatLng(c[0], c[1]); });
    [nv.halo, nv.line].forEach(function (l) { if (l) l.setMap(null); });
    nv.halo = new kakao.maps.Polyline({
      path: path, strokeWeight: 15, strokeColor: nvToken('--panel', '#FFFFFF'),
      strokeOpacity: 0.95, strokeStyle: 'solid'
    });
    nv.line = new kakao.maps.Polyline({
      path: path, strokeWeight: 9, strokeColor: nvToken('--sig', '#FF9E12'),
      strokeOpacity: 0.98, strokeStyle: 'solid'
    });
    nv.halo.setMap(nv.map);
    nv.line.setMap(nv.map);

    if (nv.dest) nv.dest.setMap(null);
    nv.dest = new kakao.maps.CustomOverlay({
      position: path[path.length - 1], yAnchor: 1.15, zIndex: 3,
      content: '<div class="dv-dest">' + nvEsc((st.dest && st.dest.name) || '도착') + '</div>'
    });
    nv.dest.setMap(nv.map);
    nvDrawAlerts();
    nv.drawn = true;
  }
  /* 안전 지점을 지도 위에도 찍는다. 소리만으로는 어디쯤인지 모른다 */
  function nvDrawAlerts() {
    if (!nv.map) return;
    (nv.marks || []).forEach(function (m) { m.setMap(null); });
    nv.marks = [];
    ((st.route && st.route.alerts) || []).forEach(function (a) {
      if (a.lat == null || a.lng == null) return;
      var ov = new kakao.maps.CustomOverlay({
        position: new kakao.maps.LatLng(a.lat, a.lng), yAnchor: 0.5, xAnchor: 0.5, zIndex: 4,
        content: '<div class="dv-mk dv-mk-' + nvEsc(a.kind || 'etc') + '" title="' +
                 nvEsc(ALERT_KO[a.kind] || '') + '"></div>'
      });
      ov.setMap(nv.map);
      nv.marks.push(ov);
    });
  }
  /* 지금 있는 자리로 지도를 옮긴다. 운전 중에는 손으로 끌 일이 없다 */
  /* 따라가기 — 실제 내비처럼 갈수록 화면이 같이 간다.
     다만 손으로 지도를 밀면 그 자리에 두어야 한다. 밀어 놓고 봤는데
     다음 좌표가 와서 도로 튕겨 돌아가면 아무것도 못 본다.
     멈춘 동안에는 「현재 위치」 단추를 띄우고, 누르면 다시 붙는다. */
  function nvFollowOff() {
    if (!nv.follow) return;
    nv.follow = false;
    var b = document.getElementById('navRecenter');
    if (b) b.hidden = false;
  }
  function nvFollowOn() {
    nv.follow = true;
    nv.pan = { x: 0, y: 0 };
    var b = document.getElementById('navRecenter');
    if (b) b.hidden = true;
    if (nv.mode === '2d') nvFollow(); else nvDraw3d();
  }

  function nvFollow() {
    if (!nv.map || !st || !st.pos) return;
    var ll = new kakao.maps.LatLng(st.pos.lat, st.pos.lng);
    var deg = st.heading == null ? 0 : st.heading;
    if (!nv.me) {
      nv.me = new kakao.maps.CustomOverlay({
        position: ll, yAnchor: 0.5, xAnchor: 0.5, zIndex: 6,
        content: '<div class="dv-me"><i style="transform:rotate(' + deg.toFixed(0) + 'deg)"></i></div>'
      });
      nv.me.setMap(nv.map);
    } else {
      nv.me.setPosition(ll);
      nv.me.setContent('<div class="dv-me"><i style="transform:rotate(' + deg.toFixed(0) + 'deg)"></i></div>');
    }
    if (nv.follow) nv.map.setCenter(ll);
  }

  /* ── 3D 모형 ──────────────────────────────────────────── */
  function nvProject(fit, lat, lng) {
    return { x: fit.a * lng + fit.b * lat + fit.c, y: fit.d * lng + fit.e * lat + fit.f };
  }
  function nvIndex() {
    if (nv.idx) return Promise.resolve(nv.idx);
    return fetch('/img/mass_index.json')
      .then(function (r) { return r.ok ? r.json() : []; })
      .then(function (j) { nv.idx = Array.isArray(j) ? j : []; return nv.idx; })
      .catch(function () { nv.idx = []; return nv.idx; });
  }
  function nvPickScene(lat, lng) {
    var best = null, bd = Infinity;
    (nv.idx || []).forEach(function (sc) {
      if (!sc.bbox || !sc.fit) return;
      var my = (sc.bbox.s + sc.bbox.n) / 2, mx = (sc.bbox.w + sc.bbox.e) / 2;
      var q = nvProject(sc.fit, lat, lng);
      var inside = q.x >= 0 && q.x <= 100 && q.y >= 0 && q.y <= 100;
      var d = (my - lat) * (my - lat) + (mx - lng) * (mx - lng);
      if (inside) d -= 1000;               /* 담기는 장면을 먼저 고른다 */
      if (d < bd) { bd = d; best = sc; }
    });
    return best;
  }
  function nvDraw3d() {
    if (!st || !st.pos) return;
    nvIndex().then(function () {
      var sc = nvPickScene(st.pos.lat, st.pos.lng);
      if (!sc) { nvNote('이 지역은 아직 3D 모형이 없습니다. 2D 지도로 보세요.'); return; }
      var q = nvProject(sc.fit, st.pos.lat, st.pos.lng);
      var far = q.x < -4 || q.x > 104 || q.y < -4 || q.y > 104;
      nvNote(far ? sc.label + ' 모형입니다. 지금 자리는 이 동네 밖이라 2D 지도가 정확합니다.' : '');
      if (nv.scene && nv.scene.key === sc.key) { nvPaint3d(sc); return; }
      fetch(sc.svg, { cache: 'force-cache' })
        .then(function (r) { return r.text(); })
        .then(function (svg) {
          var frame = document.getElementById('navModelFrame');
          if (!frame) return null;
          frame.innerHTML = svg;
          nv.scene = sc;
          return fetch(sc.json).then(function (r) { return r.ok ? r.json() : null; });
        })
        .then(function (meta) { nv.meta = meta || nv.meta; nvPaint3d(sc); })
        .catch(function () { nvNote('모형을 불러오지 못했습니다. 2D 지도로 보세요.'); });
    });
  }
  function nvPaint3d(sc) {
    var stage = document.getElementById('navMap3d');
    var frame = document.getElementById('navModelFrame');
    var layer = document.getElementById('navModelLayer');
    if (!stage || !frame || !layer || !sc || !st || !st.pos) return;
    var ar = (nv.meta && nv.meta.ar) || 1.5;
    /* 동네 전체를 한 화면에 담으면 건물이 점만 해진다. 실제 내비처럼 당긴다.
       휴대폰이 더 좁으니 더 당기고, 달리는 자리가 화면 한가운데 오게 민다. */
    var Z = (window.innerWidth <= 520 ? 2.6 : 1.8);
    var w = Math.max(stage.clientWidth, stage.clientHeight * ar) * Z;
    var h = w / ar;
    frame.style.width = w + 'px';  frame.style.height = h + 'px';
    layer.style.width = w + 'px';  layer.style.height = h + 'px';
    var at = nvProject(sc.fit, st.pos.lat, st.pos.lng);
    var pan = nv.pan || { x: 0, y: 0 };
    var shift = 'translate(-50%,-50%) translate(' +
      ((50 - at.x) / 100 * w + pan.x).toFixed(1) + 'px,' +
      ((50 - at.y) / 100 * h + pan.y).toFixed(1) + 'px)';
    frame.style.transform = shift;
    layer.style.transform = shift;

    var d = '';
    var line = (st.route && st.route.line) || [];
    var run = [], out = null;
    for (var i = 0; i < line.length; i++) {
      var q = nvProject(sc.fit, line[i][0], line[i][1]);
      if (q.x >= -6 && q.x <= 106 && q.y >= -6 && q.y <= 106) {
        if (out) { run.push(out); out = null; }
        run.push(q);
      } else {
        if (run.length) { run.push(q); d += nvRunD(run); run = []; }
        out = q;
      }
    }
    if (run.length >= 2) d += nvRunD(run);

    var me = nvProject(sc.fit, st.pos.lat, st.pos.lng);
    var deg = st.heading == null ? 0 : st.heading;
    var marks = ((st.route && st.route.alerts) || []).map(function (a) {
      if (a.lat == null) return '';
      var p = nvProject(sc.fit, a.lat, a.lng);
      if (p.x < 0 || p.x > 100 || p.y < 0 || p.y > 100) return '';
      return '<div class="dv-mk dv-mk-' + nvEsc(a.kind || 'etc') +
             '" style="left:' + p.x.toFixed(2) + '%;top:' + p.y.toFixed(2) + '%"></div>';
    }).join('');
    layer.innerHTML =
      '<svg class="dv-m-svg" viewBox="0 0 100 100" preserveAspectRatio="none">' +
        (d ? '<path class="dv-m-halo" d="' + d + '"/><path class="dv-m-road" d="' + d + '"/>' : '') +
      '</svg>' + marks +
      '<div class="dv-me dv-me-3d" style="left:' + me.x.toFixed(2) + '%;top:' + me.y.toFixed(2) +
      '%"><i style="transform:rotate(' + deg.toFixed(0) + 'deg)"></i></div>';
  }
  /* 모형도 손으로 밀 수 있게 한다. 미는 동안은 따라가기를 멈춘다 */
  function nvBind3dDrag() {
    var box = document.getElementById('navMap3d');
    if (!box || box._nvDrag) return;
    box._nvDrag = true;
    var g = null;
    box.addEventListener('pointerdown', function (e) {
      if (e.target.closest('button')) return;
      var pn = nv.pan || { x: 0, y: 0 };
      g = { x: e.clientX, y: e.clientY, px: pn.x, py: pn.y };
      nvFollowOff();
      try { box.setPointerCapture(e.pointerId); } catch (err) {}
    });
    box.addEventListener('pointermove', function (e) {
      if (!g) return;
      nv.pan = { x: g.px + (e.clientX - g.x), y: g.py + (e.clientY - g.y) };
      if (nv.scene) nvPaint3d(nv.scene);
    });
    ['pointerup','pointercancel','pointerleave'].forEach(function (t) {
      box.addEventListener(t, function () { g = null; });
    });
  }

  function nvRunD(run) {
    return 'M' + run.map(function (q) { return q.x.toFixed(2) + ' ' + q.y.toFixed(2); }).join('L');
  }

  function nvMode(m) {
    nv.mode = m;
    var a = document.getElementById('navMap2d'), b = document.getElementById('navMap3d');
    var v2 = document.getElementById('navV2'), v3 = document.getElementById('navV3');
    var tf = document.getElementById('navTraffic');
    if (a) a.hidden = (m !== '2d');
    if (b) b.hidden = (m !== '3d');
    if (v2) v2.classList.toggle('on', m === '2d');
    if (v3) v3.classList.toggle('on', m === '3d');
    /* 교통정보는 카카오 지도에만 얹힌다. 모형에는 얹을 자리가 없다 */
    if (tf) tf.hidden = (m !== '2d');
    if (m === '2d') {
      nvNote('');
      nvInit2d();
      if (nv.map) { nv.map.relayout(); nvFollow(); }
    } else {
      nvBind3dDrag();
      nvDraw3d();
    }
  }
  function nvOnPos() {
    if (!st || !st.pos) return;
    if (nv.mode === '2d') {
      if (!nv.map) nvInit2d();
      else { if (!nv.drawn) nvDrawRoute(); nvFollow(); }
    } else {
      nvDraw3d();
    }
  }
  function nvReset() {
    (nv.marks || []).forEach(function (m) { try { m.setMap(null); } catch (e) {} });
    nv = { follow: true, map: null, line: null, halo: null, me: null, dest: null, marks: [],
           mode: '2d', traffic: true, idx: nv.idx, scene: null, meta: null, drawn: false };
  }

  /* ══ 시작 · 종료 ═══════════════════════════════════════ */
  function startDrive(dest) {
    /* 이미 켜져 있으면 먼저 끈다. 안 그러면 위치 추적이 두 겹으로 돌고
       히스토리도 두 칸 쌓여서 뒤로가기를 두 번 눌러야 닫힌다 */
    if (st) stopDrive();
    if (!dest || dest.lat == null || dest.lng == null) {
      alert('다음 목적지 좌표가 없어 길 안내를 시작할 수 없습니다.');
      return;
    }
    if (!navigator.geolocation) { alert('이 브라우저는 위치를 지원하지 않습니다.'); return; }

    /* 위치 전송 고지 — 시작 전에 한 번 묻는다.
       다른 화면은 좌표를 격자로 뭉개서 보내지만 주행 안내는 그럴 수 없다.
       110m 격자로는 턴 안내가 안 되고, 브라우저에서 티맵을 직접 부르면
       appKey 가 노출된다. 그래서 이 기능만 정밀 좌표를 서버로 보낸다. */
    var NL = String.fromCharCode(10);
    var NOTICE = '길 안내 중에는 지금 있는 곳의 정확한 위치를 서버로 보냅니다.' + NL
      + '티맵에서 경로를 받아 갈림길을 읽어 주기 위해서입니다.' + NL
      + '안내를 끄면 전송도 같이 멈춥니다.' + NL + NL
      + '시작할까요?';
    if (!window.confirm(NOTICE)) return;

    wakeAudio();   /* 아이폰은 누른 그 순간에 한 번 울려 둬야 뒤 안내가 나온다 */

    st = { dest: dest, pos: null, route: null, acc: [], offStreak: 0, guideIdx: 0,
           spoken: {}, alerted: {}, overSpoken: {}, wake: null, watchId: null,
           meter: 0, speedKmh: 0, heading: null, arrived: false, pushed: false, clock: null };
    ensureOverlay();
    pushHistory();
    window.addEventListener('popstate', onPop);
    setTurn('depart', null, '현재 위치를 잡는 중', '');
    speak('운전 중에는 화면을 보지 마세요. 소리로 안내합니다.', true);
    acquireWake();
    document.addEventListener('visibilitychange', onVisible);
    nvSuggestStart();
    st.watchId = navigator.geolocation.watchPosition(
      function (p) {
        var first = !st.pos;
        st.pos = { lat: p.coords.latitude, lng: p.coords.longitude };
        st.speedKmh = kmh(p.coords.speed);
        /* 첫 좌표가 오면 경로를 받기 전에 지도부터 띄운다 — 빈 화면을 보여 주지 않는다 */
        if (first) { paintSpeed(); nvOnPos(); loadRoute(); } else onPos(p);
      },
      function () { setTurn('straight', null, '위치 권한을 켜 주세요', '브라우저 설정에서 바꿀 수 있습니다'); },
      { enableHighAccuracy: true, maximumAge: 1000, timeout: 10000 }
    );
  }

  /* 화면 안 단추로 닫을 때 — 히스토리를 거쳐 닫는다.
     그래야 브라우저 뒤로가기와 닫는 길이 하나가 된다 */
  function closeDrive() {
    if (st && st.pushed) { history.back(); return; }
    stopDrive();
  }
  function stopDrive(fromPop) {
    if (!st) return;
    var pushed = st.pushed;
    if (st.sugTimer) { clearInterval(st.sugTimer); st.sugTimer = null; }
    if (st.watchId != null) navigator.geolocation.clearWatch(st.watchId);
    if (st.clock) clearTimeout(st.clock);
    if (st.wake) { try { st.wake.release(); } catch (e) {} }
    document.removeEventListener('visibilitychange', onVisible);
    window.removeEventListener('popstate', onPop);
    try { window.speechSynthesis.cancel(); } catch (e) {}
    document.body.classList.remove('dv-driving');
    var el = document.getElementById('navDrive');
    if (el) el.remove();
    document.body.classList.remove('dv-open');
    nvReset();
    st = null;
    /* 단추가 아니라 코드로 껐다면 쌓아 둔 히스토리 한 칸을 되돌린다 */
    if (pushed && !fromPop) { try { history.back(); } catch (e) {} }
  }

  window.startDrive = startDrive;
  window.stopDrive = function () { stopDrive(); };

  /* ══ 자체검증 ══════════════════════════════════════════ */
  window._navCheck = function () {
    var line = [[35.1151, 129.0413], [35.1160, 129.0500]];
    var on = distToLine(35.1155, 129.0450, line);
    console.assert(on < 50, '선 위 점은 50m 이내: ' + on);
    var off = distToLine(35.2000, 129.0450, line);
    console.assert(off > 50, '먼 점은 50m 초과: ' + off);

    var s = 0, res;
    res = offStreak(80, s); s = res.streak; console.assert(!res.off, '1회는 이탈 아님');
    res = offStreak(80, s); s = res.streak; console.assert(!res.off, '2회는 이탈 아님');
    res = offStreak(80, s); s = res.streak; console.assert(res.off, '3회 연속은 이탈');
    res = offStreak(10, s); s = res.streak; console.assert(s === 0 && !res.off, '복귀하면 초기화');

    var d = haversine(35.1151, 129.0413, 35.1587, 129.1604);
    console.assert(d > 10000 && d < 20000, '부산역-해운대 대략 14km: ' + Math.round(d));

    console.assert(!isDriving(2), '2m/s(7km/h)는 주행 아님');
    console.assert(isDriving(3), '3m/s(10.8km/h)는 주행');
    console.assert(!isDriving(null), '속도 없으면 주행 아님');

    /* 누적 거리와 스냅 — 안전 안내가 이 값 위에 선다 */
    var acc = cumulative(line);
    console.assert(acc.length === 2 && acc[0] === 0, '누적 첫 값은 0');
    console.assert(acc[1] > 700 && acc[1] < 900, '두 점 사이 대략 790m: ' + Math.round(acc[1]));
    var mid = snap(35.11555, 129.04565, line, acc);
    console.assert(mid.dist < 30, '선 위 점은 붙는다: ' + Math.round(mid.dist));
    console.assert(mid.meter > 300 && mid.meter < 500, '중간쯤이면 절반 지점: ' + Math.round(mid.meter));
    var head = snap(35.1151, 129.0413, line, acc);
    console.assert(head.meter < 5, '출발점은 0m: ' + Math.round(head.meter));

    /* 갈림길 가르기 — 실제 티맵 문구로 */
    console.assert(turnKind('교차로에서 좌회전 후 일반도로를 따라 22m 이동', 12) === 'left', '좌회전');
    console.assert(turnKind('해운대해수욕장 삼거리에서 송정 방면으로 우회전 후', 13) === 'right', '우회전');
    console.assert(turnKind('노보텔 앞 삼거리에서 2시 방향 우회전 후', 18) === 'slightR', '2시 방향은 완만한 우회전');
    console.assert(turnKind('일반도로를 따라 79m 이동', 200) === 'depart', '출발');
    console.assert(turnKind('목적지에 도착', 201) === 'arrive', '도착');
    console.assert(turnKind('유턴 후 직진', 14) === 'uturn', '유턴');

    /* 안내 문구 줄이기 */
    console.assert(shortDesc('교차로에서 좌회전 후 일반도로를 따라 22m 이동') === '교차로에서 좌회전', '앞부분만');
    console.assert(shortDesc('해운대해변로를 따라 324m 이동') === '해운대해변로', '따라 뒤를 자른다');

    /* 무엇을 하나 · 어느 길로 */
    console.assert(turnText({ description:'일반도로를 따라 79m 이동' }) === '직진', '길 이름만 있으면 직진');
    console.assert(turnText({ description:'교차로에서 좌회전 후 일반도로를 따라 22m 이동' }) === '교차로에서 좌회전', '회전은 그대로');
    console.assert(roadOf({ description:'우회전 후 해운대해변로298번길을 따라 199m 이동' }) === '해운대해변로298번길', '올라탈 길 이름');
    console.assert(roadOf({ nextRoadName:'센텀4로', description:'아무말' }) === '센텀4로', '서버가 주면 그걸 쓴다');
    console.assert(eul('해운대해변로') === '를', '받침 없으면 를');
    console.assert(eul('해운대해변로298번길') === '을', '받침 있으면 을');
    console.assert(speechFor({ description:'일반도로를 따라 79m 이동' }, 300) === '300미터 앞, 일반도로를 따라 직진하세요', '직진 문장: ' + speechFor({ description:'일반도로를 따라 79m 이동' }, 300));

    /* 차선 추천 */
    console.assert(laneAdvice('right', null, null).side === 'right', '우회전이면 오른쪽');
    console.assert(laneAdvice('left', null, null).side === 'left', '좌회전이면 왼쪽');
    console.assert(laneAdvice('straight', 'left', 300).side === 'left', '직진이어도 곧 좌회전이면 미리 왼쪽');
    console.assert(laneAdvice('straight', 'left', 300).early === true, '미리 표시');
    console.assert(laneAdvice('straight', 'left', 2000).side === 'center', '멀면 가운데');
    console.assert(laneAdvice('straight', null, null).side === 'center', '다음이 없으면 가운데');
    console.assert(laneOn('right', 3).join() === '2', '3차선 우회전은 3차로');
    console.assert(laneOn('right', 5).join() === '3,4', '5차선 우회전은 바깥 두 줄');
    console.assert(laneOn('left', 5).join() === '0,1', '5차선 좌회전은 안쪽 두 줄');
    console.assert(laneOn('center', 3).join() === '1', '3차선 가운데는 2차로');
    console.assert(laneOn('center', 4).join() === '1,2', '4차선 가운데는 2·3차로');

    /* 거리 표기 */
    console.assert(fmtM(1500) === '1.5km', 'km 표기: ' + fmtM(1500));
    console.assert(fmtM(320) === '320m', 'm 표기: ' + fmtM(320));
    console.assert(fmtM(317) === '320m', '10m 단위로 끊는다: ' + fmtM(317));

    console.log('OK _navCheck 통과');
    return true;
  };
})();

/* 음성 비서 「체키」 — 듣기(SpeechRecognition) → 챗봇 → 읽기(speechSynthesis)
 * 운전 중엔 말로만. 「체키야」로 부른 문장만 명령으로 본다.
 * iOS 사파리는 SpeechRecognition 이 없어 꾹 눌러 말하기 버튼으로 대체한다.
 * 자체검증: 콘솔에서 _voiceCheck()
 */
(function () {
  'use strict';

  var WAKE = '체키야';
  var vs = null; // 음성 상태

  /* ── 순수 판정 (자체검증 대상) ── */
  // 「체키야」로 부른 문장이면 명령부만 잘라 돌려준다. 아니면 null.
  function parseCommand(text) {
    if (!text) return null;
    var t = text.replace(/\s+/g, ' ').trim();
    var i = t.indexOf(WAKE);
    if (i === -1) return null;
    var cmd = t.slice(i + WAKE.length).replace(/^[\s,]+/, '').trim();
    return cmd.length ? cmd : null;
  }
  // 읽어 줄 답은 한 문장만 (운전 중엔 길면 놓친다)
  function firstSentence(text) {
    if (!text) return '';
    var m = text.replace(/\s+/g, ' ').trim().split(/(?<=[.?!。])\s|\n/);
    return (m[0] || '').trim();
  }

  function speak(text) {
    try {
      if (!('speechSynthesis' in window) || !text) return;
      var u = new SpeechSynthesisUtterance(text);
      u.lang = 'ko-KR'; u.rate = 1.05;
      window.speechSynthesis.speak(u);
    } catch (e) {}
  }

  /* ── 챗봇 왕복 ── */
  function ensureSession(cb) {
    if (vs.sessionId) { cb(vs.sessionId); return; }
    var headers = { 'Content-Type': 'application/json' };
    fetch('/api/chat/sessions', {
      method: 'POST', headers: headers,
      body: JSON.stringify({ planId: vs.tripId || null })
    }).then(function (r) { return r.json(); })
      .then(function (j) { vs.sessionId = j && j.data && j.data.sessionId; cb(vs.sessionId); })
    .catch(function () { speak('연결이 끊겼습니다. 다시 눌러 주세요.'); });
  }
  function ask(cmd) {
    ensureSession(function (sid) {
  if (!sid) { speak('연결이 끊겼습니다. 다시 눌러 주세요.'); return; }
      render('체키에게 물어보는 중...');
      fetch('/api/chat/message', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ sessionId: sid, message: cmd })
      }).then(function (r) { return r.json(); })
        .then(function (j) {
          var reply = (j && (j.data && (j.data.reply || j.data.message) || j.message)) || '';
          var one = firstSentence(reply) || '답을 받지 못했습니다.';
          render(one); speak(one);
        })
        .catch(function () { speak('답을 받지 못했습니다.'); });
    });
  }

  function heard(text) {
    var cmd = parseCommand(text);
    if (!cmd) return;              // 「체키야」 없는 말은 버린다
    render('“' + cmd + '”');
    ask(cmd);
  }

  /* ── 오버레이 ── */
  function render(text) {
    var h = document.getElementById('navVoiceMsg');
    if (h) h.textContent = text;
  }
  function ensureUi() {
    if (document.getElementById('navVoice')) return;
    var el = document.createElement('div');
    el.id = 'navVoice';
    el.className = 'rl-voice';
    el.innerHTML =
      '<div class="rl-voice-msg" id="navVoiceMsg">체키 대기 중</div>' +
      '<button type="button" class="rl-act" id="navMic">마이크 끄기</button>' +
      '<button type="button" class="rl-act" id="navPtt">꾹 눌러 말하기</button>';
    document.body.appendChild(el);
    document.getElementById('navMic').addEventListener('click', toggleMic);
    // iOS 등 상시인식 안 되는 기기용 — 누르는 동안만 듣기
    var ptt = document.getElementById('navPtt');
    ptt.addEventListener('mousedown', pttStart);
    ptt.addEventListener('mouseup', pttStop);
    ptt.addEventListener('touchstart', function (e) { e.preventDefault(); pttStart(); });
    ptt.addEventListener('touchend', function (e) { e.preventDefault(); pttStop(); });
  }

  function makeRecognizer(continuous) {
    var SR = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!SR) return null;
    var r = new SR();
    r.lang = 'ko-KR'; r.continuous = continuous; r.interimResults = false;
    r.onresult = function (e) {
      for (var i = e.resultIndex; i < e.results.length; i++) {
        if (e.results[i].isFinal) heard(e.results[i][0].transcript);
      }
    };
    return r;
  }

  function toggleMic() {
    if (!vs) return;
    if (vs.on) { stopListen(); } else { startListen(); }
  }
  function startListen() {
    var r = makeRecognizer(true);
    if (!r) {
      // 상시 인식 불가(iOS) — 꾹 눌러 말하기만 쓴다
    render('계속 듣기는 지원하지 않습니다. 꾹 눌러 말하세요.');
      document.getElementById('navMic').style.display = 'none';
      return;
    }
    vs.rec = r; vs.on = true;
    document.getElementById('navMic').textContent = '마이크 끄기';
    r.onend = function () { if (vs && vs.on) { try { r.start(); } catch (e) {} } }; // 끊기면 다시
    try { r.start(); render('체키 듣는 중'); } catch (e) {}
  }
  function stopListen() {
    if (vs && vs.rec) { vs.on = false; try { vs.rec.stop(); } catch (e) {} }
    var b = document.getElementById('navMic');
    if (b) b.textContent = '마이크 켜기';
    render('마이크 꺼짐');
  }
  // 꾹 눌러 말하기 — 누르는 동안 한 번만 듣기
  function pttStart() {
    var r = makeRecognizer(false);
    if (!r) { render('이 브라우저는 음성 인식을 지원하지 않습니다.'); return; }
    vs.ptt = r; try { r.start(); render('말하세요...'); } catch (e) {}
  }
  function pttStop() { if (vs && vs.ptt) { try { vs.ptt.stop(); } catch (e) {} } }

  window.startVoice = function (tripId) {
    vs = { tripId: tripId || null, sessionId: null, rec: null, ptt: null, on: false };
    ensureUi();
    render('체키 준비됨. 마이크를 켜거나 꾹 눌러 말하세요.');
  };
  window.stopVoice = function () {
    stopListen();
    try { window.speechSynthesis.cancel(); } catch (e) {}
    var el = document.getElementById('navVoice'); if (el) el.remove();
    vs = null;
  };

  /* ── 자체검증 ── */
  window._voiceCheck = function () {
    console.assert(parseCommand('체키야 다음 어디야') === '다음 어디야', '명령 추출');
    console.assert(parseCommand('체키야, 비 와?') === '비 와?', '쉼표 뒤 명령');
    console.assert(parseCommand('그냥 혼잣말') === null, '체키야 없으면 무시');
    console.assert(parseCommand('') === null, '빈 문자열 무시');
    console.assert(firstSentence('12분 줄어듭니다. 순서를 바꿀까요') === '12분 줄어듭니다.', '한 문장만');
    console.assert(firstSentence('짧은 답') === '짧은 답', '문장부호 없으면 통째');
    console.log('OK _voiceCheck 통과');
    return true;
  };
})();

