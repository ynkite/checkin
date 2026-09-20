/** _myTrips를 기반으로 가계부 여행 선택 UI 렌더링 (페이지네이션 포함) */
async function updateLedgerList() {
    _updateExportButtons();
    if (!_currentUser) return;
    const listEl = document.getElementById('my-ledger-list');
    if (!listEl) return;

    if (!_budgetSelectedTripId && _myTrips.length > 0) {
        _budgetSelectedTripId = _myTrips[0].id;
    }

    if (!_myTrips.length) {
        listEl.innerHTML = '<div style="color:var(--text3);font-size:13px;padding:20px 0;text-align:center">가계부 기록이 없습니다.</div>';
        _drawMyLedgerPager(0);
        return;
    }

    const total = _myTrips.length;
    const totalPages = Math.ceil(total / _LEDGER_CARD_PAGE_SIZE);
    if (_myLedgerPage > totalPages) _myLedgerPage = totalPages;
    const start = (_myLedgerPage - 1) * _LEDGER_CARD_PAGE_SIZE;
    const pageTrips = _myTrips.slice(start, start + _LEDGER_CARD_PAGE_SIZE);

    listEl.innerHTML = pageTrips.map(l => {
        const isSel = (_budgetSelectedTripId === l.id);
        return `
        <div onclick="selLedger(${l.id});goLedger2()"
             style="display:flex;align-items:center;gap:14px;padding:14px 16px;border-radius:var(--r);
                    border:2px solid ${isSel ? 'var(--sage)' : 'var(--border)'};
                    background:${isSel ? 'var(--sage-pale)' : 'var(--surface)'};
                    cursor:pointer;margin-bottom:10px;transition:all .2s">
          <div style="width:42px;height:42px;border-radius:10px;background:var(--sage);
                      display:flex;align-items:center;justify-content:center;font-size:20px">지도</div>
          <div style="flex:1">
      <div style="font-weight:700;font-size:14px">${l.title || '여행 일정'}</div>
            <div style="font-size:11px;color:var(--text3);margin-top:2px">
              ${l.startDate || ''} ~ ${l.endDate || ''} · ${l.destination || ''}
            </div>
          </div>
        </div>`;
    }).join('');

    _drawMyLedgerPager(totalPages);
}

/* ── 가계부 페이저: 5개 숫자만 노출 + 현재 페이지 중앙 정렬 ── */
function _getBudgetPagerWindow(currentPage, totalPages, windowSize = 5) {
    const total = Math.max(1, Number(totalPages) || 1);
    const size = Math.min(Number(windowSize) || 5, total);
    const current = Math.min(Math.max(Number(currentPage) || 1, 1), total);

    let start = current - Math.floor(size / 2);
    if (start < 1) start = 1;
    if (start + size - 1 > total) start = Math.max(1, total - size + 1);

    return Array.from({ length: size }, function (_, idx) { return start + idx; });
}


function _drawMyLedgerPager(totalPages) {
    const el = document.getElementById('my-ledger-pager');
    if (!el) return;
    if (totalPages <= 0) { el.innerHTML = ''; return; }

    const current = Math.min(Math.max(Number(_myLedgerPage) || 1, 1), Number(totalPages));
    const pages = _getBudgetPagerWindow(current, totalPages, 5);

    const prev = totalPages > 1
        ? `<button class="pager-btn" onclick="_setMyLedgerPage(${Math.max(1, current - 1)})" ${current === 1 ? 'disabled' : ''}>&lt;</button>`
        : '';

    const next = totalPages > 1
        ? `<button class="pager-btn" onclick="_setMyLedgerPage(${Math.min(Number(totalPages), current + 1)})" ${current === Number(totalPages) ? 'disabled' : ''}>&gt;</button>`
        : '';

    const nums = pages
        .map(n => `<button class="pager-btn${n === current ? ' on' : ''}" onclick="_setMyLedgerPage(${n})">${n}</button>`)
        .join('');

    el.innerHTML = `<div class="ledger-pager" style="margin-top:4px;margin-bottom:12px">${prev}${nums}${next}</div>`;
}



function _setMyLedgerPage(n) {
    const totalPages = Math.ceil((_myTrips || []).length / _LEDGER_CARD_PAGE_SIZE);
    if (n < 1 || n > totalPages) return;
    _myLedgerPage = n;
    updateLedgerList();
}

function selLedger(tripId) {
    _budgetSelectedTripId = tripId;
    sessionStorage.setItem('budgetSelectedTripId', tripId);  // [v2] 새로고침 복원용
    updateLedgerList();
}

async function goLedger2() {
    // go('ledger') 내부에서 실제 트립 카드 채우기 / 선택된 여행 상세 로딩까지 처리한다
    go('ledger');
}

/** ledger-selector 내 여행 카드를 실제 _myTrips 데이터로 채우기 (페이지네이션 포함) */
function _populateLedgerTripCards() {
    const container = document.getElementById('ledger-trip-cards');
    if (!container) return;
    if (!_myTrips || !_myTrips.length) {
        /* 안내만 두면 여기서 막힌다. 갈 곳을 같이 준다. */
        container.innerHTML =
            '<div class="lg-empty">' +
            '<p>아직 만든 여행이 없습니다.</p>' +
            '<p class="lg-empty-sub">경로를 만들면 숙박비와 이동비가 여기에 쌓입니다.</p>' +
            '<button type="button" class="btn-f" onclick="goNewPlanner()">경로 만들기</button>' +
            '</div>';
        _drawLedgerCardPager(0);
        /* 고를 것이 없으면 버튼을 켜 두지 않는다 */
        var go = document.getElementById('ledger-go');
        if (go) go.style.display = 'none';
        return;
    }
    var goBtn = document.getElementById('ledger-go');
    if (goBtn) goBtn.style.display = '';
    const total = _myTrips.length;
    const totalPages = Math.ceil(total / _LEDGER_CARD_PAGE_SIZE);
    if (_ledgerCardPage > totalPages) _ledgerCardPage = totalPages;
    const start = (_ledgerCardPage - 1) * _LEDGER_CARD_PAGE_SIZE;
    const pageTrips = _myTrips.slice(start, start + _LEDGER_CARD_PAGE_SIZE);

    container.innerHTML = pageTrips.map(t => {
        const isSel = (_budgetSelectedTripId === t.id);
        return `
      <div class="ts-card${isSel ? ' on' : ''}" onclick="_selLedgerCard(this, ${t.id})">
        <div class="ts-thumb">지도</div>
        <div class="ts-info">
          <div class="ts-name">${t.title || '여행 일정'}</div>
          <div class="ts-meta">${t.startDate || ''} ~ ${t.endDate || ''} · ${t.destination || ''}</div>
        </div>
        <div class="ts-budget" style="font-size:13px;font-weight:700;color:var(--text2)">${t.status === 'CONFIRMED' ? '확정' : '초안'}</div>
      </div>`;
    }).join('');

    _drawLedgerCardPager(totalPages);

    // 버튼 onclick을 실제 API 연동 함수로 교체
    const btn = document.querySelector('#ledger-selector .btn-next');
    if (btn) btn.onclick = goLedger2;
}

function _drawLedgerCardPager(totalPages) {
    const el = document.getElementById('ledger-card-pager');
    if (!el) return;
    if (totalPages <= 0) { el.innerHTML = ''; return; }

    const current = Math.min(Math.max(Number(_ledgerCardPage) || 1, 1), Number(totalPages));
    const pages = _getBudgetPagerWindow(current, totalPages, 5);

    const prev = totalPages > 1
        ? `<button class="pager-btn" onclick="_setLedgerCardPage(${Math.max(1, current - 1)})" ${current === 1 ? 'disabled' : ''}>&lt;</button>`
        : '';

    const next = totalPages > 1
        ? `<button class="pager-btn" onclick="_setLedgerCardPage(${Math.min(Number(totalPages), current + 1)})" ${current === Number(totalPages) ? 'disabled' : ''}>&gt;</button>`
        : '';

    const nums = pages
        .map(n => `<button class="pager-btn${n === current ? ' on' : ''}" onclick="_setLedgerCardPage(${n})">${n}</button>`)
        .join('');

    el.innerHTML = `<div class="ledger-pager" style="margin-top:10px;margin-bottom:4px">${prev}${nums}${next}</div>`;
}



function _setLedgerCardPage(n) {
    const totalPages = Math.ceil((_myTrips || []).length / _LEDGER_CARD_PAGE_SIZE);
    if (n < 1 || n > totalPages) return;
    _ledgerCardPage = n;
    _populateLedgerTripCards();
}

