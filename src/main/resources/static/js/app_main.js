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
const _LEDGER_CARD_PAGE_SIZE = 5;
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
// 로고/홈 클릭 → 메인페이지로 이동하면서 강제 새로고침
// (SPA라 URL이 이미 '/'여도 reload가 생략되지 않도록 sessionStorage를 main으로 맞추고 reload)
function goHomeRefresh() {
    try {
        sessionStorage.setItem('currentPage', 'main');
        sessionStorage.removeItem('map_refresh_lock');
    } catch (e) {}
    // 경로가 '/'가 아니면 메인 경로로 이동(자동 새로고침), 이미 '/'면 강제 reload
    if (location.pathname !== '/') {
        location.href = '/';
    } else {
        location.reload();
    }
}

/**
 * 지정한 페이지로 "새로고침하며" 진입한다.
 * sessionStorage에 목표 페이지를 저장한 뒤 reload → 로드 시 복원 로직이 해당 페이지를 연다.
 * (홈 탭과 동일하게, 클릭 시 실제 새로고침 후 해당 탭으로 들어가는 동작)
 */
function goRefresh(id) {
    try {
        sessionStorage.setItem('currentPage', id);
        sessionStorage.removeItem('map_refresh_lock');
    } catch (e) {}
    if (location.pathname !== '/') {
        location.href = '/';
    } else {
        location.reload();
    }
}

/**
 * 관리자 하위 섹션(대시보드/회원/신고/큐레이션)으로 새로고침하며 진입한다.
 * admin 페이지를 currentPage로 저장하고, 열어야 할 섹션을 따로 저장한 뒤 reload.
 */
function goAdminRefresh(sec) {
    try {
        sessionStorage.setItem('currentPage', 'admin');
        sessionStorage.setItem('adminSection', sec);
        sessionStorage.removeItem('map_refresh_lock');
    } catch (e) {}
    if (location.pathname !== '/') {
        location.href = '/';
    } else {
        location.reload();
    }
}

function go(id, addToHistory) {
    if (id !== 'map') {
        window._isInvitedEditView = false;
        if (typeof updateNav === 'function') updateNav();
    }

    sessionStorage.setItem('currentPage', id);  // [v2] 새로고침 복원용
    if (addToHistory !== false) history.pushState({page: id}, '', '/');
    document.querySelectorAll('.page').forEach(p => p.classList.remove('active'));
    const pg = document.getElementById('page-' + id);
    if (pg) pg.classList.add('active');

    if (id === 'map') {
        // 🎯 [수정] SPA 이동 시에도 경로 데이터를 다시 불러와 렌더링 → 새로고침 불필요
        //   기존엔 relayout()만 호출해서 빈 지도가 남았고, 새로고침(DOMContentLoaded)을
        //   해야 initMapPage()가 돌아 경로가 보였음. 여기서 직접 initMapPage()를 호출한다.
        setTimeout(function() {
            if (typeof initMapPage === 'function') {
                // initMapPage 내부에서 DB/sessionStorage 경로를 읽어 마커·동선을 다시 그림
                initMapPage();
            } else if (window._kakaoMap) {
                window._kakaoMap.relayout();
                if (typeof updateBoundsForDay === 'function') updateBoundsForDay('all');
            } else if (typeof initKakaoMap === 'function') {
                initKakaoMap();
            }
        }, 100);
    }

    //가계부 페이지 진입 시 항상 실제 데이터로 갱신
    if (id === 'ledger') {
        loadPageCSS('/css/styles_budget.css');
        _populateLedgerTripCards();
        const selEl  = document.querySelector('.ledger-selector-outer');
        const mainEl = document.getElementById('ledger-main');
        const tripStillValid = _myTrips.some(t => t.id === _budgetSelectedTripId);
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

        if (window._pendingCurationPlan) {
            const c = window._pendingCurationPlan;
            window._pendingCurationPlan = null;
            setTimeout(() => _applyCurationPreferences(c), 160);
        }

    }
    if (typeof _syncPlannerTopbar === 'function') setTimeout(_syncPlannerTopbar, 60);

    if (id === 'mypage') {
        if (typeof updateMyPageUI === 'function') updateMyPageUI();
    }

    if (id === 'community') {
        setTimeout(function () {
            if (window._pendingPlaceReview) return;
            var tab = (typeof _commState !== 'undefined' && _commState.currentTab) ? _commState.currentTab : 'route';
            var tabEl = document.getElementById('tab-' + tab);
            // 이미 게시글이 있으면 재로드 안 함 (뒤로가기 시 깜박임/초기화 방지)
            if (tabEl && tabEl.querySelector('.comm-post-item')) return;
            if (['stay', 'food', 'tour', 'cafe'].indexOf(tab) !== -1) {
                if (typeof window._loadPlaceCards === 'function') window._loadPlaceCards(tab, 0, true);
            } else {
                if (typeof window.loadCommunityPosts === 'function') window.loadCommunityPosts(0, true);
            }
        }, 100);
    }
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
        window._currentUser = _currentUser;
        window._isAdmin   = (_currentUser.role === 'ADMIN');   // 숨김 콘텐츠 접근 제어용
        _isSuspended  = (_currentUser.role === 'SUSPENDED');
    }

    updateNav();
    await updateMyPageUI();
    await _loadNotifications();
}

/** 강제 로그아웃 (토큰 만료 등) */
function forceLogout() {
    Token.clear();
    // 🎯 [캐시 박멸] 세션 만료로 강제 로그아웃 시에도 플랜 캐시 전부 제거
    window._currentTripId = null; window._mapDestRegion = null;
    window._planHydrateTripId = null; window._planLoadedTripId = null; window._chatRestored = false;
    sessionStorage.removeItem('ai_generated_route');
    sessionStorage.removeItem('plannerDraftId');
    sessionStorage.removeItem('plannerDraftStep');
    sessionStorage.removeItem('plannerDraftState');
    sessionStorage.removeItem('map_fallback_queue');
    _currentUser = null; window._currentUser = null; window._isAdmin = false; _isSuspended = false; _loggedIn = false;
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

    // 🎯 [신규] 상단바에 '초대받은 일정 저장하기' 버튼 동적 생성
    let saveInviteBtn = document.getElementById('navSaveInviteBtn');
    if (!saveInviteBtn) {
        const navBtns = document.querySelector('.nav-btns');
        if (navBtns) {
            saveInviteBtn = document.createElement('button');
            saveInviteBtn.id = 'navSaveInviteBtn';
            saveInviteBtn.className = 'btn-f';
            saveInviteBtn.style.background = 'var(--warm)'; // 눈에 띄는 주황색 계열
            saveInviteBtn.innerHTML = '🔗 링크 보관하기';

            saveInviteBtn.onclick = () => {
                const tid = window._currentTripId;
                if (!tid) return;

                saveInviteBtn.innerHTML = '⏳ 저장 중...';
                saveInviteBtn.style.opacity = '0.7';
                saveInviteBtn.style.pointerEvents = 'none';

                const exactInviteUrl = window.location.href;
                const titleText = document.querySelector('.ml-plan-ttl')?.textContent || '초대받은 여행 플랜';
                const metaText = document.querySelector('.ml-plan-meta')?.textContent?.split('·')[0]?.trim() || '공유받은 지역';

                api.post('/api/trips/invited', {
                    originalTripId: tid,
                    inviteUrl: exactInviteUrl,
                    title: titleText,
                    destination: metaText
                }).then(res => {
                    if(res.success) {
                        toast('✅ [초대받은 일정] 탭에 안전하게 저장되었습니다!');
                        saveInviteBtn.style.display = 'none';
                        if (typeof updateMyPageUI === 'function') updateMyPageUI();
                    } else {
                        toast('⚠️ 저장 중 오류가 발생했습니다.');
                        saveInviteBtn.innerHTML = '📌 내 목록에 담기';
                        saveInviteBtn.style.opacity = '1';
                        saveInviteBtn.style.pointerEvents = 'auto';
                    }
                });
            };
            navBtns.insertBefore(saveInviteBtn, navBtns.firstChild);
        }
    }

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
        // 로그아웃 시 알림 배지 숫자도 반드시 숨김
        const _badgeEl = document.getElementById('notifBadge');
        if (_badgeEl) _badgeEl.style.display = 'none';
        if (al) al.style.display = 'none';
    }

    // 🎯 [신규] 수정 권한으로 접속했고, 로그인 상태일 때만 '저장하기' 버튼 노출
    if (saveInviteBtn) {
        const isMapPageActive = document.getElementById('page-map')?.classList.contains('active');

        if (_loggedIn && window._isInvitedEditView) {
            api.get('/api/trips/invited').then(r => {
                if (r.success && r.data) {
                    const currentUrl = window.location.href;
                    const tid = window._currentTripId;
                    const isAlreadySaved = r.data.some(item => item.originalTripId === tid || item.url === currentUrl);

                    saveInviteBtn.style.display = isAlreadySaved ? 'none' : 'inline-block';
                }
            });
        } else {
            saveInviteBtn.style.display = 'none';
        }
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

    // 초대장 링크 처리
    const redirectUrl = sessionStorage.getItem('redirectUrl');
    if (redirectUrl) {
        sessionStorage.removeItem('redirectUrl');
        window.location.href = redirectUrl;
        return;
    }

    // 커뮤니티에서 "이 경로로 여행 계획하기" 후 로그인한 경우 플래너로 이동
    if (window._pendingLoginThenPlanner) {
        const pending = window._pendingLoginThenPlanner;
        window._pendingLoginThenPlanner = null;

        if (typeof resetPlannerForm === 'function') resetPlannerForm();

        window._currentTripId = null;
        window._chatRestored = false;
        window._planHydrateTripId = null;
        window._planLoadedTripId = null;
        window._communityPlannerOriginalPreference = pending;

        sessionStorage.removeItem('plannerDraftId');
        sessionStorage.removeItem('plannerDraftStep');
        sessionStorage.removeItem('plannerDraftState');
        sessionStorage.removeItem('ai_generated_route');

        go('planner', false);
        if (typeof goPlanStep === 'function') goPlanStep(1);

        setTimeout(function () {
            if (window._commUtil && typeof window._commUtil.applyCommunityPlannerSeedToPlanner === 'function') {
                window._commUtil.applyCommunityPlannerSeedToPlanner(pending);
                return;
            }
            if (window._commUtil && typeof window._commUtil.applyCommunityDestinationToPlanner === 'function') {
                window._commUtil.applyCommunityDestinationToPlanner(pending);
            }
        }, 180);
        return;
    }

    go('main');

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
    // 🎯 [캐시 박멸] 다른 계정 로그인 시 이전 플랜이 남지 않도록 플랜 관련 캐시 전부 제거
    window._currentTripId = null; window._mapDestRegion = null;
    window._planHydrateTripId = null; window._planLoadedTripId = null; window._chatRestored = false;
    sessionStorage.removeItem('ai_generated_route');
    sessionStorage.removeItem('plannerDraftId');
    sessionStorage.removeItem('plannerDraftStep');
    sessionStorage.removeItem('plannerDraftState');
    sessionStorage.removeItem('map_fallback_queue');
    _currentUser = null; window._currentUser = null; window._isAdmin = false; _isSuspended = false; _loggedIn = false;
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
        setTimeout(function() { go('main'); }, 1000);
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

    // ✨ 내 기록과 초대받은 기록을 병렬로 각각 가져옵니다!
    const [tripsRes, invitedRes] = await Promise.all([
        api.get('/api/trips'),
        api.get('/api/trips/invited'),
        _renderMyReviews(),
        _renderMyLikedPosts(),
    ]);

    _myTrips = (tripsRes.success && tripsRes.data) ? tripsRes.data : [];
    const invitedList = (invitedRes.success && invitedRes.data) ? invitedRes.data : [];

    _renderMyTrips(_myTrips);
    _renderMyInvitedTrips(invitedList); // DB 데이터로 렌더러 기동
    updateLedgerList();
}

