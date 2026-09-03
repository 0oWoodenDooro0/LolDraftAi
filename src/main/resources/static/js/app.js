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
    btnLockIn.addEventListener('click', () => {
        if (!selectedChampionId || !ws || ws.readyState !== WebSocket.OPEN) return;
        const msg = {
            type: 'com.loldraft.platform.live.models.LiveWsClientMessage.ApplyTurn',
            championId: selectedChampionId
        };
        ws.send(JSON.stringify(msg));
        selectedChampionId = null;
        selectedChampName.textContent = 'None';
        btnLockIn.disabled = true;
    });

    // 7. Undo Button
    btnUndo.addEventListener('click', () => {
        if (!ws || ws.readyState !== WebSocket.OPEN) return;
        const msg = { type: 'com.loldraft.platform.live.models.LiveWsClientMessage.Undo' };
        ws.send(JSON.stringify(msg));
    });

    // 8. Reset Button
    btnReset.addEventListener('click', () => {
        if (!ws || ws.readyState !== WebSocket.OPEN) return;
        const msg = { type: 'com.loldraft.platform.live.models.LiveWsClientMessage.Reset' };
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
                    loadTeamsForLeague(defaultLeague);
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
        }

        const filtered = allChampions.filter(c => {
            const matchSearch = !search || c.name.toLowerCase().includes(search);
            const matchRole = activeRoleFilter === 'ALL' || c.primaryRole === activeRoleFilter;
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
                btnLockIn.disabled = false;
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
            ws.send(JSON.stringify({ type: 'com.loldraft.platform.live.models.LiveWsClientMessage.Ping' }));
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
        if (msg.type?.includes('SessionSnapshot')) {
            updateDashboard(msg.latestSnapshot);
        } else if (msg.type?.includes('TurnApplied')) {
            updateDashboard(msg.snapshot);
        } else if (msg.type?.includes('TurnUndone')) {
            updateDashboard(msg.snapshot);
        } else if (msg.type?.includes('Error')) {
            alert(`Draft Action Error: ${msg.message}`);
        }
    }

    function updateDashboard(snapshot) {
        if (!snapshot) return;
        latestSnapshot = snapshot;

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
            const bWr = (snapshot.evalBar.blueWinRate * 100).toFixed(1);
            const rWr = (snapshot.evalBar.redWinRate * 100).toFixed(1);
            blueWinRateText.textContent = `${bWr}%`;
            redWinRateText.textContent = `${rWr}%`;
            evalBarBlue.style.width = `${bWr}%`;
            evalBarRed.style.width = `${rWr}%`;

            const score = snapshot.evalBar.evalScore;
            const sign = score > 0 ? '+' : '';
            evalScoreBadge.textContent = `${sign}${score.toFixed(2)} (${score >= 0 ? 'Blue Adv' : 'Red Adv'})`;
            evalExplanation.textContent = snapshot.evalBar.explanation || 'Real-time composition evaluation.';
        }

        // 3. Update Slots
        updateSlots(snapshot.draftState, currentTurnNumber);

        // 4. Update AI Predictions
        updateIntentPredictions(snapshot.predictedEnemyPicks);
        updateRecommendations(snapshot.recommendations);
        updateFlaws(snapshot.compositionFlaws);
        updateCurvesAndRadar(snapshot.timeCurves, snapshot.radar);

        // 5. Refresh grid disabled state
        renderChampionGrid();
    }

    function updateSlots(draftState, activeTurn) {
        if (!draftState) return;

        // Reset slots
        document.querySelectorAll('.bp-slot').forEach(slot => {
            const turn = parseInt(slot.dataset.turn, 10);
            const nameEl = slot.querySelector('.champ-name');
            slot.classList.remove('active-turn', 'filled');

            if (turn === activeTurn) {
                slot.classList.add('active-turn');
            }

            // Check if filled from turns
            const turnAction = (draftState.turns || []).find(t => t.turnNumber === turn);
            if (turnAction && turnAction.championId) {
                slot.classList.add('filled');
                nameEl.textContent = turnAction.championId;
            } else {
                nameEl.textContent = '-';
            }
        });
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
            const prob = (p.predictedProbability * 100).toFixed(1);
            item.innerHTML = `
                <span class="name">${p.championId} <small>(${p.suggestedRole || 'Flex'})</small></span>
                <span class="score">${prob}% Pick Prob</span>
            `;
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
            const delta = (r.expectedWinRateGain * 100).toFixed(1);
            item.innerHTML = `
                <span class="name">⭐ ${r.championId}</span>
                <span class="score">+${delta}% WR</span>
            `;
            recommendationsList.appendChild(item);
        });
    }

    function updateFlaws(flaws) {
        flawsContainer.innerHTML = '';
        if (!flaws || (flaws.blueSideFlaws.length === 0 && flaws.redSideFlaws.length === 0)) {
            flawsContainer.innerHTML = '<div class="no-flaws">No structural composition defects detected.</div>';
            return;
        }
        (flaws.blueSideFlaws || []).forEach(f => {
            const el = document.createElement('div');
            el.className = 'flaw-alert';
            el.textContent = `[BLUE] ${f.title}: ${f.description}`;
            flawsContainer.appendChild(el);
        });
        (flaws.redSideFlaws || []).forEach(f => {
            const el = document.createElement('div');
            el.className = 'flaw-alert';
            el.textContent = `[RED] ${f.title}: ${f.description}`;
            flawsContainer.appendChild(el);
        });
    }

    function updateCurvesAndRadar(curves, radar) {
        if (curves) {
            earlyBar.style.width = `${(curves.earlyWinRate * 100).toFixed(0)}%`;
            midBar.style.width = `${(curves.midWinRate * 100).toFixed(0)}%`;
            lateBar.style.width = `${(curves.lateWinRate * 100).toFixed(0)}%`;
        }
        if (radar) {
            const b = radar.blueScores;
            const r = radar.redScores;
            document.getElementById('radarDamage').textContent = `${b.damage.toFixed(1)} vs ${r.damage.toFixed(1)}`;
            document.getElementById('radarTank').textContent = `${b.tankiness.toFixed(1)} vs ${r.tankiness.toFixed(1)}`;
            document.getElementById('radarCc').textContent = `${b.cc.toFixed(1)} vs ${r.cc.toFixed(1)}`;
            document.getElementById('radarMobility').textContent = `${b.mobility.toFixed(1)} vs ${r.mobility.toFixed(1)}`;
            document.getElementById('radarWaveclear').textContent = `${b.waveclear.toFixed(1)} vs ${r.waveclear.toFixed(1)}`;
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
    }
});
