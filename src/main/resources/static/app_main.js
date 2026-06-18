/* =============================================================================
 * TripLinker - 메인 애플리케이션 로직 (app_main.js) — API 연동 버전
 *
 * 【변경사항】
 * - ACCOUNTS / NOTIF_DATA / MYPAGE_DATA / PLACE_REVIEWS 하드코딩 완전 제거
 * - 모든 데이터 조회·변경을 실제 REST API 호출로 전환
 * - JWT (accessToken / refreshToken) → localStorage 관리
 * - 토큰 만료 시 POST /api/auth/refresh 자동 재발급
 * - 공통 응답 형식: { success: true, message: "...", data: {...} }
 *
 * 【주요 API 매핑】
 * - tryLogin()        → POST /api/auth/login
 * - doLogout()        → POST /api/auth/logout
 * - updateMyPageUI()  → GET  /api/users/me + GET /api/trips
 * - updateLedgerList()→ GET  /api/trips
 * - openNotifPopup()  → GET  /api/notifications
 * - showMapPlacePopup()→ GET /api/maps/places?keyword=
 * - checkUname()      → GET  /api/auth/check-username?username=
 * - checkEmail()      → GET  /api/auth/check-email?email=
 * - sendMsg()         → POST /api/chat/message
 * - startChatWithSummary() → POST /api/chat/sessions
 *
 * 【연관 파일】
 * - app_community.js: 커뮤니티·관리자 로직
 * - styles_main.css:  전체 스타일
 * - index.html:       메인 진입점
 * ============================================================================= */

/* ───────────────────────────────────────────────
 * 1. API 유틸리티 (공통 fetch 래퍼 + 토큰 관리)
 * ─────────────────────────────────────────────── */
const API_BASE = '';  // 동일 Origin이면 '' / 다른 도메인이면 'https://api.triplinker.com'

/** localStorage 헬퍼 */
const Token = {
  getAccess:   () => localStorage.getItem('accessToken'),
  getRefresh:  () => localStorage.getItem('refreshToken'),
  setAccess:   (t) => localStorage.setItem('accessToken', t),
  setRefresh:  (t) => localStorage.setItem('refreshToken', t),
  clear:       () => { localStorage.removeItem('accessToken'); localStorage.removeItem('refreshToken'); }
};

/**
 * 공통 API 호출 함수 (자동 토큰 재발급 포함)
 * @param {string} path  - API 경로 (e.g. '/api/auth/login')
 * @param {object} opts  - fetch options (method, body 등)
 * @param {boolean} retry - 재시도 여부 (내부 재귀용)
 * @returns {Promise<{success, message, data}>}
 */
async function apiCall(path, opts = {}, retry = true) {
  const headers = { 'Content-Type': 'application/json', ...(opts.headers || {}) };
  const token = Token.getAccess();
  if (token) headers['Authorization'] = 'Bearer ' + token;

  let res;
  try {
    res = await fetch(API_BASE + path, { ...opts, headers });
  } catch (e) {
    console.error('[API] 네트워크 오류:', e);
    toast('⚠️ 서버에 연결할 수 없습니다.');
    return { success: false, message: '네트워크 오류', data: null };
  }

  // 401 Unauthorized → 토큰 재발급 시도
  if (res.status === 401 && retry) {
    const refreshed = await refreshAccessToken();
    if (refreshed) return apiCall(path, opts, false);
    // 재발급 실패 → 강제 로그아웃
    forceLogout();
    return { success: false, message: '인증 만료', data: null };
  }

  let json;
  try { json = await res.json(); } catch (e) { json = {}; }
  return json;
}

/** Refresh Token으로 Access Token 재발급 */
async function refreshAccessToken() {
  const refreshToken = Token.getRefresh();
  if (!refreshToken) return false;
  try {
    const res = await fetch(API_BASE + '/api/auth/refresh', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken })
    });
    const json = await res.json();
    if (json.success && json.data && json.data.accessToken) {
      Token.setAccess(json.data.accessToken);
      return true;
    }
  } catch (e) { console.error('[API] 토큰 재발급 실패:', e); }
  return false;
}

/** JSON body를 포함하는 POST 헬퍼 */
const api = {
  get:   (path)         => apiCall(path, { method: 'GET' }),
  post:  (path, body)   => apiCall(path, { method: 'POST',  body: JSON.stringify(body) }),
  put:   (path, body)   => apiCall(path, { method: 'PUT',   body: JSON.stringify(body) }),
  patch: (path, body)   => apiCall(path, { method: 'PATCH', body: JSON.stringify(body) }),
  del:   (path)         => apiCall(path, { method: 'DELETE' })
};

/* ───────────────────────────────────────────────
 * 2. 앱 상태 (서버 응답 기반으로만 갱신)
 * ─────────────────────────────────────────────── */
let _currentUser          = null;   // GET /api/users/me 응답의 data
let _isSuspended          = false;  // role === 'SUSPENDED'
let _loggedIn             = false;
let _userNotifs           = [];     // GET /api/notifications 응답의 data[]
let _myTrips              = [];     // GET /api/trips 응답의 data[]
let _chatSessionId        = null;   // POST /api/chat/sessions 응답의 data.sessionId
let _budgetSelectedTripId = parseInt(sessionStorage.getItem('budgetSelectedTripId')) || null;  // [v2] 새로고침 복원용
let _lastExpenseData      = null;
let _allActualExps        = [];
let _expensePage          = 1;
const _EXP_PAGE_SIZE      = 8;
let _ledgerCardPage       = 1;
let _myLedgerPage         = 1;
const _LEDGER_CARD_PAGE_SIZE = 6;
let _activeTags           = new Set();
let _loginFailCount       = 0;
let _loginLockedUntil     = null;
let _loginLockTimer       = null;   // [v2] 잠금 카운트다운 인터벌 ID

/** 모달 열기 헬퍼 (플래너/로그인 체크에서 사용) */
function openModal(id) {
  if (id === 'modal-auth') go('login');
}

/** 페이지별 CSS를 처음 진입할 때만 동적으로 로드 */
function loadPageCSS(href) {
  if (!document.querySelector(`link[href="${href}"]`)) {
    const link = document.createElement('link');
    link.rel  = 'stylesheet';
    link.href = href;
    document.head.appendChild(link);
  }
}

/* ───────────────────────────────────────────────
 * 3. NAV 라우팅
 * ─────────────────────────────────────────────── */
function go(id, addToHistory) {
  sessionStorage.setItem('currentPage', id);  // [v2] 새로고침 복원용
  if (addToHistory !== false) history.pushState({page: id}, '', location.href);
  document.querySelectorAll('.page').forEach(p => p.classList.remove('active'));
  const pg = document.getElementById('page-' + id);
  if (pg) pg.classList.add('active');

  if (id === 'map') {
    // 🎯 지도방문 플래그가 없으면 세션에 기록하고 쿨하게 F5 한 번 날려버리기
    if (!sessionStorage.getItem('map_refresh_lock')) {
      sessionStorage.setItem('map_refresh_lock', 'true');
      location.reload();
      return; // 새로고침 되므로 아래 코드는 실행할 필요 없음
    }

    // F5를 누르고 다시 들어왔을 때 실행되는 안전망
    setTimeout(function() {
      if (window._kakaoMap) {
        window._kakaoMap.relayout();
        if (typeof updateBoundsForDay === 'function') updateBoundsForDay('all');
      }
    }, 100);
  } else {
    // 🎯 지도 외에 다른 페이지(홈, 플래너 등)로 가면 플래너 플래그를 지워줘서 나중에 지도 올 때 또 새로고침 되게 함
    sessionStorage.removeItem('map_refresh_lock');
  }

  //가계부 페이지 진입 시 항상 실제 데이터로 갱신
  if (id === 'ledger') {
    loadPageCSS('/css/styles_budget.css');
    _populateLedgerTripCards();
    const selEl  = document.querySelector('.ledger-selector-outer');
    const mainEl = document.getElementById('ledger-main');
    const tripStillValid = _myTrips.some(t => t.tripId === _budgetSelectedTripId);
    if (tripStillValid) {
      if (selEl)  selEl.style.display  = 'none';
      if (mainEl) mainEl.style.display = 'block';
      _loadExpenses(_budgetSelectedTripId);
    } else {
      if (selEl)  selEl.style.display  = 'block';
      if (mainEl) mainEl.style.display = 'none';
    }
  }
  document.querySelectorAll('.wf-item').forEach(b => b.classList.remove('on'));
  const map = {
    main: 0, signup: 1, 'signup-social': 1, login: 2, mypage: 3, planner: 4,
    map: 5, budget: 6, ledger: 7, community: 8, admin: 9, review: 10,
    'edit-review': 11, 'place-reviews': 12, 'place-teraroasa': 12,
    'place-hyeopjae': 12, weather: 13,
    'r-gangneung': 10, 'r-jeju-gaseongbi': 10, 'r-gyeongju': 10, 'r-busan': 10,
    'r-jeonju': 10, 'r-namhae': 10, 'r-seorak': 10, 'r-jeju-east-west': 10,
    's-hyeopjae': 10, 's-anmok': 10, 's-haeundae': 10, 's-jeju-compare': 10,
    's-sokcho': 10, 's-gyeongju': 10, 's-namhae': 10, 's-jeonju': 10,
    'f-jeju-top10': 10, 'f-gangneung-cafe': 10, 'f-busan-jagalchi': 10,
    'f-jeju-blackpig': 10, 'f-gyeongju': 10, 'f-busan-seafood': 10,
    'f-sokcho': 10, 'f-jeonju': 10,
    'place-f-olleisunjae': 12, 'place-f-jagalchi': 12, 'place-f-teraroasa-g': 12,
    'place-f-hwangnam': 12, 'place-f-jeonjubibim': 12, 'place-f-heukdwaeji': 12,
    'place-f-haundaesea': 12,
    'place-t-seongsan': 12, 'place-t-udo': 12, 'place-t-ollesigang': 12,
    'place-t-yongduram': 12, 'place-t-hyeopjaebeach': 12,
    'place-c-handam': 12, 'place-c-arario': 12, 'place-c-monsant': 12,
    'place-c-daraon': 12, 'place-c-seohyang': 12
  };
  const wfi = document.querySelectorAll('.wf-item');
  if (map[id] !== undefined && wfi[map[id]]) wfi[map[id]].classList.add('on');
  // nav-link .on 동기화 (로고 클릭 등 setNav 없이 호출되는 경우 대응)
    document.querySelectorAll('.nav-link').forEach(b => b.classList.remove('on'));
    const _goNavMap = { main: 0, community: 1, weather: 2 };
    const _goNavLinks = document.querySelectorAll('.nav-link');
    if (_goNavMap[id] !== undefined && _goNavLinks[_goNavMap[id]]) {
        _goNavLinks[_goNavMap[id]].classList.add('on');
      } else if (id === 'planner') {
        document.getElementById('navPlannerBtn')?.classList.add('on');
      }
  window.scrollTo(0, 0);

  // 후기/장소 상세 페이지 이동 시 렌더러 자동 호출
  setTimeout(function() {
    try {
      var allR = Object.assign({},
          typeof MOCK_ROUTE_REVIEWS !== 'undefined' ? MOCK_ROUTE_REVIEWS : {},
          typeof MOCK_STAY_REVIEWS  !== 'undefined' ? MOCK_STAY_REVIEWS  : {},
          typeof MOCK_FOOD_REVIEWS  !== 'undefined' ? MOCK_FOOD_REVIEWS  : {}
      );
      if (allR[id] && typeof renderReviewDetailPage === 'function') renderReviewDetailPage(id);
      var allP = Object.assign({},
          typeof MOCK_TOUR_PLACES !== 'undefined' ? MOCK_TOUR_PLACES : {},
          typeof MOCK_CAFE_PLACES !== 'undefined' ? MOCK_CAFE_PLACES : {}
      );
      if (allP[id] && typeof renderPlaceDetailPage === 'function') renderPlaceDetailPage(id);
    } catch(e) {}
  }, 30);

  // [v1] 날씨 탭 / 플래너 날짜 제약 자동 초기화
  if (id === 'weather' && typeof window.renderWeatherTab === 'function') {
    setTimeout(window.renderWeatherTab, 30);
  }
  if (id === 'planner') {
    if (typeof initPlannerDateConstraints === 'function') setTimeout(initPlannerDateConstraints, 100);
    if (typeof initPlannerMBTI === 'function') setTimeout(initPlannerMBTI, 100);
    setTimeout(_syncPlannerTopbar, 60);

    if (window._pendingDestText) {
      const val = window._pendingDestText;
      window._pendingDestText = null;
      setTimeout(() => _applyDestText(val), 150);
    }

  }
  if (id === 'map') {
    setTimeout(function() {
      if (window._kakaoMap) {
        window._kakaoMap.relayout();
      } else if (typeof initKakaoMap === 'function') {
        initKakaoMap();
      }
    }, 100);
  }
  if (typeof _syncPlannerTopbar === 'function') setTimeout(_syncPlannerTopbar, 60);
}

function setNav(btn) {
  document.querySelectorAll('.nav-link').forEach(b => b.classList.remove('on'));
  btn.classList.add('on');
}

/* ───────────────────────────────────────────────
 * 4. 인증 (Auth Domain)
 * ─────────────────────────────────────────────── */

/** 로그인 성공 시 공통 세션 초기화 */
async function _initSession(accessToken, refreshToken) {
  Token.setAccess(accessToken);
  Token.setRefresh(refreshToken);
  _loggedIn = true;

  // GET /api/users/me
  const meRes = await api.get('/api/users/me');
  if (meRes.success && meRes.data) {
    _currentUser  = meRes.data;
    _isSuspended  = (_currentUser.role === 'SUSPENDED');
  }

  updateNav();
  await updateMyPageUI();
  await _loadNotifications();
}

/** 강제 로그아웃 (토큰 만료 등) */
function forceLogout() {
  Token.clear();
  _currentUser = null; _isSuspended = false; _loggedIn = false;
  _userNotifs = []; _myTrips = [];
  updateNav();
  toast('⚠️ 세션이 만료되었습니다. 다시 로그인해주세요.');
  go('login');
}

/** 네비게이션 버튼 표시/숨김 */
function updateNav() {
  console.log("현재 유저 정보:", _currentUser);
  const li = document.getElementById('navLoginBtn');
  const si = document.getElementById('navSignupBtn');
  const un = document.getElementById('navUserNameBtn');
  const lo = document.getElementById('navLogoutBtn');
  const al = document.getElementById('navAdminLink');
  const nb = document.getElementById('navBellBtn');
  if (_loggedIn && _currentUser) {
    if (li) li.style.display = 'none';
    if (si) si.style.display = 'none';
    if (un) { un.style.display = ''; un.textContent = (_currentUser.name || _currentUser.username) + '님'; }
    if (lo) lo.style.display = '';
    if (nb) nb.style.display = '';
    if (al) al.style.display = (_currentUser.role === 'ADMIN') ? '' : 'none';
  } else {
    if (li) li.style.display = '';
    if (si) si.style.display = '';
    if (un) { un.style.display = 'none'; un.textContent = ''; }
    if (lo) lo.style.display = 'none';
    if (nb) nb.style.display = 'none';
    if (al) al.style.display = 'none';
  }
  const plannerBtn = document.getElementById('navPlannerBtn');
    if (plannerBtn) {
        if (_loggedIn && _hasPlannerDraft()) {
            plannerBtn.textContent = '✏️ 작성중인 플랜';
          } else {
            plannerBtn.textContent = '✈ 플랜';
          }
      }
  if (typeof _syncPlannerTopbar === 'function') _syncPlannerTopbar();
}

/** [v2] 로그인 잠금 카운트다운 (UI 실시간 업데이트) */
function _startLockCountdown(totalSeconds, warnEl) {
  if (_loginLockTimer) clearInterval(_loginLockTimer);
  let secs = Math.ceil(totalSeconds);
  _loginLockedUntil = Date.now() + secs * 1000;

  function _fmt(s) {
    const m = Math.floor(s / 60), r = s % 60;
    return m > 0 ? `${m}분 ${r}초` : `${s}초`;
  }

  warnEl.innerHTML = `🔒 5회 실패로 잠겼습니다. ${_fmt(secs)} 후 재시도 가능합니다.`;
  warnEl.style.display = 'flex';

  _loginLockTimer = setInterval(() => {
    secs--;
    if (secs <= 0) {
      clearInterval(_loginLockTimer);
      _loginLockTimer = null;
      _loginLockedUntil = null;
      warnEl.innerHTML = '✅ 잠금이 해제되었습니다. 다시 로그인해주세요.';
      return;
    }
    warnEl.innerHTML = `🔒 5회 실패로 잠겼습니다. ${_fmt(secs)} 후 재시도 가능합니다.`;
  }, 1000);
}

/** ─── POST /api/auth/login ─── */
async function tryLogin() {
  const id = document.getElementById('lid').value.trim();
  const pw = document.getElementById('lpw').value;
  const w  = document.getElementById('login-warn');

  // 클라이언트 잠금 체크 — 카운트다운 중이면 API 호출 차단
  if (_loginLockedUntil && Date.now() < _loginLockedUntil) {
    w.style.display = 'flex';
    return;
  }
  if (!id || !pw) {
    w.innerHTML = '⚠️ 아이디와 비밀번호를 입력하세요';
    w.style.display = 'flex';
    return;
  }

  const res = await api.post('/api/auth/login', { username: id, password: pw });

  if (!res.success) {
    if (res.data && res.data.locked) {
      // [v2] 서버 잠금 응답 → 카운트다운 시작
      _startLockCountdown(res.data.remainSeconds || 300, w);
    } else {
      const failCount = res.data?.failCount;
      w.innerHTML = failCount
          ? `⚠️ 비밀번호 오류 (${failCount}/5회) · 5회 실패 시 5분 잠금`
          : '⚠️ 아이디 또는 비밀번호를 확인해주세요.';
      w.style.display = 'flex';
    }
    return;
  }
  w.style.display = 'none';

  await _initSession(res.data.accessToken, res.data.refreshToken);

  toast((_currentUser ? _currentUser.name : id) + '님, 환영합니다! 🎉');

  // ✨ [핵심 수정] 로그인 전에 저장해 둔 초대장 링크(redirectUrl)가 있는지 체크합니다.
  const redirectUrl = sessionStorage.getItem('redirectUrl');
  if (redirectUrl) {
    sessionStorage.removeItem('redirectUrl'); // 사용했으니 청소
    window.location.href = redirectUrl;        // 주소창을 초대 링크 상태로 강제 변경하여 새로고침 기동!
    return; // 메인화면으로 가는 아래 go('main') 코드를 실행하지 않고 여기서 끝냅니다.
  }

  await _initSession(res.data.accessToken, res.data.refreshToken);
  go('main');
  toast((_currentUser ? _currentUser.name : id) + '님, 환영합니다! 🎉');

  if (_isSuspended) {
    setTimeout(() => {
      const rm = document.getElementById('suspended-reason-msg');
      if (rm) rm.textContent = _currentUser.suspensionReason || '계정이 정지되었습니다.';
      const am = document.getElementById('suspended-admin-msg');
      if (am) am.textContent = '커뮤니티 기능(후기 작성, 댓글 등록)이 제한되었습니다.';
      document.getElementById('suspendedAlert').classList.add('open');
    }, 400);
  } else if (_currentUser && _currentUser.role === 'ADMIN') {
    setTimeout(() => {
      const pm = document.getElementById('pwModal');
      if (pm) pm.classList.add('open');
    }, 1200);
  }
}

