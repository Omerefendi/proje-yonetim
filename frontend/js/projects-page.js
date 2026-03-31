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
    printButton: null,
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
    workloadElements.printButton = document.getElementById('workloadPrintBtn');
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

    if (workloadElements.printButton) {
        workloadElements.printButton.addEventListener('click', () => {
            printWorkloadReport();
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

                setWorkloadQueryState('Filtreleri ayarlayip Sorgula ile listeleyin. Personel secilmezse tum personeller listelenir.');
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

function renderWorkloadIdleState(message = 'Filtreleri ayarlayip Sorgula ile listeleyin. Personel secilmezse tum personeller listelenir.') {
    hasWorkloadQuery = false;
    currentWorkloadRows = [];
    renderWorkloadSummary([]);

    if (workloadElements.tableBody) {
        workloadElements.tableBody.innerHTML = `<tr><td colspan="9" style="text-align: center; color: var(--color-text-subtle);">${escapeHtml(message)}</td></tr>`;
    }

    setWorkloadQueryState('Filtreleri ayarlayip Sorgula ile listeleyin. Personel secilmezse tum personeller listelenir.');
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
        uniquePersonnelCount: new Set(
            list
                .map((row) => row.personId)
                .filter((id) => id !== null && id !== undefined)
        ).size,
        uniqueProjectCount: new Set(
            list
                .map((row) => row.projectId)
                .filter((id) => id !== null && id !== undefined)
        ).size,
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
        const hasProject = row.projectId !== null && row.projectId !== undefined;
        const personName = escapeHtml(row.personName || '-');
        const username = escapeHtml(row.username || '-');
        const roleLabel = escapeHtml(ROLE_LABELS[row.personRole] || row.personRole || 'Personel');
        const projectName = escapeHtml(row.projectName || '-');
        const projectContent = hasProject
            ? `
                        <a href="project-detail.html?id=${encodeURIComponent(row.projectId)}" class="workload-project-chip">
                            <span class="workload-color-dot" style="background-color: ${projectColor};"></span>
                            <span>${projectName}</span>
                        </a>
                    `
            : `
                        <div class="workload-project-chip">
                            <span class="workload-color-dot" style="background-color: ${projectColor};"></span>
                            <span>${projectName}</span>
                        </div>
                    `;
        const projectSubtext = hasProject && row.projectStatus
            ? escapeHtml(getStatusLabel(row.projectStatus))
            : 'Is yuku yok';

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
                        ${projectContent}
                        <div class="workload-subtext">${projectSubtext}</div>
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

function getVisibleWorkloadRows() {
    return filterWorkloadRowsBySearch(
        currentWorkloadRows,
        workloadElements.searchInput ? workloadElements.searchInput.value : ''
    );
}

function printWorkloadReport() {
    if (!hasWorkloadQuery) {
        window.alert('Once sorgu calistirin, sonra yazdirin.');
        return;
    }

    const rows = getVisibleWorkloadRows();
    const printWindow = window.open('', '_blank', 'width=1280,height=900');

    if (!printWindow) {
        window.alert('Tarayici yeni pencereyi engelledi. Lutfen popup izni verin.');
        return;
    }

    printWindow.document.open();
    printWindow.document.write(buildWorkloadPrintDocument(rows));
    printWindow.document.close();

    let printTriggered = false;
    const triggerPrint = () => {
        if (printTriggered) {
            return;
        }
        printTriggered = true;
        printWindow.focus();
        printWindow.print();
    };

    printWindow.addEventListener('load', () => {
        setTimeout(triggerPrint, 150);
    }, { once: true });
    setTimeout(triggerPrint, 300);
}

function buildWorkloadPrintDocument(rows) {
    const summary = computeVisibleSummary(rows);
    const currentUser = typeof getUser === 'function' ? getUser() : null;
    const searchText = workloadElements.searchInput && workloadElements.searchInput.value.trim()
        ? workloadElements.searchInput.value.trim()
        : 'Yok';
    const createdAtText = new Intl.DateTimeFormat('tr-TR', {
        day: '2-digit',
        month: '2-digit',
        year: 'numeric',
        hour: '2-digit',
        minute: '2-digit'
    }).format(new Date());

    return `<!DOCTYPE html>
<html lang="tr">
<head>
    <meta charset="UTF-8">
    <title>Personel ve Proje Is Yuku</title>
    <style>
        @page { size: landscape; margin: 12mm; }
        * { box-sizing: border-box; }
        body { margin: 0; font-family: "Segoe UI", Arial, sans-serif; color: #102a43; background: #ffffff; }
        .report-shell { padding: 20px; }
        .report-header { display: flex; justify-content: space-between; gap: 20px; align-items: flex-start; margin-bottom: 16px; }
        .report-title { margin: 0 0 6px; font-size: 28px; font-weight: 800; color: #102a43; }
        .report-subtitle { margin: 0; font-size: 13px; line-height: 1.6; color: #486581; }
        .report-meta { min-width: 280px; border: 1px solid #d9e2ec; border-radius: 14px; padding: 14px 16px; }
        .report-meta-row { display: flex; justify-content: space-between; gap: 12px; font-size: 13px; }
        .report-meta-row + .report-meta-row { margin-top: 8px; }
        .report-meta-label { color: #627d98; }
        .report-meta-value { color: #102a43; font-weight: 700; text-align: right; }
        .report-card { border: 1px solid #d9e2ec; border-radius: 14px; padding: 16px; margin-bottom: 14px; }
        .report-card h2 { margin: 0 0 12px; font-size: 17px; color: #102a43; }
        .report-filter-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 10px; }
        .report-filter-item { border: 1px solid #e5edf5; border-radius: 12px; padding: 10px 12px; background: #f8fbff; }
        .report-filter-label { display: block; margin-bottom: 6px; font-size: 11px; font-weight: 700; letter-spacing: 0.04em; text-transform: uppercase; color: #627d98; }
        .report-filter-value { font-size: 13px; font-weight: 600; color: #102a43; line-height: 1.5; }
        .report-summary-grid { display: grid; grid-template-columns: repeat(5, minmax(0, 1fr)); gap: 10px; }
        .report-summary-card { border: 1px solid #d9e8f5; border-radius: 12px; padding: 12px; background: linear-gradient(180deg, #f7fbff 0%, #eef6ff 100%); }
        .report-summary-label { font-size: 11px; font-weight: 700; letter-spacing: 0.04em; text-transform: uppercase; color: #627d98; margin-bottom: 6px; }
        .report-summary-value { font-size: 22px; font-weight: 800; color: #102a43; margin-bottom: 4px; }
        .report-summary-note { font-size: 12px; color: #486581; }
        table { width: 100%; border-collapse: collapse; }
        th, td { border: 1px solid #d9e2ec; padding: 9px 10px; vertical-align: top; text-align: left; }
        th { background: #f1f5f9; font-size: 12px; color: #334e68; }
        td { font-size: 12px; color: #102a43; }
        .cell-title { font-weight: 700; margin-bottom: 4px; }
        .cell-subtext { font-size: 11px; color: #627d98; line-height: 1.5; }
        .empty-state { padding: 18px; text-align: center; border: 1px dashed #bcccdc; border-radius: 12px; color: #627d98; font-size: 13px; }
        @media print {
            .report-card { break-inside: avoid; }
        }
    </style>
</head>
<body>
    <div class="report-shell">
        <header class="report-header">
            <div>
                <h1 class="report-title">Personel ve Proje Is Yuku</h1>
                <p class="report-subtitle">Sorgu sonucunun yazdirilabilir standart cikti dokumudur.</p>
            </div>
            <div class="report-meta">
                <div class="report-meta-row"><span class="report-meta-label">Tarih</span><span class="report-meta-value">${escapeHtml(createdAtText)}</span></div>
                <div class="report-meta-row"><span class="report-meta-label">Olusturan</span><span class="report-meta-value">${escapeHtml(currentUser && currentUser.fullName ? currentUser.fullName : '-')}</span></div>
                <div class="report-meta-row"><span class="report-meta-label">Kayit</span><span class="report-meta-value">${escapeHtml(String(rows.length))}</span></div>
            </div>
        </header>

        <section class="report-card">
            <h2>Filtre Bilgileri</h2>
            <div class="report-filter-grid">
                <div class="report-filter-item">
                    <span class="report-filter-label">Personel</span>
                    <div class="report-filter-value">${escapeHtml(workloadElements.personFilter.value ? workloadElements.personFilter.options[workloadElements.personFilter.selectedIndex].text : 'Tum personeller')}</div>
                </div>
                <div class="report-filter-item">
                    <span class="report-filter-label">Proje</span>
                    <div class="report-filter-value">${escapeHtml(workloadElements.projectFilter.value ? workloadElements.projectFilter.options[workloadElements.projectFilter.selectedIndex].text : 'Tum projeler')}</div>
                </div>
                <div class="report-filter-item">
                    <span class="report-filter-label">Durum</span>
                    <div class="report-filter-value">${escapeHtml(workloadElements.statusFilter.options[workloadElements.statusFilter.selectedIndex].text)}</div>
                </div>
                <div class="report-filter-item">
                    <span class="report-filter-label">Arama</span>
                    <div class="report-filter-value">${escapeHtml(searchText)}</div>
                </div>
            </div>
        </section>

        <section class="report-card">
            <h2>Ozet</h2>
            <div class="report-summary-grid">
                <div class="report-summary-card">
                    <div class="report-summary-label">Kayit</div>
                    <div class="report-summary-value">${escapeHtml(String(summary.rowCount))}</div>
                    <div class="report-summary-note">${escapeHtml(`${summary.uniquePersonnelCount} personel / ${summary.uniqueProjectCount} proje`)}</div>
                </div>
                <div class="report-summary-card">
                    <div class="report-summary-label">Gorev</div>
                    <div class="report-summary-value">${escapeHtml(String(summary.totalTaskCount))}</div>
                    <div class="report-summary-note">${escapeHtml(`${summary.totalSubTaskCount} alt gorev`)}</div>
                </div>
                <div class="report-summary-card">
                    <div class="report-summary-label">Acik Is</div>
                    <div class="report-summary-value">${escapeHtml(String(summary.openItemCount))}</div>
                    <div class="report-summary-note">${escapeHtml(`${summary.completedItemCount} tamamlandi`)}</div>
                </div>
                <div class="report-summary-card">
                    <div class="report-summary-label">Toplam Saat</div>
                    <div class="report-summary-value">${escapeHtml(String(summary.totalEstimatedHours))}</div>
                    <div class="report-summary-note">${escapeHtml(`${summary.averageCompletionPercent}% ortalama ilerleme`)}</div>
                </div>
                <div class="report-summary-card">
                    <div class="report-summary-label">Iptal</div>
                    <div class="report-summary-value">${escapeHtml(String(summary.cancelledItemCount))}</div>
                    <div class="report-summary-note">${escapeHtml(`${summary.totalItemCount} toplam is kalemi`)}</div>
                </div>
            </div>
        </section>

        <section class="report-card">
            <h2>Sonuc Tablosu</h2>
            ${renderWorkloadPrintTable(rows)}
        </section>
    </div>
</body>
</html>`;
}

function renderWorkloadPrintTable(rows) {
    if (!rows.length) {
        return '<div class="empty-state">Secilen filtrelerle eslesen yazdirilabilir sonuc bulunamadi.</div>';
    }

    return `
        <table>
            <thead>
                <tr>
                    <th>PERSONEL</th>
                    <th>PROJE</th>
                    <th>GOREV</th>
                    <th>ALT GOREV</th>
                    <th>ACIK IS</th>
                    <th>TOPLAM SAAT</th>
                    <th>ORT. ILERLEME</th>
                    <th>DURUM DAGILIMI</th>
                </tr>
            </thead>
            <tbody>
                ${rows.map((row) => {
                    const roleLabel = ROLE_LABELS[row.personRole] || row.personRole || 'Personel';
                    const projectStatusText = row.projectStatus ? getStatusLabel(row.projectStatus) : 'Is yuku yok';
                    return `
                        <tr>
                            <td>
                                <div class="cell-title">${escapeHtml(row.personName || '-')}</div>
                                <div class="cell-subtext">@${escapeHtml(row.username || '-')} | ${escapeHtml(roleLabel)}</div>
                            </td>
                            <td>
                                <div class="cell-title">${escapeHtml(row.projectName || '-')}</div>
                                <div class="cell-subtext">${escapeHtml(projectStatusText)}</div>
                            </td>
                            <td>${safeNumber(row.taskCount)} / ${safeNumber(row.taskEstimatedHours)} saat</td>
                            <td>${safeNumber(row.subTaskCount)} / ${safeNumber(row.subTaskEstimatedHours)} saat</td>
                            <td>${safeNumber(row.openItemCount)} / ${safeNumber(row.totalItemCount)} toplam</td>
                            <td>${safeNumber(row.totalEstimatedHours)}</td>
                            <td>%${safeNumber(row.averageCompletionPercent)}</td>
                            <td>${escapeHtml(renderWorkloadPrintStatusText(row))}</td>
                        </tr>
                    `;
                }).join('')}
            </tbody>
        </table>`;
}

function renderWorkloadPrintStatusText(row) {
    const entries = [
        ['Todo', row.todoItemCount],
        ['Devam', row.inProgressItemCount],
        ['Review', row.inReviewItemCount],
        ['Bitti', row.completedItemCount],
        ['Iptal', row.cancelledItemCount]
    ]
        .filter((entry) => safeNumber(entry[1]) > 0)
        .map((entry) => `${entry[0]}: ${safeNumber(entry[1])}`);

    return entries.length ? entries.join(' | ') : 'Kayit yok';
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

    if (workloadElements.printButton) {
        workloadElements.printButton.disabled = isLoading;
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