function _selLedgerCard(el, tripId) {
    document.querySelectorAll('#ledger-trip-cards .ts-card').forEach(c => c.classList.remove('on'));
    el.classList.add('on');
    _budgetSelectedTripId = tripId;
    sessionStorage.setItem('budgetSelectedTripId', tripId);  // [v2] 새로고침 복원용
}

/** '← 다른 여행 선택' — 가계부 확인 셀렉터(page_budget) 대신 마이페이지 가계부 탭으로 복귀 */
function returnToLedgerSelector() {
    // page_budget 의 자체 셀렉터 화면은 더 이상 쓰지 않고, 마이페이지 가계부 탭으로 돌아간다.
    if (typeof go === 'function') {
        go('mypage');
        // 마이페이지 진입 후 가계부 탭 활성화 + 목록 갱신
        setTimeout(function () {
            if (typeof showMySection === 'function') {
                var btn = document.querySelector('.my-menu[onclick*="ledger"]');
                showMySection('ledger', btn || null);
            }
            if (typeof updateLedgerList === 'function') updateLedgerList();
        }, 60);
    }
}

const _CATEGORY_MAP = {
    STAY:      { label: '숙박',  color: 'var(--sage)'  },
    FOOD:      { label: '식비',  color: 'var(--coral)' },
    TOUR:      { label: '관광',  color: 'var(--num)'   },
    CAFE:      { label: '카페',  color: 'var(--slate)' },
    /* 이동 구간 — 없으면 라벨 자리에 「TRANSPORT」가 그대로 찍힌다 */
    TRANSPORT: { label: '교통비', color: 'var(--ui-l)' }
};

function _fmtWon(n) {
    if (!n) return '0원';
    return Number(n).toLocaleString() + '원';
}

/** GET /api/trips/{tripId}/expenses → 가계부 상세 화면 렌더링 */
async function _loadExpenses(tripId) {
    const res = await api.get('/api/trips/' + tripId + '/expenses');
    if (!res.success) return;
    const d = res.data;
    _lastExpenseData = d;
    _updateExportButtons();

    const cats       = d.categoryBudgets  || [];
    const actualExps = d.actualExpenses   || [];
    const estExps    = d.estimatedExpenses || [];

    // 여행 정보 헤더
    const metaEl = document.getElementById('ledger-trip-meta');
    if (metaEl && d.tripTitle) metaEl.textContent = d.tripTitle;
    const destEl = document.getElementById('ledger-trip-dest');
    if (destEl) destEl.textContent = [d.destination, (d.startDate && d.endDate) ? d.startDate + ' ~ ' + d.endDate : null].filter(Boolean).join(' · ');

    /* 예상 총액이 왜 그 값인지 — 성수기·축제 한 줄 */
    _loadLedgerSeason({ startDate: d.startDate, endDate: d.endDate, destination: d.destination });

    // 미입력 카테고리 경고
    const estCatSet  = new Set(estExps.map(e => e.category));
    const actCatSet  = new Set(actualExps.map(e => e.category));
    const missingCats = [...estCatSet].filter(c => !actCatSet.has(c));
    const warnEl     = document.getElementById('ledger-warning');
    const warnCatsEl = document.getElementById('ledger-warn-cats');
    if (warnEl) {
        if (missingCats.length > 0) {
            const labels = missingCats.map(c => (_CATEGORY_MAP[c] || { label: c }).label);
            if (warnCatsEl) warnCatsEl.textContent = labels.join(', ');
            warnEl.style.display = 'block';
        } else {
            warnEl.style.display = 'none';
        }
    }

    // 요약 카드
    const totalEl  = document.getElementById('ledger-total');
    if (totalEl)  totalEl.textContent  = _fmtWon(d.totalEstimatedAmount);
    const actualEl = document.getElementById('ledger-actual');
    if (actualEl) actualEl.textContent = _fmtWon(d.totalActualAmount);
    const statusEl = document.getElementById('ledger-status');
    if (statusEl) {
        const actual = d.totalActualAmount || 0;
        const base   = d.budget || d.totalEstimatedAmount || 0;
        if (base > 0) {
            const remain = base - actual;
            statusEl.textContent = remain >= 0 ? _fmtWon(remain) : '-' + _fmtWon(-remain);
            statusEl.style.color = remain >= 0 ? 'var(--sage)' : 'var(--coral)';
        } else {
            statusEl.textContent = '-';
        }
    }
    // 설정 예산 기준 표시
    const budgetRefEl = document.getElementById('ledger-budget-ref');
    if (budgetRefEl) {
        if (d.budget) {
            budgetRefEl.textContent = '설정 예산 ' + _fmtWon(d.budget) + ' 기준';
            budgetRefEl.style.display = 'block';
        } else {
            budgetRefEl.style.display = 'none';
        }
    }

    // ── 예상 파이 차트 ──
    const estPieEl = document.getElementById('pie-estimated');
    const estLegEl = document.getElementById('pie-est-legend');
    if (cats.length > 0) {
        const totalEst = d.totalEstimatedAmount || 1;
        if (estLegEl) {
            estLegEl.innerHTML = cats.map(c => {
                const info = _CATEGORY_MAP[c.category] || { label: c.category, color: 'var(--ink-3)' };
                const pct  = Math.round((c.estimatedAmount || 0) / totalEst * 100) + '%';
                return `<div class="pie-leg-item"><div class="pie-dot" style="background:${info.color}"></div>${info.label} ${pct}</div>`;
            }).join('');
        }
        if (estPieEl) {
            let deg = 0;
            const segs = cats.map(c => {
                const info  = _CATEGORY_MAP[c.category] || { color: 'var(--ink-3)' };
                const start = deg;
                deg += ((c.estimatedAmount || 0) / totalEst) * 360;
                return `${info.color} ${Math.round(start)}deg ${Math.round(deg)}deg`;
            });
            estPieEl.style.background = `conic-gradient(${segs.join(', ')})`;
        }
    }

    // ── 실제 파이 차트 ──
    const actPieEl  = document.getElementById('pie-actual');
    const actLegEl  = document.getElementById('pie-act-legend');
    const totalAct  = d.totalActualAmount || 0;
    const actCats   = cats.filter(c => (c.actualAmount || 0) > 0);
    if (totalAct > 0 && actCats.length > 0) {
        if (actLegEl) {
            actLegEl.innerHTML = actCats.map(c => {
                const info = _CATEGORY_MAP[c.category] || { label: c.category, color: 'var(--ink-3)' };
                const pct  = Math.round((c.actualAmount || 0) / totalAct * 100) + '%';
                return `<div class="pie-leg-item"><div class="pie-dot" style="background:${info.color}"></div>${info.label} ${pct}</div>`;
            }).join('');
        }
        if (actPieEl) {
            let deg = 0;
            const segs = actCats.map(c => {
                const info  = _CATEGORY_MAP[c.category] || { color: 'var(--ink-3)' };
                const start = deg;
                deg += ((c.actualAmount || 0) / totalAct) * 360;
                return `${info.color} ${Math.round(start)}deg ${Math.round(deg)}deg`;
            });
            actPieEl.style.background = `conic-gradient(${segs.join(', ')})`;
        }
    } else {
        if (actPieEl) actPieEl.style.background = 'var(--ui-line-2)';
        if (actLegEl) actLegEl.innerHTML = '<div class="pie-leg-item" style="color:var(--text3)">실제 지출 없음</div>';
    }

    // ── 카테고리별 비교 막대 (풀 너비 2컬럼 그리드) ──
    const maxAmt = Math.max(...cats.map(c => Math.max(c.estimatedAmount || 0, c.actualAmount || 0)), 1);
    const listEl = document.getElementById('ledger-item-list');
    if (listEl) {
        if (cats.length === 0) {
    listEl.innerHTML = '<div style="color:var(--text3);font-size:13px;padding:20px 0;text-align:center">예상 비용을 불러오지 못했습니다.</div>';
        } else {
            const items = cats.map(c => {
                const info   = _CATEGORY_MAP[c.category] || { label: c.category, color: 'var(--ink-3)' };
                const estW   = Math.round((c.estimatedAmount || 0) / maxAmt * 100) + '%';
                const actW   = Math.round((c.actualAmount   || 0) / maxAmt * 100) + '%';
                const noAct  = !actCatSet.has(c.category);   // 0원 입력도 "입력됨"으로 처리
                const isOver = !noAct && (c.actualAmount > c.estimatedAmount);
                return `
          <div style="padding:12px 14px;background:var(--cream2);border-radius:10px">
            <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:8px">
              <div style="display:flex;align-items:center;gap:7px">
                <div style="width:10px;height:10px;border-radius:50%;background:${info.color};flex-shrink:0"></div>
                <span class="bi-label" style="margin:0">${info.label}${noAct ? ' <span style="font-size:10px;color:var(--ink-3);font-weight:400">미입력</span>' : ''}</span>
              </div>
              <div style="font-size:11px;color:var(--text3);text-align:right">
                예상 <strong style="color:var(--text2)">${_fmtWon(c.estimatedAmount)}</strong>
                &nbsp;/&nbsp; 실제 <strong style="color:${isOver ? 'var(--coral)' : 'var(--text)'}">${_fmtWon(c.actualAmount)}</strong>
              </div>
            </div>
            <div class="bi-bar-track" title="예상 지출"><div class="bi-bar-fill" style="width:${estW};background:${info.color};opacity:.35"></div></div>
            <div class="bi-bar-track" style="margin-top:4px" title="실제 지출"><div class="bi-bar-fill" style="width:${actW};background:${info.color}"></div></div>
            ${isOver ? `<div style="font-size:11px;color:var(--coral);margin-top:5px;font-weight:600">${_fmtWon(c.actualAmount - c.estimatedAmount)} 초과</div>` : ''}
          </div>`;
            }).join('');
            listEl.innerHTML = `<div style="display:grid;grid-template-columns:repeat(2,1fr);gap:12px">${items}</div>`;
        }
    }

    // ── 실제 지출 테이블 (페이지네이션) ──
    _allActualExps = actualExps;
    _expensePage   = 1;
    _drawExpensePage();

    // 지출 입력 카테고리 select
    const selEl = document.getElementById('ledger-exp-cat');
    if (selEl) {
        selEl.innerHTML = Object.entries(_CATEGORY_MAP)
            .map(([k, v]) => `<option value="${k}">${v.label}</option>`)
            .join('');
    }

    // 날짜 input — 기본값: 오늘, 범위: 여행 기간
    const dateEl = document.getElementById('ledger-exp-date');
    if (dateEl) {
        if (!dateEl.value) dateEl.value = new Date().toISOString().slice(0, 10);
        if (d.startDate) dateEl.min = d.startDate;
        if (d.endDate)   dateEl.max = d.endDate;
    }

    // 일정별 지출 현황 렌더링
    const routeRes = await api.get('/api/trips/' + tripId + '/routes');
    if (routeRes.success && routeRes.data) {
        try {
            const routeData = (typeof routeRes.data === 'string') ? JSON.parse(routeRes.data) : routeRes.data;
            _renderItinerary(routeData, actualExps, d.startDate);
        } catch(e) {
            console.error('라우트 파싱 에러:', e);
        }
    }

    // 여행 시작 전이면 지출 입력 비활성화
    const addPanel    = document.getElementById('ledger-add-panel');
    const notStarted  = document.getElementById('ledger-not-started');
    const addForm     = document.getElementById('ledger-add-form');
    if (addPanel && d.startDate) {
        const today      = new Date().toISOString().slice(0, 10);
        const tripStarted = d.startDate <= today;
        if (notStarted) notStarted.style.display = tripStarted ? 'none' : 'block';
        if (notStarted && !tripStarted) {
            notStarted.textContent = `여행 시작 전입니다. 지출 입력은 ${d.startDate.replace(/-/g, '.')} 이후부터 가능합니다.`;
        }
        if (addForm) addForm.style.display = tripStarted ? '' : 'none';
    }

    // 숙박비 근거 표 · 예측 정확도 — 실패해도 가계부 화면은 그대로 뜬다
    _loadBudgetEstimate(tripId);
    _loadBudgetAccuracy();
}

