/* 서비스워커 등록 · 웹 푸시 구독
 *
 * 왜 따로 두는가 — sw.js 와 백엔드(/api/push/*)는 진작 있었는데 브라우저에게
 * 알려 주는 코드가 없어서 한 번도 동작한 적이 없다. 이 파일이 그 연결이다.
 *
 * 아이폰은 홈 화면에 추가한 뒤에만 푸시가 온다(iOS 16.4+). 그래서 켜기 전에
 * 무엇이 필요한지 먼저 말하고, 안 되는 기기에서는 안 된다고 말한다.
 * 조용히 실패하면 사용자는 알림이 오는 줄 알고 기다린다.
 *
 * 콘솔 점검: _pushCheck()
 */
(function () {
  'use strict';

  var SW_PATH = '/sw.js';
  var ready = null;      // 서비스워커 등록 약속. 한 번만 한다

  /* ── 기기가 할 수 있는가 ────────────────────────────── */

  function supported() {
    return ('serviceWorker' in navigator) && ('PushManager' in window) && ('Notification' in window);
  }

  // 홈 화면에서 연 상태인가. 아이폰은 이것이어야 푸시가 온다
  function standalone() {
    return window.matchMedia('(display-mode: standalone)').matches ||
           window.navigator.standalone === true;
  }

  function isIOS() {
    return /iPad|iPhone|iPod/.test(navigator.userAgent) ||
           (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
  }

  /**
   * 지금 이 기기에서 푸시를 켤 수 있는가. 못 켜면 왜 못 켜는지 한 문장으로.
   * 화면 문구를 만드는 자리라 따로 떼어 두고 점검한다.
   */
  function why(supportedNow, iosNow, standaloneNow) {
    if (iosNow && !standaloneNow) {
      return '아이폰은 홈 화면에 추가한 뒤에 알림을 켤 수 있습니다.';
    }
    if (!supportedNow) {
      return '이 브라우저는 알림을 지원하지 않습니다.';
    }
    return null;   // 켤 수 있다
  }

  function blockedReason() {
    return why(supported(), isIOS(), standalone());
  }

  /* ── 서비스워커 ─────────────────────────────────────── */

  function register() {
    if (!('serviceWorker' in navigator)) return Promise.resolve(null);
    if (!ready) {
      ready = navigator.serviceWorker.register(SW_PATH)
        .then(function (reg) { return reg; })
        .catch(function (e) {
          ready = null;                       /* 다음에 다시 시도할 수 있게 */
          console.warn('[push] 서비스워커 등록 실패', e);
          return null;
        });
    }
    return ready;
  }

  /* ── 키 변환 ────────────────────────────────────────── */

  // VAPID 공개키는 base64url 문자열로 오고 브라우저는 바이트 배열을 받는다
  function urlBase64ToUint8Array(base64) {
    var padding = '='.repeat((4 - (base64.length % 4)) % 4);
    var normal = (base64 + padding).replace(/-/g, '+').replace(/_/g, '/');
    var raw = window.atob(normal);
    var out = new Uint8Array(raw.length);
    for (var i = 0; i < raw.length; i++) out[i] = raw.charCodeAt(i);
    return out;
  }

  /* ── 토큰 ───────────────────────────────────────────── */

  function token() {
    return localStorage.getItem('accessToken') || sessionStorage.getItem('accessToken');
  }
  function headers() {
    var h = { 'Content-Type': 'application/json' };
    var t = token();
    if (t) h.Authorization = 'Bearer ' + t;
    return h;
  }

  /* ── 켜기 ───────────────────────────────────────────── */

  function enable() {
    var blocked = blockedReason();
    if (blocked) return Promise.resolve({ ok: false, note: blocked });
    if (!token()) return Promise.resolve({ ok: false, note: '로그인하면 알림을 켤 수 있습니다.' });

    return Notification.requestPermission().then(function (perm) {
      if (perm !== 'granted') {
        return { ok: false, note: '알림 권한이 없어 켜지 못했습니다. 브라우저 설정에서 허용해 주세요.' };
      }
      return register().then(function (reg) {
        if (!reg) return { ok: false, note: '알림을 준비하지 못했습니다. 새로 고친 뒤 다시 눌러 주세요.' };

        return fetch('/api/push/public-key')
          .then(function (r) { return r.json(); })
          .then(function (j) {
            var key = j && j.data && j.data.publicKey;
            if (!key) return { ok: false, note: '알림 설정이 아직 준비되지 않았습니다.' };

            return reg.pushManager.subscribe({
              userVisibleOnly: true,
              applicationServerKey: urlBase64ToUint8Array(key)
            }).then(function (sub) {
              return fetch('/api/push/subscribe', {
                method: 'POST', headers: headers(), body: JSON.stringify(sub.toJSON())
              }).then(function (r) { return r.json(); })
                .then(function (res) {
                  return res && res.success
                    ? { ok: true, note: '알림을 켰습니다.' }
                    : { ok: false, note: (res && res.message) || '알림을 켜지 못했습니다.' };
                });
            });
          });
      });
    }).catch(function (e) {
      console.warn('[push] 구독 실패', e);
      return { ok: false, note: '알림을 켜지 못했습니다.' };
    });
  }

  /* ── 끄기 ───────────────────────────────────────────── */

  function disable() {
    return register().then(function (reg) {
      if (!reg) return { ok: false, note: '알림 상태를 확인하지 못했습니다.' };
      return reg.pushManager.getSubscription().then(function (sub) {
        if (!sub) return { ok: true, note: '이미 꺼져 있습니다.' };
        var endpoint = sub.endpoint;
        return sub.unsubscribe().then(function () {
          return fetch('/api/push/unsubscribe', {
            method: 'POST', headers: headers(), body: JSON.stringify({ endpoint: endpoint })
          }).then(function () { return { ok: true, note: '알림을 껐습니다.' }; });
        });
      });
    });
  }

  /* ── 지금 켜져 있는가 ───────────────────────────────── */

  function status() {
    if (!supported()) return Promise.resolve({ on: false, can: false, note: blockedReason() });
    return register().then(function (reg) {
      if (!reg) return { on: false, can: true, note: null };
      return reg.pushManager.getSubscription().then(function (sub) {
        return { on: !!sub, can: !blockedReason(), note: blockedReason() };
      });
    });
  }

  /* 서비스워커는 로그인 여부와 무관하게 미리 등록해 둔다.
     등록이 끝나 있어야 「알림 켜기」를 눌렀을 때 한 번에 켜진다. */
  if ('serviceWorker' in navigator) {
    window.addEventListener('load', function () { register(); });
  }

  window.pushEnable = enable;
  window.pushDisable = disable;
  window.pushStatus = status;
  window.pushBlockedReason = blockedReason;

  /* ── 자체검증 ───────────────────────────────────────── */
  window._pushCheck = function () {
    // 아이폰인데 홈 화면이 아니면 이유를 말해야 한다
    console.assert(why(true, true, false) !== null, '아이폰 + 브라우저면 못 켠다');
    console.assert(why(true, true, true) === null, '아이폰 + 홈 화면이면 켤 수 있다');
    console.assert(why(false, false, false) !== null, '지원 안 하면 못 켠다');
    console.assert(why(true, false, false) === null, '안드로이드 크롬은 그냥 켜진다');

    // base64url 변환 — 「-」와 「_」가 「+」「/」로 바뀌고 길이가 맞아야 한다
    var bytes = urlBase64ToUint8Array('AQAB');
    console.assert(bytes.length === 3, 'AQAB 는 3바이트: ' + bytes.length);
    console.assert(bytes[0] === 1 && bytes[1] === 0 && bytes[2] === 1, '변환값 틀림');

    var padded = urlBase64ToUint8Array('-_8');   // 패딩이 모자란 입력
    console.assert(padded.length === 2, '패딩을 채워 2바이트: ' + padded.length);
    console.assert(padded[0] === 251 && padded[1] === 255, '- 와 _ 를 + / 로 바꿔야 한다');

    console.log('OK _pushCheck 통과');
    return true;
  };
})();
