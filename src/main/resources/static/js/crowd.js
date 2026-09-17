/* 혼잡도 — 화면마다 기준이 다르면 같은 숫자가 다른 말로 나온다. 여기서만 정한다.

   눈금은 관광공사 집중률(cnctrRate)이다. 0~100 이고 클수록 붐빈다.
   경계는 실제 분포에서 잡았다 — 다섯 시군구 30일분 1,770건의 5분위
   (p80 86.2 · p60 71.3 · p30 52.2 · p10 34.7 · 중앙값 65.7).

   서버의 CrowdLevel.java 와 같은 값이다. 한쪽만 바꾸면 화면의 「혼잡」과
   서버가 재계획을 권하는 자리가 어긋난다.

   crowd(94.6) -> { key:'vhigh', label:'매우 혼잡', idx:95 }
   CSS 는 .cw-vhigh … .cw-vlow 다섯 개만 알면 된다. */
(function (w) {
  var L = [
    { min: 85, key: 'vhigh', label: '매우 혼잡' },
    { min: 70, key: 'high',  label: '혼잡'      },
    { min: 52, key: 'mid',   label: '정상'      },
    { min: 35, key: 'low',   label: '한적'      },
    { min:  0, key: 'vlow',  label: '매우 한적' }
  ];

  w.crowd = function (n) {
    n = Number(n);
    if (!isFinite(n) || n < 0) return null;      /* 음수는 「모름」이다 */
    for (var i = 0; i < L.length; i++) {
      if (n >= L[i].min) return { key: L[i].key, label: L[i].label, idx: Math.round(n) };
    }
    return null;
  };

  w.crowd.levels = L;

  /* 서버가 준 값을 그대로 쓴다. levelKey 가 있으면 다시 판정하지 않는다 —
     서버와 화면이 같은 경계를 갖고 있어도, 값이 오면 그 값이 맞다. */
  w.crowd.of = function (f) {
    if (!f) return null;
    if (f.levelKey && f.levelLabel) {
      return { key: f.levelKey, label: f.levelLabel,
               idx: f.rate == null ? null : Math.round(f.rate) };
    }
    return w.crowd(f.rate);
  };

  /* 「지금보다 한산해지는 첫 날」. 화면이 「○일에 가면 낫다」고 말할 때 쓴다.
     list 는 /api/crowd/timeline 이 준 배열이다. */
  w.crowd.firstQuieter = function (list, fromDate, thanRate) {
    if (!list || !list.length || thanRate == null) return null;
    for (var i = 0; i < list.length; i++) {
      var d = list[i];
      if (fromDate && d.date <= fromDate) continue;
      if (d.rate != null && d.rate < thanRate - 8) return d;   /* 8 이하 차이는 같다고 본다 */
    }
    return null;
  };
})(window);
