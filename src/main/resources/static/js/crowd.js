/* 혼잡도 — 화면마다 기준이 다르면 같은 숫자가 다른 말로 나온다.
   여기서만 정한다. 100 이 그 장소·그 시각의 평소다.

   crowd(142) -> { key:'vhigh', label:'매우 혼잡', idx:142 }
   CSS 는 .cw-vhigh … .cw-vlow 다섯 개만 알면 된다. */
(function (w) {
  var L = [
    { min: 140, key: 'vhigh', label: '매우 혼잡' },
    { min: 115, key: 'high',  label: '혼잡'      },
    { min:  85, key: 'mid',   label: '정상'      },
    { min:  60, key: 'low',   label: '한적'      },
    { min:  -1, key: 'vlow',  label: '매우 한적' }
  ];
  w.crowd = function (n) {
    n = Number(n);
    if (!isFinite(n)) return null;
    for (var i = 0; i < L.length; i++) {
      if (n >= L[i].min) return { key: L[i].key, label: L[i].label, idx: Math.round(n) };
    }
    return null;
  };
  w.crowd.levels = L;
})(window);
