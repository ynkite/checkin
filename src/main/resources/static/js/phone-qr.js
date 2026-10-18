/*
 * 휴대폰 이어 보기 — PC 에서 짠 여행을 휴대폰 카메라로 바로 연다.
 * 주소는 /?trip=번호. 받는 쪽은 app_main.js 초기화가 처리한다
 * (로그인돼 있으면 그 여행 지도로, 아니면 로그인한 뒤에).
 *
 * QR 은 라이브러리 없이 여기서 그린다. 바이트 모드 · 오류 정정 L · 1~5형만 —
 * 106바이트까지 담기고, 여행 주소는 40자 안팎이라 그걸로 된다.
 * 순서는 표준(ISO/IEC 18004) 그대로다. 마스크는 0번 하나로 고정한다
 * (어떤 마스크든 읽는 쪽은 형식 정보를 보고 푼다).
 */
(function () {
  var TOTAL = [0, 26, 44, 70, 100, 134];   /* 형별 전체 코드어 */
  var ECC   = [0, 7, 10, 15, 20, 26];      /* L 오류 정정 코드어 (1~5형은 한 블록) */

  function gfMul(x, y) {
    var z = 0;
    for (var i = 7; i >= 0; i--) {
      z = (z << 1) ^ ((z >>> 7) * 0x11d);
      z ^= ((y >>> i) & 1) * x;
    }
    return z & 0xff;
  }
  function rsRemainder(data, deg) {
    var div = [], root = 1, i, j;
    for (i = 0; i < deg; i++) div.push(i === deg - 1 ? 1 : 0);
    for (i = 0; i < deg; i++) {
      for (j = 0; j < deg; j++) {
        div[j] = gfMul(div[j], root);
        if (j + 1 < deg) div[j] ^= div[j + 1];
      }
      root = gfMul(root, 2);
    }
    var rem = div.map(function () { return 0; });
    data.forEach(function (b) {
      var f = b ^ rem.shift();
      rem.push(0);
      div.forEach(function (c, k) { rem[k] ^= gfMul(c, f); });
    });
    return rem;
  }

  function encode(text) {
    var bytes = Array.from(new TextEncoder().encode(text));
    var ver = 1;
    while (ver <= 5 && (TOTAL[ver] - ECC[ver]) * 8 < 12 + bytes.length * 8) ver++;
    if (ver > 5) throw new Error('주소가 너무 깁니다');
    var cap = (TOTAL[ver] - ECC[ver]) * 8;

    /* 데이터 비트: 모드 0100 · 길이 8비트 · 바이트 · 끝표시 · 채움 */
    var bits = [];
    function put(v, n) { for (var i = n - 1; i >= 0; i--) bits.push((v >>> i) & 1); }
    put(4, 4); put(bytes.length, 8);
    bytes.forEach(function (b) { put(b, 8); });
    put(0, Math.min(4, cap - bits.length));
    put(0, (8 - bits.length % 8) % 8);
    for (var p = 0; bits.length < cap; p ^= 1) put(p ? 0x11 : 0xec, 8);
    var data = [];
    for (var i = 0; i < bits.length; i += 8) {
      var v = 0;
      for (var k = 0; k < 8; k++) v = (v << 1) | bits[i + k];
      data.push(v);
    }
    data = data.concat(rsRemainder(data, ECC[ver]));

    var size = ver * 4 + 17, m = [], fn = [];
    for (var y = 0; y < size; y++) { m.push(new Array(size).fill(false)); fn.push(new Array(size).fill(false)); }
    function set(x, y, d) { m[y][x] = d; fn[y][x] = true; }

    /* 위치 찾기 무늬 셋 · 타이밍 선 · 정렬 무늬 */
    for (i = 0; i < size; i++) { set(6, i, i % 2 === 0); set(i, 6, i % 2 === 0); }
    [[3, 3], [size - 4, 3], [3, size - 4]].forEach(function (c) {
      for (var dy = -4; dy <= 4; dy++) for (var dx = -4; dx <= 4; dx++) {
        var xx = c[0] + dx, yy = c[1] + dy, dist = Math.max(Math.abs(dx), Math.abs(dy));
        if (xx >= 0 && xx < size && yy >= 0 && yy < size) set(xx, yy, dist !== 2 && dist !== 4);
      }
    });
    if (ver > 1) {
      var a = size - 7;
      for (var dy2 = -2; dy2 <= 2; dy2++) for (var dx2 = -2; dx2 <= 2; dx2++)
        set(a + dx2, a + dy2, Math.max(Math.abs(dx2), Math.abs(dy2)) !== 1);
    }

    /* 형식 정보: L(01) · 마스크 0, BCH(15,5) */
    var fmt = 1 << 3, rem = fmt;
    for (i = 0; i < 10; i++) rem = (rem << 1) ^ ((rem >>> 9) * 0x537);
    fmt = ((fmt << 10) | rem) ^ 0x5412;
    function fb(n) { return ((fmt >>> n) & 1) === 1; }
    for (i = 0; i <= 5; i++) set(8, i, fb(i));
    set(8, 7, fb(6)); set(8, 8, fb(7)); set(7, 8, fb(8));
    for (i = 9; i < 15; i++) set(14 - i, 8, fb(i));
    for (i = 0; i < 8; i++) set(size - 1 - i, 8, fb(i));
    for (i = 8; i < 15; i++) set(8, size - 15 + i, fb(i));
    set(8, size - 8, true);

    /* 코드어를 오른쪽 아래부터 두 칸씩 지그재그로 채우고 마스크 0((x+y)%2==0)을 씌운다 */
    var n = 0;
    for (var right = size - 1; right >= 1; right -= 2) {
      if (right === 6) right = 5;
      for (var vert = 0; vert < size; vert++) {
        for (var j = 0; j < 2; j++) {
          var x = right - j, up = ((right + 1) & 2) === 0, yy2 = up ? size - 1 - vert : vert;
          if (fn[yy2][x]) continue;
          var bit = n < data.length * 8 && ((data[n >>> 3] >>> (7 - (n & 7))) & 1) === 1;
          n++;
          m[yy2][x] = bit !== ((x + yy2) % 2 === 0);
        }
      }
    }
    return m;
  }

  /* 둘레 4칸 여백을 두고 칸 하나를 1로 그린다 — 경로 하나라 가볍다 */
  function toSvg(m) {
    var s = m.length, w = s + 8, d = '';
    m.forEach(function (row, y) {
      row.forEach(function (on, x) { if (on) d += 'M' + (x + 4) + ' ' + (y + 4) + 'h1v1h-1z'; });
    });
    return '<svg viewBox="0 0 ' + w + ' ' + w + '" shape-rendering="crispEdges" aria-hidden="true">' +
      '<rect width="' + w + '" height="' + w + '" class="pq-bg"/><path d="' + d + '" class="pq-fg"/></svg>';
  }

  function trips() { return window._myTripsData || []; }
  function tripId(t) { return t.id || t.tripId; }

  function draw() {
    var sel = document.getElementById('pqTrip'), box = document.getElementById('pqCode');
    if (!sel || !box) return;
    var url = location.origin + '/?trip=' + encodeURIComponent(sel.value);
    box.innerHTML = toSvg(encode(url));
    document.getElementById('pqUrl').textContent = url;
  }

  window.openPhoneQr = function () {
    var list = trips(), sel = document.getElementById('pqTrip');
    if (!sel) return;
    if (!list.length) { if (typeof toast === 'function') toast('먼저 여행을 만들어 주세요.'); return; }
    /* 같은 이름 · 날짜 여행이 여럿이면(부산 해운대구 여행이 여섯 개) 예산을, 그래도 같으면 고친 시각을 붙인다 */
    var tag = function (t, lv) {
      var d = (t.startDate || '').slice(5).replace('-', '.');
      var won = parseInt(String(t.budget || '').replace(/[^\d]/g, ''), 10);
      var at = String(t.updatedAt || '').replace('T', ' ').slice(5, 16).replace('-', '.');
      return (t.title || '여행') + (d ? ' · ' + d : '') +
        (lv > 0 && won > 0 ? ' · 예산 ' + Math.round(won / 10000) + '만' : '') +
        (lv > 1 && at ? ' · ' + at + ' 수정' : '');
    };
    var count = function (lv) {
      var n = {};
      list.forEach(function (t) { n[tag(t, lv)] = (n[tag(t, lv)] || 0) + 1; });
      return n;
    };
    var c0 = count(0), c1 = count(1);
    sel.innerHTML = list.map(function (t) {
      var o = document.createElement('option');
      o.value = tripId(t);
      o.textContent = tag(t, c0[tag(t, 0)] < 2 ? 0 : c1[tag(t, 1)] < 2 ? 1 : 2);
      return o.outerHTML;
    }).join('');
    /* 처음 골라 두는 것은 목록 맨 위(가장 최근에 고친) 여행이다. 마이페이지 목록과 같은 순서 */
    sel.value = String(tripId(list[0]));
    draw();
    document.getElementById('phoneQr').classList.add('open');
  };
  window.closePhoneQr = function () { document.getElementById('phoneQr').classList.remove('open'); };
  window.drawPhoneQr = draw;
  window._qrEncode = encode;   /* 자체검증용 */
})();