// 항목 상태 3종. 색만으로 구분하지 않고 기호를 같이 찍는다.
const _BASIS_STATUS = {
    CONFIRMED: { mark: '●', label: '확정',   color: 'var(--terra)' },
    ESTIMATED: { mark: '○', label: '추정',   color: 'var(--ink-3)' },
    NONE:      { mark: '—', label: '해당없음', color: 'var(--ink-3)' }
};

/** GET /api/trips/{tripId}/budget-estimate → 숙박비 항목별 근거 표 (예산 엔진 2층) */
async function _loadBudgetEstimate(tripId) {
    const card = document.getElementById('budget-basis-card');
    const rows = document.getElementById('budget-basis-rows');
    if (!card || !rows) return;

    // 숙소를 못 찾거나 API 가 죽으면 카드만 안 뜬다 — 가계부 화면은 그대로 산다
    const res = await api.get('/api/trips/' + tripId + '/budget-estimate');
    if (!res || !res.success || !res.data) return;

    const d     = res.data;
    const items = d.items || [];
    if (items.length === 0) return;

    const esc = v => String(v == null ? '' : v)
        .replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');

    const itemHtml = items.map(function (it) {
        const st = _BASIS_STATUS[it.status] || _BASIS_STATUS.NONE;
        return '<div style="display:grid;grid-template-columns:1fr auto auto;gap:10px;align-items:baseline;'
             +   'padding:9px 0;border-bottom:1px solid var(--ui-line)">'
             +   '<div>'
             +     '<div style="font-size:13px;color:var(--ink)">' + esc(it.label) + '</div>'
             +     '<div style="font-size:11px;color:var(--ink-3);margin-top:2px">' + esc(it.basis) + '</div>'
             +   '</div>'
             +   '<div style="font-size:13px;font-weight:700;color:' + st.color + ';text-align:right;'
             +     'font-variant-numeric:tabular-nums">'
             /* 해당없음은 0원이 아니다. 값이 없는 것과 0원인 것을 같은
                얼굴로 두면 「반려동물 요금 0원」이 「안 받는다」로 읽힌다.
                _fmtWon 은 공통 포맷터라 여기서만 갈라 쓴다. */
             +     (it.status === 'NONE' ? '\u2014' : _fmtWon(it.amount)) + '</div>'
             +   '<div style="font-size:11px;color:' + st.color + ';white-space:nowrap">'
             +     st.mark + ' ' + st.label + '</div>'
             + '</div>';
    }).join('');

    rows.innerHTML = itemHtml
        + '<div style="display:flex;justify-content:space-between;align-items:baseline;padding:12px 0 4px">'
        +   '<div style="font-size:13px;font-weight:800;color:var(--ink)">총액</div>'
        +   '<div style="font-size:18px;font-weight:800;color:var(--ink);font-variant-numeric:tabular-nums">'
        +     _fmtWon(d.total) + '</div>'
        + '</div>'
        // 추정 항목이 없으면 구간 폭이 0 이다 — 의미 없는 줄은 내보내지 않는다
        + (d.low === d.high ? '' :
            '<div style="display:flex;justify-content:space-between;align-items:baseline;font-size:12px;color:var(--ink-3)">'
          +   '<div>예측 구간</div>'
          +   '<div style="font-variant-numeric:tabular-nums">' + _fmtWon(d.low) + ' ~ ' + _fmtWon(d.high) + '</div>'
          + '</div>')
        + '<div style="display:flex;justify-content:space-between;align-items:baseline;font-size:12px;margin-top:6px">'
        +   '<div style="color:var(--ink-3)">신뢰도</div>'
        +   '<div style="font-weight:800;color:var(--terra)">' + (d.confidence || 0) + '%</div>'
        + '</div>'
        // 산출식을 같이 싣는다 — 근거 없는 정확도 수치는 화면에 올리지 않는다
        + '<div style="font-size:11px;color:var(--ink-3);margin-top:8px;line-height:1.5">'
        +   esc(d.note || '') + '</div>';

    card.style.display = '';
}

