/*
 * 전국 둘러보기 — 시도 → 시군구를 고르면 그 모형 한 장을 띄운다.
 * 장면 목록은 지도 화면과 같은 /img/mass_index.json 을 쓴다.
 * 모형 한 장이 260KB~1.1MB 라 화면에는 늘 한 장만 둔다. 다른 곳을 고르면 갈아 끼우고,
 * 화면을 떠나면 비운다(go() 가 exploreOn 을 부른다).
 */
(function () {
  var SIDO = ['서울', '부산', '대구', '인천', '광주', '대전', '울산', '세종', '경기',
              '강원', '충북', '충남', '전북', '전남', '경북', '경남', '제주'];
  var groups = null, req = 0;
  var $ = function (id) { return document.getElementById(id); };

  function head(sc) { return sc.label.split(' ')[0]; }
  function mid(sc) { return [(sc.bbox.s + sc.bbox.n) / 2, (sc.bbox.w + sc.bbox.e) / 2]; }

  /* 「경주 불국사」처럼 시도 이름이 없는 명소 장면은 제일 가까운 시군구 장면의 시도로 넣는다 */
  function group(list) {
    var g = {}, named = list.filter(function (sc) { return SIDO.indexOf(head(sc)) >= 0; });
    SIDO.forEach(function (s) { g[s] = []; });
    list.forEach(function (sc) {
      var s = head(sc);
      if (!g[s]) {
        var c = mid(sc), bd = Infinity;
        named.forEach(function (n) {
          var d = mid(n), dd = (d[0] - c[0]) * (d[0] - c[0]) + (d[1] - c[1]) * (d[1] - c[1]);
          if (dd < bd) { bd = dd; s = head(n); }
        });
      }
      g[s].push(sc);
    });
    return g;
  }

  function options(sel, items) {
    sel.innerHTML = '';
    items.forEach(function (it) { sel.add(new Option(it[1], it[0])); });
  }

  function say(t) { var m = $('exMsg'); m.hidden = !t; m.textContent = t || ''; }

  window.exPickSido = function () {
    var s = $('exSido').value;
    options($('exScene'), groups[s].map(function (sc, i) {
      return [i, head(sc) === s ? sc.label.slice(s.length + 1) : sc.label];
    }));
    window.exShow();
  };

  window.exShow = function () {
    var sc = groups[$('exSido').value][$('exScene').value], my = ++req;
    if (!sc) return;
    $('exFrame').innerHTML = '';
    say('모형을 불러오는 중입니다');
    fetch(sc.svg, { cache: 'force-cache' })
      .then(function (r) { if (!r.ok) throw r; return r.text(); })
      .then(function (svg) {
        if (my !== req) return;          /* 그사이 다른 곳을 골랐다 */
        $('exFrame').innerHTML = svg;
        say('');
      })
      .catch(function () { if (my === req) say('모형을 불러오지 못했어요. 잠시 뒤에 다시 골라 주세요.'); });
  };

  window.exploreOn = function (on) {
    var f = $('exFrame');
    if (!f) return;
    if (!on) { req++; f.innerHTML = ''; return; }
    if (groups) { window.exShow(); return; }
    fetch('/img/mass_index.json')
      .then(function (r) { return r.json(); })
      .then(function (list) {
        groups = group(list.filter(function (sc) { return sc.bbox && sc.svg; }));
        var sidos = SIDO.filter(function (s) { return groups[s].length; });
        options($('exSido'), sidos.map(function (s) { return [s, s + ' (' + groups[s].length + ')']; }));
        $('exCount').textContent = '전국 ' + sidos.length + '개 시도, 모형 ' + list.length + '곳';
        window.exPickSido();
      })
      .catch(function () { say('모형 목록을 불러오지 못했어요.'); });
  };
})();

/* 새로고침으로 이 화면에 돌아왔으면 go() 가 이 파일보다 먼저 돌았다 */
if (document.querySelector('#page-explore.active')) window.exploreOn(true);