/** ─── GET /oauth2/authorization/kakao ─── */
function tryKakaoLogin() {
  toast('카카오 계정으로 로그인 중...');
  window.location.href = API_BASE + '/oauth2/authorization/kakao';
}

/** ─── GET /oauth2/authorization/google ─── */
function tryGoogleLogin() {
  toast('구글 계정으로 로그인 중...');
  window.location.href = API_BASE + '/oauth2/authorization/google';
}

// OAuth2 콜백 후 토큰을 URL 파라미터로 수신하는 경우를 처리
function _handleOAuthCallback() {
  var params = new URLSearchParams(location.search);

  // 소셜 로그인 에러 처리
  var oauthError = params.get('oauthError');
  if (oauthError) {
    history.replaceState({}, '', location.pathname);
    if (oauthError === 'email_already_exists') {
      toast('⚠️ 이미 가입되어 있는 이메일입니다.');
    } else {
      toast('⚠️ 소셜 로그인 중 오류가 발생했습니다.');
    }
    return;
  }

  var accessToken  = params.get('accessToken');
  var refreshToken = params.get('refreshToken');
  if (accessToken && refreshToken) {
    history.replaceState({}, '', location.pathname);
    _initSession(accessToken, refreshToken).then(function() {
      if (_currentUser && _currentUser.isSocial && _currentUser.region === '미설정') {
        toast('회원가입을 먼저 진행해주세요!');
        setTimeout(function() { startSocialSignup(); }, 800);
      } else {
        go('main');
        toast((_currentUser ? _currentUser.name : '') + '님, 환영합니다! 🎉');
      }
    });
  }
}

/** ─── POST /api/auth/logout ─── */
async function doLogout() {
  await api.post('/api/auth/logout', {});
  Token.clear();
  _currentUser = null; _isSuspended = false; _loggedIn = false;
  _userNotifs = []; _myTrips = [];
  updateNav();
  toast('로그아웃 되었습니다.');
  go('main');
}

/** 소셜 회원가입 완료 */
async function doSocialSignup() {
  var nameEl = document.getElementById('social-name');
  if (!nameEl || !nameEl.value.trim()) { toast('이름을 입력해주세요'); return; }

  var birthEl = document.getElementById('social-birth');
  var birthDate = birthEl ? birthEl.value : '';
  if (!birthDate) { toast('생년월일을 선택해주세요'); return; }

  var genderOn = document.querySelector('#social-gender-row .chip.on');
  if (!genderOn) { toast('성별을 선택해주세요'); return; }
  var gender = genderOn.textContent.trim() === '남성' ? 'M' : 'F';

  var bigEl    = document.getElementById('social-region-big');
  var province = bigEl ? bigEl.value : '';
  if (!province) { toast('거주 지역(도/시)을 선택해주세요'); return; }
  var cityEl  = document.getElementById('social-region-city');
  var cityVal = cityEl ? cityEl.value : '';
  var city    = (cityVal && cityVal !== '시/군/구 선택' && cityVal !== '전체') ? cityVal : '';
  var region  = city ? (province + ' ' + city) : province;

  var mbti = '';
  document.querySelectorAll('#social-mbti .chip-row').forEach(function(row) {
    var on = row.querySelector('.chip-sm.on');
    if (on) mbti += on.textContent.trim()[0];
  });
  if (mbti.length !== 4) { toast('MBTI를 모두 선택해주세요'); return; }

  var body = { name: nameEl.value.trim(), region: region, gender: gender, birthDate: birthDate, mbti: mbti };
  var res = await api.patch('/api/users/me', body);
  if (res.success) {
    if (_currentUser) {
      _currentUser.name = body.name;
      _currentUser.region = region;
      _currentUser.gender = gender;
      _currentUser.birthDate = birthDate;
      _currentUser.mbti = mbti;
    }
    toast('✅ 소셜 계정으로 회원가입이 완료되었습니다!');
    setTimeout(function() { go('mypage'); }, 1000);
  } else {
    toast('⚠️ ' + (res.message || '가입 처리 중 오류가 발생했습니다.'));
  }
}
function startSocialSignup() {
  var provider = (_currentUser && _currentUser.username && _currentUser.username.startsWith('google')) ? 'google' : 'kakao';
  var icon     = provider === 'google' ? '🔵' : '🟡';
  var iconEl   = document.getElementById('social-signup-icon');
  var noticeEl = document.getElementById('social-signup-notice');
  if (iconEl)   iconEl.textContent = icon;
  if (noticeEl) noticeEl.textContent = icon + ' 소셜 연결 완료 — 아이디·이메일·비밀번호는 소셜 계정으로 대체됩니다.';
  go('signup-social');
  var nameEl = document.getElementById('social-name');
  if (nameEl && _currentUser && _currentUser.name) nameEl.value = _currentUser.name;
}

/* ───────────────────────────────────────────────
 * 5. 마이페이지 (User Domain + Plan Domain)
 * ─────────────────────────────────────────────── */

/** GET /api/trips + GET /api/users/me/posts + GET /api/users/me/liked-posts 병렬 호출 */
async function updateMyPageUI() {
  if (!_currentUser) return;

  const av = document.getElementById('myAvatar');
  const nm = document.getElementById('myName');
  const em = document.getElementById('myEmail');
  if (av) av.textContent = _currentUser.name ? _currentUser.name[0] : '?';
  if (nm) nm.textContent = _currentUser.name  || '';
  if (em) em.textContent = _currentUser.email || '';

  const [tripsRes] = await Promise.all([
    api.get('/api/trips'),
    _renderMyReviews(),
    _renderMyLikedPosts(),
  ]);
  _myTrips = (tripsRes.success && tripsRes.data) ? tripsRes.data : [];

  _renderMyTrips(_myTrips);
  updateLedgerList();
}

// 1. 기존 함수 덮어쓰기 (onclick 부분이 수정됨!)
function _renderMyTrips(trips) {
  const te = document.getElementById('my-trips');
  if (!te) return;

  te.innerHTML = '<h3 class="my-sec-ttl">내 여행 기록</h3>' + (
      trips.length
          ? trips.map(x => {
            console.log("Trip Data Check:", x);

            // 1. 최종 수정 날짜 파싱 및 줄바꿈 처리
            let lastUpdate = '—';
            if (x.updatedAt) {
              // 🎯 ISO 형식을 모든 브라우저(Safari, Chrome, 모바일)에서 에러 없이 인식하도록 변환 보완
              let dateStr = x.updatedAt.replace ? x.updatedAt.replace(/-/g, '/').replace('T', ' ') : x.updatedAt;
              // 밀리초나 나노초 단위 규격 문자열 찌꺼기 제거 (.)
              if (typeof dateStr === 'string' && dateStr.includes('.')) {
                dateStr = dateStr.split('.')[0];
              }

              const d = new Date(dateStr);

              if (!isNaN(d.getTime())) {
                const year  = d.getFullYear();
                const month = String(d.getMonth() + 1).padStart(2, '0');
                const day   = String(d.getDate()).padStart(2, '0');
                const hours = String(d.getHours()).padStart(2, '0');
                const mins  = String(d.getMinutes()).padStart(2, '0');

                lastUpdate = `${year}.${month}.${day}<br>${hours}:${mins}`;
              }
            }

            // 2. 백엔드 DTO(TripListResponseDto)의 새로운 필드와 완벽 호환되도록 안전망 매핑
            const displayStart = x.startDate ? x.startDate.replace(/-/g, '.') : '';
            const displayEnd   = x.endDate ? x.endDate.replace(/-/g, '.') : '';
            const displayDest  = x.destination || '';

            return `
          <div class="trip-card" onclick="openMyTrip(${x.id || x.tripId})"> 
            <div class="trip-thumb">🗺️</div>
            <div class="trip-info">
              <div class="trip-ttl">${x.title || '여행 플랜'}</div>
              <div class="trip-meta">${displayStart} ~ ${displayEnd} · ${displayDest}</div>
            </div>
            <div class="trip-budget" style="color:var(--text3); font-size:11px; text-align:right; line-height:1.4; min-width:80px; flex-shrink:0;">
                <span style="display:block; font-size:10px; color:var(--text3); font-weight:700; margin-bottom:2px;">최종 수정</span>
                <span style="color:var(--text2); font-weight:500;">${lastUpdate}</span>
            </div>
          </div>`;
          }).join('')
          : '<div style="color:var(--text3);font-size:13px;padding:20px 0;text-align:center">여행 기록이 없습니다.</div>'
  );
}

// 2. 새로 추가할 함수 (_renderMyTrips 함수 바로 밑에 붙여넣어 주세요)
function openMyTrip(tripId) {
  // ✨ 클릭한 카드의 진짜 tripId로 브라우저 기억을 강제로 덮어씌웁니다.
  window._currentTripId = tripId;
  sessionStorage.setItem('plannerDraftId', tripId);

  // 맵 전환 시 이전 데이터 잔상이 보이지 않도록 화면 백지화
  const listEl = document.getElementById('mapDayList');
  if (listEl) listEl.innerHTML = '<div style="padding:40px 20px;text-align:center;color:var(--sage-d);font-weight:700;">✨ 여행 정보를 불러오는 중...</div>';

  if (window._kakaoOverlays) window._kakaoOverlays.forEach(o => o.overlay.setMap(null));
  if (window._kakaoPolylines) window._kakaoPolylines.forEach(p => p.line.setMap(null));

  // 지도 화면으로 부드럽게 이동
  go('map');

  // 방금 덮어씌운 새 tripId를 바탕으로 지도를 새로 그림!
  setTimeout(() => {
    if (typeof initMapPage === 'function') {
      initMapPage();
    }
  }, 50);
}

/** [v2] GET /api/users/me/posts → 작성한 후기 */
async function _renderMyReviews() {
  const listEl = document.getElementById('my-reviews-list');
  if (!listEl) return;
  listEl.innerHTML = '<div style="color:var(--text3);font-size:13px;padding:20px 0;text-align:center">후기를 불러오는 중...</div>';
  const res = await apiCall('/api/users/me/posts');
  const posts = res.data ?? [];
  if (posts.length === 0) {
    listEl.innerHTML = '<p style="color:var(--text3);font-size:13px">작성한 후기가 없습니다.</p>';
    return;
  }
  listEl.innerHTML = posts.map(r => `
    <div class="post-card"><span class="post-cat ${r.catClass}">${r.catLabel}</span>
      <div class="post-ttl" style="margin-top:5px">${r.title}</div>
      <div class="post-foot">
        <div class="post-stats"><span class="post-stat">❤️ ${r.likes}</span>${r.views ? `<span class="post-stat">👁 ${r.views}</span>` : ''}</div>
        <div style="display:flex;gap:6px">
          <button class="btn-scrap" onclick="event.stopPropagation();go('edit-review')">✏️ 수정</button>
          <button class="btn-scrap" style="color:var(--coral);border-color:var(--coral)" onclick="event.stopPropagation();if(confirm('삭제?'))toast('삭제 완료')">삭제</button>
        </div>
      </div>
    </div>
  `).join('');
}

/** [v2] GET /api/users/me/liked-posts → 좋아요한 후기 */
async function _renderMyLikedPosts() {
  const listEl = document.getElementById('my-likes-list');
  if (!listEl) return;
  listEl.innerHTML = '<div style="color:var(--text3);font-size:13px;padding:20px 0;text-align:center">불러오는 중...</div>';
  const res = await apiCall('/api/users/me/liked-posts');
  const liked = res.data ?? [];
  if (liked.length === 0) {
    listEl.innerHTML = '<p style="color:var(--text3);font-size:13px">좋아요한 후기가 없습니다.</p>';
    return;
  }
  listEl.innerHTML = liked.map(r => `
    <div class="post-card"><span class="post-cat ${r.catClass}">${r.catLabel}</span>
      <div class="post-ttl" style="margin-top:5px">${r.title}</div>
      <div class="post-stats"><span class="post-stat">❤️ ${r.likes}</span>${r.views ? `<span class="post-stat">👁 ${r.views}</span>` : ''}</div>
    </div>
  `).join('');
}

/* ───────────────────────────────────────────────
 * 7. 회원정보 수정 (PATCH /api/users/me)
 * ─────────────────────────────────────────────── */
function resetInfoStep() {
  const s1 = document.getElementById('info-pw-step'), s2 = document.getElementById('info-edit-form');
  if (s1) s1.style.display = 'block'; if (s2) s2.style.display = 'none';
  const i = document.getElementById('infoPwInput'); if (i) i.value = '';
  const e = document.getElementById('info-pw-err'); if (e) e.style.display = 'none';
}

function buildEditHTML(u, isSocial) {
  const mbtiStr = u.mbti || '';
  const dims = [
    {k:'ei', pairs:[['E','E(외향)'],['I','I(내향)']]},
    {k:'sn', pairs:[['S','S(감각)'],['N','N(직관)']]},
    {k:'tf', pairs:[['T','T(사고)'],['F','F(감정)']]},
    {k:'jp', pairs:[['J','J(계획)'],['P','P(즉흥)']]}
  ];
  let mbtiHtml = '<div class="form-group"><label class="form-label">MBTI</label><div style="display:flex;flex-direction:column;gap:6px;margin-top:4px">';
  dims.forEach((d, i) => {
    mbtiHtml += `<div class="chip-row" style="display:flex;gap:6px;align-items:center;flex-wrap:nowrap"><span style="font-size:11px;color:var(--text3);width:38px;flex-shrink:0">${d.k.toUpperCase()}</span>`;
    d.pairs.forEach(p => {
      const on = mbtiStr[i] === p[0] ? ' on' : '';
      mbtiHtml += `<button class="chip chip-sm${on}" onclick="pick(this)">${p[1]}</button>`;
    });
    mbtiHtml += '</div>';
  });
  mbtiHtml += '</div></div>';

  const regionParts = (u.region || '').split(' ');
  const savedProvince = regionParts[0] || '';
  const savedCity     = regionParts.slice(1).join(' ') || '';
  const provinces = ['서울','경기','인천','강원','충북','충남','대전','세종','전북','전남','광주','경북','경남','대구','울산','부산','제주'];
  const cityData = _cities;
  const provinceOpts = provinces.map(p => `<option${p===savedProvince?' selected':''}>${p}</option>`).join('');
  const cities = cityData[savedProvince] || [];
  const cityOpts = '<option value="">시/군/구 선택</option>' + cities.map(c => `<option${c===savedCity?' selected':''}>${c}</option>`).join('');
  const regionHtml = `<div class="form-group"><label class="form-label">거주 지역</label><div style="display:flex;gap:8px;margin-top:4px"><select class="form-input" id="edit-region-big" onchange="updateCity(this,'edit-region-city')" style="flex:1"><option value="">도/시 선택</option>${provinceOpts}</select><select class="form-input" id="edit-region-city" style="flex:1">${cityOpts}</select></div></div>`;
  const pwHtml = isSocial ? '' : `<hr style="border:none;border-top:1px solid var(--border2);margin:14px 0"><div class="form-group"><label class="form-label">새 비밀번호</label><input class="form-input" type="password" id="edit-newpw" placeholder="새 비밀번호 8자 이상"></div><div class="form-group"><label class="form-label">새 비밀번호 확인</label><input class="form-input" type="password" id="edit-newpw2" placeholder="새 비밀번호 재입력"></div>`;
  const ds = 'style="background:var(--cream2);color:var(--text3);cursor:not-allowed"';
  const socialNotice = isSocial ? `<div style="background:#FFF9E6;border:1px solid #FEE500;border-radius:9px;padding:10px 14px;font-size:12px;color:#6B5A00;margin-bottom:14px">🟡 카카오 계정: 아이디·이메일·비밀번호는 카카오에서 관리됩니다.</div>` : '';
  return socialNotice
      + `<div class="form-row"><div class="form-group"><label class="form-label">아이디 <span style="font-size:10px;color:var(--text3)">(변경 불가)</span></label><input class="form-input" value="${u.username||''}" disabled ${ds}></div><div class="form-group"><label class="form-label">이름</label><input class="form-input" id="edit-name" value="${u.name||''}"></div></div>`
      + `<div class="form-group"><label class="form-label">이메일 <span style="font-size:10px;color:var(--text3)">(변경 불가)</span></label><input class="form-input" value="${u.email||''}" disabled ${ds}></div>`
      + `<div class="form-row"><div class="form-group"><label class="form-label">생년월일</label><input class="form-input" type="date" id="edit-birth" value="${u.birthDate||''}"></div><div class="form-group"><label class="form-label">성별</label><div class="chip-row" style="margin-top:4px"><button class="chip${u.gender==='M'?' on':''}" onclick="pick(this,'edit-gender')">남성</button><button class="chip${u.gender==='F'?' on':''}" onclick="pick(this,'edit-gender')">여성</button><button class="chip" onclick="pick(this,'edit-gender')">기타</button></div></div></div>`
      + regionHtml + mbtiHtml + pwHtml;
}

function showSocialInfoEdit() {
  document.getElementById('info-pw-step').style.display = 'none';
  document.getElementById('info-edit-form').style.display = 'block';
  document.getElementById('info-edit-fields').innerHTML = buildEditHTML(_currentUser, true);
}

/** POST /api/users/me/verify-password → 현재 비밀번호 서버 검증 */
async function verifyInfoPw() {
  if (!_currentUser) { toast('로그인이 필요합니다'); return; }
  const pw = document.getElementById('infoPwInput').value;
  const res = await api.post('/api/users/me/verify-password', { password: pw });
  if (!res.success) { document.getElementById('info-pw-err').style.display = 'block'; return; }
  document.getElementById('info-pw-step').style.display = 'none';
  document.getElementById('info-edit-form').style.display = 'block';
  document.getElementById('info-edit-fields').innerHTML = buildEditHTML(_currentUser, false);
}

