// LoL Draft AI - Web Dashboard Application Logic
document.addEventListener('DOMContentLoaded', () => {
    // State
    let currentSessionId = null;
    let ws = null;
    let allChampions = [];
    let activeRoleFilter = 'ALL';
    let selectedChampionId = null;
    let latestSnapshot = null;
    let turnSpecs = [];

    // Elements
    const connectionStatusEl = document.getElementById('connectionStatus');
    const leagueSelect = document.getElementById('leagueSelect');
    const blueTeamSelect = document.getElementById('blueTeamSelect');
    const redTeamSelect = document.getElementById('redTeamSelect');
    const btnStartMatch = document.getElementById('btnStartMatch');
    const btnUndo = document.getElementById('btnUndo');
    const btnReset = document.getElementById('btnReset');
    const btnDebrief = document.getElementById('btnDebrief');

    const evalBlueTeam = document.getElementById('evalBlueTeam');
    const evalRedTeam = document.getElementById('evalRedTeam');
    const blueWinRateText = document.getElementById('blueWinRateText');
    const redWinRateText = document.getElementById('redWinRateText');
    const evalBarBlue = document.getElementById('evalBarBlue');
    const evalBarRed = document.getElementById('evalBarRed');
    const evalScoreBadge = document.getElementById('evalScoreBadge');
    const evalExplanation = document.getElementById('evalExplanation');

    const turnPhaseText = document.getElementById('turnPhaseText');
    const turnActionText = document.getElementById('turnActionText');
    const roleBtns = document.querySelectorAll('.role-btn');
    const champSearch = document.getElementById('champSearch');
    const championGrid = document.getElementById('championGrid');
    const selectedChampName = document.getElementById('selectedChampName');
    const btnLockIn = document.getElementById('btnLockIn');

    const intentPredictionList = document.getElementById('intentPredictionList');
    const recommendationsList = document.getElementById('recommendationsList');
    const flawsContainer = document.getElementById('flawsContainer');
    const earlyBar = document.getElementById('earlyBar');
    const midBar = document.getElementById('midBar');
    const lateBar = document.getElementById('lateBar');

    const debriefModal = document.getElementById('debriefModal');
    const debriefModalBody = document.getElementById('debriefModalBody');
    const closeDebriefModal = document.getElementById('closeDebriefModal');
    const btnCloseDebrief = document.getElementById('btnCloseDebrief');
    const btnExportMarkdown = document.getElementById('btnExportMarkdown');

    // 1. Initialize Metadata
    initPatches();
    initLeagues();
    initChampions();
    initTurnSpecs();

    // 2. League Selection Change
    leagueSelect.addEventListener('change', () => {
        loadTeamsForLeague(leagueSelect.value);
    });

    // 3. Start Match Button
    btnStartMatch.addEventListener('click', () => {
        startMatchSession();
    });

    // 4. Role Filter Buttons
    roleBtns.forEach(btn => {
        btn.addEventListener('click', () => {
            roleBtns.forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            activeRoleFilter = btn.dataset.role;
            renderChampionGrid();
        });
    });

    // 5. Search Filter
    champSearch.addEventListener('input', () => {
        renderChampionGrid();
    });

    // 6. Lock In Button
    btnLockIn.addEventListener('click', async () => {
        if (!selectedChampionId) return;
        if (!ws || ws.readyState !== WebSocket.OPEN) {
            await startMatchSession();
            setTimeout(() => {
                if (ws && ws.readyState === WebSocket.OPEN && selectedChampionId) {
                    sendTurn(selectedChampionId);
                }
            }, 300);
            return;
        }
        sendTurn(selectedChampionId);
    });

    function sendTurn(championId) {
        const msg = {
            type: 'apply_turn',
            championId: championId
        };
        ws.send(JSON.stringify(msg));
        selectedChampionId = null;
        selectedChampName.textContent = 'None';
        updateLockInButton();
    }

    // 7. Undo Button
    btnUndo.addEventListener('click', () => {
        if (!ws || ws.readyState !== WebSocket.OPEN) return;
        const msg = { type: 'undo' };
        ws.send(JSON.stringify(msg));
    });

    // 8. Reset Button
    btnReset.addEventListener('click', () => {
        if (!ws || ws.readyState !== WebSocket.OPEN) return;
        const msg = { type: 'reset' };
        ws.send(JSON.stringify(msg));
    });

    // 9. Debrief Modal Actions
    btnDebrief.addEventListener('click', openDebrief);
    closeDebriefModal.addEventListener('click', () => debriefModal.classList.remove('show'));
    btnCloseDebrief.addEventListener('click', () => debriefModal.classList.remove('show'));
    btnExportMarkdown.addEventListener('click', exportDebriefMarkdown);

    // API Functions
    async function initLeagues() {
        try {
            const res = await fetch('/api/pro/leagues');
            if (res.ok) {
                const leagues = await res.json();
                leagueSelect.innerHTML = '';
                leagues.forEach(l => {
                    const opt = document.createElement('option');
                    opt.value = l;
                    opt.textContent = l;
                    leagueSelect.appendChild(opt);
                });
                if (leagues.length > 0) {
                    const defaultLeague = leagues.includes('LCK') ? 'LCK' : leagues[0];
                    leagueSelect.value = defaultLeague;
                    await loadTeamsForLeague(defaultLeague);
                    startMatchSession();
                }
            }
        } catch (e) {
            console.error('Failed to load leagues', e);
        }
    }

    async function loadTeamsForLeague(league) {
        try {
            const res = await fetch(`/api/pro/teams?league=${encodeURIComponent(league)}`);
            if (res.ok) {
                const teams = await res.json();
                blueTeamSelect.innerHTML = '';
                redTeamSelect.innerHTML = '';
                teams.forEach(t => {
                    const optB = document.createElement('option');
                    optB.value = t.id;
                    optB.textContent = `${t.name} (${t.code})`;
                    blueTeamSelect.appendChild(optB);

                    const optR = document.createElement('option');
                    optR.value = t.id;
                    optR.textContent = `${t.name} (${t.code})`;
                    redTeamSelect.appendChild(optR);
                });
                if (teams.length >= 2) {
                    redTeamSelect.selectedIndex = 1;
                }
            }
        } catch (e) {
            console.error('Failed to load teams', e);
        }
    }

    async function initPatches() {
        try {
            const res = await fetch('/api/pro/patches');
            if (res.ok) {
                const patches = await res.json();
                if (patches && patches.length > 0) {
                    const patchBadge = document.getElementById('patchBadge');
                    if (patchBadge) {
                        patchBadge.textContent = `Patch ${patches[0]}`;
                    }
                }
            }
        } catch (e) {
            console.error('Failed to load patches', e);
        }
    }

    async function initChampions() {
        try {
            const res = await fetch('/api/pro/champions');
            if (res.ok) {
                allChampions = await res.json();
                renderChampionGrid();
            }
        } catch (e) {
            console.error('Failed to load champions', e);
        }
    }

    function renderChampionGrid() {
        championGrid.innerHTML = '';
        const search = champSearch.value.trim().toLowerCase();

        // Check used champions in draft
        const usedChamps = new Set();
        if (latestSnapshot && latestSnapshot.draftState) {
            const d = latestSnapshot.draftState;
            (d.blueBans || []).forEach(c => usedChamps.add(c.toLowerCase()));
            (d.redBans || []).forEach(c => usedChamps.add(c.toLowerCase()));
            (d.bluePicks || []).forEach(p => usedChamps.add(p.championId.toLowerCase()));
            (d.redPicks || []).forEach(p => usedChamps.add(p.championId.toLowerCase()));
            (d.turns || []).forEach(t => usedChamps.add(t.championId.toLowerCase()));
        }

        const filtered = allChampions.filter(c => {
            const matchSearch = !search || c.name.toLowerCase().includes(search);
            const matchRole =
                activeRoleFilter === 'ALL' ||
                c.primaryRole === activeRoleFilter ||
                (Array.isArray(c.secondaryRoles) && c.secondaryRoles.includes(activeRoleFilter));
            return matchSearch && matchRole;
        });

        filtered.forEach(champ => {
            const card = document.createElement('div');
            card.className = 'champ-card';
            const isUsed = usedChamps.has(champ.id.toLowerCase()) || usedChamps.has(champ.name.toLowerCase());
            if (isUsed) {
                card.classList.add('disabled');
            }
            if (selectedChampionId === champ.name) {
                card.classList.add('selected');
            }

            const initial = champ.name.substring(0, 2).toUpperCase();
            card.innerHTML = `
                <div class="champ-avatar-placeholder">${initial}</div>
                <div class="champ-card-name" title="${champ.name}">${champ.name}</div>
            `;

            card.addEventListener('click', () => {
                if (isUsed) return;
                document.querySelectorAll('.champ-card').forEach(c => c.classList.remove('selected'));
                card.classList.add('selected');
                selectedChampionId = champ.name;
                selectedChampName.textContent = champ.name;
                updateLockInButton();
            });

            championGrid.appendChild(card);
        });
    }

    async function startMatchSession() {
        const blueTeamId = blueTeamSelect.value;
        const redTeamId = redTeamSelect.value;
        const blueName = blueTeamSelect.options[blueTeamSelect.selectedIndex]?.text || 'Blue Team';
        const redName = redTeamSelect.options[redTeamSelect.selectedIndex]?.text || 'Red Team';

        evalBlueTeam.textContent = blueName;
        evalRedTeam.textContent = redName;
        document.getElementById('blueSideHeader').textContent = `BLUE SIDE (${blueName})`;
        document.getElementById('redSideHeader').textContent = `RED SIDE (${redName})`;

        try {
            const createPayload = {
                blueTeam: { id: blueTeamId, name: blueName, code: blueName },
                redTeam: { id: redTeamId, name: redName, code: redName }
            };

            const res = await fetch('/api/live/sessions', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(createPayload)
            });

            if (res.ok) {
                const session = await res.json();
                currentSessionId = session.sessionId;
                connectWebSocket(currentSessionId);
            }
        } catch (e) {
            console.error('Failed to create match session', e);
        }
    }

    function connectWebSocket(sessionId) {
        if (ws) {
            ws.close();
        }

        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        const wsUrl = `${protocol}//${window.location.host}/api/live/ws/${sessionId}`;

        ws = new WebSocket(wsUrl);

        ws.onopen = () => {
            setConnected(true);
            // send initial ping
            ws.send(JSON.stringify({ type: 'ping' }));
        };

        ws.onmessage = (event) => {
            try {
                const msg = JSON.parse(event.data);
                handleServerMessage(msg);
            } catch (err) {
                console.error('Error handling WS message', err);
            }
        };

        ws.onclose = () => {
            setConnected(false);
        };

        ws.onerror = (err) => {
            console.error('WebSocket Error', err);
            setConnected(false);
        };
    }

    function setConnected(connected) {
        if (connected) {
            connectionStatusEl.innerHTML = '<span class="dot connected"></span><span class="status-label">Live WS Connected</span>';
        } else {
            connectionStatusEl.innerHTML = '<span class="dot disconnected"></span><span class="status-label">Disconnected</span>';
        }
    }

    function handleServerMessage(msg) {
        const type = (msg.type || '').toLowerCase();
        if (type === 'session_snapshot' || type.includes('sessionsnapshot')) {
            updateDashboard(msg.latestSnapshot);
        } else if (type === 'turn_applied' || type.includes('turnapplied')) {
            updateDashboard(msg.snapshot);
        } else if (type === 'turn_undone' || type.includes('turnundone')) {
            updateDashboard(msg.snapshot);
        } else if (type === 'session_reset' || type.includes('sessionreset')) {
            updateDashboard(msg.snapshot);
        } else if (type === 'error' || type.includes('error')) {
            alert(`Draft Action Error: ${msg.message || msg.code || 'Action failed'}`);
        }
    }

    function updateDashboard(snapshot) {
        if (!snapshot) return;
        latestSnapshot = snapshot;

        try {
            // 1. Update Turn Tracker & Indicator
            const turnNum = snapshot.turnNumber || 0;
            const currentTurnNumber = turnNum + 1;

            if (currentTurnNumber <= 20) {
                const spec = turnSpecs[currentTurnNumber - 1];
                turnPhaseText.textContent = `TURN ${currentTurnNumber} OF 20`;
                turnActionText.textContent = `${spec.side} TEAM ${spec.actionType}`;
            } else {
                turnPhaseText.textContent = 'DRAFT COMPLETE';
                turnActionText.textContent = 'ALL 20 TURNS COMPLETED';
            }

            // 2. Update Eval Bar
            if (snapshot.evalBar) {
                const bPct = typeof snapshot.evalBar.blueBarPercentage === 'number'
                    ? snapshot.evalBar.blueBarPercentage
                    : (snapshot.evalBar.blueWinRate ? snapshot.evalBar.blueWinRate * 100 : 50.0);
                const rPct = typeof snapshot.evalBar.redBarPercentage === 'number'
                    ? snapshot.evalBar.redBarPercentage
                    : (snapshot.evalBar.redWinRate ? snapshot.evalBar.redWinRate * 100 : 50.0);

                const bWr = bPct.toFixed(1);
                const rWr = rPct.toFixed(1);
                blueWinRateText.textContent = `${bWr}%`;
                redWinRateText.textContent = `${rWr}%`;
                evalBarBlue.style.width = `${bWr}%`;
                evalBarRed.style.width = `${rWr}%`;

                const score = typeof snapshot.evalBar.score === 'number'
                    ? snapshot.evalBar.score
                    : (typeof snapshot.evalBar.evalScore === 'number' ? snapshot.evalBar.evalScore : 0.0);
                const sign = score > 0 ? '+' : '';
                const formatted = snapshot.evalBar.formattedScore || `${sign}${score.toFixed(2)}`;
                const adv = score > 0.05 ? 'Blue Adv' : (score < -0.05 ? 'Red Adv' : 'Even');
                evalScoreBadge.textContent = `${formatted} (${adv})`;
                evalExplanation.textContent = snapshot.evalBar.leadCategory
                    ? `Composition evaluation: ${snapshot.evalBar.leadCategory.replace(/_/g, ' ')}`
                    : 'Real-time composition evaluation.';
            }

            // 3. Update Slots
            updateSlots(snapshot.draftState, currentTurnNumber);

            // 4. Update AI Predictions
            const predictions = snapshot.aiIntentPredictions || snapshot.predictedEnemyPicks || [];
            updateIntentPredictions(predictions);

            const recommendations = snapshot.aiRecommendations || snapshot.recommendations || [];
            updateRecommendations(recommendations);

            updateFlaws(snapshot);
            updateCurvesAndRadar(snapshot);

            // 5. Refresh grid disabled state & lock-in button
            renderChampionGrid();
            updateLockInButton();
        } catch (err) {
            console.error('Error in updateDashboard', err);
        }
    }

    function updateSlots(draftState, activeTurn) {
        if (!draftState) return;

        // Reset slots
        document.querySelectorAll('.bp-slot').forEach(slot => {
            const turn = parseInt(slot.dataset.turn, 10);
            const nameEl = slot.querySelector('.champ-name');
            const playerEl = slot.querySelector('.player-name');
            slot.classList.remove('active-turn', 'filled');

            if (turn === activeTurn) {
                slot.classList.add('active-turn');
            }

            // 1. First check if filled from turns
            let champ = null;
            let roleOrPlayer = null;

            const turnAction = (draftState.turns || []).find(t => t.turnNumber === turn);
            if (turnAction && turnAction.championId) {
                champ = turnAction.championId;
                roleOrPlayer = turnAction.player || turnAction.role || '';
            } else {
                // Fallback check from bans/picks lists by index
                champ = getFallbackChampForSlot(draftState, turn);
            }

            if (champ) {
                slot.classList.add('filled');
                if (nameEl) nameEl.textContent = champ;
                if (playerEl && roleOrPlayer) playerEl.textContent = roleOrPlayer;
            } else {
                if (nameEl) nameEl.textContent = '-';
                if (playerEl) playerEl.textContent = '';
            }
        });
    }

    function getFallbackChampForSlot(d, turn) {
        if (!d) return null;
        const blueBans = d.blueBans || [];
        const redBans = d.redBans || [];
        const bluePicks = d.bluePicks || [];
        const redPicks = d.redPicks || [];

        switch (turn) {
            case 1: return blueBans[0] || null;
            case 2: return redBans[0] || null;
            case 3: return blueBans[1] || null;
            case 4: return redBans[1] || null;
            case 5: return blueBans[2] || null;
            case 6: return redBans[2] || null;
            case 7: return bluePicks[0]?.championId || null;
            case 8: return redPicks[0]?.championId || null;
            case 9: return redPicks[1]?.championId || null;
            case 10: return bluePicks[1]?.championId || null;
            case 11: return bluePicks[2]?.championId || null;
            case 12: return redPicks[2]?.championId || null;
            case 13: return redBans[3] || null;
            case 14: return blueBans[3] || null;
            case 15: return redBans[4] || null;
            case 16: return blueBans[4] || null;
            case 17: return redPicks[3]?.championId || null;
            case 18: return bluePicks[3]?.championId || null;
            case 19: return bluePicks[4]?.championId || null;
            case 20: return redPicks[4]?.championId || null;
            default: return null;
        }
    }

    function updateIntentPredictions(predictions) {
        intentPredictionList.innerHTML = '';
        if (!predictions || predictions.length === 0) {
            intentPredictionList.innerHTML = '<div class="intel-placeholder">No enemy predictions active.</div>';
            return;
        }
        predictions.slice(0, 3).forEach(p => {
            const item = document.createElement('div');
            item.className = 'intel-item';
            item.style.cursor = 'pointer';
            item.title = `Click to select ${p.championId}`;
            const probVal = typeof p.probability === 'number'
                ? p.probability
                : (typeof p.predictedProbability === 'number' ? p.predictedProbability : 0);
            const prob = (probVal * 100).toFixed(1);
            const role = p.predictedRole || p.suggestedRole || 'Flex';
            item.innerHTML = `
                <span class="name">${p.championId} <small>(${role})</small></span>
                <span class="score">${prob}% Pick Prob</span>
            `;
            item.addEventListener('click', () => selectChampionByName(p.championId));
            intentPredictionList.appendChild(item);
        });
    }

    function updateRecommendations(recoms) {
        recommendationsList.innerHTML = '';
        if (!recoms || recoms.length === 0) {
            recommendationsList.innerHTML = '<div class="intel-placeholder">No active recommendations.</div>';
            return;
        }
        recoms.slice(0, 3).forEach(r => {
            const item = document.createElement('div');
            item.className = 'intel-item';
            item.style.cursor = 'pointer';
            item.title = `Click to select ${r.championId}`;
            const gain = typeof r.winRateGain === 'number'
                ? r.winRateGain
                : (typeof r.expectedWinRateGain === 'number' ? r.expectedWinRateGain : 0);
            const delta = (gain * 100).toFixed(1);
            const role = r.recommendedRole ? ` (${r.recommendedRole})` : '';
            item.innerHTML = `
                <span class="name">⭐ ${r.championId}<small>${role}</small></span>
                <span class="score">+${delta}% WR</span>
            `;
            item.addEventListener('click', () => selectChampionByName(r.championId));
            recommendationsList.appendChild(item);
        });
    }

    function updateFlaws(snapshot) {
        flawsContainer.innerHTML = '';
        const bFlaws = snapshot.blueFlaws || snapshot.compositionFlaws?.blueSideFlaws || [];
        const rFlaws = snapshot.redFlaws || snapshot.compositionFlaws?.redSideFlaws || [];

        if (bFlaws.length === 0 && rFlaws.length === 0) {
            flawsContainer.innerHTML = '<div class="no-flaws">No structural composition defects detected.</div>';
            return;
        }
        bFlaws.forEach(f => {
            const el = document.createElement('div');
            el.className = 'flaw-alert';
            el.textContent = `[BLUE] ${f.title}: ${f.description}`;
            flawsContainer.appendChild(el);
        });
        rFlaws.forEach(f => {
            const el = document.createElement('div');
            el.className = 'flaw-alert';
            el.textContent = `[RED] ${f.title}: ${f.description}`;
            flawsContainer.appendChild(el);
        });
    }

    function updateCurvesAndRadar(snapshot) {
        const curves = snapshot.timeCurve || snapshot.timeCurves;
        if (curves) {
            const early = typeof curves.earlyGameWinRate === 'number' ? curves.earlyGameWinRate : (curves.earlyWinRate || 0.5);
            const mid = typeof curves.midGameWinRate === 'number' ? curves.midGameWinRate : (curves.midWinRate || 0.5);
            const late = typeof curves.lateGameWinRate === 'number' ? curves.lateGameWinRate : (curves.lateWinRate || 0.5);
            earlyBar.style.width = `${Math.round(early * 100)}%`;
            midBar.style.width = `${Math.round(mid * 100)}%`;
            lateBar.style.width = `${Math.round(late * 100)}%`;
        }

        const b = snapshot.blueRadar || snapshot.radar?.blueScores;
        const r = snapshot.redRadar || snapshot.radar?.redScores;
        if (b && r) {
            const dEl = document.getElementById('radarDamage');
            const tEl = document.getElementById('radarTank');
            const cEl = document.getElementById('radarCc');
            const mEl = document.getElementById('radarMobility');
            const wEl = document.getElementById('radarWaveclear');

            const bLaning = (b.laning ?? 5.0).toFixed(1);
            const rLaning = (r.laning ?? 5.0).toFixed(1);
            const bEngage = (b.engage ?? 5.0).toFixed(1);
            const rEngage = (r.engage ?? 5.0).toFixed(1);
            const bDmg = (b.damageBalance ?? b.damage ?? 5.0).toFixed(1);
            const rDmg = (r.damageBalance ?? r.damage ?? 5.0).toFixed(1);
            const bScaling = (b.lateScaling ?? 5.0).toFixed(1);
            const rScaling = (r.lateScaling ?? 5.0).toFixed(1);
            const bWave = (b.waveclear ?? 5.0).toFixed(1);
            const rWave = (r.waveclear ?? 5.0).toFixed(1);

            if (tEl) tEl.textContent = `${bLaning} vs ${rLaning}`;
            if (cEl) cEl.textContent = `${bEngage} vs ${rEngage}`;
            if (dEl) dEl.textContent = `${bDmg} vs ${rDmg}`;
            if (mEl) mEl.textContent = `${bScaling} vs ${rScaling}`;
            if (wEl) wEl.textContent = `${bWave} vs ${rWave}`;
        }
    }

    async function openDebrief() {
        debriefModal.classList.add('show');
        debriefModalBody.innerHTML = '<div class="debrief-loading">Computing post-match debrief and attribution metrics...</div>';

        if (!currentSessionId) {
            debriefModalBody.innerHTML = '<div class="debrief-loading">Please start a draft session first.</div>';
            return;
        }

        try {
            const res = await fetch('/api/debrief/game', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    gameId: currentSessionId,
                    blueTeam: { id: blueTeamSelect.value, name: evalBlueTeam.textContent, code: evalBlueTeam.textContent },
                    redTeam: { id: redTeamSelect.value, name: evalRedTeam.textContent, code: evalRedTeam.textContent },
                    draftState: latestSnapshot?.draftState || { blueBans: [], redBans: [], bluePicks: [], redPicks: [], turns: [] },
                    winner: 'BLUE'
                })
            });

            if (res.ok) {
                const debrief = await res.json();
                renderDebriefReport(debrief);
            } else {
                debriefModalBody.innerHTML = `<div class="debrief-loading">Failed to generate debrief: ${res.statusText}</div>`;
            }
        } catch (e) {
            debriefModalBody.innerHTML = `<div class="debrief-loading">Error generating debrief: ${e.message}</div>`;
        }
    }

    function renderDebriefReport(debrief) {
        let turnsHtml = '';
        (debrief.turnDeltas || []).forEach(t => {
            turnsHtml += `<tr><td>Turn ${t.turnNumber}</td><td>${t.side}</td><td>${t.championId}</td><td>${(t.deltaExpectedWinRate * 100).toFixed(2)}%</td></tr>`;
        });

        debriefModalBody.innerHTML = `
            <div class="debrief-report">
                <h3>Match Debrief: ${debrief.blueTeam.name} vs ${debrief.redTeam.name}</h3>
                <p><strong>Coach Attribution Score:</strong> Blue: ${(debrief.blueCoachScore || 0).toFixed(1)} / Red: ${(debrief.redCoachScore || 0).toFixed(1)}</p>
                <p><strong>Decisive BP Swing:</strong> Turn ${debrief.decisiveTurn || 1} (${debrief.decisiveChampion || 'None'})</p>
                <h4 style="margin-top: 12px;">Turn-by-Turn Delta Expected Win Rate:</h4>
                <table style="width: 100%; border-collapse: collapse; margin-top: 8px; font-size: 12px;">
                    <thead><tr style="border-bottom: 1px solid #334155; text-align: left;"><th>Turn</th><th>Side</th><th>Champion</th><th>Delta WR</th></tr></thead>
                    <tbody>${turnsHtml}</tbody>
                </table>
            </div>
        `;
    }

    async function exportDebriefMarkdown() {
        if (!currentSessionId) return;
        const res = await fetch(`/api/debrief/reports/${currentSessionId}/markdown`);
        if (res.ok) {
            const md = await res.text();
            const blob = new Blob([md], { type: 'text/markdown' });
            const url = URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = `debrief-${currentSessionId}.md`;
            a.click();
            URL.revokeObjectURL(url);
        } else {
            alert('Debrief markdown report not yet saved.');
        }
    }

    function initTurnSpecs() {
        turnSpecs = [
            { turnNumber: 1, side: 'BLUE', actionType: 'BAN' },
            { turnNumber: 2, side: 'RED', actionType: 'BAN' },
            { turnNumber: 3, side: 'BLUE', actionType: 'BAN' },
            { turnNumber: 4, side: 'RED', actionType: 'BAN' },
            { turnNumber: 5, side: 'BLUE', actionType: 'BAN' },
            { turnNumber: 6, side: 'RED', actionType: 'BAN' },
            { turnNumber: 7, side: 'BLUE', actionType: 'PICK' },
            { turnNumber: 8, side: 'RED', actionType: 'PICK' },
            { turnNumber: 9, side: 'RED', actionType: 'PICK' },
            { turnNumber: 10, side: 'BLUE', actionType: 'PICK' },
            { turnNumber: 11, side: 'BLUE', actionType: 'PICK' },
            { turnNumber: 12, side: 'RED', actionType: 'PICK' },
            { turnNumber: 13, side: 'RED', actionType: 'BAN' },
            { turnNumber: 14, side: 'BLUE', actionType: 'BAN' },
            { turnNumber: 15, side: 'RED', actionType: 'BAN' },
            { turnNumber: 16, side: 'BLUE', actionType: 'BAN' },
            { turnNumber: 17, side: 'RED', actionType: 'PICK' },
            { turnNumber: 18, side: 'BLUE', actionType: 'PICK' },
            { turnNumber: 19, side: 'BLUE', actionType: 'PICK' },
            { turnNumber: 20, side: 'RED', actionType: 'PICK' },
        ];
        updateLockInButton();
    }

    function updateLockInButton() {
        const turnNum = latestSnapshot ? (latestSnapshot.turnNumber || 0) : 0;
        const currentTurnNumber = turnNum + 1;
        if (currentTurnNumber > 20) {
            btnLockIn.textContent = 'Draft Complete';
            btnLockIn.disabled = true;
            btnLockIn.classList.remove('ban-mode', 'pick-mode');
            return;
        }

        const spec = turnSpecs[currentTurnNumber - 1];
        const isBan = spec ? spec.actionType === 'BAN' : true;

        if (isBan) {
            btnLockIn.classList.add('ban-mode');
            btnLockIn.classList.remove('pick-mode');
        } else {
            btnLockIn.classList.add('pick-mode');
            btnLockIn.classList.remove('ban-mode');
        }

        if (selectedChampionId) {
            btnLockIn.textContent = `${isBan ? 'Ban' : 'Pick'} ${selectedChampionId}`;
            btnLockIn.disabled = false;
        } else {
            btnLockIn.textContent = isBan ? 'Select Champion to Ban' : 'Select Champion to Pick';
            btnLockIn.disabled = true;
        }
    }

    function selectChampionByName(champName) {
        selectedChampionId = champName;
        selectedChampName.textContent = champName;
        champSearch.value = champName;
        activeRoleFilter = 'ALL';
        roleBtns.forEach(b => b.classList.toggle('active', b.dataset.role === 'ALL'));
        renderChampionGrid();
        updateLockInButton();
    }
});
