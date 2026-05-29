/* =============================================================================
 * TripLinker - 커뮤니티/관리자 로직 (app_community.js) — API 연동 버전
 *
 * 【변경사항 요약】
 * ① ACCOUNTS / NOTIF_DATA / MYPAGE_DATA / PLACE_REVIEWS 하드코딩 완전 제거
 * ② app_main.js 와 중복된 모든 함수 제거 (go, toast, tryLogin, doLogout,
 *    updateNav, updateMyPageUI, updateLedgerList, openNotificationPopup,
 *    showMapPlacePopup, checkUname, checkEmail, sendMsg 등)
 * ③ addPlanItem() 내부에 <select> 태그가 JS 코드 사이에 삽입되던 오류 수정
 *    → 문자열 템플릿 리터럴로 이동
 * ④ sortPosts() 탭 목록 5개로 통일 (route/stay/food/tour/cafe)
 * ⑤ 모든 데이터 조회·변경을 REST API 호출로 전환
 *
 * 【이 파일이 담당하는 도메인】
 *   Post   : PostController → PostService → PostRepository
 *            Entity: Post, PostImage, PostLike, PostScrap, PostComment
 *            DTO: PostWriteDto, PostDetailResponseDto
 *   Admin  : AdminController → AdminService → AdminRepository
 *            Entity: AdminLog, Curation
 *            DTO: AdminActionDto, CurationRequestDto
 *   System : SystemController → SystemService → SystemRepository
 *            Entity: Notification, Report
 *            DTO: ReportRequestDto, NotificationResponseDto
 *
 * 【API 매핑 (전체)】
 *  POST   /api/posts                              → submitReview()
 *  GET    /api/posts                              → loadCommunityPosts()
 *  GET    /api/posts/{postId}                     → openPostDetail()
 *  PATCH  /api/posts/{postId}                     → submitEditReview()
 *  DELETE /api/posts/{postId}                     → deleteMyPost()
 *  POST   /api/posts/{postId}/likes               → likePost()
 *  POST   /api/posts/{postId}/scraps              → scrapPost()
 *  POST   /api/posts/{postId}/reports             → _doReportPost()
 *  POST   /api/posts/{postId}/comments            → submitComment()
 *  GET    /api/posts/{postId}/comments            → loadComments()
 *  GET    /api/scraps                             → loadMyScrap()
 *  DELETE /api/scraps/{scrapId}                   → deleteScrap()
 *  GET    /api/admin/dashboard                    → loadAdminDashboard()
 *  GET    /api/admin/users                        → loadAdminUsers()
 *  PATCH  /api/admin/users/{userId}/suspend       → confirmSuspend()
 *  PATCH  /api/admin/users/{userId}/unsuspend     → unsuspendUser()
 *  GET    /api/admin/reports                      → loadAdminReports()
 *  DELETE /api/admin/reports/{reportId}           → confirmReportAction('delete')
 *  PATCH  /api/admin/reports/{reportId}           → confirmReportAction('reject')
 *  POST   /api/admin/curations                    → saveCuration()
 *  GET    /api/admin/curations                    → loadAdminCurations()
 *  PATCH  /api/admin/curations/{curationId}       → updateCuration()
 *  DELETE /api/admin/curations/{curationId}       → deleteCuration()
 *  GET    /api/admin/statistics                   → loadAdminStatistics()
 *
 * 【의존 전역 변수 (app_main.js 제공)】
 *   api, toast, go, _loggedIn, _currentUser, _isSuspended,
 *   _budgetSelectedTripId, _activeTags, closeWrite, openWriteModal
 * ============================================================================= */

/* ═══════════════════════════════════════════════════════════════════
 * §1. 커뮤니티 상태
 * ═══════════════════════════════════════════════════════════════════ */
const _commState = {
  currentTab:   'route',
  currentPage:   0,
  pageSize:      10,
  totalPages:    0,
  sortOrder:    'scrap',   // 'likes' | 'scrap' | 'latest'
  isLoading:    false
};

let _openedPostId     = null;   // 현재 열려 있는 게시글 ID
let _reportPostId     = null;   // 신고 대상 게시글 ID
let _reportAction     = 'delete'; // 관리자 신고 처리 타입
let _editCurationId   = null;   // 수정 중인 큐레이션 ID

/* ═══════════════════════════════════════════════════════════════════
 * §2. 커뮤니티 탭 전환
 *     → setCommTab(btn, cat)
 *     기존 app_community.js 의 동일 함수를 오버라이드
 * ═══════════════════════════════════════════════════════════════════ */
function setCommTab(btn, cat) {
  document.querySelectorAll('#commTabs .comm-tab')
    .forEach(b => b.classList.remove('on'));
  btn.classList.add('on');

  ['route', 'stay', 'food', 'tour', 'cafe'].forEach(c => {
    const el = document.getElementById('tab-' + c);
    if (el) el.style.display = (c === cat) ? 'block' : 'none';
  });

  const s = document.getElementById('sortSelect');
  if (s) {
    if (cat === 'route') {
      s.innerHTML =
        '<option value="likes">좋아요순</option>' +
        '<option value="scrap" selected>스크랩순</option>' +
        '<option value="latest">최신순</option>';
    } else {
      s.innerHTML =
        '<option value="saved" selected>담긴 순</option>' +
        '<option value="scrap">스크랩순</option>' +
        '<option value="latest">최신순</option>';
    }
  }

  _commState.currentTab  = cat;
  _commState.currentPage = 0;
  loadCommunityPosts(0, true);
}

/* ═══════════════════════════════════════════════════════════════════
 * §3. 정렬 변경
 *     → sortPosts(val)  : sortSelect.onchange 에서 호출
 * ═══════════════════════════════════════════════════════════════════ */