/** GET /api/trips/budget-accuracy → 예측 vs 실측 오차 표 (예산 엔진 3층) */
async function _loadBudgetAccuracy() {
    const card = document.getElementById('budget-accuracy-card');
    const rows = document.getElementById('budget-accuracy-rows');
    const nEl  = document.getElementById('budget-accuracy-n');
    if (!card || !rows) return;

    const res = await api.get('/api/trips/budget-accuracy');
    if (!res || !res.success || !res.data) return;

    const d = res.data;
    // 실측을 한 번도 입력하지 않았으면 오차가 아니라 빈 루프다 — 카드를 안 띄운다
    if (!d.rows || d.rows.length === 0) return;

    const esc = v => String(v == null ? '' : v)
        .replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');

    // 막대는 이 화면에서 가장 큰 오차를 100% 로 잡는다. 절대 기준이 아니라 비교용이다
    const maxErr = Math.max.apply(null, d.rows.map(r => Math.abs(r.errorPct))) || 1;

    rows.innerHTML = d.rows.map(function (r) {
        const over  = r.errorPct > 0;                            // 예측보다 더 썼다
        const color = over ? 'var(--terra)' : 'var(--ink-3)';
        const sign  = over ? '+' : '';
        return '<div style="padding:9px 0;border-bottom:1px solid var(--ui-line)">'
             +   '<div style="display:flex;justify-content:space-between;align-items:baseline;gap:10px">'
             +     '<div style="font-size:13px;color:var(--ink)">' + esc(r.tripTitle) + '</div>'
             +     '<div style="font-size:12px;font-weight:700;color:' + color + ';white-space:nowrap;'
             +       'font-variant-numeric:tabular-nums">' + sign + r.errorPct.toFixed(1) + '%</div>'
             +   '</div>'
             +   '<div style="font-size:11px;color:var(--ink-3);margin-top:2px;font-variant-numeric:tabular-nums">'
             +     '예측 ' + _fmtWon(r.predicted) + ' · 실제 ' + _fmtWon(r.actual) + '</div>'
             +   '<div style="height:6px;border-radius:3px;background:var(--ui-line);margin-top:6px">'
             +     '<div style="height:6px;border-radius:3px;background:' + color + ';width:'
             +       (Math.abs(r.errorPct) / maxErr * 100) + '%"></div>'
             +   '</div>'
             + '</div>';
    }).join('')
        + '<div style="display:flex;justify-content:space-between;align-items:baseline;padding:12px 0 4px">'
        +   '<div style="font-size:13px;font-weight:800;color:var(--ink)">누적 평균 오차</div>'
        +   '<div style="font-size:18px;font-weight:800;color:var(--terra);font-variant-numeric:tabular-nums">'
        +     d.avgErrorPct.toFixed(1) + '%</div>'
        + '</div>'
        // 산출식과 「시연」 표기를 같이 싣는다 — 근거 없는 정확도 수치는 화면에 올리지 않는다
        + '<div style="font-size:11px;color:var(--ink-3);margin-top:4px;line-height:1.5">'
        +   esc(d.note || '') + '</div>';

    if (nEl) nEl.textContent = '현재 N=' + d.n + ' · 시연';
    card.style.display = '';
}

// 일정별 장소 목록 + Day별 예상/실제 비교 렌더링
function _renderItinerary(routeData, actualExps, startDate) {
    const container = document.getElementById('ledger-itinerary');
    if (!container) return;
    if (!routeData || !routeData.length) { container.style.display = 'none'; return; }

    // 날짜별 실제 지출 합산
    const actByDate = {};
    actualExps.forEach(e => {
        if (e.date) actByDate[e.date] = (actByDate[e.date] || 0) + (e.amount || 0);
    });

    const typeIcon = { stay: '숙', food: '맛', cafe: '카', tour: '관', sight: '관', attraction: '관' };

    const html = routeData.map((day, idx) => {
        // Day N의 날짜 계산 (startDate + idx일)
        let dayDate = null;
        if (startDate) {
            const d = new Date(startDate);
            d.setDate(d.getDate() + idx);
            dayDate = d.toISOString().slice(0, 10);
        }

        // 장소별 예상 금액 파싱 및 합산
        const places = (day.places || []).filter(p => !p.transit);
        let estTotal = 0;
        places.forEach(p => {
            if (p.sub) {
                const m = p.sub.match(/₩\s*([\d,]+)(?:\s*[xX×*]\s*(\d+))?/);
                if (m) estTotal += (parseInt(m[1].replace(/,/g, '')) || 0) * parseInt(m[2] || '1');
            }
        });
        // 이동 비용도 포함
        (day.places || []).filter(p => p.transit).forEach(p => {
            const m = p.transit.match(/₩\s*([\d,]+)|([\d,]+)\s*원/);
            if (m) estTotal += parseInt(m[1].replace(/,/g, '')) || 0;
        });

        const actTotal = dayDate ? (actByDate[dayDate] || 0) : 0;
        const hasAct   = actTotal > 0;
        const diff     = actTotal - estTotal;

        const placeRows = places.map(p => {
            let estAmt = 0;
            if (p.sub) {
                const m = p.sub.match(/₩\s*([\d,]+)(?:\s*[xX×*]\s*(\d+))?/);
                if (m) estAmt = (parseInt(m[1].replace(/,/g, '')) || 0) * parseInt(m[2] || '1');
            }
            const icon = typeIcon[p.type] || '곳';
            return `
                <div style="display:flex;align-items:center;gap:8px;padding:7px 0;border-bottom:1px solid var(--border2)">
                    <span style="font-size:13px;width:18px;text-align:center">${icon}</span>
                    <span style="flex:1;font-size:12px;color:var(--text2);font-weight:500">${p.name || ''}</span>
                    ${estAmt > 0 ? `<span style="font-size:11px;color:var(--text3)">${_fmtWon(estAmt)}</span>` : ''}
                </div>`;
        }).join('');

        return `
            <div style="background:var(--surface);border:1.5px solid var(--border);border-radius:var(--r);overflow:hidden;margin-bottom:10px">
                <div style="display:flex;justify-content:space-between;align-items:center;padding:11px 16px;background:var(--cream2)">
                    <div>
                        <span style="font-size:13px;font-weight:700">${day.label || 'Day ' + day.day}</span>
                        ${dayDate ? `<span style="font-size:11px;color:var(--text3);margin-left:7px">${dayDate.replace(/-/g, '.')}</span>` : ''}
                    </div>
                    <div style="font-size:11px;text-align:right">
                        <span style="color:var(--text3)">예상 </span><strong>${_fmtWon(estTotal)}</strong>
                        ${hasAct ? `
                            <span style="margin-left:10px;color:var(--text3)">실제 </span>
                            <strong style="color:${diff > 0 ? 'var(--coral)' : 'var(--sage)'}">${_fmtWon(actTotal)}</strong>
                            <span style="margin-left:6px;font-weight:700;color:${diff > 0 ? 'var(--coral)' : 'var(--sage)'}">
                                ${diff > 0 ? '+' + _fmtWon(diff) + ' 초과' : '−' + _fmtWon(-diff) + ' 절약'}
                            </span>
                        ` : `<span style="margin-left:10px;color:var(--text3);font-size:10px">실제 미입력</span>`}
                    </div>
                </div>
                <div style="padding:4px 16px 8px">
                    ${placeRows || '<div style="font-size:12px;color:var(--text3);padding:8px 0">장소 정보 없음</div>'}
                </div>
            </div>`;
    }).join('');

    container.style.display = 'block';
    container.innerHTML = `
        <div style="font-size:13px;font-weight:700;color:var(--text);margin-bottom:10px">일정별 지출 현황</div>
        ${html}`;
}

/** 지도 페이지 예산 탭 → 가계부 페이지로 이동 */
function goToLedgerFromMap() {
    const tripId = window._currentTripId;
    if (!tripId) { toast('여행 정보를 불러오는 중입니다.'); return; }
    _budgetSelectedTripId = +tripId;
    sessionStorage.setItem('budgetSelectedTripId', tripId);
    go('ledger');
}