window._invitedTripsData = [];
window._invitedTripsCurrentPage = 1;
const INVITED_PER_PAGE = 5;
window._invitedDeleteMode = false;

/* ── 마이페이지 공통 페이저: 5개 숫자만 노출 + 현재 페이지 중앙 정렬 ── */
function _getMyPageWindow(currentPage, totalPages, windowSize = 5) {
    const total = Math.max(1, Number(totalPages) || 1);
    const size = Math.min(Number(windowSize) || 5, total);
    const current = Math.min(Math.max(Number(currentPage) || 1, 1), total);

    let start = current - Math.floor(size / 2);
    if (start < 1) start = 1;
    if (start + size - 1 > total) start = Math.max(1, total - size + 1);

    return Array.from({ length: size }, function (_, idx) { return start + idx; });
}

function _renderMyPagePagerHtml(currentPage, totalPages, callBuilder) {
    if (!totalPages || totalPages <= 0) return '';

    const current = Math.min(Math.max(Number(currentPage) || 1, 1), Number(totalPages));
    const pages = _getMyPageWindow(current, totalPages, 5);

    let html = '<div class="community-v2-pagination mypage-section-pagination" style="margin-top:24px;padding-bottom:20px;">';

    if (totalPages > 1) {
        html += '<button type="button" class="community-v2-page-btn" onclick="' + callBuilder(Math.max(1, current - 1)) + '" ' + (current === 1 ? 'disabled' : '') + '>&lt;</button>';
    }

    pages.forEach(function (p) {
        html += '<button type="button" class="community-v2-page-btn ' + (p === current ? 'on' : '') + '" onclick="' + callBuilder(p) + '">' + p + '</button>';
    });

    if (totalPages > 1) {
        html += '<button type="button" class="community-v2-page-btn" onclick="' + callBuilder(Math.min(Number(totalPages), current + 1)) + '" ' + (current === Number(totalPages) ? 'disabled' : '') + '>&gt;</button>';
    }

    html += '</div>';
    return html;
}


function _renderMyInvitedTrips(trips = null, page = 1) {
    const container = document.getElementById('my-invited-list');
    if (!container) return;

    if (trips !== null) {
        window._invitedTripsData = trips;
        window._invitedTripsCurrentPage = 1;
    } else {
        window._invitedTripsCurrentPage = page;
    }

    // 최신 저장순으로 정렬
    const allInvited = window._invitedTripsData || [];
    allInvited.sort((a, b) => new Date(b.savedAt) - new Date(a.savedAt));

    const totalPages = Math.ceil(allInvited.length / INVITED_PER_PAGE) || 1;
    const currentPage = window._invitedTripsCurrentPage;

    const startIndex = (currentPage - 1) * INVITED_PER_PAGE;
    const paginated = allInvited.slice(startIndex, startIndex + INVITED_PER_PAGE);

    let html = `
  <div class="my-sec-hd" style="display:flex; justify-content:space-between; align-items:center; margin-bottom:16px;">
    <h3 class="my-sec-ttl" style="margin:0;">초대받은 일정</h3>
    <div style="display:flex; gap:8px; margin-left:auto;">
      ${window._invitedDeleteMode ? `
        <button onclick="execInvitedBulkDelete()" style="padding:4px 10px; background:var(--coral); color:#fff; border:none; border-radius:6px; font-size:12px; font-weight:700; cursor:pointer;">선택 삭제 실행</button>
        <button onclick="toggleInvitedDeleteMode(false)" style="padding:4px 10px; background:var(--cream); color:var(--text2); border:1px solid var(--border); border-radius:6px; font-size:12px; font-weight:600; cursor:pointer;">취소</button>
      ` : `
        <button onclick="toggleInvitedDeleteMode(true)" style="padding:4px 10px; background:var(--cream); color:var(--text2); border:1px solid var(--border); border-radius:6px; font-size:12px; font-weight:600; cursor:pointer;">삭제하기</button>
      `}
    </div>
  </div>
  <div id="my-invited-list" class="trip-list">`;

    if (paginated.length > 0) {
        html += paginated.map(x => {
            // 🎯 삭제 모드일 때는 클릭 시 체크박스가 눌리게 하고, 평소에는 링크로 이동
            const cardClickAction = window._invitedDeleteMode
                ? `const chk = document.getElementById('chk-invited-${x.id}'); if(chk) chk.checked = !chk.checked;`
                : `window.location.href='${x.url}'`;

            return `
      <div class="trip-card" onclick="${cardClickAction}" style="cursor:pointer; position:relative; display:flex; align-items:center; gap:12px;"> 
        ${window._invitedDeleteMode ? `
          <input type="checkbox" class="invited-del-chk" id="chk-invited-${x.id}" value="${x.id}" onclick="event.stopPropagation();" style="width:16px; height:16px; cursor:pointer; margin-left:4px;">
        ` : ''}
        <div class="trip-thumb">🤝</div>
        <div class="trip-info" style="flex:1;">
          <div class="trip-ttl">${x.title || '초대받은 여행 플랜'}</div>
          <div class="trip-meta">${x.destination || '공유받은 지역'}</div>
        </div>
        <div class="trip-budget" style="color:var(--sage); font-size:12px; font-weight:800; min-width:80px; text-align:right;">
          ${window._invitedDeleteMode ? '<span style="color:var(--text3); font-size:11px; font-weight:600;">선택 대기</span>' : '🔗 연결됨'}
        </div>
      </div>`;
        }).join('');

        html += `</div>`; // .trip-list 닫기

        html += _renderMyPagePagerHtml(currentPage, totalPages, function (p) {
            return '_renderMyInvitedTrips(null, ' + p + ')';
        });

    } else {
        html += `</div>`;
        html += '<div style="color:var(--text3);font-size:13px;padding:20px 0;text-align:center">초대받은 일정이 없습니다.</div>';
    }

    container.innerHTML = html;
}

// 🎯 내 초대받은 일정 삭제 모드 토글
function toggleInvitedDeleteMode(isDeleteMode) {
    window._invitedDeleteMode = isDeleteMode;
    _renderMyInvitedTrips(null, window._invitedTripsCurrentPage);
}