/** PATCH /api/users/me + (선택) PATCH /api/users/me/password */
async function saveInfoEdit() {
  const n = document.getElementById('edit-name');
  if (!n || !n.value.trim()) { toast('이름을 입력해주세요'); return; }

  const bigEl    = document.getElementById('edit-region-big');
  const cityEl   = document.getElementById('edit-region-city');
  const province = bigEl?.value || '';
  const city     = (cityEl?.value && cityEl.value !== '시/군/구 선택') ? cityEl.value : '';
  const region   = province ? (city ? province + ' ' + city : province) : '';

  const genderChips = document.querySelectorAll('#info-edit-fields .chip-row');
  let gender = '';
  genderChips.forEach(row => {
    const onBtn = row.querySelector('.chip.on:not(.chip-sm)');
    if (onBtn) {
      const t = onBtn.textContent.trim();
      if (t === '남성') gender = 'M';
      else if (t === '여성') gender = 'F';
    }
  });

  const birthDate = document.getElementById('edit-birth')?.value || '';

  let mbti = '';
  document.querySelectorAll('#info-edit-fields .chip-row').forEach(row => {
    const onBtn = row.querySelector('.chip-sm.on');
    if (onBtn) mbti += onBtn.textContent.trim()[0];
  });

  const body = { name: n.value.trim() };
  if (region)            body.region    = region;
  if (gender)            body.gender    = gender;
  if (birthDate)         body.birthDate = birthDate;
  if (mbti.length === 4) body.mbti      = mbti;

  const res = await api.patch('/api/users/me', body);
  if (!res.success) { toast('⚠️ 정보 수정에 실패했습니다.'); return; }

  // 비밀번호 변경 (선택)
  const np = document.getElementById('edit-newpw');
  if (np && np.value) {
    if (np.value.length < 8) { toast('비밀번호는 8자 이상이어야 합니다'); return; }
    const np2 = document.getElementById('edit-newpw2');
    if (np2 && np.value !== np2.value) { toast('새 비밀번호가 일치하지 않습니다'); return; }
    const currentPw = document.getElementById('infoPwInput')?.value || '';
    const pwRes = await api.patch('/api/users/me/password', { currentPassword: currentPw, newPassword: np.value });
    if (!pwRes.success) { toast('⚠️ 비밀번호 변경에 실패했습니다.'); return; }
  }

  if (_currentUser) {
    _currentUser.name = n.value.trim();
    if (region)            _currentUser.region    = region;
    if (gender)            _currentUser.gender    = gender;
    if (birthDate)         _currentUser.birthDate = birthDate;
    if (mbti.length === 4) _currentUser.mbti      = mbti;
  }
  const av = document.getElementById('myAvatar'); if (av) av.textContent = n.value.trim()[0];
  const nm = document.getElementById('myName');   if (nm) nm.textContent = n.value.trim();

  if (_currentUser?.isSocial) {
    showSocialInfoEdit();
  } else {
    resetInfoStep();
  }
  toast('✅ 회원정보가 수정되었습니다.');
}

/* ───────────────────────────────────────────────
 * 8. 회원 탈퇴 (DELETE /api/users/me)  [v2 신규]
 * ─────────────────────────────────────────────── */
async function doWithdraw() {
  if (!_currentUser) { toast('로그인이 필요합니다'); return; }

  if (_currentUser.isSocial) {
    const inp    = document.getElementById('withdrawSocialInput');
    const errEl  = document.getElementById('withdraw-social-err');
    if (!inp || inp.value.trim() !== '탈퇴하겠습니다') {
      if (errEl) errEl.style.display = 'block';
      return;
    }
    if (errEl) errEl.style.display = 'none';
    document.getElementById('withdraw-confirm-modal').style.display = 'flex';

  } else {
    // 일반 계정: 비밀번호로 본인 확인
    const pw    = document.getElementById('withdrawPwInput')?.value;
    const errEl = document.getElementById('withdraw-pw-err');
    if (!pw) { toast('비밀번호를 입력해주세요'); return; }
    const res = await api.post('/api/users/me/verify-password', { password: pw });
    if (!res.success) {
      if (errEl) errEl.style.display = 'block';
      return;
    }
    if (errEl) errEl.style.display = 'none';
    document.getElementById('withdraw-confirm-modal').style.display = 'flex';
  }
}

async function confirmWithdraw() {
  closeWithdrawModal();
  const res = await api.del('/api/users/me');
  if (!res.success) {
    toast('⚠️ ' + (res.message || '탈퇴 처리 중 오류가 발생했습니다.'));
    return;
  }
  toast('탈퇴가 완료되었습니다.');
  doLogout();
}

function closeWithdrawModal() {
  document.getElementById('withdraw-confirm-modal').style.display = 'none';
}

/* ───────────────────────────────────────────────
 * 9. 알림 (GET /api/notifications)
 * ─────────────────────────────────────────────── */

async function _loadNotifications() {
  if (!_loggedIn) return;
  const res = await api.get('/api/notifications');
  _userNotifs = (res.success && res.data) ? res.data : [];
  updateNotifBadge();
}

function updateNotifBadge() {
  const b = document.getElementById('notifBadge');
  if (!b) return;
  const cnt = _userNotifs.filter(n => !n.isRead).length;
  b.style.display = cnt > 0 ? '' : 'none';
}

async function openNotificationPopup() {
  if (!_loggedIn) { toast('로그인이 필요합니다'); return; }
  await _loadNotifications();
  renderNotifList();
  document.getElementById('notifOverlay').style.display = 'block';
  document.getElementById('notifPopup').style.display = 'block';
}

function closeNotifPopup() {
  document.getElementById('notifOverlay').style.display = 'none';
  document.getElementById('notifPopup').style.display = 'none';
}

// function renderNotifList() {
//   const list = document.getElementById('notifList');
//   if (!list) return;
//   if (!_userNotifs.length) {
//     list.innerHTML = '<div style="padding:20px;text-align:center;color:var(--text3);font-size:13px">새로운 알림이 없습니다.</div>';
//     return;
//   }
//   list.innerHTML = _userNotifs.map((n, i) => `
//     <div style="padding:12px;border-radius:9px;margin-bottom:6px;
//                 background:${n.isRead ? 'var(--cream)' : 'var(--sage-pale)'};
//                 border:1px solid ${n.isRead ? 'var(--border2)' : 'var(--sage-l)'}">
//       <div style="display:flex;align-items:flex-start;gap:9px">
//         <span style="font-size:18px;flex-shrink:0">📢</span>
//         <div style="flex:1;min-width:0">
//           <div style="font-size:12px;font-weight:700;margin-bottom:3px">${n.title || ''}</div>
//           <div style="font-size:12px;color:var(--text2);line-height:1.6">${n.content || ''}</div>
//           <div style="font-size:10px;color:var(--text3);margin-top:4px">${n.createdAt ? n.createdAt.substring(0,10) : ''}</div>
//         </div>
//         <button onclick="deleteNotif(${n.id})" style="background:none;border:none;cursor:pointer;color:var(--text3);font-size:14px;flex-shrink:0">✕</button>
//       </div>
//     </div>`).join('');
//
//   // PATCH /api/notifications/read-all
//   api.patch('/api/notifications/read-all', {}).then(() => updateNotifBadge());
// }
//
// /** PATCH /api/notifications/{notificationId}/read */
// async function deleteNotif(notifId) {
//   await api.patch('/api/notifications/' + notifId + '/read', {});
//   _userNotifs = _userNotifs.filter(n => n.id !== notifId);
//   renderNotifList();
//   updateNotifBadge();
// }

// 제미나이 추가
function renderNotifList() {
  const list = document.getElementById('notifList');
  if (!list) return;
  if (!_userNotifs.length) {
    list.innerHTML = '<div style="padding:20px;text-align:center;color:var(--text3);font-size:13px">새로운 알림이 없습니다.</div>';
    return;
  }
  list.innerHTML = _userNotifs.map((n) => `
    <div style="padding:12px;border-radius:9px;margin-bottom:6px;
                background:${n.isRead ? 'var(--cream)' : 'var(--sage-pale)'};
                border:1px solid ${n.isRead ? 'var(--border2)' : 'var(--sage-l)'}">
      <div style="display:flex;align-items:flex-start;gap:9px">
        <span style="font-size:18px;flex-shrink:0">📢</span>
        <div style="flex:1;min-width:0">
          <div style="font-size:12px;font-weight:700;margin-bottom:3px">${n.title || ''}</div>
          <div style="font-size:12px;color:var(--text2);line-height:1.6">${n.content || ''}</div>
          <div style="font-size:10px;color:var(--text3);margin-top:4px">${n.createdAt ? n.createdAt.substring(0,10) : ''}</div>
        </div>
        <button onclick="deleteNotif(${n.id})" style="background:none;border:none;cursor:pointer;color:var(--text3);font-size:14px;flex-shrink:0">✕</button>
      </div>
    </div>`).join('');

  // PATCH /api/notifications/read-all
  api.patch('/api/notifications/read-all', {}).then(() => {
    // 백엔드 처리가 성공하면 프론트엔드의 데이터도 전부 읽음 상태로 변경!
    _userNotifs.forEach(n => n.isRead = true);
    updateNotifBadge(); // 배지 업데이트 (알림 숫자 0으로 사라짐)
  });
}

/** 알림 개별 삭제 (DELETE /api/notifications/{notificationId}) */
async function deleteNotif(notifId) {
  // PATCH(읽음) 대신 DELETE(삭제) API를 호출하도록 변경하는 것이 자연스럽습니다.
  await api.delete('/api/notifications/' + notifId);

  // 성공적으로 삭제되면 화면 목록에서도 지우고 다시 그리기
  _userNotifs = _userNotifs.filter(n => n.id !== notifId);
  renderNotifList();
}
// 여기까지

/* ───────────────────────────────────────────────
 * 10. 지도 장소 팝업 (GET /api/maps/places)
 * ─────────────────────────────────────────────── */

/** GET /api/maps/places?keyword={key} */
async function showMapPlacePopup(key, type) {
  const modal = document.getElementById('mapPlaceModal');
  const tl = type === 'stay' ? '🏨 숙소' : type === 'food' ? '🍽️ 맛집' : '📍 관광지';
  document.getElementById('mpPlace').textContent = key;
  document.getElementById('mpType').textContent = tl;
  document.getElementById('mpReviews').innerHTML = '<div style="text-align:center;padding:20px;color:var(--text3)">불러오는 중...</div>';
  document.getElementById('mpLinks').innerHTML = getMapLinks(key);
  modal.classList.add('open');

  // 길찾기 버튼 (MAP_PINS 좌표 기반)
  const naviEl = document.getElementById('mpNavi');
  if (naviEl) {
    const pin = (typeof MAP_PINS !== 'undefined') ? MAP_PINS.find(p => p.key === key) : null;
    const displayName = (typeof PLACE_REVIEWS !== 'undefined' && PLACE_REVIEWS[key])
      ? PLACE_REVIEWS[key].name : key;
    naviEl.innerHTML = pin
      ? `<a href="https://map.kakao.com/link/to/${encodeURIComponent(displayName)},${pin.lat},${pin.lng}" target="_blank"
           style="display:block;width:100%;padding:10px;border-radius:9px;background:#FEE500;color:#3C1E1E;
           text-decoration:none;text-align:center;font-size:13px;font-weight:700;margin-bottom:12px;box-sizing:border-box">🚗 카카오맵으로 길찾기</a>`
      : '';
  }

  const res = await api.get('/api/maps/places?keyword=' + encodeURIComponent(key));
  if (!res.success || !res.data || !res.data.length) {
    document.getElementById('mpReviews').innerHTML = '<div style="text-align:center;padding:20px;color:var(--text3)">아직 후기가 없습니다.</div>';
    return;
  }

  const place = res.data[0];
  document.getElementById('mpPlace').textContent = place.name || key;

  const reviews = place.reviews || [];
  if (!reviews.length) {
    document.getElementById('mpReviews').innerHTML = '<div style="text-align:center;padding:20px;color:var(--text3)">아직 후기가 없습니다.</div>';
    return;
  }

  let h = `<div style="font-size:13px;font-weight:700;margin-bottom:12px;color:var(--sage-d)">💬 방문 후기 (${reviews.length}개)</div>`;
  reviews.forEach(r => {
    const stars = '★'.repeat(Math.floor(r.rating || 0)) + '☆'.repeat(5 - Math.floor(r.rating || 0));
    h += `<div style="background:var(--cream);border-radius:10px;padding:12px;margin-bottom:9px;cursor:pointer"
               onclick="showReviewDetail('${(place.name||key).replace(/'/g,"\\'")}','${tl}','${stars} ${r.rating}','${(r.content||'').replace(/'/g,"\\'")}')">
      <div style="display:flex;align-items:center;gap:8px;margin-bottom:7px">
        <div style="width:26px;height:26px;border-radius:50%;background:var(--sage);color:#fff;display:flex;align-items:center;justify-content:center;font-size:11px;font-weight:700">${(r.reviewerName||'?')[0]}</div>
        <span style="font-size:12px;font-weight:700">${r.reviewerName||'익명'}</span>
        <span style="color:#F5A623;font-size:12px">${stars}</span>
        <span style="font-size:11px;font-weight:700">${r.rating||''}</span>
      </div>
      <p style="font-size:12px;color:var(--text2);line-height:1.6;display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;overflow:hidden">${r.content||''}</p>
      <div style="font-size:10px;color:var(--sage);margin-top:4px">클릭하여 전체 보기 →</div>
    </div>`;
  });
  document.getElementById('mpReviews').innerHTML = h;
  document.getElementById('mpLinks').innerHTML = getMapLinks(place.name || key);
}

function getMapLinks(q) {
  const e = encodeURIComponent(q);
  return `<a href="https://map.naver.com/v5/search/${e}" target="_blank" style="flex:1;display:flex;align-items:center;justify-content:center;gap:5px;padding:9px;border-radius:9px;background:#03C75A;color:#fff;text-decoration:none;font-size:12px;font-weight:700">🗺️ 네이버 지도</a>`
      + `<a href="https://map.kakao.com/?q=${e}" target="_blank" style="flex:1;display:flex;align-items:center;justify-content:center;gap:5px;padding:9px;border-radius:9px;background:#FEE500;color:#3C1E1E;text-decoration:none;font-size:12px;font-weight:700">🗺️ 카카오맵</a>`
      + `<a href="https://www.google.com/maps/search/${e}" target="_blank" style="flex:1;display:flex;align-items:center;justify-content:center;gap:5px;padding:9px;border-radius:9px;background:#4285F4;color:#fff;text-decoration:none;font-size:12px;font-weight:700">🗺️ 구글 맵</a>`;
}

function showReviewDetail(place, type, stars, text) {
  document.getElementById('rdPlace').textContent = place;
  document.getElementById('rdType').textContent  = type;
  document.getElementById('rdStars').textContent = stars;
  document.getElementById('rdText').textContent  = text;
  document.getElementById('reviewDetailModal').classList.add('open');
}

/* ───────────────────────────────────────────────
 * 11. AI 챗봇 (Chat Domain)
 * POST /api/chat/sessions  → sessionId 생성
 * POST /api/chat/message   → AI 응답
 * ─────────────────────────────────────────────── */

/** POST /api/chat/sessions : 플래너 입력 후 챗봇 세션 생성 */
async function startChatWithSummary() {
  if (typeof updateSummaryCard === 'function') updateSummaryCard();
  const dest   = (document.getElementById('sum-dest')   || {}).textContent || '-';
  const ppl    = (document.getElementById('sum-people') || {}).textContent || '-';
  const budget = (document.getElementById('sum-budget') || {}).textContent || '-';
  const msgs   = document.getElementById('chatMsgs');
  if (!msgs) return;
  msgs.innerHTML = '';

  const tripId = window._currentTripId || null;
  const sesRes = await api.post('/api/chat/sessions', { planId: tripId });
  if (sesRes.success && sesRes.data) {
    _chatSessionId = sesRes.data.sessionId;
  }

  addBubble(
      `입력 정보를 정리해드릴게요 📋<br><br>📍 <strong>여행지:</strong> ${dest}<br>👥 <strong>인원:</strong> ${ppl}<br>💰 <strong>예산:</strong> ${budget}<br><br>위 정보를 바탕으로 최적의 여행 일정을 만들어드리겠습니다!`,
      'bot',
      ['일정 생성하기', '추가 요청 있어요', '예산 조정할게요']
  );
}

function startChat() {
  const msgs = document.getElementById('chatMsgs');
  if (!msgs) return;
  msgs.innerHTML = '';
  addBubble('안녕하세요! AI 여행 플래너입니다 ✈<br>추가로 원하시는 내용이 있으시면 말씀해주세요!', 'bot', ['반려동물 없음','🐕 강아지','일정 생성']);
}

/* ───────────────────────────────────────────────
 * 10. AI 챗봇 (Chat Domain)
 * POST /api/chat/message   → AI 응답
 * ─────────────────────────────────────────────── */

