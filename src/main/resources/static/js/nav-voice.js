/* 안내 목소리 — 미리 만들어 둔 mp3 를 먼저 틀고, 없는 문장만 기기 목소리로.
 *
 * 파일은 tools/make_nav_voice.py 가 만든다. static/audio/nav/index.json 이
 * 「문장 → 파일」 표다. 실행 중에 바깥 음성 서비스를 부르지 않는다.
 *
 * say(parts, urgent, done)
 *   parts  문장 하나(문자열) 또는 조각 배열. 조각이 전부 파일이면 이어서 틀고,
 *          하나라도 없으면 통째로 기기 목소리로 읽는다 — 두 목소리가 섞이면 더 어색하다.
 *   urgent 지금 나오는 소리를 끊고 먼저 말한다. 갈림길이 안전 안내를 끊는 규칙과 같다.
 *   done   다 읽은 뒤 한 번 부른다. 읽는 중에 마이크를 열면 제 목소리를 알아듣는다.
 *
 * 읽는 동안 body 에 dv-speaking 이 붙는다. 영상엔 소리가 없으니 화면이 그걸 보여 준다.
 */
(function () {
  'use strict';

  var BASE = '/audio/nav/';
  var files = null;          /* 문장 → 파일 이름 */
  var q = [], cur = null, onDone = null, voice = null;
  /* 소리 칸은 하나만 쓴다. 아이폰은 누른 순간 한 번 튼 그 칸만 나중에도 틀어 준다 */
  var player = typeof Audio === 'function' ? new Audio() : null;

  fetch(BASE + 'index.json').then(function (r) { return r.ok ? r.json() : {}; })
    .then(function (j) { files = j || {}; }).catch(function () { files = {}; });

  /* 기기 목소리 — 한국어 중 자연 음성(Natural·Neural·Premium·Enhanced·Siri)을 먼저 */
  function pickVoice() {
    if (voice || !('speechSynthesis' in window)) return voice;
    var ko = window.speechSynthesis.getVoices().filter(function (v) { return /^ko/i.test(v.lang); });
    voice = ko.filter(function (v) { return /Natural|Neural|Premium|Enhanced|Siri/i.test(v.name); })[0] || ko[0] || null;
    return voice;
  }
  if ('speechSynthesis' in window) {
    try { window.speechSynthesis.addEventListener('voiceschanged', function () { voice = null; pickVoice(); }); } catch (e) {}
  }

  function speaking(on) { document.body.classList.toggle('dv-speaking', !!on); }

  function finish() {
    speaking(false);
    var cb = onDone; onDone = null;
    if (cb) cb();
  }
  function next() {
    var src = q.shift();
    if (!src || !player) { cur = null; finish(); return; }
    var a = player, mine = {};
    cur = mine;
    a.onended = a.onerror = function () { if (cur === mine) next(); };
    a.src = src;
    a.play().catch(function () { if (cur === mine) next(); });
  }
  function tts(text) {
    try {
      if (!('speechSynthesis' in window)) { finish(); return; }
      var u = new SpeechSynthesisUtterance(text);
      u.lang = 'ko-KR'; u.rate = 1.05;
      var v = pickVoice(); if (v) u.voice = v;
      u.onend = u.onerror = finish;
      window.speechSynthesis.speak(u);
    } catch (e) { finish(); }
  }

  function stop() {
    q = [];
    if (cur) { cur = null; try { player.pause(); } catch (e) {} }
    try { if ('speechSynthesis' in window) window.speechSynthesis.cancel(); } catch (e) {}
    onDone = null;
    speaking(false);
  }

  function say(parts, urgent, done) {
    var list = Array.isArray(parts) ? parts : [parts];
    list = list.filter(function (p) { return p; });
    if (!list.length) { if (done) done(); return; }
    if (urgent) stop();
    else if (cur || (window.speechSynthesis && window.speechSynthesis.speaking)) { stop(); }
    onDone = done || null;
    speaking(true);
    var f = files || {};
    var srcs = list.map(function (p) { return f[p] ? BASE + f[p] : null; });
    if (srcs.every(Boolean)) { q = srcs; next(); }
    else tts(list.join(', '));
  }

  window.navVoice = { say: say, stop: stop, busy: function () { return !!cur; } };
})();