function sortPosts(val) {
  _commState.sortOrder   = val;
  _commState.currentPage = 0;
  loadCommunityPosts(0, true);
  toast('정렬: ' + val);
}

/* ═══════════════════════════════════════════════════════════════════
 * §4. 게시글 목록 로드
 *     GET /api/posts?page=&size=&sort=&category=
 * ═══════════════════════════════════════════════════════════════════ */
async function loadCommunityPosts(page = 0, reset = true) {
  if (_commState.isLoading) return;
  _commState.isLoading = true;

  const qs = new URLSearchParams({
    page:     page,
    size:     _commState.pageSize,
    sort:     _commState.sortOrder,
    category: _commState.currentTab
  });

  const res = await api.get('/api/posts?' + qs.toString());
  _commState.isLoading = false;

  if (!res.success) return;

  const posts = Array.isArray(res.data)
    ? res.data
    : (res.data && res.data.content ? res.data.content : []);

  _commState.currentPage = page;
  if (res.data && res.data.totalPages !== undefined) {
    _commState.totalPages = res.data.totalPages;
  }

  _renderPostList(posts, reset);
  _renderPagination();
}

/* ─── 게시글 목록 렌더링 헬퍼 ─── */
function _renderPostList(posts, reset) {
  const tabEl = document.getElementById('tab-' + _commState.currentTab);
  if (!tabEl) return;
  if (reset) tabEl.innerHTML = '';

  if (!posts.length) {
    tabEl.innerHTML +=
      '<div style="padding:40px 20px;text-align:center;color:var(--text3);font-size:14px">' +
      '게시글이 없습니다.</div>';
    return;
  }

  posts.forEach(post => {
    const tags   = (post.styleTags || []).join(',');
    const date   = (post.createdAt || '').substring(0, 10).replace(/-/g, '') || '0';
    const label  = _catLabel(_commState.currentTab);
    const pid    = post.postId;

    const div = document.createElement('div');
    div.className = 'comm-post-item';
    div.setAttribute('data-tags',   tags);
    div.setAttribute('data-author', (post.author && post.author.username) || '');
    div.setAttribute('data-likes',  post.likeCount  || 0);
    div.setAttribute('data-scrap',  post.scrapCount || 0);
    div.setAttribute('data-date',   date);

    div.innerHTML =
      '<div class="post-card" onclick="openPostDetail(' + pid + ')">' +
        '<span class="post-cat cat-' + _commState.currentTab + '">' + label + '</span>' +
        '<div class="post-ttl" style="margin-top:5px">' + _esc(post.title) + '</div>' +
        '<div class="post-foot">' +
          '<div class="post-stats">' +
            '<span class="post-stat" id="like-cnt-' + pid + '">❤️ ' + (post.likeCount  || 0) + '</span>' +
            '<span class="post-stat">👁 '                          + (post.viewCount  || 0) + '</span>' +
            '<span class="post-stat" id="scrap-cnt-' + pid + '">🔖 ' + (post.scrapCount || 0) + '</span>' +
          '</div>' +
          '<div style="display:flex;gap:6px">' +
            '<button onclick="likePost(event,' + pid + ')"  class="btn-comm-sm">❤️ 좋아요</button>' +
            '<button onclick="scrapPost(event,' + pid + ')" class="btn-comm-sm">🔖 스크랩</button>' +
            '<button onclick="openReportPostModal(event,' + pid + ')" class="btn-comm-sm">🚨 신고</button>' +
          '</div>' +
        '</div>' +
      '</div>';

    tabEl.appendChild(div);
  });
}

/* ─── 페이지네이션 렌더링 ─── */
function _renderPagination() {
  const wrap = document.getElementById('commPagination');
  if (!wrap) return;
  wrap.innerHTML = '';
  if (_commState.totalPages <= 1) return;

  for (let i = 0; i < _commState.totalPages; i++) {
    const btn = document.createElement('button');
    btn.textContent = i + 1;
    btn.className   = 'page-btn' + (i === _commState.currentPage ? ' on' : '');
    btn.onclick     = () => loadCommunityPosts(i, true);
    wrap.appendChild(btn);
  }
}

/* ─── 유틸 ─── */
function _catLabel(tab) {
  return { route: '여행 경로', stay: '숙소', food: '맛집', tour: '관광지', cafe: '카페' }[tab] || tab;
}
function _esc(str) {
  return (str || '').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;');
}

/* ═══════════════════════════════════════════════════════════════════
 * §5. 검색
 *     GET /api/posts?keyword=&searchType=&category=
 * ═══════════════════════════════════════════════════════════════════ */
async function doSearch() {
  const typeEl = document.getElementById('searchType');
  const inpEl  = document.getElementById('searchInp');
  const type   = typeEl ? typeEl.value : 'title';
  const q      = inpEl  ? inpEl.value.trim() : '';

  if (!q) { toast('검색어를 입력해주세요'); return; }

  const qs = new URLSearchParams({
    keyword:    q,
    searchType: type,
    category:   _commState.currentTab,
    page:       0,
    size:       _commState.pageSize
  });

  const res = await api.get('/api/posts?' + qs.toString());
  if (!res.success) { toast('검색 결과를 불러오지 못했습니다.'); return; }

  const posts = Array.isArray(res.data) ? res.data : (res.data && res.data.content ? res.data.content : []);
  _renderPostList(posts, true);
  toast('"' + q + '" 검색 결과: ' + posts.length + '건');
}

/* ═══════════════════════════════════════════════════════════════════
 * §6. 태그 필터 (클라이언트 측)
 * ═══════════════════════════════════════════════════════════════════ */