/** POST /api/chat/message : 메시지 전송 + AI 응답 수신 */
async function sendMsg() {
  const inp = document.getElementById('chatInp');
  const txt = inp.value.trim();
  if (!txt) return;

  // 1. 내가 보낸 메시지 화면에 그리기
  addBubble(txt, 'user');
  inp.value = '';

// 2_1. 해외 여행지 1차 차단 (프론트) - 로딩 띄우기 전에 먼저 체크
    const overseasKeywords = ['일본','도쿄','오사카','미국','뉴욕','파리','유럽','방콕','베트남','싱가포르','홍콩','대만','중국'];
    if (overseasKeywords.some(k => txt.includes(k))) {
        addBubble('본 서비스는 국내 전용입니다. 국내 도시를 입력해 주세요', 'bot');
        inp.disabled = false;
        inp.focus();
        return;
    }

// 2_2. AI 로딩 애니메이션 띄우기 및 입력창 잠금
    const msgs = document.getElementById('chatMsgs');
    const loadingId = 'loading-' + Date.now();
    const loadingDiv = document.createElement('div');
    loadingDiv.id = loadingId;
    loadingDiv.className = 'cmsg';
    loadingDiv.innerHTML = `
  <div class="cav bot">🤖</div>
  <div>
    <div class="cbubble bot" style="display:flex; align-items:center; gap:4px; min-height: 38px;">
      <div class="typing-dot"></div>
      <div class="typing-dot"></div>
      <div class="typing-dot"></div>
    </div>
  </div>`;
    msgs.appendChild(loadingDiv);
    msgs.scrollTop = msgs.scrollHeight;
    inp.disabled = true;

  // app_main.js 상단에 정의된 전역 변수 _chatSessionId 사용
  const sessionId = _chatSessionId;

  try {
    const payload = {
      sessionId: _chatSessionId,
      message: txt
    };

    // 공통 api.post 래퍼 사용
    const res = await api.post('/api/chat/message', payload);

    // 3. 서버 응답이 오면 로딩 말풍선 삭제
    const loadingEl = document.getElementById(loadingId);
    if (loadingEl) loadingEl.remove();

    // =================================================================
    // 4. 진짜 AI 답변 띄우기 및 UI 자동 업데이트
    // =================================================================
    if (res && res.success && res.data && res.data.response) {
      let replyText = res.data.response;

      // (1) 정규식으로 AI가 몰래 보낸 [UPDATE:항목코드:새로운값] 태그 찾기
      const updateRegex = /\[UPDATE:([A-Za-z가-힣0-9]+):([^\]]*)\]/g;
      let match;
      const _patchFields = {};
      while ((match = updateRegex.exec(replyText)) !== null) {
        const field = match[1];
        const newVal = match[2].trim();
        if (!newVal) {
          // 빈 값일 경우에 EXTRA 행 삭제 할 수 있도록 신호를 보내준다
          const extraRow = document.getElementById('sum-extra-' + field);
          if (extraRow) extraRow.remove();
          continue;
        }

        // (2) 코드를 요약 패널의 HTML ID와 매핑
        const domMap = {
          'DEST': 'sum-dest', 'DATE': 'sum-date', 'PEOPLE': 'sum-people',
          'BUDGET': 'sum-budget', 'TRANS': 'sum-trans', 'ACC': 'sum-acc',
          'STYLE': 'sum-style', 'DENSITY': 'sum-density', 'PET': 'sum-pet'
        };

        const domId = domMap[field];
        if (domId) {
          const el = document.getElementById(domId);
          if (el) {
            el.textContent = newVal;
            el.classList.remove('missing');
            el.style.color = 'var(--sage)';
            el.style.fontWeight = '800';
            setTimeout(() => { el.style.color = ''; el.style.fontWeight = ''; }, 2000);
            reSyncChipFromUpdate(field, newVal);
            // BUDGET 변경 시 s1-budget 입력값도 동기화 → updateSummaryCard 재호출 시 덮어쓰기 방지
            if (field === 'BUDGET') {
              const s1b = document.getElementById('s1-budget');
              if (s1b) s1b.value = newVal.replace(/[^0-9]/g, '');
            }
          }
        }

        // DB 업데이트용 필드 수집
        const _apiMap = {
          'BUDGET': 'budget', 'TRANS': 'transportType',
          'ACC': 'accommodationType', 'DENSITY': 'scheduleDensity', 'DEST': 'destination',
          'PEOPLE': 'companionCount',
          'PET': 'hasPet'
        };
        const _af = _apiMap[field];
        if (_af) _patchFields[_af] = (_af === 'budget') ? newVal.replace(/[^0-9]/g, '') : newVal;
      }

      // UPDATE 태그가 있으면 DB에 일괄 PATCH
      const _tid = window._currentTripId;
      if (_tid && Object.keys(_patchFields).length > 0) {
        api.patch(`/api/trips/${_tid}/input-form`, _patchFields);
      }

      const extraRegex = /\[EXTRA:([^:\]]+):([^\]]+)\]/g;
      let extraMatch;
      const extraContainer = document.getElementById('sum-extra-rows');
      while ((extraMatch = extraRegex.exec(replyText)) !== null) {
        const label = extraMatch[1].trim();
        const value = extraMatch[2].trim();
        if (!extraContainer || !label || !value) continue;

        const rowId = 'sum-extra-' + label;
        let row = document.getElementById(rowId);
        if (!row) {
          row = document.createElement('div');
          row.id = rowId;
          row.className = 'asc-row';
          row.innerHTML = `<div class="asc-label">${label}</div><div class="asc-val">${value}</div><button class="asc-del" onclick="event.stopPropagation();
                clearExtraRow('${label.replace(/'/g,"\\'")}')">✕</button>`;
          extraContainer.appendChild(row);
        } else {
          row.querySelector('.asc-val').textContent = value;
        }
      }
      const _allExtraRows = document.querySelectorAll('#sum-extra-rows .asc-row');
      if (_allExtraRows.length > 0) {
        // empty 안내문 숨기기
        const emptyMsg = document.getElementById('extra-empty-msg');
        if (emptyMsg) emptyMsg.style.display = 'none';
        // 현재 추가사항 탭이 아니면 badge 표시
        const currentTab = document.querySelector('.asc-tab.on');
        if (currentTab && currentTab.dataset.tab !== 'tab-extra') {
          const badge = document.getElementById('extra-badge');
          if (badge) badge.style.display = 'inline-block';
        }
        if (window._currentTripId) {
          const extraArr = [..._allExtraRows].map(r => ({
            label: r.querySelector('.asc-label')?.textContent || '',
            value: r.querySelector('.asc-val')?.textContent  || ''
          }));
          api.patch(`/api/trips/${window._currentTripId}/input-form`, {
            extraNotes: JSON.stringify(extraArr)
          });
        }
      }

// (4) 모든 태그 제거 후 말풍선에 표시
      replyText = replyText.replace(/\[UPDATE:[A-Za-z가-힣0-9]+:[^\]]*\]/g, '').trim();
      replyText = replyText.replace(/\[EXTRA:[^\]]+\]/g, '').trim();
      addBubble(replyText, 'bot');
    } else {
      console.error('[Chat] 응답 오류:', res);
      addBubble('죄송합니다, 잠시 후 다시 시도해주세요.', 'bot');
    }
  } catch (e) {
    console.error('[Chat] 통신 예외:', e);
    const loadingEl = document.getElementById(loadingId);
    if (loadingEl) loadingEl.remove();
    addBubble('서버와 통신 중 문제가 발생했습니다.', 'bot');
  } finally {
    // 5. 처리가 끝나면 다시 입력창 열기
    inp.disabled = false;
    inp.focus();
  }
}
/* 요약 패널 항목 삭제 */
function clearSumField(key) {
  const map = {
    dep:     { el:'sum-dep',     clear(){['dep-prov','dep-city'].forEach(id=>{const e=document.getElementById(id);if(e)e.value='';});}, api:{departure:''} },
    dest:    { el:'sum-dest',    clear(){['dest-prov','dest-city'].forEach(id=>{const e=document.getElementById(id);if(e)e.value='';});}, api:{destination:''} },
    date:    { el:'sum-date',    clear(){['s1-date-start','s1-date-end'].forEach(id=>{const e=document.getElementById(id);if(e)e.value='';});if(typeof updateWeatherBtn==='function')updateWeatherBtn();}, api:null },
    people:  { el:'sum-people',  clear(){const e=document.getElementById('s1-pax');if(e)e.value='';}, api:{companionCount:0} },
    comp:    { el:'sum-comp',    clear(){document.querySelectorAll('#chip-comp .chip').forEach(c=>c.classList.remove('on'));const o=document.getElementById('other-comp');if(o){o.value='';o.classList.remove('show');}}, api:{companionType:''} },
    budget:  { el:'sum-budget',  clear(){const e=document.getElementById('s1-budget');if(e)e.value='';}, api:{budget:0} },
    trans:   { el:'sum-trans',   clear(){document.querySelectorAll('#chip-trans .chip').forEach(c=>c.classList.remove('on'));const o=document.getElementById('other-trans');if(o){o.value='';o.classList.remove('show');}}, api:{transportType:''} },
    acc:     { el:'sum-acc',     clear(){document.querySelectorAll('#chip-acc .chip').forEach(c=>c.classList.remove('on'));const o=document.getElementById('other-acc');if(o){o.value='';o.classList.remove('show');}}, api:{accommodationType:''} },
    accopts: { el:'sum-accopts', clear(){document.querySelectorAll('#chip-accopts .chip').forEach(c=>c.classList.remove('on'));const o=document.getElementById('other-accopts');if(o){o.value='';o.classList.remove('show');}}, api:{accommodationOptions:'[]'} },
    style:   { el:'sum-style',   clear(){document.querySelectorAll('#chip-style .chip').forEach(c=>c.classList.remove('on'));const o=document.getElementById('other-style');if(o){o.value='';o.classList.remove('show');}}, api:{travelStyles:'[]'} },
    food:    { el:'sum-food',    clear(){document.querySelectorAll('#chip-food .chip').forEach(c=>c.classList.remove('on'));['other-allergy','other-food'].forEach(id=>{const o=document.getElementById(id);if(o){o.value='';o.classList.remove('show');}});}, api:{dietaryInfo:'[]'} },
    density: { el:'sum-density', clear(){document.querySelectorAll('#chip-density .chip').forEach(c=>c.classList.remove('on'));}, api:{scheduleDensity:''} },
    pet:     { el:'sum-pet',     clear(){[...document.querySelectorAll('#chip-special .chip')].filter(c=>c.textContent.includes('반려동물')).forEach(c=>c.classList.remove('on'));}, api:{hasPet:0} },
    special: { el:'sum-special', clear(){[...document.querySelectorAll('#chip-special .chip')].filter(c=>!c.textContent.includes('반려동물')).forEach(c=>c.classList.remove('on'));const o=document.getElementById('other-special');if(o){o.value='';o.classList.remove('show');}}, api:null },
  };
  const cfg = map[key];
  if (!cfg) return;
  const el = document.getElementById(cfg.el);
  if (el) el.textContent = '—';
  cfg.clear();
  if (typeof updateSummaryCard === 'function') updateSummaryCard();
  if (cfg.api && window._currentTripId) api.patch(`/api/trips/${window._currentTripId}/input-form`, cfg.api);
  toast('항목이 초기화되었습니다');
}

/* 추가사항 행 삭제 */
function clearExtraRow(label) {
  const row = document.getElementById('sum-extra-' + label);
  if (row) row.remove();
  const remaining = document.querySelectorAll('#sum-extra-rows .asc-row');
  const emptyMsg = document.getElementById('extra-empty-msg');
  if (emptyMsg) emptyMsg.style.display = remaining.length === 0 ? '' : 'none';
  if (window._currentTripId) {
    const extraArr = [...remaining].map(r => ({
      label: r.querySelector('.asc-label')?.textContent || '',
      value: r.querySelector('.asc-val')?.textContent  || ''
    }));
    api.patch(`/api/trips/${window._currentTripId}/input-form`, { extraNotes: JSON.stringify(extraArr) });
  }
  toast('항목이 삭제되었습니다');
}

/*  챗봇 UPDATE 수신 시 칩 재선택  */
function reSyncChipFromUpdate(field, value) {
  if (!value || value === '—') return;
  // 단일 선택 칩
  const singleMap = {
    TRANS: { group:'#chip-trans', other:'other-trans' },
    ACC:   { group:'#chip-acc',   other:'other-acc'   },
    DENSITY: { group:'#chip-density' },
  };
  if (singleMap[field]) {
    const { group, other } = singleMap[field];
    const isOther = value.startsWith('기타(');
    const label   = isOther ? '기타' : value;
    document.querySelectorAll(group + ' .chip').forEach(c => c.classList.toggle('on', c.textContent.trim() === label));
    if (isOther && other) { const el = document.getElementById(other); if(el){el.value=value.slice(3,-1);el.classList.add('show');} }
    return;
  }
  // 스타일 (다중)
  if (field === 'STYLE') {
    document.querySelectorAll('#chip-style .chip').forEach(c => c.classList.remove('on'));
    value.split(',').map(s => s.trim()).forEach(v => {
      const isOther = v.startsWith('기타('), label = isOther ? '기타' : v;
      const chip = [...document.querySelectorAll('#chip-style .chip')].find(c => c.textContent.trim() === label);
      if (chip) { chip.classList.add('on'); if(isOther){const el=document.getElementById('other-style');if(el){el.value=v.slice(3,-1);el.classList.add('show');}} }
    });
    return;
  }
  // 반려동물
  if (field === 'PET') {
    [...document.querySelectorAll('#chip-special .chip')].filter(c => c.textContent.includes('반려동물')).forEach(c => c.classList.toggle('on', value === '동반'));
    return;
  }
  // 여행지 셀렉트 동기화
  if (field === 'DEST') {
    const parts = value.split(' '), provSel = document.getElementById('dest-prov');
    if (provSel && parts[0]) {
      for(let i=0;i<provSel.options.length;i++){if(provSel.options[i].text===parts[0]){provSel.selectedIndex=i;if(typeof updateCityDest==='function')updateCityDest(provSel);break;}}
      if(parts[1]) setTimeout(()=>{const c=document.getElementById('dest-city');if(c)for(let i=0;i<c.options.length;i++){if(c.options[i].text===parts[1]){c.selectedIndex=i;break;}}},50);
    }
  }
  // 인원 / 예산 input 동기화
  if (field === 'PEOPLE') { const e=document.getElementById('s1-pax');    if(e) e.value=value.replace(/[^0-9]/g,''); }
  if (field === 'BUDGET') { const e=document.getElementById('s1-budget'); if(e) e.value=value.replace(/[^0-9]/g,''); }
}
// 요약 패널 탭 전환
function switchSumTab(panelId) {
  document.querySelectorAll('.asc-tab').forEach(t =>
      t.classList.toggle('on', t.dataset.tab === panelId)
  );
  document.querySelectorAll('.asc-tab-panel').forEach(p =>
      p.style.display = p.id === panelId ? 'block' : 'none'
  );
  if (panelId === 'tab-extra') {
    const badge = document.getElementById('extra-badge');
    if (badge) badge.style.display = 'none';
  }
}
async function openSummaryEdit(field, domId, label) {
  const el = document.getElementById(domId);
  if (!el) return;

  const current = el.textContent.trim() === '—' ? '' : el.textContent.trim();
  const newVal = prompt(`${label} 수정:`, current);
  if (newVal === null || newVal.trim() === '' || newVal.trim() === current) return;

  const trimmed = newVal.trim();

  // 1. UI 즉시 반영
  el.textContent = trimmed;
  el.classList.remove('missing');
  el.style.color = 'var(--sage)';
  el.style.fontWeight = '800';
  setTimeout(() => { el.style.color = ''; el.style.fontWeight = ''; }, 2000);

  // 2. DB PATCH — PLAN_INPUT_FORM 업데이트
  const tripId = window._currentTripId;
  if (tripId) {
    const patchBody = {};
    if (field === 'budget') {
      patchBody[field] = String(trimmed.replace(/[^0-9]/g, ''));
    } else {
      patchBody[field] = trimmed;
    }
    await api.patch(`/api/trips/${tripId}/input-form`, patchBody);
  }

  // 3. 숨은 컨텍스트 LLM 주입 — SYSTEM 메시지로 DB 저장
  if (_chatSessionId) {
    await api.post('/api/chat/message', {
      sessionId: _chatSessionId,
      message: `[시스템 메시지: 사용자가 ${label}을(를) "${trimmed}"(으)로 수동 변경했습니다]`,
      isSystem: true
    });
  }

  toast(`✅ ${label} 수정 완료`);
}

function addBubble(txt, role, qrs) {
  const msgs = document.getElementById('chatMsgs');
  const d = document.createElement('div');
  d.className = 'cmsg' + (role === 'user' ? ' user' : '');
  if (role === 'bot') {
    // 마침표/물음표 뒤 줄바꿈 처리
    const formatted = txt
        .replace(/\. ([가-힣A-Za-z0-9])/g, '.<br>$1')
        .replace(/\? ([가-힣A-Za-z0-9])/g, '?<br>$1');
    d.innerHTML = `<div class="cav bot">🤖</div><div><div class="cbubble bot">${formatted}</div>`
        + (qrs ? '<div class="qr-row">' + qrs.map(q => `<button class="qr-btn" onclick="document.getElementById('chatInp').value='${q}';sendMsg()">${q}</button>`).join('') + '</div>' : '')
        + '</div>';
  } else {
    d.innerHTML = `<div class="cav user">나</div><div><div class="cbubble user">${txt}</div></div>`;
  }
  msgs.appendChild(d);
  msgs.scrollTop = msgs.scrollHeight;
}
/* ───────────────────────────────────────────────
 * 12. 회원가입 유효성 검사 (실시간 API 중복확인)
 * GET /api/auth/check-username?username=
 * GET /api/auth/check-email?email=
 * ─────────────────────────────────────────────── */
let _unameTimer = null, _emailTimer = null;

function checkUname(inp) {
  const v = inp.value.trim();
  const m = document.getElementById('uname-msg');
  if (!v) { m.textContent = ''; inp.className = 'form-input'; return; }
  if (v.length > 20) { sv(inp, m, 'err', '20자 이내로 입력해주세요'); return; }
  clearTimeout(_unameTimer);
  _unameTimer = setTimeout(async () => {
    const res = await api.get('/api/auth/check-username?username=' + encodeURIComponent(v));
    if (res.success && res.data) {
      res.data.available ? sv(inp, m, 'ok', '사용 가능한 아이디입니다') : sv(inp, m, 'err', '이미 사용 중인 아이디입니다');
    }
  }, 350);
}

function checkEmail(inp) {
  const v = inp.value.trim();
  const m = document.getElementById('email-msg');
  if (!v) { m.textContent = ''; inp.className = 'form-input'; return; }
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v)) { sv(inp, m, 'err', '이메일 형식이 올바르지 않습니다'); return; }
  clearTimeout(_emailTimer);
  _emailTimer = setTimeout(async () => {
    const res = await api.get('/api/auth/check-email?email=' + encodeURIComponent(v));
    if (res.success && res.data) {
      res.data.available ? sv(inp, m, 'ok', '사용 가능한 이메일입니다') : sv(inp, m, 'err', '이미 가입된 이메일입니다');
    }
  }, 350);
}

function checkPw(inp) {
  const v=inp.value, m=document.getElementById('pw-msg'), bar=document.getElementById('pw-bar');
  let sc=0; if(v.length>=8)sc++; if(/[A-Za-z]/.test(v))sc++; if(/[0-9]/.test(v))sc++; if(/[^A-Za-z0-9]/.test(v))sc++;
  const cols=['#e5e7eb','var(--coral)','#F5A623','var(--sage)','var(--sage-d)'], lbls=['','약함','보통','강함','매우 강함'];
  if(bar){bar.style.width=(sc*25)+'%';bar.style.background=cols[sc];}
  if(m){m.textContent=sc?'강도: '+lbls[sc]:'';m.className='form-msg '+(sc<3?'err':'ok');}
}
function checkPw2(inp) {
  const pw=document.querySelector('#page-signup input[type=password]'); if(!pw) return;
  const m=document.getElementById('pw2-msg'); if(!inp.value){m.textContent='';return;}
  inp.value===pw.value ? sv(inp,m,'ok','비밀번호가 일치합니다') : sv(inp,m,'err','비밀번호가 일치하지 않습니다');
}
function sv(inp, m, type, txt) { inp.className='form-input '+type; m.className='form-msg '+type; m.textContent=txt; }

/* ───────────────────────────────────────────────
 * 13. 비밀번호 재설정
 * POST /api/auth/password/reset-request
 * PATCH /api/auth/password/reset
 * ─────────────────────────────────────────────── */
let _pwTimer = null;

function openPwReset()  { document.getElementById('pwResetModal').classList.add('open'); }
function closePwReset() { document.getElementById('pwResetModal').classList.remove('open'); clearInterval(_pwTimer); }

async function sendPwEmail() {
  const id = document.getElementById('pr-id').value.trim();
  const em = document.getElementById('pr-email').value.trim();
  if (!id || !em) { toast('아이디와 이메일을 모두 입력해주세요'); return; }

  const res = await api.post('/api/auth/password/reset-request', { email: em });
  if (!res.success) { toast('⚠️ ' + (res.message || '이메일 발송에 실패했습니다.')); return; }

  document.getElementById('pr-step1').style.display = 'none';
  document.getElementById('pr-step2').style.display = 'block';
  let sec = 180;
  const timerEl = document.getElementById('pr-timer');
  timerEl.textContent = '(' + Math.floor(sec/60) + ':' + String(sec%60).padStart(2,'0') + ')';
  _pwTimer = setInterval(() => {
    sec--;
    timerEl.textContent = '(' + Math.floor(sec/60) + ':' + String(sec%60).padStart(2,'0') + ')';
    if (sec <= 0) { clearInterval(_pwTimer); timerEl.textContent = '(만료)'; timerEl.style.color = 'var(--coral)'; }
  }, 1000);
  toast('인증 메일이 발송되었습니다.');
}