// 🎯 체크박스 선택된 항목들 일괄 삭제 처리 (API 연동)
async function execInvitedBulkDelete() {
    const chks = document.querySelectorAll('.invited-del-chk:checked');
    if (chks.length === 0) { toast('삭제할 일정을 선택해주세요.'); return; }
    if (!confirm(`선택한 ${chks.length}개의 초대 일정을 정말 삭제하시겠습니까?`)) return;

    let successCount = 0;
    for (const chk of chks) {
        const id = chk.value;
        const res = await api.del('/api/trips/invited/' + id);
        if (res.success) successCount++;
    }

    if (successCount > 0) {
        toast(`✅ ${successCount}개의 일정이 삭제되었습니다.`);
        window._invitedDeleteMode = false;
        updateMyPageUI(); // 마이페이지 전체 리로드
    } else {
        toast('⚠️ 삭제 처리에 실패했습니다.');
    }
}


// 1. 기존 함수 덮어쓰기 (onclick 부분이 수정됨!)
window._myTripsData = [];
window._myTripsCurrentPage = 1;
const TRIPS_PER_PAGE = 5; // 🎯 한 페이지에 보여줄 카드 개수 (필요시 변경하세요)
window._myTripsDeleteMode = false;

function _renderMyTrips(trips = null, page = 1) {
    const te = document.getElementById('my-trips');
    if (!te) return;

    // 1. 처음 데이터를 받을 때는 전역 변수에 저장하고 1페이지로 세팅, 그 외엔 페이지 이동
    if (trips !== null) {
        window._myTripsData = trips;
        window._myTripsCurrentPage = 1;
    } else {
        window._myTripsCurrentPage = page;
    }

    const allTrips = window._myTripsData || [];
    const totalPages = Math.ceil(allTrips.length / TRIPS_PER_PAGE) || 1;
    const currentPage = window._myTripsCurrentPage;

    // 2. 현재 페이지에 해당하는 데이터만 잘라내기
    const startIndex = (currentPage - 1) * TRIPS_PER_PAGE;
    const paginatedTrips = allTrips.slice(startIndex, startIndex + TRIPS_PER_PAGE);

    // 🎯 타이틀 옆에 삭제버튼 배치 (초대받은 일정과 동일한 로직)
    let html = `
  <div class="my-sec-hd" style="display:flex; justify-content:space-between; align-items:center; margin-bottom:16px;">
    <h3 class="my-sec-ttl" style="margin:0;">내 여행 기록</h3>
    <div style="display:flex; gap:8px; margin-left:auto;">
      ${window._myTripsDeleteMode ? `
        <button onclick="execMyTripsBulkDelete()" style="padding:4px 10px; background:var(--coral); color:#fff; border:none; border-radius:6px; font-size:12px; font-weight:700; cursor:pointer;">선택 삭제 실행</button>
        <button onclick="toggleMyTripsDeleteMode(false)" style="padding:4px 10px; background:var(--cream); color:var(--text2); border:1px solid var(--border); border-radius:6px; font-size:12px; font-weight:600; cursor:pointer;">취소</button>
      ` : `
        <button onclick="toggleMyTripsDeleteMode(true)" style="padding:4px 10px; background:var(--cream); color:var(--text2); border:1px solid var(--border); border-radius:6px; font-size:12px; font-weight:600; cursor:pointer;">삭제하기</button>
      `}
    </div>
  </div>`;

    // 3. 재민님이 주신 오리지널 스타일(trip-card) 그대로 렌더링
    if (paginatedTrips.length > 0) {
        html += paginatedTrips.map(x => {
            // 1. 최종 수정 날짜 파싱 및 줄바꿈 처리
            let lastUpdate = '—';
            if (x.updatedAt) {
                let dateStr = x.updatedAt.replace ? x.updatedAt.replace(/-/g, '/').replace('T', ' ') : x.updatedAt;
                if (typeof dateStr === 'string' && dateStr.includes('.')) dateStr = dateStr.split('.')[0];
                const d = new Date(dateStr);
                if (!isNaN(d.getTime())) {
                    const year  = d.getFullYear(); const month = String(d.getMonth() + 1).padStart(2, '0'); const day = String(d.getDate()).padStart(2, '0');
                    const hours = String(d.getHours()).padStart(2, '0'); const mins = String(d.getMinutes()).padStart(2, '0');
                    lastUpdate = `${year}.${month}.${day}<br>${hours}:${mins}`;
                }
            }

            const displayStart = x.startDate ? x.startDate.replace(/-/g, '.') : '';
            const displayEnd   = x.endDate ? x.endDate.replace(/-/g, '.') : '';
            const displayDest  = x.destination || '';

            // 🎯 삭제 모드일 때 클릭하면 체크박스가 눌리도록 로직 분기
            const cardClickAction = window._myTripsDeleteMode
                ? `const chk = document.getElementById('chk-mytrip-${x.id || x.tripId}'); if(chk) chk.checked = !chk.checked;`
                : `openMyTrip(${x.id || x.tripId})`;

            return `
      <div class="trip-card" onclick="${cardClickAction}"
           style="cursor:pointer; position:relative; display:flex; align-items:center; gap:14px;
                  padding:14px 16px; border-radius:var(--r); border:2px solid var(--border);
                  background:var(--surface); margin-bottom:10px; transition:all .2s;">
        ${window._myTripsDeleteMode ? `
          <input type="checkbox" class="mytrip-del-chk" id="chk-mytrip-${x.id || x.tripId}" value="${x.id || x.tripId}" onclick="event.stopPropagation();" style="width:16px; height:16px; cursor:pointer; margin-left:4px;">
        ` : ''}
        <div style="width:42px;height:42px;border-radius:10px;background:var(--sage);
                    display:flex;align-items:center;justify-content:center;font-size:20px;flex-shrink:0;">🗺️</div>
        <div class="trip-info" style="flex:1;">
          <div class="trip-ttl" style="font-weight:700;font-size:14px;">${x.title || '여행 플랜'}</div>
          <div class="trip-meta" style="font-size:11px;color:var(--text3);margin-top:2px;">${displayStart} ~ ${displayEnd} · ${displayDest}</div>
        </div>
        <div class="trip-budget" style="color:var(--text3); font-size:11px; text-align:right; line-height:1.4; min-width:80px; flex-shrink:0;">
            <span style="display:block; font-size:10px; color:var(--text3); font-weight:700; margin-bottom:2px;">최종 수정</span>
            <span style="color:var(--text2); font-weight:500;">${lastUpdate}</span>
        </div>
      </div>`;
        }).join('');

        // 4. 리스트 하단에 통일된 페이지네이션 버튼 추가
        html += _renderMyPagePagerHtml(currentPage, totalPages, function (p) {
            return '_renderMyTrips(null, ' + p + ')';
        });

    } else {
        html += '<div style="color:var(--text3);font-size:13px;padding:20px 0;text-align:center">여행 기록이 없습니다.</div>';
    }

    te.innerHTML = html;
}

// 🎯 내 여행 기록 삭제 모드 토글
function toggleMyTripsDeleteMode(isDeleteMode) {
    window._myTripsDeleteMode = isDeleteMode;
    _renderMyTrips(null, window._myTripsCurrentPage);
}

// 🎯 체크박스 선택된 항목들 일괄 삭제 처리 (새로 만든 API 연동)
async function execMyTripsBulkDelete() {
    const chks = document.querySelectorAll('.mytrip-del-chk:checked');
    if (chks.length === 0) { toast('삭제할 일정을 선택해주세요.'); return; }
    if (!confirm(`선택한 ${chks.length}개의 내 여행 일정을 정말 삭제하시겠습니까?`)) return;

    let successCount = 0;
    for (const chk of chks) {
        const id = chk.value;
        const res = await api.del('/api/trips/' + id); // 🚀 새로 추가한 백엔드 API 호출!
        if (res.success) successCount++;
    }

    if (successCount > 0) {
        toast(`✅ ${successCount}개의 일정이 삭제되었습니다.`);
        window._myTripsDeleteMode = false;
        updateMyPageUI(); // 마이페이지 전체 리로드
    } else {
        toast('⚠️ 삭제 처리에 실패했습니다.');
    }
}

