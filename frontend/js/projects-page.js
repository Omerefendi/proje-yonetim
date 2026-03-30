const ROLE_LABELS = {
    ADMIN: 'Sistem Yonetici',
    MANAGER: 'Proje Muduru',
    USER: 'Personel'
};

let cachedProjects = [];
let cachedUsers = [];
let currentWorkloadRows = [];
let workloadInitialized = false;
let loadProjectsPromise = null;
let hasWorkloadQuery = false;

const workloadElements = {
    personFilter: null,
    projectFilter: null,
    statusFilter: null,
    searchInput: null,
    queryButton: null,
    resetButton: null,
    queryState: null,
    visibleState: null,
    summaryGrid: null,
    tableBody: null
};

document.addEventListener('DOMContentLoaded', async () => {
    bindWorkloadElements();
    bindWorkloadEvents();

    try {
        await loadUsersForFilters();
    } catch (error) {
        console.error('Personel filtreleri yuklenemedi', error);
        setWorkloadQueryState(`Personel listesi yuklenemedi: ${error.message}`);
    }

    workloadInitialized = true;

    if (!cachedProjects.length) {
        try {
            await loadProjects();
        } catch (error) {
            console.error('Projeler tekrar yuklenemedi', error);
        }
    } else {
        populateProjectFilterOptions(cachedProjects);
    }

    renderWorkloadIdleState();
});

async function loadProjects() {
    if (loadProjectsPromise) {
        return loadProjectsPromise;
    }

    const tbody = document.getElementById('projectsTableBody');
    const request = (async () => {
        try {
            const response = await apiCall('/projects');
            cachedProjects = Array.isArray(response.data) ? response.data : [];
            renderProjectsTable(cachedProjects);
            populateProjectFilterOptions(cachedProjects);
        } catch (error) {
            console.error('Projeler yuklenemedi', error);
            if (tbody) {
                tbody.innerHTML = `<tr><td colspan="6" style="text-align: center; color: var(--color-danger);">Projeler yuklenemedi: ${escapeHtml(error.message)}</td></tr>`;
            }
            throw error;
        }
    })();

    loadProjectsPromise = request;

    try {
        return await request;
    } finally {
        if (loadProjectsPromise === request) {
            loadProjectsPromise = null;
        }
    }
}

window.loadProjects = loadProjects;

function bindWorkloadElements() {
    workloadElements.personFilter = document.getElementById('workloadPersonFilter');
    workloadElements.projectFilter = document.getElementById('workloadProjectFilter');
    workloadElements.statusFilter = document.getElementById('workloadStatusFilter');
    workloadElements.searchInput = document.getElementById('workloadSearchInput');
    workloadElements.queryButton = document.getElementById('workloadQueryBtn');
    workloadElements.resetButton = document.getElementById('workloadResetBtn');
    workloadElements.queryState = document.getElementById('workloadQueryState');
    workloadElements.visibleState = document.getElementById('workloadVisibleState');
    workloadElements.summaryGrid = document.getElementById('workloadSummaryGrid');
    workloadElements.tableBody = document.getElementById('workloadTableBody');
}

function bindWorkloadEvents() {
    if (workloadElements.queryButton) {
        workloadElements.queryButton.addEventListener('click', async () => {
            await loadWorkloadReport();
        });
    }

    if (workloadElements.resetButton) {
        workloadElements.resetButton.addEventListener('click', () => {
            workloadElements.personFilter.value = '';
            workloadElements.projectFilter.value = '';
            workloadElements.statusFilter.value = 'ALL';
            workloadElements.searchInput.value = '';
            renderWorkloadIdleState();
        });
    }

    if (workloadElements.searchInput) {
        workloadElements.searchInput.addEventListener('input', () => {
            if (!hasWorkloadQuery) {
                return;
            }
            renderVisibleWorkload();
        });
    }

    [workloadElements.personFilter, workloadElements.projectFilter, workloadElements.statusFilter]
        .filter(Boolean)
        .forEach((element) => {
            element.addEventListener('change', () => {
                if (hasWorkloadQuery) {
                    setWorkloadQueryState(`Hazir: ${buildQueryStateLabel()}`);
                    return;
                }

                setWorkloadQueryState('Personel veya proje secip Sorgula ile listeleyin.');
            });
        });
}

