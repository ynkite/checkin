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
  patch: (path, body)   => apiCall(path, { method: 'PATCH', body: JSON.stringify(body) }),
  del:   (path)         => apiCall(path, { method: 'DELETE' })
};

/* ───────────────────────────────────────────────
 * 2. 앱 상태 (서버 응답 기반으로만 갱신)
 * ─────────────────────────────────────────────── */
let _currentUser   = null;   // GET /api/users/me 응답의 data
let _isSuspended   = false;  // role === 'SUSPENDED'
let _loggedIn      = false;
let _userNotifs    = [];     // GET /api/notifications 응답의 data[]
let _myTrips       = [];     // GET /api/trips 응답의 data[]
let _chatSessionId = null;   // POST /api/chat/sessions 응답의 data.sessionId
let _budgetSelectedTripId = null;
let _activeTags    = new Set();
let _loginFailCount = 0;
let _loginLockedUntil = null;

/* ───────────────────────────────────────────────
 * 3. NAV 라우팅
 * ─────────────────────────────────────────────── */
function go(id, addToHistory) {
  if (addToHistory !== false && id !== 'main') history.pushState({page: id}, '', location.href);
  document.querySelectorAll('.page').forEach(p => p.classList.remove('active'));
  const pg = document.getElementById('page-' + id);
  if (pg) pg.classList.add('active');
  document.querySelectorAll('.wf-item').forEach(b => b.classList.remove('on'));
  const map = {
    main: 0, signup: 1, 'signup-kakao': 1, login: 2, mypage: 3, planner: 4,
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
  window.scrollTo(0, 0);

  // 후기/장소 상세 페이지 이동 시 데이터 렌더러 호출
// 후기/장소 상세 페이지 이동 시 렌더러 자동 호출
  setTimeout(function() {
    try {
      var allR = Object.assign({},
          typeof MOCK_ROUTE_REVIEWS!=='undefined' ? MOCK_ROUTE_REVIEWS : {},
          typeof MOCK_STAY_REVIEWS !=='undefined' ? MOCK_STAY_REVIEWS  : {},
          typeof MOCK_FOOD_REVIEWS !=='undefined' ? MOCK_FOOD_REVIEWS  : {}
      );
      if (allR[id] && typeof renderReviewDetailPage==='function') renderReviewDetailPage(id);
      var allP = Object.assign({},
          typeof MOCK_TOUR_PLACES!=='undefined' ? MOCK_TOUR_PLACES : {},
          typeof MOCK_CAFE_PLACES!=='undefined' ? MOCK_CAFE_PLACES : {}
      );
      if (allP[id] && typeof renderPlaceDetailPage==='function') renderPlaceDetailPage(id);
    } catch(e) {}
  }, 30);
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
  const li = document.getElementById('navLoginBtn');
  const si = document.getElementById('navSignupBtn');
  const mi = document.getElementById('navMypageBtn');
  const lo = document.getElementById('navLogoutBtn');
  const al = document.getElementById('navAdminLink');
  const nb = document.getElementById('navBellBtn');

  if (_loggedIn && _currentUser) {
    if (li) li.style.display = 'none';
    if (si) si.style.display = 'none';
    if (mi) mi.style.display = '';
    if (lo) lo.style.display = '';
    if (nb) nb.style.display = '';
    if (al) al.style.display = (_currentUser.role === 'ADMIN') ? '' : 'none';
  } else {
    if (li) li.style.display = '';
    if (si) si.style.display = '';
    if (mi) mi.style.display = 'none';
    if (lo) lo.style.display = 'none';
    if (nb) nb.style.display = 'none';
    if (al) al.style.display = 'none';
  }
}

/** ─── POST /api/auth/login ─── */
// let _loginFailCount = 0, _loginLockedUntil = null;
async function tryLogin() {
  const id = document.getElementById('lid').value.trim();
  const pw = document.getElementById('lpw').value;
  const w  = document.getElementById('login-warn');

  // 클라이언트 잠금 체크 (5회 실패 5분)
  if (_loginLockedUntil && Date.now() < _loginLockedUntil) {
    const s = Math.ceil((_loginLockedUntil - Date.now()) / 1000);
    w.innerHTML = '🔒 계정 잠김. ' + s + '초 후 재시도.';
    w.style.display = 'flex';
    return;
  }
  if (!id || !pw) {
    w.innerHTML = '⚠️ 아이디와 비밀번호를 입력하세요';
    w.style.display = 'flex';
    return;
  }

  const res = await api.post('/api/auth/login', { username: id, password: pw });

// ✅ 교체
  if (!res.success) {
    if (res.data && res.data.locked) {
      // 서버에서 잠금 상태 수신 (locked_until 기반)
      const remain = res.data.remainSeconds
          ? Math.ceil(res.data.remainSeconds) + '초 후 재시도 가능합니다.'
          : '잠시 후 다시 시도해주세요.';
      w.innerHTML = '🔒 로그인 5회 실패로 잠겼습니다. ' + remain;
    } else {
      const failCount = res.data && res.data.failCount;
      w.innerHTML = '⚠️ 아이디 또는 비밀번호 오류'
          + (failCount ? ' (' + failCount + '/5회)' : '');
    }
    w.style.display = 'flex';
    return;
  }
  w.style.display = 'none';

  await _initSession(res.data.accessToken, res.data.refreshToken);
  go('mypage');
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
  // Spring Security OAuth2 리다이렉트
  window.location.href = API_BASE + '/oauth2/authorization/kakao';
}

/** OAuth2 콜백 후 토큰을 URL 파라미터로 수신하는 경우를 처리 */
function _handleOAuthCallback() {
  const params = new URLSearchParams(location.search);
  const accessToken  = params.get('accessToken');
  const refreshToken = params.get('refreshToken');
  if (accessToken && refreshToken) {
    history.replaceState({}, '', location.pathname);
    _initSession(accessToken, refreshToken).then(() => {
      go('mypage');
      toast((_currentUser ? _currentUser.name : '') + '님, 환영합니다! 🎉');
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

/** 카카오 회원가입 완료 */
async function doKakaoSignup() {
  const nameEl = document.getElementById('kakao-name');
  if (!nameEl || !nameEl.value.trim()) { toast('이름을 입력해주세요'); return; }
  // 소셜 가입은 OAuth 콜백에서 처리됨 — 추가 정보 저장
  const res = await api.patch('/api/users/me', { name: nameEl.value.trim() });
  if (res.success) {
    toast('카카오 계정으로 회원가입 완료! 로그인해주세요 🟡');
    setTimeout(() => go('login'), 1000);
  }
}
function startKakaoSignup() {
  toast('🟡 카카오 계정으로 연결되었습니다');
  setTimeout(() => go('signup-kakao'), 600);
}

/* ───────────────────────────────────────────────
 * 5. 마이페이지 (User Domain + Plan Domain)
 * ─────────────────────────────────────────────── */

/** GET /api/users/me + GET /api/trips */
async function updateMyPageUI() {
  if (!_currentUser) return;

  const av = document.getElementById('myAvatar');
  const nm = document.getElementById('myName');
  const em = document.getElementById('myEmail');
  if (av) av.textContent = _currentUser.name ? _currentUser.name[0] : '?';
  if (nm) nm.textContent = _currentUser.name  || '';
  if (em) em.textContent = _currentUser.email || '';

  // 여행 기록
  const tripsRes = await api.get('/api/trips');
  _myTrips = (tripsRes.success && tripsRes.data) ? tripsRes.data : [];

  _renderMyTrips(_myTrips);
  _renderMyReviews();   // 후기는 커뮤니티 API로 처리
  updateLedgerList();
}

function _renderMyTrips(trips) {
  const te = document.getElementById('my-trips');
  if (!te) return;
  te.innerHTML = '<h3 class="my-sec-ttl">내 여행 기록</h3>' + (
    trips.length
      ? trips.map(x => `
          <div class="trip-card" onclick="go('map')">
            <div class="trip-thumb">🗺️</div>
            <div class="trip-info">
              <div class="trip-ttl">${x.title || '여행 플랜'}</div>
              <div class="trip-meta">${x.startDate || ''} ~ ${x.endDate || ''} · ${x.destination || ''}</div>
            </div>
            <div class="trip-budget">${x.status === 'CONFIRMED' ? '✅ 확정' : '📝 초안'}</div>
          </div>`).join('')
      : '<div style="color:var(--text3);font-size:13px;padding:20px 0;text-align:center">여행 기록이 없습니다.</div>'
  );
}

function _renderMyReviews() {
  // 작성한 후기: GET /api/posts?author=me 형태가 없으면 커뮤니티 탭에서 관리
  // 여기서는 빈 상태 렌더링 후 커뮤니티 도메인에서 채움
  const re = document.getElementById('my-reviews');
  if (!re) return;
  re.innerHTML = '<h3 class="my-sec-ttl">작성한 후기</h3>'
    + '<div style="color:var(--text3);font-size:13px;padding:20px 0;text-align:center">후기를 불러오는 중...</div>';
}

/* ───────────────────────────────────────────────
 * 6. 가계부 (Expense Domain)
 * ─────────────────────────────────────────────── */

/** _myTrips를 기반으로 가계부 여행 선택 UI 렌더링 */
async function updateLedgerList() {
  if (!_currentUser) return;
  const el = document.getElementById('my-ledger');
  if (!el) return;

  if (!_budgetSelectedTripId && _myTrips.length > 0) {
    _budgetSelectedTripId = _myTrips[0].tripId;
  }

  let html = '<h3 class="my-sec-ttl">💰 가계부</h3>'
    + '<p style="color:var(--text3);font-size:13px;margin-bottom:16px">여행을 선택하세요</p>';

  if (!_myTrips.length) {
    html += '<div style="color:var(--text3);font-size:13px;padding:20px 0;text-align:center">가계부 기록이 없습니다.</div>';
  } else {
    _myTrips.forEach(l => {
      const isSel = (_budgetSelectedTripId === l.tripId);
      html += `
        <div onclick="selLedger(${l.tripId})"
             style="display:flex;align-items:center;gap:14px;padding:14px 16px;border-radius:var(--r);
                    border:2px solid ${isSel ? 'var(--sage)' : 'var(--border)'};
                    background:${isSel ? 'var(--sage-pale)' : 'var(--surface)'};
                    cursor:pointer;margin-bottom:10px;transition:all .2s">
          <div style="width:42px;height:42px;border-radius:10px;background:var(--sage);
                      display:flex;align-items:center;justify-content:center;font-size:20px">🗺️</div>
          <div style="flex:1">
            <div style="font-weight:700;font-size:14px">${l.title || '여행 플랜'}</div>
            <div style="font-size:11px;color:var(--text3);margin-top:2px">
              ${l.startDate || ''} ~ ${l.endDate || ''} · ${l.destination || ''}
            </div>
          </div>
          <div style="font-size:13px;font-weight:700;color:var(--sage)">
            ${l.status === 'CONFIRMED' ? '✅' : '📝'}
          </div>
        </div>`;
    });
    html += '<button class="btn-f" style="padding:12px 22px;border-radius:var(--r);font-size:14px;margin-top:4px" onclick="goLedger2()">가계부 상세 보기 →</button>';
  }
  el.innerHTML = html;
}

function selLedger(tripId) {
  _budgetSelectedTripId = tripId;
  updateLedgerList();
}

async function goLedger2() {
  go('ledger');
  document.getElementById('ledger-selector').style.display = 'none';
  document.getElementById('ledger-main').style.display = 'block';

  const found = _myTrips.find(l => l.tripId === _budgetSelectedTripId);
  const el = document.getElementById('ledger-trip-meta');
  if (el && found) {
    el.textContent = (found.title || '여행 플랜') + ' · ' + (found.startDate || '') + ' ~ ' + (found.endDate || '');
  }

  // GET /api/trips/{tripId}/expenses
  if (_budgetSelectedTripId) {
    await _loadExpenses(_budgetSelectedTripId);
  }
}

/** GET /api/trips/{tripId}/expenses */
async function _loadExpenses(tripId) {
  const res = await api.get('/api/trips/' + tripId + '/expenses');
  if (!res.success) return;
  // 지출 내역을 ledger-main 영역에 렌더링 (기존 HTML 구조 유지)
  console.log('[Expense] 지출 내역 로드 완료:', res.data);
}

function returnToMyLedger() {
  go('mypage', false);
  const sel   = document.getElementById('ledger-selector');
  const main2 = document.getElementById('ledger-main');
  if (sel)   sel.style.display = 'block';
  if (main2) main2.style.display = 'none';
  ['trips','reviews','likes','scrap-stay','scrap-food','ledger','info','withdraw'].forEach(s => {
    const e = document.getElementById('my-' + s);
    if (e) e.style.display = 'none';
  });
  const lg = document.getElementById('my-ledger');
  if (lg) { lg.style.display = 'block'; updateLedgerList(); }
  document.querySelectorAll('.my-sidebar .my-menu').forEach(b => {
    b.classList.remove('on');
    if (b.textContent.includes('가계부')) b.classList.add('on');
  });
}

/* ───────────────────────────────────────────────
 * 7. 회원정보 수정 (PATCH /api/users/me)
 * ─────────────────────────────────────────────── */
function showMySection(sec, btn) {
  ['trips','reviews','likes','scrap-stay','scrap-food','ledger','info','withdraw'].forEach(s => {
    const e = document.getElementById('my-' + s);
    if (e) e.style.display = 'none';
  });
  const t = document.getElementById('my-' + sec);
  if (t) t.style.display = 'block';
  btn.closest('.my-sidebar').querySelectorAll('.my-menu').forEach(b => b.classList.remove('on'));
  btn.classList.add('on');
  if (sec === 'info')     { resetInfoStep(); _currentUser?.social ? showSocialInfoEdit() : (() => { document.getElementById('info-social-notice').style.display='none'; document.getElementById('info-pw-form').style.display='block'; })(); }
  if (sec === 'withdraw') { const pwb=document.getElementById('withdraw-pw-box'), sob=document.getElementById('withdraw-social-box'); if(_currentUser?.social){ if(pwb) pwb.style.display='none'; if(sob) sob.style.display='block'; } else { if(pwb) pwb.style.display='block'; if(sob) sob.style.display='none'; } }
  if (sec === 'ledger')      updateLedgerList();
  if (sec === 'scrap-stay')  loadMyScrap('stay');
  if (sec === 'scrap-food')  loadMyScrap('food');
}

function resetInfoStep() {
  const s1=document.getElementById('info-pw-step'), s2=document.getElementById('info-edit-form');
  if(s1) s1.style.display='block'; if(s2) s2.style.display='none';
  const i=document.getElementById('infoPwInput'); if(i) i.value='';
  const e=document.getElementById('info-pw-err'); if(e) e.style.display='none';
}

function buildEditHTML(u, isSocial) {
  const mbtiStr = u.mbti || 'ESTP';
  const dims = [
    {k:'ei', pairs:[['E','E(외향)'],['I','I(내향)']]},
    {k:'sn', pairs:[['S','S(감각)'],['N','N(직관)']]},
    {k:'tf', pairs:[['T','T(사고)'],['F','F(감정)']]},
    {k:'jp', pairs:[['J','J(계획)'],['P','P(즉흥)']]}
  ];
  let mbtiHtml = '<div class="form-group"><label class="form-label">MBTI</label><div style="display:flex;flex-direction:column;gap:6px;margin-top:4px">';
  dims.forEach(d => {
    mbtiHtml += `<div style="display:flex;gap:6px;align-items:center"><span style="font-size:11px;color:var(--text3);width:38px">${d.k.toUpperCase()}</span>`;
    d.pairs.forEach(p => { const on = mbtiStr.indexOf(p[0]) >= 0 ? ' on' : ''; mbtiHtml += `<button class="chip chip-sm${on}" onclick="pick(this,'em-${d.k}')">${p[1]}</button>`; });
    mbtiHtml += '</div>';
  });
  mbtiHtml += '</div></div>';
  const regionHtml = `<div class="form-group"><label class="form-label">거주 지역</label><div style="display:flex;gap:8px;margin-top:4px"><select class="form-input" id="edit-region-big" onchange="updateCity(this,'edit-region-city')" style="flex:1"><option value="">도/시 선택</option><option>서울</option><option>경기</option><option>인천</option><option>강원</option><option>충북</option><option>충남</option><option>대전</option><option>세종</option><option>전북</option><option>전남</option><option>광주</option><option>경북</option><option>경남</option><option>대구</option><option>울산</option><option>부산</option><option>제주</option></select><select class="form-input" id="edit-region-city" style="flex:1"><option>시/군/구 선택</option></select></div></div>`;
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

/** PATCH /api/users/password → 현재 비밀번호 서버 검증 */
async function verifyInfoPw() {
  if (!_currentUser) { toast('로그인이 필요합니다'); return; }
  const pw = document.getElementById('infoPwInput').value;
  // 현재 비밀번호 확인: PATCH /api/users/password (currentPassword만 전달해 검증)
  const res = await api.patch('/api/users/password', { currentPassword: pw, newPassword: pw });
  if (!res.success) { document.getElementById('info-pw-err').style.display = 'block'; return; }
  document.getElementById('info-pw-step').style.display = 'none';
  document.getElementById('info-edit-form').style.display = 'block';
  document.getElementById('info-edit-fields').innerHTML = buildEditHTML(_currentUser, false);
}

/** PATCH /api/users/me + (선택) PATCH /api/users/password */
async function saveInfoEdit() {
  const n = document.getElementById('edit-name');
  if (!n || !n.value.trim()) { toast('이름을 입력해주세요'); return; }

  const body = { name: n.value.trim() };
  const res = await api.patch('/api/users/me', body);
  if (!res.success) { toast('⚠️ 정보 수정에 실패했습니다.'); return; }

  const np = document.getElementById('edit-newpw');
  if (np && np.value) {
    if (np.value.length < 8) { toast('비밀번호는 8자 이상이어야 합니다'); return; }
    const np2 = document.getElementById('edit-newpw2');
    if (np2 && np.value !== np2.value) { toast('새 비밀번호가 일치하지 않습니다'); return; }
    const currentPw = document.getElementById('infoPwInput')?.value || '';
    const pwRes = await api.patch('/api/users/password', { currentPassword: currentPw, newPassword: np.value });
    if (!pwRes.success) { toast('⚠️ 비밀번호 변경에 실패했습니다.'); return; }
  }

  // 화면에 반영
  if (_currentUser) _currentUser.name = n.value.trim();
  const av = document.getElementById('myAvatar'); if (av) av.textContent = n.value.trim()[0];
  const nm = document.getElementById('myName');   if (nm) nm.textContent = n.value.trim();

  resetInfoStep();
  toast('✅ 회원정보가 수정되었습니다.');
}

/* ───────────────────────────────────────────────
 * 8. 알림 (GET /api/notifications)
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

function renderNotifList() {
  const list = document.getElementById('notifList');
  if (!list) return;
  if (!_userNotifs.length) {
    list.innerHTML = '<div style="padding:20px;text-align:center;color:var(--text3);font-size:13px">새로운 알림이 없습니다.</div>';
    return;
  }
  list.innerHTML = _userNotifs.map((n, i) => `
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
  api.patch('/api/notifications/read-all', {}).then(() => updateNotifBadge());
}

/** PATCH /api/notifications/{notificationId}/read (삭제는 UI에서만) */
async function deleteNotif(notifId) {
  await api.patch('/api/notifications/' + notifId + '/read', {});
  _userNotifs = _userNotifs.filter(n => n.id !== notifId);
  renderNotifList();
  updateNotifBadge();
}

/* ───────────────────────────────────────────────
 * 9. 지도 장소 팝업 (GET /api/maps/places)
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

  const res = await api.get('/api/maps/places?keyword=' + encodeURIComponent(key));
  if (!res.success || !res.data || !res.data.length) {
    document.getElementById('mpReviews').innerHTML = '<div style="text-align:center;padding:20px;color:var(--text3)">아직 후기가 없습니다.</div>';
    return;
  }

  const place = res.data[0];
  document.getElementById('mpPlace').textContent = place.name || key;

  // 장소별 리뷰 렌더링 (PlaceReview 엔티티)
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
 * 10. AI 챗봇 (Chat Domain)
 * POST /api/chat/sessions  → sessionId 생성
 * POST /api/chat/message   → AI 응답
 * ─────────────────────────────────────────────── */

/** POST /api/chat/sessions : 플래너 입력 후 챗봇 세션 생성 */
async function startChatWithSummary() {
  const dest   = (document.getElementById('sum-dest')   || {}).textContent || '-';
  const ppl    = (document.getElementById('sum-people') || {}).textContent || '-';
  const budget = (document.getElementById('sum-budget') || {}).textContent || '-';
  const msgs   = document.getElementById('chatMsgs');
  if (!msgs) return;
  msgs.innerHTML = '';

  // 현재 활성 플랜의 tripId (플랜 생성 후 세션 연결)
  const tripId = _budgetSelectedTripId || null;
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

/** POST /api/chat/message : 메시지 전송 + AI 응답 수신 */
async function sendMsg() {
  const inp = document.getElementById('chatInp');
  const txt = inp.value.trim();
  if (!txt) return;
  addBubble(txt, 'user');
  inp.value = '';

  const sessionId = _chatSessionId;
  if (!sessionId) {
    // 세션 없으면 임시 생성
    const sesRes = await api.post('/api/chat/sessions', { planId: null });
    if (sesRes.success && sesRes.data) _chatSessionId = sesRes.data.sessionId;
  }

  const res = await api.post('/api/chat/message', { sessionId: _chatSessionId, message: txt });
  if (res.success && res.data && res.data.response) {
    addBubble(res.data.response, 'bot');
  } else {
    addBubble('죄송합니다, 잠시 후 다시 시도해주세요.', 'bot');
  }
}

function addBubble(txt, role, qrs) {
  const msgs = document.getElementById('chatMsgs');
  const d = document.createElement('div');
  d.className = 'cmsg' + (role === 'user' ? ' user' : '');
  if (role === 'bot') {
    d.innerHTML = `<div class="cav bot">🤖</div><div><div class="cbubble bot">${txt}</div>`
      + (qrs ? '<div class="qr-row">' + qrs.map(q => `<button class="qr-btn" onclick="document.getElementById('chatInp').value='${q}';sendMsg()">${q}</button>`).join('') + '</div>' : '')
      + '</div>';
  } else {
    d.innerHTML = `<div class="cav user">나</div><div><div class="cbubble user">${txt}</div></div>`;
  }
  msgs.appendChild(d);
  msgs.scrollTop = msgs.scrollHeight;
}

/* ───────────────────────────────────────────────
 * 11. 회원가입 유효성 검사 (실시간 API 중복확인)
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
 * 12. 비밀번호 재설정
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
 * 13. 커뮤니티 (Post Domain — 공통 함수)
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
 * 14. 슬라이더
 * ─────────────────────────────────────────────── */
let _si = 0, _sn = 4;
function changeSlide(d) { goSlide((_si + d + _sn) % _sn); }
function goSlide(i) {
  document.querySelectorAll('.slide').forEach((s,j) => s.classList.toggle('on', j===i));
  document.querySelectorAll('.dot').forEach((d,j)  => d.classList.toggle('on', j===i));
  _si = i;
}

/* ───────────────────────────────────────────────
 * 15. AI 플래너 입력폼 + 일정 생성
 * POST /api/ai/schedule/generate
 * ─────────────────────────────────────────────── */
function startPlanFromCard(data) {
  go('planner'); goPlanStep(1);
  const pr = document.getElementById('dest-prov');
  if (pr && data.prov) { for(let i=0;i<pr.options.length;i++){if(pr.options[i].text===data.prov){pr.value=pr.options[i].value||pr.options[i].text;break;}} updateCityDest(pr); }
  const sd=document.getElementById('sum-dest');    if(sd) sd.textContent = data.dest||'';
  const sp=document.getElementById('sum-people');  if(sp) sp.textContent = data.people?(data.people+'인'):'';
  const sb=document.getElementById('sum-budget');  if(sb) sb.textContent = data.budget?('₩'+data.budget.toLocaleString()):'';
  toast((data.dest||'') + ' 여행 플랜을 시작합니다 ✈');
}

function goPlanStep(n) {
  for(let i=1;i<=3;i++) {
    const sb2=document.getElementById('sb-'+i), sp2=document.getElementById('sp-'+i);
    if(!sb2||!sp2) continue;
    sb2.classList.remove('active','done'); sp2.classList.remove('active');
    if(i<n) sb2.classList.add('done');
    if(i===n){ sb2.classList.add('active'); sp2.classList.add('active'); }
  }
  if(n===3) startChatWithSummary();
}

/* ───────────────────────────────────────────────
 * 16. 지도 UI
 * ─────────────────────────────────────────────── */
function showDay(day, btn) {
  btn.closest('.day-tabs').querySelectorAll('.day-tab').forEach(b => b.classList.remove('on'));
  btn.classList.add('on');
  document.querySelectorAll('.day-section').forEach(s => {
    if(day==='all') s.style.display='block';
    else s.style.display = (s.dataset.day==day)?'block':'none';
  });
}
function switchMapTab(tab, btn) {
  document.querySelectorAll('.btn-map-act').forEach(b => b.classList.remove('on'));
  btn.classList.add('on');
  const mv=document.getElementById('mapView'), bv=document.getElementById('budgetView');
  if(tab==='map'){mv.style.display='block';bv.style.display='none';}
  else           {mv.style.display='none'; bv.style.display='block';}
}
function toggleMarker(btn, type) {
  btn.classList.toggle('on');
  const isOn = btn.classList.contains('on');
  document.querySelectorAll('.map-pin[data-type="'+type+'"]').forEach(p => p.style.display=isOn?'flex':'none');
  toast((isOn?'✅ 표시':'❌ 숨김') + ' · ' + btn.textContent.trim().replace(/[🏨🍽️📍]/g,'').trim());
}

/* ───────────────────────────────────────────────
 * 17. 교체 큐 (장소 교체 요청)
 * POST /api/ai/schedule/regenerate
 * ─────────────────────────────────────────────── */
let _q = [];
function showReplaceInput(btn, name) {
  const id='ri-'+name, area=document.getElementById(id); if(!area) return;
  const open=area.style.display==='block';
  document.querySelectorAll('.replace-area').forEach(a => a.style.display='none');
  if(!open) area.style.display='block';
}
function addQueue(name) {
  const inp=document.getElementById('rt-'+name), req=inp?inp.value.trim():'';
  if(!req){toast('교체 요구사항을 입력해주세요.');return;}
  _q.push({place:name,req});
  document.querySelectorAll('.replace-area').forEach(a => a.style.display='none');
  if(inp) inp.value='';
  renderQ(); toast('"'+name+'" 교체 요청이 대기열에 추가됐습니다.');
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

/** POST /api/ai/schedule/regenerate */
async function execAllReplace() {
  if(_q.length===0) return;
  const btn=document.getElementById('btnAll'), rb=document.getElementById('recalcBar'), cnt=_q.length;
  if(btn){btn.textContent='⏳ AI 처리 중...';btn.disabled=true;}
  if(rb)  rb.style.display='flex';

  const feedback = _q.map(q => q.place + ': ' + q.req).join(', ');
  const res = await api.post('/api/ai/schedule/regenerate', { planId: _budgetSelectedTripId, feedback });

  if(rb) rb.style.display='none';
  _q=[]; renderQ();
  if(btn){btn.textContent='✅ 전체 교체하기';btn.disabled=true;}
  if(res.success) {
    toast('🎉 '+cnt+'개 장소 교체 완료!');
    setTimeout(()=>toast('✅ route_recalc_needed → 0 초기화'),1000);
    // 교통비 재계산
    if(_budgetSelectedTripId) {
      await api.post('/api/trips/'+_budgetSelectedTripId+'/routes/recalculate', {});
    }
  } else {
    toast('⚠️ 교체 처리에 실패했습니다.');
  }
}

/* ───────────────────────────────────────────────
 * 18. 관리자 (Admin Domain)
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

/* ───────────────────────────────────────────────
 * 19. 블로그 에디터 이미지 (AWS S3 업로드 준비)
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
 * 20. 모달 / 기타 UI 헬퍼
 * ─────────────────────────────────────────────── */
function showConfirmModal()  { document.getElementById('confirmModal').classList.add('open'); }
function openConfirmDone()   { document.getElementById('confirmDoneModal').classList.add('open'); }
function openShareModal()    { document.getElementById('shareModal').classList.add('open'); }
function showPlaceReviews(val){ const sec=document.getElementById('placeReviewsSection'); if(sec) sec.style.display=val?'block':'none'; }
function setStars(btn, rating){ btn.closest('.star-sel').querySelectorAll('.star-btn').forEach((b,i)=>b.classList.toggle('lit',i<rating)); }
function setShareTab(btn, tab) {
  document.querySelectorAll('.share-tab').forEach(b=>b.classList.remove('on')); btn.classList.add('on');
  document.getElementById('share-members').style.display = tab==='members'?'block':'none';
  document.getElementById('share-post').style.display    = tab==='post'?'block':'none';
}

/* ───────────────────────────────────────────────
 * 21. Hero & 큐레이션 미리보기
 * ─────────────────────────────────────────────── */
function setDest(dest) {
  const inp=document.getElementById('heroInp'); if(inp){inp.value=dest;inp.focus();}
  const destMap={'제주도':'제주','강릉':'강원','부산':'부산','전주':'전북','경주':'경북'};
  const prov=destMap[dest]; if(prov){const pr=document.getElementById('dest-prov'); if(pr){pr.value=prov;updateCityDest(pr);}}
  const sd=document.getElementById('sum-dest'); if(sd) sd.textContent=dest;
}
function heroSearch() {
  const inp=document.getElementById('heroInp'); const val=inp?inp.value.trim():'';
  if(!val){toast('여행지를 입력해주세요');return;}
  if(['도쿄','파리','오사카','뉴욕'].some(c=>val.includes(c))){toast('⚠️ 본 서비스는 국내 전용입니다');return;}
  startPlanFromCard({dest:val,people:2,transport:'자차',companion:'커플',styles:[],budget:500000});
}

const _md = {
  jeju:     {tags:['시즌 큐레이션','초여름'],ttl:'🌊 제주 에메랄드 해안 3박 4일',budget:'₩425,000~',places:'8곳',dur:'3박 4일',stay:'협재 오션뷰 풀빌라 외 1건',foods:[{icon:'🦞',name:'민락어민활어직판장 횟집',r:'4.6'},{icon:'☕',name:'오션뷰 카페 에메랄드힐',r:'4.8'}]},
  seorak:   {tags:['가을 특선','10월 단풍'],ttl:'🍁 설악산 단풍 트레킹 2박 3일',budget:'₩380,000~',places:'6곳',dur:'2박 3일',stay:'설악동 게스트하우스 외 1건',foods:[{icon:'🍜',name:'속초 닭강정 명가',r:'4.5'}]},
  gyeongju: {tags:['봄 기획','벚꽃 시즌'],ttl:'🌸 경주 벚꽃 역사 기행 1박 2일',budget:'₩290,000~',places:'7곳',dur:'1박 2일',stay:'경주 한옥 스테이 외 1건',foods:[{icon:'🍞',name:'황남빵 카페',r:'4.7'}]},
  busan:    {tags:['여름 특선','서핑 시즌'],ttl:'🏄 부산 해운대 서핑 투어 2박 3일',budget:'₩620,000~',places:'9곳',dur:'2박 3일',stay:'해운대 호텔 외 1건',foods:[{icon:'🐟',name:'자갈치시장 회',r:'4.6'}]},
  gangneung:{tags:['커뮤니티 인기','힐링'],ttl:'☕ 강릉 바다+커피 힐링 루트',budget:'₩480,000~',places:'10곳',dur:'2박 3일',stay:'안목해변 오션뷰 펜션 외 1건',foods:[{icon:'☕',name:'테라로사 강릉본점',r:'4.9'}]},
  jeonju:   {tags:['가성비 TOP','한식'],ttl:'🍚 전주 한옥마을 미식 기행 1박 2일',budget:'₩320,000~',places:'8곳',dur:'1박 2일',stay:'전통 한옥 스테이 외 1건',foods:[{icon:'🍚',name:'비빔밥 명가',r:'4.8'}]},
  namhae:   {tags:['커뮤니티 인기','액티비티'],ttl:'🏝 남해 독일마을+다랭이마을 2박 3일',budget:'₩620,000~',places:'7곳',dur:'2박 3일',stay:'오션뷰 펜션 외 1건',foods:[{icon:'🐙',name:'바다낙지 식당',r:'4.5'}]}
};

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
}
function closePrev() { const m = document.getElementById('prevModal'); if(m) m.classList.remove('open'); }

/* ───────────────────────────────────────────────
 * 22. Chips / MBTI / Location
 * ─────────────────────────────────────────────── */
function pick(chip, grp) { const p=chip.closest('.chip-row,.chip-grid4'); if(!p) return; p.querySelectorAll('.chip').forEach(c=>c.classList.remove('on')); chip.classList.add('on'); }
function tog(chip)    { chip.classList.toggle('on'); }
function togBtn(btn)  { btn.classList.toggle('off'); }
function pickVis(btn) { btn.closest('div').querySelectorAll('.vis-chip').forEach(b=>b.classList.remove('on')); btn.classList.add('on'); }
function showOtherInput(id, chip) { const el=document.getElementById(id); if(!el) return; if(chip.classList.contains('on')) el.classList.add('show'); else{el.classList.remove('show');el.value='';} }

let _mbti = {ei:'E',sn:'S',tf:'T',jp:'P'};
function selectMbti(btn, dim, val) {
  const parent=btn.closest('div'); parent.querySelectorAll('.chip').forEach(b=>b.classList.remove('on')); btn.classList.add('on');
  _mbti[dim]=val;
  const result=_mbti.ei+_mbti.sn+_mbti.tf+_mbti.jp;
  const el=document.getElementById('mbti-result'); if(el) el.textContent=result;
  const den=document.getElementById('mbti-density'); if(den) den.textContent=_mbti.jp==='P'?'→ P: 여유롭게 자동 설정':'→ J: 빼곡하게 자동 설정';
  // PATCH /api/users/mbti (로그인 상태일 때)
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
 * 23. TOAST
 * ─────────────────────────────────────────────── */
let _tt = null;
function toast(msg, dur=2800) {
  const t=document.getElementById('toast'); if(!t) return;
  if(_tt) clearTimeout(_tt);
  t.textContent=msg; t.classList.add('show');
  _tt=setTimeout(()=>t.classList.remove('show'), dur);
}

/* ───────────────────────────────────────────────
 * 24. 전역 이벤트 리스너
 * ─────────────────────────────────────────────── */
document.addEventListener('keydown', e => {
  if(e.key==='Escape'){
    closePrev(); closeWrite();
    document.querySelectorAll('.overlay.open').forEach(o=>o.classList.remove('open'));
    closeNotifPopup();
  }
});

window.addEventListener('popstate', e => {
  if(e.state&&e.state.page){
    const p=e.state.page;
    document.querySelectorAll('.page').forEach(pg=>pg.classList.remove('active'));
    const pg=document.getElementById('page-'+p); if(pg) pg.classList.add('active');
  }
});

/* ───────────────────────────────────────────────
 * 25. 초기화
 * ─────────────────────────────────────────────── */
(async () => {
  // OAuth 콜백 처리 (URL에 토큰이 있을 경우)
  _handleOAuthCallback();

  // 기존 토큰으로 자동 로그인 복원
  const savedToken = Token.getAccess();
  if (savedToken) {
    const meRes = await api.get('/api/users/me');
    if (meRes.success && meRes.data) {
      _currentUser = meRes.data;
      // ✅ 수정
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
  updateNav();
})();

function showMySection(key, btn) {
  document.querySelectorAll('[id^="my-"]').forEach(el => {
    if (el.id.startsWith('my-') && !el.id.includes('list') && !el.id.includes('inner')
        && !el.id.includes('ledger-inner') && !el.id.includes('avatar')
        && !el.id.includes('name') && !el.id.includes('email')) {
      el.style.display = 'none';
    }
  });
  const target = document.getElementById('my-' + key);
  if (target) target.style.display = '';
  document.querySelectorAll('.my-menu').forEach(b => b.classList.remove('on'));
  if (btn) btn.classList.add('on');
}
