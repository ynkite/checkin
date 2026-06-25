/* =============================================================================
 * community v2 — 공통 유틸리티 (전역 1회 선언)
 * ============================================================================= */
(function () {
    'use strict';

    /* ── HTML 이스케이프 ─────────────────────────────────────────── */
    window._commUtil = window._commUtil || {};

    window._commUtil.escapeHtml = function escapeHtml(value) {
        return String(value ?? '')
            .replaceAll('&', '&amp;')
            .replaceAll('<', '&lt;')
            .replaceAll('>', '&gt;')
            .replaceAll('"', '&quot;')
            .replaceAll("'", '&#039;');
    };

    /* ── 날짜 포맷 (시간 포함: "2024-06-19 12:34") ────────────────── */
    window._commUtil.formatDate = function formatDate(value) {
        if (!value) return '';
        try {
            return String(value).replace('T', ' ').slice(0, 16);
        } catch (e) {
            return '';
        }
    };

    /*
     * ── 스타일 태그 파싱 ────────────────────────────────────────────
     * DB에 "#힐링,#맛집" 처럼 # 이 붙어 저장된 경우도 정규화.
     * 렌더링 측에서 항상 `#` + tag 형식으로 출력하므로 여기서 제거.
     */
    window._commUtil.parseStyleTags = function parseStyleTags(styleTags) {
        function cleanTag(v) {
            return String(v ?? '')
                .trim()
                .replace(/^#/, '')
                .replaceAll('[', '')
                .replaceAll(']', '')
                .replaceAll('"', '')
                .trim();
        }

        if (!styleTags) return [];
        if (Array.isArray(styleTags)) return styleTags.map(cleanTag).filter(Boolean);

        try {
            const parsed = JSON.parse(styleTags);
            if (Array.isArray(parsed)) return parsed.map(cleanTag).filter(Boolean);
        } catch (e) { /* JSON이 아니면 쉼표 문자열로 처리 */ }

        return String(styleTags).split(',').map(cleanTag).filter(Boolean);
    };

    /* ── routeData 파싱 ─────────────────────────────────────────── */
    window._commUtil.parseRouteData = function parseRouteData(value) {
        try {
            if (!value) return [];
            if (Array.isArray(value)) return value;
            if (typeof value === 'string') return JSON.parse(value);
            if (typeof value.data === 'string') return JSON.parse(value.data);
            if (Array.isArray(value.data)) return value.data;
        } catch (e) {
            console.warn('[community-v2] routeData parse fail:', e);
        }
        return [];
    };

    /* ── routeData 서버 조회 + 파싱 ────────────────────────────── */
    window._commUtil.getRouteData = async function getRouteData(post) {
        if (!post?.planId) return window._commUtil.parseRouteData(post?.planRouteJson);
        const res = await api.get(`/api/trips/${post.planId}/routes?t=${Date.now()}`);
        if (res && res.success !== false && res.data) {
            return window._commUtil.parseRouteData(res.data);
        }
        return window._commUtil.parseRouteData(post.planRouteJson);
    };

    /*
     * ── 실제 방문 장소 추출 (transit·이름 없는 항목 제외) ──────────
     * getAllPlaces / getActualPlaces 를 통합한 버전.
     * coord 필드도 함께 부착한다.
     */
    window._commUtil.getAllPlaces = function getAllPlaces(routeData) {
        function getCoord(place) {
            const lat = place.lat ?? place.latitude ?? place.placeLat ?? place.y;
            const lng = place.lng ?? place.longitude ?? place.placeLng ?? place.x;
            const nLat = Number(lat);
            const nLng = Number(lng);
            if (Number.isNaN(nLat) || Number.isNaN(nLng)) return null;
            return { lat: nLat, lng: nLng };
        }

        const places = [];
        routeData.forEach(day => {
            (day.places || []).forEach(p => {
                if (p.transit || !p.name) return;
                places.push({ ...p, day: day.day, coord: getCoord(p) });
            });
        });
        return places;
    };

    /* ── 현재 게시글 ID ─────────────────────────────────────────── */
    window._commUtil.getCurrentPostId = function getCurrentPostId() {
        return window._currentPostId
            || window._openedPostId
            || window._currentPostDetail?.postId
            || window._currentPostDetail?.id
            || null;
    };

    /* ── API 응답에서 배열 추출 ──────────────────────────────────── */
    window._commUtil.extractPosts = function extractPosts(res) {
        if (Array.isArray(res)) return res;
        if (Array.isArray(res?.data)) return res.data;
        if (Array.isArray(res?.data?.content)) return res.data.content;
        if (Array.isArray(res?.content)) return res.content;
        return [];
    };

    /* ── 5개 단위 슬라이딩 페이징 범위 계산 ───────────────────────
     * currentPage: 0부터 시작하는 현재 페이지 번호
     * totalPages : 전체 페이지 수
     * 반환값     : 화면에 보여줄 0-based 페이지 번호 배열
     * 예) 7번 페이지(0-based 6) 선택, totalPages 충분 → [4,5,6,7,8]
     */
    window._commUtil.getPagerWindow = function getPagerWindow(currentPage, totalPages, windowSize) {
        const total = Math.max(1, Number(totalPages) || 1);
        const size = Math.min(Number(windowSize) || 5, total);
        const current = Math.min(Math.max(Number(currentPage) || 0, 0), total - 1);

        let start = current - Math.floor(size / 2);
        if (start < 0) start = 0;
        if (start + size > total) start = Math.max(0, total - size);

        return Array.from({ length: size }, function (_, idx) { return start + idx; });
    };

    /* ── 로그인 확인 ──────────────────────────────────────────────  */
    window._commUtil.requireLogin = function requireLogin() {
        if (typeof Token === 'undefined' || !Token.getAccess || !Token.getAccess()) {
            if (typeof toast === 'function') toast('로그인이 필요합니다.');
            if (typeof go === 'function') go('login');
            return false;
        }
        return true;
    };

    /* ── 엑세스 토큰 취득 (마이페이지 등) ────────────────────────── */
    window._commUtil.getAccessToken = function getAccessToken() {
        if (typeof Token !== 'undefined' && Token.getAccess) return Token.getAccess();
        return localStorage.getItem('accessToken')
            || localStorage.getItem('access_token')
            || localStorage.getItem('token')
            || sessionStorage.getItem('accessToken')
            || sessionStorage.getItem('access_token')
            || sessionStorage.getItem('token');
    };

    /* ── fetch + JSON 헬퍼 ───────────────────────────────────────── */
    window._commUtil.authHeaders = function authHeaders(json) {
        const token = window._commUtil.getAccessToken();
        const headers = {};
        if (json) headers['Content-Type'] = 'application/json';
        if (token) headers['Authorization'] = 'Bearer ' + token;
        return headers;
    };

    window._commUtil.requestJson = async function requestJson(url, options) {
        const response = await fetch(url, options);
        let data = null;
        try { data = await response.json(); } catch (e) { data = null; }
        if (!response.ok) throw new Error(data?.message || '요청 처리에 실패했습니다.');
        return data;
    };

    /* ── 본문 이미지 삽입/저장/렌더링 유틸 ─────────────────────────
     * 새 이미지는 작성/수정 중에는 URL 문자열이 아니라 실제 이미지 미리보기로 보인다.
     * 이때 src는 브라우저 임시 미리보기용 data:image URL이고,
     * 등록/수정 버튼을 누르는 순간 /api/posts/images 로 업로드한 뒤 실제 서버 URL로 교체해서 저장한다.
     */
    window._commUtil.saveEditorSelection = function saveEditorSelection(editor, key) {
        if (!editor || !key) return;
        const sel = window.getSelection();
        if (!sel || !sel.rangeCount) return;

        const range = sel.getRangeAt(0);
        const node = range.commonAncestorContainer;
        if (node === editor || editor.contains(node)) {
            window[key] = range.cloneRange();
        }
    };

    window._commUtil.restoreEditorSelection = function restoreEditorSelection(editor, key) {
        if (!editor || !key || !window[key]) return false;

        const range = window[key].cloneRange();
        const node = range.commonAncestorContainer;
        if (!(node === editor || editor.contains(node))) return false;

        const sel = window.getSelection();
        if (!sel) return false;
        editor.focus();
        sel.removeAllRanges();
        sel.addRange(range);
        return true;
    };

    window._commUtil.bindEditorSelectionMemory = function bindEditorSelectionMemory(editor, key) {
        if (!editor || !key) return;

        if (editor.dataset.selectionMemoryBound !== key) {
            editor.dataset.selectionMemoryBound = key;

            ['keyup', 'mouseup', 'focus'].forEach(function (eventName) {
                editor.addEventListener(eventName, function () {
                    window._commUtil.saveEditorSelection(editor, key);
                    if (typeof window._commUtil.syncEditorTypingState === 'function') {
                        window._commUtil.syncEditorTypingState(editor, key);
                    }
                    if (typeof window._commUtil.updateEditorToolbarState === 'function') {
                        window._commUtil.updateEditorToolbarState(editor);
                    }
                });
            });

            editor.addEventListener('input', function () {
                window._commUtil.saveEditorSelection(editor, key);
                if (typeof window._commUtil.updateEditorToolbarState === 'function') {
                    window._commUtil.updateEditorToolbarState(editor);
                }
            });

            editor.addEventListener('blur', function () {
                if (typeof window._commUtil.updateEditorToolbarState === 'function') {
                    window._commUtil.updateEditorToolbarState(editor);
                }
            });
        }

        if (typeof window._commUtil.syncEditorTypingState === 'function') {
            window._commUtil.syncEditorTypingState(editor, key);
        }
        if (typeof window._commUtil.updateEditorToolbarState === 'function') {
            window._commUtil.updateEditorToolbarState(editor);
        }
    };

    /* ── contenteditable B/I/U 수동 토글 유틸 ─────────────────────
     * 버튼 색상은 커서가 위치한 기존 글자의 서식이 아니라 사용자가 버튼을 눌러 둔 상태를 기준으로 표시한다.
     * 따라서 굵은 글자 근처로 커서를 이동해도 B 버튼이 갑자기 켜지지 않는다.
     * 버튼을 누르면 즉시 #47B39F로 바뀌고, 다시 누르면 즉시 기본 색상으로 돌아온다.
     */
    window._commUtil.getEditorCommandState = function getEditorCommandState(editor) {
        if (!editor) return { bold: false, italic: false, underline: false };
        if (!editor._manualEditorCommandState) {
            editor._manualEditorCommandState = { bold: false, italic: false, underline: false };
        }
        return editor._manualEditorCommandState;
    };

    window._commUtil.resetEditorCommandState = function resetEditorCommandState(editor, selectionKey) {
        if (!editor) return;
        editor._manualEditorCommandState = { bold: false, italic: false, underline: false };
        if (selectionKey) window._commUtil.saveEditorSelection(editor, selectionKey);
        window._commUtil.syncEditorTypingState(editor, selectionKey);
        window._commUtil.updateEditorToolbarState(editor);
    };

    window._commUtil.setEditorCommandButtonState = function setEditorCommandButtonState(button, active) {
        if (!button) return;

        const activeColor = '#47B39F';

        button.dataset.active = active ? 'true' : 'false';
        button.style.background = active ? activeColor : 'var(--surface)';
        button.style.borderColor = active ? activeColor : 'var(--border)';
        button.style.color = active ? '#fff' : 'var(--text1)';
    };

    window._commUtil.updateEditorToolbarState = function updateEditorToolbarState(editor) {
        if (!editor || !editor.id) return;

        const state = window._commUtil.getEditorCommandState(editor);
        const buttons = document.querySelectorAll(
            '[data-editor-id="' + editor.id + '"][data-editor-command]'
        );

        buttons.forEach(function (button) {
            const command = button.dataset.editorCommand;
            window._commUtil.setEditorCommandButtonState(button, !!state[command]);
        });
    };

    window._commUtil.isSelectionInsideEditor = function isSelectionInsideEditor(editor) {
        const sel = window.getSelection();
        if (!editor || !sel || !sel.rangeCount) return false;
        const range = sel.getRangeAt(0);
        const node = range.commonAncestorContainer;
        return node === editor || editor.contains(node);
    };

    window._commUtil.syncEditorTypingState = function syncEditorTypingState(editor, selectionKey) {
        if (!editor) return;

        if (selectionKey) {
            window._commUtil.restoreEditorSelection(editor, selectionKey);
        }

        const sel = window.getSelection();
        if (!sel || !sel.rangeCount) return;
        const range = sel.getRangeAt(0);
        const node = range.commonAncestorContainer;
        if (!(node === editor || editor.contains(node))) return;

        // 선택 영역이 있는 상태에서는 기존 글자 서식을 강제로 바꾸지 않는다.
        // 커서만 있는 상태에서만 앞으로 입력될 글자의 서식 상태를 수동 토글 상태와 맞춘다.
        if (!range.collapsed) return;

        const state = window._commUtil.getEditorCommandState(editor);

        try { editor.focus({ preventScroll: true }); }
        catch (e) { editor.focus(); }

        try {
            document.execCommand('styleWithCSS', false, false);
            ['bold', 'italic', 'underline'].forEach(function (command) {
                let current = false;
                try { current = document.queryCommandState(command); } catch (e) { current = false; }
                const expected = !!state[command];
                if (current !== expected) {
                    document.execCommand(command, false, null);
                }
            });
        } catch (e) {
            console.warn('[community-v2] editor typing state sync failed:', e);
        }

        if (selectionKey) {
            window._commUtil.saveEditorSelection(editor, selectionKey);
        }
    };

    window._commUtil.applyEditorCommand = function applyEditorCommand(editor, command, selectionKey) {
        if (!editor || !command) return;

        if (selectionKey) {
            window._commUtil.restoreEditorSelection(editor, selectionKey);
        }

        const state = window._commUtil.getEditorCommandState(editor);
        const nextActive = !state[command];
        state[command] = nextActive;

        try { editor.focus({ preventScroll: true }); }
        catch (e) { editor.focus(); }

        const sel = window.getSelection();
        const hasSelection = sel && sel.rangeCount && window._commUtil.isSelectionInsideEditor(editor)
            && !sel.getRangeAt(0).collapsed;

        try {
            document.execCommand('styleWithCSS', false, false);

            if (hasSelection) {
                let current = false;
                try { current = document.queryCommandState(command); } catch (e) { current = false; }
                if (current !== nextActive) {
                    document.execCommand(command, false, null);
                }
            } else {
                window._commUtil.syncEditorTypingState(editor, selectionKey);
            }
        } catch (e) {
            console.warn('[community-v2] editor command failed:', command, e);
        }

        if (selectionKey) {
            window._commUtil.saveEditorSelection(editor, selectionKey);
        }

        window._commUtil.updateEditorToolbarState(editor);
        editor.dispatchEvent(new Event('input', { bubbles: true }));
    };

    /* ── 본문 이미지 삭제 지원 유틸 ───────────────────────────────
     * contenteditable 안의 이미지는 일반 텍스트처럼 Backspace 한 번으로 잘 지워지지 않을 수 있다.
     * 그래서 선택된 이미지, 커서 바로 앞/뒤 이미지, 범위 선택 안의 이미지를 직접 제거한다.
     * 제거된 새 이미지 파일은 selectedImages 배열에서도 함께 제거되어 저장 시 맨 아래에 다시 붙지 않는다.
     */
    window._commUtil.removeInlineImageRecord = function removeInlineImageRecord(editor, imageId) {
        if (!editor || !imageId) return;
        const arr = editor._inlineImageSelectedImages;
        if (!Array.isArray(arr)) return;

        for (let i = arr.length - 1; i >= 0; i--) {
            if (arr[i] && arr[i].id === imageId) arr.splice(i, 1);
        }
    };

    window._commUtil.cleanupDeletedInlineImageRecords = function cleanupDeletedInlineImageRecords(editor) {
        if (!editor || !Array.isArray(editor._inlineImageSelectedImages)) return;

        const aliveIds = new Set(
            Array.from(editor.querySelectorAll('img[data-inline-image-id]'))
                .map(img => img.dataset.inlineImageId)
                .filter(Boolean)
        );

        const arr = editor._inlineImageSelectedImages;
        for (let i = arr.length - 1; i >= 0; i--) {
            if (arr[i]?.id && !aliveIds.has(arr[i].id)) arr.splice(i, 1);
        }
    };

    window._commUtil.clearInlineImageSelection = function clearInlineImageSelection(editor) {
        if (!editor) return;
        editor.querySelectorAll('img[data-inline-selected="true"]').forEach(function (img) {
            img.removeAttribute('data-inline-selected');
            img.style.outline = '';
            img.style.boxShadow = '';
        });
    };

    window._commUtil.selectInlineImage = function selectInlineImage(editor, img) {
        if (!editor || !img) return;
        window._commUtil.clearInlineImageSelection(editor);
        img.dataset.inlineSelected = 'true';
        img.style.outline = '3px solid #47B39F';
        img.style.boxShadow = '0 0 0 4px rgba(71,179,159,.18)';
    };

    window._commUtil.placeCaretAt = function placeCaretAt(container, offset) {
        const range = document.createRange();
        const sel = window.getSelection();
        range.setStart(container, Math.max(0, offset));
        range.collapse(true);
        sel.removeAllRanges();
        sel.addRange(range);
    };

    window._commUtil.removeInlineImageElement = function removeInlineImageElement(editor, img, selectionKey) {
        if (!editor || !img || !editor.contains(img)) return false;

        const imageId = img.dataset.inlineImageId;
        const parent = img.parentNode;
        if (!parent) return false;

        const next = img.nextSibling;
        const prev = img.previousSibling;
        const offset = Array.prototype.indexOf.call(parent.childNodes, img);

        if (next && next.nodeName === 'BR') next.remove();
        else if (prev && prev.nodeName === 'BR') prev.remove();

        img.remove();
        window._commUtil.removeInlineImageRecord(editor, imageId);
        window._commUtil.cleanupDeletedInlineImageRecords(editor);

        try {
            window._commUtil.placeCaretAt(parent, Math.min(offset, parent.childNodes.length));
        } catch (e) {
            editor.focus();
        }

        if (selectionKey) window._commUtil.saveEditorSelection(editor, selectionKey);
        editor.dispatchEvent(new Event('input', { bubbles: true }));
        return true;
    };

    window._commUtil.findAdjacentImageForDelete = function findAdjacentImageForDelete(editor, range, keyName) {
        if (!editor || !range || !range.collapsed) return null;

        function findImgFrom(node, direction) {
            let cur = node;
            while (cur && cur !== editor) {
                if (cur.nodeType === Node.ELEMENT_NODE && cur.tagName === 'IMG') return cur;

                if (cur.nodeType === Node.ELEMENT_NODE && cur.tagName === 'BR') {
                    cur = direction === 'prev' ? cur.previousSibling : cur.nextSibling;
                    continue;
                }

                if (cur.nodeType === Node.TEXT_NODE && cur.textContent.trim() === '') {
                    cur = direction === 'prev' ? cur.previousSibling : cur.nextSibling;
                    continue;
                }

                if (cur.nodeType === Node.ELEMENT_NODE && cur.querySelector) {
                    const img = direction === 'prev'
                        ? Array.from(cur.querySelectorAll('img')).pop()
                        : cur.querySelector('img');
                    if (img) return img;
                }

                break;
            }
            return null;
        }

        const container = range.startContainer;
        const offset = range.startOffset;

        if (container.nodeType === Node.TEXT_NODE) {
            if (keyName === 'Backspace' && offset === 0) return findImgFrom(container.previousSibling || container.parentNode?.previousSibling, 'prev');
            if (keyName === 'Delete' && offset === container.textContent.length) return findImgFrom(container.nextSibling || container.parentNode?.nextSibling, 'next');
            return null;
        }

        if (container.nodeType === Node.ELEMENT_NODE) {
            const target = keyName === 'Backspace'
                ? container.childNodes[offset - 1]
                : container.childNodes[offset];
            const direct = findImgFrom(target, keyName === 'Backspace' ? 'prev' : 'next');
            if (direct) return direct;

            const sibling = keyName === 'Backspace'
                ? container.childNodes[offset - 1]?.previousSibling
                : container.childNodes[offset]?.nextSibling;
            return findImgFrom(sibling, keyName === 'Backspace' ? 'prev' : 'next');
        }

        return null;
    };

    window._commUtil.removeImagesInSelection = function removeImagesInSelection(editor, range, selectionKey) {
        if (!editor || !range || range.collapsed) return false;

        const imgs = Array.from(editor.querySelectorAll('img')).filter(function (img) {
            try { return range.intersectsNode(img); }
            catch (e) { return false; }
        });

        if (!imgs.length) return false;

        imgs.forEach(function (img) {
            window._commUtil.removeInlineImageElement(editor, img, selectionKey);
        });
        return true;
    };

    window._commUtil.bindInlineImageDeleteSupport = function bindInlineImageDeleteSupport(editor, selectedImages, selectionKey) {
        if (!editor) return;
        editor._inlineImageSelectedImages = selectedImages || editor._inlineImageSelectedImages || [];

        if (editor.dataset.inlineImageDeleteBound === 'true') return;
        editor.dataset.inlineImageDeleteBound = 'true';

        editor.addEventListener('click', function (e) {
            const img = e.target && e.target.closest ? e.target.closest('img') : null;
            if (img && editor.contains(img)) {
                e.preventDefault();
                window._commUtil.selectInlineImage(editor, img);
                return;
            }
            window._commUtil.clearInlineImageSelection(editor);
        });

        editor.addEventListener('keydown', function (e) {
            if (e.key !== 'Backspace' && e.key !== 'Delete') return;

            const selectedImg = editor.querySelector('img[data-inline-selected="true"]');
            if (selectedImg) {
                e.preventDefault();
                window._commUtil.removeInlineImageElement(editor, selectedImg, selectionKey);
                return;
            }

            const sel = window.getSelection();
            if (!sel || !sel.rangeCount) return;
            const range = sel.getRangeAt(0);
            const node = range.commonAncestorContainer;
            if (!(node === editor || editor.contains(node))) return;

            if (!range.collapsed && window._commUtil.removeImagesInSelection(editor, range, selectionKey)) {
                e.preventDefault();
                return;
            }

            const adjacentImg = window._commUtil.findAdjacentImageForDelete(editor, range, e.key);
            if (adjacentImg) {
                e.preventDefault();
                window._commUtil.removeInlineImageElement(editor, adjacentImg, selectionKey);
            }
        });

        const observer = new MutationObserver(function () {
            window._commUtil.cleanupDeletedInlineImageRecords(editor);
        });
        observer.observe(editor, { childList: true, subtree: true });
    };

    window._commUtil.insertImagesIntoEditor = function insertImagesIntoEditor(editor, files, selectedImages, selectionKey) {
        if (!editor) return;
        selectedImages = selectedImages || [];
        window._commUtil.bindInlineImageDeleteSupport(editor, selectedImages, selectionKey);

        const validFiles = Array.from(files || []).filter(file => {
            if (!file.type || !file.type.startsWith('image/')) {
                if (typeof toast === 'function') toast('이미지 파일만 추가할 수 있습니다.');
                return false;
            }
            return true;
        });

        validFiles.forEach(file => {
            const itemId = 'inline-img-' + Date.now() + '-' + Math.random().toString(16).slice(2);
            selectedImages.push({ id: itemId, file: file });

            const reader = new FileReader();
            reader.onload = function (ev) {
                const img = document.createElement('img');
                img.src = ev.target.result;
                img.alt = file.name || '첨부 이미지';
                img.dataset.inlineImageId = itemId;
                img.dataset.inlineEditorImage = 'true';
                img.setAttribute('contenteditable', 'false');
                img.draggable = false;
                img.style.cssText = 'max-width:100%;border-radius:10px;margin:10px 0;display:block;cursor:pointer;';

                const br = document.createElement('br');
                let inserted = false;

                if (selectionKey) {
                    inserted = window._commUtil.restoreEditorSelection(editor, selectionKey);
                }

                const sel = window.getSelection();
                if (sel && sel.rangeCount && (editor.contains(sel.anchorNode) || sel.anchorNode === editor)) {
                    const range = sel.getRangeAt(0);
                    range.deleteContents();

                    range.insertNode(br);
                    range.insertNode(img);

                    range.setStartAfter(br);
                    range.collapse(true);
                    sel.removeAllRanges();
                    sel.addRange(range);
                    inserted = true;
                }

                if (!inserted) {
                    editor.appendChild(img);
                    editor.appendChild(br);
                }

                editor.focus();
                if (selectionKey) window._commUtil.saveEditorSelection(editor, selectionKey);
                window._commUtil.cleanupDeletedInlineImageRecords(editor);
            };
            reader.readAsDataURL(file);
        });

        if (validFiles.length && typeof toast === 'function') {
            toast('본문에 이미지가 삽입되었습니다. 이미지를 클릭한 뒤 Backspace/Delete로 삭제할 수 있습니다.');
        }
    };

    window._commUtil.uploadPostImages = async function uploadPostImages(selectedImages) {
        const items = Array.from(selectedImages || []);
        if (!items.length) return [];

        const formData = new FormData();
        items.forEach(item => formData.append('files', item.file));

        const token = window._commUtil.getAccessToken();
        const uploadRes = await fetch('/api/posts/images', {
            method: 'POST',
            headers: token ? { Authorization: 'Bearer ' + token } : {},
            body: formData
        });

        if (!uploadRes.ok) throw new Error('이미지 업로드에 실패했습니다.');
        return await uploadRes.json();
    };

    window._commUtil.finalizeInlineEditorImages = async function finalizeInlineEditorImages(editor, selectedImages) {
        if (!editor) return { content: '', imageUrls: [], uploadedImageUrls: [] };

        window._commUtil.clearInlineImageSelection(editor);
        window._commUtil.cleanupDeletedInlineImageRecords(editor);

        const pendingImgs = Array.from(editor.querySelectorAll('img[data-inline-image-id]'));
        const usedItems = Array.from(selectedImages || []).filter(item =>
            pendingImgs.some(img => img.dataset.inlineImageId === item.id)
        );

        const uploadedUrls = await window._commUtil.uploadPostImages(usedItems);
        const uploadedImageUrls = Array.isArray(uploadedUrls)
            ? uploadedUrls.filter(Boolean)
            : [];

        usedItems.forEach((item, index) => {
            const img = pendingImgs.find(el => el.dataset.inlineImageId === item.id);
            if (!img) return;

            const uploadedUrl = uploadedImageUrls[index];
            if (!uploadedUrl) return;

            img.src = uploadedUrl;
            img.removeAttribute('data-inline-image-id');
            img.removeAttribute('data-inline-editor-image');
            img.removeAttribute('data-inline-selected');
            img.removeAttribute('contenteditable');
            img.style.cssText = 'max-width:100%;border-radius:10px;margin:10px 0;display:block;';
        });

        window._commUtil.cleanupDeletedInlineImageRecords(editor);

        const content = window._commUtil.sanitizePostContent(editor.innerHTML).trim();
        const imageUrls = window._commUtil.extractImageUrlsFromHtml(content)
            .filter(src => src && !src.startsWith('data:image/'));

        return {
            content: content,
            imageUrls: imageUrls,
            uploadedImageUrls: uploadedImageUrls
        };
    };

    window._commUtil.editorHasContent = function editorHasContent(editor) {
        if (!editor) return false;
        const text = (editor.textContent || '').replace(/\u00a0/g, ' ').trim();
        return !!text || !!editor.querySelector('img');
    };

    window._commUtil.sanitizePostContent = function sanitizePostContent(html) {
        const template = document.createElement('template');
        template.innerHTML = String(html || '');

        const allowedTags = new Set(['B', 'STRONG', 'I', 'EM', 'U', 'BR', 'DIV', 'P', 'SPAN', 'IMG']);

        function clean(node) {
            Array.from(node.childNodes).forEach(child => clean(child));

            if (node.nodeType !== Node.ELEMENT_NODE) return;
            const tag = node.tagName.toUpperCase();

            if (!allowedTags.has(tag)) {
                node.replaceWith(document.createTextNode(node.textContent || ''));
                return;
            }

            const originalSrc = tag === 'IMG' ? (node.getAttribute('src') || '') : '';
            Array.from(node.attributes).forEach(attr => node.removeAttribute(attr.name));

            if (tag === 'IMG') {
                const src = originalSrc;
                const allowedSrc = src.startsWith('/') || src.startsWith('http://') || src.startsWith('https://') || src.startsWith('data:image/');
                if (!allowedSrc) {
                    node.remove();
                    return;
                }
                node.setAttribute('src', src);
                node.setAttribute('alt', '첨부 이미지');
                node.setAttribute('style', 'max-width:100%;border-radius:10px;margin:10px 0;display:block;');
            }
        }

        clean(template.content);
        return template.innerHTML;
    };

    window._commUtil.extractImageUrlsFromHtml = function extractImageUrlsFromHtml(html) {
        const template = document.createElement('template');
        template.innerHTML = String(html || '');
        return Array.from(template.content.querySelectorAll('img[src]'))
            .map(img => img.getAttribute('src'))
            .filter(Boolean);
    };

    window._commUtil.buildEditableContentWithImages = function buildEditableContentWithImages(post) {
        const baseContent = window._commUtil.sanitizePostContent(post?.content || '');
        const inlineUrls = new Set(window._commUtil.extractImageUrlsFromHtml(baseContent));
        const detachedImages = Array.isArray(post?.imageUrls) ? post.imageUrls : [];

        const detachedImageHtml = detachedImages
            .filter(url => url && !inlineUrls.has(url))
            .map(url => '<p><img src="' + window._commUtil.escapeHtml(url) + '" alt="첨부 이미지" style="max-width:100%;border-radius:10px;margin:10px 0;display:block;"></p>')
            .join('');

        return (baseContent + detachedImageHtml).trim();
    };

})();


/* =============================================================================
 * community v2 — setCommTab 통합 래퍼 (카테고리 추적 + 목록 로드)
 *
 * 기존 IIFE 4 / IIFE 10 / IIFE 12 의 세 겹 래핑을 하나로 통합.
 * 플래그를 __communityV2Final 하나로 통일해 순서 문제 제거.
 * ============================================================================= */
(function () {
    'use strict';

    const tabToCategory = {
        route: 'ROUTE', stay: 'STAY', food: 'FOOD', tour: 'TOUR', cafe: 'CAFE'
    };

    const PLACE_TABS = { stay: 'stay', food: 'food', tour: 'tour', cafe: 'cafe' };

    function patchSetCommTab() {
        if (typeof window.setCommTab !== 'function') {
            setTimeout(patchSetCommTab, 50);
            return;
        }
        if (window.setCommTab.__communityV2Final) return;

        const prev = window.setCommTab;

        window.setCommTab = function (btn, cat) {
            /* ── IIFE 4: 카테고리 추적 ── */
            const category = tabToCategory[cat] || 'ROUTE';
            window._communityWriteCategory = category;

            /* 탭 전환 시 후기 패널에서 숨겼던 검색/정렬 바 복원 */
            const _sb = document.querySelector('#page-community .search-bar');
            if (_sb) _sb.style.display = '';

            if (typeof _commState !== 'undefined') {
                _commState.currentTab = cat || 'route';
                _commState.page = 0;
            }

            const result = prev.apply(this, arguments);

            /* ── IIFE 12 / IIFE 10: 탭 종류에 따라 목록 갱신 ── */
            if (PLACE_TABS[cat]) {
                // place 탭 → loadPlaceCards
                if (typeof window._loadPlaceCards === 'function') {
                    window._loadPlaceCards(cat, 0, true);
                }
            } else {
                // route 탭 → loadCommunityPosts
                setTimeout(() => {
                    if (typeof window.loadCommunityPosts === 'function') {
                        window.loadCommunityPosts(0, true);
                    }
                }, 50);
            }

            return result;
        };

        /* 모든 구버전 플래그 호환 */
        window.setCommTab.__communityV2Final = true;
        window.setCommTab.__communityV2Wrapped = true;
        window.setCommTab.__communityV2ListWrapped = true;
        window.setCommTab.__v3Patched = true;
    }

    patchSetCommTab();
    document.addEventListener('DOMContentLoaded', patchSetCommTab);

})();


/* =============================================================================
 * community v2 — 신고
 * ============================================================================= */
(function () {
    'use strict';

    const { escapeHtml, getCurrentPostId, requireLogin } = window._commUtil;

    window.doReviewReport = function () {
        if (!requireLogin()) return;

        const postId = getCurrentPostId();
        if (!postId) {
            if (typeof toast === 'function') toast('게시글 정보를 찾을 수 없습니다.');
            return;
        }

        window._reportPostId = postId;
        const sel = document.getElementById('reportReasonSelect');
        if (sel) sel.value = '';
        const modal = document.getElementById('reportPostModal');
        if (modal) modal.style.display = 'flex';
    };

    window.submitReportPost = async function () {
        const sel = document.getElementById('reportReasonSelect');
        const reason = sel ? sel.value : '';
        if (!reason) {
            if (typeof toast === 'function') toast('신고 사유를 선택해주세요.');
            return;
        }

        const postId = window._reportPostId || window._currentPostId;
        const res = await api.post(`/api/posts/${postId}/reports`, { postId, reason });

        const modal = document.getElementById('reportPostModal');
        if (modal) modal.style.display = 'none';
        window._reportPostId = null;

        if (typeof toast === 'function') {
            toast(res?.success !== false ? '🚨 신고가 접수되었습니다.' : res?.message || '신고 처리에 실패했습니다.');
        }
    };

})();


/* =============================================================================
 * community v2 — 목록 카드 스크랩
 * ============================================================================= */
(function () {
    'use strict';

    window.scrapPost = async function (e, postId) {
        if (e && typeof e.stopPropagation === 'function') e.stopPropagation();

        if (!window._commUtil.requireLogin()) return;

        if (typeof _isSuspended !== 'undefined' && _isSuspended) {
            if (typeof toast === 'function') toast('⛔ 해당 계정은 커뮤니티 기능이 제한되었습니다.');
            return;
        }

        if (!postId) {
            if (typeof toast === 'function') toast('게시글 정보를 찾을 수 없습니다.');
            return;
        }

        const currentTab = (typeof _commState !== 'undefined' && _commState.currentTab)
            ? _commState.currentTab : 'route';

        const categoryMap = { route: 'ROUTE', stay: 'STAY', food: 'FOOD', tour: 'TOUR', cafe: 'CAFE' };
        const category = categoryMap[currentTab] || 'ROUTE';

        const res = await api.post(`/api/posts/${postId}/scraps?category=${category}`, {});

        const isSuccess = res && res.success !== false;
        if (!isSuccess) {
            if (typeof toast === 'function') toast(res?.message || '스크랩 처리에 실패했습니다.');
            return;
        }

        /* data === true → 스크랩됨, data === false → 취소됨 */
        const scrapped = res.data === true;

        /* 카운트 UI 업데이트 */
        const el = document.getElementById('scrap-cnt-' + postId);
        if (el) {
            const n = parseInt(el.textContent.replace(/\D/g, ''), 10) || 0;
            el.textContent = '🔖 ' + (scrapped ? n + 1 : Math.max(0, n - 1));
        }

        /* 버튼 상태 토글 — 이벤트 타겟이 버튼이면 텍스트/스타일 변경 */
        const btn = e && e.target && e.target.closest ? e.target.closest('button') : null;
        if (btn) {
            if (scrapped) {
                btn.textContent = '✅ 스크랩됨';
                btn.style.background = 'var(--sage-pale)';
                btn.style.borderColor = 'var(--sage-d)';
                btn.style.color = 'var(--sage-d)';
            } else {
                btn.textContent = '🔖 스크랩';
                btn.style.background = '';
                btn.style.borderColor = '';
                btn.style.color = '';
            }
        }

        if (typeof toast === 'function') toast(scrapped ? '🔖 스크랩했습니다.' : '🔖 스크랩을 취소했습니다.');
    };

})();


/* =============================================================================
 * community v2 — 게시글 상세 렌더링
 * ============================================================================= */
(function () {
    'use strict';

    const { escapeHtml, formatDate, parseStyleTags } = window._commUtil;

    function setText(id, value) {
        const el = document.getElementById(id);
        if (el) el.textContent = value ?? '';
    }

    function setHtml(id, html) {
        const el = document.getElementById(id);
        if (el) el.innerHTML = html ?? '';
    }

    function renderReviewComments(comments) {
        const box = document.getElementById('pr-comments');
        if (!box) return;

        const list = Array.isArray(comments) ? comments : [];

        /* 현재 열린 게시글 ID */
        const curPostId = window._currentPostId || window._openedPostId || 0;

        const _myIdForComment = window._myUserId ? window._myUserId() : null;

        // 숨김 댓글: 관리자 또는 댓글 작성자 본인만 보임
        const _isAdminForComment = window._checkIsAdmin ? window._checkIsAdmin() : false;
        const visibleComments = list.filter(c => {
            if (c.status === 'DELETED') return false;
            if (c.status === 'HIDDEN') {
                return _isAdminForComment || (_myIdForComment && Number(c.userId) === Number(_myIdForComment));
            }
            return true;
        });

        const commentItems = visibleComments.length
            ? visibleComments.map(c => {
                const isHiddenComment = c.status === 'HIDDEN';
                if (isHiddenComment) {
                    return `
                <div class="comment-item" style="background:#FFF3F3;border-radius:8px;padding:8px 12px">
                    <div style="display:flex;align-items:center;gap:6px">
                        <span class="comment-writer" style="color:#9E9E9E">${escapeHtml(c.writerName || '사용자')}${window._adminBadge ? window._adminBadge(c.writerRole) : ''}</span>
                        <span class="comment-date">${escapeHtml(formatDate(c.createdAt))}</span>
                        <span style="font-size:10px;color:#E53935;background:#FFEBEE;border:1px solid #FFCDD2;border-radius:4px;padding:1px 6px">🚫 숨김 처리된 댓글</span>
                    </div>
                    <div class="comment-content" style="color:#9E9E9E">${escapeHtml(c.content || '')}</div>
                </div>`;
                }
                return `
                <div class="comment-item">
                    <div style="display:flex;align-items:center;gap:6px">
                        <span class="comment-writer">${escapeHtml(c.writerName || '사용자')}${window._adminBadge ? window._adminBadge(c.writerRole) : ''}</span>
                        <span class="comment-date">${escapeHtml(formatDate(c.createdAt))}</span>
                        <button onclick="openReportCommentModal(${c.commentId}, ${curPostId})"
                                style="margin-left:auto;font-size:10px;background:none;border:1px solid var(--border2);
                                       border-radius:4px;padding:1px 7px;cursor:pointer;color:var(--text3)"
                                title="댓글 신고">🚨 신고</button>
                    </div>
                    <div class="comment-content">${escapeHtml(c.content || '')}</div>
                </div>`;
            }).join('')
            : `<div style="padding:12px 0;color:var(--text3);font-size:13px">아직 댓글이 없습니다.</div>`;

        box.innerHTML = `
            <h3>댓글</h3>
            <div style="display:flex;gap:8px;margin-bottom:14px">
                <input id="commentInput" type="text" placeholder="댓글을 입력하세요..."
                       style="flex:1;padding:10px 12px;border-radius:10px;border:1.5px solid var(--border2);font-size:13px">
                <button class="btn-f" onclick="submitComment()">등록</button>
            </div>
            <div id="commentList">${commentItems}</div>
        `;
    }

    function renderLinkedPlan(post) {
        const badge    = document.getElementById('pr-plan-badge');
        const placeList = document.getElementById('pr-place-list');

        if (!badge) return;

        if (!post.planId) {
            badge.style.display = 'none';
            if (placeList) placeList.style.display = 'none';
            return;
        }

        badge.style.display = 'block';
        badge.innerHTML = `
            <strong>연동된 플랜</strong><br>
            ${escapeHtml(post.planTitle || '제목 없는 플랜')}
            ${post.planDestination ? ` · ${escapeHtml(post.planDestination)}` : ''}
        `;

        if (placeList) {
            placeList.style.display = 'block';
            placeList.innerHTML = `
                <div class="route-preview-card">
                    <div class="route-preview-title">${escapeHtml(post.planTitle || '연동된 여행 플랜')}</div>
                    <div class="route-preview-meta">
                        ${escapeHtml(post.planStartDate || '')}
                        ${post.planEndDate ? ` ~ ${escapeHtml(post.planEndDate)}` : ''}
                    </div>
                </div>
            `;
        }
    }

    function getLoggedInUserIdForReviewEdit() {
        const user = (typeof window._currentUser !== 'undefined' && window._currentUser)
            ? window._currentUser
            : (typeof _currentUser !== 'undefined' ? _currentUser : null);

        return user?.userId ?? user?.id ?? null;
    }

    function isCurrentUserPostOwnerForReviewEdit(post) {
        if (!post) return false;

        // 백엔드에서 본인 여부를 내려주는 경우 우선 사용
        if (post.isMine === true || post.mine === true) return true;

        const loginUserId = getLoggedInUserIdForReviewEdit();
        const writerId = post.userId ?? post.writerId ?? post.authorId ?? null;

        if (!loginUserId || !writerId) return false;
        return String(loginUserId) === String(writerId);
    }

    function updateReviewEditButtonForModal(post) {
        const editBtn = document.getElementById('btn-review-edit');
        if (!editBtn) return;

        const postId = post?.postId ?? post?.id ?? window._currentPostId ?? window._openedPostId;
        const canEdit = !!postId && isCurrentUserPostOwnerForReviewEdit(post);

        editBtn.style.display = canEdit ? 'inline-flex' : 'none';
        editBtn.dataset.postId = canEdit ? String(postId) : '';
    }

    window.openCurrentPostEditModal = async function openCurrentPostEditModal() {
        const editBtn = document.getElementById('btn-review-edit');
        const postId = editBtn?.dataset.postId
            || window._currentPostId
            || window._openedPostId
            || window._currentPostDetail?.postId
            || window._currentPostDetail?.id;

        if (!postId) {
            if (typeof toast === 'function') toast('게시글 정보를 찾을 수 없습니다.');
            return;
        }

        if (typeof window.editMyPost !== 'function') {
            if (typeof toast === 'function') toast('수정 기능을 준비하지 못했습니다.');
            return;
        }

        await window.editMyPost(postId);
    };

    function renderPostDetail(post) {
        if (!post) return;

        // 숨김 게시글 접근 제어
        const _myId = window._myUserId ? window._myUserId() : null;
        const _isAdminUser = window._checkIsAdmin ? window._checkIsAdmin() : false;
        const _isMyPost = _myId && (
            Number(post.userId) === Number(_myId) ||
            Number(post.writerId) === Number(_myId)
        );
        const _isHiddenPost = post.status === 'HIDDEN';
        if (_isHiddenPost && !_isAdminUser && !_isMyPost) {
            // 접근 불가: 커뮤니티로 돌아감
            if (typeof toast === 'function') toast('접근할 수 없는 게시글입니다.');
            if (typeof go === 'function') go('community');
            return;
        }

        window._currentPostId      = post.postId;
        window._openedPostId       = post.postId;
        window._currentPostCategory = post.category || 'ROUTE';
        window._currentPostDetail  = post;

        const tags = parseStyleTags(post.styleTags);

        // 제목 + 숨김 배지 표시 (숨김 글이면 제목 옆에 배지 인라인 삽입)
        const _titleEl2 = document.getElementById('pr-title');
        if (_titleEl2) {
            if (_isHiddenPost) {
                _titleEl2.innerHTML = escapeHtml(post.title || '') +
                    ' <span style="font-size:12px;font-weight:700;background:#FFF3F3;color:#E53935;' +
                    'border:1px solid #FFCDD2;border-radius:5px;padding:2px 8px;vertical-align:middle;' +
                    'white-space:nowrap">🚫 숨김 처리된 글</span>';
            } else {
                _titleEl2.textContent = post.title || '';
            }
        }
        // 이전 배너 요소가 남아 있다면 제거
        const _oldBanner = document.getElementById('pr-hidden-banner');
        if (_oldBanner) _oldBanner.remove();
        // 관리자 배지 포함 작성자명
        const _prAuthorEl = document.getElementById('pr-author-name');
        if (_prAuthorEl) {
            _prAuthorEl.innerHTML = escapeHtml(post.writerName || '사용자') +
                (window._adminBadge ? window._adminBadge(post.writerRole) : '');
        }
        setText('pr-author-av',   (post.writerName || 'U').substring(0, 1));
        setText('pr-cat',         '여행 후기');
        setText('pr-tag',         tags.length ? '#' + tags[0] : '#커뮤니티');

        setHtml('pr-meta', `
            <span>조회 ${escapeHtml(post.viewCount ?? 0)}</span>
            <span>좋아요 ${escapeHtml(post.likeCount ?? 0)}</span>
            ${post.createdAt ? `<span>${escapeHtml(formatDate(post.createdAt))}</span>` : ''}
        `);

        const tagHtml = tags.length
            ? `<div style="display:flex;gap:6px;flex-wrap:wrap;margin-bottom:14px">
                ${tags.map(tag => `<span class="post-cat" style="background:var(--sage-pale);color:var(--sage-d)">#${escapeHtml(tag)}</span>`).join('')}
               </div>`
            : '';

        const safeContent = window._commUtil.sanitizePostContent(post.content || '').trim();
        const inlineImageUrls = new Set(window._commUtil.extractImageUrlsFromHtml(safeContent));
        const fallbackImageUrls = Array.isArray(post.imageUrls)
            ? post.imageUrls.filter(url => url && !inlineImageUrls.has(url))
            : [];

        const imageHtml = fallbackImageUrls.length
            ? `<div style="display:flex;flex-direction:column;gap:10px;margin-bottom:16px">
                ${fallbackImageUrls.map(url =>
                `<img src="${escapeHtml(url)}" alt="첨부 이미지"
                          style="max-width:100%;border-radius:10px;display:block"
                          onerror="this.style.display='none'">`
            ).join('')}
               </div>`
            : '';

        const bodyContentHtml =
            '<div style="white-space:pre-wrap;line-height:1.8;color:var(--text2);font-size:15px;' +
            'max-width:760px;overflow-wrap:anywhere;word-break:break-word;box-sizing:border-box">' +
            safeContent +
            '</div>';

        setHtml('pr-body', tagHtml + bodyContentHtml + imageHtml);

        renderLinkedPlan(post);
        renderReviewComments(post.comments);

        const ctaSub = document.getElementById('pr-cta-sub');
        if (ctaSub) ctaSub.textContent = post.planTitle
            ? `${post.planTitle} 플랜을 기반으로 새 여행을 계획할 수 있습니다.` : '';

        updateReviewEditButtonForModal(post);
    }

    window.openPostDetail = async function (postId) {
        if (!postId) {
            if (typeof toast === 'function') toast('게시글 정보를 찾을 수 없습니다.');
            return;
        }

        window._currentPostId  = postId;
        window._openedPostId   = postId;
        sessionStorage.setItem('communityCurrentPostId', String(postId));

        if (typeof go === 'function') go('review');

        const titleEl = document.getElementById('pr-title');
        const bodyEl  = document.getElementById('pr-body');
        if (titleEl) titleEl.textContent = '';
        if (bodyEl)  bodyEl.innerHTML = '<div style="text-align:center;padding:40px;color:var(--text3)">불러오는 중...</div>';

        const res = await api.get(`/api/posts/${postId}`);

        if (!res || res.success === false || !res.data) {
            if (bodyEl) bodyEl.innerHTML = '<div style="text-align:center;padding:40px;color:var(--coral)">게시글을 불러오지 못했습니다.</div>';
            if (typeof toast === 'function') toast(res?.message || '게시글 조회에 실패했습니다.');
            return;
        }

        renderPostDetail(res.data);
    };

    window.submitComment = async function () {
        const postId  = window._currentPostId || window._openedPostId;
        const input   = document.getElementById('commentInput');
        const content = input ? input.value.trim() : '';

        if (!postId) { if (typeof toast === 'function') toast('게시글 정보를 찾을 수 없습니다.'); return; }
        if (!window._commUtil.requireLogin()) return;
        if (!content) { if (typeof toast === 'function') toast('댓글을 입력해주세요.'); return; }

        const res = await api.post(`/api/posts/${postId}/comments`, { content });

        if (res && res.success !== false) {
            if (typeof toast === 'function') toast('댓글이 등록되었습니다.');
            await window.openPostDetail(postId);
        } else {
            if (typeof toast === 'function') toast(res?.message || '댓글 등록에 실패했습니다.');
        }
    };

})();


/* =============================================================================
 * community v2 — 게시글 작성 (submitReview)
 * ============================================================================= */
(function () {
    'use strict';

    window._communityWriteCategory = window._communityWriteCategory || 'ROUTE';

    function getCategoryFromCurrentTabButton() {
        const activeTab = document.querySelector('#commTabs .comm-tab.on');
        if (!activeTab) return window._communityWriteCategory || 'ROUTE';

        const text = activeTab.textContent.trim();
        if (text.includes('숙소'))    return 'STAY';
        if (text.includes('맛집'))    return 'FOOD';
        if (text.includes('관광지'))  return 'TOUR';
        if (text.includes('카페'))    return 'CAFE';
        if (text.includes('여행 경로')) return 'ROUTE';

        return window._communityWriteCategory || 'ROUTE';
    }

    window.submitReview = async function () {
        if (typeof _isSuspended !== 'undefined' && _isSuspended) {
            if (typeof toast === 'function') toast('⛔ 해당 계정은 커뮤니티 기능이 제한되었습니다.');
            return;
        }
        if (!window._commUtil.requireLogin()) return;

        const titleEl   = document.getElementById('writeTitle');
        const editorEl  = document.getElementById('blogEditor');
        const tagsEl    = document.getElementById('writeTags');
        const publicEl  = document.getElementById('writePublic');

        const title   = titleEl ? titleEl.value.trim() : '';
        const tagText = tagsEl ? tagsEl.value.trim() : '';

        const styleTags = tagText.split(/[\s,]+/).map(v => v.trim()).filter(Boolean).join(',');
        const isPublic  = publicEl ? !!publicEl.checked : true;

        if (!title || !window._commUtil.editorHasContent(editorEl)) {
            if (typeof toast === 'function') toast('제목과 내용을 입력해주세요.');
            return;
        }

        const categoryCode = getCategoryFromCurrentTabButton();
        window._communityWriteCategory = categoryCode;

        let finalized;
        try {
            finalized = await window._commUtil.finalizeInlineEditorImages(
                editorEl,
                window._communityWriteImages || []
            );
        } catch (e) {
            if (typeof toast === 'function') toast(e.message || '이미지 업로드에 실패했습니다.');
            return;
        }

        const body = {
            planId: document.getElementById('writePlanId')?.value
                ? Number(document.getElementById('writePlanId').value) : null,
            title,
            content: finalized.content,
            styleTags,
            category: categoryCode,
            isPublic,
            imageUrls: finalized.uploadedImageUrls || finalized.imageUrls || []
        };

        const res = await api.post('/api/posts', body);

        const success = typeof res === 'number' || res?.success === true || typeof res?.data === 'number';

        if (success) {
            window._communitySelectedImages = [];
            window._communityWriteImages = [];
            if (editorEl) editorEl.innerHTML = '';
            const preview = document.getElementById('writeImagePreview');
            if (preview) preview.innerHTML = '';
            if (typeof closeWrite === 'function') closeWrite();
            if (typeof toast === 'function') toast('후기가 등록되었습니다! 🎉');
            if (typeof loadCommunityPosts === 'function') await loadCommunityPosts(0, true);
            if (typeof go === 'function') go('community');
            return;
        }

        if (typeof toast === 'function') toast(res?.message || '게시글 등록에 실패했습니다.');
    };

})();


/* =============================================================================
 * community v2 — 플랜 연동 select
 * ============================================================================= */
(function () {
    'use strict';

    function extractArray(res) {
        if (Array.isArray(res)) return res;
        if (Array.isArray(res?.data)) return res.data;
        if (Array.isArray(res?.data?.content)) return res.data.content;
        if (Array.isArray(res?.content)) return res.content;
        return [];
    }

    function getTripId(trip) {
        return trip.tripId || trip.planId || trip.id || trip.travelPlanId;
    }

    function getTripTitle(trip) {
        return trip.title || trip.planTitle || trip.destination || trip.name
            || `여행계획 #${getTripId(trip)}`;
    }

    async function loadWritePlanOptions(select) {
        if (!select) return;
        select.innerHTML = `<option value="">플랜을 선택하지 않음</option>`;

        try {
            const res   = await api.get('/api/trips');
            const trips = extractArray(res);

            if (!trips.length) {
                const opt = document.createElement('option');
                opt.value = ''; opt.textContent = '연동 가능한 플랜이 없습니다'; opt.disabled = true;
                select.appendChild(opt);
                return;
            }

            trips.forEach(trip => {
                const id = getTripId(trip);
                if (!id) return;
                const opt = document.createElement('option');
                opt.value = id; opt.textContent = getTripTitle(trip);
                select.appendChild(opt);
            });
        } catch (e) {
            console.error('[community-v2] 플랜 목록 조회 실패:', e);
            if (typeof toast === 'function') toast('플랜 목록을 불러오지 못했습니다.');
        }
    }

    window.injectWritePlanSelect = async function () {
        const titleEl = document.getElementById('writeTitle');
        if (!titleEl) return;

        const modal = titleEl.closest('.modal') || titleEl.closest('.overlay')
            || titleEl.closest('div') || document.body;

        const duplicatedWrap = document.getElementById('writePlanWrap');
        if (duplicatedWrap) duplicatedWrap.remove();

        let select = document.getElementById('writePlanId');

        if (!select) {
            const candidates = [...modal.querySelectorAll('select')]
                .filter(s => !s.classList.contains('loc-big'));
            select = candidates[0];
        }

        if (!select) {
            const wrap = document.createElement('div');
            wrap.style.cssText = 'margin-top:14px;margin-bottom:14px';
            wrap.innerHTML = `
                <label style="display:block;font-size:13px;font-weight:700;margin-bottom:6px;color:var(--text2)">
                    플랜 연동 (선택)
                </label>
                <select id="writePlanId"
                        style="width:100%;padding:11px 12px;border-radius:10px;border:1.5px solid var(--border2);background:#fff;color:var(--text1);font-size:13px">
                    <option value="">플랜을 선택하지 않음</option>
                </select>
            `;
            titleEl.insertAdjacentElement('afterend', wrap);
            select = document.getElementById('writePlanId');
        }

        select.id = 'writePlanId';
        await loadWritePlanOptions(select);
    };

})();


/* =============================================================================
 * community v2 — 이미지 첨부 버튼
 * ============================================================================= */
(function () {
    'use strict';

    window._communitySelectedImages = window._communitySelectedImages || [];

    window.openWriteImagePicker = function openWriteImagePicker() {
        const editor = document.getElementById('blogEditor');

        if (editor && window._commUtil) {
            window._communityWriteImages = window._communityWriteImages || [];
            window._commUtil.bindEditorSelectionMemory(editor, '_communityWriteEditorRange');
            window._commUtil.bindInlineImageDeleteSupport(editor, window._communityWriteImages, '_communityWriteEditorRange');
            window._commUtil.saveEditorSelection(editor, '_communityWriteEditorRange');
        }

        const input = document.getElementById('writeImageInput');

        if (input) {
            input.click();
        } else if (typeof toast === 'function') {
            toast('이미지 입력창을 찾을 수 없습니다.');
        }
    };

    function findImageButton() {
        return [...document.querySelectorAll('button')]
            .find(btn => btn.innerText && btn.innerText.includes('사진 첨부'));
    }

    function ensureImageInput() {
        let input = document.getElementById('communityImageInput');
        if (!input) {
            input = document.createElement('input');
            input.id = 'communityImageInput';
            input.type = 'file'; input.accept = 'image/*'; input.multiple = true;
            input.style.display = 'none';
            document.body.appendChild(input);
            input.addEventListener('change', handleImageSelect);
        }
        return input;
    }

    function handleImageSelect(e) {
        const files = [...(e.target.files || [])];
        if (!files.length) return;

        const editor = document.getElementById('blogEditor');
        if (!editor) {
            if (typeof toast === 'function') toast('본문 입력창을 찾을 수 없습니다.');
            e.target.value = '';
            return;
        }

        window._communityWriteImages = window._communityWriteImages || [];
        window._commUtil.insertImagesIntoEditor(
            editor,
            files,
            window._communityWriteImages,
            '_communityWriteEditorRange'
        );

        e.target.value = '';
    }

    window.bindCommunityImageButton = function () {
        const btn = findImageButton();
        if (!btn) return;
        btn.onclick = function (e) {
            if (e) e.preventDefault();
            ensureImageInput().click();
        };
    };

    const prevCheckAndOpenWrite = window.checkAndOpenWrite;
    if (typeof prevCheckAndOpenWrite === 'function') {
        window.checkAndOpenWrite = function () {
            window._communitySelectedImages = [];
            window._communityWriteImages    = [];
            const preview = document.getElementById('writeImagePreview');
            if (preview) preview.innerHTML = '';
            const result = prevCheckAndOpenWrite.apply(this, arguments);
            setTimeout(function () {
                if (typeof window.injectWritePlanSelect === 'function') window.injectWritePlanSelect();

                const editor = document.getElementById('blogEditor');
                if (editor && window._commUtil) {
                    window._commUtil.bindEditorSelectionMemory(editor, '_communityWriteEditorRange');
                    window._commUtil.bindInlineImageDeleteSupport(editor, window._communityWriteImages, '_communityWriteEditorRange');
                    window._commUtil.resetEditorCommandState(editor, '_communityWriteEditorRange');
                }
            }, 200);

            return result;
        };
    }

})();

/* =============================================================================
 * community v2 — 후기 작성 이미지 핸들러
 * ============================================================================= */
window._communityWriteImages = window._communityWriteImages || [];

window._handleWriteImageSelect = function(input) {
    var files = Array.prototype.slice.call(input.files || []);
    if (!files.length) return;

    var editor = document.getElementById('blogEditor');
    if (!editor) {
        if (typeof toast === 'function') toast('본문 입력창을 찾을 수 없습니다.');
        input.value = '';
        return;
    }

    window._commUtil.insertImagesIntoEditor(editor, files, window._communityWriteImages, '_communityWriteEditorRange');
    input.value = '';
};


/* =============================================================================
 * community v2 — 목록 카드 렌더링
 * ============================================================================= */
(function () {
    'use strict';

    const { escapeHtml, parseStyleTags } = window._commUtil;

    function formatPostDate(createdAt) {
        if (!createdAt) return '날짜 없음';
        try { return String(createdAt).substring(0, 10).replaceAll('-', '.'); } catch (e) { return '날짜 없음'; }
    }

    function getCategoryLabel(tabKey, post) {
        if (post.catLabel) return post.catLabel;
        return { route: '여행 경로', stay: '숙소', food: '맛집', tour: '관광지', cafe: '카페' }[tabKey] || '여행 경로';
    }

    function getCategoryClass(tabKey, post) {
        if (post.catClass) return post.catClass;
        return { route: 'cat-route', stay: 'cat-stay', food: 'cat-food', tour: 'cat-tour', cafe: 'cat-cafe' }[tabKey] || 'cat-route';
    }

    window._renderPostList = function (posts, reset) {
        const currentTab = (typeof _commState !== 'undefined' && _commState.currentTab)
            ? _commState.currentTab : 'route';

        const tabEl = document.getElementById('tab-' + currentTab);
        if (!tabEl) return;

        if (reset) tabEl.innerHTML = '';

        if (!posts || !posts.length) {
            tabEl.innerHTML += `<div style="padding:40px 20px;text-align:center;color:var(--text3);font-size:14px">게시글이 없습니다.</div>`;
            return;
        }

        posts.forEach(post => {
            const isHidden   = post.hidden === true;
            // 숨김 게시글: 관리자 또는 작성자 본인만 볼 수 있음
            const _myId = window._myUserId ? window._myUserId() : null;
            const _isMyPost = _myId && Number(post.writerId) === Number(_myId);
            const _adminCheck = window._checkIsAdmin ? window._checkIsAdmin() : false;
            if (isHidden && !_adminCheck && !_isMyPost) return;

            const postId     = post.postId;
            const tags       = parseStyleTags(post.styleTags);
            const dateText   = formatPostDate(post.createdAt);
            const writerText = post.writerName || '사용자';
            const likes      = post.likes ?? post.likeCount ?? 0;
            const scraps     = post.scraps ?? post.scrapCount ?? 0;
            const views      = post.views ?? post.viewCount ?? 0;
            const catLabel   = getCategoryLabel(currentTab, post);
            const catClass   = getCategoryClass(currentTab, post);
            const dateVal    = post.createdAt
                ? String(post.createdAt).substring(0, 10).replaceAll('-', '') : '0';

            const div = document.createElement('div');
            div.className = 'comm-post-item';
            div.setAttribute('data-tags',    tags.join(','));
            div.setAttribute('data-content', post.content || '');
            div.setAttribute('data-author',  writerText);
            div.setAttribute('data-likes',   likes);
            div.setAttribute('data-scrap',   scraps);
            div.setAttribute('data-date',    dateVal);

            // 숨김 처리된 글 표시 (관리자 & 본인)
            const hiddenBadge = isHidden
                ? `<span style="font-size:10px;color:#E53935;background:#FFF3F3;border:1px solid #FFCDD2;border-radius:4px;padding:1px 6px;margin-left:6px">🚫 숨김 처리된 글</span>`
                : '';

            div.innerHTML = `
                <div class="post-card" onclick="openPostDetail(${postId})"
                     style="${isHidden ? 'opacity:0.55;background:#FAFAFA' : ''}">
                    <div class="community-card-head">
                        <span class="post-cat ${escapeHtml(catClass)}">${escapeHtml(catLabel)}</span>
                        <span class="community-card-meta">${escapeHtml(writerText)}${window._adminBadge ? window._adminBadge(post.writerRole) : ''} · ${escapeHtml(dateText)}</span>
                    </div>
                    <div class="post-ttl">${escapeHtml(post.title || '제목 없음')}${isHidden ? ' <span style="font-size:11px;font-weight:700;background:#FFF3F3;color:#E53935;border:1px solid #FFCDD2;border-radius:4px;padding:1px 7px;vertical-align:middle;white-space:nowrap">🚫 숨김 처리된 글</span>' : ''}</div>
                    ${tags.length ? `<div class="community-card-tags">${tags.map(tag => `<span>#${escapeHtml(tag)}</span>`).join('')}</div>` : ''}
                    <div class="post-foot">
                        <div class="post-stats">
                            <span class="post-stat">❤️ ${likes}</span>
                            <span class="post-stat">🔖 ${scraps}</span>
                            <span class="post-stat">👁 ${views}</span>
                        </div>
                    </div>
                </div>
            `;

            tabEl.appendChild(div);
        });
    };

    try { _renderPostList = window._renderPostList; } catch (e) { /* 일부 환경에서 재할당 불가 */ }

})();


/* =============================================================================
 * community v2 — 카테고리별 목록 / 페이징
 * ============================================================================= */
(function () {
    'use strict';

    // 현재 로그인 사용자 ID 반환 헬퍼
    window._myUserId = () => {
        const u = window._currentUser;
        if (!u) return null;
        return u.id || u.userId || u.memberId || null;
    };

    // 관리자 여부 체크 (window._isAdmin → window._currentUser.role → 로컬 _currentUser 순으로 시도)
    window._checkIsAdmin = () => {
        if (window._isAdmin === true) return true;
        if (window._currentUser && window._currentUser.role === 'ADMIN') return true;
        try {
            // app_main.js의 let _currentUser에 직접 접근 (같은 페이지 스코프)
            if (typeof _currentUser !== 'undefined' && _currentUser && _currentUser.role === 'ADMIN') return true;
        } catch(e) {}
        return false;
    };

    // 관리자 배지 HTML 헬퍼
    window._adminBadge = (role) =>
        role === 'ADMIN'
            ? ' <span style="font-size:10px;font-weight:800;background:#0d9488;color:#fff;border-radius:4px;padding:2px 9px;letter-spacing:.4px;vertical-align:middle">관리자</span>'
            : '';

    // 장소 카테고리 → 커뮤니티 탭 이름 매핑
    const _catToTab = {accommodation:'stay', restaurant:'food', cafe:'cafe', attraction:'tour'};

    // 방문 장소 스냅샷 카드 클릭: 장소 이름과 함께 리뷰 페이지로 이동
    window._placeSnapshotClick = function(el) {
        const placeId   = Number(el.dataset.placeId);
        const placeName = el.dataset.placeName || '';
        // page_place.html의 goToPlaceReviews로 전용 페이지 이동 (커뮤니티 탭 간섭 없음)
        if (typeof goToPlaceReviews === 'function') {
            goToPlaceReviews(placeId, placeName);
        }
    };

    const { extractPosts } = window._commUtil;

    const tabTextToKey = {
        '여행 경로': 'route', '숙소': 'stay', '맛집': 'food', '관광지': 'tour', '카페': 'cafe'
    };

    function getCurrentTabKey() {
        const activeTab = document.querySelector('#commTabs .comm-tab.on');
        if (activeTab) {
            const text = activeTab.textContent.trim();
            if (tabTextToKey[text]) return tabTextToKey[text];
        }
        if (typeof _commState !== 'undefined' && _commState.currentTab) return _commState.currentTab;
        return 'route';
    }

    function extractPage(res) {
        const page = res?.data || res || {};
        return {
            content:       Array.isArray(page.content) ? page.content : [],
            number:        Number(page.number ?? 0),
            totalPages:    Number(page.totalPages ?? 1),
            totalElements: Number(page.totalElements ?? 0)
        };
    }

    function removeCommunityV2Pager() {
        const v2Pager = document.getElementById('community-v2-pagination');
        if (v2Pager) v2Pager.remove();

        document.querySelectorAll('.pagination, .pager, .comm-pagination, .page-wrap, .post-pagination').forEach(el => {
            if (el.id !== 'community-v2-pagination') el.remove();
        });

        document.querySelectorAll('div').forEach(div => {
            if (div.id === 'community-v2-pagination') return;
            if (div.classList && div.classList.contains('place-v2-pagination')) return;
            const buttons = Array.from(div.querySelectorAll(':scope > button'));
            if (buttons.length < 2) return;
            if (buttons.every(btn => /^\d+$/.test(btn.textContent.trim()))) div.remove();
        });
    }

    function renderCommunityV2Pager(tab, pageNumber, totalPages) {
        removeCommunityV2Pager();
        const tabEl = document.getElementById('tab-' + tab);
        if (!tabEl || !totalPages || totalPages <= 0) return;

        const pager = document.createElement('div');
        pager.id = 'community-v2-pagination';
        pager.className = 'community-v2-pagination';

        const current = Math.min(Math.max(Number(pageNumber) || 0, 0), Number(totalPages) - 1);
        const pageIndexes = window._commUtil.getPagerWindow(current, totalPages, 5);

        let html = '';
        if (totalPages > 1) {
            html += `<button type="button" class="community-v2-page-btn" data-page="${Math.max(0, current - 1)}" ${current === 0 ? 'disabled' : ''}>&lt;</button>`;
        }

        pageIndexes.forEach(function (i) {
            html += `<button type="button" class="community-v2-page-btn ${i === current ? 'on' : ''}" data-page="${i}">${i + 1}</button>`;
        });

        if (totalPages > 1) {
            html += `<button type="button" class="community-v2-page-btn" data-page="${Math.min(Number(totalPages) - 1, current + 1)}" ${current >= Number(totalPages) - 1 ? 'disabled' : ''}>&gt;</button>`;
        }

        pager.innerHTML = html;

        pager.querySelectorAll('.community-v2-page-btn').forEach(btn => {
            btn.addEventListener('click', function () {
                if (this.disabled) return;
                window.loadCommunityPosts(Number(this.dataset.page || 0), true);
            });
        });

        tabEl.appendChild(pager);
    }


    window.loadCommunityPosts = async function (page = 0, reset = true) {
        const tab = getCurrentTabKey();
        const requestKey = `${tab}:${page}:${reset}`;
        if (window._communityPostsLoadingKey === requestKey) return;
        window._communityPostsLoadingKey = requestKey;

        removeCommunityV2Pager();

        if (typeof _commState !== 'undefined') {
            _commState.currentTab = tab;
            _commState.page = page;
        }

        const tabEl = document.getElementById('tab-' + tab);
        if (tabEl && reset) {
            tabEl.innerHTML = `<div style="padding:40px 20px;text-align:center;color:var(--text3);font-size:14px">불러오는 중...</div>`;
        }

        try {
            const sortVal = (typeof _commState !== 'undefined' && _commState.sortOrder) ? _commState.sortOrder : 'scrap';
            const res      = await api.get(`/api/posts?page=${page}&size=10&sort=${sortVal}&category=${tab}`);
            const pageData = extractPage(res);

            if (typeof window._renderPostList === 'function') {
                window._renderPostList(pageData.content, reset);
            }

            renderCommunityV2Pager(tab, pageData.number, pageData.totalPages);

            if (typeof window.loadCommunitySidePanels === 'function') window.loadCommunitySidePanels();
        } catch (e) {
            console.error('[community-v2] 카테고리 목록 조회 실패:', e);
            if (tabEl) tabEl.innerHTML = `<div style="padding:40px 20px;text-align:center;color:var(--coral);font-size:14px">게시글을 불러오지 못했습니다.</div>`;
        } finally {
            window._communityPostsLoadingKey = null;
        }
    };

})();


/* =============================================================================
 * community v2 — 상세 페이지 와이어프레임 보정
 * ============================================================================= */
(function () {
    'use strict';

    const { escapeHtml, formatDate, parseStyleTags, getRouteData, getAllPlaces } = window._commUtil;

    function removeBodyInlineTags() {
        const body  = document.getElementById('pr-body');
        if (!body) return;
        const first = body.firstElementChild;
        if (first && first.querySelector && first.querySelector('.post-cat')) first.remove();
    }

    function renderDetailMeta(post) {
        const metaEl = document.getElementById('pr-meta');
        if (!metaEl) return;
        const writer   = post.writerName || '사용자';
        const dateText = formatDate(post.createdAt);
        const views    = post.viewCount ?? post.views ?? 0;
        const likes    = post.likeCount ?? post.likes ?? 0;

        metaEl.innerHTML = `
            <span>${escapeHtml(writer)}</span>
            ${dateText ? `<span>${escapeHtml(dateText)}</span>` : ''}
            <span>👁 ${escapeHtml(views)}</span>
            <span>❤️ ${escapeHtml(likes)}</span>
        `;
    }

    function renderDetailCategory(post) {
        const catEl = document.getElementById('pr-cat');
        const tagEl = document.getElementById('pr-tag');
        if (catEl) catEl.textContent = post.catLabel || '여행 경로';
        if (tagEl) tagEl.style.display = 'none';
    }

    function renderTagsBeforePlan(post) {
        const badge = document.getElementById('pr-plan-badge');
        if (!badge) return;
        const tags = parseStyleTags(post.styleTags);
        const old  = document.getElementById('pr-detail-tags');
        if (old) old.remove();
        if (!tags.length) return;

        const tagBox = document.createElement('div');
        tagBox.id = 'pr-detail-tags';
        tagBox.className = 'review-detail-tags';
        tagBox.innerHTML = tags.map(tag => `<span>#${escapeHtml(tag)}</span>`).join('');
        badge.insertAdjacentElement('beforebegin', tagBox);
    }

    function getDayCount(post) {
        if (!post?.planStartDate || !post?.planEndDate) return '일정';
        const diff = Math.floor((new Date(post.planEndDate) - new Date(post.planStartDate)) / (1000 * 60 * 60 * 24)) + 1;
        return diff > 0 ? `${diff}일` : '일정';
    }

    function getPlanSummary(post, routeData) {
        const places     = getAllPlaces(routeData);
        const title      = post.planTitle || post.planDestination || '연동된 플랜';
        const styles     = post.planTravelStyles ? String(post.planTravelStyles).replaceAll(',', ' · ') : '여행';
        const transport  = post.planTransportType || '';
        const budget     = post.planBudget ? '₩' + Number(post.planBudget).toLocaleString('ko-KR') : '';
        return [title, places.length ? `${places.length}곳` : '', styles, transport, budget].filter(Boolean).join(' · ');
    }

    function renderPlanBadge(post, routeData) {
        const badge = document.getElementById('pr-plan-badge');
        if (!badge) return;
        if (!post.planId) { badge.style.display = 'none'; return; }

        badge.style.cssText = 'display:flex;align-items:center;justify-content:space-between;gap:12px';
        badge.innerHTML = `
            <div>
                <strong>🧳 연결된 플랜</strong>
                <span class="review-plan-summary">${escapeHtml(getPlanSummary(post, routeData))}</span>
            </div>
            <button id="btn-plan-preview" type="button" class="btn-plan-preview" onclick="openCommunityPlanPreview()">미리보기</button>
        `;
    }

    function getPlaceShortType(type) {
        if (type === 'stay') return '숙소';
        if (type === 'cafe' || type === 'breakfast') return '카페';
        if (type === 'food' || type === 'lunch' || type === 'dinner') return '맛집';
        if (type === 'tour') return '관광지';
        return '장소';
    }

    async function renderPlaceSnapshot(postId) {
        const placeList = document.getElementById('pr-place-list');
        if (!placeList) return;

        placeList.style.display = 'none';
        placeList.innerHTML = '';

        try {
            const res = await api.get(`/api/posts/${postId}/place-reviews`);
            const allReviews = Array.isArray(res?.data) ? res.data : (Array.isArray(res) ? res : []);

            // ① 게시글 작성자 본인이 쓴 리뷰만 필터
            const postAuthorId = window._currentPostDetail?.userId;
            const authorReviews = postAuthorId
                ? allReviews.filter(r => Number(r.writerId) === Number(postAuthorId))
                : allReviews; // 작성자 정보 없는 경우 fallback

            // ② placeId 기준 중복 제거 (createdAt desc = 최신 1건)
            const seenPlaceIds = new Set();
            const uniqueReviews = [];
            authorReviews.forEach(r => {
                if (!seenPlaceIds.has(r.placeId)) {
                    seenPlaceIds.add(r.placeId);
                    uniqueReviews.push(r);
                }
            });

            if (!uniqueReviews.length) return;

            placeList.style.display = 'block';
            placeList.innerHTML = `
                <div class="review-place-snapshot">
                    <h3>📍 방문 장소별 별점 & 한줄평</h3>
                    ${uniqueReviews.map(r => `
                        <div class="review-place-snapshot-row" style="cursor:pointer"
                             data-place-id="${r.placeId}"
                             data-place-name="${escapeHtml(r.placeName||'')}"
                             data-category="${r.category||'attraction'}"
                             onclick="_placeSnapshotClick(this)">
                            <div class="review-place-snapshot-top">
                                <strong class="review-place-snapshot-name">${escapeHtml(r.placeName || '')}</strong>
                                <b class="review-place-snapshot-stars">${escapeHtml(r.starsHtml || '')}</b>
                            </div>
                            ${r.comment ? `<div class="review-place-snapshot-comment">${escapeHtml(r.comment)}</div>` : ''}
                        </div>
                    `).join('')}
                </div>
            `;
        } catch (e) {
            console.error('[place-snapshot] 장소 리뷰 로드 실패', e);
        }
    }

    window.openCommunityPlaceSummary = function (placeName, placeType) {
        if (!placeName) return;
        if (typeof showMapPlacePopup === 'function') { showMapPlacePopup(placeName, placeType || 'tour'); return; }
        if (typeof toast === 'function') toast(`${placeName} 상세보기`);
    };

    async function beautifyReviewDetail() {
        const post = window._currentPostDetail;
        if (!post) return;

        const category = String(post.category || 'ROUTE').toUpperCase();

        if (category !== 'ROUTE') {
            ['pr-detail-tags'].forEach(id => { const el = document.getElementById(id); if (el) el.remove(); });
            // pr-plan-badge만 숨기고, pr-place-list는 장소 후기 표시를 위해 유지
            const planBadgeEl = document.getElementById('pr-plan-badge');
            if (planBadgeEl) { planBadgeEl.style.display = 'none'; planBadgeEl.innerHTML = ''; }
            renderDetailCategory(post);
            renderDetailMeta(post);
            // STAY/FOOD/TOUR/CAFE도 장소 별점 한줄평 표시
            renderPlaceSnapshot(post.postId);
            return;
        }

        const routeData = await getRouteData(post);
        removeBodyInlineTags();
        renderDetailCategory(post);
        renderDetailMeta(post);
        renderTagsBeforePlan(post);
        renderPlanBadge(post, routeData);
        renderPlaceSnapshot(post.postId);
    }

    const prevOpenPostDetail = window.openPostDetail;
    if (typeof prevOpenPostDetail === 'function') {
        window.openPostDetail = async function (postId) {
            const result = await prevOpenPostDetail.apply(this, arguments);
            try { await beautifyReviewDetail(); } catch (e) { console.warn('[community-v2] 와이어프레임 보정 실패:', e); }
            return result;
        };
    }

})();


/* =============================================================================
 * community v2 — 플랜 미리보기 모달 (Kakao 지도)
 * ============================================================================= */
(function () {
    'use strict';

    const { escapeHtml, parseRouteData, getRouteData, getAllPlaces } = window._commUtil;

    function getPinColor(type) {
        if (type === 'stay') return '#2D9E8A';
        if (['food','lunch','dinner','breakfast'].includes(type)) return '#F87171';
        if (type === 'cafe') return '#7C3AED';
        if (type === 'tour') return '#22B5C4';
        return '#2D9E8A';
    }

    function getDayColor(day) {
        return ['#2D9E8A','#A78BFA','#22B5C4','#F5A623','#F472B6'][(Number(day || 1) - 1) % 5];
    }

    function getDayCount(post) {
        if (!post?.planStartDate || !post?.planEndDate) return '일정';
        const diff = Math.floor((new Date(post.planEndDate) - new Date(post.planStartDate)) / (1000 * 60 * 60 * 24)) + 1;
        return diff > 0 ? `${diff}일` : '일정';
    }

    function formatBudget(routeData, fallbackBudget) {
        const total = routeData.reduce((sum, day) => {
            const n = Number(String(day.budget || '').replace(/[^\d]/g, ''));
            return sum + (Number.isNaN(n) ? 0 : n);
        }, 0);
        if (total > 0) return `₩${total.toLocaleString('ko-KR')}~`;
        if (fallbackBudget) { const n = Number(fallbackBudget); if (!Number.isNaN(n)) return `₩${n.toLocaleString('ko-KR')}~`; }
        return '예산 정보 없음';
    }

    function ensurePreviewModal() {
        let overlay = document.getElementById('communityPlanPreviewOverlay');
        if (overlay) {
            // 이미 존재하면 innerHTML 재생성 없이 바로 반환
            return overlay;
        }

        overlay = document.createElement('div');
        overlay.id = 'communityPlanPreviewOverlay';
        overlay.className = 'community-plan-preview-overlay';
        document.body.appendChild(overlay);

        overlay.innerHTML = `
            <div class="community-plan-preview-modal">
                <button class="community-plan-preview-close" type="button" onclick="closeCommunityPlanPreview()">×</button>
                <div class="community-plan-preview-badges"><span>시즌 큐레이션</span><span>초여름</span></div>
                <h2 id="cpp-title">플랜 미리보기</h2>
                <div class="community-plan-preview-stats">
                    <div><strong id="cpp-budget">예산 정보 없음</strong><span>예산 합산 검증액</span></div>
                    <div><strong id="cpp-place-count">0곳</strong><span>방문 장소</span></div>
                    <div><strong id="cpp-period">일정</strong><span>일정</span></div>
                </div>
                <div class="community-plan-preview-map">
                    <div id="communityPlanKakaoMap" style="width:100%;height:100%;border-radius:14px"></div>
                </div>
                <div class="community-plan-preview-section">
                    <h3>🏨 숙소 스냅샷</h3>
                    <p id="cpp-stay">연동된 플랜의 숙소 정보가 없습니다.</p>
                </div>
                <div class="community-plan-preview-section">
                    <h3>🍽 맛집 리스트 핵심글</h3>
                    <div id="cpp-places"></div>
                </div>
                <div class="community-plan-preview-actions">
                    <button type="button" class="cpp-main-btn" id="cpp-go-planner-btn">→ 해당 경로로 여행 계획하기</button>
                    <button type="button" class="cpp-sub-btn" id="cpp-preview-scrap-btn">📌 스크랩</button>
                </div>
            </div>
        `;

        // onclick은 최초 생성 시 단 한 번만 등록
        const goBtn = document.getElementById('cpp-go-planner-btn');
        if (goBtn) goBtn.onclick = function () {
            const token = localStorage.getItem('accessToken');
            if (!token) {
                closeCommunityPlanPreview();
                if (typeof openModal === 'function') openModal('modal-auth');
                const post = window._currentPostDetail;
                const dest = post?.planDestination || '';
                const parts = dest ? dest.split('|') : [];
                window._pendingLoginThenPlanner = { prov: parts[0]||'', city: parts[1]||'' };
                return;
            }

            closeCommunityPlanPreview();
            const post = window._currentPostDetail;
            const dest = post?.planDestination || '';
            const parts = dest ? dest.split('|') : [];
            const prov = parts[0] || '';
            const city = parts[1] || '';

            window._pendingCommunityDest = { prov, city };
            if (typeof resetPlannerForm === 'function') resetPlannerForm();
            window._currentTripId = null;
            if (typeof go === 'function') go('planner', false);
            if (typeof goPlanStep === 'function') goPlanStep(1);

            setTimeout(function () {
                const pending = window._pendingCommunityDest;
                if (!pending || !pending.prov) return;
                const provSel = document.getElementById('dest-prov');
                if (!provSel) return;
                provSel.value = pending.prov;
                if (typeof updateCityDest === 'function') updateCityDest(provSel);
                if (pending.city) {
                    setTimeout(function () {
                        const cityEl = document.getElementById('dest-city');
                        if (!cityEl) return;
                        const cityOpts = Array.from(cityEl.options);
                        const cityMatch = cityOpts.find(o => o.value === pending.city || o.text === pending.city);
                        if (cityMatch) cityEl.value = cityMatch.value;
                        window._pendingCommunityDest = null;
                    }, 50);
                } else {
                    window._pendingCommunityDest = null;
                }
            }, 0);
        };

        const scrapBtn = document.getElementById('cpp-preview-scrap-btn');
        if (scrapBtn) {
            scrapBtn.onclick = function () { window.scrapCurrentPreviewPlan(); };
            scrapBtn.onmouseenter = function () { setPreviewScrapButtonHover(true); };
            scrapBtn.onmouseleave = function () { setPreviewScrapButtonHover(false); };
        }

        return overlay;
    }

    function renderPreviewInfo(post, routeData) {
        const places = getAllPlaces(routeData);
        document.getElementById('cpp-title').textContent       = post.planTitle || post.planDestination || '연동된 여행 플랜';
        document.getElementById('cpp-budget').textContent      = formatBudget(routeData, post.planBudget);
        document.getElementById('cpp-place-count').textContent = `${places.length}곳`;
        document.getElementById('cpp-period').textContent      = getDayCount(post);

        const stay = places.find(p => p.type === 'stay');
        document.getElementById('cpp-stay').textContent = stay
            ? `${stay.name} · ${stay.sub || '숙소'}`
            : `${post.planTitle || '연동된 플랜'}입니다.`;
    }

    function renderPreviewList(routeData) {
        const box = document.getElementById('cpp-places');
        if (!box) return;
        const html = [];
        routeData.forEach(day => {
            html.push(`<div class="cpp-day-title">${escapeHtml(day.label || `Day ${day.day}`)}</div>`);
            (day.places || []).forEach(p => {
                if (p.transit) { html.push(`<div class="cpp-transit-row">${escapeHtml(p.transit)}</div>`); return; }
                html.push(`
                    <div class="cpp-place-row">
                        <span>${escapeHtml(p.icon || '📍')}</span>
                        <strong>${escapeHtml(p.name || '장소')}</strong>
                        <em>${escapeHtml(p.time || p.sub || '플랜 장소')}</em>
                    </div>
                `);
            });
        });
        box.innerHTML = html.join('');
    }

    function searchPlaceByName(place, callback) {
        if (place.coord) { callback(place.coord); return; }
        if (!kakao?.maps?.services?.Places) { callback(null); return; }
        new kakao.maps.services.Places().keywordSearch(place.name, function (data, status) {
            if (status === kakao.maps.services.Status.OK && data?.length) {
                callback({ lat: Number(data[0].y), lng: Number(data[0].x) });
            } else {
                callback(null);
            }
        });
    }

    function renderActualKakaoMap(routeData) {
        const container = document.getElementById('communityPlanKakaoMap');
        if (!container) return;
        const places = getAllPlaces(routeData);

        if (!places.length) { container.innerHTML = `<div class="cpp-map-loading">표시할 장소가 없습니다.</div>`; return; }
        if (typeof kakao === 'undefined' || !kakao.maps) { container.innerHTML = `<div class="cpp-map-loading">Kakao 지도 SDK를 불러오지 못했습니다.</div>`; return; }

        kakao.maps.load(function () {
            container.innerHTML = '';
            const map    = new kakao.maps.Map(container, { center: new kakao.maps.LatLng(37.5665, 126.9780), level: 6 });
            const bounds = new kakao.maps.LatLngBounds();
            const placedByDay = {};
            let resolvedCount = 0;

            places.forEach(place => {
                searchPlaceByName(place, function (coord) {
                    resolvedCount++;
                    if (coord) {
                        const position = new kakao.maps.LatLng(coord.lat, coord.lng);
                        bounds.extend(position);
                        if (!placedByDay[place.day]) placedByDay[place.day] = [];
                        placedByDay[place.day].push({ ...place, position });

                        new kakao.maps.CustomOverlay({
                            map, position, xAnchor: 0, yAnchor: 0,
                            content: `
                                <div style="cursor:pointer;position:relative;width:0;height:0;">
                                    <div style="position:absolute;left:-18px;top:-18px;width:36px;height:36px;box-sizing:border-box;border-radius:50%;background:${getPinColor(place.type)};display:flex;align-items:center;justify-content:center;font-size:16px;box-shadow:0 2px 8px rgba(0,0,0,.3);border:2.5px solid #fff;z-index:2;">${escapeHtml(place.icon || '📍')}</div>
                                    <div style="position:absolute;top:20px;left:0;transform:translateX(-50%);background:#fff;border-radius:8px;padding:3px 8px;font-size:10px;font-weight:800;color:#111;box-shadow:0 2px 6px rgba(0,0,0,.3);white-space:nowrap;border:1px solid rgba(0,0,0,.08);z-index:1;">${escapeHtml(place.name)}</div>
                                </div>`
                        });
                    }

                    if (resolvedCount === places.length) {
                        Object.keys(placedByDay).forEach(day => {
                            const path = placedByDay[day].map(p => p.position);
                            if (path.length < 2) return;
                            new kakao.maps.Polyline({ map, path, strokeWeight: 5, strokeColor: getDayColor(day), strokeOpacity: 0.65, strokeStyle: 'solid' });
                        });

                        if (Object.keys(placedByDay).length > 0) {
                            map.setBounds(bounds);
                            setTimeout(() => { map.relayout(); map.setBounds(bounds); }, 150);
                        } else {
                            container.innerHTML = `<div class="cpp-map-loading">지도에 표시할 장소를 찾지 못했습니다.</div>`;
                        }
                    }
                });
            });
        });
    }

    function extractPreviewScrapList(res) {
        if (Array.isArray(res)) return res;
        if (Array.isArray(res?.data)) return res.data;
        if (Array.isArray(res?.data?.content)) return res.data.content;
        if (Array.isArray(res?.content)) return res.content;
        return [];
    }

    function getPreviewScrapPostId(item) {
        return item?.postId || item?.id || item?.post?.postId || item?.post?.id || null;
    }

    function setPreviewScrapButtonState(scrapped) {
        const btn = document.getElementById('cpp-preview-scrap-btn');
        if (!btn) return;

        const activeColor = '#4CA693';
        const active = !!scrapped;
        btn.dataset.scrapped = active ? 'true' : 'false';
        btn.textContent = active ? '📌 스크랩됨' : '📌 스크랩';
        btn.style.background = active ? activeColor : '#fff';
        btn.style.borderColor = activeColor;
        btn.style.color = active ? '#fff' : activeColor;
    }

    function setPreviewScrapButtonHover(hovered) {
        const btn = document.getElementById('cpp-preview-scrap-btn');
        if (!btn) return;

        const activeColor = '#4CA693';
        const active = btn.dataset.scrapped === 'true';

        if (active || hovered) {
            btn.style.background = activeColor;
            btn.style.borderColor = activeColor;
            btn.style.color = '#fff';
        } else {
            btn.style.background = '#fff';
            btn.style.borderColor = activeColor;
            btn.style.color = activeColor;
        }
    }

    async function syncPreviewScrapButtonState(postId) {
        if (!postId) {
            setPreviewScrapButtonState(false);
            return;
        }

        const token = window._commUtil?.getAccessToken ? window._commUtil.getAccessToken() : null;
        if (!token) {
            setPreviewScrapButtonState(false);
            return;
        }

        try {
            const res = await requestJson('/api/posts/scrapped', { method: 'GET', headers: authHeaders(false) });
            const list = extractPreviewScrapList(res);
            const isScrapped = list.some(function (item) {
                return String(getPreviewScrapPostId(item)) === String(postId);
            });
            setPreviewScrapButtonState(isScrapped);
        } catch (e) {
            console.warn('[community-v2] 미리보기 스크랩 상태 조회 실패:', e);
            setPreviewScrapButtonState(false);
        }
    }

    window.openCommunityPlanPreview = async function () {
        const postId = window._commUtil.getCurrentPostId();
        if (!postId) { if (typeof toast === 'function') toast('연동된 플랜 정보를 찾을 수 없습니다.'); return; }

        const res  = await api.get(`/api/posts/${postId}`);
        const post = (res && res.success !== false && res.data) ? res.data : null;

        if (!post || !post.planId) { if (typeof toast === 'function') toast('연동된 플랜 정보를 찾을 수 없습니다.'); return; }

        window._currentPostDetail = post;

        const overlay   = ensurePreviewModal();
        overlay.classList.add('open');
        setPreviewScrapButtonState(false);

        const routeData = await getRouteData(post);
        renderPreviewInfo(post, routeData);
        renderPreviewList(routeData);
        renderActualKakaoMap(routeData);
        await syncPreviewScrapButtonState(post.postId || post.id || postId);
    };

    window.closeCommunityPlanPreview = function () {
        const overlay = document.getElementById('communityPlanPreviewOverlay');
        if (overlay) overlay.classList.remove('open');
    };

    window.scrapCurrentPreviewPlan = async function () {
        if (!window._commUtil || !window._commUtil.requireLogin()) return;

        const post = window._currentPostDetail || {};
        const postId = post.postId || post.id || window._currentPostId || window._openedPostId;

        if (!postId) {
            if (typeof toast === 'function') toast('스크랩할 여행 경로 정보를 찾을 수 없습니다.');
            return;
        }

        const btn = document.getElementById('cpp-preview-scrap-btn');
        if (btn) btn.disabled = true;

        try {
            const res = await api.post('/api/posts/' + postId + '/scraps?category=ROUTE', {});
            if (!res || res.success === false) {
                if (typeof toast === 'function') toast(res?.message || '스크랩 처리에 실패했습니다.');
                return;
            }

            const scrapped = res.data === true;
            setPreviewScrapButtonState(scrapped);

            if (scrapped) {
                if (typeof toast === 'function') toast('마이페이지 → 스크랩한 여행 경로에 추가되었습니다.');
            } else {
                if (typeof toast === 'function') toast('스크랩이 취소되었습니다.');
            }

            if (window._myCommunityPagingState && window._myCommunityPagingState['scrap-route']) {
                window._myCommunityPagingState['scrap-route'].page = 0;
            }
            if (typeof window.loadMyRouteScrap === 'function') {
                const routeSection = document.getElementById('my-scrap-route');
                if (routeSection && routeSection.style.display !== 'none') {
                    await window.loadMyRouteScrap(0);
                }
            }
        } catch (e) {
            console.error('[community-v2] 미리보기 플랜 스크랩 처리 실패:', e);
            if (typeof toast === 'function') toast(e.message || '스크랩 처리에 실패했습니다.');
        } finally {
            if (btn) btn.disabled = false;
        }
    };

})();


/* =============================================================================
 * community v2 — 사이드바 인기 태그 / AI 추천
 * ============================================================================= */
(function () {
    'use strict';

    const { escapeHtml, parseStyleTags, extractPosts } = window._commUtil;

    async function fetchCommunityPostsForSide() {
        const res = await api.get('/api/posts?page=0&size=50&sort=scrap&category=route');
        return extractPosts(res);
    }

    function renderPopularTags(posts) {
        const box = document.getElementById('popular-tags-list');
        if (!box) return;

        const tagCount = {};
        posts.forEach(post => {
            parseStyleTags(post.styleTags).forEach(tag => {
                if (!tag) return;
                tagCount[tag] = (tagCount[tag] || 0) + 1;
            });
        });

        let tags = Object.entries(tagCount)
            .sort((a, b) => b[1] - a[1])
            .map(([name, count]) => ({ name, count }))
            .slice(0, 9);

        if (!tags.length) {
            tags = ['힐링','맛집','카페','가성비','바다','커뮤니티'].map(name => ({ name, count: 1 }));
        }

        box.innerHTML = tags.map(t => `
            <button type="button" class="chip chip-sm community-side-tag" title="${escapeHtml(t.count)}개 글"
                    onclick="handleCommunitySideTag('${escapeHtml(t.name)}', this)">
                #${escapeHtml(t.name)}
            </button>
        `).join('');
    }

    function getPostScore(post) {
        return (post.likes ?? post.likeCount ?? 0) * 3
            + (post.scraps ?? post.scrapCount ?? 0) * 4
            + (post.views ?? post.viewCount ?? 0) * 0.1;
    }

    function renderAiRecommendations(posts) {
        const titleEl = document.getElementById('ai-reco-title');
        const box     = document.getElementById('ai-reco-list');
        if (!box) return;
        if (titleEl) titleEl.textContent = '지난 제주 힐링 여행 기반 추천';

        const recommendations = [...posts]
            .filter(p => p.postId && p.title)
            .sort((a, b) => getPostScore(b) - getPostScore(a));

        if (!recommendations.length) {
            box.innerHTML = `<div class="community-ai-simple-card"><div class="community-ai-empty">추천할 게시글이 아직 없습니다.</div></div>`;
            return;
        }

        const post     = recommendations[0];
        const tags     = parseStyleTags(post.styleTags);
        const likes    = post.likes ?? post.likeCount ?? 0;
        const scraps   = post.scraps ?? post.scrapCount ?? 0;
        const matchCount = Math.max(1, tags.length + (likes > 0 ? 1 : 0) + (scraps > 0 ? 1 : 0));

        box.innerHTML = `
            <div class="community-ai-simple-card" onclick="openPostDetail(${post.postId})">
                <div class="community-ai-simple-cat">${escapeHtml(post.catLabel || '여행 경로')}</div>
                <div class="community-ai-simple-title">${escapeHtml(post.title)}</div>
                <div class="community-ai-simple-match">취향 일치 ${escapeHtml(matchCount)}개</div>
            </div>
        `;
    }

    window.handleCommunitySideTag = function (tag, btn) {
        if (typeof filterByTag === 'function') { filterByTag(tag, btn); return; }
        const q = String(tag || '').toLowerCase();
        document.querySelectorAll('.comm-post-item').forEach(item => {
            item.style.display = (item.getAttribute('data-tags') || '').toLowerCase().includes(q) ? '' : 'none';
        });
    };

    window.loadCommunitySidePanels = async function () {
        if (window._communitySidePanelsLoading) return;
        const tagBox  = document.getElementById('popular-tags-list');
        const recoBox = document.getElementById('ai-reco-list');
        if (!tagBox && !recoBox) return;

        window._communitySidePanelsLoading = true;
        try {
            const posts = await fetchCommunityPostsForSide();
            renderPopularTags(posts);
            renderAiRecommendations(posts);
        } catch (e) {
            console.error('[community-v2] 사이드바 렌더링 실패:', e);
            if (tagBox)  tagBox.innerHTML  = `<div style="font-size:12px;color:var(--text3);grid-column:1/-1">인기 태그를 불러오지 못했습니다.</div>`;
            if (recoBox) recoBox.innerHTML = `<div style="font-size:12px;color:var(--text3)">추천 정보를 불러오지 못했습니다.</div>`;
        } finally {
            window._communitySidePanelsLoading = false;
        }
    };

})();


/* =============================================================================
 * community v2 — 상세 페이지 새로고침 복구
 * ============================================================================= */
(function () {
    'use strict';

    window.restoreCommunityReviewDetail = function () {
        const savedPostId = sessionStorage.getItem('communityCurrentPostId');
        if (!savedPostId) return;
        if (window._currentPostDetail && String(window._currentPostId) === String(savedPostId)) return;

        const reviewPage = document.getElementById('page-review');
        if (!reviewPage) return;
        const style = window.getComputedStyle(reviewPage);
        if (style.display === 'none' || style.visibility === 'hidden') return;

        if (typeof window.openPostDetail === 'function') window.openPostDetail(savedPostId);
    };

    document.addEventListener('DOMContentLoaded', function () {
        setTimeout(window.restoreCommunityReviewDetail, 500);
        setTimeout(window.restoreCommunityReviewDetail, 1200);
    });

    const originalGo = window.go;
    if (typeof originalGo === 'function' && !originalGo.__communityRestoreWrapped) {
        window.go = function (id) {
            const result = originalGo.apply(this, arguments);
            if (id === 'review') setTimeout(window.restoreCommunityReviewDetail, 300);
            return result;
        };
        window.go.__communityRestoreWrapped = true;
    }

})();


/* =============================================================================
 * community v2 — 검색
 * ============================================================================= */
(function () {
    'use strict';

    function getCurrentTabKeyForSearch() {
        const activeTab = document.querySelector('#commTabs .comm-tab.on');
        if (activeTab) {
            const text = activeTab.textContent.trim();
            if (text.includes('숙소'))    return 'stay';
            if (text.includes('맛집'))    return 'food';
            if (text.includes('관광지'))  return 'tour';
            if (text.includes('카페'))    return 'cafe';
            if (text.includes('여행 경로')) return 'route';
        }
        if (typeof _commState !== 'undefined' && _commState.currentTab) return _commState.currentTab;
        return 'route';
    }

    function communityV2Search() {
        const typeEl  = document.getElementById('searchType');
        const inputEl = document.getElementById('searchInp');
        const type    = typeEl ? typeEl.value : 'title';
        const q       = inputEl ? inputEl.value.trim().toLowerCase() : '';

        const tabEl = document.getElementById('tab-' + getCurrentTabKeyForSearch());
        if (!tabEl) return;

        if (!q) {
            let count = 0;
            tabEl.querySelectorAll('.comm-post-item').forEach(item => { item.style.display = ''; count++; });
            const old = document.getElementById('community-search-empty');
            if (old) old.remove();
            if (typeof toast === 'function') toast(`전체 목록을 표시합니다. (${count}건)`);
            return;
        }

        const old = document.getElementById('community-search-empty');
        if (old) old.remove();

        let found = 0;
        tabEl.querySelectorAll('.comm-post-item').forEach(item => {
            const title   = (item.querySelector('.post-ttl')?.textContent || '').toLowerCase();
            const content = (item.getAttribute('data-content') || '').toLowerCase();
            const author  = (item.getAttribute('data-author') || '').toLowerCase();
            const tags    = (item.getAttribute('data-tags') || '').toLowerCase();

            const match = type === 'title'   ? title.includes(q)
                : type === 'content' ? content.includes(q)
                    : type === 'author'  ? author.includes(q)
                        : type === 'tag'     ? tags.includes(q)
                            : false;

            item.style.display = match ? '' : 'none';
            if (match) found++;
        });

        if (found === 0) {
            const empty = document.createElement('div');
            empty.id = 'community-search-empty';
            empty.style.cssText = 'padding:40px 20px;text-align:center;color:var(--text3);font-size:14px';
            empty.textContent = '검색 결과가 없습니다.';
            tabEl.appendChild(empty);
        }

        if (typeof toast === 'function') toast(`"${q}" 검색 결과: ${found}건`);
    }

    function installCommunityV2Search() {
        window.doSearch = communityV2Search;

        const searchBtn = document.querySelector('.btn-search');
        if (searchBtn) searchBtn.onclick = function (e) { if (e) e.preventDefault(); communityV2Search(); };

        const searchInput = document.getElementById('searchInp');
        if (searchInput && !searchInput.__communityV2SearchBound) {
            searchInput.addEventListener('keydown', function (e) { if (e.key === 'Enter') { e.preventDefault(); communityV2Search(); } });
            searchInput.__communityV2SearchBound = true;
        }
    }

    installCommunityV2Search();
    document.addEventListener('DOMContentLoaded', function () {
        setTimeout(installCommunityV2Search, 300);
        setTimeout(installCommunityV2Search, 1000);
        setTimeout(installCommunityV2Search, 2000);
    });
    window.addEventListener('load', () => setTimeout(installCommunityV2Search, 300));

})();


/* =============================================================================
 * community v2 — 상세 CTA 카테고리별 표시
 * ============================================================================= */
(function () {
    'use strict';

    function fixReviewCtaByCategory() {
        const post = window._currentPostDetail;
        const cta  = document.querySelector('#page-review .review-cta');
        if (!cta || !post) return;
        const category = String(post.category || 'ROUTE').toUpperCase();
        cta.style.display = category === 'ROUTE' ? '' : 'none';
    }

    const prevOpenPostDetailForCta = window.openPostDetail;
    if (typeof prevOpenPostDetailForCta === 'function' && !prevOpenPostDetailForCta.__communityCtaWrapped) {
        window.openPostDetail = async function (postId) {
            const result = await prevOpenPostDetailForCta.apply(this, arguments);
            setTimeout(fixReviewCtaByCategory, 50);
            return result;
        };
        window.openPostDetail.__communityCtaWrapped = true;
    }

})();


/* =============================================================================
 * community v2 — 장소 탭 (카드 목록 / 후기 상세 / 별점 / 장소 리뷰 작성)
 * ============================================================================= */
(function () {
    'use strict';

    const { escapeHtml } = window._commUtil;

    const PLACE_TABS = { stay: 'stay', food: 'food', tour: 'tour', cafe: 'cafe' };
    const TYPE_ICON  = { stay: '🏨', food: '🍽️', cafe: '☕', tour: '🎡' };
    const TYPE_CSS   = { stay: 'pr-stay', food: 'pr-food', cafe: 'pr-cafe', tour: 'pr-tour' };

    function starsHtml(rating) {
        let s = '';
        for (let i = 1; i <= 5; i++) s += i <= rating ? '★' : '☆';
        return s;
    }

    function extractList(res) {
        if (Array.isArray(res?.data)) return res.data;
        if (Array.isArray(res)) return res;
        return [];
    }

    const PLACE_PAGE_SIZE = 10;
    const PLACE_MAX_PAGE_PROBE = 50;
    const _loading = {};
    window._placeTabPageState = window._placeTabPageState || {};

    function getPlaceSortValue() {
        return (typeof _commState !== 'undefined' && _commState.sortOrder)
            ? _commState.sortOrder
            : 'saved';
    }

    function getPlacePageState(type) {
        window._placeTabPageState[type] = window._placeTabPageState[type] || {
            page: 0,
            totalPages: 1,
            loadedTotal: false,
            sort: getPlaceSortValue()
        };
        return window._placeTabPageState[type];
    }

    async function requestPlaceCardList(type, page) {
        const sortVal = getPlaceSortValue();
        const pageNo = Number(page || 0);
        const res = await api.get(`/api/places?category=${type}&page=${pageNo}&size=${PLACE_PAGE_SIZE}&sort=${sortVal}`);
        return extractList(res);
    }

    async function calculatePlaceTotalPages(type, firstPageList) {
        const state = getPlacePageState(type);
        const sortVal = getPlaceSortValue();

        if (state.loadedTotal && state.sort === sortVal) {
            return state.totalPages || 1;
        }

        state.sort = sortVal;
        let totalPages = firstPageList && firstPageList.length ? 1 : 0;

        if (firstPageList && firstPageList.length >= PLACE_PAGE_SIZE) {
            for (let page = 1; page < PLACE_MAX_PAGE_PROBE; page++) {
                const list = await requestPlaceCardList(type, page);
                if (!list.length) break;
                totalPages = page + 1;
                if (list.length < PLACE_PAGE_SIZE) break;
            }
        }

        state.totalPages = Math.max(totalPages, 1);
        state.loadedTotal = true;
        return state.totalPages;
    }

    function removePlacePager(type) {
        const tabEl = document.getElementById('tab-' + type);
        if (!tabEl) return;
        tabEl.querySelectorAll('.place-v2-pagination').forEach(el => el.remove());
    }

    function renderPlacePager(type, currentPage, totalPages) {
        const tabEl = document.getElementById('tab-' + type);
        if (!tabEl) return;

        removePlacePager(type);
        if (!totalPages || totalPages <= 1) return;

        const pager = document.createElement('div');
        pager.className = 'community-v2-pagination place-v2-pagination';
        pager.dataset.placePagerFor = type;

        const current = Math.min(Math.max(Number(currentPage) || 0, 0), Number(totalPages) - 1);
        const pageIndexes = window._commUtil.getPagerWindow(current, totalPages, 5);

        let html = '';
        html += `<button type="button" class="community-v2-page-btn place-v2-page-btn" data-page="${Math.max(0, current - 1)}" ${current === 0 ? 'disabled' : ''}>&lt;</button>`;

        pageIndexes.forEach(function (i) {
            html += `<button type="button" class="community-v2-page-btn place-v2-page-btn ${i === current ? 'on' : ''}" data-page="${i}">${i + 1}</button>`;
        });

        html += `<button type="button" class="community-v2-page-btn place-v2-page-btn" data-page="${Math.min(Number(totalPages) - 1, current + 1)}" ${current >= Number(totalPages) - 1 ? 'disabled' : ''}>&gt;</button>`;

        pager.innerHTML = html;

        pager.querySelectorAll('.place-v2-page-btn').forEach(btn => {
            btn.addEventListener('click', function () {
                if (this.disabled) return;
                window._loadPlaceCards(type, Number(this.dataset.page || 0), true);
            });
        });

        tabEl.appendChild(pager);
    }


    window._loadPlaceCards = async function loadPlaceCards(type, page, reset) {
        const pageNo = Number(page || 0);
        const sortVal = getPlaceSortValue();
        const key = type + ':' + pageNo + ':' + sortVal;
        if (_loading[key]) return;
        _loading[key] = true;

        const tabEl = document.getElementById('tab-' + type);
        if (!tabEl) { _loading[key] = false; return; }

        const state = getPlacePageState(type);
        if (state.sort !== sortVal || pageNo === 0) {
            state.sort = sortVal;
            state.loadedTotal = false;
            state.totalPages = 1;
        }
        state.page = pageNo;

        if (reset !== false) tabEl.innerHTML = '<div class="comm-empty">불러오는 중...</div>';

        try {
            const list = await requestPlaceCardList(type, pageNo);
            const totalPages = await calculatePlaceTotalPages(type, pageNo === 0 ? list : null);
            tabEl.innerHTML = '';

            if (!list.length) {
                tabEl.innerHTML = '<div class="comm-empty">등록된 장소가 없습니다.</div>';
                return;
            }

            const frag = document.createDocumentFragment();
            list.forEach(function (card) {
                const el = document.createElement('div');
                el.className = 'place-card-item';
                el.innerHTML = [
                    `<div class="place-card-info">`,
                    `  <span class="place-card-icon ${TYPE_CSS[card.category] || 'pr-tour'}">${TYPE_ICON[card.category] || '📍'}</span>`,
                    `  <span class="place-card-name">${escapeHtml(card.name)}</span>`,
                    `</div>`,
                    `<div class="place-card-meta">`,
                    `  <span class="place-card-stars">${starsHtml(Math.round(card.avgRating || 0))}</span>`,
                    `  <span class="place-card-avg">${(card.avgRating || 0).toFixed(1)}</span>`,
                    `  <span class="place-card-count">📌 ${card.reviewCount || 0}번 담김</span>`,
                    `  <span class="place-card-scrap">🔖 ${card.scrapCount || 0}</span>`,
                    `</div>`
                ].join('');
                el.addEventListener('click', function () {
                    openPlaceReviews(card.placeId, card.name, type, card.avgRating, card.reviewCount);
                });
                frag.appendChild(el);
            });
            tabEl.appendChild(frag);
            renderPlacePager(type, pageNo, totalPages);

        } catch (e) {
            console.error('[place-tab] 장소 카드 로드 실패:', e);
            tabEl.innerHTML = '<div class="comm-empty">장소 정보를 불러오지 못했습니다.</div>';
        } finally {
            _loading[key] = false;
        }
    };

    window._openPlaceReviews = async function(placeId, placeName, type, avgRating, reviewCount) {
        await openPlaceReviews(placeId, placeName, type, avgRating, reviewCount);
    };

    async function openPlaceReviews(placeId, placeName, type, avgRating, reviewCount) {
        const tabEl = document.getElementById('tab-' + type);
        if (!tabEl) return;

        /* 후기 패널에서는 검색/정렬 바를 숨김 */
        const searchBar = document.querySelector('#page-community .search-bar');
        if (searchBar) searchBar.style.display = 'none';

        tabEl.innerHTML = '<div class="comm-empty">불러오는 중...</div>';

        try {
            const res     = await api.get(`/api/places/${placeId}/reviews`);
            const reviews = extractList(res);
            tabEl.innerHTML = '';

            const wrap = document.createElement('div');
            wrap.className = 'place-review-wrap';

            const back = document.createElement('button');
            back.className = 'place-back-btn'; back.textContent = '← 목록으로';
            back.addEventListener('click', function () {
                /* 목록으로 돌아오면 검색/정렬 바 복원 */
                const sb = document.querySelector('#page-community .search-bar');
                if (sb) sb.style.display = '';
                const returnPage = window._placeTabPageState?.[type]?.page || 0;
                window._loadPlaceCards(type, returnPage, true);
            });
            wrap.appendChild(back);

            /* 평균 별점: 인자로 안 넘어오면(예: 메인페이지에서 진입) 후기들의 rating 평균을 직접 계산 */
            let avg;
            if (avgRating != null && Number(avgRating) > 0) {
                avg = Number(avgRating);
            } else {
                const rated = reviews.filter(function (r) { return r.rating != null && Number(r.rating) > 0; });
                avg = rated.length
                    ? rated.reduce(function (sum, r) { return sum + Number(r.rating); }, 0) / rated.length
                    : 0;
            }
            const cnt = reviewCount != null ? Number(reviewCount) : reviews.length;

            /* 이 장소를 이미 스크랩했는지 확인 (집합 우선, 없으면 API 1회 조회) */
            let alreadyScrapped = false;
            try {
                if (window._scrappedPlaceIds && window._scrappedPlaceIds.size > 0) {
                    alreadyScrapped = window._scrappedPlaceIds.has(String(placeId));
                } else {
                    const sres = await api.get('/api/scraps');
                    const slist = (sres && sres.success !== false) ? (sres.data || []) : [];
                    window._scrappedPlaceIds = window._scrappedPlaceIds || new Set();
                    slist.forEach(function (s) { if (s && s.placeId != null) window._scrappedPlaceIds.add(String(s.placeId)); });
                    alreadyScrapped = window._scrappedPlaceIds.has(String(placeId));
                }
            } catch (e) {}

            const header = document.createElement('div');
            header.className = 'place-review-header';
            const mapQuery = encodeURIComponent(placeName || '');
            header.innerHTML = [
                `<div class="place-review-name">📍 ${escapeHtml(placeName)}</div>`,
                `<div class="place-avg-stars">${starsHtml(Math.round(avg))}</div>`,
                `<div class="place-avg-score">${avg.toFixed(1)}</div>`,
                `<div class="place-avg-count">${cnt}개 후기</div>`,
                `<button class="place-review-scrap-btn${alreadyScrapped ? ' scrapped' : ''}"`,
                `        onclick="doCommPlaceScrapToggle(this, ${placeId},'${type}')">`,
                `  <span class="prs-star">★</span> <span class="prs-label">스크랩</span>`,
                `</button>`,
                `<div class="place-map-links">`,
                `  <div class="pml-title">지도 앱에서 보기</div>`,
                `  <div class="pml-btns">`,
                `    <a class="pml-btn pml-naver"  href="https://map.naver.com/v5/search/${mapQuery}" target="_blank" rel="noopener">네이버 지도</a>`,
                `    <a class="pml-btn pml-kakao"  href="https://map.kakao.com/?q=${mapQuery}" target="_blank" rel="noopener">카카오맵</a>`,
                `    <a class="pml-btn pml-google" href="https://www.google.com/maps/search/?api=1&query=${mapQuery}" target="_blank" rel="noopener">구글 지도</a>`,
                `  </div>`,
                `</div>`
            ].join('');
            wrap.appendChild(header);

            if (!reviews.length) {
                const empty = document.createElement('div');
                empty.className = 'comm-empty'; empty.textContent = '이 장소에 대한 후기가 없습니다.';
                wrap.appendChild(empty); tabEl.appendChild(wrap); return;
            }

            const listEl = document.createElement('div');
            listEl.className = 'place-review-list';

            reviews.forEach(function (r) {
                const el = document.createElement('div');
                el.className = 'place-review-item';
                const hasComment = r.comment && r.comment.trim();

                el.innerHTML = [
                    `<div class="pri-top">`,
                    `  <span class="pri-stars">${starsHtml(r.rating)}</span>`,
                    `  <span class="pri-writer">${escapeHtml(r.writerName || '')}</span>`,
                    `</div>`,
                    hasComment
                        ? `<div class="pri-comment">${escapeHtml(r.comment.trim())}</div>` +
                        `<button class="pri-toggle-btn" data-expanded="false">자세히 보기</button>`
                        : '',
                    r.postId ? `<button class="pri-goto">해당 후기로 이동 →</button>` : ''
                ].join('');

                if (hasComment) {
                    const commentEl = el.querySelector('.pri-comment');
                    const toggleBtn = el.querySelector('.pri-toggle-btn');
                    toggleBtn.addEventListener('click', function () {
                        const expanded = this.dataset.expanded === 'true';
                        commentEl.classList.toggle('expanded', !expanded);
                        this.textContent = expanded ? '자세히 보기' : '접기';
                        this.dataset.expanded = String(!expanded);
                    });
                    requestAnimationFrame(function () {
                        if (commentEl.scrollHeight <= commentEl.clientHeight + 2) toggleBtn.style.display = 'none';
                    });
                }

                if (r.postId) {
                    el.querySelector('.pri-goto').addEventListener('click', function () {
                        if (typeof window.openPostDetail === 'function') window.openPostDetail(r.postId);
                    });
                }

                listEl.appendChild(el);
            });

            wrap.appendChild(listEl);
            tabEl.appendChild(wrap);

        } catch (e) {
            console.error('[place-tab] 장소 후기 로드 실패:', e);
            tabEl.innerHTML = '<div class="comm-empty">후기를 불러오지 못했습니다.</div>';
        }
    }

    /* loadCommunityPosts — place 탭이면 loadPlaceCards로 위임 */
    const _origLoadPosts = window.loadCommunityPosts;
    window.loadCommunityPosts = async function (page, reset) {
        let cat = '';
        try { cat = (typeof _commState !== 'undefined') ? _commState.currentTab : ''; } catch (e) { cat = ''; }

        const placeType = PLACE_TABS[(cat || '').toLowerCase()];
        if (placeType) return window._loadPlaceCards(placeType, page || 0, reset !== false);

        return typeof _origLoadPosts === 'function' ? _origLoadPosts.apply(this, arguments) : undefined;
    };

    /* 별점 클릭 */
    window.setStars = function (btn, rating) {
        const sel = btn.closest('.star-sel');
        if (!sel) return;
        sel.querySelectorAll('.star-btn').forEach((b, i) => b.classList.toggle('lit', i < rating));
        sel.dataset.rating = rating;
    };

    /* 플랜 선택 시 방문 장소 섹션 표시 */
    window.onWritePlanSelect = async function (planId) {
        const section = document.getElementById('placeReviewsSection');
        const body    = document.getElementById('placeReviewsBody');
        if (!section || !body) return;

        if (!planId) {
            section.style.display = 'none';
            body.innerHTML = '<div class="plr-empty">플랜을 선택하면 방문 장소가 표시됩니다.</div>';
            return;
        }

        body.innerHTML = '<div class="plr-empty">장소를 불러오는 중...</div>';
        section.style.display = 'block';

        try {
            const res      = await api.get(`/api/trips/${planId}`);
            const plan     = (res && res.data) || res;
            const routeRaw = plan && (plan.routeJson || plan.route_json);

            if (!routeRaw) { body.innerHTML = '<div class="plr-empty">이 플랜에 경로 정보가 없습니다.</div>'; return; }

            let days;
            try { days = typeof routeRaw === 'string' ? JSON.parse(routeRaw) : routeRaw; }
            catch (_) { body.innerHTML = '<div class="plr-empty">경로 데이터를 읽을 수 없습니다.</div>'; return; }

            const places = [];
            (Array.isArray(days) ? days : []).forEach(day => {
                (day.places || []).forEach(p => { if (p.name) places.push(p); });
            });

            if (!places.length) { body.innerHTML = '<div class="plr-empty">등록된 장소가 없습니다.</div>'; return; }

            body.innerHTML = places.map(p => {
                const type     = (p.type || 'tour').toLowerCase();
                const safeName = p.name.replace(/"/g, '&quot;');
                return [
                    `<div class="plr-row" data-place-name="${safeName}" data-place-type="${type}">`,
                    `  <div class="plr-icon ${TYPE_CSS[type] || 'pr-tour'}">${TYPE_ICON[type] || '📍'}</div>`,
                    `  <div class="plr-name">${escapeHtml(p.name)}</div>`,
                    `  <div class="star-sel" data-rating="0">`,
                    [1,2,3,4,5].map(n => `<button class="star-btn" onclick="setStars(this,${n})">★</button>`).join(''),
                    `  </div>`,
                    `  <input class="one-line" placeholder="한줄평 (선택)" maxlength="200">`,
                    `</div>`
                ].join('');
            }).join('');

        } catch (e) {
            console.error('[place-review] 플랜 경로 로드 실패:', e);
            body.innerHTML = '<div class="plr-empty">장소 정보를 불러오지 못했습니다.</div>';
        }
    };

    /* checkAndOpenWrite 래핑 — 작성 모달 열 때 장소 섹션 초기화 */
    const _prevCheckAndOpenWrite = window.checkAndOpenWrite;
    if (typeof _prevCheckAndOpenWrite === 'function') {
        window.checkAndOpenWrite = function () {
            const section = document.getElementById('placeReviewsSection');
            const body    = document.getElementById('placeReviewsBody');
            if (section) section.style.display = 'none';
            if (body)    body.innerHTML = '<div class="plr-empty">플랜을 선택하면 방문 장소가 표시됩니다.</div>';
            return _prevCheckAndOpenWrite.apply(this, arguments);
        };
    }

    /* submitReview 래핑 — 장소 리뷰도 함께 저장 */
    const _origSubmitReview = window.submitReview;
    window.submitReview = async function () {
        const placeReviews = [];
        document.querySelectorAll('#placeReviewsBody .plr-row').forEach(function (row) {
            const sel    = row.querySelector('.star-sel');
            const rating = sel ? parseInt(sel.dataset.rating, 10) : 0;
            if (!rating) return;
            placeReviews.push({
                placeName: row.dataset.placeName || '',
                placeType: row.dataset.placeType || 'tour',
                rating,
                comment: ((row.querySelector('.one-line') || {}).value || '').trim() || null
            });
        });

        window._lastCreatedPostId = null;
        if (typeof _origSubmitReview === 'function') await _origSubmitReview();
        if (!placeReviews.length) return;

        const postId = window._lastCreatedPostId;
        if (!postId) return;

        try {
            await api.post(`/api/posts/${postId}/place-reviews`, { reviews: placeReviews });
        } catch (e) {
            console.error('[place-review] 장소 리뷰 저장 실패:', e);
        }
    };

    /* api.post 인터셉터 — 생성된 postId 캡처 */
    const _origApiPost = (typeof api !== 'undefined' && api.post) ? api.post.bind(api) : null;
    if (_origApiPost) {
        api.post = async function (url) {
            const res = await _origApiPost.apply(this, arguments);
            if (/^\/api\/posts$/.test(url)) {
                const pid = (res && typeof res.data === 'number') ? res.data
                    : (typeof res === 'number') ? res : null;
                if (pid) window._lastCreatedPostId = pid;
            }
            return res;
        };
    }

})();


/* =============================================================================
 * community v2 — 마이페이지 (목록, 좋아요/스크랩 상태, 수정 모달)
 * ============================================================================= */
(function () {
    'use strict';

    const { escapeHtml, extractPosts, getAccessToken, authHeaders, requestJson } = window._commUtil;

    /* ── 마이페이지 커뮤니티 활동 탭별 페이징 공통 유틸 ────────────────
     * 각 탭의 리스트 컨테이너 내부에만 페이징을 붙인다.
     * 다른 탭이나 다른 팀원 기능에 영향이 가지 않도록 page_mypage.html 구조는 건드리지 않는다.
     */
    const MY_COMMUNITY_PAGE_SIZE = 5;
    window._myCommunityPagingState = window._myCommunityPagingState || {};

    function getMyCommunityPagingState(key) {
        window._myCommunityPagingState[key] = window._myCommunityPagingState[key] || { page: 0 };
        return window._myCommunityPagingState[key];
    }

    function escapeJsString(value) {
        return String(value ?? '')
            .replace(/\\/g, '\\\\')
            .replace(/'/g, "\\'")
            .replace(/\r?\n/g, ' ');
    }

    function emptyMyPageMessage(message) {
        return '<div style="color:var(--text3);font-size:13px;padding:20px 0;text-align:center">' +
            escapeHtml(message) +
            '</div>';
    }

    function renderMyCommunityPager(listEl, key, totalCount, currentPage, loadPageFn) {
        if (!listEl) return;

        listEl.querySelectorAll('.my-community-pagination').forEach(el => el.remove());

        if (totalCount <= 0) return;
        const totalPages = Math.max(1, Math.ceil(totalCount / MY_COMMUNITY_PAGE_SIZE));
        const current = Math.min(Math.max(Number(currentPage) || 0, 0), totalPages - 1);
        const pageIndexes = window._commUtil.getPagerWindow(current, totalPages, 5);

        const pager = document.createElement('div');
        pager.className = 'community-v2-pagination my-community-pagination';
        pager.dataset.myPagerFor = key;

        let html = '';
        if (totalPages > 1) {
            html += '<button type="button" class="community-v2-page-btn my-community-page-btn" data-page="' + Math.max(0, current - 1) + '" ' + (current === 0 ? 'disabled' : '') + '>&lt;</button>';
        }

        pageIndexes.forEach(function (i) {
            html += '<button type="button" class="community-v2-page-btn my-community-page-btn' +
                (i === current ? ' on' : '') +
                '" data-page="' + i + '">' + (i + 1) + '</button>';
        });

        if (totalPages > 1) {
            html += '<button type="button" class="community-v2-page-btn my-community-page-btn" data-page="' + Math.min(totalPages - 1, current + 1) + '" ' + (current >= totalPages - 1 ? 'disabled' : '') + '>&gt;</button>';
        }

        pager.innerHTML = html;

        pager.querySelectorAll('.my-community-page-btn').forEach(btn => {
            btn.addEventListener('click', function () {
                if (this.disabled) return;
                const nextPage = Number(this.dataset.page || 0);
                getMyCommunityPagingState(key).page = nextPage;
                loadPageFn(nextPage);
            });
        });

        listEl.appendChild(pager);
    }


    function renderMyCommunityPagedList(listEl, key, list, emptyText, renderItem, loadPageFn, requestedPage) {
        if (!listEl) return;

        const items = Array.isArray(list) ? list : [];
        if (!items.length) {
            listEl.innerHTML = emptyMyPageMessage(emptyText);
            return;
        }

        const totalPages = Math.max(1, Math.ceil(items.length / MY_COMMUNITY_PAGE_SIZE));
        const state = getMyCommunityPagingState(key);
        let pageNo = Number(requestedPage ?? state.page ?? 0);

        if (Number.isNaN(pageNo) || pageNo < 0) pageNo = 0;
        if (pageNo >= totalPages) pageNo = totalPages - 1;
        state.page = pageNo;

        const start = pageNo * MY_COMMUNITY_PAGE_SIZE;
        const pageItems = items.slice(start, start + MY_COMMUNITY_PAGE_SIZE);

        listEl.innerHTML = pageItems.map(renderItem).join('');
        renderMyCommunityPager(listEl, key, items.length, pageNo, loadPageFn);
    }

    function normalizeMyPageListResponse(res) {
        if (Array.isArray(res)) return res;
        if (Array.isArray(res?.data)) return res.data;
        if (Array.isArray(res?.data?.content)) return res.data.content;
        if (Array.isArray(res?.content)) return res.content;
        return [];
    }

    function renderMyReviewCard(post) {
        const postId   = post.postId || post.id;
        const title    = post.title || '제목 없음';
        const likes    = post.likes ?? post.likeCount ?? 0;
        const views    = post.views ?? post.viewCount ?? 0;
        const category = post.catLabel || post.category || '후기';
        const catClass = post.catClass || '';

        return `
                <div class="post-card" data-my-post-id="${escapeHtml(postId)}" onclick="openPostDetail(${escapeHtml(postId)})">
                    <span class="post-cat ${escapeHtml(catClass)}">${escapeHtml(category)}</span>
                    <div class="post-ttl" style="margin-top:5px">${escapeHtml(title)}</div>
                    <div class="post-foot">
                        <div class="post-stats">
                            <span class="post-stat">❤️ ${escapeHtml(likes)}</span>
                            ${views ? `<span class="post-stat">👁 ${escapeHtml(views)}</span>` : ''}
                        </div>
                        <div style="display:flex;gap:6px">
                            <button class="btn-scrap" onclick="event.stopPropagation(); editMyPost(${escapeHtml(postId)})">✏️ 수정</button>
                            <button class="btn-scrap" style="color:var(--coral);border-color:var(--coral)" onclick="event.stopPropagation(); deleteMyPost(${escapeHtml(postId)})">삭제</button>
                        </div>
                    </div>
                </div>
            `;
    }

    function renderMyLikedReviewCard(post) {
        const postId   = post.postId || post.id;
        const title    = post.title || '제목 없음';
        const likes    = post.likes ?? post.likeCount ?? 0;
        const views    = post.views ?? post.viewCount ?? 0;
        const category = post.catLabel || post.category || '후기';
        const catClass = post.catClass || '';

        return `
                <div class="post-card" data-my-liked-post-id="${escapeHtml(postId)}" onclick="openPostDetail(${escapeHtml(postId)})">
                    <span class="post-cat ${escapeHtml(catClass)}">${escapeHtml(category)}</span>
                    <div class="post-ttl" style="margin-top:5px">${escapeHtml(title)}</div>
                    <div class="post-foot">
                        <div class="post-stats">
                            <span class="post-stat">❤️ ${escapeHtml(likes)}</span>
                            ${views ? `<span class="post-stat">👁 ${escapeHtml(views)}</span>` : ''}
                        </div>
                    </div>
                </div>
            `;
    }


    // ── 목록 조회 / 삭제 ───────────────────────────────────────────────

    window._renderMyReviews = async function (page) {
        const listEl = document.getElementById('my-reviews-list');
        if (!listEl) return;
        listEl.innerHTML = '<div style="color:var(--text3);font-size:13px;padding:20px 0;text-align:center">후기를 불러오는 중...</div>';

        let posts = [];
        try {
            const res = await requestJson('/api/users/me/posts', { method: 'GET', headers: authHeaders(false) });
            posts = extractPosts(res);
        } catch (e) {
            listEl.innerHTML = '<p style="color:var(--text3);font-size:13px">작성한 후기를 불러오지 못했습니다.</p>';
            if (typeof toast === 'function') toast(e.message || '작성한 후기를 불러오지 못했습니다.');
            return;
        }

        renderMyCommunityPagedList(
            listEl,
            'reviews',
            posts,
            '작성한 후기가 없습니다.',
            renderMyReviewCard,
            window._renderMyReviews,
            page
        );
    };

    window._renderMyLikedPosts = async function (page) {
        const listEl = document.getElementById('my-likes-list');
        if (!listEl) return;
        listEl.innerHTML = '<div style="color:var(--text3);font-size:13px;padding:20px 0;text-align:center">불러오는 중...</div>';

        let liked = [];
        try {
            const res = await requestJson('/api/users/me/liked-posts', { method: 'GET', headers: authHeaders(false) });
            liked = normalizeMyPageListResponse(res);
        } catch (e) {
            listEl.innerHTML = '<p style="color:var(--text3);font-size:13px">좋아요한 후기를 불러오지 못했습니다.</p>';
            if (typeof toast === 'function') toast(e.message || '좋아요한 후기를 불러오지 못했습니다.');
            return;
        }

        renderMyCommunityPagedList(
            listEl,
            'likes',
            liked,
            '좋아요한 후기가 없습니다.',
            renderMyLikedReviewCard,
            window._renderMyLikedPosts,
            page
        );
    };


    window.deleteMyPost = async function (postId) {
        if (!postId) { if (typeof toast === 'function') toast('게시글 정보를 찾을 수 없습니다.'); return; }
        if (!confirm('게시글을 삭제하시겠습니까?')) return;

        try {
            const res = await fetch(`/api/posts/${postId}`, { method: 'DELETE', headers: authHeaders(false) });
            if (!res.ok) throw new Error('삭제에 실패했습니다.');
            if (typeof toast === 'function') toast('게시글이 삭제되었습니다.');

            const card = document.querySelector(`[data-my-post-id="${postId}"]`);
            if (card) card.remove();

            await window._renderMyReviews();
            if (typeof window.loadCommunityPosts === 'function') await window.loadCommunityPosts(0, true);
        } catch (e) {
            if (typeof toast === 'function') toast(e.message || '삭제에 실패했습니다.');
        }
    };

    const prevGoForMyReviews = window.go;
    if (typeof prevGoForMyReviews === 'function' && !prevGoForMyReviews.__communityMyReviewWrapped) {
        window.go = function (id) {
            const result = prevGoForMyReviews.apply(this, arguments);
            if (id === 'mypage') setTimeout(function () { window._renderMyReviews(); }, 300);
            return result;
        };
        window.go.__communityMyReviewWrapped = true;
    }

    document.addEventListener('DOMContentLoaded', function () {
        setTimeout(function () {
            const token = typeof getAccessToken === 'function' ? getAccessToken()
                : localStorage.getItem('accessToken');
            if (document.getElementById('my-reviews-list') && token) {
                window._renderMyReviews();
            }
        }, 700);
    });


    // ── 좋아요 / 스크랩 상태 유지 ─────────────────────────────────────

    const ACTIVE_COLOR = '#46B29E';

    function findActionButton(keyword) {
        return [...document.querySelectorAll('#page-review button, #page-review .btn-f, #page-review .btn-scrap')]
            .find(btn => (btn.textContent || '').includes(keyword));
    }

    function setButtonState(btn, active) {
        if (!btn) return;
        if (active) {
            btn.classList.add('community-action-active');
            btn.style.cssText += 'background:' + ACTIVE_COLOR + ';border-color:' + ACTIVE_COLOR + ';color:#fff';
        } else {
            btn.classList.remove('community-action-active');
            btn.style.background = ''; btn.style.borderColor = ''; btn.style.color = '';
        }
    }

    function applyActionState() {
        const post = window._currentPostDetail;
        if (!post) return;
        setButtonState(findActionButton('좋아요'), !!post.likedByMe);
        setButtonState(findActionButton('스크랩'), !!post.scrappedByMe);
    }

    const prevOpenPostDetailForAction = window.openPostDetail;
    if (typeof prevOpenPostDetailForAction === 'function' && !prevOpenPostDetailForAction.__communityActionStateWrapped) {
        window.openPostDetail = async function (postId) {
            const result = await prevOpenPostDetailForAction.apply(this, arguments);
            [50, 300, 800].forEach(t => setTimeout(applyActionState, t));
            return result;
        };
        window.openPostDetail.__communityActionStateWrapped = true;
    }

    window.doReviewLike = async function () {
        if (!window._commUtil.requireLogin()) return;
        const postId = window._commUtil.getCurrentPostId();
        if (!postId) return;

        const res   = await api.post(`/api/posts/${postId}/likes`, {});
        const liked = res?.data === true;
        if (window._currentPostDetail) window._currentPostDetail.likedByMe = liked;
        if (typeof toast === 'function') toast(liked ? '좋아요를 눌렀습니다.' : '좋아요를 취소했습니다.');

        await window.openPostDetail(postId);
        setTimeout(applyActionState, 100);
    };

    window.doReviewScrap = async function () {
        if (!window._commUtil.requireLogin()) return;
        const postId   = window._commUtil.getCurrentPostId();
        if (!postId) return;

        const category = window._currentPostCategory || window._currentPostDetail?.category || 'ROUTE';
        const res      = await api.post(`/api/posts/${postId}/scraps?category=${category}`, {});
        const scrapped = res?.data === true;
        if (window._currentPostDetail) window._currentPostDetail.scrappedByMe = scrapped;
        if (typeof toast === 'function') toast(scrapped ? '🔖 스크랩했습니다.' : '🔖 스크랩을 취소했습니다.');

        await window.openPostDetail(postId);
        setTimeout(applyActionState, 100);
    };

    // ── 수정 모달 ──────────────────────────────────────────────────────

    let editSelectedImages = [];

    function styleTagsToInput(styleTags) {
        if (!styleTags) return '';
        if (Array.isArray(styleTags)) return styleTags.join(', ');
        try { const p = JSON.parse(styleTags); if (Array.isArray(p)) return p.join(', '); } catch (e) {}
        return String(styleTags);
    }

    function inputToStyleTags(value) {
        return String(value || '').split(/[\s,]+/).map(v => v.trim()).filter(Boolean).join(',');
    }

    function ensureEditModal() {
        let overlay = document.getElementById('communityEditPostOverlay');
        if (overlay) return overlay;

        overlay = document.createElement('div');
        overlay.id = 'communityEditPostOverlay';
        overlay.style.cssText = 'display:none;position:fixed;inset:0;z-index:9999;background:rgba(0,0,0,.45);align-items:center;justify-content:center;padding:20px;box-sizing:border-box;';

        overlay.innerHTML = `
            <div style="width:720px;max-width:100%;max-height:90vh;overflow:auto;background:#fff;border-radius:22px;padding:26px;box-sizing:border-box;box-shadow:0 18px 50px rgba(0,0,0,.25);">
                <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:18px">
                    <h2 style="margin:0;font-size:20px;font-weight:800;color:var(--text1)">후기 수정</h2>
                    <button type="button" id="communityEditCloseBtn" style="border:none;background:transparent;font-size:28px;cursor:pointer;color:var(--text3)">×</button>
                </div>
                <input type="hidden" id="communityEditPostId">
                <div class="form-group" style="margin-bottom:14px">
                    <label class="form-label">제목</label>
                    <input id="communityEditTitle" class="form-input" type="text" placeholder="제목을 입력하세요" style="width:100%">
                </div>
                <div class="form-group" style="margin-bottom:14px">
                    <label class="form-label">연동 여행 경로</label>
                    <div id="communityEditPlanInfo" style="font-size:13px;color:var(--text2);padding:10px 12px;background:var(--cream);border:1px solid var(--border);border-radius:var(--r);min-height:36px">-</div>
                </div>
                <div class="form-group" style="margin-bottom:14px">
                    <label class="form-label">태그</label>
                    <input id="communityEditTags" class="form-input" type="text" placeholder="예: 힐링, 제주, 맛집" style="width:100%">
                </div>
                <div class="form-group" style="margin-bottom:14px">
                    <label class="form-label">내용</label>
                    <div style="border:1px solid var(--border2);border-radius:var(--r);background:#fff;display:flex;flex-direction:column;min-height:220px">
                        <div style="display:flex;gap:4px;padding:7px 10px;border-bottom:1px solid var(--border2);background:var(--cream)">
                            <button type="button"
                                    data-editor-id="communityEditContent"
                                    data-editor-command="bold"
                                    style="padding:3px 8px;border:1px solid var(--border);border-radius:5px;background:var(--surface);cursor:pointer;font-size:12px;font-weight:700;color:var(--text1)"
                                    onmousedown="event.preventDefault(); window._commUtil && window._commUtil.saveEditorSelection(document.getElementById('communityEditContent'), '_communityEditEditorRange')"
                                    onclick="window._commUtil && window._commUtil.applyEditorCommand(document.getElementById('communityEditContent'), 'bold', '_communityEditEditorRange')">B</button>
                            <button type="button"
                                    data-editor-id="communityEditContent"
                                    data-editor-command="italic"
                                    style="padding:3px 8px;border:1px solid var(--border);border-radius:5px;background:var(--surface);cursor:pointer;font-size:12px;font-style:italic;color:var(--text1)"
                                    onmousedown="event.preventDefault(); window._commUtil && window._commUtil.saveEditorSelection(document.getElementById('communityEditContent'), '_communityEditEditorRange')"
                                    onclick="window._commUtil && window._commUtil.applyEditorCommand(document.getElementById('communityEditContent'), 'italic', '_communityEditEditorRange')">I</button>
                            <button type="button"
                                    data-editor-id="communityEditContent"
                                    data-editor-command="underline"
                                    style="padding:3px 8px;border:1px solid var(--border);border-radius:5px;background:var(--surface);cursor:pointer;font-size:12px;text-decoration:underline;color:var(--text1)"
                                    onmousedown="event.preventDefault(); window._commUtil && window._commUtil.saveEditorSelection(document.getElementById('communityEditContent'), '_communityEditEditorRange')"
                                    onclick="window._commUtil && window._commUtil.applyEditorCommand(document.getElementById('communityEditContent'), 'underline', '_communityEditEditorRange')">U</button>
                        </div>
                        <div id="communityEditContent" contenteditable="true" data-placeholder="내용을 입력하세요" style="min-height:220px;
                        padding:14px;font-size:13px;color:var(--text);line-height:1.8;outline:none;font-family:inherit;overflow-y:auto;
                        white-space:pre-wrap;overflow-wrap:anywhere;word-break:break-word;max-width:100%;box-sizing:border-box"></div>
                    </div>
                </div>
                <div class="form-group" style="margin-bottom:14px" id="communityEditPlaceReviewsSection" style="display:none">
                    <label class="form-label">📍 장소별 별점 &amp; 한줄평</label>
                    <div id="communityEditPlaceReviewsBody" style="display:flex;flex-direction:column;gap:10px;margin-top:8px"></div>
                </div>
                <div class="form-group" style="margin-bottom:14px">
                    <label style="display:flex;align-items:center;gap:8px;font-size:13px;color:var(--text2);cursor:pointer">
                        <input id="communityEditPublic" type="checkbox"> 공개글로 설정
                    </label>
                </div>
                <div class="form-group" style="margin-bottom:18px">
                    <label class="form-label">첨부 이미지</label>
                    <button type="button" id="communityEditAddImageBtn" class="btn-prev-step" style="padding:9px 14px;border-radius:10px;font-size:13px">새 이미지 추가</button>
                    <input id="communityEditImageInput" type="file" accept="image/*" multiple style="display:none">
                </div>
                <div style="display:flex;gap:8px;justify-content:flex-end">
                    <button type="button" id="communityEditCancelBtn" class="btn-prev-step" style="padding:11px 18px;border-radius:var(--r)">취소</button>
                    <button type="button" id="communityEditSubmitBtn" class="btn-f" style="padding:11px 22px;border-radius:var(--r)">수정 완료</button>
                </div>
            </div>
        `;
        document.body.appendChild(overlay);

        const editEditor = document.getElementById('communityEditContent');
        if (editEditor) {
            window._commUtil.bindEditorSelectionMemory(editEditor, '_communityEditEditorRange');
            window._commUtil.bindInlineImageDeleteSupport(editEditor, editSelectedImages, '_communityEditEditorRange');
        }

        overlay.addEventListener('click', e => { if (e.target === overlay) closeEditModal(); });
        document.getElementById('communityEditCloseBtn').onclick  = closeEditModal;
        document.getElementById('communityEditCancelBtn').onclick = closeEditModal;
        document.getElementById('communityEditSubmitBtn').onclick = submitEditPost;

        const btn   = document.getElementById('communityEditAddImageBtn');
        const input = document.getElementById('communityEditImageInput');
        if (btn && input) {
            btn.onmousedown = function () {
                const editor = document.getElementById('communityEditContent');
                if (editor) window._commUtil.saveEditorSelection(editor, '_communityEditEditorRange');
            };
            btn.onclick = function () { input.click(); };
            input.onchange = handleEditImageSelect;
        }

        return overlay;
    }


    function closeEditModal() {
        const overlay = document.getElementById('communityEditPostOverlay');
        if (overlay) overlay.style.display = 'none';
    }

    function renderEditImages(postId, imageUrls) {
        // const box   = document.getElementById('communityEditImages');
        // const empty = document.getElementById('communityEditImageEmpty');
        // if (!box || !empty) return;
        //
        // const urls = Array.isArray(imageUrls) ? imageUrls : [];
        // if (!urls.length) { box.innerHTML = ''; empty.style.display = 'block'; return; }
        //
        // empty.style.display = 'none';
        // box.innerHTML = urls.map(url => `
        //     <div class="community-edit-image-item" data-image-url="${escapeHtml(url)}" style="position:relative;border:1px solid var(--border);border-radius:12px;overflow:hidden;background:#fff">
        //         <img src="${escapeHtml(url)}" alt="첨부 이미지" style="width:100%;height:110px;object-fit:cover;display:block" onerror="this.style.display='none'">
        //         <button type="button" class="community-edit-image-delete"
        //                 data-post-id="${escapeHtml(postId)}" data-image-url="${escapeHtml(url)}"
        //                 style="width:100%;border:none;border-top:1px solid var(--border);background:#FEF3F2;color:var(--coral);padding:8px 0;font-size:12px;font-weight:800;cursor:pointer;">이미지 삭제</button>
        //     </div>
        // `).join('');
        //
        // box.querySelectorAll('.community-edit-image-delete').forEach(btn => {
        //     btn.onclick = async function () {
        //         const targetPostId = this.getAttribute('data-post-id');
        //         const imageUrl     = this.getAttribute('data-image-url');
        //         if (!targetPostId || !imageUrl) return;
        //         if (!confirm('이 이미지를 삭제하시겠습니까? 삭제하면 복구할 수 없습니다.')) return;
        //
        //         try {
        //             await requestJson(`/api/posts/${targetPostId}/images?imageUrl=${encodeURIComponent(imageUrl)}`, {
        //                 method: 'DELETE', headers: authHeaders(false)
        //             });
        //             const item = this.closest('.community-edit-image-item');
        //             if (item) item.remove();
        //             if (!box.querySelector('.community-edit-image-item')) empty.style.display = 'block';
        //             if (typeof toast === 'function') toast('이미지가 삭제되었습니다.');
        //         } catch (e) {
        //             if (typeof toast === 'function') toast(e.message || '이미지 삭제에 실패했습니다.');
        //         }
        //     };
        // });
    }

    function handleEditImageSelect(e) {
        const files = [...(e.target.files || [])];
        if (!files.length) return;

        const editor = document.getElementById('communityEditContent');
        if (!editor) {
            if (typeof toast === 'function') toast('본문 입력창을 찾을 수 없습니다.');
            e.target.value = '';
            return;
        }

        window._commUtil.insertImagesIntoEditor(editor, files, editSelectedImages, '_communityEditEditorRange');
        e.target.value = '';
    }

    async function uploadEditImages() {
        const editor = document.getElementById('communityEditContent');
        return await window._commUtil.finalizeInlineEditorImages(editor, editSelectedImages);
    }

    async function submitEditPost() {
        const postId   = document.getElementById('communityEditPostId')?.value;
        const title    = document.getElementById('communityEditTitle')?.value.trim();
        const editor   = document.getElementById('communityEditContent');
        const tags     = document.getElementById('communityEditTags')?.value || '';
        const isPublic = !!document.getElementById('communityEditPublic')?.checked;
        const category = window._communityEditOriginalPost?.category || 'ROUTE';

        if (!postId || !title || !window._commUtil.editorHasContent(editor)) {
            if (typeof toast === 'function') toast('제목과 내용을 입력해주세요.');
            return;
        }

        let finalized;
        try {
            finalized = await uploadEditImages();
        } catch (e) {
            if (typeof toast === 'function') toast(e.message || '이미지 업로드에 실패했습니다.');
            return;
        }

        const originalImageUrls = Array.isArray(window._communityEditOriginalPost?.imageUrls)
            ? window._communityEditOriginalPost.imageUrls.filter(Boolean)
            : [];

        const remainingImageUrlSet = new Set((finalized.imageUrls || []).filter(Boolean));
        const removedImageUrls = originalImageUrls.filter(function (url) {
            return url && !remainingImageUrlSet.has(url);
        });

        const body = {
            title,
            content: finalized.content,
            styleTags: inputToStyleTags(tags),
            category,
            isPublic,
            planId: window._communityEditOriginalPost?.planId || null,
            imageUrls: finalized.uploadedImageUrls || []
        };

        try {
            await requestJson(`/api/posts/${postId}`, {
                method: 'PATCH', headers: authHeaders(true), body: JSON.stringify(body)
            });

            // 본문에서 삭제한 기존 이미지는 서버의 POST_IMAGES에서도 삭제한다.
            // 삭제하지 않으면 상세페이지에서 imageUrls fallback으로 글 맨 아래에 다시 표시된다.
            for (const imageUrl of removedImageUrls) {
                try {
                    await requestJson(`/api/posts/${postId}/images?imageUrl=${encodeURIComponent(imageUrl)}`, {
                        method: 'DELETE', headers: authHeaders(false)
                    });
                } catch (deleteError) {
                    console.warn('[community-v2] 기존 이미지 삭제 실패:', imageUrl, deleteError);
                }
            }

            // 장소 리뷰 저장 — 기존 리뷰 업데이트 + 신규 리뷰 생성
            const plrRows = document.querySelectorAll('#communityEditPlaceReviewsBody .plr-row');
            if (plrRows.length) {
                const toUpdate = [], toCreate = [];
                plrRows.forEach(function(row) {
                    const rating  = Number(row.querySelector('.star-sel')?.dataset.rating || 0);
                    const comment = (row.querySelector('.one-line')?.value || '').trim() || null;
                    if (!rating) return;
                    if (row.dataset.reviewId) {
                        toUpdate.push({ placeReviewId: Number(row.dataset.reviewId), rating, comment });
                    } else {
                        toCreate.push({ placeName: row.dataset.placeName || '', placeType: row.dataset.placeType || 'tour', rating, comment });
                    }
                });
                if (toUpdate.length) {
                    await requestJson('/api/posts/' + postId + '/place-reviews', {
                        method: 'PATCH', headers: authHeaders(true), body: JSON.stringify({ reviews: toUpdate })
                    });
                }
                if (toCreate.length) {
                    await requestJson('/api/posts/' + postId + '/place-reviews', {
                        method: 'POST', headers: authHeaders(true), body: JSON.stringify({ reviews: toCreate })
                    });
                }
            }

            if (typeof toast === 'function') toast('후기가 수정되었습니다.');
            closeEditModal();

            if (typeof window._renderMyReviews === 'function') await window._renderMyReviews();
            if (typeof window.loadCommunityPosts === 'function') await window.loadCommunityPosts(0, true);

            if ((window._currentPostId && String(window._currentPostId) === String(postId)) ||
                (window._openedPostId  && String(window._openedPostId)  === String(postId))) {
                if (typeof window.openPostDetail === 'function') await window.openPostDetail(postId);
            }
        } catch (e) {
            if (typeof toast === 'function') toast(e.message || '수정에 실패했습니다.');
        }
    }

    window.editMyPost = async function (postId) {
        if (!postId) return;
        if (!window._commUtil.requireLogin()) return;

        let postRes;
        try {
            postRes = await requestJson(`/api/posts/${postId}`, { method: 'GET', headers: authHeaders(false) });
        } catch (e) {
            if (typeof toast === 'function') toast(e.message || '게시글 정보를 불러오지 못했습니다.');
            return;
        }

        const post = postRes?.data || postRes;
        if (!post || !post.postId) return;

        const loginUserId = (typeof window._currentUser !== 'undefined' && window._currentUser)
            ? (window._currentUser.userId ?? window._currentUser.id)
            : (typeof _currentUser !== 'undefined' && _currentUser ? (_currentUser.userId ?? _currentUser.id) : null);

        const writerId = post.userId ?? post.writerId ?? post.authorId ?? null;

        if (loginUserId && writerId && String(loginUserId) !== String(writerId)) {
            if (typeof toast === 'function') toast('본인이 작성한 글만 수정할 수 있습니다.');
            return;
        }

        const overlay = ensureEditModal();
        window._communityEditOriginalPost = post;
        editSelectedImages = [];

        document.getElementById('communityEditPostId').value  = post.postId;
        document.getElementById('communityEditTitle').value   = post.title || '';
        const editor = document.getElementById('communityEditContent');
        if (editor) {
            editor.innerHTML = window._commUtil.buildEditableContentWithImages(post);
            window._commUtil.bindEditorSelectionMemory(editor, '_communityEditEditorRange');
            window._commUtil.bindInlineImageDeleteSupport(editor, editSelectedImages, '_communityEditEditorRange');
            window._communityEditEditorRange = null;
            window._commUtil.resetEditorCommandState(editor, '_communityEditEditorRange');
        }
        document.getElementById('communityEditTags').value    = styleTagsToInput(post.styleTags);
        document.getElementById('communityEditPublic').checked = post.isPublic !== false;

        // 플랜 정보 표시
        const planInfoEl = document.getElementById('communityEditPlanInfo');
        if (planInfoEl) {
            planInfoEl.textContent = post.planTitle
                ? ('📍 ' + post.planTitle + (post.planDestination ? ' · ' + post.planDestination : ''))
                : '연동된 여행 경로 없음';
        }

        renderEditImages(post.postId, post.imageUrls || []);

        const imageInput = document.getElementById('communityEditImageInput');
        if (imageInput) imageInput.value = '';


        // 장소 리뷰 로드 (planRouteJson 파싱 + 기존 리뷰 매핑)
        const plrSection = document.getElementById('communityEditPlaceReviewsSection');
        const plrBody    = document.getElementById('communityEditPlaceReviewsBody');
        if (plrSection && plrBody) {
            plrSection.style.display = 'none';
            plrBody.innerHTML = '';
            try {
                // planRouteJson에서 장소 목록 추출
                var routeRaw = post.planRouteJson;
                var days = [];
                try { days = routeRaw ? (typeof routeRaw === 'string' ? JSON.parse(routeRaw) : routeRaw) : []; } catch(_) {}
                var TYPE_ICON_MAP = { stay: '🏨', food: '🍽️', cafe: '☕', tour: '🎡', attraction: '🎡' };
                var TYPE_CSS_MAP  = { stay: 'pr-stay', food: 'pr-food', cafe: 'pr-cafe', tour: 'pr-tour', attraction: 'pr-tour' };
                var planPlaces = [];
                (Array.isArray(days) ? days : []).forEach(function(day) {
                    (day.places || []).forEach(function(p) { if (p.name) planPlaces.push(p); });
                });

                // 기존 장소 리뷰 조회
                var existingReviews = [];
                try {
                    var plrRes = await requestJson('/api/posts/' + post.postId + '/place-reviews', { method: 'GET', headers: authHeaders(false) });
                    existingReviews = Array.isArray(plrRes?.data) ? plrRes.data : Array.isArray(plrRes) ? plrRes : [];
                } catch(_) {}

                // placeName 기준으로 기존 리뷰 맵 생성
                var reviewMap = {};
                existingReviews.forEach(function(r) { reviewMap[r.placeName] = r; });

                if (planPlaces.length) {
                    plrSection.style.display = 'block';
                    plrBody.innerHTML = planPlaces.map(function(p) {
                        var type    = (p.type || 'tour').toLowerCase();
                        var icon    = TYPE_ICON_MAP[type] || '📍';
                        var css     = TYPE_CSS_MAP[type]  || 'pr-tour';
                        var existing = reviewMap[p.name];
                        var rating  = existing ? (existing.rating || 0) : 0;
                        var comment = existing ? (existing.comment || '') : '';
                        var reviewId = existing ? existing.id : '';
                        var stars = [1,2,3,4,5].map(function(n) {
                            return '<button class="star-btn' + (n <= rating ? ' lit' : '') + '" onclick="setStars(this,' + n + ')">★</button>';
                        }).join('');
                        return '<div class="plr-row" data-place-name="' + escapeHtml(p.name) + '" data-place-type="' + type + '"' + (reviewId ? ' data-review-id="' + reviewId + '"' : '') + '>' +
                            '<div class="plr-icon ' + css + '">' + icon + '</div>' +
                            '<div class="plr-name">' + escapeHtml(p.name) + '</div>' +
                            '<div class="star-sel" data-rating="' + rating + '">' + stars + '</div>' +
                            '<input class="one-line" placeholder="한줄평 (선택)" maxlength="200" value="' + escapeHtml(comment) + '">' +
                            '</div>';
                    }).join('');
                } else if (existingReviews.length) {
                    // planRouteJson 없어도 기존 리뷰는 표시
                    plrSection.style.display = 'block';
                    plrBody.innerHTML = existingReviews.map(function(r) {
                        var stars = [1,2,3,4,5].map(function(n) {
                            return '<button class="star-btn' + (n <= (r.rating||0) ? ' lit' : '') + '" onclick="setStars(this,' + n + ')">★</button>';
                        }).join('');
                        return '<div class="plr-row" data-place-name="' + escapeHtml(r.placeName||'') + '" data-place-type="' + (r.category||'tour').toLowerCase() + '" data-review-id="' + r.id + '">' +
                            '<div class="plr-icon pr-tour">📍</div>' +
                            '<div class="plr-name">' + escapeHtml(r.placeName||'') + '</div>' +
                            '<div class="star-sel" data-rating="' + (r.rating||0) + '">' + stars + '</div>' +
                            '<input class="one-line" placeholder="한줄평 (선택)" maxlength="200" value="' + escapeHtml(r.comment||'') + '">' +
                            '</div>';
                    }).join('');
                }
            } catch(e) { console.error('[edit] 장소 리뷰 로드 실패', e); }
        }

        overlay.style.display = 'flex';
    };


    /* =============================================================================
     * community v2 — loadMyScrap override (마이페이지 장소 스크랩 탭)
     * app_community.js의 게시글 스크랩 버전을 장소 스크랩으로 교체
     * ============================================================================= */
    function renderMyPlaceScrapCard(s, category, iconMap, labelMap) {
        const placeId = Number(s.placeId || 0);
        const placeName = s.placeName || '장소';
        const safeNameForJs = escapeJsString(placeName);
        const safeCategoryForJs = escapeJsString(s.category || '');
        const avgRatingForJs = s.avgRating ? Number(s.avgRating) : 'null';

        const starHtml = function (avg) {
            if (!avg) return '';
            const filled = Math.round(avg);
            return '★'.repeat(filled) + '☆'.repeat(5 - filled) + ' <b>' + Number(avg).toFixed(1) + '</b>';
        };

        return '<div class="place-card" style="cursor:pointer" ' +
            'data-my-place-scrap-category="' + escapeHtml(category) + '" ' +
            'onclick="window.goToScrapPlace(' + placeId + ',\'' + safeNameForJs + '\',\'' + safeCategoryForJs + '\',' + avgRatingForJs + ')">' +
            '<div class="pc-hd">' +
            '<div class="pc-icon">' + (iconMap[s.category] || '📍') + '</div>' +
            '<div style="flex:1;min-width:0">' +
            '<div class="pc-name">' + escapeHtml(placeName) + '</div>' +
            '<div class="pc-meta">' + escapeHtml(s.address || labelMap[s.category] || '') + '</div>' +
            (s.avgRating ? '<div style="color:var(--warm);font-size:12px;margin-top:2px">' + starHtml(s.avgRating) + '</div>' : '') +
            '</div>' +
            '<button onclick="event.stopPropagation();window.deleteMyPlaceScrap(' + s.scrapId + ',this,\'' + escapeHtml(category) + '\')" ' +
            'style="font-size:11px;background:none;border:1px solid var(--border2);border-radius:5px;padding:2px 7px;cursor:pointer;color:var(--coral);flex-shrink:0">' +
            '🗑️ 삭제' +
            '</button>' +
            '</div>' +
            '</div>';
    }

    window.loadMyScrap = async function loadMyScrap(category, page) {
        const catMap   = { stay: 'STAY', food: 'FOOD', tour: 'TOUR', cafe: 'CAFE' };
        const iconMap  = { STAY: '🏨', FOOD: '🍽️', TOUR: '🗺️', CAFE: '☕' };
        const labelMap = { STAY: '숙소', FOOD: '맛집', TOUR: '관광지', CAFE: '카페' };
        const listId   = { stay: 'my-scrap-stay-list', food: 'my-scrap-food-list', tour: 'my-scrap-tour-list', cafe: 'my-scrap-cafe-list' };

        const el = document.getElementById(listId[category]);
        if (!el) return;

        el.innerHTML = '<div style="color:var(--text3);font-size:13px;padding:20px 0;text-align:center">불러오는 중...</div>';

        const res = await api.get('/api/scraps');
        if (!res || !res.success || !Array.isArray(res.data)) {
            el.innerHTML = emptyMyPageMessage('스크랩한 장소가 없습니다.');
            return;
        }

        const dbCat = catMap[category] || category.toUpperCase();
        const list  = res.data.filter(s => s.category === dbCat);
        const emptyText = '스크랩한 ' + (labelMap[dbCat] || '장소') + '가 없습니다.';

        renderMyCommunityPagedList(
            el,
            'scrap-' + category,
            list,
            emptyText,
            function (s) { return renderMyPlaceScrapCard(s, category, iconMap, labelMap); },
            function (nextPage) { window.loadMyScrap(category, nextPage); },
            page
        );
    };;

    window.goToScrapPlace = function(placeId, placeName, category, avgRating) {
        const catToType = { STAY: 'stay', FOOD: 'food', TOUR: 'tour', CAFE: 'cafe' };
        const type = catToType[category] || 'tour';

        go('community');

        // 탭 전환 후 장소 후기 열기
        setTimeout(function () {
            const tabBtn = document.querySelector('#commTabs .comm-tab[onclick*="\'' + type + '\'"]');
            if (tabBtn && typeof window.setCommTab === 'function') {
                window.setCommTab(tabBtn, type);
            }
            if (typeof window._openPlaceReviews === 'function') {
                window._openPlaceReviews(placeId, placeName, type, avgRating, null);
            }
        }, 100);
    };

    window.deleteMyPlaceScrap = async function(scrapId, btn, category) {
        const res = await api.del('/api/scraps/' + scrapId);
        if (res && res.success !== false) {
            if (typeof toast === 'function') toast('스크랩이 삭제되었습니다.');

            if (category && typeof window.loadMyScrap === 'function') {
                const state = getMyCommunityPagingState('scrap-' + category);
                await window.loadMyScrap(category, state.page || 0);
            } else {
                const card = btn && btn.closest ? btn.closest('.place-card') : null;
                if (card) card.remove();
            }
        } else {
            if (typeof toast === 'function') toast('삭제에 실패했습니다.');
        }
    };

    /* =============================================================================
     * community v2 — 장소 스크랩 (장소 후기 탭)
     * ============================================================================= */
    window.doCommPlaceScrap = async function (placeId, category) {
        if (!window._commUtil.requireLogin()) return;
        const res = await api.post(`/api/places/${placeId}/scraps`, { category: category || 'tour' });
        const scrapped = res?.data === true;
        if (typeof toast === 'function') {
            toast(res?.success === false
                ? (res?.message || '⚠️ 스크랩 처리에 실패했습니다.')
                : (scrapped ? '🔖 스크랩했습니다.' : '🔖 스크랩을 취소했습니다.'));
        }
        /* 집합 갱신 → 다른 화면에서도 상태 일관 */
        if (window._scrappedPlaceIds) {
            if (scrapped) window._scrappedPlaceIds.add(String(placeId));
            else          window._scrappedPlaceIds.delete(String(placeId));
        }
        return scrapped;
    };

    /* 후기 패널 안의 스크랩 버튼 — 색을 토글하고 상태 유지 */
    window.doCommPlaceScrapToggle = async function (btn, placeId, category) {
        const scrapped = await window.doCommPlaceScrap(placeId, category);
        if (btn && scrapped !== undefined) {
            if (scrapped) {
                btn.classList.add('scrapped');
                btn.style.background  = '#46B29E';
                btn.style.borderColor = '#46B29E';
                btn.style.color       = '#fff';
            } else {
                btn.classList.remove('scrapped');
                btn.style.background  = '';
                btn.style.borderColor = '';
                btn.style.color       = '';
            }
        }
    };

    /* =============================================================================
     * community v2 — 여행 경로 스크랩 (마이페이지)
     * ============================================================================= */
    window._myRouteScrapDeleteMode = window._myRouteScrapDeleteMode || false;

    function formatMyRouteScrapDate(value) {
        if (!value) return '—';
        try {
            const text = String(value).replace('T', ' ');
            const datePart = text.substring(0, 10).replaceAll('-', '.');
            const timePart = text.length >= 16 ? text.substring(11, 16) : '';
            return timePart ? datePart + '<br>' + timePart : datePart;
        } catch (e) {
            return '—';
        }
    }

    function renderMyRouteScrapActions() {
        const actionBox = document.getElementById('my-scrap-route-actions');
        if (!actionBox) return;

        if (window._myRouteScrapDeleteMode) {
            actionBox.innerHTML =
                '<button type="button" onclick="execMyRouteScrapBulkDelete()" ' +
                'style="padding:4px 10px;background:var(--coral);color:#fff;border:none;border-radius:6px;font-size:12px;font-weight:700;cursor:pointer;">선택 삭제 실행</button>' +
                '<button type="button" onclick="toggleMyRouteScrapDeleteMode(false)" ' +
                'style="padding:4px 10px;background:var(--cream);color:var(--text2);border:1px solid var(--border);border-radius:6px;font-size:12px;font-weight:600;cursor:pointer;">취소</button>';
            return;
        }

        actionBox.innerHTML =
            '<button type="button" onclick="toggleMyRouteScrapDeleteMode(true)" ' +
            'style="padding:4px 10px;background:var(--cream);color:var(--text2);border:1px solid var(--border);border-radius:6px;font-size:12px;font-weight:600;cursor:pointer;">삭제하기</button>';
    }

    function renderMyRouteScrapCard(post) {
        const postId = post.postId || post.id;
        const title = post.title || '스크랩한 여행 경로';
        const writer = post.writerName || '작성자';
        const createdAt = formatMyRouteScrapDate(post.createdAt);
        const tags = Array.isArray(post.styleTags)
            ? post.styleTags.slice(0, 3).map(function (tag) {
                return '<span style="font-size:11px;background:var(--sage-pale);color:var(--sage-d);border-radius:4px;padding:1px 6px;margin-right:3px">#' + escapeHtml(tag) + '</span>';
            }).join('')
            : '';

        const cardClickAction = window._myRouteScrapDeleteMode
            ? "const chk = document.getElementById('chk-route-scrap-" + postId + "'); if(chk) chk.checked = !chk.checked;"
            : 'window.goToScrapRoute(' + postId + ')';

        return '' +
            '<div class="trip-card" onclick="' + cardClickAction + '" ' +
            'style="cursor:pointer;position:relative;display:flex;align-items:center;gap:14px;padding:14px 16px;border-radius:var(--r);border:2px solid var(--border);background:var(--surface);margin-bottom:10px;transition:all .2s;">' +
            (window._myRouteScrapDeleteMode
                ? '<input type="checkbox" class="route-scrap-del-chk" id="chk-route-scrap-' + escapeHtml(postId) + '" value="' + escapeHtml(postId) + '" onclick="event.stopPropagation();" style="width:16px;height:16px;cursor:pointer;margin-left:4px;">'
                : '') +
            '<div style="width:42px;height:42px;border-radius:10px;background:var(--sage);display:flex;align-items:center;justify-content:center;font-size:20px;color:#fff;flex-shrink:0;">🗺️</div>' +
            '<div class="trip-info" style="flex:1;min-width:0;">' +
            '<div class="trip-ttl" style="font-weight:700;font-size:14px;color:var(--text);white-space:nowrap;overflow:hidden;text-overflow:ellipsis;">' + escapeHtml(title) + '</div>' +
            '<div class="trip-meta" style="font-size:11px;color:var(--text3);margin-top:2px;">' + escapeHtml(writer) + ' · 여행 경로</div>' +
            (tags ? '<div style="margin-top:4px">' + tags + '</div>' : '') +
            '</div>' +
            '<div class="trip-budget" style="color:var(--text3);font-size:11px;text-align:right;line-height:1.4;min-width:80px;flex-shrink:0;">' +
            '<span style="display:block;font-size:10px;color:var(--text3);font-weight:700;margin-bottom:2px;">스크랩 일시</span>' +
            '<span style="color:var(--text2);font-weight:500;">' + createdAt + '</span>' +
            '</div>' +
            '</div>';
    }

    window.loadMyRouteScrap = async function(page) {
        const listEl = document.getElementById('my-scrap-route-list');
        if (!listEl) return;

        renderMyRouteScrapActions();
        listEl.innerHTML = '<div style="color:var(--text3);font-size:13px;padding:20px 0;text-align:center">불러오는 중...</div>';

        let list = [];
        try {
            const res = await requestJson('/api/posts/scrapped', { method: 'GET', headers: authHeaders(false) });
            list = normalizeMyPageListResponse(res);
        } catch (e) {
            listEl.innerHTML = emptyMyPageMessage('스크랩한 여행 경로를 불러오지 못했습니다.');
            if (typeof toast === 'function') toast(e.message || '스크랩한 여행 경로를 불러오지 못했습니다.');
            return;
        }

        renderMyCommunityPagedList(
            listEl,
            'scrap-route',
            list,
            '스크랩한 여행 경로가 없습니다.',
            renderMyRouteScrapCard,
            window.loadMyRouteScrap,
            page
        );
    };

    window.toggleMyRouteScrapDeleteMode = function(isDeleteMode) {
        window._myRouteScrapDeleteMode = !!isDeleteMode;
        const state = getMyCommunityPagingState('scrap-route');
        if (typeof window.loadMyRouteScrap === 'function') {
            window.loadMyRouteScrap(state.page || 0);
        }
    };

    window.execMyRouteScrapBulkDelete = async function() {
        const checked = document.querySelectorAll('.route-scrap-del-chk:checked');
        if (!checked.length) {
            if (typeof toast === 'function') toast('삭제할 스크랩 여행 경로를 선택해주세요.');
            return;
        }

        if (!confirm('선택한 ' + checked.length + '개의 스크랩 여행 경로를 삭제하시겠습니까?')) return;

        let successCount = 0;
        for (const chk of checked) {
            const postId = chk.value;
            try {
                const res = await api.post('/api/posts/' + postId + '/scraps?category=ROUTE', {});
                if (res && res.success !== false) successCount++;
            } catch (e) {
                console.warn('[community-v2] 스크랩 여행 경로 삭제 실패:', postId, e);
            }
        }

        if (successCount > 0) {
            if (typeof toast === 'function') toast('선택한 스크랩 여행 경로가 삭제되었습니다.');
            window._myRouteScrapDeleteMode = false;
            const state = getMyCommunityPagingState('scrap-route');
            await window.loadMyRouteScrap(state.page || 0);
        } else {
            if (typeof toast === 'function') toast('삭제 처리에 실패했습니다.');
        }
    };

    window.goToScrapRoute = function(postId) {
        if (!postId) return;
        go('community');
        setTimeout(function() {
            if (typeof openPostDetail === 'function') openPostDetail(postId);
        }, 150);
    };

    window.deleteMyRouteScrap = async function(postId) {
        if (!postId) return;
        if (!confirm('이 스크랩 여행 경로를 삭제하시겠습니까?')) return;

        const res = await api.post('/api/posts/' + postId + '/scraps?category=ROUTE', {});
        if (res && res.success !== false) {
            if (typeof toast === 'function') toast('스크랩이 삭제되었습니다.');
            const state = getMyCommunityPagingState('scrap-route');
            await window.loadMyRouteScrap(state.page || 0);
        } else {
            if (typeof toast === 'function') toast('삭제에 실패했습니다.');
        }
    };

    window.startPlanFromCommunityPost = function () {
        const token = localStorage.getItem('accessToken');
        const post = window._currentPostDetail;
        const dest = post?.planDestination || '';
        const parts = dest ? dest.split('|') : [];
        const prov = parts[0] || '';
        const city = parts[1] || '';

        if (!token) {
            if (typeof openModal === 'function') openModal('modal-auth');
            window._pendingLoginThenPlanner = { prov, city };
            return;
        }

        window._pendingCommunityDest = { prov, city };
        if (typeof resetPlannerForm === 'function') resetPlannerForm();
        window._currentTripId = null;
        if (typeof go === 'function') go('planner', false);
        if (typeof goPlanStep === 'function') goPlanStep(1);

        setTimeout(function () {
            const pending = window._pendingCommunityDest;
            if (!pending || !pending.prov) return;
            const provSel = document.getElementById('dest-prov');
            if (!provSel) return;
            provSel.value = pending.prov;
            if (typeof updateCityDest === 'function') updateCityDest(provSel);
            if (pending.city) {
                setTimeout(function () {
                    const cityEl = document.getElementById('dest-city');
                    if (!cityEl) return;
                    const cityOpts = Array.from(cityEl.options);
                    const cityMatch = cityOpts.find(o => o.value === pending.city || o.text === pending.city);
                    if (cityMatch) cityEl.value = cityMatch.value;
                    window._pendingCommunityDest = null;
                }, 50);
            } else {
                window._pendingCommunityDest = null;
            }
        }, 0);
    };
})();