// 2. 새로 추가할 함수 (_renderMyTrips 함수 바로 밑에 붙여넣어 주세요)
function openMyTrip(tripId) {
    // ✨ 클릭한 카드의 진짜 tripId로 브라우저 기억을 강제로 덮어씌웁니다.
    window._currentTripId = tripId;
    sessionStorage.setItem('plannerDraftId', tripId);

    // 🔁 내 여행 기록에서 진입 → 1·2·3 단계 폼/대화를 백엔드 기준으로 다시 채워야 함을 표시.
    window._planHydrateTripId = tripId;
    window._chatRestored      = false;
    sessionStorage.removeItem('plannerDraftStep');
    sessionStorage.removeItem('plannerDraftState');

    // 🎯 [핵심 버그 수정]: 다른 일정으로 바꿀 때 기존에 쌓여있던 교체 요청 대기열을 완전히 박멸(초기화)합니다.
    window._q = [];
    if (typeof _q !== 'undefined') _q = [];
    // 🎯 [지역 캐시 박멸]: 이전 플랜의 목적지가 남아 엉뚱한 지역 지도가 뜨는 것을 방지
    window._mapDestRegion = null;
    // 🎯 교체 요청 레이아웃 바 및 접혀있던 토글 안내판도 깨끗하게 초기 상태로 원상복구합니다.
    const rb = document.getElementById('recalcBar');
    if (rb) rb.style.display = 'none';
    const qBox = document.getElementById('queueBox');
    if (qBox) { qBox.classList.remove('has'); qBox.style.display = 'none'; }
    const qToggle = document.getElementById('queueToggle');
    if (qToggle) qToggle.style.display = 'none';

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
          <button class="btn-scrap" onclick="event.stopPropagation();openMyReviewEdit(${r.postId})">✏️ 수정</button>
          <button class="btn-scrap" style="color:var(--coral);border-color:var(--coral)" onclick="event.stopPropagation();deleteMyPost(${r.postId})">삭제</button>
        </div>
      </div>
    </div>
  `).join('');
}

/** 마이페이지 후기 수정 진입 — 상세 데이터 로드 후 편집 페이지로 이동 */
async function openMyReviewEdit(postId) {
    window._currentPostId  = postId;
    window._openedPostId   = postId;

    // 편집에 필요한 상세 데이터를 미리 불러와 _currentPostDetail 세팅
    try {
        const res = await api.get(`/api/posts/${postId}`);
        if (res && res.data) {
            window._currentPostDetail = res.data;
        }
    } catch (e) {
        toast('후기 정보를 불러오지 못했습니다.');
        return;
    }

    // page_place.html 의 goEditReview() 호출
    if (typeof goEditReview === 'function') {
        goEditReview();
    } else {
        go('edit-review');
    }
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
    if (cnt > 0) {
        b.style.display = 'inline-flex';
        b.style.alignItems = 'center';
        b.style.justifyContent = 'center';
        b.textContent = cnt > 99 ? '99+' : String(cnt);
    } else {
        b.style.display = 'none';
    }
}

async function openNotificationPopup() {
    if (!_loggedIn) { toast('로그인이 필요합니다'); return; }
    await _loadNotifications();
    renderNotifList();
    document.getElementById('notifOverlay').style.display = 'block';
    document.getElementById('notifPopup').style.display = 'block';
    // 팝업을 열 때 한 번만 전체 읽음 처리 (renderNotifList 안에서는 하지 않음)
    api.patch('/api/notifications/read-all', {}).then(() => {
        _userNotifs.forEach(n => n.isRead = true);
        updateNotifBadge();
    });
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

    function formatNotifContent(n) {
        const raw = n.content || '';
        // \n을 <br>로 변환하여 줄바꿈 보존
        // 사유 라벨(정지사유 / 신고사유 / 반려사유)은 볼드 처리
        return raw
            .replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;')
            .replace(/(정지사유|신고사유|반려사유|삭제사유)/g,'<strong>$1</strong>')
            .replace(/\n/g,'<br>');
    }

    function notifIcon(type) {
        if (type === 'ACCOUNT_SUSPENDED') return '🚫';
        if (type === 'REPORT_REJECTED')   return '↩️';
        if (type === 'POST_DELETED')      return '🗑️';
        if (type === 'ROLE_CHANGED')      return '🔑';
        return '📢';
    }

    list.innerHTML = _userNotifs.map((n) => `
    <div style="padding:12px;border-radius:9px;margin-bottom:6px;
                background:${n.isRead ? 'var(--cream)' : 'var(--sage-pale)'};
                border:1px solid ${n.isRead ? 'var(--border2)' : 'var(--sage-l)'}">
      <div style="display:flex;align-items:flex-start;gap:9px">
        <span style="font-size:18px;flex-shrink:0">${notifIcon(n.type)}</span>
        <div style="flex:1;min-width:0">
          <div style="font-size:12px;font-weight:700;margin-bottom:3px">${n.title || ''}</div>
          <div style="font-size:12px;color:var(--text2);line-height:1.7;word-break:keep-all;overflow-wrap:break-word">${formatNotifContent(n)}</div>
          <div style="font-size:10px;color:var(--text3);margin-top:4px">${n.createdAt ? n.createdAt.substring(0,10) : ''}</div>
        </div>
        <button onclick="deleteNotif(${n.id})" style="background:none;border:none;cursor:pointer;color:var(--text3);font-size:14px;flex-shrink:0">✕</button>
      </div>
    </div>`).join('');

}

/** 알림 개별 삭제 (DELETE /api/notifications/{notificationId}) */
async function deleteNotif(notifId) {
    await api.del('/api/notifications/' + notifId);  // delete → del, /read 제거
    _userNotifs = _userNotifs.filter(n => n.id !== notifId);
    renderNotifList();
    updateNotifBadge();
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
    // 🔁 복원된(기존 여행 기록에서 불러온) 대화가 이미 떠 있으면 새 세션으로 덮어쓰지 않는다.
    if (window._chatRestored) {
        if (typeof updateSummaryCard === 'function') updateSummaryCard();
        return;
    }

    if (typeof updateSummaryCard === 'function') updateSummaryCard();

    const textOf = function (id) {
        const el = document.getElementById(id);
        return (el && el.textContent ? el.textContent.trim() : '') || '—';
    };

    const safe = function (value) {
        if (typeof escapeHtmlBubble === 'function') return escapeHtmlBubble(value);
        return String(value == null ? '' : value)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    };

    const parseArrayText = function (value) {
        if (!value) return '—';
        if (Array.isArray(value)) return value.length ? value.join(', ') : '—';
        const str = String(value || '').trim();
        if (!str) return '—';
        try {
            const arr = JSON.parse(str);
            if (Array.isArray(arr)) return arr.length ? arr.join(', ') : '—';
        } catch (e) {}
        return str;
    };

    const formatCompanionText = function (value) {
        const map = { SOLO: '혼자', COUPLE: '커플', FAMILY: '가족', FRIENDS: '친구' };
        return map[value] || value || '—';
    };

    const formatSpecialText = function (source) {
        if (!source) return '—';
        const arr = [];
        if (Number(source.hasInfant) === 1) arr.push('유아 동반');
        if (Number(source.hasPet) === 1) arr.push('반려동물 동반');
        return arr.length ? arr.join(', ') : '—';
    };

    const formatExtraText = function (value) {
        let arr = [];
        try { arr = JSON.parse(value || '[]'); } catch (e) { arr = []; }
        if (!Array.isArray(arr) || !arr.length) return '—';
        return arr
            .filter(item => item && item.label && item.value)
            .map(item => item.label + ': ' + item.value)
            .join(', ') || '—';
    };

    const currentRows = [
        ['출발지', textOf('sum-dep')],
        ['여행지', textOf('sum-dest')],
        ['날짜', textOf('sum-date')],
        ['인원', textOf('sum-people')],
        ['동행자', textOf('sum-comp')],
        ['예산', textOf('sum-budget')],
        ['이동수단', textOf('sum-trans')],
        ['숙소형태', textOf('sum-acc')],
        ['숙소옵션', textOf('sum-accopts')],
        ['여행 스타일', textOf('sum-style')],
        ['식이 정보', textOf('sum-food')],
        ['일정 밀도', textOf('sum-density')],
        ['특수 조건', textOf('sum-special')],
        ['반려동물', textOf('sum-pet')]
    ];

    const currentHtml = currentRows
        .map(function (row) { return safe(row[0]) + ': <strong>' + safe(row[1]) + '</strong>'; })
        .join('<br>');

    const source = window._communityPlannerOriginalPreference || null;
    let sourceHtml = '';

    if (source && source._communitySource) {
        const sourceRows = [
            ['원본 플랜', source._communitySourceTitle || source.title || '커뮤니티 원본 플랜'],
            ['여행지', source.destination || '—'],
            ['동행자', formatCompanionText(source.companionType)],
            ['이동수단', source.transportType || '—'],
            ['숙소형태', source.accommodationType || '—'],
            ['숙소옵션', parseArrayText(source.accommodationOptions)],
            ['여행 스타일', parseArrayText(source.travelStyles)],
            ['식이 정보', parseArrayText(source.dietaryInfo)],
            ['일정 밀도', source.scheduleDensity || '—'],
            ['특수 조건', formatSpecialText(source)],
            ['원본 추가사항', formatExtraText(source.extraNotes)]
        ];

        sourceHtml = '<br><br><hr style="border:none;border-top:1px solid rgba(0,0,0,.08);margin:10px 0">'
            + '📌 <strong>커뮤니티 원본 플랜에서 반영된 값</strong><br>'
            + sourceRows
                .map(function (row) { return safe(row[0]) + ': <strong>' + safe(row[1]) + '</strong>'; })
                .join('<br>');
    }

    const msgs = document.getElementById('chatMsgs');
    if (!msgs) return;
    msgs.innerHTML = '';

    const tripId = window._currentTripId || null;
    const sesRes = await api.post('/api/chat/sessions', { planId: tripId });
    if (sesRes.success && sesRes.data) {
        _chatSessionId = sesRes.data.sessionId;
    }

    addBubble(
        '입력 정보를 정리해드릴게요 📋<br><br>'
        + '✅ <strong>현재 여행 계획에 반영된 값</strong><br>'
        + currentHtml
        + sourceHtml
        + '<br><br>위 정보를 바탕으로 최적의 여행 일정을 만들어드리겠습니다!',
        'bot',
        ['일정 생성하기', '추가 요청 있어요', '예산 조정할게요'],
        true
    );
}

function startChat() {
    const msgs = document.getElementById('chatMsgs');
    if (!msgs) return;
    msgs.innerHTML = '';
    addBubble('안녕하세요! AI 여행 플래너입니다 ✈<br>추가로 원하시는 내용이 있으시면 말씀해주세요!', 'bot', ['반려동물 없음','🐕 강아지','일정 생성'], true);
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
    // 음식·식당·일정 맥락이 있으면 해외 여행지가 아님 (예: "중국집", "2일차 점심", "일식당")
    const foodCtxKeywords = ['집','식당','음식','요리','라멘','쌀국수','카레','일식','양식','중식','한식','분식','맛집','먹','점심','저녁','아침','식사','메뉴'];
    const dayCtxKeywords  = ['일차','day','날','번째날','1일','2일','3일','첫날','마지막날'];
    // 실제 여행 목적 표현이 있을 때만 차단 (단순 음식 표현 "중국집"은 여행 의도 없음)
    const travelIntentKeywords = ['여행','가고싶','가자','갈게','갈거야','방문','출발','떠나고싶'];

    const _m = txt.replace(/\s/g, '');
    const _hasOverseas     = overseasKeywords.some(k => _m.includes(k));
    const _hasTravelIntent = travelIntentKeywords.some(k => _m.includes(k));
    const _hasFoodCtx      = foodCtxKeywords.some(k => _m.includes(k));
    const _hasDayCtx       = dayCtxKeywords.some(k => _m.includes(k));

    if (_hasOverseas && _hasTravelIntent && !_hasFoodCtx && !_hasDayCtx) {
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

function addBubble(txt, role, qrs, isHtml) {
    const msgs = document.getElementById('chatMsgs');
    const d = document.createElement('div');
    d.className = 'cmsg' + (role === 'user' ? ' user' : '');
    if (role === 'bot') {
        // isHtml=true 면 코드가 직접 만든 안전한 HTML이므로 그대로 사용,
        // 아니면 AI 응답이므로 마크다운 변환 + 이스케이프 처리
        const inner = isHtml ? txt : formatBotReply(txt);
        d.innerHTML = `<div class="cav bot">🤖</div><div><div class="cbubble bot">${inner}</div>`
            + (qrs ? '<div class="qr-row">' + qrs.map(q => `<button class="qr-btn" onclick="document.getElementById('chatInp').value='${q}';sendMsg()">${q}</button>`).join('') + '</div>' : '')
            + '</div>';
    } else {
        d.innerHTML = `<div class="cav user">나</div><div><div class="cbubble user">${escapeHtmlBubble(txt)}</div></div>`;
    }
    msgs.appendChild(d);
    msgs.scrollTop = msgs.scrollHeight;
}

/* 🎯 [신규] 챗봇 답변 포맷터 — XSS 방지 + 마크다운 → HTML 변환
 *  - 먼저 HTML 특수문자를 이스케이프해 스크립트 주입을 막고,
 *  - 그 다음 **굵게**, *기울임*, - 목록, 줄바꿈 등을 안전한 태그로 바꾼다. */
function escapeHtmlBubble(s) {
    return String(s == null ? '' : s)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}

function formatBotReply(raw) {
    let s = escapeHtmlBubble(raw);

    // 1) **굵게** → <strong>
    s = s.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');
    // 2) 남은 *기울임* → <em> (양쪽이 공백이 아닌 단일 별표 쌍만)
    s = s.replace(/(^|[^*])\*([^*\n]+)\*(?!\*)/g, '$1<em>$2</em>');

    // 3) 줄 단위로 끊어 목록(- , • , 숫자.)을 항목으로 변환
    const lines = s.split(/\r?\n/);
    let html = '';
    let inList = false;
    const closeList = () => { if (inList) { html += '</ul>'; inList = false; } };

    lines.forEach(line => {
        const t = line.trim();
        if (t === '') { closeList(); return; }

        // "- 항목", "• 항목", "* 항목", "1. 항목" 형태를 목록으로
        const m = t.match(/^(?:[-•*]|\d+\.)\s+(.*)$/);
        if (m) {
            if (!inList) { html += '<ul class="cb-list">'; inList = true; }
            html += '<li>' + m[1] + '</li>';
        } else {
            closeList();
            html += '<div class="cb-line">' + t + '</div>';
        }
    });
    closeList();

    // 4) 문장 안에 " - " 로 나열된 인라인 항목도 줄바꿈으로 분리 (AI가 한 줄에 몰아쓴 경우)
    html = html.replace(/\s+-\s+(?=[가-힣A-Za-z0-9])/g, '<br>· ');

    return html;
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
    // _commState가 있으면 API 재호출로 정렬
    if (typeof _commState !== 'undefined' && typeof loadCommunityPosts === 'function') {
        _commState.sortOrder   = val;
        _commState.currentPage = 0;
        loadCommunityPosts(0, true);
        toast('정렬: ' + val);
        return;
    }
    // fallback: DOM 정렬
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

/* ───────────────────────────────────────────────
 * 16-1. 관리자 큐레이션으로 플랜 시작 (메인페이지 "계획하기 →")
 * ─────────────────────────────────────────────── */
function startPlanFromCuration(curationId) {
    if (!_loggedIn) {
        toast('⚠️ 로그인이 필요합니다. 로그인 후 이용해주세요.');
        openModal('modal-auth');
        return;
    }
    const c = (window._curationData || []).find(x => String(x.curationId) === String(curationId));
    if (!c) { toast('큐레이션 정보를 불러올 수 없습니다.'); return; }

    window._pendingCurationPlan = c;
    resetPlannerForm();
    window._currentTripId = null;
    go('planner', false);
    goPlanStep(1);

    // 여행지 자동 세팅 (destination → dest-prov 셀렉트)
    setTimeout(() => {
        if (c.destination) {
            const destSel = document.getElementById('dest-prov');
            if (destSel) {
                // 셀렉트 옵션 중 destination과 일치하는 것 선택
                const opts = Array.from(destSel.options);
                const parts = (c.destination||'').split('|');
                const prov = parts[0]; const city = parts[1]||'';
                const match = opts.find(o => o.value === prov || o.text === prov);
                if (match) {
                    destSel.value = match.value;
                    // 도시 목록 먼저 채우고
                    if (typeof updateCityDest === 'function') updateCityDest(destSel);
                    // 채워진 직후 구/군 선택
                    if (city) {
                        const cityEl = document.getElementById('dest-city');
                        if (cityEl) {
                            // updateCityDest가 동기 함수이므로 바로 옵션 탐색
                            const cityOpts = Array.from(cityEl.options);
                            const cityMatch = cityOpts.find(o => o.value === city || o.text === city);
                            if (cityMatch) {
                                cityEl.value = cityMatch.value;
                            } else {
                                // 옵션이 아직 없으면 직접 추가 후 선택
                                const opt = document.createElement('option');
                                opt.value = city; opt.text = city;
                                cityEl.appendChild(opt);
                                cityEl.value = city;
                            }
                        }
                    }
                } else {
                    window._pendingDestText = prov;
                }
            }
        }
        // preferences + 추천 정보 적용
        _applyCurationPreferences(c);
    }, 150);

    toast((c.title || '') + ' 큐레이션으로 플랜을 시작합니다 ✈');
}

/** 큐레이션의 칩 선택값 + 추천 숙소/맛집을 플래너 화면에 적용 (사용자가 그 후 자유롭게 변경 가능) */
function _applyCurationPreferences(c) {
    let extra = {};
    try { extra = JSON.parse(c.extraNotes || c.extra_notes || '{}'); } catch (e) { extra = {}; }
    const pref = extra.preferences || {};

    const setChip = (sel, val, multi) => {
        if (!val || (Array.isArray(val) && !val.length)) return;
        const vals = multi ? (Array.isArray(val) ? val : [val]) : [val];
        document.querySelectorAll(sel + ' .chip').forEach(chip => {
            chip.classList.toggle('on', vals.includes(chip.textContent.trim()));
        });
    };

    setChip('#chip-trans',   pref.transport,     false);
    setChip('#chip-acc',     pref.accommodation, false);
    setChip('#chip-comp',    pref.companion,     false);
    setChip('#chip-style',   pref.style,         true);
    setChip('#chip-diet',    pref.diet,          true);
    setChip('#chip-special', pref.special,       true);
    setChip('#chip-density', pref.density,       false);
    setChip('#chip-accopts', pref.accOptions,    true);

    // extra_notes의 타입별 분류 필드에서 추천 정보 구성
    const recoRows = [];
    const addReco = (label, arr) => (arr||[]).forEach(name => recoRows.push({label, value: name}));
    addReco('관리자 추천 숙소',  extra.adminRecommendedAccommodations);
    addReco('관리자 추천 맛집',  extra.adminRecommendedRestaurants);
    addReco('관리자 추천 관광지', extra.adminRecommendedAttractions);
    addReco('관리자 추천 카페',  extra.adminRecommendedCafes);
    addReco('관리자 추천 문화',  extra.adminRecommendedCultures);
    // 위 필드가 없으면 days에서 fallback
    if (!recoRows.length) {
        (extra.days || []).forEach(day => (day.places || []).forEach(p => {
            if (p.type === '🏨 숙소')        recoRows.push({ label: '관리자 추천 숙소', value: p.name });
            else if (p.type === '🍽️ 맛집')   recoRows.push({ label: '관리자 추천 맛집', value: p.name });
            else if (p.type === '📍 관광지')  recoRows.push({ label: '관리자 추천 관광지', value: p.name });
            else if (p.type === '☕ 카페')    recoRows.push({ label: '관리자 추천 카페', value: p.name });
            else if (p.type === '🎭 문화')    recoRows.push({ label: '관리자 추천 문화', value: p.name });
        }));
    }

    if (recoRows.length) {
        window._curationRecommendations = recoRows;
        const extraContainer = document.getElementById('sum-extra-rows');
        if (extraContainer) {
            recoRows.forEach(item => {
                const row = document.createElement('div');
                row.className = 'asc-row';
                row.innerHTML = `<div class="asc-label">${item.label}</div><div class="asc-val">${item.value}</div><button class="asc-del" onclick="event.stopPropagation();clearExtraRow('${item.label.replace(/'/g,"\\'")}')">✕</button>`;
                extraContainer.appendChild(row);
            });
            const emptyMsg = document.getElementById('extra-empty-msg');
            if (emptyMsg) emptyMsg.style.display = 'none';
        }
    }

    toast('큐레이션 추천 정보가 반영됐어요. 원하는 항목은 자유롭게 바꿔주세요.');
}

