/** _myTrips를 기반으로 가계부 여행 선택 UI 렌더링 (페이지네이션 포함) */
async function updateLedgerList() {
    if (!_currentUser) return;
    const listEl = document.getElementById('my-ledger-list');
    if (!listEl) return;

    if (!_budgetSelectedTripId && _myTrips.length > 0) {
        _budgetSelectedTripId = _myTrips[0].tripId;
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
        const isSel = (_budgetSelectedTripId === l.tripId);
        return `
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
    }).join('');

    _drawMyLedgerPager(totalPages);
}

function _drawMyLedgerPager(totalPages) {
    const el = document.getElementById('my-ledger-pager');
    if (!el) return;
    if (totalPages <= 1) { el.innerHTML = ''; return; }
    const prev = `<button class="pager-btn" onclick="_setMyLedgerPage(${_myLedgerPage - 1})" ${_myLedgerPage === 1 ? 'disabled style="opacity:.4;cursor:default"' : ''}>‹</button>`;
    const next = `<button class="pager-btn" onclick="_setMyLedgerPage(${_myLedgerPage + 1})" ${_myLedgerPage === totalPages ? 'disabled style="opacity:.4;cursor:default"' : ''}>›</button>`;
    const nums = Array.from({ length: totalPages }, (_, i) => i + 1)
        .map(n => `<button class="pager-btn${n === _myLedgerPage ? ' on' : ''}" onclick="_setMyLedgerPage(${n})">${n}</button>`)
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
        container.innerHTML = '<div style="color:var(--text3);font-size:13px;padding:20px 0;text-align:center">등록된 여행이 없습니다.</div>';
        _drawLedgerCardPager(0);
        return;
    }
    const total = _myTrips.length;
    const totalPages = Math.ceil(total / _LEDGER_CARD_PAGE_SIZE);
    if (_ledgerCardPage > totalPages) _ledgerCardPage = totalPages;
    const start = (_ledgerCardPage - 1) * _LEDGER_CARD_PAGE_SIZE;
    const pageTrips = _myTrips.slice(start, start + _LEDGER_CARD_PAGE_SIZE);

    container.innerHTML = pageTrips.map(t => {
        const isSel = (_budgetSelectedTripId === t.tripId);
        return `
      <div class="ts-card${isSel ? ' on' : ''}" onclick="_selLedgerCard(this, ${t.tripId})">
        <div class="ts-thumb">🗺️</div>
        <div class="ts-info">
          <div class="ts-name">${t.title || '여행 플랜'}</div>
          <div class="ts-meta">${t.startDate || ''} ~ ${t.endDate || ''} · ${t.destination || ''}</div>
        </div>
        <div class="ts-budget" style="font-size:13px;font-weight:700;color:var(--text2)">${t.status === 'CONFIRMED' ? '✅ 확정' : '📝 초안'}</div>
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
    if (totalPages <= 1) { el.innerHTML = ''; return; }
    const prev = `<button class="pager-btn" onclick="_setLedgerCardPage(${_ledgerCardPage - 1})" ${_ledgerCardPage === 1 ? 'disabled style="opacity:.4;cursor:default"' : ''}>‹</button>`;
    const next = `<button class="pager-btn" onclick="_setLedgerCardPage(${_ledgerCardPage + 1})" ${_ledgerCardPage === totalPages ? 'disabled style="opacity:.4;cursor:default"' : ''}>›</button>`;
    const nums = Array.from({ length: totalPages }, (_, i) => i + 1)
        .map(n => `<button class="pager-btn${n === _ledgerCardPage ? ' on' : ''}" onclick="_setLedgerCardPage(${n})">${n}</button>`)
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

/** page_budget.html의 returnToLedgerSelector() 오버라이드 — 실제 데이터 사용 */
function returnToLedgerSelector() {
    document.getElementById('ledger-main').style.display = 'none';
    const selEl = document.querySelector('.ledger-selector-outer');
    if (selEl) selEl.style.display = 'block';
    _populateLedgerTripCards();
}

const _CATEGORY_MAP = {
    STAY: { label: '숙박', color: 'var(--sage)' },
    FOOD: { label: '식비', color: 'var(--coral)' },
    TOUR: { label: '관광', color: '#F5A623' },
    CAFE: { label: '카페', color: '#22B5C4' }
};

function _fmtWon(n) {
    if (!n) return '₩0';
    return '₩' + Number(n).toLocaleString();
}

/** GET /api/trips/{tripId}/expenses → 가계부 상세 화면 렌더링 */
async function _loadExpenses(tripId) {
    const res = await api.get('/api/trips/' + tripId + '/expenses');
    if (!res.success) return;
    const d = res.data;
    _lastExpenseData = d;

    const cats       = d.categoryBudgets  || [];
    const actualExps = d.actualExpenses   || [];
    const estExps    = d.estimatedExpenses || [];

    // 여행 정보 헤더
    const metaEl = document.getElementById('ledger-trip-meta');
    if (metaEl && d.tripTitle) metaEl.textContent = d.tripTitle;
    const destEl = document.getElementById('ledger-trip-dest');
    if (destEl) destEl.textContent = [d.destination, (d.startDate && d.endDate) ? d.startDate + ' ~ ' + d.endDate : null].filter(Boolean).join(' · ');

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
                const info = _CATEGORY_MAP[c.category] || { label: c.category, color: '#aaa' };
                const pct  = Math.round((c.estimatedAmount || 0) / totalEst * 100) + '%';
                return `<div class="pie-leg-item"><div class="pie-dot" style="background:${info.color}"></div>${info.label} ${pct}</div>`;
            }).join('');
        }
        if (estPieEl) {
            let deg = 0;
            const segs = cats.map(c => {
                const info  = _CATEGORY_MAP[c.category] || { color: '#aaa' };
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
                const info = _CATEGORY_MAP[c.category] || { label: c.category, color: '#aaa' };
                const pct  = Math.round((c.actualAmount || 0) / totalAct * 100) + '%';
                return `<div class="pie-leg-item"><div class="pie-dot" style="background:${info.color}"></div>${info.label} ${pct}</div>`;
            }).join('');
        }
        if (actPieEl) {
            let deg = 0;
            const segs = actCats.map(c => {
                const info  = _CATEGORY_MAP[c.category] || { color: '#aaa' };
                const start = deg;
                deg += ((c.actualAmount || 0) / totalAct) * 360;
                return `${info.color} ${Math.round(start)}deg ${Math.round(deg)}deg`;
            });
            actPieEl.style.background = `conic-gradient(${segs.join(', ')})`;
        }
    } else {
        if (actPieEl) actPieEl.style.background = '#E5E7EB';
        if (actLegEl) actLegEl.innerHTML = '<div class="pie-leg-item" style="color:var(--text3)">실제 지출 없음</div>';
    }

    // ── 카테고리별 비교 막대 (풀 너비 2컬럼 그리드) ──
    const maxAmt = Math.max(...cats.map(c => Math.max(c.estimatedAmount || 0, c.actualAmount || 0)), 1);
    const listEl = document.getElementById('ledger-item-list');
    if (listEl) {
        if (cats.length === 0) {
            listEl.innerHTML = '<div style="color:var(--text3);font-size:13px;padding:20px 0;text-align:center">AI 예상 비용 데이터가 없습니다.</div>';
        } else {
            const items = cats.map(c => {
                const info   = _CATEGORY_MAP[c.category] || { label: c.category, color: '#aaa' };
                const estW   = Math.round((c.estimatedAmount || 0) / maxAmt * 100) + '%';
                const actW   = Math.round((c.actualAmount   || 0) / maxAmt * 100) + '%';
                const noAct  = !actCatSet.has(c.category);   // 0원 입력도 "입력됨"으로 처리
                const isOver = !noAct && (c.actualAmount > c.estimatedAmount);
                return `
          <div style="padding:12px 14px;background:var(--cream2);border-radius:10px">
            <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:8px">
              <div style="display:flex;align-items:center;gap:7px">
                <div style="width:10px;height:10px;border-radius:50%;background:${info.color};flex-shrink:0"></div>
                <span class="bi-label" style="margin:0">${info.label}${noAct ? ' <span style="font-size:10px;color:#9CA3AF;font-weight:400">미입력</span>' : ''}</span>
              </div>
              <div style="font-size:11px;color:var(--text3);text-align:right">
                예상 <strong style="color:var(--text2)">${_fmtWon(c.estimatedAmount)}</strong>
                &nbsp;/&nbsp; 실제 <strong style="color:${isOver ? 'var(--coral)' : 'var(--text)'}">${_fmtWon(c.actualAmount)}</strong>
              </div>
            </div>
            <div class="bi-bar-track" title="예상 지출"><div class="bi-bar-fill" style="width:${estW};background:${info.color};opacity:.35"></div></div>
            <div class="bi-bar-track" style="margin-top:4px" title="실제 지출"><div class="bi-bar-fill" style="width:${actW};background:${info.color}"></div></div>
            ${isOver ? `<div style="font-size:11px;color:var(--coral);margin-top:5px;font-weight:600">⚠ ${_fmtWon(c.actualAmount - c.estimatedAmount)} 초과</div>` : ''}
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
            notStarted.textContent = `✈️ 여행 시작 전입니다. 지출 입력은 ${d.startDate.replace(/-/g, '.')} 이후부터 가능합니다.`;
        }
        if (addForm) addForm.style.display = tripStarted ? '' : 'none';
    }
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

    const typeIcon = { stay: '🏨', food: '🍽️', cafe: '☕', tour: '📍', sight: '📍', attraction: '📍' };

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
            const m = p.transit.match(/₩\s*([\d,]+)/);
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
            const icon = typeIcon[p.type] || '📍';
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
                                ${diff > 0 ? '⚠ ' + _fmtWon(diff) + ' 초과' : '✓ ' + _fmtWon(-diff) + ' 절약'}
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
        <div style="font-size:13px;font-weight:700;color:var(--text);margin-bottom:10px">📅 일정별 지출 현황</div>
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
            itemsEl.innerHTML = '<div style="color:var(--text3);font-size:12px;padding:12px 0;text-align:center">AI 예상 비용 데이터가 없습니다.</div>';
        } else {
            itemsEl.innerHTML = cats.map(c => {
                const info   = _CATEGORY_MAP[c.category] || { label: c.category, color: '#aaa' };
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
            ${isOver && hasAct ? `<div style="font-size:10px;color:var(--coral);margin-top:3px;font-weight:600">⚠ ${_fmtWon(c.actualAmount - c.estimatedAmount)} 초과</div>` : ''}
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
    const topBudgetEl = document.getElementById('totalBudget');
    if (topBudgetEl) topBudgetEl.textContent = hasAct ? _fmtWon(totalAct) : _fmtWon(totalEst);

    // 잔여 예산
    if (remainEl) {
        const base  = d.budget || totalEst;
        const spent = hasAct ? totalAct : totalEst;
        if (base > 0) {
            const remain = base - spent;
            remainEl.textContent       = remain >= 0
                ? `예산 범위 내 ✓ 잔여 ${_fmtWon(remain)}`
                : `⚠️ 예산 ${_fmtWon(-remain)} 초과`;
            remainEl.style.background  = remain >= 0 ? 'var(--sage-pale)' : '#FEF3F2';
            remainEl.style.borderColor = remain >= 0 ? 'var(--sage-l)'    : '#FECACA';
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
            const info  = _CATEGORY_MAP[c.category] || { color: '#aaa' };
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
                const info = _CATEGORY_MAP[c.category] || { label: c.category, color: '#aaa' };
                const amt  = hasAct ? (c.actualAmount || 0) : (c.estimatedAmount || 0);
                const pct  = Math.round(amt / chartTotal * 100);
                return `<div class="pie-leg-item"><div class="pie-dot" style="background:${info.color}"></div>${info.label} ${pct}%</div>`;
            }).join('');
        }
    } else {
        if (pieRing)   pieRing.style.background = '#E5E7EB';
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
            const info = _CATEGORY_MAP[e.category] || { label: e.category, color: '#aaa' };
            const safeDesc = (e.description || '').replace(/\\/g,'\\\\').replace(/'/g,"\\'");
            return `<tr id="exp-row-${e.id}" style="border-bottom:1px solid var(--border)">
              <td style="padding:7px 4px;color:var(--text3)">${e.date || '-'}</td>
              <td style="padding:7px 4px"><span style="background:${info.color}22;color:${info.color};border-radius:4px;padding:2px 8px;font-size:11px;font-weight:600">${info.label}</span></td>
              <td style="padding:7px 4px;color:var(--text2)">${e.description || '-'}</td>
              <td style="padding:7px 4px;text-align:right;font-weight:700">${_fmtWon(e.amount)}</td>
              <td style="padding:7px 4px;white-space:nowrap">
                <button onclick="startEditExpense(${e.id},'${e.category}',${e.amount},'${e.date || ''}','${safeDesc}')" style="font-size:11px;padding:3px 10px;background:var(--sage-pale);border:1.5px solid var(--sage-l);border-radius:5px;cursor:pointer;color:var(--sage-d);font-weight:600">수정</button>
                <button onclick="deleteExpense(${e.id})" style="font-size:11px;padding:3px 10px;background:#FEF2F2;border:1.5px solid #FECACA;border-radius:5px;cursor:pointer;color:#DC2626;font-weight:600;margin-left:4px">삭제</button>
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
    if (!cat || amt === '' || +amt < 0) { toast('카테고리와 금액을 입력해주세요.'); return; }

    const payload = { category: cat, amount: +amt };
    if (date) payload.expenseDate = date;
    if (memo) payload.description = memo;

    const res = await api.post('/api/trips/' + _budgetSelectedTripId + '/expenses', payload);
    if (!res.success) { toast('저장 실패: ' + res.message); return; }

    document.getElementById('ledger-exp-amount').value = '';
    const memoEl = document.getElementById('ledger-exp-memo');
    if (memoEl) memoEl.value = '';
    toast('지출이 저장됐습니다.');
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
      <button onclick="saveEditExpense(${id})" style="font-size:10px;padding:2px 7px;background:var(--sage);color:#fff;border:none;border-radius:4px;cursor:pointer;margin-right:2px">저장</button>
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
    if (!category || amount === '' || +amount < 0) { toast('카테고리와 금액을 확인해주세요.'); return; }
    const payload = { category, amount: +amount };
    if (date) payload.expenseDate = date;
    if (desc) payload.description = desc;
    const res = await api.put('/api/trips/' + _budgetSelectedTripId + '/expenses/' + id, payload);
    if (!res.success) { toast('수정 실패: ' + res.message); return; }
    toast('수정됐습니다.');
    await _loadExpenses(_budgetSelectedTripId);
}

//실제 지출 삭제 - DELETE /api/trips/{tripId}/expenses/{expenseId}
async function deleteExpense(id) {
    if (!confirm('이 지출 내역을 삭제하시겠습니까?')) return;
    const res = await api.del('/api/trips/' + _budgetSelectedTripId + '/expenses/' + id);
    if (!res.success) { toast('삭제 실패: ' + res.message); return; }
    toast('삭제됐습니다.');
    await _loadExpenses(_budgetSelectedTripId);
}

function switchLedgerTab(tab, btn) {
    document.querySelectorAll('.ledger-tab').forEach(b => b.classList.remove('on'));
    btn.classList.add('on');
    document.getElementById('ledger-tab-charts').style.display  = tab === 'charts'  ? '' : 'none';
    document.getElementById('ledger-tab-history').style.display = tab === 'history' ? '' : 'none';
}

/** 가계부 PDF 자동 다운로드 (jsPDF + html2canvas) */
async function exportBudgetPDF() {
    if (!_lastExpenseData) { toast('가계부 데이터를 먼저 불러주세요.'); return; }
    if (typeof window.jspdf === 'undefined' || typeof html2canvas === 'undefined') {
        toast('PDF 라이브러리 로딩 중입니다. 잠시 후 다시 시도해주세요.'); return;
    }
    const d = _lastExpenseData;

    const catRows = (d.categoryBudgets || []).map(c => {
        const info = _CATEGORY_MAP[c.category] || { label: c.category };
        const diff = (c.actualAmount || 0) - (c.estimatedAmount || 0);
        return `<tr>
      <td style="padding:7px 10px;border-bottom:1px solid #E5E7EB">${info.label}</td>
      <td style="padding:7px 10px;border-bottom:1px solid #E5E7EB;text-align:right">${_fmtWon(c.estimatedAmount)}</td>
      <td style="padding:7px 10px;border-bottom:1px solid #E5E7EB;text-align:right">${_fmtWon(c.actualAmount)}</td>
      <td style="padding:7px 10px;border-bottom:1px solid #E5E7EB;text-align:right;color:${diff > 0 ? '#EF4444' : '#10B981'}">${diff > 0 ? '+' + _fmtWon(diff) : diff < 0 ? '-' + _fmtWon(-diff) : '-'}</td>
    </tr>`;
    }).join('');

    const actRows = (d.actualExpenses || []).length === 0
        ? '<tr><td colspan="4" style="padding:10px;text-align:center;color:#9CA3AF">실제 지출 내역 없음</td></tr>'
        : (d.actualExpenses || []).map(e => {
            const info = _CATEGORY_MAP[e.category] || { label: e.category };
            return `<tr>
          <td style="padding:7px 10px;border-bottom:1px solid #E5E7EB">${e.date || '-'}</td>
          <td style="padding:7px 10px;border-bottom:1px solid #E5E7EB">${info.label}</td>
          <td style="padding:7px 10px;border-bottom:1px solid #E5E7EB">${e.description || '-'}</td>
          <td style="padding:7px 10px;border-bottom:1px solid #E5E7EB;text-align:right;font-weight:700">${_fmtWon(e.amount)}</td>
        </tr>`;
        }).join('');

    const pdfDiv = document.getElementById('budget-pdf-content');
    if (!pdfDiv) return;

    const thStyle = 'padding:8px 10px;text-align:left;background:#F9FAFB;font-weight:700;border-bottom:2px solid #E5E7EB';
    pdfDiv.innerHTML = `
    <h1 style="font-size:22px;font-weight:900;margin:0 0 4px">가계부 리포트</h1>
    <p style="color:#6B7280;margin:0 0 6px;font-size:13px">${d.tripTitle || ''}${d.destination ? ' · ' + d.destination : ''}${d.startDate ? ' · ' + d.startDate + ' ~ ' + d.endDate : ''}</p>
    ${d.budget ? `<p style="color:#6B7280;margin:0 0 20px;font-size:12px">설정 예산: ${_fmtWon(d.budget)}</p>` : '<div style="margin-bottom:20px"></div>'}

    <h3 style="font-size:14px;font-weight:700;margin:0 0 8px;border-bottom:2px solid #E5E7EB;padding-bottom:6px">카테고리별 예산 비교</h3>
    <table style="width:100%;border-collapse:collapse;font-size:12px;margin-bottom:28px">
      <thead><tr>
        <th style="${thStyle}">카테고리</th>
        <th style="${thStyle};text-align:right">예상 금액</th>
        <th style="${thStyle};text-align:right">실제 지출</th>
        <th style="${thStyle};text-align:right">차이</th>
      </tr></thead>
      <tbody>${catRows}</tbody>
      <tfoot><tr style="font-weight:900;background:#F9FAFB">
        <td style="padding:8px 10px;border-top:2px solid #E5E7EB">합계</td>
        <td style="padding:8px 10px;border-top:2px solid #E5E7EB;text-align:right">${_fmtWon(d.totalEstimatedAmount)}</td>
        <td style="padding:8px 10px;border-top:2px solid #E5E7EB;text-align:right">${_fmtWon(d.totalActualAmount)}</td>
        <td style="padding:8px 10px;border-top:2px solid #E5E7EB"></td>
      </tr></tfoot>
    </table>

    <h3 style="font-size:14px;font-weight:700;margin:0 0 8px;border-bottom:2px solid #E5E7EB;padding-bottom:6px">실제 지출 상세 내역</h3>
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
        const canvas   = await html2canvas(pdfDiv, { scale: 2, useCORS: true, backgroundColor: '#ffffff' });
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
        toast('PDF 다운로드 완료!');
    } catch (e) {
        console.error(e);
        toast('PDF 생성 실패: ' + e.message);
    }
}

/** 가계부 Excel 다운로드 (XML SpreadsheetML — 색상·열 너비 포함) */
function exportBudgetCSV() {
    if (!_lastExpenseData) { toast('가계부 데이터를 먼저 불러주세요.'); return; }
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
    <Font ss:Bold="1" ss:Size="14" ss:Color="#FFFFFF"/>
    <Interior ss:Color="#5B8272" ss:Pattern="Solid"/>
    <Alignment ss:Horizontal="Left" ss:Vertical="Center"/>
  </Style>
  <Style ss:ID="meta">
    <Font ss:Bold="1" ss:Size="12" ss:Color="#2C5F4E"/>
    <Alignment ss:Horizontal="Left"/>
  </Style>
  <Style ss:ID="lbl">
    <Font ss:Bold="1" ss:Color="#555555"/>
    <Interior ss:Color="#F2F2F2" ss:Pattern="Solid"/>
  </Style>
  <Style ss:ID="metaV">
    <Font ss:Color="#333333"/>
  </Style>
  <Style ss:ID="sec">
    <Font ss:Bold="1" ss:Size="11" ss:Color="#FFFFFF"/>
    <Interior ss:Color="#7FAF97" ss:Pattern="Solid"/>
    <Alignment ss:Horizontal="Left" ss:Vertical="Center"/>
  </Style>
  <Style ss:ID="hdr">
    <Font ss:Bold="1" ss:Color="#2C5F4E"/>
    <Interior ss:Color="#C8DDD6" ss:Pattern="Solid"/>
    <Borders>
      <Border ss:Position="Bottom" ss:LineStyle="Continuous" ss:Weight="2" ss:Color="#7FAF97"/>
    </Borders>
  </Style>
  <Style ss:ID="hdrR">
    <Font ss:Bold="1" ss:Color="#2C5F4E"/>
    <Interior ss:Color="#C8DDD6" ss:Pattern="Solid"/>
    <Alignment ss:Horizontal="Right"/>
    <Borders>
      <Border ss:Position="Bottom" ss:LineStyle="Continuous" ss:Weight="2" ss:Color="#7FAF97"/>
    </Borders>
  </Style>
  <Style ss:ID="numR">
    <Alignment ss:Horizontal="Right"/>
    <NumberFormat ss:Format="#,##0"/>
  </Style>
  <Style ss:ID="over">
    <Alignment ss:Horizontal="Right"/>
    <Font ss:Color="#CC3333"/>
    <NumberFormat ss:Format="#,##0"/>
  </Style>
  <Style ss:ID="sumLbl">
    <Font ss:Bold="1" ss:Color="#2C5F4E"/>
    <Interior ss:Color="#E4F0EB" ss:Pattern="Solid"/>
    <Borders>
      <Border ss:Position="Top" ss:LineStyle="Continuous" ss:Weight="2" ss:Color="#7FAF97"/>
    </Borders>
  </Style>
  <Style ss:ID="sumNum">
    <Font ss:Bold="1" ss:Color="#2C5F4E"/>
    <Interior ss:Color="#E4F0EB" ss:Pattern="Solid"/>
    <Alignment ss:Horizontal="Right"/>
    <NumberFormat ss:Format="#,##0"/>
    <Borders>
      <Border ss:Position="Top" ss:LineStyle="Continuous" ss:Weight="2" ss:Color="#7FAF97"/>
    </Borders>
  </Style>
  <Style ss:ID="sumOver">
    <Font ss:Bold="1" ss:Color="#CC3333"/>
    <Interior ss:Color="#E4F0EB" ss:Pattern="Solid"/>
    <Alignment ss:Horizontal="Right"/>
    <NumberFormat ss:Format="#,##0"/>
    <Borders>
      <Border ss:Position="Top" ss:LineStyle="Continuous" ss:Weight="2" ss:Color="#7FAF97"/>
    </Borders>
  </Style>
  <Style ss:ID="stripe">
    <Interior ss:Color="#F5F9F7" ss:Pattern="Solid"/>
  </Style>
  <Style ss:ID="stripeR">
    <Interior ss:Color="#F5F9F7" ss:Pattern="Solid"/>
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