function verifyCode() {
  const code = document.getElementById('pr-code').value.trim();
  if (!code) { toast('인증코드를 입력해주세요'); return; }
  document.getElementById('pr-step2').style.display = 'none';
  document.getElementById('pr-step3').style.display = 'block';
  clearInterval(_pwTimer);
}

async function setPwNew() {
  const np = document.getElementById('pr-new-pw').value;
  const cp = document.getElementById('pr-confirm-pw').value;
  const tk = document.getElementById('pr-code').value.trim();
  if (np !== cp) { toast('비밀번호가 일치하지 않습니다'); return; }
  if (np.length < 8) { toast('8자 이상 입력해주세요'); return; }

  const res = await api.patch('/api/auth/password/reset', { token: tk, newPassword: np });
  if (res.success) {
    closePwReset();
    toast('비밀번호가 성공적으로 변경되었습니다!');
  } else {
    toast('⚠️ ' + (res.message || '비밀번호 변경에 실패했습니다.'));
  }
}

/* ───────────────────────────────────────────────
 * 14. 커뮤니티 (Post Domain — 공통 함수)
 * ─────────────────────────────────────────────── */
function checkAndOpenWrite() {
  if (_isSuspended) { toast('⛔ 해당 계정은 커뮤니티 기능이 제한되었습니다.'); return; }
  document.getElementById('writeModal').classList.add('open');
}
function openWriteModal()  { document.getElementById('writeModal').classList.add('open'); }
function closeWrite()      { document.getElementById('writeModal').classList.remove('open'); }
function submitComment()   { if (_isSuspended) { toast('⛔ 해당 계정은 커뮤니티 기능이 제한되었습니다.'); return; } toast('댓글이 등록되었습니다!'); }
function closeSuspendedAlert() { document.getElementById('suspendedAlert').classList.remove('open'); go('mypage'); }

function filterByTag(tag, btn) {
  if (_activeTags.has(tag)) { _activeTags.delete(tag); if(btn) btn.classList.remove('active-tag'); }
  else                       { _activeTags.add(tag);    if(btn) btn.classList.add('active-tag');    }
  applyTagFilter();
  if (_activeTags.size === 0) toast('필터 해제됨');
  else toast('#' + [..._activeTags].join(' #') + ' 필터 중');
}

function applyTagFilter() {
  document.querySelectorAll('.comm-post-item').forEach(item => {
    if (_activeTags.size === 0) { item.style.display = ''; return; }
    const tags  = (item.getAttribute('data-tags') || '').toLowerCase();
    const match = [..._activeTags].some(t => tags.includes(t.toLowerCase()));
    item.style.display = match ? '' : 'none';
  });
}

function doSearch() {
  const type = document.getElementById('searchType').value;
  const q    = document.getElementById('searchInp').value.trim().toLowerCase();
  if (!q) { toast('검색어를 입력해주세요'); return; }
  let found = 0;
  document.querySelectorAll('.comm-post-item').forEach(item => {
    const title  = item.querySelector('.post-ttl')?.textContent.toLowerCase() || '';
    const tags   = (item.getAttribute('data-tags')   || '').toLowerCase();
    const author = (item.getAttribute('data-author') || '').toLowerCase();
    let match = false;
    if      (type === 'title')   match = title.includes(q);
    else if (type === 'content') match = title.includes(q) || tags.includes(q);
    else if (type === 'author')  match = author.includes(q);
    else                         match = tags.includes(q);
    item.style.display = match ? '' : 'none';
    if (match) found++;
  });
  toast(`"${q}" 검색 결과: ${found}건`);
}

function sortPosts(val) {
  ['tab-route','tab-stay','tab-food','tab-tour','tab-cafe'].forEach(tabId => {
    const tab = document.getElementById(tabId); if (!tab) return;
    const items = Array.from(tab.querySelectorAll('.comm-post-item')); if (!items.length) return;
    items.sort((a, b) => {
      const al=parseInt(a.getAttribute('data-likes')||'0'), bl=parseInt(b.getAttribute('data-likes')||'0');
      const as=parseInt(a.getAttribute('data-scrap')||'0'), bs=parseInt(b.getAttribute('data-scrap')||'0');
      const ad=parseInt(a.getAttribute('data-date') ||'0'), bd=parseInt(b.getAttribute('data-date') ||'0');
      if (val==='likes'||val==='saved') return bl-al;
      if (val==='scrap')  return bs-as;
      if (val==='latest') return bd-ad;
      return 0;
    });
    items.forEach(el => tab.appendChild(el));
  });
  toast('정렬: ' + val);
}

function setCommTab(btn, cat) {
  document.querySelectorAll('#commTabs .comm-tab').forEach(b => b.classList.remove('on'));
  btn.classList.add('on');
  ['route','stay','food','tour','cafe'].forEach(c => { const el=document.getElementById('tab-'+c); if(el) el.style.display='none'; });
  const t = document.getElementById('tab-' + cat); if(t) t.style.display = 'block';
  const s = document.getElementById('sortSelect');
  if (s) {
    if (cat === 'route') s.innerHTML = '<option value="likes">좋아요순</option><option value="scrap" selected>스크랩순</option><option value="latest">최신순</option>';
    else                 s.innerHTML = '<option value="saved" selected>담긴 순</option><option value="scrap">스크랩순</option><option value="latest">최신순</option>';
  }
}

/* ───────────────────────────────────────────────
 * 15. 슬라이더
 * ─────────────────────────────────────────────── */
let _si = 0, _sn = 4;
function changeSlide(d) { goSlide((_si + d + _sn) % _sn); }
function goSlide(i) {
  document.querySelectorAll('.slide').forEach((s,j) => s.classList.toggle('on', j===i));
  document.querySelectorAll('.dot').forEach((d,j)  => d.classList.toggle('on', j===i));
  _si = i;
}

/* ───────────────────────────────────────────────
 * 16. AI 플래너 입력폼 + 일정 생성
 * POST /api/ai/schedule/generate
 * ─────────────────────────────────────────────── */
function startPlanFromCard(data) {
  if (!_loggedIn) {
    toast('⚠️ 로그인이 필요합니다. 로그인 후 이용해주세요.');
    openModal('modal-auth');
    return;
  }
  go('planner'); goPlanStep(1);
  const pr = document.getElementById('dest-prov');
  if (pr && data.prov) { for(let i=0;i<pr.options.length;i++){if(pr.options[i].text===data.prov){pr.value=pr.options[i].value||pr.options[i].text;break;}} updateCityDest(pr); }
  const sd=document.getElementById('sum-dest');    if(sd) sd.textContent = data.dest||'';
  const sp=document.getElementById('sum-people');  if(sp) sp.textContent = data.people?(data.people+'인'):'';
  const sb=document.getElementById('sum-budget');  if(sb) sb.textContent = data.budget?('₩'+data.budget.toLocaleString()):'';
  toast((data.dest||'') + ' 여행 플랜을 시작합니다 ✈');
}

function _validatePlanStep1() {
  const destProv  = document.getElementById('dest-prov');
  const dateStart = document.getElementById('s1-date-start');
  const dateEnd   = document.getElementById('s1-date-end');
  const pax       = document.getElementById('s1-pax');
  const budget    = document.getElementById('s1-budget');

  if (!destProv  || !destProv.value)               { toast('⚠️ 여행지(도/시)를 선택해주세요.');     return false; }
  if (!dateStart || !dateStart.value)               { toast('⚠️ 출발일을 입력해주세요.');           return false; }
  if (!dateEnd   || !dateEnd.value)                 { toast('⚠️ 귀환일을 입력해주세요.');           return false; }
  if (dateStart.value > dateEnd.value)              { toast('⚠️ 귀환일은 출발일 이후여야 합니다.'); return false; }
  if (!pax    || !pax.value    || +pax.value < 1)   { toast('⚠️ 인원을 1명 이상 입력해주세요.');    return false; }
  if (!budget || !budget.value || +budget.value < 1){ toast('⚠️ 총 예산을 입력해주세요.');          return false; }

  const transChips = document.querySelectorAll('#chip-trans .chip.on');
  if (transChips.length === 0) { toast('⚠️ 이동 수단을 선택해주세요.'); return false; }
  if ([...transChips].some(c => c.textContent.includes('기타'))) {
    if (!document.getElementById('other-trans')?.value.trim()) {
      toast('⚠️ 이동 수단(기타)을 입력해주세요.'); return false;
    }
  }
  // 동행자 기타 미입력 체크
  const compChip = document.querySelector('#chip-comp .chip.on');
  if (compChip && compChip.textContent.includes('기타')) {
    if (!document.getElementById('other-comp')?.value.trim()) {
      toast('⚠️ 동행자 유형(기타)을 입력해주세요.'); return false;
    }
  }
  return true;
}

function _validatePlanStep2() {
  const styleChips = document.querySelectorAll('#chip-style .chip.on');
  if (styleChips.length === 0) { toast('⚠️ 여행 스타일을 1개 이상 선택해주세요.'); return false; }
  if ([...styleChips].some(c => c.textContent.includes('기타'))) {
    if (!document.getElementById('other-style')?.value.trim()) {
      toast('⚠️ 여행 스타일(기타)을 입력해주세요.'); return false;
    }
  }
  const foodChips = document.querySelectorAll('#chip-food .chip.on');
  if ([...foodChips].some(c => c.textContent.includes('알러지'))) {
    if (!document.getElementById('other-allergy')?.value.trim()) {
      toast('⚠️ 알러지 정보를 입력해주세요.'); return false;
    }
  }
  if ([...foodChips].some(c => c.textContent.includes('기타'))) {
    if (!document.getElementById('other-food')?.value.trim()) {
      toast('⚠️ 식이 정보(기타)를 입력해주세요.'); return false;
    }
  }
  return true;
}

function _currentPlanStep() {
  for (let i = 1; i <= 3; i++) {
    const sb = document.getElementById('sb-' + i);
    if (sb && sb.classList.contains('active')) return i;
  }
  return 1;
}

function markPlanDirty() {
  if (!sessionStorage.getItem('ai_generated_route')) return;
  _planDirty = true;
  _syncStep4State();
}

function _syncStep4State() {
  const sb4 = document.getElementById('sb-4');
  if (!sb4) return;
  const hasRoute = !!sessionStorage.getItem('ai_generated_route');
  sb4.classList.remove('done', 'locked');
  if (_planDirty) {
    sb4.classList.add('locked');
  } else if (hasRoute) {
    sb4.classList.add('done');
  }
}

function _syncPlannerTopbar() {
  const hasDraft  = !!window._currentTripId;
  const onPlanner = document.getElementById('page-planner')?.classList.contains('active') ?? false;
  const onMap     = document.getElementById('page-map')?.classList.contains('active') ?? false;
  const showSteps = onPlanner || (onMap && hasDraft);

  const navSteps  = document.getElementById('navPlannerSteps');
  const sb4       = document.getElementById('sb-4');
  const stepBar   = document.getElementById('plannerStepBar');

  if (navSteps) navSteps.style.display = showSteps ? 'flex' : 'none';
  if (sb4)      sb4.style.display = hasDraft ? '' : 'none';
  if (stepBar)  stepBar.classList.toggle('steps-4', hasDraft);

  _syncStep4State();
  _syncNavStepHighlight();
}

function _syncNavStepHighlight() {
  const onMap    = document.getElementById('page-map')?.classList.contains('active') ?? false;
  const current  = onMap ? 4 : ((typeof _currentPlanStep === 'function') ? _currentPlanStep() : 1);
  const hasRoute = !!sessionStorage.getItem('ai_generated_route');

  for (let i = 1; i <= 4; i++) {
    const btn = document.getElementById('nps-' + i);
    if (!btn) continue;
    btn.classList.remove('active', 'done', 'locked');
    if (i === current) {
      btn.classList.add('active');
    } else if (i < current) {
      btn.classList.add('done');
    } else if (i === 4) {
      if (_planDirty)    btn.classList.add('locked');
      else if (hasRoute) btn.classList.add('done');
    }
  }
}

function goPlanStep(n) {
  if (!_loggedIn) {
    toast('⚠️ 로그인이 필요합니다. 로그인 후 이용해주세요.');
    openModal('modal-auth');
    return;
  }

  if (n === 4) {
    if (!sessionStorage.getItem('ai_generated_route')) {
      toast('⚠️ 먼저 AI 챗봇에서 일정을 생성해주세요.');
      return;
    }
    if (_planDirty) {
      toast('⚠️ 기본 정보 또는 취향 설정이 변경되었습니다. 일정을 다시 생성해주세요.');
      return;
    }
    go('map');
    return;
  }

  // map 페이지에서 1~3 클릭 시 planner 페이지로 먼저 전환
  const onMap = document.getElementById('page-map')?.classList.contains('active') ?? false;
  if (onMap) {
    document.querySelectorAll('.page').forEach(p => p.classList.remove('active'));
    document.getElementById('page-planner')?.classList.add('active');
    sessionStorage.setItem('currentPage', 'planner');
    document.querySelectorAll('.nav-link').forEach(b => b.classList.remove('on'));
    document.getElementById('navPlannerBtn')?.classList.add('on');
  }

  const current = _currentPlanStep();

  if (n > current) {
    if (current === 1 && n >= 2) { if (!_validatePlanStep1()) return; }
    if (current === 2 && n >= 3) { if (!_validatePlanStep2()) return; }
    if (current === 1 && n === 3) { if (!_validatePlanStep1() || !_validatePlanStep2()) return; }
  }

  for(let i=1;i<=3;i++) {
    const sb2=document.getElementById('sb-'+i), sp2=document.getElementById('sp-'+i);
    if(!sb2||!sp2) continue;
    sb2.classList.remove('active','done'); sp2.classList.remove('active');
    if(i<n) sb2.classList.add('done');
    if(i===n){ sb2.classList.add('active'); sp2.classList.add('active'); }
  }
  _syncStep4State();
  _syncPlannerTopbar();
  _syncNavStepHighlight();

  if(n===3) startChatWithSummary();
  history.pushState({ page: 'planner', step: n }, '', location.href);
  if (window._currentTripId) _savePlannerDraft();
}

/* ───────────────────────────────────────────────
 * 17. 지도 UI
 * ─────────────────────────────────────────────── */
function showDay(day, btn) {
  btn.closest('.day-tabs').querySelectorAll('.day-tab').forEach(b => b.classList.remove('on'));
  btn.classList.add('on');
  document.querySelectorAll('.day-section').forEach(s => {
    if(day==='all') s.style.display='block';
    else s.style.display = (s.dataset.day==day)?'block':'none';
  });
  if (window._kakaoOverlays && window._kakaoMap) {
    window._kakaoOverlays.forEach(o => {
      o.overlay.setMap((day === 'all' || o.day == day) ? window._kakaoMap : null);
    });
  }
  if (window._kakaoPolylines) {
    window._kakaoPolylines.forEach(p => {
      p.line.setMap((day === 'all' || p.day == day) ? window._kakaoMap : null);
    });
  }
  if (typeof updateBoundsForDay === 'function') updateBoundsForDay(day);
}
function switchMapTab(tab, btn) {
  document.querySelectorAll('.btn-map-act').forEach(b => b.classList.remove('on'));
  btn.classList.add('on');
  const mv=document.getElementById('mapView'), bv=document.getElementById('budgetView');
  if(tab==='map'){mv.style.display='block';bv.style.display='none';}
  else           {mv.style.display='none'; bv.style.display='block'; _loadMapBudget();}
}


function toggleMarker(btn, type) {
  btn.classList.toggle('on');
  const isOn = btn.classList.contains('on');

  if (window._kakaoOverlays && window._kakaoMap) {
    window._kakaoOverlays.filter(o => {
      // 💡 핵심: AI가 'tour' 대신 'sight', 'attraction' 등으로 지어낸 경우까지 모두 관광지로 묶어서 강제 필터링!
      if (type === 'tour') {
        return ['tour', 'sight', 'attraction', 'place'].includes(o.type);
      }
      return o.type === type;
    }).forEach(o => {
      o.overlay.setMap(isOn ? window._kakaoMap : null);
    });
  } else {
    document.querySelectorAll('.map-pin[data-type="'+type+'"]').forEach(p => p.style.display=isOn?'flex':'none');
  }

  toast((isOn?'✅ 표시':'❌ 숨김') + ' · ' + btn.textContent.trim().replace(/[🏨🍽️📍☕\s]/g,''));
}

/* ───────────────────────────────────────────────
 * 18. 교체 큐 (장소 교체 요청)
 * POST /api/ai/schedule/regenerate
 * ─────────────────────────────────────────────── */
let _q = [];
function showReplaceInput(btn, name) {
  const id='ri-'+name, area=document.getElementById(id); if(!area) return;
  const open=area.style.display==='block';
  document.querySelectorAll('.replace-area').forEach(a => a.style.display='none');
  if(!open) area.style.display='block';
}
function addQueue(key) {
  const inp = document.getElementById('rt-' + key);
  const req = inp ? inp.value.trim() : '';
  if (!req) { toast('교체 요구사항을 입력해주세요.'); return; }

  // key → 실제 장소명 역참조 (MAP_PINS 우선, 없으면 MAP_ITINERARY 탐색)
  let placeName = key; // fallback
  if (typeof MAP_PINS !== 'undefined') {
    const pin = MAP_PINS.find(p => p.key === key);
    if (pin && pin.label) placeName = pin.label;
  }
  if (placeName === key && typeof MAP_ITINERARY !== 'undefined' && Array.isArray(MAP_ITINERARY)) {
    outer: for (const day of MAP_ITINERARY) {
      if (!day.places) continue;
      for (const p of day.places) {
        if (p.key === key && p.name) { placeName = p.name; break outer; }
      }
    }
  }

  // 중복 등록 방지
  if (_q.some(q => q.place === placeName)) { toast('이미 교체 요청 목록에 있습니다.'); return; }

  _q.push({ place: placeName, req });

  // 입력창 초기화 및 닫기
  if (inp) inp.value = '';
  const area = document.getElementById('ri-' + key);
  if (area) area.style.display = 'none';

  renderQ();
  toast('"' + placeName + '" 교체 요청이 대기열에 추가됐습니다.');
}
function renderQ() {
  const box=document.getElementById('queueBox'), items=document.getElementById('qItems'), cnt=document.getElementById('qCnt'), btn=document.getElementById('btnAll');
  if(!box||!items) return;
  if(_q.length===0){box.classList.remove('has');cnt.textContent='0';if(btn)btn.disabled=true;items.innerHTML='';return;}
  box.classList.add('has'); box.style.display='block'; cnt.textContent=_q.length;
  if(btn) btn.disabled=false;
  items.innerHTML=_q.map((q,i)=>`<div class="q-item"><div style="flex:1"><div class="q-place">📍 ${q.place}</div><div class="q-req">"${q.req}"</div></div><button class="q-rm" onclick="rmQ(${i})">✕</button></div>`).join('');
}
function rmQ(i) { _q.splice(i,1); renderQ(); toast('요청 제거됨'); }
function closeQueue() { document.getElementById('queueBox').classList.remove('has'); document.getElementById('queueBox').style.display='none'; document.getElementById('queueToggle').style.display='block'; }
function openQueue()  { document.getElementById('queueBox').classList.add('has'); document.getElementById('queueBox').style.display='block'; document.getElementById('queueToggle').style.display='none'; }

