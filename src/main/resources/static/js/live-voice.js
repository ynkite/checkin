/* ══════════════════════════════════════════════════════════════
 *  말로 하기 — 실시간 화면
 *
 *  「체크인」이라고 부르면 답하고, 그다음 말을 알아듣는다.
 *  운전 중이거나 걷는 중에는 화면을 못 본다. 그때 쓰라고 만든 것이다.
 *
 *  ── 정한 것 넷 ───────────────────────────────────────────────
 *
 *  1. 켠 동안만 듣는다. 늘 듣지 않는다.
 *     브라우저는 화면을 벗어나면 어차피 못 듣고, 마이크를 계속 열어 두면
 *     배터리를 먹는다. 무엇보다 사용자가 켠 적 없는 마이크가 돌면 안 된다.
 *
 *  2. 말로는 일정을 바꾸지 않는다. 바꾸자고 제안만 한다.
 *     「순서 바꿔」가 「순서 바꿔 줘」로도 「손서 바꿔」로도 들린다.
 *     잘못 들은 말로 남의 여행이 바뀌면 되돌릴 수 있어도 늦다.
 *     실제 실행은 rlApplyLive 가 한 번 더 묻는다.
 *
 *  3. 소리로 하는 말은 화면에도 적는다.
 *     차 안에서 음악이 나오면 안 들린다. 소리를 못 듣는 사람도 있다.
 *     소리는 거들 뿐이고, 화면이 본문이다.
 *
 *  4. 안 되는 기기에서는 안 된다고 말한다.
 *     iOS 사파리는 SpeechRecognition 이 없다. 단추만 띄워 놓고 아무 일도
 *     안 일어나면 고장으로 보인다. 왜 안 되는지 적고 무엇으로 대신할지 말한다.
 *
 *  ── 아는 말 ─────────────────────────────────────────────────
 *  먼저 이 자리에서 답할 수 있는 말인지 본다(다음 어디 · 붐벼 · 날씨 · 안내).
 *  네트워크를 안 타서 빠르고, 잘못 알아들을 여지도 적다.
 *  거기 없으면 챗봇에게 넘긴다.
 * ══════════════════════════════════════════════════════════════ */