/** 지도 페이지 예산 탭 - 실제 가계부 API와 연동 */
async function _loadMapBudget() {
    const tripId = window._currentTripId;
    if (!tripId) return;

    const itemsEl   = document.getElementById('map-budget-items');
    const totalEl   = document.getElementById('map-budget-total');
    const remainEl  = document.getElementById('map-budget-remaining');
    const pieRing   = document.querySelector('#budgetView .pie-ring');
    const pieLegend = document.querySelector('#budgetView .pie-legend');

    if (itemsEl) itemsEl.innerHTML = '<div style="color:var(--text3);font-size:12px;padding:12px 0;text-align:center">불러오는 중...</div>';

    const res = await api.get('/api/trips/' + tripId + '/expenses');
    if (!res.success || !res.data) {
        if (itemsEl) itemsEl.innerHTML = '<div style="color:var(--coral);font-size:12px;padding:12px 0;text-align:center">데이터를 불러올 수 없습니다.</div>';
        return;
    }
    const d = res.data;

    const cats     = d.categoryBudgets || [];
    const totalEst = d.totalEstimatedAmount || 0;
    const totalAct = d.totalActualAmount    || 0;
    const hasAct   = totalAct > 0;
    const maxAmt   = Math.max(...cats.map(c => Math.max(c.estimatedAmount || 0, c.actualAmount || 0)), 1);

    // 바 차트 아이템
    if (itemsEl) {
        if (cats.length === 0) {
    itemsEl.innerHTML = '<div style="color:var(--text3);font-size:12px;padding:12px 0;text-align:center">예상 비용을 불러오지 못했습니다.</div>';
        } else {
            /* 막대가 두 줄인데 어느 쪽이 어느 쪽인지 화면이 말하지 않았다.
               title= 로만 달아 뒀는데 휴대폰에는 마우스를 올릴 수가 없다.
               실제 지출이 하나라도 있을 때만 붙인다 — 한 줄뿐이면 설명할 것이 없다. */
            const guide = hasAct ? `
          <div style="display:flex;gap:14px;align-items:center;font-size:11px;color:var(--text3);margin:0 0 12px">
            <span style="display:inline-flex;align-items:center;gap:5px">
              <i style="width:14px;height:5px;border-radius:4px;background:var(--ink-3);opacity:.4;display:inline-block"></i>예상</span>
            <span style="display:inline-flex;align-items:center;gap:5px">
              <i style="width:14px;height:5px;border-radius:4px;background:var(--ink-3);display:inline-block"></i>실제</span>
          </div>` : '';
            itemsEl.innerHTML = guide + cats.map(c => {
                const info   = _CATEGORY_MAP[c.category] || { label: c.category, color: 'var(--ink-3)' };
                const estW   = Math.round((c.estimatedAmount || 0) / maxAmt * 100) + '%';
                const actW   = Math.round((c.actualAmount   || 0) / maxAmt * 100) + '%';
                const isOver = (c.actualAmount || 0) > (c.estimatedAmount || 0);
                return `
          <div style="margin-bottom:14px">
            <div style="display:flex;justify-content:space-between;font-size:12px;margin-bottom:4px">
              <span style="font-weight:700">${info.label}</span>
              <span style="font-size:11px;color:var(--text3)">
                예상 <strong style="color:${info.color}">${_fmtWon(c.estimatedAmount)}</strong>
                ${hasAct ? ` · 실제 <strong style="color:${isOver ? 'var(--coral)' : 'var(--text)'}">${_fmtWon(c.actualAmount)}</strong>` : ''}
              </span>
            </div>
            <div style="background:var(--border2);border-radius:4px;height:5px;margin-bottom:${hasAct ? '3' : '0'}px" title="예상 지출">
              <div style="width:${estW};background:${info.color};opacity:.4;height:5px;border-radius:4px;transition:width .4s"></div>
            </div>
            ${hasAct ? `<div style="background:var(--border2);border-radius:4px;height:5px" title="실제 지출">
              <div style="width:${actW};background:${info.color};height:5px;border-radius:4px;transition:width .4s"></div>
            </div>` : ''}
            ${isOver && hasAct ? `<div style="font-size:10px;color:var(--coral);margin-top:3px;font-weight:600">${_fmtWon(c.actualAmount - c.estimatedAmount)} 초과</div>` : ''}
          </div>`;
            }).join('');
        }
    }

    // 총액 라벨 + 금액
    const totalLabelEl = document.getElementById('map-budget-total-label');
    if (totalLabelEl) totalLabelEl.textContent = hasAct ? '실제 총액' : '예상 총액';
    if (totalEl) {
        if (hasAct) {
            totalEl.innerHTML = `<span style="font-size:12px;color:var(--text3);font-weight:500">실제 </span>${_fmtWon(totalAct)}`;
        } else {
            totalEl.textContent = _fmtWon(totalEst);
        }
    }
    /* #totalBudget(지도 위 「전체 여행 예산」)은 건드리지 않는다. 그 칸은 동선에 적힌
       금액의 합이고, 여기 숫자는 가계부에 복사해 둔 예상 지출이라 출처가 다르다.
       덮으면 예산 탭을 한 번 열었다는 이유로 지도 숫자가 바뀐다 — 실제로 그렇게 보였다.
       가계부 쪽 총액은 아래 #map-budget-total 에만 쓴다 */

    // 잔여 예산
    if (remainEl) {
        const base  = d.budget || totalEst;
        const spent = hasAct ? totalAct : totalEst;
        if (base > 0) {
            const remain = base - spent;
            remainEl.textContent       = remain >= 0
      ? `예산에서 ${_fmtWon(remain)} 남음`
                : `예산 ${_fmtWon(-remain)} 초과`;
            remainEl.style.background  = remain >= 0 ? 'var(--sage-pale)' : 'var(--tile-pale)';
            remainEl.style.borderColor = remain >= 0 ? 'var(--sage-l)'    : 'var(--tile)';
            remainEl.style.color       = remain >= 0 ? 'var(--sage-d)'    : 'var(--coral)';
        } else {
            remainEl.textContent = hasAct ? '실제 지출 기준' : '예상 지출 기준';
        }
    }

    // 파이 차트
    const chartCats  = hasAct ? cats.filter(c => (c.actualAmount || 0) > 0)
        : cats.filter(c => (c.estimatedAmount || 0) > 0);
    const chartTotal = hasAct ? totalAct : totalEst;

    if (chartTotal > 0 && chartCats.length > 0) {
        let deg = 0;
        const segs = chartCats.map(c => {
            const info  = _CATEGORY_MAP[c.category] || { color: 'var(--ink-3)' };
            const amt   = hasAct ? (c.actualAmount || 0) : (c.estimatedAmount || 0);
            const start = deg;
            deg += (amt / chartTotal) * 360;
            return `${info.color} ${Math.round(start)}deg ${Math.round(deg)}deg`;
        });
        if (pieRing) {
            pieRing.style.background   = `conic-gradient(${segs.join(', ')})`;
            pieRing.style.borderRadius = '50%';
        }
        if (pieLegend) {
            pieLegend.innerHTML = chartCats.map(c => {
                const info = _CATEGORY_MAP[c.category] || { label: c.category, color: 'var(--ink-3)' };
                const amt  = hasAct ? (c.actualAmount || 0) : (c.estimatedAmount || 0);
                const pct  = Math.round(amt / chartTotal * 100);
                return `<div class="pie-leg-item"><div class="pie-dot" style="background:${info.color}"></div>${info.label} ${pct}%</div>`;
            }).join('');
        }
    } else {
        if (pieRing)   pieRing.style.background = 'var(--ui-line-2)';
        if (pieLegend) pieLegend.innerHTML = '<div class="pie-leg-item" style="color:var(--text3)">데이터 없음</div>';
    }
}

/** 현재 페이지의 지출 내역 테이블 렌더링 */
function _drawExpensePage() {
    const actTableEl = document.getElementById('ledger-act-table');
    if (!actTableEl) return;
    const total    = _allActualExps.length;
    const start    = (_expensePage - 1) * _EXP_PAGE_SIZE;
    const pageExps = _allActualExps.slice(start, start + _EXP_PAGE_SIZE);

    if (total === 0) {
        actTableEl.innerHTML = '<div style="color:var(--text3);font-size:12px;padding:12px 0">아직 입력된 실제 지출이 없습니다.</div>';
    } else {
        actTableEl.innerHTML = `
      <table style="width:100%;border-collapse:collapse;font-size:12px">
        <thead>
          <tr style="border-bottom:1.5px solid var(--border2);color:var(--text3)">
            <th style="text-align:left;padding:6px 4px;font-weight:600">날짜</th>
            <th style="text-align:left;padding:6px 4px;font-weight:600">카테고리</th>
            <th style="text-align:left;padding:6px 4px;font-weight:600">메모</th>
            <th style="text-align:right;padding:6px 4px;font-weight:600">금액</th>
            <th style="padding:6px 4px"></th>
          </tr>
        </thead>
        <tbody>
          ${pageExps.map(e => {
            const info = _CATEGORY_MAP[e.category] || { label: e.category, color: 'var(--ink-3)' };
            const safeDesc = (e.description || '').replace(/\\/g,'\\\\').replace(/'/g,"\\'");
            return `<tr id="exp-row-${e.id}" style="border-bottom:1px solid var(--border)">
              <td style="padding:7px 4px;color:var(--text3)">${e.date || '-'}</td>
              <td style="padding:7px 4px"><span style="background:${info.color}22;color:${info.color};border-radius:4px;padding:2px 8px;font-size:11px;font-weight:600">${info.label}</span></td>
              <td style="padding:7px 4px;color:var(--text2)">${e.description || '-'}</td>
              <td style="padding:7px 4px;text-align:right;font-weight:700">${_fmtWon(e.amount)}</td>
              <td style="padding:7px 4px;white-space:nowrap">
                <button onclick="startEditExpense(${e.id},'${e.category}',${e.amount},'${e.date || ''}','${safeDesc}')" style="font-size:11px;padding:3px 10px;background:var(--sage-pale);border:1.5px solid var(--sage-l);border-radius:5px;cursor:pointer;color:var(--sage-d);font-weight:600">수정</button>
                <button onclick="deleteExpense(${e.id})" style="font-size:11px;padding:3px 10px;background:var(--tile-pale);border:1.5px solid var(--tile);border-radius:5px;cursor:pointer;color:var(--tile);font-weight:600;margin-left:4px">삭제</button>
              </td>
            </tr>`;
        }).join('')}
        </tbody>
      </table>`;
    }
    _drawPagination(total);
}