/** POST /api/trips/{tripId}/routes/replace */
async function execAllReplace() {
  const tripId = window._currentTripId;
  if (!tripId || _q.length === 0) return;

  const btn = document.getElementById('btnAll');
  const originalText = btn ? btn.innerHTML : '';
  const loadingOverlay = document.getElementById('replaceLoadingOverlay');

  if (loadingOverlay) loadingOverlay.style.display = 'flex';
  if (btn) {
    btn.innerHTML = '⏳ AI 부분 교체 중...';
    btn.disabled = true;
  }

  try {
    const res = await api.post(`/api/trips/${tripId}/routes/replace`, { requests: _q });

    if (res.success && res.data) {
      toast('✅ AI 부분 교체가 완료되었습니다! 화면을 갱신합니다.');

      // ✨ 핵심: 브라우저 임시 저장소(sessionStorage)의 옛날 데이터 찌꺼기를 최신 데이터로 강제 덮어쓰기!
      let cleanJson = res.data;
      if (typeof cleanJson === 'string' && cleanJson.includes('[')) {
        cleanJson = cleanJson.substring(cleanJson.indexOf('['), cleanJson.lastIndexOf(']') + 1);
      } else if (typeof cleanJson === 'object') {
        cleanJson = JSON.stringify(cleanJson);
      }
      sessionStorage.setItem('ai_generated_route', cleanJson); // 이 코드가 없어서 옛날 마커가 떴던 겁니다!

      // 1.5초 뒤 완벽하게 새로고침
      setTimeout(() => {
        location.reload();
      }, 1500);

    } else {
      toast('<span style="color: black;">⚠️ 교체처리에 실패했습니다.</span>');
      if (loadingOverlay) loadingOverlay.style.display = 'none';
      if (btn) { btn.innerHTML = originalText; btn.disabled = false; }
    }
  } catch(e) {
    toast('<span style="color: black;">⚠️ 교체처리에 실패했습니다.</span>');
    if (loadingOverlay) loadingOverlay.style.display = 'none';
    if (btn) { btn.innerHTML = originalText; btn.disabled = false; }
  }
}


/* ───────────────────────────────────────────────
 * 19. 관리자 (Admin Domain)
 * ─────────────────────────────────────────────── */
function showAdmin(sec, btn) {
  ['dashboard','users','reports','curation'].forEach(s => { const e=document.getElementById('ad-'+s); if(e) e.style.display='none'; });
  const t=document.getElementById('ad-'+sec); if(t) t.style.display='block';
  btn.closest('.admin-nav').querySelectorAll('.admin-link').forEach(b => b.classList.remove('on'));
  btn.classList.add('on');
}

let _dayN = 2;
function removeDay(btn) {
  const block=btn.closest('.plan-day-block');
  if(document.querySelectorAll('#curDays .plan-day-block').length<=1){toast('최소 1개의 Day가 필요합니다');return;}
  block.remove();
}
function addDay() {
  _dayN++;
  const div=document.createElement('div'); div.className='plan-day-block'; div.style.cssText='border:1px solid var(--sage-l)';
  div.innerHTML=`<div class="pdb-hd" style="border-bottom:1px solid var(--border2);padding-bottom:8px;margin-bottom:8px">
    <span style="font-weight:800;color:var(--sage-d)">Day ${_dayN}</span>
    <div style="display:flex;gap:6px">
      <button style="font-size:11px;background:var(--sage-pale);border:1px solid var(--sage-l);border-radius:5px;padding:3px 9px;cursor:pointer;color:var(--sage-d)" onclick="addPlanItem(this)">+ 장소 추가</button>
      <button style="font-size:11px;background:#FEF3F2;border:1px solid #FECACA;border-radius:5px;padding:3px 7px;cursor:pointer;color:var(--coral)" onclick="removeDay(this)">✕</button>
    </div></div>
    <div style="font-size:11px;color:var(--text3);padding:6px;text-align:center">장소를 추가해주세요</div>`;
  document.getElementById('curDays').appendChild(div);
}
function addPlanItem(btn) {
  const block=btn.closest('.plan-day-block');
  const ph=block.querySelector('[style*="text-align:center"]'); if(ph) ph.remove();
  const div=document.createElement('div'); div.className='pdb-item';
  div.style.cssText='flex-direction:column;align-items:flex-start;gap:8px;margin-top:6px';
  div.innerHTML=`<div style="display:flex;align-items:center;gap:8px;width:100%">
    <span class="pdb-type-icon">📍</span>
    <input style="flex:1;border:1px solid var(--border2);background:var(--surface);padding:5px 9px;border-radius:6px;font-size:12px;font-family:inherit;outline:none" placeholder="장소명">
    <button class="btn-pdb-rm" onclick="this.closest('.pdb-item').remove()" style="flex-shrink:0">✕</button>
  </div>
  <div style="display:grid;grid-template-columns:1fr 1fr 1fr;gap:6px;width:100%">
    <select style="padding:5px 7px;border-radius:6px;border:1px solid var(--border2);font-size:11px;font-family:inherit">
      <option value="관광지">📍 관광지</option>
      <option value="숙소">🏨 숙소</option>
      <option value="맛집">🍽️ 맛집</option>
      <option value="카페">☕ 카페</option>
    </select>
    <input type="time" value="10:00" style="padding:5px 7px;border-radius:6px;border:1px solid var(--border2);font-size:11px;font-family:inherit">
    <input type="number" placeholder="금액(원)" style="padding:5px 7px;border-radius:6px;border:1px solid var(--border2);font-size:11px;font-family:inherit">
  </div>`;
  block.appendChild(div);
}

/** PATCH /api/admin/users/{userId}/suspend */
var _reportAction = 'delete';
function openReportAction(type, id, post, reporter, reason) {
  _reportAction=type; const isDelete=(type==='delete');
  document.getElementById('reportActionTitle').textContent = isDelete?'🗑️ 게시글 삭제 처리':'↩️ 신고 반려 처리';
  document.getElementById('ra-id').textContent=id; document.getElementById('ra-post').textContent=post;
  document.getElementById('ra-reporter').textContent=reporter; document.getElementById('ra-reason').textContent=reason;
  document.getElementById('ra-reason-label').innerHTML=(isDelete?'삭제 사유':'반려 사유')+' <span style="color:var(--coral)">*</span>';
  const sel=document.getElementById('ra-reason-select');
  if(isDelete){sel.innerHTML='<option value="">사유 선택...</option><option>허위 정보 게시</option><option>스팸/광고성 콘텐츠</option><option>불법 정보 포함</option><option>욕설/혐오 표현</option><option>개인정보 침해</option><option value="other">직접 입력</option>'; document.getElementById('ra-notify-msg').value='귀하의 게시글이 운영 정책에 따라 삭제 처리되었습니다.'; document.getElementById('ra-confirm-btn').style.background='var(--coral)'; document.getElementById('ra-confirm-btn').textContent='삭제 완료';}
  else        {sel.innerHTML='<option value="">사유 선택...</option><option>신고 증거 불충분</option><option>허용된 표현 범위 내</option><option>중복 신고</option><option>사실과 다른 신고</option><option value="other">직접 입력</option>'; document.getElementById('ra-notify-msg').value='귀하의 게시글에 대한 신고가 검토 후 반려되었습니다.'; document.getElementById('ra-confirm-btn').style.background='var(--sage)'; document.getElementById('ra-confirm-btn').textContent='반려 완료';}
  document.getElementById('ra-detail').value=''; document.getElementById('reportActionModal').classList.add('open');
}
function closeReportAction() { document.getElementById('reportActionModal').classList.remove('open'); }

function openSuspendModal(username, uid) {
  document.getElementById('su-username').textContent=username; document.getElementById('su-id').textContent=uid;
  document.getElementById('su-reason-select').value=''; document.getElementById('su-detail').value='';
  document.getElementById('su-notify-msg').value='귀하의 계정은 운영 정책 위반으로 인해 정지되었습니다.';
  document.getElementById('suspendModal').classList.add('open');
}
function closeSuspendModal() { document.getElementById('suspendModal').classList.remove('open'); }

async function confirmSuspend() {
  const r=document.getElementById('su-reason-select').value; if(!r){toast('정지 사유를 선택해주세요');return;}
  const uid=document.getElementById('su-id').textContent;
  const res=await api.patch('/api/admin/users/'+uid+'/suspend', {reason:r});
  closeSuspendModal();
  toast(res.success?'계정 정지 처리 완료 · 알림 전송됨':'⚠️ 정지 처리에 실패했습니다.');
}

/** DELETE /api/admin/reports/{reportId} or PATCH (반려) */
async function confirmReportAction() {
  const r=document.getElementById('ra-reason-select').value; if(!r){toast('사유를 선택해주세요');return;}
  const rid=document.getElementById('ra-id').textContent;
  let res;
  if(_reportAction==='delete') res=await api.del('/api/admin/reports/'+rid);
  else                         res=await api.patch('/api/admin/reports/'+rid,{status:'REJECTED',reason:r});
  closeReportAction();
  toast(res.success
      ? (_reportAction==='delete'?'게시글 삭제 완료 · 작성자 알림 전송됨':'신고 반려 완료 · 신고자 알림 전송됨')
      : '⚠️ 처리에 실패했습니다.');
}

// 제미나이 추가
async function changeRole(action, userId, username) {
  const label = action === 'promote' ? '관리자로 승격' : '일반 회원으로 강등';
  if (!confirm(`${username} 님을 ${label}하시겠습니까?`)) return;

  const token = localStorage.getItem('accessToken');
  const url = `/api/admin/users/${userId}/${action === 'promote' ? 'promote' : 'demote'}`;

  try {
    const res = await fetch(url, {
      method: 'PATCH',
      headers: { 'Authorization': 'Bearer ' + token, 'Content-Type': 'application/json' }
    });
    const data = await res.json();
    if (data.success) {
      toast(`${label} 완료`);
      // 회원 목록 새로고침
      document.querySelector('#ad-users table tbody').innerHTML = '';
      renderDynamicMockData();
    } else {
      toast('처리 실패: ' + (data.message || ''));
    }
  } catch (e) {
    toast('오류 발생');
  }
}
// 여기까지

/* ───────────────────────────────────────────────
 * 20. 블로그 에디터 이미지 (AWS S3 업로드 준비)
 * ─────────────────────────────────────────────── */
function handleEditImg(input) {
  const file=input.files[0]; if(!file) return;
  const reader=new FileReader();
  reader.onload=e=>{
    const ed=document.getElementById('editBlogEditor'); if(!ed) return;
    ed.focus(); const img=document.createElement('img'); img.src=e.target.result;
    img.style.maxWidth='100%'; img.style.borderRadius='8px'; img.style.margin='8px 0';
    const sel=window.getSelection();
    if(sel.rangeCount){const r=sel.getRangeAt(0);r.deleteContents();r.insertNode(img);} else ed.appendChild(img);
  };
  reader.readAsDataURL(file);
}
function handleWriteImg(input) {
  const file=input.files[0]; if(!file) return;
  const reader=new FileReader();
  reader.onload=e=>{
    const ed=document.getElementById('blogEditor'); if(!ed) return;
    ed.focus(); const img=document.createElement('img'); img.src=e.target.result;
    img.style.maxWidth='100%'; img.style.borderRadius='8px'; img.style.margin='8px 0';
    const sel=window.getSelection();
    if(sel.rangeCount){const r=sel.getRangeAt(0);r.deleteContents();r.insertNode(img);} else ed.appendChild(img);
  };
  reader.readAsDataURL(file);
}
document.addEventListener('DOMContentLoaded', () => {
  const ed=document.getElementById('blogEditor');
  if(ed) {
    ed.addEventListener('paste', e => {
      const items=e.clipboardData&&e.clipboardData.items; if(!items) return;
      for(let i=0;i<items.length;i++){
        if(items[i].type.startsWith('image/')){
          e.preventDefault();
          const reader=new FileReader();
          reader.onload=ev=>{
            const img=document.createElement('img'); img.src=ev.target.result;
            img.style.maxWidth='100%'; img.style.borderRadius='8px'; img.style.margin='8px 0';
            const sel=window.getSelection();
            if(sel.rangeCount){const r=sel.getRangeAt(0);r.deleteContents();r.insertNode(img);} else ed.appendChild(img);
          };
          reader.readAsDataURL(items[i].getAsFile()); return;
        }
      }
    });
  }
});

/* ───────────────────────────────────────────────
 * 21. 모달 / 기타 UI 헬퍼
 * ─────────────────────────────────────────────── */
function showConfirmModal()  { document.getElementById('confirmModal').classList.add('open'); }
function openConfirmDone()   { document.getElementById('confirmDoneModal').classList.add('open'); }
function showPlaceReviews(val){ const sec=document.getElementById('placeReviewsSection'); if(sec) sec.style.display=val?'block':'none'; }
function setStars(btn, rating){ btn.closest('.star-sel').querySelectorAll('.star-btn').forEach((b,i)=>b.classList.toggle('lit',i<rating)); }

// ── [여행 플랜 공유 기능] ──

// 공유 모달 열기 및 데이터 로드
function openShareModal() {
  const modal = document.getElementById('shareModal');
  if (!modal) return;

  const tripId = window._currentTripId;
  const linkEl = document.getElementById('share-link-val');

  if (linkEl && tripId) {
    // 🎯 백엔드 규칙과 동일한 16진수 보안 암호화 규칙 적용하여 처음부터 난수로 표출
    const obscureToken = (tripId ^ 0x5A3C9B7D2E).toString(16);
    linkEl.value = `${window.location.origin}/plan/view?token=${obscureToken}`;
  }

  modal.classList.add('open');
  loadShareMembersData();

  // 🎯 꼬여있던 내부 호출용 함수명을 아래 실제 구현된 함수명과 일치시킵니다.
  loadShareMembersData();
}

function shareInviteToKakaoTalk() {
  const tripId = window._currentTripId || sessionStorage.getItem('plannerDraftId');
  if (!tripId) { toast('⚠️ 여행 플랜 정보가 올바르지 않습니다.'); return; }

  if (typeof Kakao !== 'undefined') {
    if (!Kakao.isInitialized()) {
      Kakao.init('cb534606e630ecbec186e4ebd2917b04');
    }

    // 백엔드 규칙과 동일한 16진수 난수 토큰 암호화 처리
    const obscureToken = (parseInt(tripId) ^ 0x5A3C9B7D2E).toString(16);

    // 🎯 [핵심 버그 수정]: 도메인 충돌 방지를 위해 카카오에 등록된 정품 로컬 주소를 명시적으로 강제 박기
    const inviteUrl = `http://localhost:8080/plan/view?token=${obscureToken}`;

    // v2 공식 규격: 이 함수를 실행하면 카카오 서버가 알아서 로그인 세션을 검증하고 단톡방/친구 선택 창(피커)을 자동으로 띄워줍니다.
    Kakao.Share.sendDefault({
      objectType: 'feed',
      content: {
        title: '✈️ TripLinker 여행 플랜 공유',
        description: `🔗 플랜 열람 링크: ${inviteUrl}`,
        imageUrl: 'https://images.unsplash.com/photo-1507525428034-b723cf961d3e?q=80&w=400',
        link: { mobileWebUrl: inviteUrl, webUrl: inviteUrl }
      },
      buttons: [
        { title: '🗺️ 여행 일정 열람하기', link: { mobileWebUrl: inviteUrl, webUrl: inviteUrl } }
      ]
    });
    toast('카카오톡 초대 창이 활성화되었습니다.');
  } else {
    toast('⚠️ 카카오 SDK를 불러올 수 없습니다.');
  }
}

// 2. 참여자 목록 실시간 API 로드 및 인풋창 동기화
async function loadShareMembersData() {
  const tripId = window._currentTripId || sessionStorage.getItem('plannerDraftId');
  const listEl = document.getElementById('share-member-list');
  const linkEl = document.getElementById('share-link-val');

  if (!tripId) {
    if(listEl) listEl.innerHTML = '<div style="font-size:13px; color:var(--coral); font-weight:700;">⚠️ 저장된 플랜이 없습니다. 먼저 플랜을 생성해주세요.</div>';
    if(linkEl) linkEl.value = '';
    return;
  }

  // 🎯 화면 로드 시에도 링크 창에 완벽한 난수 주소가 유지되도록 체결
  const obscureToken = (parseInt(tripId) ^ 0x5A3C9B7D2E).toString(16);
  if(linkEl) linkEl.value = `${window.location.origin}/plan/view?token=${obscureToken}`;
  if(listEl) listEl.innerHTML = '<div style="font-size:13px; color:var(--text3);">참여자 목록 불러오는 중...</div>';

  try {
    const res = await api.get(`/api/trips/${tripId}/members`);
    if (res.success && res.data && res.data.length > 0) {
      listEl.innerHTML = res.data.map(m => `
        <div style="display:flex; align-items:center; justify-content:space-between; margin-bottom: 8px;">
          <div style="display:flex; align-items:center; gap:12px;">
            <div style="width:36px; height:36px; border-radius:50%; background:${m.role === 'OWNER' ? 'var(--sage)' : '#E5E7EB'}; color:${m.role === 'OWNER' ? '#fff' : '#333'}; display:flex; align-items:center; justify-content:center; font-weight:800; font-size:14px;">${(m.name||'?')[0]}</div>
            <div style="font-weight:700; font-size:14px; color:#111;">${m.name||''} ${m.role === 'OWNER' ? '<span style="color:#2563EB; font-weight:800;">(소유자)</span>' : ''}</div>
          </div>
          <div style="font-size:12px; color:var(--text3);">${m.role==='OWNER'?'소유자':'편집자'}</div>
        </div>`).join('');
    } else {
      const me = (typeof _currentUser !== 'undefined' && _currentUser) ? _currentUser.name : '나';
      listEl.innerHTML = `
        <div style="display:flex; align-items:center; justify-content:space-between;">
          <div style="display:flex; align-items:center; gap:12px;">
            <div style="width:36px; height:36px; border-radius:50%; background:var(--sage); color:#fff; display:flex; align-items:center; justify-content:center; font-weight:800; font-size:14px;">${me[0]}</div>
            <div style="font-weight:700; font-size:14px; color:#111;">${me} <span style="color:#2563EB; font-weight:800;">(소유자)</span></div>
          </div>
          <div style="font-size:12px; color:var(--text3);">소유자</div>
        </div>`;
    }
  } catch (e) {
    if(listEl) listEl.innerHTML = '<div style="font-size:13px; color:var(--coral);">서버 통신 오류가 발생했습니다.</div>';
  }
}