function filterByTag(tag, btn) {
  if (_activeTags.has(tag)) {
    _activeTags.delete(tag);
    if (btn) btn.classList.remove('active-tag');
  } else {
    _activeTags.add(tag);
    if (btn) btn.classList.add('active-tag');
  }
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

/* ═══════════════════════════════════════════════════════════════════
 * §7. 게시글 상세
 *     GET /api/posts/{postId}
 * ═══════════════════════════════════════════════════════════════════ */
async function openPostDetail(postId) {
  const res = await api.get('/api/posts/' + postId);
  if (!res.success || !res.data) { toast('게시글을 불러올 수 없습니다.'); return; }

  _openedPostId = postId;
  go('review');

  const post = res.data;
  const ttl  = document.getElementById('reviewTitle');
  const body = document.getElementById('reviewBody');
  const meta = document.getElementById('reviewMeta');

  if (ttl)  ttl.textContent = post.title   || '';
  if (body) body.innerHTML  = post.content || '';
  if (meta) meta.textContent =
    (post.author ? post.author.name : '') +
    ' · ' + (post.createdAt ? post.createdAt.substring(0, 10) : '');

  await loadComments(postId);
}

/* ═══════════════════════════════════════════════════════════════════
 * §8. 좋아요
 *     POST /api/posts/{postId}/likes
 * ═══════════════════════════════════════════════════════════════════ */
async function likePost(e, postId) {
  e.stopPropagation();
  if (!_loggedIn)   { toast('로그인이 필요합니다'); return; }
  if (_isSuspended) { toast('⛔ 해당 계정은 커뮤니티 기능이 제한되었습니다.'); return; }

  const res = await api.post('/api/posts/' + postId + '/likes', {});
  if (res.success) {
    const el = document.getElementById('like-cnt-' + postId);
    if (el) {
      const n = parseInt(el.textContent.replace(/\D/g, '')) || 0;
      el.textContent = '❤️ ' + (n + 1);
    }
    toast('❤️ 좋아요!');
  } else {
    toast(res.message || '⚠️ 좋아요 처리에 실패했습니다.');
  }
}

/* ═══════════════════════════════════════════════════════════════════
 * §9. 스크랩
 *     POST /api/posts/{postId}/scraps
 * ═══════════════════════════════════════════════════════════════════ */
async function scrapPost(e, postId) {
  e.stopPropagation();
  if (!_loggedIn)   { toast('로그인이 필요합니다'); return; }
  if (_isSuspended) { toast('⛔ 해당 계정은 커뮤니티 기능이 제한되었습니다.'); return; }

  const res = await api.post('/api/posts/' + postId + '/scraps', {});
  if (res.success) {
    const el = document.getElementById('scrap-cnt-' + postId);
    if (el) {
      const n = parseInt(el.textContent.replace(/\D/g, '')) || 0;
      el.textContent = '🔖 ' + (n + 1);
    }
    toast('🔖 스크랩 완료!');
  } else {
    toast(res.message || '⚠️ 스크랩 처리에 실패했습니다.');
  }
}

/* ═══════════════════════════════════════════════════════════════════
 * §10. 신고
 *      POST /api/posts/{postId}/reports
 * ═══════════════════════════════════════════════════════════════════ */
function openReportPostModal(e, postId) {
  e.stopPropagation();
  if (!_loggedIn) { toast('로그인이 필요합니다'); return; }
  _reportPostId = postId;
  const modal = document.getElementById('reportPostModal');
  if (modal) modal.classList.add('open');
}

async function submitReportPost() {
  const sel    = document.getElementById('reportReasonSelect');
  const reason = sel ? sel.value : '';
  if (!reason) { toast('신고 사유를 선택해주세요'); return; }

  const res = await api.post('/api/posts/' + _reportPostId + '/reports', { reason });
  const modal = document.getElementById('reportPostModal');
  if (modal) modal.classList.remove('open');
  toast(res.success ? '🚨 신고가 접수되었습니다.' : '⚠️ 신고 처리에 실패했습니다.');
  _reportPostId = null;
}

/* ═══════════════════════════════════════════════════════════════════
 * §11. 후기 작성 / 수정
 *      POST  /api/posts
 *      PATCH /api/posts/{postId}
 * ═══════════════════════════════════════════════════════════════════ */
async function submitReview() {
  if (_isSuspended) { toast('⛔ 해당 계정은 커뮤니티 기능이 제한되었습니다.'); return; }
  if (!_loggedIn)   { toast('로그인이 필요합니다'); return; }

  const titleEl   = document.getElementById('writeTitle');
  const editorEl  = document.getElementById('blogEditor');
  const tagsEl    = document.getElementById('writeTags');
  const publicEl  = document.getElementById('writePublic');

  const title   = titleEl  ? titleEl.value.trim() : '';
  const content = editorEl ? (editorEl.innerText  || editorEl.value || '').trim() : '';
  const tags    = tagsEl
    ? tagsEl.value.trim().split(/[\s,]+/).filter(Boolean)
    : [];
  const isPublic = publicEl ? (publicEl.checked ? 1 : 0) : 1;

  if (!title || !content) { toast('제목과 내용을 입력해주세요'); return; }

  const body = {
    title,
    content,
    styleTags: tags,
    isPublic,
    planId: (typeof _budgetSelectedTripId !== 'undefined' ? _budgetSelectedTripId : null)
  };

  const res = await api.post('/api/posts', body);

  if (res.success) {
    closeWrite();
    toast('후기가 등록되었습니다! 🎉');
    _commState.currentPage = 0;
    await loadCommunityPosts(0, true);
  } else {
    toast('⚠️ ' + (res.message || '게시글 등록에 실패했습니다.'));
  }
}

async function submitEditReview() {
  if (!_openedPostId) { toast('수정할 게시글이 없습니다.'); return; }

  const titleEl  = document.getElementById('editTitle');
  const editorEl = document.getElementById('editBlogEditor');
  const title    = titleEl  ? titleEl.value.trim() : '';
  const content  = editorEl ? (editorEl.innerText || editorEl.value || '').trim() : '';

  if (!title || !content) { toast('제목과 내용을 입력해주세요'); return; }

  const res = await api.patch('/api/posts/' + _openedPostId, { title, content });
  if (res.success) {
    toast('✅ 후기가 수정되었습니다.');
    go('review');
    openPostDetail(_openedPostId);
  } else {
    toast('⚠️ ' + (res.message || '수정에 실패했습니다.'));
  }
}

/* ─── 후기 삭제 ─── */
async function deleteMyPost(postId) {
  if (!confirm('게시글을 삭제하시겠습니까?')) return;
  const res = await api.del('/api/posts/' + (postId || _openedPostId));
  if (res.success) {
    toast('게시글이 삭제되었습니다.');
    go('community');
    _commState.currentPage = 0;
    await loadCommunityPosts(0, true);
  } else {
    toast('⚠️ ' + (res.message || '삭제에 실패했습니다.'));
  }
}

/* ═══════════════════════════════════════════════════════════════════
 * §12. 댓글
 *      GET  /api/posts/{postId}/comments
 *      POST /api/posts/{postId}/comments
 * ═══════════════════════════════════════════════════════════════════ */
async function loadComments(postId) {
  const list = document.getElementById('commentList');
  if (!list) return;
  list.innerHTML = '<div style="color:var(--text3);font-size:12px;padding:8px 0">댓글 불러오는 중...</div>';

  const res = await api.get('/api/posts/' + postId + '/comments');
  const comments = (res.success && res.data) ? res.data : [];

  if (!comments.length) {
    list.innerHTML =
      '<div style="color:var(--text3);font-size:13px;padding:10px 0;text-align:center">' +
      '첫 댓글을 남겨보세요!</div>';
    return;
  }

  list.innerHTML = comments.map(c =>
    '<div class="comment-item" style="padding:10px 0;border-bottom:1px solid var(--border2)">' +
      '<div style="display:flex;align-items:center;gap:8px;margin-bottom:4px">' +
        '<div style="width:22px;height:22px;border-radius:50%;background:var(--sage);' +
             'color:#fff;display:flex;align-items:center;justify-content:center;' +
             'font-size:10px;font-weight:700">' +
          _esc((c.authorName || '?')[0]) +
        '</div>' +
        '<span style="font-size:12px;font-weight:700">' + _esc(c.authorName || '익명') + '</span>' +
        '<span style="font-size:10px;color:var(--text3)">' +
          (c.createdAt ? c.createdAt.substring(0, 10) : '') +
        '</span>' +
      '</div>' +
      '<div style="font-size:13px;color:var(--text2);line-height:1.6">' + _esc(c.content || '') + '</div>' +
    '</div>'
  ).join('');
}

async function submitComment() {
  if (_isSuspended) { toast('⛔ 해당 계정은 커뮤니티 기능이 제한되었습니다.'); return; }
  if (!_loggedIn)   { toast('로그인이 필요합니다'); return; }

  const inp     = document.getElementById('commentInput');
  const content = inp ? inp.value.trim() : '';
  if (!content)  { toast('댓글 내용을 입력해주세요'); return; }
  if (!_openedPostId) { toast('게시글 정보가 없습니다.'); return; }

  const res = await api.post('/api/posts/' + _openedPostId + '/comments', { content });
  if (res.success) {
    if (inp) inp.value = '';
    toast('댓글이 등록되었습니다!');
    await loadComments(_openedPostId);
  } else {
    toast('⚠️ ' + (res.message || '댓글 등록에 실패했습니다.'));
  }
}

/* ═══════════════════════════════════════════════════════════════════
 * §13. 스크랩 목록 (마이페이지)
 *      GET    /api/scraps
 *      DELETE /api/scraps/{scrapId}
 * ═══════════════════════════════════════════════════════════════════ */
async function loadMyScrap(category) {
  if (!_loggedIn) return;
  const res = await api.get('/api/scraps');
  if (!res.success || !res.data) return;

  const scraps = category
    ? res.data.filter(s => s.category === category)
    : res.data;

  const suffix = category ? category.toLowerCase() : 'all';
// ✅ 수정
  const targetId = (suffix === 'stay' || suffix === 'food')
      ? 'my-scrap-' + suffix
      : null;
  const el = targetId ? document.getElementById(targetId) : null;
  if (!el) return;

  el.innerHTML = scraps.length
    ? scraps.map(s =>
        '<div class="post-card" onclick="openPostDetail(' + s.postId + ')">' +
          '<span class="post-cat">' + _catLabel((s.category || 'route').toLowerCase()) + '</span>' +
          '<div class="post-ttl" style="margin-top:5px">' + _esc(s.postTitle || '스크랩한 게시글') + '</div>' +
          '<div class="post-foot">' +
            '<div class="post-stats">' +
              '<span class="post-stat">❤️ ' + (s.likeCount || 0) + '</span>' +
              '<span class="post-stat">👁 '  + (s.viewCount || 0) + '</span>' +
            '</div>' +
            '<button onclick="deleteScrap(event,' + s.scrapId + ')" ' +
                    'style="font-size:11px;background:none;border:1px solid var(--border2);' +
                           'border-radius:5px;padding:2px 7px;cursor:pointer;color:var(--coral)">' +
              '🗑️ 삭제' +
            '</button>' +
          '</div>' +
        '</div>'
      ).join('')
    : '<div style="color:var(--text3);font-size:13px;padding:20px 0;text-align:center">' +
      '스크랩한 게시글이 없습니다.</div>';
}

async function deleteScrap(e, scrapId) {
  e.stopPropagation();
  const res = await api.del('/api/scraps/' + scrapId);
  if (res.success) {
    toast('스크랩이 삭제되었습니다.');
    loadMyScrap('stay');
    loadMyScrap('food');
  } else             { toast('⚠️ 삭제에 실패했습니다.'); }
}

/* ═══════════════════════════════════════════════════════════════════
 * §14. 마이페이지 — 작성한 후기
 *      GET /api/posts?author=me
 * ═══════════════════════════════════════════════════════════════════ */
async function loadMyReviews() {
  const res = await api.get('/api/posts?author=me&size=20');
  const re  = document.getElementById('my-reviews');
  if (!re) return;

  const posts = (res.success && res.data)
    ? (Array.isArray(res.data) ? res.data : (res.data.content || []))
    : [];

  re.innerHTML = '<h3 class="my-sec-ttl">작성한 후기</h3>' + (
    posts.length
      ? posts.map(x =>
          '<div class="post-card" onclick="openPostDetail(' + x.postId + ')">' +
            '<span class="post-cat">' + _catLabel((x.styleTags || [])[0] || 'route') + '</span>' +
            '<div class="post-ttl" style="margin-top:5px">' + _esc(x.title || '') + '</div>' +
            '<div class="post-foot"><div class="post-stats">' +
              '<span class="post-stat">❤️ ' + (x.likeCount || 0) + '</span>' +
              '<span class="post-stat">👁 '  + (x.viewCount || 0) + '</span>' +
            '</div></div>' +
          '</div>'
        ).join('')
      : '<div style="color:var(--text3);font-size:13px;padding:20px 0;text-align:center">' +
        '작성한 후기가 없습니다.</div>'
  );
}

/* ═══════════════════════════════════════════════════════════════════
 * §15. 관리자 탭 전환 + 데이터 자동 로드
 *      (app_main.js 의 showAdmin 을 오버라이드)
 * ═══════════════════════════════════════════════════════════════════ */
function showAdmin(sec, btn) {
  ['dashboard', 'users', 'reports', 'curation'].forEach(s => {
    const e = document.getElementById('ad-' + s);
    if (e) e.style.display = 'none';
  });
  const t = document.getElementById('ad-' + sec);
  if (t) t.style.display = 'block';

  btn.closest('.admin-nav').querySelectorAll('.admin-link')
    .forEach(b => b.classList.remove('on'));
  btn.classList.add('on');

  switch (sec) {
    case 'dashboard': loadAdminDashboard(); break;
    case 'users':     loadAdminUsers();     break;
    case 'reports':   loadAdminReports();   break;
    case 'curation':  loadAdminCurations(); break;
  }
}

/* ═══════════════════════════════════════════════════════════════════
 * §16. 관리자 대시보드
 *      GET /api/admin/dashboard
 * ═══════════════════════════════════════════════════════════════════ */
async function loadAdminDashboard() {
  const res = await api.get('/api/admin/dashboard');
  if (!res.success || !res.data) return;
  const d = res.data;
  const set = (id, val) => {
    const el = document.getElementById(id);
    if (el) el.textContent = (val !== undefined && val !== null) ? val.toLocaleString() : '-';
  };
  set('stat-users',   d.totalUsers);
  set('stat-trips',   d.totalTrips);
  set('stat-posts',   d.totalPosts);
  set('stat-reports', d.pendingReports);
}

/* ─── 기간별 통계 ─── */
async function loadAdminStatistics() {
  const start = document.getElementById('stat-start') && document.getElementById('stat-start').value;
  const end   = document.getElementById('stat-end')   && document.getElementById('stat-end').value;
  if (!start || !end) { toast('조회 기간을 선택해주세요'); return; }

  const res = await api.get('/api/admin/statistics?startDate=' + start + '&endDate=' + end);
  if (!res.success || !res.data) { toast('통계 데이터를 불러오지 못했습니다.'); return; }
  const d = res.data;
  const set = (id, val) => { const el = document.getElementById(id); if (el) el.textContent = (val || 0).toLocaleString(); };
  set('stat-visit',    d.visitCount);
  set('stat-new-user', d.newUsers);
  set('stat-new-trip', d.newTrips);
}

/* ═══════════════════════════════════════════════════════════════════
 * §17. 관리자 회원 목록
 *      GET   /api/admin/users
 *      PATCH /api/admin/users/{userId}/suspend
 *      PATCH /api/admin/users/{userId}/unsuspend
 * ═══════════════════════════════════════════════════════════════════ */
async function loadAdminUsers(page = 0) {
  const res = await api.get('/api/admin/users?page=' + page + '&size=20');
  if (!res.success || !res.data) return;

  const users  = Array.isArray(res.data) ? res.data : (res.data.content || []);
  const tbody  = document.getElementById('adminUserTable');
  if (!tbody) return;

  tbody.innerHTML = users.map(u => {
    const isActive    = u.status === 'ACTIVE';
    const isSuspended = u.status === 'SUSPENDED';
    const badge       = isSuspended ? 'badge-warn' : isActive ? 'badge-ok' : 'badge-del';
    return '<tr>' +
      '<td>' + (u.userId   || '') + '</td>' +
      '<td>' + _esc(u.username || '') + '</td>' +
      '<td>' + _esc(u.name     || '') + '</td>' +
      '<td>' + _esc(u.email    || '') + '</td>' +
      '<td><span class="badge ' + badge + '">' + (u.status || '') + '</span></td>' +
      '<td>' + (u.createdAt ? u.createdAt.substring(0, 10) : '') + '</td>' +
      '<td>' +
        (isSuspended
          ? '<button onclick="unsuspendUser(' + u.userId + ')" class="btn-admin-sm btn-ok">해제</button>'
          : isActive
            ? '<button onclick="openSuspendModal(\'' + _esc(u.username) + '\',' + u.userId + ')" class="btn-admin-sm btn-warn">정지</button>'
            : '') +
      '</td>' +
    '</tr>';
  }).join('');
}

/* ─── 정지 ─── */
function openSuspendModal(username, uid) {
  const su = document.getElementById('su-username');
  const si = document.getElementById('su-id');
  const sr = document.getElementById('su-reason-select');
  const sd = document.getElementById('su-detail');
  const sn = document.getElementById('su-notify-msg');
  if (su) su.textContent = username;
  if (si) si.textContent = uid;
  if (sr) sr.value = '';
  if (sd) sd.value = '';
  if (sn) sn.value = '귀하의 계정은 운영 정책 위반으로 인해 정지되었습니다.';
  const modal = document.getElementById('suspendModal');
  if (modal) modal.classList.add('open');
}
function closeSuspendModal() {
  const modal = document.getElementById('suspendModal');
  if (modal) modal.classList.remove('open');
}
async function confirmSuspend() {
  const r   = document.getElementById('su-reason-select') && document.getElementById('su-reason-select').value;
  const uid = document.getElementById('su-id') && document.getElementById('su-id').textContent;
  if (!r) { toast('정지 사유를 선택해주세요'); return; }
  const msg = document.getElementById('su-notify-msg') && document.getElementById('su-notify-msg').value;
  const res = await api.patch('/api/admin/users/' + uid + '/suspend', { reason: r, notifyMessage: msg });
  closeSuspendModal();
  toast(res.success ? '계정 정지 처리 완료 · 알림 전송됨' : '⚠️ 정지 처리에 실패했습니다.');
  if (res.success) loadAdminUsers();
}

/* ─── 정지 해제 ─── */
async function unsuspendUser(userId) {
  const res = await api.patch('/api/admin/users/' + userId + '/unsuspend', {});
  toast(res.success ? '✅ 계정 정지가 해제되었습니다.' : '⚠️ 처리에 실패했습니다.');
  if (res.success) loadAdminUsers();
}

/* ═══════════════════════════════════════════════════════════════════
 * §18. 관리자 신고 목록 + 처리
 *      GET    /api/admin/reports?status=
 *      DELETE /api/admin/reports/{reportId}   (게시글 삭제)
 *      PATCH  /api/admin/reports/{reportId}   (반려)
 * ═══════════════════════════════════════════════════════════════════ */
async function loadAdminReports(status = 'PENDING', page = 0) {
  const res = await api.get('/api/admin/reports?status=' + status + '&page=' + page + '&size=20');
  if (!res.success || !res.data) return;

  const reports = Array.isArray(res.data) ? res.data : (res.data.content || []);
  const tbody   = document.getElementById('adminReportTable');
  if (!tbody) return;

  tbody.innerHTML = reports.map(r =>
    '<tr>' +
    '<td>' + (r.reportId || '') + '</td>' +
    '<td>' + _esc(r.postTitle  || String(r.postId || '')) + '</td>' +
    '<td>' + _esc(r.reporterName || '') + '</td>' +
    '<td>' + _esc(r.reason      || '') + '</td>' +
    '<td><span class="badge ' + (r.status === 'PENDING' ? 'badge-warn' : 'badge-ok') + '">' + (r.status || '') + '</span></td>' +
    '<td>' + (r.createdAt ? r.createdAt.substring(0, 10) : '') + '</td>' +
    '<td>' +
      '<button onclick="openReportAction(\'delete\',' + r.reportId +
        ',\'' + _esc(r.postTitle  || '').replace(/'/g,"\\'") + '\'' +
        ',\'' + _esc(r.reporterName || '').replace(/'/g,"\\'") + '\'' +
        ',\'' + _esc(r.reason      || '').replace(/'/g,"\\'") + '\')" class="btn-admin-sm btn-danger">삭제</button>' +
      '<button onclick="openReportAction(\'reject\',' + r.reportId +
        ',\'' + _esc(r.postTitle  || '').replace(/'/g,"\\'") + '\'' +
        ',\'' + _esc(r.reporterName || '').replace(/'/g,"\\'") + '\'' +
        ',\'' + _esc(r.reason      || '').replace(/'/g,"\\'") + '\')" class="btn-admin-sm">반려</button>' +
    '</td>' +
    '</tr>'
  ).join('');
}

/* ─── 신고 처리 모달 ─── */
function openReportAction(type, id, post, reporter, reason) {
  _reportAction = type;
  const isDelete = (type === 'delete');

  const el = (eid) => document.getElementById(eid);
  if (el('reportActionTitle')) el('reportActionTitle').textContent = isDelete ? '🗑️ 게시글 삭제 처리' : '↩️ 신고 반려 처리';
  if (el('ra-id'))       el('ra-id').textContent       = id;
  if (el('ra-post'))     el('ra-post').textContent     = post;
  if (el('ra-reporter')) el('ra-reporter').textContent = reporter;
  if (el('ra-reason'))   el('ra-reason').textContent   = reason;
  if (el('ra-reason-label')) el('ra-reason-label').innerHTML =
    (isDelete ? '삭제 사유' : '반려 사유') + ' <span style="color:var(--coral)">*</span>';

  const sel = el('ra-reason-select');
  if (sel) {
    if (isDelete) {
      sel.innerHTML =
        '<option value="">사유 선택...</option>' +
        '<option>허위 정보 게시</option>' +
        '<option>스팸/광고성 콘텐츠</option>' +
        '<option>불법 정보 포함</option>' +
        '<option>욕설/혐오 표현</option>' +
        '<option>개인정보 침해</option>' +
        '<option value="other">직접 입력</option>';
      if (el('ra-notify-msg')) el('ra-notify-msg').value = '귀하의 게시글이 운영 정책에 따라 삭제 처리되었습니다.';
      if (el('ra-confirm-btn')) { el('ra-confirm-btn').style.background = 'var(--coral)'; el('ra-confirm-btn').textContent = '삭제 완료'; }
    } else {
      sel.innerHTML =
        '<option value="">사유 선택...</option>' +
        '<option>신고 증거 불충분</option>' +
        '<option>허용된 표현 범위 내</option>' +
        '<option>중복 신고</option>' +
        '<option>사실과 다른 신고</option>' +
        '<option value="other">직접 입력</option>';
      if (el('ra-notify-msg')) el('ra-notify-msg').value = '귀하의 게시글에 대한 신고가 검토 후 반려되었습니다.';
      if (el('ra-confirm-btn')) { el('ra-confirm-btn').style.background = 'var(--sage)'; el('ra-confirm-btn').textContent = '반려 완료'; }
    }
  }
  if (el('ra-detail')) el('ra-detail').value = '';
  const modal = el('reportActionModal');
  if (modal) modal.classList.add('open');
}

function closeReportAction() {
  const modal = document.getElementById('reportActionModal');
  if (modal) modal.classList.remove('open');
}

async function confirmReportAction() {
  const r = document.getElementById('ra-reason-select') && document.getElementById('ra-reason-select').value;
  if (!r) { toast('사유를 선택해주세요'); return; }

  const rid = document.getElementById('ra-id') && document.getElementById('ra-id').textContent;
  let res;
  if (_reportAction === 'delete') {
    res = await api.del('/api/admin/reports/' + rid);
  } else {
    const detail = document.getElementById('ra-detail') && document.getElementById('ra-detail').value;
    res = await api.patch('/api/admin/reports/' + rid, { status: 'REJECTED', reason: r, adminNote: detail });
  }

  closeReportAction();
  toast(res.success
    ? (_reportAction === 'delete'
        ? '게시글 삭제 완료 · 작성자 알림 전송됨'
        : '신고 반려 완료 · 신고자 알림 전송됨')
    : '⚠️ 처리에 실패했습니다.');
  if (res.success) loadAdminReports();
}

/* ═══════════════════════════════════════════════════════════════════
 * §19. 큐레이션 관리
 *      GET    /api/admin/curations
 *      POST   /api/admin/curations
 *      PATCH  /api/admin/curations/{curationId}
 *      DELETE /api/admin/curations/{curationId}
 * ═══════════════════════════════════════════════════════════════════ */
async function loadAdminCurations(page = 0) {
  const res = await api.get('/api/admin/curations?page=' + page + '&size=20');
  if (!res.success || !res.data) return;

  const curations = Array.isArray(res.data) ? res.data : (res.data.content || []);
  const tbody     = document.getElementById('curationTable');
  if (!tbody) return;

  tbody.innerHTML = curations.map(c =>
    '<tr>' +
    '<td>' + (c.curationId    || '') + '</td>' +
    '<td>' + _esc(c.title     || '') + '</td>' +
    '<td>' + _esc(c.theme     || '') + '</td>' +
    '<td>' + (c.displayOrder  || '') + '</td>' +
    '<td>' + (c.startAt ? c.startAt.substring(0, 10) : '-') + ' ~ ' + (c.endAt ? c.endAt.substring(0, 10) : '-') + '</td>' +
    '<td><span class="badge ' + (c.isDefault ? 'badge-ok' : 'badge-info') + '">' + (c.isDefault ? '기본' : '시즌') + '</span></td>' +
    '<td>' +
      '<button onclick="openEditCuration(' + c.curationId + ')" class="btn-admin-sm">수정</button>' +
      '<button onclick="deleteCuration(' + c.curationId + ')"   class="btn-admin-sm btn-danger">삭제</button>' +
    '</td>' +
    '</tr>'
  ).join('');
}

/* ─── 큐레이션 등록/수정 폼 제출 ─── */
async function saveCuration() {
  const get = (id) => { const el = document.getElementById(id); return el ? el.value.trim() : ''; };
  const title   = get('cur-title');
  const theme   = get('cur-theme');
  const order   = get('cur-order');
  const startAt = get('cur-start') || null;
  const endAt   = get('cur-end')   || null;
  const planId  = get('cur-plan-id');

  if (!title) { toast('큐레이션 제목을 입력해주세요'); return; }

  const body = {
    title,
    theme,
    displayOrder: parseInt(order)  || 1,
    startAt,
    endAt,
    planId:    planId ? parseInt(planId) : null,
    isDefault: 0
  };

  let res;
  if (_editCurationId) {
    res = await api.patch('/api/admin/curations/' + _editCurationId, body);
  } else {
    res = await api.post('/api/admin/curations', body);
  }

  if (res.success) {
    toast(_editCurationId ? '✅ 큐레이션이 수정되었습니다.' : '✅ 큐레이션이 등록되었습니다.');
    _editCurationId = null;
    _clearCurationForm();
    loadAdminCurations();
  } else {
    toast('⚠️ ' + (res.message || '처리에 실패했습니다.'));
  }
}

/* ─── 수정 모드 진입: 해당 큐레이션 데이터를 폼에 채움 ─── */
async function openEditCuration(curationId) {
  _editCurationId = curationId;
  const res = await api.get('/api/admin/curations/' + curationId);
  if (!res.success || !res.data) { toast('큐레이션 데이터를 불러오지 못했습니다.'); return; }

  const c = res.data;
  const set = (id, val) => { const el = document.getElementById(id); if (el) el.value = val || ''; };
  set('cur-title',   c.title);
  set('cur-theme',   c.theme);
  set('cur-order',   c.displayOrder);
  set('cur-start',   c.startAt ? c.startAt.substring(0, 10) : '');
  set('cur-end',     c.endAt   ? c.endAt.substring(0, 10)   : '');
  set('cur-plan-id', c.planId);

  const btn = document.getElementById('cur-save-btn');
  if (btn) btn.textContent = '큐레이션 수정';
  toast('수정 모드: ' + (c.title || ''));
}

/* ─── 큐레이션 삭제 ─── */
async function deleteCuration(curationId) {
  if (!confirm('큐레이션을 삭제하시겠습니까?')) return;
  const res = await api.del('/api/admin/curations/' + curationId);
  if (res.success) { toast('✅ 큐레이션이 삭제되었습니다.'); loadAdminCurations(); }
  else             { toast('⚠️ 삭제에 실패했습니다.'); }
}

/* ─── 폼 초기화 ─── */
function _clearCurationForm() {
  ['cur-title','cur-theme','cur-order','cur-start','cur-end','cur-plan-id'].forEach(id => {
    const el = document.getElementById(id); if (el) el.value = '';
  });
  _editCurationId = null;
  const btn = document.getElementById('cur-save-btn');
  if (btn) btn.textContent = '큐레이션 등록';
}

/* ═══════════════════════════════════════════════════════════════════
 * §20. 관리자 큐레이션 Day 편집 헬퍼
 *      addPlanItem() — app_main.js 의 오류 수정본
 *      (기존: <select>가 JS 코드 사이에 삽입되어 있던 버그 제거)
 * ═══════════════════════════════════════════════════════════════════ */
let _curDayN = 2;

function removeDay(btn) {
  const block = btn.closest('.plan-day-block');
  if (document.querySelectorAll('#curDays .plan-day-block').length <= 1) {
    toast('최소 1개의 Day가 필요합니다'); return;
  }
  block.remove();
}

function addDay() {
  _curDayN++;
  const div = document.createElement('div');
  div.className   = 'plan-day-block';
  div.style.cssText = 'border:1px solid var(--sage-l)';
  div.innerHTML =
    '<div class="pdb-hd" style="border-bottom:1px solid var(--border2);padding-bottom:8px;margin-bottom:8px">' +
      '<span style="font-weight:800;color:var(--sage-d)">Day ' + _curDayN + '</span>' +
      '<div style="display:flex;gap:6px">' +
        '<button style="font-size:11px;background:var(--sage-pale);border:1px solid var(--sage-l);border-radius:5px;padding:3px 9px;cursor:pointer;color:var(--sage-d)" onclick="addPlanItem(this)">+ 장소 추가</button>' +
        '<button style="font-size:11px;background:#FEF3F2;border:1px solid #FECACA;border-radius:5px;padding:3px 7px;cursor:pointer;color:var(--coral)" onclick="removeDay(this)">✕</button>' +
      '</div>' +
    '</div>' +
    '<div style="font-size:11px;color:var(--text3);padding:6px;text-align:center">장소를 추가해주세요</div>';
  document.getElementById('curDays').appendChild(div);
}

function addPlanItem(btn) {
  const block = btn.closest('.plan-day-block');
  const ph    = block.querySelector('[style*="text-align:center"]');
  if (ph) ph.remove();

  const div = document.createElement('div');
  div.className   = 'pdb-item';
  div.style.cssText = 'flex-direction:column;align-items:flex-start;gap:8px;margin-top:6px';

  /* ✅ 수정: <select> 태그를 문자열 안에 올바르게 포함 */
  div.innerHTML =
    '<div style="display:flex;align-items:center;gap:8px;width:100%">' +
      '<span class="pdb-type-icon">📍</span>' +
      '<input style="flex:1;border:1px solid var(--border2);background:var(--surface);padding:5px 9px;border-radius:6px;font-size:12px;font-family:inherit;outline:none" placeholder="장소명">' +
      '<button class="btn-pdb-rm" onclick="this.closest(\'.pdb-item\').remove()" style="flex-shrink:0">✕</button>' +
    '</div>' +
    '<div style="display:grid;grid-template-columns:1fr 1fr 1fr;gap:6px;width:100%">' +
      '<select style="padding:5px 7px;border-radius:6px;border:1px solid var(--border2);font-size:11px;font-family:inherit">' +
        '<option value="관광지">📍 관광지</option>' +
        '<option value="숙소">🏨 숙소</option>' +
        '<option value="맛집">🍽️ 맛집</option>' +
        '<option value="카페">☕ 카페</option>' +
      '</select>' +
      '<input type="time" value="10:00" style="padding:5px 7px;border-radius:6px;border:1px solid var(--border2);font-size:11px;font-family:inherit">' +
      '<input type="number" placeholder="금액(원)" style="padding:5px 7px;border-radius:6px;border:1px solid var(--border2);font-size:11px;font-family:inherit">' +
    '</div>';

  block.appendChild(div);
}

/* ═══════════════════════════════════════════════════════════════════
 * §21. 커뮤니티 페이지 초기화
 *      (go('community') 또는 DOMContentLoaded 에서 호출)
 * ═══════════════════════════════════════════════════════════════════ */
async function initCommunityPage() {
  _commState.currentTab  = 'route';
  _commState.currentPage = 0;
  _commState.sortOrder   = 'scrap';
  _activeTags.clear();

  /* 탭 초기 상태 */
  const firstTab = document.querySelector('#commTabs .comm-tab');
  if (firstTab) {
    document.querySelectorAll('#commTabs .comm-tab').forEach(b => b.classList.remove('on'));
    firstTab.classList.add('on');
  }
  ['route','stay','food','tour','cafe'].forEach(c => {
    const el = document.getElementById('tab-' + c);
    if (el) el.style.display = (c === 'route') ? 'block' : 'none';
  });

  await loadCommunityPosts(0, true);
}

/* ═══════════════════════════════════════════════════════════════════
 * §22. DOMContentLoaded
 * ═══════════════════════════════════════════════════════════════════ */
document.addEventListener('DOMContentLoaded', () => {
  const commPage = document.getElementById('page-community');
  if (commPage && commPage.classList.contains('active')) {
    initCommunityPage();
  }
});