function _drawPagination(total) {
    const el = document.getElementById('ledger-pagination');
    if (!el) return;
    const pages = Math.ceil(total / _EXP_PAGE_SIZE);
    if (pages <= 1) { el.innerHTML = ''; return; }
    const prev = `<button class="pager-btn" onclick="setExpensePage(${_expensePage - 1})" ${_expensePage === 1 ? 'disabled style="opacity:.4;cursor:default"' : ''}>‹</button>`;
    const next = `<button class="pager-btn" onclick="setExpensePage(${_expensePage + 1})" ${_expensePage === pages ? 'disabled style="opacity:.4;cursor:default"' : ''}>›</button>`;
    const nums = Array.from({ length: pages }, (_, i) => i + 1)
        .map(n => `<button class="pager-btn${n === _expensePage ? ' on' : ''}" onclick="setExpensePage(${n})">${n}</button>`)
        .join('');
    el.innerHTML = `<div class="ledger-pager">${prev}${nums}${next}</div>`;
}

function setExpensePage(n) {
    const pages = Math.ceil(_allActualExps.length / _EXP_PAGE_SIZE);
    if (n < 1 || n > pages) return;
    _expensePage = n;
    _drawExpensePage();
}

/** POST /api/trips/{tripId}/expenses → 실제 지출 저장 후 새로고침 */
async function addLedgerExpense() {
    if (!_budgetSelectedTripId) return;
    const cat  = document.getElementById('ledger-exp-cat')?.value;
    const amt  = document.getElementById('ledger-exp-amount')?.value;
    const date = document.getElementById('ledger-exp-date')?.value || null;
    const memo = document.getElementById('ledger-exp-memo')?.value?.trim() || null;
    if (!cat || amt === '' || +amt < 0) { toast('어디에 얼마를 썼는지 적어 주세요.'); return; }

    const payload = { category: cat, amount: +amt };
    if (date) payload.expenseDate = date;
    if (memo) payload.description = memo;

    const res = await api.post('/api/trips/' + _budgetSelectedTripId + '/expenses', payload);
    if (!res.success) { toast('지출을 저장하지 못했습니다. 입력 내용을 확인해 주세요.'); return; }

    document.getElementById('ledger-exp-amount').value = '';
    const memoEl = document.getElementById('ledger-exp-memo');
    if (memoEl) memoEl.value = '';
    toast('지출을 저장했습니다.');
    await _loadExpenses(_budgetSelectedTripId);
}

/** 지출 행을 인라인 수정 모드로 전환 */
function startEditExpense(id, category, amount, date, desc) {
    const row = document.getElementById('exp-row-' + id);
    if (!row) return;
    const catOptions = Object.entries(_CATEGORY_MAP)
        .map(([k, v]) => `<option value="${k}"${k === category ? ' selected' : ''}>${v.label}</option>`)
        .join('');
    row.innerHTML = `
    <td><input type="date" id="edit-date-${id}" value="${date}" ${_lastExpenseData&&_lastExpenseData.startDate?'min="'+_lastExpenseData.startDate+'"':''} ${_lastExpenseData&&_lastExpenseData.endDate?'max="'+_lastExpenseData.endDate+'"':''} style="width:108px;font-size:11px;padding:3px 4px;border:1px solid var(--border);border-radius:4px;background:var(--surface);color:var(--text)"></td>
    <td><select id="edit-cat-${id}" style="font-size:11px;padding:3px 4px;border:1px solid var(--border);border-radius:4px;background:var(--surface);color:var(--text)">${catOptions}</select></td>
    <td><input type="text" id="edit-desc-${id}" value="${desc}" placeholder="메모" style="width:100%;font-size:11px;padding:3px 4px;border:1px solid var(--border);border-radius:4px;background:var(--surface);color:var(--text)"></td>
    <td><input type="number" id="edit-amt-${id}" value="${amount}" min="0" style="width:80px;font-size:11px;padding:3px 4px;border:1px solid var(--border);border-radius:4px;background:var(--surface);color:var(--text)"></td>
    <td style="white-space:nowrap">
      <button onclick="saveEditExpense(${id})" style="font-size:10px;padding:2px 7px;background:var(--sage);color:var(--panel);border:none;border-radius:4px;cursor:pointer;margin-right:2px">저장</button>
      <button onclick="_loadExpenses(_budgetSelectedTripId)" style="font-size:10px;padding:2px 7px;background:none;border:1px solid var(--border);border-radius:4px;cursor:pointer;color:var(--text2)">취소</button>
    </td>
  `;
}

/** 인라인 수정 저장 → PUT /api/trips/{tripId}/expenses/{expenseId} */
async function saveEditExpense(id) {
    const category = document.getElementById('edit-cat-' + id)?.value;
    const amount   = document.getElementById('edit-amt-' + id)?.value;
    const date     = document.getElementById('edit-date-' + id)?.value || null;
    const desc     = document.getElementById('edit-desc-' + id)?.value?.trim() || null;
    if (!category || amount === '' || +amount < 0) { toast('어디에 얼마를 썼는지 다시 봐 주세요.'); return; }
    const payload = { category, amount: +amount };
    if (date) payload.expenseDate = date;
    if (desc) payload.description = desc;
    const res = await api.put('/api/trips/' + _budgetSelectedTripId + '/expenses/' + id, payload);
    if (!res.success) { toast('지출을 고치지 못했습니다. 입력 내용을 확인해 주세요.'); return; }
    toast('지출을 고쳤습니다.');
    await _loadExpenses(_budgetSelectedTripId);
}

//실제 지출 삭제 - DELETE /api/trips/{tripId}/expenses/{expenseId}
async function deleteExpense(id) {
    if (!confirm('이 지출 내역을 삭제하시겠습니까?')) return;
    const res = await api.del('/api/trips/' + _budgetSelectedTripId + '/expenses/' + id);
    if (!res.success) { toast('지출을 삭제하지 못했습니다. 다시 눌러 주세요.'); return; }
    toast('지출을 삭제했습니다.');
    await _loadExpenses(_budgetSelectedTripId);
}

function switchLedgerTab(tab, btn) {
    document.querySelectorAll('.ledger-tab').forEach(b => b.classList.remove('on'));
    btn.classList.add('on');
    document.getElementById('ledger-tab-charts').style.display  = tab === 'charts'  ? '' : 'none';
    document.getElementById('ledger-tab-history').style.display = tab === 'history' ? '' : 'none';
}

function _updateExportButtons() {
    const hasData = _lastExpenseData
        && (((_lastExpenseData.categoryBudgets || []).length > 0)
            || ((_lastExpenseData.actualExpenses || []).length > 0));
    const btns = document.querySelectorAll('.btn-export-header');
    btns.forEach(btn => {
        btn.disabled = !hasData;
        btn.title = hasData ? '' : '내보낼 가계부 데이터가 없습니다.';
    });
}