// 3. 이메일 기반 멤버 초대 요청 처리
async function inviteShareMember(btn) {
  const tripId = window._currentTripId || sessionStorage.getItem('plannerDraftId');
  const input  = document.getElementById('share-email-inp');
  if (!input?.value.trim()) { toast('이메일을 입력해주세요.'); return; }
  if (!tripId) { toast('공유할 플랜이 없습니다.'); return; }

  const originalText = btn ? btn.innerHTML : '초대';
  if (btn) {
    btn.innerHTML = '⏳ 발송중...';
    btn.disabled = true;
    btn.style.opacity = '0.6';
    btn.style.cursor = 'not-allowed';
  }

  const emails = input.value.split(',').map(e => e.trim()).filter(Boolean);
  let successCount = 0;
  for (const email of emails) {
    const res = await api.post(`/api/trips/${tripId}/members`, { email: email, role: 'EDITOR' });
    if(res.success) successCount++;
  }

  if (btn) {
    btn.innerHTML = originalText;
    btn.disabled = false;
    btn.style.opacity = '1';
    btn.style.cursor = 'pointer';
  }

  if(successCount > 0) {
    toast('✅ 초대(편집 권한)가 발송되었습니다.');
    input.value = '';
    await loadShareMembersData(); // 목록 리로드 함수명 일치화
  } else {
    toast('⚠️ 초대 실패. 가입된 유저인지 확인해주세요.');
  }
}

async function copyShareLink() {
  const tripId = window._currentTripId;
  if (!tripId) { toast('여행 플랜 정보가 올바르지 않습니다.'); return; }

  try {
    // 1. 백엔드 난수 링크 생성 API 호출
    const response = await fetch(`/api/trips/${tripId}/share`, {
      method: 'POST',
      headers: {
        'Authorization': Token.getAccess() ? `Bearer ${Token.getAccess()}` : '',
        'Content-Type': 'application/json'
      }
    });
    const res = await response.json();

    // 2. 백엔드에서 생성해 준 안전한 난수 주소(?token=...)가 넘어왔을 때
    if (res && res.success && res.data && res.data.shareLink) {
      const linkEl = document.getElementById('share-link-val');
      if (linkEl) {
        linkEl.value = res.data.shareLink;
      }
      await navigator.clipboard.writeText(res.data.shareLink);
      toast('읽기 전용 링크가 클립보드에 복사되었습니다!');
    } else {
      throw new Error("API 반환 오류");
    }

  } catch (error) {
    console.error("공유 링크 생성 실패:", error);

    // 3. 백엔드 통신 실패 시 프론트 자체 방어막 가드
    const fallbackObscure = (tripId ^ 0x5A3C9B7D2E).toString(16);
    const fallbackLink = `http://localhost:8080/plan/view?token=${fallbackObscure}`;

    const linkEl = document.getElementById('share-link-val');
    if (linkEl) linkEl.value = fallbackLink;

    await navigator.clipboard.writeText(fallbackLink);
    toast('링크가 복사되었습니다.');
  }
}

function setShareTab(btn, tab) {
  if(!btn) return;
  document.querySelectorAll('#shareModal .share-tab').forEach(b => {
    b.classList.remove('on');
    b.style.color = 'var(--text3)';
    b.style.borderBottom = 'none';
    b.style.fontWeight = '600'; // 비활성화 시 폰트 굵기 조절
  });
  btn.classList.add('on');
  btn.style.color = 'var(--sage-d)';
  btn.style.borderBottom = '3px solid var(--sage)';
  btn.style.fontWeight = '700'; // 활성화 시 폰트 굵기 조절

  document.getElementById('share-members').style.display = tab === 'members' ? 'block' : 'none';
  document.getElementById('share-post').style.display = tab === 'post' ? 'block' : 'none';
}
/* ───────────────────────────────────────────────
 * 22. Hero & 큐레이션 미리보기
 * ─────────────────────────────────────────────── */
function navPlannerClick() {
  if (!_loggedIn) { toast('⚠️ 로그인이 필요합니다.'); go('login'); return; }
  if (_hasPlannerDraft()) {
    goResumePlanner();
  } else {
    goNewPlanner();
  }
}

function goNewPlanner() {
  if (!_loggedIn) { toast('⚠️ 로그인이 필요합니다.'); go('login'); return; }
  resetPlannerForm();
  window._currentTripId = null;
  sessionStorage.removeItem('plannerDraftId');
  sessionStorage.removeItem('plannerDraftStep');
  sessionStorage.removeItem('plannerDraftState');

  go('planner', false);   // history는 goPlanStep이 추가
  goPlanStep(1);
}

function goResumePlanner() {
  if (!_loggedIn) { toast('⚠️ 로그인이 필요합니다.'); go('login'); return; }
  if (!_hasPlannerDraft()) {
        toast('작성중인 플랜이 없습니다.');
        goNewPlanner();
        return;
      }
    go('planner', false);
    if (typeof _restorePlannerDraft === 'function') _restorePlannerDraft();
    const savedStep = parseInt(sessionStorage.getItem('plannerDraftStep') || '1');
    // 검증 없이 직접 패널 전환
        for (let i = 1; i <= 3; i++) {
        const sb = document.getElementById('sb-' + i), sp = document.getElementById('sp-' + i);
        if (!sb || !sp) continue;
        sb.classList.remove('active', 'done'); sp.classList.remove('active');
        if (i < savedStep) sb.classList.add('done');
        if (i === savedStep) { sb.classList.add('active'); sp.classList.add('active'); }
      }
    if (savedStep === 3 && typeof startChatWithSummary === 'function') {
        setTimeout(() => startChatWithSummary(), 100);
      }
    history.pushState({ page: 'planner', step: savedStep }, '', location.href);
}
function _hasPlannerDraft() {
  if (window._currentTripId) return true;
  if (sessionStorage.getItem('plannerDraftId')) return true;
  const raw = sessionStorage.getItem('plannerDraftState');
  if (!raw) return false;
  try {
    const state = JSON.parse(raw);
    return ['dest-prov','s1-date-start','s1-date-end','s1-pax','s1-budget']
        .some(id => state[id] && String(state[id]).trim() !== '');
  } catch { return false; }
}

    function _savePlannerDraft() {
      if (window._currentTripId) {
                  sessionStorage.setItem('plannerDraftId', window._currentTripId);
                }
              sessionStorage.setItem('plannerDraftStep', _currentPlanStep());
        const state = {};
        ['dest-prov','dest-city','dep-prov','dep-city','s1-date-start','s1-date-end','s1-pax','s1-budget']
          .forEach(id => { const el = document.getElementById(id); if (el) state[id] = el.value; });
        ['chip-trans','chip-acc','chip-comp','chip-style','chip-food','chip-special','chip-density','chip-accopts']
          .forEach(id => {
              const chips = document.querySelectorAll('#' + id + ' .chip');
              state['chips_' + id] = Array.from(chips).map(c => c.classList.contains('on'));
            });
        ['other-trans','other-acc','other-comp','other-style','other-food','other-allergy','other-special','other-accopts']
          .forEach(id => { const el = document.getElementById(id); if (el) state[id] = el.value; });
        sessionStorage.setItem('plannerDraftState', JSON.stringify(state));
      }

function _restorePlannerDraft() {
    const savedId = sessionStorage.getItem('plannerDraftId');
    if (savedId) window._currentTripId = parseInt(savedId);
    const raw = sessionStorage.getItem('plannerDraftState');
    if (!raw) return;
    try {
      const state = JSON.parse(raw);
      ['dest-prov','dest-city','dep-prov','dep-city','s1-date-start','s1-date-end','s1-pax','s1-budget']
          .forEach(id => { const el = document.getElementById(id); if (el && state[id] !== undefined) el.value = state[id]; });
        ['chip-trans','chip-acc','chip-comp','chip-style','chip-food','chip-special','chip-density','chip-accopts']
          .forEach(id => {
            const chips = document.querySelectorAll('#' + id + ' .chip');
            const saved = state['chips_' + id];
            if (saved) chips.forEach((c, i) => c.classList.toggle('on', !!saved[i]));
          });
        ['other-trans','other-acc','other-comp','other-style','other-food','other-allergy','other-special','other-accopts']
          .forEach(id => {
            const el = document.getElementById(id);
            if (el && state[id]) { el.value = state[id]; el.classList.add('show'); }
          });
        const depEl = document.getElementById('dep-prov');
      if (depEl && state['dep-prov']) {
                  depEl.value = state['dep-prov'];
                  if (typeof updateCityDep === 'function') updateCityDep(depEl);
                  // 옵션 재생성 후 city 값 재적용
                      const depCityEl = document.getElementById('dep-city');
                  if (depCityEl && state['dep-city']) depCityEl.value = state['dep-city'];
                }
        const destEl = document.getElementById('dest-prov');
        if (destEl && state['dest-prov']) {
                  destEl.value = state['dest-prov'];
                  if (typeof updateCityDest === 'function') updateCityDest(destEl);
                  // 옵션 재생성 후 city 값 재적용
                      const destCityEl = document.getElementById('dest-city');
                  if (destCityEl && state['dest-city']) destCityEl.value = state['dest-city'];
                }
        updateNav();
      } catch (e) { console.warn('[planner] draft restore 실패', e); }
  }

function resetPlannerForm() {
  // Step1 초기화
  const fields = ['dest-prov','dest-city','dep-prov','dep-city','s1-date-start','s1-date-end','s1-pax','s1-budget'];
  fields.forEach(id => { const el = document.getElementById(id); if (el) el.value = ''; });

  // 칩 초기화
  document.querySelectorAll('#chip-trans .chip').forEach((c,i) => c.classList.toggle('on', i===0));
  document.querySelectorAll('#chip-acc .chip').forEach((c,i) => c.classList.toggle('on', i===0));
  document.querySelectorAll('#chip-comp .chip').forEach((c,i) => c.classList.toggle('on', i===1));
  document.querySelectorAll('#chip-style .chip').forEach((c,i) => c.classList.toggle('on', i===0));
  document.querySelectorAll('#chip-food .chip').forEach(c => c.classList.remove('on'));
  document.querySelectorAll('#chip-special .chip').forEach(c => c.classList.remove('on'));
  document.querySelectorAll('#chip-density .chip').forEach((c,i) => c.classList.toggle('on', i===1));
  document.querySelectorAll('#chip-accopts .chip').forEach(c => c.classList.remove('on'));

  // other-input 초기화
  ['other-trans','other-acc','other-comp','other-style','other-food','other-allergy','other-special'].forEach(id => {
    const el = document.getElementById(id);
    if (el) { el.value = ''; el.classList.remove('show'); }
  });

  // 요약 카드 초기화
  ['sum-dep','sum-dest','sum-date','sum-people','sum-comp','sum-budget','sum-trans','sum-acc','sum-accopts','sum-style','sum-food','sum-density','sum-special','sum-pet'].forEach(id => {
    const el = document.getElementById(id); if (el) el.textContent = '—';
  });
  const extra = document.getElementById('sum-extra-rows');
  if (extra) extra.innerHTML = '';
  switchSumTab('tab-basic');
  const emptyMsg = document.getElementById('extra-empty-msg');
  if (emptyMsg) emptyMsg.style.display = '';
  const badge = document.getElementById('extra-badge');
  if (badge) badge.style.display = 'none';
}

// 검색어를 도/시 셀렉트에 매핑해서 자동 선택
  function _applyDestText(val) {
    const cityToProvMap = {
      '제주도': { prov: '제주', city: '제주시' },
      '강릉':   { prov: '강원', city: '강릉시' },
      '부산':   { prov: '부산', city: '' },
      '전주':   { prov: '전북', city: '전주시' },
      '경주':   { prov: '경북', city: '경주시' },
    };
    const mapped = cityToProvMap[val.trim()];
    if (!mapped) return;
    const provSel = document.getElementById('dest-prov');
    if (!provSel) return;
    for (let i = 0; i < provSel.options.length; i++) {
      if (provSel.options[i].text === mapped.prov) {
        provSel.selectedIndex = i;
        updateCityDest(provSel);
        break;
      }
    }
    if (mapped.city) {
      setTimeout(() => {
        const citySel = document.getElementById('dest-city');
        if (!citySel) return;
        for (let i = 0; i < citySel.options.length; i++) {
          if (citySel.options[i].text === mapped.city) {
            citySel.selectedIndex = i;
            break;
          }
        }
        if (typeof updateSummaryCard === 'function') updateSummaryCard();
      }, 50);
    } else {
      setTimeout(() => {
        if (typeof updateSummaryCard === 'function') updateSummaryCard();
      }, 50);
    }
  }

const _md = {
  jeju:     {tags:['시즌 큐레이션','초여름'],ttl:'🌊 제주 에메랄드 해안 3박 4일',budget:'₩425,000~',places:'8곳',dur:'3박 4일',stay:'협재 오션뷰 풀빌라 외 1건',foods:[{icon:'🦞',name:'민락어민활어직판장 횟집',r:'4.6'},{icon:'☕',name:'오션뷰 카페 에메랄드힐',r:'4.8'}],
    coords:[{lat:33.5097,lng:126.4927},{lat:33.3946,lng:126.2390},{lat:33.2450,lng:126.4122},{lat:33.3617,lng:126.5292},{lat:33.4583,lng:126.9425},{lat:33.5008,lng:126.9519}]},
  seorak:   {tags:['가을 특선','10월 단풍'],ttl:'🍁 설악산 단풍 트레킹 2박 3일',budget:'₩380,000~',places:'6곳',dur:'2박 3일',stay:'설악동 게스트하우스 외 1건',foods:[{icon:'🍜',name:'속초 닭강정 명가',r:'4.5'}],
    coords:[{lat:38.2070,lng:128.5918},{lat:38.2100,lng:128.5927},{lat:38.1190,lng:128.4654},{lat:38.1600,lng:128.4750},{lat:38.2070,lng:128.5918}]},
  gyeongju: {tags:['봄 기획','벚꽃 시즌'],ttl:'🌸 경주 벚꽃 역사 기행 1박 2일',budget:'₩290,000~',places:'7곳',dur:'1박 2일',stay:'경주 한옥 스테이 외 1건',foods:[{icon:'🍞',name:'황남빵 카페',r:'4.7'}],
    coords:[{lat:35.8394,lng:129.2117},{lat:35.8347,lng:129.2198},{lat:35.8344,lng:129.2253},{lat:35.7896,lng:129.3317},{lat:35.7947,lng:129.3473}]},
  busan:    {tags:['여름 특선','서핑 시즌'],ttl:'🏄 부산 해운대 서핑 투어 2박 3일',budget:'₩620,000~',places:'9곳',dur:'2박 3일',stay:'해운대 호텔 외 1건',foods:[{icon:'🐟',name:'자갈치시장 회',r:'4.6'}],
    coords:[{lat:35.1628,lng:129.1603},{lat:35.1533,lng:129.1186},{lat:35.0979,lng:129.0378},{lat:35.0590,lng:129.0850},{lat:35.0970,lng:129.0127}]},
  gangneung:{tags:['커뮤니티 인기','힐링'],ttl:'☕ 강릉 바다+커피 힐링 루트',budget:'₩480,000~',places:'10곳',dur:'2박 3일',stay:'안목해변 오션뷰 펜션 외 1건',foods:[{icon:'☕',name:'테라로사 강릉본점',r:'4.9'}],
    coords:[{lat:37.7958,lng:128.9004},{lat:37.7746,lng:128.9415},{lat:37.6847,lng:129.0527},{lat:37.7654,lng:128.9083},{lat:37.7755,lng:128.8745}]},
  jeonju:   {tags:['가성비 TOP','한식'],ttl:'🍚 전주 한옥마을 미식 기행 1박 2일',budget:'₩320,000~',places:'8곳',dur:'1박 2일',stay:'전통 한옥 스테이 외 1건',foods:[{icon:'🍚',name:'비빔밥 명가',r:'4.8'}],
    coords:[{lat:35.8196,lng:127.1474},{lat:35.8196,lng:127.1504},{lat:35.8175,lng:127.1523},{lat:35.8162,lng:127.1551},{lat:35.8122,lng:127.1529}]},
  namhae:   {tags:['커뮤니티 인기','액티비티'],ttl:'🏝 남해 독일마을+다랭이마을 2박 3일',budget:'₩620,000~',places:'7곳',dur:'2박 3일',stay:'오션뷰 펜션 외 1건',foods:[{icon:'🐙',name:'바다낙지 식당',r:'4.5'}],
    coords:[{lat:34.9035,lng:127.9013},{lat:34.8369,lng:127.9211},{lat:34.7738,lng:127.9076},{lat:34.7990,lng:128.0448},{lat:34.8768,lng:128.0272}]}
};

let _prevMap = null;

function initPreviewMap(coords) {
  if (typeof kakao === 'undefined' || !coords || coords.length < 2) return;
  kakao.maps.load(function() {
    const container = document.getElementById('prevKakaoMap');
    if (!container) return;
    container.innerHTML = '';
    _prevMap = null;
    const latlngs = coords.map(c => new kakao.maps.LatLng(c.lat, c.lng));
    const bounds = new kakao.maps.LatLngBounds();
    latlngs.forEach(p => bounds.extend(p));
    _prevMap = new kakao.maps.Map(container, { center: latlngs[0], level: 8 });
    _prevMap.setDraggable(false);
    _prevMap.setZoomable(false);
    new kakao.maps.Polyline({
      map: _prevMap, path: latlngs,
      strokeWeight: 2, strokeColor: '#2D9E8A', strokeOpacity: 0.85, strokeStyle: 'solid'
    });
    latlngs.forEach((pos, i) => {
      const edge = i === 0 || i === latlngs.length - 1;
      new kakao.maps.CustomOverlay({
        map: _prevMap, position: pos, zIndex: edge ? 2 : 1,
        content: `<div style="width:${edge?9:6}px;height:${edge?9:6}px;background:${edge?'#E85D5D':'#2D9E8A'};border-radius:50%;border:1.5px solid #fff;box-shadow:0 1px 3px rgba(0,0,0,.3);transform:translate(-50%,-50%)"></div>`
      });
    });
    _prevMap.setBounds(bounds);
  });
}

function openPreview(key) {
  const d = _md[key] || _md.jeju;
  const modal = document.getElementById('prevModal');
  if (!modal) { toast('미리보기를 불러올 수 없습니다.'); return; }
  const el = function(id){ return document.getElementById(id); };
  if (el('prevTags'))     el('prevTags').innerHTML     = d.tags.map(t=>`<span class="prev-tag">${t}</span>`).join('');
  if (el('prevPlanTtl'))  el('prevPlanTtl').textContent = d.ttl;
  if (el('prevBudget'))   el('prevBudget').textContent  = d.budget;
  if (el('prevPlaces'))   el('prevPlaces').textContent  = d.places;
  if (el('prevDur'))      el('prevDur').textContent     = d.dur;
  if (el('prevStay'))     el('prevStay').textContent    = d.stay;
  if (el('prevFoodList')) el('prevFoodList').innerHTML  = d.foods.map(f=>`<div class="prev-food-item"><div class="pfi-left"><span class="pfi-icon">${f.icon}</span>${f.name}</div><span class="pfi-rating">★ ${f.r}</span></div>`).join('');
  modal.classList.add('open');
  setTimeout(() => initPreviewMap(d.coords), 80);
}
function closePrev() { const m = document.getElementById('prevModal'); if(m) m.classList.remove('open'); }