(function () {
  'use strict';

  var SR = window.SpeechRecognition || window.webkitSpeechRecognition;
  var WAKE = ['체크인', '체크 인', '책인', '체킨'];   /* 잘못 들리는 꼴도 같이 받는다 */
  var LISTEN_MS = 8000;      /* 부른 뒤 이만큼 기다렸다 다시 잠든다 */

  var st = {
    on: false, rec: null, awake: false, awakeUntil: 0,
    sessionId: null, busy: false, restart: true
  };

  /* ── 화면에 적기 ───────────────────────────────────────── */

  function box() { return document.getElementById('rlVoiceLog'); }

  function say(who, text, kind) {
    var b = box();
    if (!b) return;
    b.hidden = false;
    var row = document.createElement('p');
    row.className = 'rv-row rv-' + who + (kind ? ' rv-' + kind : '');
    row.textContent = text;
    b.appendChild(row);
    while (b.children.length > 6) b.removeChild(b.firstChild);
    b.scrollTop = b.scrollHeight;
  }

  /* 소리와 글자를 같이 낸다. 하나가 빠져도 뜻이 전해져야 한다 */
  function reply(text, kind) {
    say('bot', text, kind);
    try {
      if (!('speechSynthesis' in window)) return;
      window.speechSynthesis.cancel();
      var u = new SpeechSynthesisUtterance(text);
      u.lang = 'ko-KR'; u.rate = 1.05;
      window.speechSynthesis.speak(u);
    } catch (e) { /* 소리가 안 나도 글자는 남는다 */ }
  }

  function badge(text, live) {
    var el = document.getElementById('rlVoiceState');
    if (!el) return;
    el.textContent = text;
    el.classList.toggle('rv-live', !!live);
  }

  /* ── 이 자리에서 답할 수 있는 말 ───────────────────────── */

  function snap() { return (window.rlSnapshot && window.rlSnapshot()) || {}; }

  function has(s) {
    for (var i = 1; i < arguments.length; i++) {
      if (s.indexOf(arguments[i]) >= 0) return true;
    }
    return false;
  }

  /** 답했으면 true. 못 알아들었으면 false 를 주고 챗봇에게 넘긴다 */
  function local(cmd) {
    var d = snap();
    var n = d.next || {};

    if (has(cmd, '그만', '멈춰', '꺼', '중지')) {
      reply('말로 하기를 끕니다.');
      setTimeout(stop, 900);
      return true;
    }

    if (has(cmd, '다음', '어디로', '어디야', '어디에요', '어디예요')) {
      reply(n.name
        ? '다음은 ' + n.name + '입니다.' + (n.time ? ' ' + n.time + ' 예정입니다.' : '')
        : '오늘 남은 일정이 없습니다.');
      return true;
    }

    if (has(cmd, '붐', '혼잡', '사람 많', '사람많')) {
      var c = d.crowd || {};
      reply(c.rate != null
        ? (n.name || '다음 장소') + '은 지금 ' + (c.levelLabel || '') +
          ', 백 점 기준 ' + Math.round(c.rate) + '입니다.'
        : '그곳 혼잡도는 아직 확인되지 않았습니다.');
      return true;
    }

    if (has(cmd, '날씨', '비 와', '비와', '비 오', '비오', '우산', '더워', '추워')) {
      var w = d.weather || {};
      var bits = [];
      if (w.label || w.sky) bits.push(w.label || w.sky);
      if (w.rainProb != null) bits.push('비 올 확률 ' + w.rainProb + '퍼센트');
      if (w.tempMin != null && w.tempMax != null) {
        bits.push(Math.round(w.tempMin) + '도에서 ' + Math.round(w.tempMax) + '도');
      }
      reply(bits.length ? bits.join(', ') + '입니다.' : '날씨를 받아 오지 못했습니다.');
      return true;
    }

    if (has(cmd, '안내', '길 알려', '내비', '네비', '출발')) {
      if (!n.lat) { reply('다음 장소 좌표가 없어 길 안내를 켤 수 없습니다.'); return true; }
      reply(n.name + '까지 안내를 켭니다.');
      if (window.rlDo) window.rlDo('navi');
      return true;
    }

    if (has(cmd, '확인')) {
      reply('다음 장소를 확인해 보겠습니다.');
      if (window.rlCheck) window.rlCheck();
      return true;
    }

    /* 일정을 바꾸는 말. 여기서 바로 하지 않는다 —
       잘못 들은 말로 남의 여행이 바뀌면 되돌릴 수 있어도 늦다.
       화면에 단추를 띄우고 손으로 누르게 한다. */
    if (has(cmd, '순서', '미뤄', '나중에')) return propose('swap', '순서 바꾸기');
    if (has(cmd, '다른 곳', '다른데', '딴 데', '바꿔 줘', '바꿔줘')) return propose('alt', '다른 곳으로');
    if (has(cmd, '가는 길', '경로', '길 바꿔')) return propose('route', '가는 길 바꾸기');

    return false;
  }

  function propose(key, label) {
    var el = document.getElementById('rlVoiceAsk');
    if (!el) return false;
    reply(label + '를 할까요? 화면에서 눌러 주세요.', 'ask');
    el.hidden = false;
    el.innerHTML = '';
    var yes = document.createElement('button');
    yes.type = 'button'; yes.className = 'rv-yes'; yes.textContent = label;
    yes.onclick = function () { el.hidden = true; if (window.rlDo) window.rlDo(key); };
    var no = document.createElement('button');
    no.type = 'button'; no.className = 'rv-no'; no.textContent = '아니요';
    no.onclick = function () { el.hidden = true; };
    el.appendChild(yes); el.appendChild(no);
    return true;
  }

  /* ── 챗봇에게 넘기기 ───────────────────────────────────── */

  function tok() {
    return localStorage.getItem('accessToken') || sessionStorage.getItem('accessToken') || '';
  }
  function auth() {
    var t = tok();
    var h = { 'Content-Type': 'application/json' };
    if (t) h.Authorization = 'Bearer ' + t;
    return h;
  }

  async function ask(cmd) {
    if (st.busy) return;
    st.busy = true;
    badge('생각하는 중', true);
    try {
      if (!st.sessionId) {
        var d = snap();
        var r0 = await fetch('/api/chat/sessions', {
          method: 'POST', headers: auth(),
          body: JSON.stringify({ planId: d.tripId || null })
        });
        var j0 = await r0.json();
        st.sessionId = j0 && j0.data && j0.data.sessionId;
      }
      if (!st.sessionId) throw new Error('세션 없음');

      var p = window.rlPosition && window.rlPosition();
      var body = { sessionId: st.sessionId, message: cmd };
      if (p) { body.lat = p[0]; body.lng = p[1]; }

      var r = await fetch('/api/chat/message', {
        method: 'POST', headers: auth(), body: JSON.stringify(body)
      });
      var j = await r.json();
      var text = j && j.data && (j.data.reply || j.data.response);
      /* 못 받았으면 못 받았다고 한다. 지어내지 않는다 */
      reply(text && String(text).trim() ? String(text).trim() : '답을 받아 오지 못했습니다.');
    } catch (e) {
      reply('지금은 답을 받아 오지 못했습니다. 연결을 확인해 주세요.', 'err');
    } finally {
      st.busy = false;
      badge(st.awake ? '듣는 중' : '「체크인」 하고 말씀하세요', st.awake);
    }
  }

  /* ── 듣기 ──────────────────────────────────────────────── */

  function heard(text) {
    var t = (text || '').replace(/\s+/g, ' ').trim();
    if (!t) return;

    if (!st.awake) {
      var hit = -1;
      for (var i = 0; i < WAKE.length; i++) {
        var at = t.indexOf(WAKE[i]);
        if (at >= 0) { hit = at + WAKE[i].length; break; }
      }
      if (hit < 0) return;                 /* 부르지 않았으면 흘려보낸다 */
      st.awake = true;
      st.awakeUntil = Date.now() + LISTEN_MS;
      badge('듣는 중', true);

      /* 「체크인, 다음 어디야」처럼 한 번에 말했으면 뒷말을 그대로 쓴다 */
      var rest = t.slice(hit).replace(/^[\s,.·?!]+/, '');
      if (rest) { run(rest); return; }
      reply('네, 말씀하세요.');
      return;
    }

    if (Date.now() > st.awakeUntil) { sleep(); return; }
    run(t);
  }

  function run(cmd) {
    say('me', cmd);
    st.awakeUntil = Date.now() + LISTEN_MS;
    if (!local(cmd)) ask(cmd);
    setTimeout(function () { if (Date.now() > st.awakeUntil) sleep(); }, LISTEN_MS + 200);
  }

  function sleep() {
    st.awake = false;
    badge('「체크인」 하고 말씀하세요', false);
  }

  /* ── 켜고 끄기 ─────────────────────────────────────────── */

  function start() {
    if (!SR) return;
    var rec = new SR();
    rec.lang = 'ko-KR';
    rec.continuous = true;
    rec.interimResults = false;   /* 중간 결과는 안 쓴다. 헛말로 움직이면 안 된다 */
    rec.maxAlternatives = 1;

    rec.onresult = function (e) {
      for (var i = e.resultIndex; i < e.results.length; i++) {
        if (e.results[i].isFinal) heard(e.results[i][0].transcript);
      }
    };
    rec.onerror = function (e) {
      if (e.error === 'not-allowed' || e.error === 'service-not-allowed') {
        st.restart = false;
        say('bot', '마이크를 쓸 수 없습니다. 브라우저 주소창의 자물쇠에서 마이크를 허용해 주세요. ' +
                   '그때까지는 화면의 단추로 하시면 됩니다.', 'err');
        stop();
      }
      /* no-speech·aborted 는 흔하다. onend 가 알아서 다시 켠다 */
    };
    rec.onend = function () {
      /* 브라우저가 몇십 초마다 스스로 끊는다. 켜 둔 동안에는 다시 붙인다 */
      if (st.on && st.restart) {
        try { rec.start(); } catch (e) { /* 이미 돌고 있으면 넘어간다 */ }
      }
    };

    try { rec.start(); } catch (e) { return; }
    st.rec = rec;
    st.on = true;
    st.restart = true;
    sleep();

    var w = document.getElementById('rlVoice');
    if (w) w.hidden = false;
    var btn = document.getElementById('rlVoiceBtn');
    if (btn) { btn.classList.add('on'); btn.textContent = '말로 하기 끄기'; }

    /* iOS 는 사용자가 누른 그 순간의 speak() 만 허용한다. 여기서 소리를 깨워 둔다 */
    reply('말로 하기를 켰습니다. 「체크인」 하고 말씀하세요.');
  }

  function stop() {
    st.on = false; st.restart = false; st.awake = false;
    if (st.rec) { try { st.rec.stop(); } catch (e) {} st.rec = null; }
    try { window.speechSynthesis.cancel(); } catch (e) {}
    var btn = document.getElementById('rlVoiceBtn');
    if (btn) { btn.classList.remove('on'); btn.textContent = '말로 하기'; }
    badge('꺼짐', false);
    var ask2 = document.getElementById('rlVoiceAsk');
    if (ask2) ask2.hidden = true;
  }

  window.rlVoiceToggle = function () {
    if (st.on) { stop(); return; }
    if (!SR) {
      /* 단추만 띄워 놓고 아무 일도 안 일어나면 고장으로 보인다 */
      var w = document.getElementById('rlVoice');
      if (w) w.hidden = false;
      say('bot', '이 브라우저는 음성 인식을 지원하지 않습니다. ' +
                 '아이폰 사파리가 그렇습니다. 크롬으로 열면 됩니다. ' +
                 '그때까지는 아래 단추로 하시면 같은 일을 할 수 있습니다.', 'err');
      badge('이 기기에서는 안 됩니다', false);
      return;
    }
    start();
  };

  window.rlVoiceStop = stop;

  /**
   * 같은 말을 글로 넣는 길.
   *
   * 아이폰 사파리에는 SpeechRecognition 이 없다. 거기서는 말로 못 한다.
   * 그렇다고 이 기능을 통째로 못 쓰게 두면, 「체크인 하고 말하세요」를
   * 읽기만 하고 아무것도 못 하는 화면이 된다.
   *
   * 해석하는 곳은 하나다. 들어오는 길만 둘이다 —
   * 규칙이 두 벌이 되면 한쪽만 고쳐져서 말과 글이 다르게 동작한다.
   */
  window.rlVoiceSay = function (text) {
    var t = String(text || '').trim();
    if (!t) return;
    var w = document.getElementById('rlVoice');
    if (w) w.hidden = false;
    run(t);
  };

  window.rlVoiceSend = function () {
    var i = document.getElementById('rlVoiceText');
    if (!i || !i.value.trim()) return;
    var v = i.value; i.value = '';
    window.rlVoiceSay(v);
  };

  window.rlVoiceKey = function (e) {
    if (e && e.key === 'Enter') { e.preventDefault(); window.rlVoiceSend(); }
  };

  /* 실시간 화면을 벗어나면 마이크를 놓는다. 켠 채로 두면 안 된다 */
  document.addEventListener('visibilitychange', function () {
    if (document.visibilityState !== 'visible' && st.on) stop();
  });
})();
