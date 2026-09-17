/* 주행모드 — 위치추적 · 경로이탈 재탐색 · 턴바이턴 음성 · Wake Lock
 * page_live 의 「길 안내」에서 startDrive(dest) 로 들어온다.
 * 지도는 없어도 된다 — 음성 우선(운전 중엔 화면을 보지 않는다).
 * 자체검증: 콘솔에서 _navCheck()
 */
(function () {
  'use strict';

  var OFF_METERS = 50;      // 경로선에서 이 이상 벗어나면
  var OFF_STREAK = 3;       // 이만큼 연속이면 이탈 (GPS 튐 방지)
  var SPEAK_AT = [500, 200, 50]; // 안내지점까지 이 거리에서 한 번씩 읽는다

  var st = null; // 주행 상태

  /* ── 순수 기하 (자체검증 대상) ── */
  function haversine(aLat, aLng, bLat, bLng) {
    var R = 6371000, toR = Math.PI / 180;
    var dLat = (bLat - aLat) * toR, dLng = (bLng - aLng) * toR;
    var la1 = aLat * toR, la2 = bLat * toR;
    var h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
          + Math.cos(la1) * Math.cos(la2) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
    return 2 * R * Math.asin(Math.min(1, Math.sqrt(h)));
  }
  // 점→선분 최단거리(m). 등거리 근사(도 단위를 m 로 환산)
  function distToSeg(pLat, pLng, aLat, aLng, bLat, bLng) {
    var toR = Math.PI / 180, latM = 111320, lngM = 111320 * Math.cos(pLat * toR);
    var px = pLng * lngM, py = pLat * latM;
    var ax = aLng * lngM, ay = aLat * latM;
    var bx = bLng * lngM, by = bLat * latM;
    var dx = bx - ax, dy = by - ay;
    var len2 = dx * dx + dy * dy;
    var t = len2 === 0 ? 0 : ((px - ax) * dx + (py - ay) * dy) / len2;
    t = Math.max(0, Math.min(1, t));
    var cx = ax + t * dx, cy = ay + t * dy;
    return Math.hypot(px - cx, py - cy);
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
  // 연속 이탈 판정 — 거리와 직전 연속횟수를 받아 새 횟수와 이탈여부를 돌려준다
  function offStreak(dist, prevStreak) {
    var s = dist > OFF_METERS ? prevStreak + 1 : 0;
    return { streak: s, off: s >= OFF_STREAK };
  }

  /* ── 음성 ── */
  function speak(text) {
    try {
      if (!('speechSynthesis' in window)) return;
      var u = new SpeechSynthesisUtterance(text);
      u.lang = 'ko-KR'; u.rate = 1.05;
      window.speechSynthesis.speak(u);
    } catch (e) { /* 음성 안 되면 화면 텍스트로만 */ }
  }

  /* ── Wake Lock — 화면 꺼지면 추적 멈춘다 ── */
  function acquireWake() {
    if (!('wakeLock' in navigator)) return;
    navigator.wakeLock.request('screen').then(function (l) {
      st.wake = l;
      l.addEventListener('release', function () { st.wake = null; });
    }).catch(function () { /* 권한 없으면 넘어간다 */ });
  }
  function onVisible() {
    if (st && document.visibilityState === 'visible' && !st.wake) acquireWake();
  }

  /* ── 경로 로드 (백엔드 navi) ── */
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
          render('경로를 불러오지 못했습니다.');
          return;
        }
        st.route = d; st.guideIdx = 0; st.spoken = {};
        render(headText(d));
        speak(headText(d));
        if (cb) cb();
      })
      .catch(function () { render('경로를 불러오지 못했습니다.'); });
  }
  function headText(d) {
    var km = (d.totalMeters / 1000).toFixed(1);
    var min = Math.round(d.totalSeconds / 60);
    return min + '분, ' + km + '킬로미터 남았습니다.';
  }

  /* ── 위치 갱신 ── */
  function onPos(p) {
    st.pos = { lat: p.coords.latitude, lng: p.coords.longitude };
    if (!st.route) return;

    // 이탈 판정
    var dist = distToLine(st.pos.lat, st.pos.lng, st.route.line);
    var r = offStreak(dist, st.offStreak);
    st.offStreak = r.streak;
    if (r.off) {
      st.offStreak = 0;
      speak('경로를 벗어나 다시 안내합니다.');
      render('경로 재탐색 중...');
      loadRoute();
      return;
    }

    // 턴바이턴 — 다음 안내점까지 거리로 읽기
    var guides = st.route.guides || [];
    if (st.guideIdx < guides.length) {
      var g = guides[st.guideIdx];
      var gd = haversine(st.pos.lat, st.pos.lng, g.lat, g.lng);
      if (gd <= 20) { st.guideIdx++; return; } // 지나감
      for (var i = 0; i < SPEAK_AT.length; i++) {
        var th = SPEAK_AT[i];
        var key = st.guideIdx + ':' + th;
        if (gd <= th && !st.spoken[key]) {
          st.spoken[key] = true;
          var msg = th + '미터 앞, ' + g.description;
          render(msg);
          speak(msg);
          break;
        }
      }
    }
  }

  /* ── 오버레이 UI (자체 주입, rl- 접두어) ── */
  function ensureOverlay() {
    var el = document.getElementById('navDrive');
    if (el) return el;
    el = document.createElement('div');
    el.id = 'navDrive';
    el.className = 'rl-drive';
    el.innerHTML =
      '<div class="rl-drive-head" id="navHead">주행 준비 중</div>' +
      '<button type="button" class="rl-act" id="navStop">안내 종료</button>';
    document.body.appendChild(el);
    document.getElementById('navStop').addEventListener('click', stopDrive);
    return el;
  }
  function render(text) {
    var h = document.getElementById('navHead');
    if (h) h.textContent = text;
  }

  /* ── 시작/종료 ── */
  function startDrive(dest) {
    if (!dest || dest.lat == null || dest.lng == null) {
      alert('다음 목적지 좌표가 없어 길 안내를 시작할 수 없습니다.');
      return;
    }
    if (!navigator.geolocation) { alert('이 브라우저는 위치를 지원하지 않습니다.'); return; }
    st = { dest: dest, pos: null, route: null, offStreak: 0, guideIdx: 0, spoken: {}, wake: null, watchId: null };
    ensureOverlay();
    render('현재 위치를 잡는 중입니다.');
    acquireWake();
    document.addEventListener('visibilitychange', onVisible);
    st.watchId = navigator.geolocation.watchPosition(
      function (p) {
        var first = !st.pos;
        st.pos = { lat: p.coords.latitude, lng: p.coords.longitude };
        if (first) loadRoute(); else onPos(p);
      },
      function () { render('위치 권한이 없어 안내할 수 없습니다.'); },
      { enableHighAccuracy: true, maximumAge: 1000, timeout: 10000 }
    );
  }
  function stopDrive() {
    if (!st) return;
    if (st.watchId != null) navigator.geolocation.clearWatch(st.watchId);
    if (st.wake) { try { st.wake.release(); } catch (e) {} }
    document.removeEventListener('visibilitychange', onVisible);
    try { window.speechSynthesis.cancel(); } catch (e) {}
    var el = document.getElementById('navDrive');
    if (el) el.remove();
    st = null;
  }

  window.startDrive = startDrive;
  window.stopDrive = stopDrive;

  /* ── 자체검증 ── */
  window._navCheck = function () {
    // 경로선: 부산역 근처 두 점
    var line = [[35.1151, 129.0413], [35.1160, 129.0500]];
    // 선 위 점 → 이탈 아님
    var on = distToLine(35.1155, 129.0450, line);
    console.assert(on < 50, '선 위 점은 50m 이내: ' + on);
    // 먼 점 → 이탈 거리
    var off = distToLine(35.2000, 129.0450, line);
    console.assert(off > 50, '먼 점은 50m 초과: ' + off);
    // 3연속 판정
    var s = 0, res;
    res = offStreak(80, s); s = res.streak; console.assert(!res.off, '1회는 이탈 아님');
    res = offStreak(80, s); s = res.streak; console.assert(!res.off, '2회는 이탈 아님');
    res = offStreak(80, s); s = res.streak; console.assert(res.off, '3회 연속은 이탈');
    res = offStreak(10, s); s = res.streak; console.assert(s === 0 && !res.off, '복귀하면 초기화');
    // 거리 계산 대략치
    var d = haversine(35.1151, 129.0413, 35.1587, 129.1604);
    console.assert(d > 10000 && d < 20000, '부산역-해운대 대략 14km: ' + Math.round(d));
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
      .catch(function () { speak('연결에 실패했습니다.'); });
  }
  function ask(cmd) {
    ensureSession(function (sid) {
      if (!sid) { speak('연결에 실패했습니다.'); return; }
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
      render('이 기기는 상시 인식이 안 됩니다. 꾹 눌러 말하세요.');
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