function _validatePlanStep1() {
    const depProv   = document.getElementById('dep-prov');
    const depCity   = document.getElementById('dep-city');
    const destProv  = document.getElementById('dest-prov');
    const destCity  = document.getElementById('dest-city');
    const dateStart = document.getElementById('s1-date-start');
    const dateEnd   = document.getElementById('s1-date-end');
    const pax       = document.getElementById('s1-pax');
    const budget    = document.getElementById('s1-budget');

    const isEmptySelect = function (el) {
        if (!el) return true;
        const value = String(el.value || '').trim();
        return !value || value === '전체' || value === '도/시 선택' || value === '시/군/구 선택';
    };

    if (isEmptySelect(depProv))  { toast('⚠️ 출발지 도/시를 선택해주세요.'); return false; }
    if (isEmptySelect(depCity))  { toast('⚠️ 출발지 시/군/구를 선택해주세요.'); return false; }
    if (isEmptySelect(destProv)) { toast('⚠️ 여행지 도/시를 선택해주세요.'); return false; }
    if (isEmptySelect(destCity)) { toast('⚠️ 여행지 시/군/구를 선택해주세요.'); return false; }

    if (!dateStart || !dateStart.value) { toast('⚠️ 출발일을 입력해주세요.'); return false; }
    if (!dateEnd   || !dateEnd.value)   { toast('⚠️ 귀환일을 입력해주세요.'); return false; }
    if (dateStart.value > dateEnd.value){ toast('⚠️ 귀환일은 출발일 이후여야 합니다.'); return false; }

    if (!pax    || !pax.value    || +pax.value < 1) { toast('⚠️ 인원을 1명 이상 입력해주세요.'); return false; }
    if (!budget || !budget.value || +String(budget.value).replace(/,/g, '') < 1) { toast('⚠️ 총 예산을 입력해주세요.'); return false; }

    const transChips = document.querySelectorAll('#chip-trans .chip.on');
    if (transChips.length === 0) { toast('⚠️ 이동 수단을 선택해주세요.'); return false; }
    if ([...transChips].some(c => c.textContent.includes('기타'))) {
        if (!document.getElementById('other-trans')?.value.trim()) {
            toast('⚠️ 이동 수단(기타)을 입력해주세요.'); return false;
        }
    }

    const accChip = document.querySelector('#chip-acc .chip.on');
    if (!accChip) { toast('⚠️ 숙소 형태를 선택해주세요.'); return false; }
    if (accChip.textContent.includes('기타')) {
        if (!document.getElementById('other-acc')?.value.trim()) {
            toast('⚠️ 숙소 형태(기타)를 입력해주세요.'); return false;
        }
    }

    const compChip = document.querySelector('#chip-comp .chip.on');
    if (!compChip) { toast('⚠️ 동행자 유형을 선택해주세요.'); return false; }
    if (compChip.textContent.includes('기타')) {
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
    if (window._restoringPlan) return;   // 복원 중 자동 발생한 change/input 은 dirty 로 치지 않음
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

/* ───────────────────────────────────────────────
 * 내 여행 기록 → 플래너 1·2·3 단계 복원
 * ─────────────────────────────────────────────── */
async function restorePlannerFromTrip(tripId) {
    if (!tripId) return;
    try {
        const [tripRes, formRes] = await Promise.all([
            api.get(`/api/trips/${tripId}`),
            api.get(`/api/trips/${tripId}/input-form`)
        ]);

        const t = (tripRes && tripRes.success) ? (tripRes.data || {}) : {};
        const f = (formRes && formRes.success) ? (formRes.data || {}) : {};

        // GET /api/trips/{tripId} (PlanDetailResponseDto) 는 budget·travelStyles·dietaryInfo·
        // transportType·accommodationType·companionType 등 취향 전체 필드를 이미 포함한다.
        // input-form(getInputFormMap)은 일부 필드만 주므로, 트립 상세(t)를 기준으로 하고
        // 비어 있는 값만 input-form(f)으로 보충한다.
        const merged = {
            destination:         t.destination        ?? f.destination,
            startDate:           t.startDate           ?? f.startDate,
            endDate:             t.endDate             ?? f.endDate,
            departure:           t.departure           ?? f.departure,
            transportType:       t.transportType       ?? f.transportType,
            accommodationType:   t.accommodationType   ?? f.accommodationType,
            accommodationOptions:t.accommodationOptions?? f.accommodationOptions,
            companionType:       t.companionType       ?? f.companionType,
            companionCount:      (t.companionCount != null ? t.companionCount : f.companionCount),
            travelStyles:        t.travelStyles        ?? f.travelStyles,
            dietaryInfo:         t.dietaryInfo         ?? f.dietaryInfo,
            scheduleDensity:     t.scheduleDensity     ?? f.scheduleDensity,
            hasInfant:           (t.hasInfant != null ? t.hasInfant : f.hasInfant),
            hasPet:              (t.hasPet != null ? t.hasPet : f.hasPet),
            budget:              (t.budget != null ? t.budget : f.budget),
            extraNotes:          t.extraNotes          ?? f.extraNotes
        };

        if (typeof _applyPlanPrefToForm === 'function') {
            window._restoringPlan = true;
            _applyPlanPrefToForm(merged, true);
            // 복원 중 발생하는 지연 change/input(도시 select, summary) 까지 끝난 뒤 플래그 해제
            setTimeout(() => { window._restoringPlan = false; }, 300);
        }
        setTimeout(() => { if (typeof updateSummaryCard === 'function') updateSummaryCard(); }, 80);

        await _restoreChatForTrip(tripId);

        window._planHydrateTripId = null;
        window._planLoadedTripId  = tripId;
        // 복원 직후엔 사용자가 바꾼 게 없으므로 dirty 아님 → 4번(경로 생성) 접근 가능
        window._planDirty = false;
        if (typeof _syncStep4State === 'function') _syncStep4State();
    } catch (e) {
        console.warn('[planner] 여행 기록 복원 실패', e);
    }
}

async function _restoreChatForTrip(tripId) {
    const msgs = document.getElementById('chatMsgs');
    if (!msgs) return;
    try {
        const listRes = await api.get('/api/chat/sessions');
        const sessions = (listRes && listRes.success && Array.isArray(listRes.data)) ? listRes.data : [];

        let target = sessions.find(s => String(s.planId) === String(tripId));
        if (!target && sessions.length) target = sessions[sessions.length - 1];
        if (!target) { window._chatRestored = false; return; }

        const sid = target.sessionId;
        const detail = await api.get(`/api/chat/sessions/${sid}`);
        const messages = (detail && detail.success && detail.data && Array.isArray(detail.data.messages))
            ? detail.data.messages : [];

        if (!messages.length) { window._chatRestored = false; return; }

        _chatSessionId = sid;
        msgs.innerHTML = '';
        messages.forEach(m => {
            const role = (String(m.role).toUpperCase() === 'USER') ? 'user' : 'bot';
            let txt = String(m.content == null ? '' : m.content);
            txt = txt.replace(/\[UPDATE:[A-Za-z가-힣0-9]+:[^\]]*\]/g, '').trim();
            txt = txt.replace(/\[EXTRA:[^\]]+\]/g, '').trim();
            if (txt) addBubble(txt, role);
        });
        window._chatRestored = true;
    } catch (e) {
        console.warn('[planner] 대화 복원 실패', e);
        window._chatRestored = false;
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

    // 🔁 내 여행 기록에서 진입(openMyTrip) 했고 아직 폼을 안 채웠다면,
    //    백엔드에서 1·2·3 단계(기본/취향/대화)를 먼저 복원한 뒤 같은 단계로 다시 진입.
    if (n <= 3 && window._planHydrateTripId && String(window._planHydrateTripId) === String(window._currentTripId)) {
        const _tid = window._planHydrateTripId;
        window._planHydrateTripId = null;
        restorePlannerFromTrip(_tid).then(() => { goPlanStep(n); });
        return;
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
    history.pushState({ page: 'planner', step: n }, '', '/');
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
    // btn이 null인 경우(새로고침 복원, 직접 호출 등) 안전하게 처리
    const nav = document.querySelector('.admin-nav');
    if (nav) nav.querySelectorAll('.admin-link').forEach(b => b.classList.remove('on'));
    if (btn) btn.classList.add('on');
    else if (nav) {
        // btn이 없으면 sec 순서에 맞는 링크를 직접 찾아서 활성화
        const order = ['dashboard','users','reports','curation'];
        const idx = order.indexOf(sec);
        const links = nav.querySelectorAll('.admin-link');
        if (idx !== -1 && links[idx]) links[idx].classList.add('on');
    }
    if(sec==='users')    loadAdminUsers();
    if(sec==='reports')  loadAdminReports(document.getElementById('admin-report-status-filter')?.value||'PENDING');
    if(sec==='curation') { if(typeof loadAdminCurations==='function') loadAdminCurations(); }
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
var _reportAction = 'hide';
function openReportAction(type, id, post, reporter, reason) {
    _reportAction=type; const isDelete=(type==='hide');
    document.getElementById('reportActionTitle').textContent = isDelete?'🙈 숨김 처리':'↩️ 신고 반려 처리';
    document.getElementById('ra-id').textContent=id; document.getElementById('ra-post').textContent=post;
    document.getElementById('ra-reporter').textContent=reporter; document.getElementById('ra-reason').textContent=reason;
    document.getElementById('ra-reason-label').innerHTML=(isDelete?'숨김 사유':'반려 사유')+' <span style="color:var(--coral)">*</span>';
    const sel=document.getElementById('ra-reason-select');
    if(isDelete){sel.innerHTML='<option value="">사유 선택...</option><option>허위 정보 게시</option><option>스팸/광고성 콘텐츠</option><option>불법 정보 포함</option><option>욕설/혐오 표현</option><option>개인정보 침해</option><option value="other">직접 입력</option>'; document.getElementById('ra-notify-msg').value='귀하의 게시글이 운영 정책에 따라 숨김 처리되었습니다.'; document.getElementById('ra-confirm-btn').style.background='#757575'; document.getElementById('ra-confirm-btn').textContent='숨김 완료';}
    else        {sel.innerHTML='<option value="">사유 선택...</option><option>신고 증거 불충분</option><option>허용된 표현 범위 내</option><option>중복 신고</option><option>사실과 다른 신고</option><option value="other">직접 입력</option>'; document.getElementById('ra-notify-msg').value='귀하의 게시글에 대한 신고가 검토 후 반려되었습니다.'; document.getElementById('ra-confirm-btn').style.background='var(--sage)'; document.getElementById('ra-confirm-btn').textContent='반려 완료';}
    document.getElementById('ra-detail').value=''; document.getElementById('reportActionModal').classList.add('open');
}
function closeReportAction() { document.getElementById('reportActionModal').classList.remove('open'); }

function openSuspendModal(username, uid) {
    document.getElementById('su-username').textContent=username; document.getElementById('su-id').textContent=uid;
    document.getElementById('su-reason-select').value=''; document.getElementById('su-detail').value='';
    document.getElementById('su-notify-msg').value='귀하의 계정은 운영 정책 위반으로 인해 정지되었습니다.(커뮤니티, 댓글 기능 이용불가능)';
    document.getElementById('suspendModal').classList.add('open');
}
function closeSuspendModal() { document.getElementById('suspendModal').classList.remove('open'); }

async function confirmSuspend() {
    const r=document.getElementById('su-reason-select').value; if(!r){toast('정지 사유를 선택해주세요');return;}
    const uid=document.getElementById('su-id').textContent;
    const notifyMsg=document.getElementById('su-notify-msg')?.value||'';
    const res=await api.patch('/api/admin/users/'+uid+'/suspend', {reason:r, notifyMessage:notifyMsg});
    closeSuspendModal();
    toast(res.success?'계정 정지 처리 완료 · 알림 전송됨':'⚠️ 정지 처리에 실패했습니다.');
    if(res.success) loadAdminUsers();
}

/** DELETE /api/admin/reports/{reportId} or PATCH (반려) */
async function confirmReportAction() {
    const r=document.getElementById('ra-reason-select').value; if(!r){toast('사유를 선택해주세요');return;}
    const rid=document.getElementById('ra-id').textContent;
    const notifyMsg=document.getElementById('ra-notify-msg')?.value||'';
    let res;
    if(_reportAction==='hide') res=await api.patch('/api/admin/reports/'+rid+'/hide?reason='+encodeURIComponent(r),{});
    else                         res=await api.patch('/api/admin/reports/'+rid,{status:'REJECTED',reason:r,notifyMessage:notifyMsg});
    closeReportAction();
    toast(res.success
        ? (_reportAction==='hide'?'숨김 처리 완료 · 작성자 알림 전송됨':'신고 반려 완료 · 신고자 알림 전송됨')
        : '⚠️ 처리에 실패했습니다.');
    if(res.success) loadAdminReports(document.getElementById('admin-report-status-filter')?.value||'PENDING');
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
            loadAdminUsers(); // ★ 회원 목록만 새로고침
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
        const obscureToken = (BigInt(tripId) ^ BigInt("0x5A3C9B7D2E")).toString(16);
        linkEl.value = `${window.location.origin}/plan/view?token=${obscureToken}`;
    }

    modal.classList.add('open');
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
        let obscureToken;
        try {
            const bigTripId = BigInt(tripId);
            const mask = BigInt("0x5A3C9B7D2E");
            const obscure = bigTripId ^ mask;
            obscureToken = obscure.toString(16);
        } catch (e) {
            console.error("카카오 토큰 암호화 실패:", e);
            obscureToken = parseInt(tripId).toString(16);
        }

        // 🎯 [동적 주소 연동]: 현재 접속 환경에 맞춰 자동으로 링크를 생성
        const inviteUrl = `${window.location.origin}/plan/view?token=${obscureToken}`;

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
    const obscureToken = (BigInt(tripId) ^ BigInt("0x5A3C9B7D2E")).toString(16);
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

// 복사 헬퍼 함수 (HTTPS/HTTP 모두 대응)
function copyToClipboard(text) {
    if (navigator.clipboard && window.isSecureContext) {
        return navigator.clipboard.writeText(text);
    }
    return new Promise((resolve, reject) => {
        const el = document.createElement('textarea');
        el.value = text;
        el.style.position = 'fixed';
        el.style.opacity = '0';
        document.body.appendChild(el);
        el.focus();
        el.select();
        try {
            document.execCommand('copy') ? resolve() : reject();
        } catch (e) {
            reject(e);
        }
        document.body.removeChild(el);
    });
}

async function copyShareLink() {
    const tripId = window._currentTripId;
    if (!tripId) { toast('여행 플랜 정보가 올바르지 않습니다.'); return; }

    try {
        const response = await fetch(`/api/trips/${tripId}/share`, {
            method: 'POST',
            headers: {
                'Authorization': Token.getAccess() ? `Bearer ${Token.getAccess()}` : '',
                'Content-Type': 'application/json'
            }
        });
        const res = await response.json();

        if (res && res.success && res.data && res.data.shareLink) {
            const linkEl = document.getElementById('share-link-val');
            if (linkEl) linkEl.value = res.data.shareLink;

            await copyToClipboard(res.data.shareLink);
            toast('읽기 전용 링크가 클립보드에 복사되었습니다!');
        } else {
            throw new Error("API 반환 오류");
        }

    } catch (error) {
        console.error("공유 링크 생성 실패:", error);

        const fallbackObscure = (tripId ^ 0x5A3C9B7D2E).toString(16);
        const fallbackLink = `${window.location.origin}/plan/view?token=${fallbackObscure}`;

        const linkEl = document.getElementById('share-link-val');
        if (linkEl) linkEl.value = fallbackLink;

        await copyToClipboard(fallbackLink);
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
    window._chatRestored = false;
    window._planHydrateTripId = null;
    window._planLoadedTripId = null;
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
    history.pushState({ page: 'planner', step: savedStep }, '', '/');
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

// 장소 이름 목록을 카카오 지도 좌표로 변환해 미리보기 지도를 그린다.
// (실제 큐레이션은 extra_notes에 좌표가 없으므로 이름으로 검색해서 경로를 만든다)
function _geocodeAndDrawPreview(placeNames, region) {
    const container = document.getElementById('prevKakaoMap');
    if (!container) return;
    if (typeof kakao === 'undefined' || !placeNames || !placeNames.length) {
        container.innerHTML = '<div style="display:flex;align-items:center;justify-content:center;height:100%;color:#888;font-size:13px">지도를 표시할 수 없습니다.</div>';
        return;
    }
    kakao.maps.load(function() {
        if (!kakao.maps.services) {
            // services 라이브러리가 없으면 지도 생략
            container.innerHTML = '<div style="display:flex;align-items:center;justify-content:center;height:100%;color:#888;font-size:13px">지도를 표시할 수 없습니다.</div>';
            return;
        }
        const ps = new kakao.maps.services.Places();
        const results = new Array(placeNames.length).fill(null);
        let done = 0;

        function finalize() {
            done++;
            if (done < placeNames.length) return;
            const coords = results.filter(Boolean);
            if (coords.length < 1) {
                container.innerHTML = '<div style="display:flex;align-items:center;justify-content:center;height:100%;color:#888;font-size:13px">지도를 표시할 수 없습니다.</div>';
                return;
            }
            initPreviewMap(coords);
        }

        placeNames.forEach((name, idx) => {
            const query = region ? (region + ' ' + name) : name;
            ps.keywordSearch(query, function(data, status) {
                if (status === kakao.maps.services.Status.OK && data.length) {
                    results[idx] = { lat: parseFloat(data[0].y), lng: parseFloat(data[0].x) };
                    finalize();
                } else {
                    // region 없이 재시도
                    ps.keywordSearch(name, function(d2, s2) {
                        if (s2 === kakao.maps.services.Status.OK && d2.length) {
                            results[idx] = { lat: parseFloat(d2[0].y), lng: parseFloat(d2[0].x) };
                        }
                        finalize();
                    });
                }
            });
        });
    });
}

function openPreview(key) {
    const modal = document.getElementById('prevModal');
    if (!modal) { toast('미리보기를 불러올 수 없습니다.'); return; }
    const el = function(id){ return document.getElementById(id); };

    // 실제 큐레이션 데이터(서버에서 받아온 것) 우선 조회
    const real = (window._curationData || []).find(x => String(x.curationId) === String(key));

    if (real && !real.isMock) {
        // ── 실제 큐레이션: extra_notes를 파싱해 해당 지역 정보로 미리보기 구성 ──
        let en = {};
        try { en = JSON.parse(real.extra_notes || real.extraNotes || '{}'); } catch (e) {}

        const tags = (en.tags && en.tags.length) ? en.tags
            : (real.theme ? real.theme.split(',').map(t => t.trim()).filter(Boolean) : []);

        const days = Array.isArray(en.days) ? en.days : [];
        const allPlaces = days.reduce((acc, d) => acc.concat(Array.isArray(d.places) ? d.places : []), []);

        // 일정(N박 M일)
        const dayCount = days.length;
        const dur = dayCount > 1 ? `${dayCount - 1}박 ${dayCount}일` : (dayCount === 1 ? '당일치기' : '—');

        // 방문 장소 수
        const placeCount = allPlaces.length;

        // 예산 합산
        const totalBudget = allPlaces.reduce((sum, p) => sum + (Number(p.amount) || 0), 0);
        const budgetStr = totalBudget > 0 ? '₩' + totalBudget.toLocaleString() + '~' : '—';

        // 숙소 스냅샷
        const accs = en.adminRecommendedAccommodations || [];
        let stayStr = '—';
        if (accs.length === 1) stayStr = accs[0];
        else if (accs.length > 1) stayStr = `${accs[0]} 외 ${accs.length - 1}건`;

        // 맛집 리스트 (이름 기반, 평점은 데이터에 없으므로 표시 생략)
        const foods = en.adminRecommendedRestaurants || [];
        const foodHtml = foods.length
            ? foods.map(f => `<div class="prev-food-item"><div class="pfi-left"><span class="pfi-icon">🍽️</span>${_escSafe(f)}</div></div>`).join('')
            : '<div style="color:#888;font-size:13px;padding:4px 0">등록된 맛집 정보가 없습니다.</div>';

        if (el('prevTags'))     el('prevTags').innerHTML      = tags.map(t => `<span class="prev-tag">${_escSafe(t)}</span>`).join('');
        if (el('prevPlanTtl'))  el('prevPlanTtl').textContent = real.title || '';
        if (el('prevBudget'))   el('prevBudget').textContent  = budgetStr;
        if (el('prevPlaces'))   el('prevPlaces').textContent  = placeCount > 0 ? placeCount + '곳' : '—';
        if (el('prevDur'))      el('prevDur').textContent     = dur;
        if (el('prevStay'))     el('prevStay').textContent    = stayStr;
        if (el('prevFoodList')) el('prevFoodList').innerHTML  = foodHtml;

        modal.classList.add('open');

        // 지도: 일정 순서대로 장소 이름을 좌표로 변환해 경로 표시
        const routeNames = allPlaces.map(p => p.name).filter(Boolean);
        const region = real.destination || '';
        const mapBox = document.getElementById('prevKakaoMap');
        if (mapBox) mapBox.innerHTML = '';
        setTimeout(() => _geocodeAndDrawPreview(routeNames, region), 80);
        return;
    }

    // ── 폴백: mock 카드(또는 데이터 없음)는 기존 _md 사용 ──
    const d = _md[key] || _md.jeju;
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

// _esc(page_main.html에 정의)가 없을 때를 대비한 안전 이스케이프
function _escSafe(str) {
    if (typeof _esc === 'function') return _esc(str);
    return String(str == null ? '' : str)
        .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;').replace(/'/g, '&#39;');
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
    // 커뮤니티 복귀 시 탭이 비어있으면 게시글 재로드
    if (p === 'community') {
        setTimeout(function() {
            var tab = (typeof _commState !== 'undefined' && _commState.currentTab) ? _commState.currentTab : 'route';
            var tabEl = document.getElementById('tab-' + tab);
            if (!tabEl || !tabEl.querySelector('.comm-post-item')) {
                if (['stay','food','tour','cafe'].indexOf(tab) !== -1) {
                    if (typeof window._loadPlaceCards === 'function') window._loadPlaceCards(tab, 0, true);
                } else {
                    if (typeof window.loadCommunityPosts === 'function') window.loadCommunityPosts(0, true);
                }
            }
        }, 50);
    }

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

    // 🎯 [추가] 뒤로가기로 페이지 복구 시 상단바(네비게이션 버튼 등) UI도 최신화
    if (typeof updateNav === 'function') {
        updateNav();
    }
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
                        tryBtn.href = window.location.origin; // 🚀 클릭 시 이동할 타겟 메인 주소
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
        }else {
            // 🎯 [신규] 읽기 전용이 아닌 '수정 권한' 링크로 접근한 경우, 초대된 일정 뷰어임을 명시
            window._isInvitedEditView = true;
        }
    }

    // 기존 토큰으로 자동 로그인 복원
    const savedToken = Token.getAccess();
    if (savedToken) {
        const meRes = await api.get('/api/users/me');
        if (meRes.success && meRes.data) {
            _currentUser           = meRes.data;
            window._currentUser    = _currentUser;
            window._isAdmin        = (_currentUser.role === 'ADMIN');
            _isSuspended           = (_currentUser.role === 'SUSPENDED' || _currentUser.status === 'SUSPENDED');
            _loggedIn              = true;
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

        // 관리자 페이지로 복원된 경우, 저장된 하위 섹션을 자동으로 연다
        if (savedPage === 'admin') {
            const sec = sessionStorage.getItem('adminSection') || 'dashboard';
            sessionStorage.removeItem('adminSection');
            const order = ['dashboard', 'users', 'reports', 'curation'];
            // admin 페이지/네비가 렌더된 뒤(initAdminPage 완료 후) 해당 섹션 버튼을 눌러 진입
            let _tries = 0;
            (function openAdminSec() {
                const links = document.querySelectorAll('.admin-nav .admin-link');
                const idx = order.indexOf(sec);
                const ready = links.length > idx && idx !== -1;
                if (!ready && _tries < 40) { _tries++; setTimeout(openAdminSec, 60); return; }
                if (typeof window.showAdmin === 'function') {
                    const matched = (idx !== -1 && links[idx]) ? links[idx] : null;
                    window.showAdmin(sec, matched);
                }
            })();
        }
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
            && !el.id.includes('pager') && el.id !== 'my-invited-list') {
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
    if (key === 'scrap-route') { if (typeof loadMyRouteScrap === 'function') loadMyRouteScrap(); }
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