/* ───────────────────────────────────────────────
 * 23. Chips / MBTI / Location
 * ─────────────────────────────────────────────── */
function clearOtherInputs(scope, exceptId) {
  if (!scope) return;
  scope.querySelectorAll('.other-input.show').forEach(el => {
    if (el.id === exceptId) return;
    el.classList.remove('show');
    el.value = '';
  });
}
function pick(chip, grp) {
  const p=chip.closest('.chip-row,.chip-grid4');
  if(!p) return;
  const card = chip.closest('.pl-card') || p.parentElement;
  p.querySelectorAll('.chip').forEach(c=>c.classList.remove('on'));
  chip.classList.add('on');
  if (!chip.textContent.trim().includes('기타')) clearOtherInputs(card);
  if (typeof markPlanDirty === 'function') markPlanDirty();
}
function tog(chip)    { chip.classList.toggle('on'); if (typeof markPlanDirty === 'function') markPlanDirty(); }
function togBtn(btn)  { btn.classList.toggle('off'); }
function pickVis(btn) { btn.closest('div').querySelectorAll('.vis-chip').forEach(b=>b.classList.remove('on')); btn.classList.add('on'); }
function showOtherInput(id, chip, clearOthers = true) {
  const el = document.getElementById(id);
  if (!el) return;
  if (chip.classList.contains('on')) {
    if (clearOthers) clearOtherInputs(chip.closest('.pl-card'), id);
    el.classList.add('show');
  } else {
    el.classList.remove('show');
    el.value = '';
  }
}

let _mbti = {ei:'E',sn:'S',tf:'T',jp:'P'};
function selectMbti(btn, dim, val) {
  const parent=btn.closest('div'); parent.querySelectorAll('.chip').forEach(b=>b.classList.remove('on')); btn.classList.add('on');
  _mbti[dim]=val;
  const result=_mbti.ei+_mbti.sn+_mbti.tf+_mbti.jp;
  const el=document.getElementById('mbti-result'); if(el) el.textContent=result;
  const den=document.getElementById('mbti-density'); if(den) den.textContent=_mbti.jp==='P'?'→ P: 여유롭게 자동 설정':'→ J: 빼곡하게 자동 설정';
  if(_loggedIn) api.patch('/api/users/mbti', {mbti:result});
}

const _cities = {
  서울:['전체','종로구','중구','용산구','성동구','광진구','동대문구','중랑구','성북구','강북구','도봉구','노원구','은평구','서대문구','마포구','양천구','강서구','구로구','금천구','영등포구','동작구','관악구','서초구','강남구','송파구','강동구'],
  경기:['전체','수원시','성남시','의정부시','안양시','부천시','광명시','평택시','안산시','고양시','과천시','구리시','남양주시','오산시','시흥시','군포시','의왕시','하남시','용인시','파주시','이천시','안성시','김포시','화성시','광주시','양주시','포천시','여주시'],
  인천:['전체','중구','동구','미추홀구','연수구','남동구','부평구','계양구','서구','강화군','옹진군'],
  강원:['전체','춘천시','원주시','강릉시','동해시','태백시','속초시','삼척시','홍천군','횡성군','영월군','평창군','정선군','철원군','화천군','양구군','인제군','고성군','양양군'],
  충북:['전체','청주시','충주시','제천시','보은군','옥천군','영동군','증평군','진천군','괴산군','음성군','단양군'],
  충남:['전체','천안시','공주시','보령시','아산시','서산시','논산시','계룡시','당진시','금산군','부여군','서천군','청양군','홍성군','예산군','태안군'],
  대전:['전체','동구','중구','서구','유성구','대덕구'],
  세종:['전체','세종시'],
  전북:['전체','전주시','군산시','익산시','정읍시','남원시','김제시','완주군','진안군','무주군','장수군','임실군','순창군','고창군','부안군'],
  전남:['전체','목포시','여수시','순천시','나주시','광양시','담양군','곡성군','구례군','고흥군','보성군','화순군','장흥군','강진군','해남군','영암군','무안군','함평군','영광군','장성군','완도군','진도군','신안군'],
  광주:['전체','동구','서구','남구','북구','광산구'],
  경북:['전체','포항시','경주시','김천시','안동시','구미시','영주시','영천시','상주시','문경시','경산시','의성군','청송군','영양군','영덕군','청도군','고령군','성주군','칠곡군','예천군','봉화군','울진군','울릉군'],
  경남:['전체','창원시','진주시','통영시','사천시','김해시','밀양시','거제시','양산시','의령군','함안군','창녕군','고성군','남해군','하동군','산청군','함양군','거창군','합천군'],
  대구:['전체','중구','동구','서구','남구','북구','수성구','달서구','달성군'],
  울산:['전체','중구','남구','동구','북구','울주군'],
  부산:['전체','중구','서구','동구','영도구','부산진구','동래구','남구','북구','해운대구','사하구','금정구','강서구','연제구','수영구','사상구','기장군'],
  제주:['전체','제주시','서귀포시']
};
function updateCity(sel, targetId) {
  if(!targetId) targetId='city-select';
  const t=document.getElementById(targetId); if(!t) return;
  const cities=_cities[sel.value]||['전체'];
  t.innerHTML=cities.map(c=>`<option>${c}</option>`).join('');
}
function updateCityDep(sel)  { updateCity(sel,'dep-city');  }
function updateCityDest(sel) { updateCity(sel,'dest-city'); }

/* ───────────────────────────────────────────────
 * 24. TOAST
 * ─────────────────────────────────────────────── */
let _planDirty = false;
let _tt = null;
function toast(msg, dur=2800) {
  const t=document.getElementById('toast'); if(!t) return;
  if(_tt) clearTimeout(_tt);

  t.innerHTML = msg;
  t.classList.add('show');

  _tt=setTimeout(()=>t.classList.remove('show'), dur);
}

/* ───────────────────────────────────────────────
 * 25. 전역 이벤트 리스너
 * ─────────────────────────────────────────────── */
document.addEventListener('keydown', e => {
  if(e.key==='Escape'){
    closePrev(); closeWrite();
    document.querySelectorAll('.overlay.open').forEach(o=>o.classList.remove('open'));
    closeNotifPopup();
  }
});

window.addEventListener('popstate', e => {
  const p = e.state?.page || 'main';
    const step = e.state?.step;

        // 플래너 내 스텝 뒤로가기
            if (p === 'planner' && step) {
        document.querySelectorAll('.page').forEach(pg => pg.classList.remove('active'));
        document.getElementById('page-planner')?.classList.add('active');
        for (let i = 1; i <= 3; i++) {
            const sb = document.getElementById('sb-' + i), sp = document.getElementById('sp-' + i);
            if (!sb || !sp) continue;
            sb.classList.remove('active', 'done'); sp.classList.remove('active');
            if (i < step) sb.classList.add('done');
            if (i === step) { sb.classList.add('active'); sp.classList.add('active'); }
          }
        document.querySelectorAll('.nav-link').forEach(b => b.classList.remove('on'));
        document.getElementById('navPlannerBtn')?.classList.add('on');
        return;
      }

      document.querySelectorAll('.page').forEach(pg => pg.classList.remove('active'));
  const pg = document.getElementById('page-' + p);
  if (pg) pg.classList.add('active');
  // nav-link .on 동기화
        document.querySelectorAll('.nav-link').forEach(b => b.classList.remove('on'));
    const navLinks = document.querySelectorAll('.nav-link');
  // 순서: 0=홈, 1=커뮤니티, 2=날씨, 3=플랜(navPlannerBtn)
        const navIdxMap = { main: 0, community: 1, weather: 2, planner: 3 };
    const idx = navIdxMap[p];
    if (idx !== undefined && navLinks[idx]) navLinks[idx].classList.add('on');
    // wf-item 동기화
        const wfiMap = { main: 0, community: 8, weather: 13, planner: 4 };
    const wfi = document.querySelectorAll('.wf-item');
    if (wfiMap[p] !== undefined && wfi[wfiMap[p]]) wfi[wfiMap[p]].classList.add('on');
});

/* ───────────────────────────────────────────────
 * 26. 초기화
 * ─────────────────────────────────────────────── */
(async () => {
  // OAuth 콜백 처리 (URL에 토큰이 있을 경우)
  _handleOAuthCallback();

  // ✨ 공유 링크 접속 시 URL에서 token(난수) 추출 후 원본 id 복원 및 읽기 전용 UI 처리
  const params = new URLSearchParams(location.search);
  const shareToken = params.get('token'); // 🎯 token 난수 파라미터 읽기
  const token = Token.getAccess();

  // 난수 토큰이 존재하면 역으로 디코딩하여 원본 숫자로 복원
  let sharedId = null;
  if (shareToken) {
    try {
      // 16진수 난수를 다시 원본 숫자 ID로 안전하게 역연산 해독
      const parsedHex = parseInt(shareToken, 16);
      sharedId = (parsedHex ^ 0x5A3C9B7D2E).toString();
    } catch (e) {
      console.error("유효하지 않은 토큰 포맷입니다.");
    }
  }

  // 🔒 공유 링크(?token=난수값)로 접속했는데, 읽기전용(/plan/view)이 아닌 편집링크(/plan)이고 토큰도 없다면?
  if (shareToken && !location.pathname.includes('/plan/view') && !token) {
    // 1. 현재 가려던 초대 링크 전체 주소를 브라우저 임시 창고에 박아둡니다.
    sessionStorage.setItem('redirectUrl', location.pathname + location.search);
    sessionStorage.setItem('currentPage', 'login');

    // 2. 화면 깜빡임과 에러를 막기 위해 0.1초 뒤 시스템이 준비되면 안전하게 로그인창만 점등합니다.
    setTimeout(() => {
      if (typeof go === 'function') {
        go('login');
        toast('🔒 편집 권한 유저 전용 링크입니다. 로그인 후 연결됩니다.');
      }
    }, 100);

    document.body.style.visibility = 'visible';
    return; // 🚨 핵심 가드: 아래쪽 지도 그리거나 메인 가는 다른 초기화 코드를 전부 중단시킵니다.
  }

  // 복원된 고유 ID로 기존 지도 연동 시스템 매핑 체결
  if (sharedId) {
    window._currentTripId = parseInt(sharedId);
    sessionStorage.setItem('plannerDraftId', sharedId);
    sessionStorage.setItem('currentPage', 'map'); // 무조건 지도 화면으로 이동

    // 🚨 읽기 전용 주소(/plan/view)로 들어왔을 때의 강력한 차단 로직
    if (location.pathname.includes('/plan/view')) {

      Token.clear();
      _loggedIn = false;
      _currentUser = null;

      // 1. CSS로 수정 버튼, 공유 버튼, 그리고 [교체 요청 바]까지 싹 다 숨김
      const style = document.createElement('style');
      style.innerHTML = `
        .pr-drag, .btn-replace, .btn-map-cfm, #queueToggle { display: none !important; }
        [onclick*=\"openShareModal\"] { display: none !important; } /* 공유 버튼 숨김 */
        #recalcBar, .recalc-bar, [id*=\"recalc\"] { display: none !important; } /* 교체 요청 바 원천 차단 */
        #queueBox, .queue-box { display: none !important; } /* 지도가 억지로 띄우는 자동 교체 박스 원천 차단 */
        
        /* 상단 네비게이션 싹 날리기 (로고 빼고) */
        .nav-link, #navLoginBtn, #navSignupBtn, #navUserNameBtn, #navLogoutBtn, #navBellBtn, #navAdminLink, #navPlannerSteps { display: none !important; }
        
        /* 로고 클릭 이벤트 차단 */
        .logo { pointer-events: none !important; cursor: default !important; }
      `;
      document.head.appendChild(style);

      setTimeout(() => {
        const logoEl = document.querySelector('.logo') || document.querySelector('.nav-logo') || document.querySelector('header a');

        if (logoEl) {
          // 🚨 로고의 a 태그 링크를 완전히 폭파시키고 클릭 이벤트 강제 정지
          logoEl.removeAttribute('href');
          logoEl.onclick = function(e) { e.preventDefault(); return false; };
          logoEl.style.pointerEvents = 'none';

          // 버튼 중복 생성 방지
          if (!document.getElementById('tryTripLinkerBtn')) {
            const tryBtn = document.createElement('a');
            tryBtn.id = 'tryTripLinkerBtn';
            tryBtn.href = 'http://localhost:8080'; // 🚀 클릭 시 이동할 타겟 메인 주소
            tryBtn.target = '_blank';              // 🚀 무조건 새 창으로 열기
            tryBtn.style.textDecoration = 'none';
            tryBtn.style.pointerEvents = 'auto';   // 버튼은 클릭 되도록 허용
            tryBtn.innerHTML = '<span style="display:inline-block; background:var(--sage); color:#fff; padding:6px 14px; border-radius:20px; font-size:12px; font-weight:800; margin-left:15px; cursor:pointer; box-shadow: 0 2px 4px rgba(0,0,0,0.1);">🚀 TripLinker 사용해보기</span>';

            // 로고 바로 오른쪽에 버튼 삽입
            logoEl.parentNode.insertBefore(tryBtn, logoEl.nextSibling);
          }
        }
      }, 100);

      // 2. 브라우저의 기본 드래그 앤 드롭 동작을 강제로 무력화
      document.addEventListener('dragstart', function(e) {
        e.preventDefault();
        e.stopPropagation();
      }, true);

      // 3. 자바스크립트 라이브러리(SortableJS 등)의 드래그 기능 무력화
      setTimeout(() => {
        document.querySelectorAll('[draggable="true"]').forEach(el => el.setAttribute('draggable', 'false'));
        toast('읽기 전용으로 플랜을 열람합니다.');
      }, 800);
    }
  }

  // 기존 토큰으로 자동 로그인 복원
  const savedToken = Token.getAccess();
  if (savedToken) {
    const meRes = await api.get('/api/users/me');
    if (meRes.success && meRes.data) {
      _currentUser = meRes.data;
      _isSuspended = (_currentUser.status === 'SUSPENDED');
      _loggedIn    = true;
      await updateMyPageUI();
      await _loadNotifications();
    } else {
      // 토큰 만료 → 재발급 시도
      const ok = await refreshAccessToken();
      if (!ok) Token.clear();
    }
  }

  const _savedDraftId = sessionStorage.getItem('plannerDraftId');
  if (_savedDraftId && !window._currentTripId) {
    window._currentTripId = parseInt(_savedDraftId);
  } else if (window._currentTripId) {
    sessionStorage.setItem('plannerDraftId', window._currentTripId);
  }
  updateNav();

  const savedPage = sessionStorage.getItem('currentPage');
  if (savedPage && savedPage !== 'main') {
    go(savedPage, false);
    history.replaceState({ page: savedPage }, '', location.href);
  } else {
    history.replaceState({ page: 'main' }, '', location.href);
  }
  document.body.style.visibility = 'visible';
})();

/* ───────────────────────────────────────────────
 * 27. 마이페이지 섹션 전환 (통합 버전)
 * ─────────────────────────────────────────────── */
function showMySection(key, btn) {
  // my-* 요소 중 내부 구성 요소(list, inner, avatar 등)를 제외하고 숨김
  document.querySelectorAll('[id^="my-"]').forEach(el => {
    if (el.id.startsWith('my-') && !el.id.includes('list') && !el.id.includes('inner')
        && !el.id.includes('ledger-inner') && !el.id.includes('avatar')
        && !el.id.includes('name') && !el.id.includes('email')
        && !el.id.includes('pager')) {
      el.style.display = 'none';
    }
  });
  const target = document.getElementById('my-' + key);
  if (target) target.style.display = '';
  document.querySelectorAll('.my-menu').forEach(b => b.classList.remove('on'));
  if (btn) btn.classList.add('on');

  // 섹션별 초기화
  if (key === 'info') {
    resetInfoStep();
    // 속성명 통일: isSocial 로 검사
    if (_currentUser?.isSocial) {
      showSocialInfoEdit();
    } else {
      const notice = document.getElementById('info-social-notice');
      const pwForm = document.getElementById('info-pw-form');
      if (notice) notice.style.display = 'none';
      if (pwForm) pwForm.style.display = 'block';
    }
  }
  if (key === 'withdraw') initWithdrawSection();
  if (key === 'ledger')      { loadPageCSS('/css/styles_budget.css'); updateLedgerList(); }
  if (key === 'scrap-stay')  loadMyScrap('stay');
  if (key === 'scrap-food')  loadMyScrap('food');
  if (key === 'scrap-tour')  loadMyScrap('tour');
  if (key === 'scrap-cafe')  loadMyScrap('cafe');
}

function initWithdrawSection() {
  if (!_currentUser) return;
  const warnEl    = document.getElementById('withdraw-warn-txt');
  const socialBox = document.getElementById('withdraw-social-box');
  const pwBox     = document.getElementById('withdraw-pw-box');

  // 입력값/오류 초기화 (이메일, 비밀번호, 소셜 문구 모두 포함)
  const emailInp  = document.getElementById('withdrawEmailInput');
  const pwInp     = document.getElementById('withdrawPwInput');
  const socialInp = document.getElementById('withdrawSocialInput');

  const emailErr  = document.getElementById('withdraw-email-err');
  const pwErr     = document.getElementById('withdraw-pw-err');
  const socialErr = document.getElementById('withdraw-social-err');

  if (emailInp)  emailInp.value = '';
  if (pwInp)     pwInp.value    = '';
  if (socialInp) socialInp.value = '';

  if (emailErr)  emailErr.style.display  = 'none';
  if (pwErr)     pwErr.style.display     = 'none';
  if (socialErr) socialErr.style.display = 'none';

  // 계정 타입에 따른 화면 분기
  if (_currentUser.isSocial) {
    if (warnEl) warnEl.innerHTML =
        '🔗 소셜 계정 탈퇴 시 카카오·구글과의 연결이 즉시 해제됩니다.<br>' +
        '• 탈퇴 즉시 모든 개인정보가 삭제됩니다.<br>' +
        '• 탈퇴 후 복구는 불가능합니다.<br>' +
        "• 작성한 후기는 '탈퇴한 사용자'로 표시됩니다.";
    if (socialBox) socialBox.style.display = 'block';
    if (pwBox)     pwBox.style.display     = 'none';
  } else {
    if (warnEl) warnEl.innerHTML =
        '• 탈퇴 즉시 모든 개인정보가 삭제됩니다.<br>' +
        '• 탈퇴 후 복구는 불가능합니다.<br>' +
        "• 작성한 후기는 '탈퇴한 사용자'로 표시됩니다.";
    if (socialBox) socialBox.style.display = 'none';
    if (pwBox)     pwBox.style.display     = 'block';
  }
}