/** 가계부 PDF 자동 다운로드 (jsPDF + html2canvas) */
async function exportBudgetPDF() {
  if (!_lastExpenseData) { toast('가계부를 먼저 불러와 주세요.'); return; }
    /* PDF 라이브러리는 누를 때 받는다. 모든 화면에서 미리 받으면 550KB 를
       거저 쓴다 — 쓰는 곳은 이 함수 하나뿐이다. */
    if (typeof window.jspdf === 'undefined' || typeof html2canvas === 'undefined') {
        toast('PDF 를 만드는 중입니다.');
        const load = src => new Promise((ok, no) => {
            const s = document.createElement('script');
            s.src = src; s.onload = ok; s.onerror = no;
            document.head.appendChild(s);
        });
        try {
            await Promise.all([
                load('https://cdnjs.cloudflare.com/ajax/libs/jspdf/2.5.1/jspdf.umd.min.js'),
                load('https://cdnjs.cloudflare.com/ajax/libs/html2canvas/1.4.1/html2canvas.min.js')
            ]);
        } catch (e) {
            toast('PDF 를 만들지 못했습니다. 잠시 뒤에 다시 해 보세요.'); return;
        }
    }
    const d = _lastExpenseData;

    const catRows = (d.categoryBudgets || []).map(c => {
        const info = _CATEGORY_MAP[c.category] || { label: c.category };
        const diff = (c.actualAmount || 0) - (c.estimatedAmount || 0);
        return `<tr>
      <td style="padding:7px 10px;border-bottom:1px solid var(--ui-line-2)">${info.label}</td>
      <td style="padding:7px 10px;border-bottom:1px solid var(--ui-line-2);text-align:right">${_fmtWon(c.estimatedAmount)}</td>
      <td style="padding:7px 10px;border-bottom:1px solid var(--ui-line-2);text-align:right">${_fmtWon(c.actualAmount)}</td>
      <td style="padding:7px 10px;border-bottom:1px solid var(--ui-line-2);text-align:right;color:${diff > 0 ? 'var(--tile)' : 'var(--slate)'}">${diff > 0 ? '+' + _fmtWon(diff) : diff < 0 ? '-' + _fmtWon(-diff) : '-'}</td>
    </tr>`;
    }).join('');

    const actRows = (d.actualExpenses || []).length === 0
        ? '<tr><td colspan="4" style="padding:10px;text-align:center;color:var(--ink-3)">실제 지출 내역 없음</td></tr>'
        : (d.actualExpenses || []).map(e => {
            const info = _CATEGORY_MAP[e.category] || { label: e.category };
            return `<tr>
          <td style="padding:7px 10px;border-bottom:1px solid var(--ui-line-2)">${e.date || '-'}</td>
          <td style="padding:7px 10px;border-bottom:1px solid var(--ui-line-2)">${info.label}</td>
          <td style="padding:7px 10px;border-bottom:1px solid var(--ui-line-2)">${e.description || '-'}</td>
          <td style="padding:7px 10px;border-bottom:1px solid var(--ui-line-2);text-align:right;font-weight:700">${_fmtWon(e.amount)}</td>
        </tr>`;
        }).join('');

    const pdfDiv = document.getElementById('budget-pdf-content');
    if (!pdfDiv) return;

    const thStyle = 'padding:8px 10px;text-align:left;background:var(--ui-plate);font-weight:700;border-bottom:2px solid var(--ui-line-2)';
    pdfDiv.innerHTML = `
    <h1 style="font-size:22px;font-weight:900;margin:0 0 4px">가계부 리포트</h1>
    <p style="color:var(--ink-2);margin:0 0 6px;font-size:13px">${d.tripTitle || ''}${d.destination ? ' · ' + d.destination : ''}${d.startDate ? ' · ' + d.startDate + ' ~ ' + d.endDate : ''}</p>
    ${d.budget ? `<p style="color:var(--ink-2);margin:0 0 20px;font-size:12px">설정 예산: ${_fmtWon(d.budget)}</p>` : '<div style="margin-bottom:20px"></div>'}

    <h3 style="font-size:14px;font-weight:700;margin:0 0 8px;border-bottom:2px solid var(--ui-line-2);padding-bottom:6px">카테고리별 예산 비교</h3>
    <table style="width:100%;border-collapse:collapse;font-size:12px;margin-bottom:28px">
      <thead><tr>
        <th style="${thStyle}">카테고리</th>
        <th style="${thStyle};text-align:right">예상 금액</th>
        <th style="${thStyle};text-align:right">실제 지출</th>
        <th style="${thStyle};text-align:right">차이</th>
      </tr></thead>
      <tbody>${catRows}</tbody>
      <tfoot><tr style="font-weight:900;background:var(--ui-plate)">
        <td style="padding:8px 10px;border-top:2px solid var(--ui-line-2)">합계</td>
        <td style="padding:8px 10px;border-top:2px solid var(--ui-line-2);text-align:right">${_fmtWon(d.totalEstimatedAmount)}</td>
        <td style="padding:8px 10px;border-top:2px solid var(--ui-line-2);text-align:right">${_fmtWon(d.totalActualAmount)}</td>
        <td style="padding:8px 10px;border-top:2px solid var(--ui-line-2)"></td>
      </tr></tfoot>
    </table>

    <h3 style="font-size:14px;font-weight:700;margin:0 0 8px;border-bottom:2px solid var(--ui-line-2);padding-bottom:6px">실제 지출 상세 내역</h3>
    <table style="width:100%;border-collapse:collapse;font-size:12px">
      <thead><tr>
        <th style="${thStyle}">날짜</th>
        <th style="${thStyle}">카테고리</th>
        <th style="${thStyle}">메모</th>
        <th style="${thStyle};text-align:right">금액</th>
      </tr></thead>
      <tbody>${actRows}</tbody>
    </table>`;

    toast('PDF 생성 중...');
    try {
        const canvas   = await html2canvas(pdfDiv, { scale: 2, useCORS: true, backgroundColor: 'var(--panel)' });
        const { jsPDF } = window.jspdf;
        const doc      = new jsPDF({ orientation: 'portrait', unit: 'mm', format: 'a4' });
        const pageW    = doc.internal.pageSize.getWidth();
        const pageH    = doc.internal.pageSize.getHeight();
        const margin   = 10;
        const imgW     = pageW - margin * 2;
        const ratio    = canvas.width / imgW;
        const pageImgH = (pageH - margin * 2) * ratio;

        let srcY = 0;
        while (srcY < canvas.height) {
            if (srcY > 0) doc.addPage();
            const sliceH = Math.min(pageImgH, canvas.height - srcY);
            const slice  = document.createElement('canvas');
            slice.width  = canvas.width;
            slice.height = sliceH;
            slice.getContext('2d').drawImage(canvas, 0, srcY, canvas.width, sliceH, 0, 0, canvas.width, sliceH);
            doc.addImage(slice.toDataURL('image/png'), 'PNG', margin, margin, imgW, sliceH / ratio);
            srcY += pageImgH;
        }

        doc.save('가계부_' + (d.tripTitle || 'report') + '.pdf');
        toast('PDF 를 내려받았습니다.');
    } catch (e) {
        console.error(e);
    toast('PDF를 만들지 못했습니다. 다시 눌러 주세요.');
    }
}