async function loadUsersForFilters() {
    const response = await apiCall('/users');
    cachedUsers = (Array.isArray(response.data) ? response.data : [])
        .filter((user) => user && user.active !== false)
        .sort((left, right) => String(left.fullName || '').localeCompare(String(right.fullName || ''), 'tr'));

    const previousValue = workloadElements.personFilter.value;
    workloadElements.personFilter.innerHTML = '<option value="">Tum personeller</option>' +
        cachedUsers.map((user) => `
            <option value="${escapeHtml(String(user.id))}">${escapeHtml(user.fullName || user.username || `#${user.id}`)}</option>
        `).join('');

    if (previousValue && cachedUsers.some((user) => String(user.id) === previousValue)) {
        workloadElements.personFilter.value = previousValue;
    }
}

function populateProjectFilterOptions(projects) {
    if (!workloadElements.projectFilter) {
        return;
    }

    const previousValue = workloadElements.projectFilter.value;
    workloadElements.projectFilter.innerHTML = '<option value="">Tum projeler</option>' +
        (Array.isArray(projects) ? projects : []).map((project) => `
            <option value="${escapeHtml(String(project.id))}">${escapeHtml(project.name || `#${project.id}`)}</option>
        `).join('');

    if (previousValue && projects.some((project) => String(project.id) === previousValue)) {
        workloadElements.projectFilter.value = previousValue;
    }
}

function renderProjectsTable(projects) {
    const tbody = document.getElementById('projectsTableBody');

    if (!tbody) {
        return;
    }

    if (!projects.length) {
        tbody.innerHTML = '<tr><td colspan="6" style="text-align: center; color: var(--color-text-subtle);">Goruntulenecek proje bulunamadi.</td></tr>';
        return;
    }

    tbody.innerHTML = projects.map((project) => {
        const projectName = escapeHtml(project.name || '-');
        const escapedNameForJs = projectName.replace(/'/g, "\\'");
        const ownerName = escapeHtml(project.owner ? project.owner.fullName : '-');
        const projectColor = escapeHtml(project.color || '#0052CC');

        return `
            <tr>
                <td>
                    <div style="display: flex; align-items: center; gap: 8px;">
                        <div style="width: 12px; height: 12px; border-radius: 3px; background-color: ${projectColor}"></div>
                        <a href="project-detail.html?id=${encodeURIComponent(project.id)}" style="font-weight: 500; font-size: 14px;">${projectName}</a>
                    </div>
                </td>
                <td><span class="badge ${getStatusBadgeClass(project.status)}">${getStatusLabel(project.status)}</span></td>
                <td>${getPriorityLabel(project.priority)}</td>
                <td>${ownerName}</td>
                <td>
                    <div style="font-size: 12px; color: var(--color-text-subtle);">
                        <div>Bas.: ${formatDate(project.startDate)}</div>
                        <div>Bitis: ${formatDate(project.endDate)}</div>
                    </div>
                </td>
                <td>
                    <a href="project-detail.html?id=${encodeURIComponent(project.id)}" class="btn btn-secondary" style="font-size: 12px; padding: 4px 8px;">Detay</a>
                    <button onclick="deleteProject(${project.id}, '${escapedNameForJs}')" class="btn" style="font-size: 12px; padding: 4px 8px; background-color: #de350b; color: #fff; border: none; border-radius: 4px; cursor: pointer; margin-left: 4px;">Sil</button>
                </td>
            </tr>
        `;
    }).join('');
}

async function loadWorkloadReport() {
    if (!workloadElements.tableBody) {
        return;
    }

    if (!hasWorkloadFilterSelection()) {
        renderWorkloadIdleState('Tum listeleme kapali. Lutfen en az bir personel veya proje secin.');
        return;
    }

    setWorkloadLoadingState(true);
    setWorkloadQueryState(`Sorgu calisiyor: ${buildQueryStateLabel()}`);

    try {
        const params = new URLSearchParams();
        if (workloadElements.personFilter.value) {
            params.set('personId', workloadElements.personFilter.value);
        }
        if (workloadElements.projectFilter.value) {
            params.set('projectId', workloadElements.projectFilter.value);
        }
        params.set('status', workloadElements.statusFilter.value || 'ALL');

        const query = params.toString();
        const response = await apiCall(`/projects/workload-report${query ? `?${query}` : ''}`);
        const report = response.data || {};

        currentWorkloadRows = Array.isArray(report.rows) ? report.rows : [];
        hasWorkloadQuery = true;
        renderVisibleWorkload();
        setWorkloadQueryState(`Sorgu tamamlandi: ${buildQueryStateLabel()}`);
    } catch (error) {
        console.error('Is yuku raporu yuklenemedi', error);
        currentWorkloadRows = [];
        hasWorkloadQuery = false;
        renderWorkloadSummary([]);
        workloadElements.tableBody.innerHTML = `<tr><td colspan="9" style="text-align: center; color: var(--color-danger);">Is yuku raporu yuklenemedi: ${escapeHtml(error.message)}</td></tr>`;
        setWorkloadVisibleState('Veri gosterilemiyor.');
        setWorkloadQueryState(`Sorgu hatasi: ${error.message}`);
    } finally {
        setWorkloadLoadingState(false);
    }
}

function hasWorkloadFilterSelection() {
    return Boolean(
        (workloadElements.personFilter && workloadElements.personFilter.value) ||
        (workloadElements.projectFilter && workloadElements.projectFilter.value)
    );
}

function renderWorkloadIdleState(message = 'Personel veya proje secip Sorgula ile listeleyin.') {
    hasWorkloadQuery = false;
    currentWorkloadRows = [];
    renderWorkloadSummary([]);

    if (workloadElements.tableBody) {
        workloadElements.tableBody.innerHTML = `<tr><td colspan="9" style="text-align: center; color: var(--color-text-subtle);">${escapeHtml(message)}</td></tr>`;
    }

    setWorkloadQueryState('Personel veya proje secip Sorgula ile listeleyin.');
    setWorkloadVisibleState('Henuz sonuc listelenmiyor.');
}

function renderVisibleWorkload() {
    const filteredRows = filterWorkloadRowsBySearch(currentWorkloadRows, workloadElements.searchInput.value);
    renderWorkloadSummary(filteredRows);
    renderWorkloadTable(filteredRows);

    const totalItems = filteredRows.reduce((sum, row) => sum + safeNumber(row.totalItemCount), 0);
    const totalHours = filteredRows.reduce((sum, row) => sum + safeNumber(row.totalEstimatedHours), 0);
    const searchNote = workloadElements.searchInput.value.trim()
        ? ` | arama: "${workloadElements.searchInput.value.trim()}"`
        : '';

    setWorkloadVisibleState(`${filteredRows.length} kayit, ${totalItems} is kalemi, ${totalHours} saat${searchNote}`);
}

function filterWorkloadRowsBySearch(rows, rawQuery) {
    const query = String(rawQuery || '').trim().toLocaleLowerCase('tr');
    if (!query) {
        return Array.isArray(rows) ? rows : [];
    }

    return (Array.isArray(rows) ? rows : []).filter((row) => {
        const haystack = [
            row.personName,
            row.username,
            row.personRole,
            row.projectName,
            row.projectStatus,
            ...(row.taskDetails || []).flatMap((item) => [item.title, item.status, item.priority]),
            ...(row.subTaskDetails || []).flatMap((item) => [item.title, item.parentTaskTitle, item.status, item.priority])
        ]
            .filter(Boolean)
            .join(' ')
            .toLocaleLowerCase('tr');

        return haystack.includes(query);
    });
}

function renderWorkloadSummary(rows) {
    if (!workloadElements.summaryGrid) {
        return;
    }

    const summary = computeVisibleSummary(rows);
    const cards = [
        {
            label: 'Kayit',
            value: summary.rowCount,
            note: `${summary.uniquePersonnelCount} personel / ${summary.uniqueProjectCount} proje`
        },
        {
            label: 'Gorev',
            value: summary.totalTaskCount,
            note: `${summary.totalSubTaskCount} alt gorev`
        },
        {
            label: 'Acik Is',
            value: summary.openItemCount,
            note: `${summary.completedItemCount} tamamlandi`
        },
        {
            label: 'Toplam Saat',
            value: summary.totalEstimatedHours,
            note: `${summary.averageCompletionPercent}% ortalama ilerleme`
        },
        {
            label: 'Iptal',
            value: summary.cancelledItemCount,
            note: `${summary.totalItemCount} toplam is kalemi`
        }
    ];

    workloadElements.summaryGrid.innerHTML = cards.map((card) => `
        <div class="workload-stat-card">
            <div class="workload-stat-label">${escapeHtml(card.label)}</div>
            <div class="workload-stat-value">${escapeHtml(String(card.value))}</div>
            <div class="workload-stat-note">${escapeHtml(card.note)}</div>
        </div>
    `).join('');
}

function computeVisibleSummary(rows) {
    const list = Array.isArray(rows) ? rows : [];
    const totalCompletionSum = list.reduce((sum, row) => sum + safeNumber(row.averageCompletionPercent), 0);

    return {
        rowCount: list.length,
        uniquePersonnelCount: new Set(list.map((row) => row.personId)).size,
        uniqueProjectCount: new Set(list.map((row) => row.projectId)).size,
        totalTaskCount: list.reduce((sum, row) => sum + safeNumber(row.taskCount), 0),
        totalSubTaskCount: list.reduce((sum, row) => sum + safeNumber(row.subTaskCount), 0),
        totalItemCount: list.reduce((sum, row) => sum + safeNumber(row.totalItemCount), 0),
        openItemCount: list.reduce((sum, row) => sum + safeNumber(row.openItemCount), 0),
        completedItemCount: list.reduce((sum, row) => sum + safeNumber(row.completedItemCount), 0),
        cancelledItemCount: list.reduce((sum, row) => sum + safeNumber(row.cancelledItemCount), 0),
        totalEstimatedHours: list.reduce((sum, row) => sum + safeNumber(row.totalEstimatedHours), 0),
        averageCompletionPercent: list.length ? Math.round(totalCompletionSum / list.length) : 0
    };
}

function renderWorkloadTable(rows) {
    if (!workloadElements.tableBody) {
        return;
    }

    if (!rows.length) {
        workloadElements.tableBody.innerHTML = '<tr><td colspan="9" style="text-align: center; color: var(--color-text-subtle);">Secilen filtrelerle eslesen is yuku bulunamadi.</td></tr>';
        return;
    }

    workloadElements.tableBody.innerHTML = rows.map((row) => {
        const detailId = `workload-detail-${row.personId}-${row.projectId}`;
        const projectColor = escapeHtml(row.projectColor || '#0052CC');
        const personName = escapeHtml(row.personName || '-');
        const username = escapeHtml(row.username || '-');
        const roleLabel = escapeHtml(ROLE_LABELS[row.personRole] || row.personRole || 'Personel');
        const projectName = escapeHtml(row.projectName || '-');

        return `
            <tr>
                <td>
                    <div class="workload-person-block">
                        <div class="workload-name">${personName}</div>
                        <div class="workload-subtext">@${username} | ${roleLabel}</div>
                    </div>
                </td>
                <td>
                    <div class="workload-project-block">
                        <a href="project-detail.html?id=${encodeURIComponent(row.projectId)}" class="workload-project-chip">
                            <span class="workload-color-dot" style="background-color: ${projectColor};"></span>
                            <span>${projectName}</span>
                        </a>
                        <div class="workload-subtext">${escapeHtml(getStatusLabel(row.projectStatus || 'PLANNING'))}</div>
                    </div>
                </td>
                <td>
                    <span class="workload-count">${safeNumber(row.taskCount)}</span>
                    <span class="workload-count-note">${safeNumber(row.taskEstimatedHours)} saat</span>
                </td>
                <td>
                    <span class="workload-count">${safeNumber(row.subTaskCount)}</span>
                    <span class="workload-count-note">${safeNumber(row.subTaskEstimatedHours)} saat</span>
                </td>
                <td>
                    <span class="workload-count">${safeNumber(row.openItemCount)}</span>
                    <span class="workload-count-note">${safeNumber(row.totalItemCount)} toplam</span>
                </td>
                <td>
                    <span class="workload-count">${safeNumber(row.totalEstimatedHours)}</span>
                    <span class="workload-count-note">tamamlanan ${safeNumber(row.completedItemCount)}</span>
                </td>
                <td>
                    <span class="workload-count">%${safeNumber(row.averageCompletionPercent)}</span>
                    <span class="workload-count-note">ortalama ilerleme</span>
                </td>
                <td>${renderStatusDistribution(row)}</td>
                <td>
                    <button type="button" class="btn btn-secondary" style="font-size: 12px; padding: 4px 8px;" onclick="toggleWorkloadDetails('${detailId}', this)">Detay Ac</button>
                </td>
            </tr>
            <tr id="${detailId}" class="workload-detail-row">
                <td colspan="9" class="workload-detail-cell">
                    <div class="workload-detail-content">
                        <div class="workload-detail-grid">
                            <section class="workload-detail-panel">
                                <div class="workload-detail-panel-header">
                                    <h3 class="workload-detail-panel-title">Gorevler</h3>
                                    <span class="workload-subtext">${safeNumber(row.taskCount)} kayit / ${safeNumber(row.taskEstimatedHours)} saat</span>
                                </div>
                                <div class="workload-item-list">${renderItemList(row.taskDetails, false)}</div>
                            </section>
                            <section class="workload-detail-panel">
                                <div class="workload-detail-panel-header">
                                    <h3 class="workload-detail-panel-title">Alt Gorevler</h3>
                                    <span class="workload-subtext">${safeNumber(row.subTaskCount)} kayit / ${safeNumber(row.subTaskEstimatedHours)} saat</span>
                                </div>
                                <div class="workload-item-list">${renderItemList(row.subTaskDetails, true)}</div>
                            </section>
                        </div>
                    </div>
                </td>
            </tr>
        `;
    }).join('');
}

function renderStatusDistribution(row) {
    const badges = [
        { label: 'Todo', value: row.todoItemCount },
        { label: 'Devam', value: row.inProgressItemCount },
        { label: 'Review', value: row.inReviewItemCount },
        { label: 'Bitti', value: row.completedItemCount },
        { label: 'Iptal', value: row.cancelledItemCount }
    ]
        .filter((entry) => safeNumber(entry.value) > 0)
        .map((entry) => `<span class="workload-mini-badge">${escapeHtml(entry.label)}: ${safeNumber(entry.value)}</span>`)
        .join('');

    return `<div class="workload-status-group">${badges || '<span class="workload-mini-badge">Kayit yok</span>'}</div>`;
}

function renderItemList(items, showParentTask) {
    const list = Array.isArray(items) ? items : [];
    if (!list.length) {
        return '<div class="workload-empty-state">Kayit bulunmuyor.</div>';
    }

    return list.map((item) => `
        <article class="workload-item-card">
            <div class="workload-item-title">${escapeHtml(item.title || '-')}</div>
            ${showParentTask && item.parentTaskTitle ? `<div class="workload-item-subtitle">Bagli gorev: ${escapeHtml(item.parentTaskTitle)}</div>` : ''}
            <div class="workload-item-badges">
                <span class="badge ${getStatusBadgeClass(item.status)}">${escapeHtml(getStatusLabel(item.status))}</span>
                <span class="workload-mini-badge">Oncelik: ${getPriorityLabel(item.priority || '-')}</span>
                <span class="workload-mini-badge">Saat: ${safeNumber(item.estimatedHours)}</span>
                <span class="workload-mini-badge">Ilerleme: %${safeNumber(item.completionPercent)}</span>
            </div>
            <div class="workload-item-meta">Bas.: ${formatDate(item.startDate)} | Bitis: ${formatDate(item.endDate)}</div>
        </article>
    `).join('');
}

function buildQueryStateLabel() {
    const personText = workloadElements.personFilter.value
        ? workloadElements.personFilter.options[workloadElements.personFilter.selectedIndex].text
        : 'Tum personeller';
    const projectText = workloadElements.projectFilter.value
        ? workloadElements.projectFilter.options[workloadElements.projectFilter.selectedIndex].text
        : 'Tum projeler';
    const statusText = workloadElements.statusFilter.options[workloadElements.statusFilter.selectedIndex].text;
    return `${personText} | ${projectText} | ${statusText}`;
}

function setWorkloadLoadingState(isLoading) {
    if (workloadElements.queryButton) {
        workloadElements.queryButton.disabled = isLoading;
        workloadElements.queryButton.textContent = isLoading ? 'Sorgulaniyor...' : 'Sorgula';
    }

    if (isLoading && workloadElements.tableBody) {
        workloadElements.tableBody.innerHTML = '<tr><td colspan="9" style="text-align: center; color: var(--color-text-subtle);">Rapor getiriliyor...</td></tr>';
    }
}

function setWorkloadQueryState(text) {
    if (workloadElements.queryState) {
        workloadElements.queryState.textContent = text;
    }
}

function setWorkloadVisibleState(text) {
    if (workloadElements.visibleState) {
        workloadElements.visibleState.textContent = text;
    }
}

function safeNumber(value) {
    return Number.isFinite(Number(value)) ? Number(value) : 0;
}

function toggleWorkloadDetails(detailId, button) {
    const detailRow = document.getElementById(detailId);
    if (!detailRow) {
        return;
    }

    const willOpen = !detailRow.classList.contains('active');
    detailRow.classList.toggle('active', willOpen);

    if (button) {
        button.textContent = willOpen ? 'Detay Kapat' : 'Detay Ac';
    }
}

window.toggleWorkloadDetails = toggleWorkloadDetails;