/** 가계부 Excel 다운로드 (XML SpreadsheetML — 색상·열 너비 포함) */
function exportBudgetCSV() {
  if (!_lastExpenseData) { toast('가계부를 먼저 불러와 주세요.'); return; }
    const d = _lastExpenseData;

    const esc = v => String(v == null ? '' : v)
        .replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
    // 텍스트 셀
    const sc = (val, sid='') =>
        `<Cell${sid ? ` ss:StyleID="${sid}"` : ''}><Data ss:Type="String">${esc(val)}</Data></Cell>`;
    // 숫자 셀
    const nc = (val, sid='') =>
        `<Cell${sid ? ` ss:StyleID="${sid}"` : ''}><Data ss:Type="Number">${+val||0}</Data></Cell>`;
    // 병합 셀 (span = 합칠 열 수)
    const mc = (val, span, sid='') =>
        `<Cell ss:MergeAcross="${span-1}"${sid ? ` ss:StyleID="${sid}"` : ''}><Data ss:Type="String">${esc(val)}</Data></Cell>`;
    const ec = '<Cell/>';

    const rows = [];

    // ── 메타 정보 ──
    rows.push(`<Row ss:Height="26">${mc('가계부 리포트', 4, 'ttl')}</Row>`);
    if (d.tripTitle)   rows.push(`<Row ss:Height="20">${mc(d.tripTitle, 4, 'meta')}</Row>`);
    if (d.destination) rows.push(`<Row>${sc('목적지','lbl')}${sc(d.destination,'metaV')}${ec}${ec}</Row>`);
    if (d.startDate)   rows.push(`<Row>${sc('기간','lbl')}${sc(d.startDate+' ~ '+d.endDate,'metaV')}${ec}${ec}</Row>`);
    if (d.budget)      rows.push(`<Row>${sc('설정 예산','lbl')}${nc(d.budget,'metaV')}${ec}${ec}</Row>`);
    rows.push(`<Row ss:Height="10"/>`);

    // ── 카테고리별 비교 ──
    rows.push(`<Row ss:Height="22">${mc('[카테고리별 비교]', 4, 'sec')}</Row>`);
    rows.push(`<Row ss:Height="20">${sc('카테고리','hdr')}${sc('예상 금액(원)','hdrR')}${sc('실제 지출(원)','hdrR')}${sc('차이(원)','hdrR')}</Row>`);
    (d.categoryBudgets || []).forEach(c => {
        const info = _CATEGORY_MAP[c.category] || { label: c.category };
        const diff = (c.actualAmount || 0) - (c.estimatedAmount || 0);
        rows.push(`<Row>${sc(info.label)}${nc(c.estimatedAmount||0,'numR')}${nc(c.actualAmount||0,'numR')}${nc(diff, diff > 0 ? 'over' : 'numR')}</Row>`);
    });
    const totalDiff = (d.totalActualAmount||0) - (d.totalEstimatedAmount||0);
    rows.push(`<Row ss:Height="20">${sc('합계','sumLbl')}${nc(d.totalEstimatedAmount||0,'sumNum')}${nc(d.totalActualAmount||0,'sumNum')}${nc(totalDiff, totalDiff > 0 ? 'sumOver' : 'sumNum')}</Row>`);
    rows.push(`<Row ss:Height="10"/>`);

    // ── 실제 지출 상세 내역 ──
    rows.push(`<Row ss:Height="22">${mc('[실제 지출 상세 내역]', 4, 'sec')}</Row>`);
    rows.push(`<Row ss:Height="20">${sc('날짜','hdr')}${sc('카테고리','hdr')}${sc('메모','hdr')}${sc('금액(원)','hdrR')}</Row>`);
    if (!(d.actualExpenses||[]).length) {
        rows.push(`<Row>${mc('(내역 없음)', 4)}</Row>`);
    } else {
        (d.actualExpenses||[]).forEach((e, i) => {
            const info = _CATEGORY_MAP[e.category] || { label: e.category };
            const rs = i % 2 === 1 ? 'stripe' : '';
            rows.push(`<Row>${sc(e.date||'',rs)}${sc(info.label,rs)}${sc(e.description||'',rs)}${nc(e.amount||0, rs ? 'stripeR' : 'numR')}</Row>`);
        });
    }

    const xml = `<?xml version="1.0" encoding="UTF-8"?>
<?mso-application progid="Excel.Sheet"?>
<Workbook xmlns="urn:schemas-microsoft-com:office:spreadsheet"
  xmlns:ss="urn:schemas-microsoft-com:office:spreadsheet"
  xmlns:x="urn:schemas-microsoft-com:office:excel">
<Styles>
  <Style ss:ID="ttl">
    <Font ss:Bold="1" ss:Size="14" ss:Color="var(--panel)"/>
    <Interior ss:Color="var(--num)" ss:Pattern="Solid"/>
    <Alignment ss:Horizontal="Left" ss:Vertical="Center"/>
  </Style>
  <Style ss:ID="meta">
    <Font ss:Bold="1" ss:Size="12" ss:Color="var(--sig-deep)"/>
    <Alignment ss:Horizontal="Left"/>
  </Style>
  <Style ss:ID="lbl">
    <Font ss:Bold="1" ss:Color="var(--ink-2)"/>
    <Interior ss:Color="var(--ui-plate)" ss:Pattern="Solid"/>
  </Style>
  <Style ss:ID="metaV">
    <Font ss:Color="var(--ink)"/>
  </Style>
  <Style ss:ID="sec">
    <Font ss:Bold="1" ss:Size="11" ss:Color="var(--panel)"/>
    <Interior ss:Color="var(--ui-l)" ss:Pattern="Solid"/>
    <Alignment ss:Horizontal="Left" ss:Vertical="Center"/>
  </Style>
  <Style ss:ID="hdr">
    <Font ss:Bold="1" ss:Color="var(--sig-deep)"/>
    <Interior ss:Color="var(--ui-line)" ss:Pattern="Solid"/>
    <Borders>
      <Border ss:Position="Bottom" ss:LineStyle="Continuous" ss:Weight="2" ss:Color="var(--ui-l)"/>
    </Borders>
  </Style>
  <Style ss:ID="hdrR">
    <Font ss:Bold="1" ss:Color="var(--sig-deep)"/>
    <Interior ss:Color="var(--ui-line)" ss:Pattern="Solid"/>
    <Alignment ss:Horizontal="Right"/>
    <Borders>
      <Border ss:Position="Bottom" ss:LineStyle="Continuous" ss:Weight="2" ss:Color="var(--ui-l)"/>
    </Borders>
  </Style>
  <Style ss:ID="numR">
    <Alignment ss:Horizontal="Right"/>
    <NumberFormat ss:Format="#,##0"/>
  </Style>
  <Style ss:ID="over">
    <Alignment ss:Horizontal="Right"/>
    <Font ss:Color="var(--tile)"/>
    <NumberFormat ss:Format="#,##0"/>
  </Style>
  <Style ss:ID="sumLbl">
    <Font ss:Bold="1" ss:Color="var(--sig-deep)"/>
    <Interior ss:Color="var(--terra-pale)" ss:Pattern="Solid"/>
    <Borders>
      <Border ss:Position="Top" ss:LineStyle="Continuous" ss:Weight="2" ss:Color="var(--ui-l)"/>
    </Borders>
  </Style>
  <Style ss:ID="sumNum">
    <Font ss:Bold="1" ss:Color="var(--sig-deep)"/>
    <Interior ss:Color="var(--terra-pale)" ss:Pattern="Solid"/>
    <Alignment ss:Horizontal="Right"/>
    <NumberFormat ss:Format="#,##0"/>
    <Borders>
      <Border ss:Position="Top" ss:LineStyle="Continuous" ss:Weight="2" ss:Color="var(--ui-l)"/>
    </Borders>
  </Style>
  <Style ss:ID="sumOver">
    <Font ss:Bold="1" ss:Color="var(--tile)"/>
    <Interior ss:Color="var(--terra-pale)" ss:Pattern="Solid"/>
    <Alignment ss:Horizontal="Right"/>
    <NumberFormat ss:Format="#,##0"/>
    <Borders>
      <Border ss:Position="Top" ss:LineStyle="Continuous" ss:Weight="2" ss:Color="var(--ui-l)"/>
    </Borders>
  </Style>
  <Style ss:ID="stripe">
    <Interior ss:Color="var(--ui-plate)" ss:Pattern="Solid"/>
  </Style>
  <Style ss:ID="stripeR">
    <Interior ss:Color="var(--ui-plate)" ss:Pattern="Solid"/>
    <Alignment ss:Horizontal="Right"/>
    <NumberFormat ss:Format="#,##0"/>
  </Style>
</Styles>
<Worksheet ss:Name="가계부">
<Table ss:DefaultRowHeight="18">
  <Column ss:Width="80"/>
  <Column ss:Width="110"/>
  <Column ss:Width="160"/>
  <Column ss:Width="110"/>
  ${rows.join('\n  ')}
</Table>
</Worksheet>
</Workbook>`;

    const blob = new Blob([xml], { type: 'application/vnd.ms-excel;charset=utf-8' });
    const a    = document.createElement('a');
    a.href     = URL.createObjectURL(blob);
    a.download = '가계부_' + (d.tripTitle || 'report') + '.xls';
    a.click();
    toast('Excel 다운로드 시작...');
}

/* ── 성수기·축제 ────────────────────────────────────────────────
   예상 총액이 왜 그 값인지 한 줄로 말한다. 8월 제주 여행의 예상이
   높은 것은 렌터카가 두 배가 되기 때문인데 그 말이 없으면 숫자를 믿을
   근거가 없다. 성수기도 축제도 없으면 줄 자체를 감춘다 —
   「평시입니다」는 알려 줄 것이 없다는 말이다. */
async function _loadLedgerSeason(trip) {
    const el = document.getElementById('ledger-season');
    if (!el) return;
    el.hidden = true;
    if (!trip || !trip.startDate) return;

    const from = String(trip.startDate).slice(0, 10);
    const to = String(trip.endDate || trip.startDate).slice(0, 10);
    const region = String(trip.destination || '').trim();

    let season = null, festivals = [];
    try {
        const r = await fetch('/api/budget/season?from=' + from + '&to=' + to);
        const j = await r.json();
        if (j && j.success) season = j.data;
    } catch (e) {}
    if (region) {
        try {
            const r2 = await fetch('/api/budget/festivals?region=' + encodeURIComponent(region) +
                '&from=' + from + '&to=' + to);
            const j2 = await r2.json();
            if (j2 && j2.success) festivals = j2.data || [];
        } catch (e) {}
    }

    const bits = [];
    if (season && season.key !== 'off') {
        let s = '<b>' + season.label + '</b>에 가는 여행입니다.';
        if (season.carMultiplier > 1) {
            s += ' 렌터카가 평시의 ' + season.carMultiplier.toFixed(2) + '배, ';
        }
        s += '숙소도 같이 오릅니다.';
        bits.push(s);
    } else if (season && season.weekendCheckIn) {
        bits.push('금·토 체크인이라 숙박이 주말 요금입니다.');
    }
    if (festivals.length) {
        bits.push('여행 기간에 <b>' + _ldEsc(festivals[0].title) + '</b>' +
            (festivals.length > 1 ? ' 등 축제 ' + festivals.length + '개' : '') +
            '가 열립니다. 숙소가 빨리 찹니다.');
    }

    if (!bits.length) return;          /* 알려 줄 것이 없으면 줄을 두지 않는다 */
    el.innerHTML = bits.join(' ');
    el.hidden = false;
}

function _ldEsc(s) {
    return String(s == null ? '' : s)
        .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}
window._loadLedgerSeason = _loadLedgerSeason;
