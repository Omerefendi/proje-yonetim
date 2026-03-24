// Configuration
const API_DEFAULT_PORT = '8080';
const API_PATH = '/api';
const API_BASE_URL_STORAGE_KEY = 'apiBaseUrl';
const LAST_WORKING_API_BASE_URL_STORAGE_KEY = 'lastWorkingApiBaseUrl';
const FRONTEND_DEV_PORTS = new Set(['3000', '4173', '5173', '5500', '8081']);
const LOCAL_API_FALLBACKS = [
    `http://localhost:${API_DEFAULT_PORT}${API_PATH}`,
    `http://127.0.0.1:${API_DEFAULT_PORT}${API_PATH}`
];

function normalizeApiBaseUrl(url) {
    return String(url || '').trim().replace(/\/+$/, '');
}

function uniqueApiBaseUrls(urls) {
    return [...new Set(urls.filter(Boolean).map(normalizeApiBaseUrl))];
}

function hasExplicitProtocol(url) {
    return /^[a-z][a-z0-9+.-]*:\/\//i.test(String(url || ''));
}

function ensureApiPath(url) {
    const normalizedUrl = normalizeApiBaseUrl(url);
    if (!normalizedUrl) {
        return null;
    }

    if (/\/api(?:$|[/?#])/i.test(normalizedUrl)) {
        return normalizedUrl;
    }

    return `${normalizedUrl}${API_PATH}`;
}

function expandApiBaseUrlCandidates(url) {
    const normalizedUrl = normalizeApiBaseUrl(url);
    if (!normalizedUrl) {
        return [];
    }

    const urlWithProtocol =
        !hasExplicitProtocol(normalizedUrl) && !normalizedUrl.startsWith('/')
            ? `http://${normalizedUrl}`
            : normalizedUrl;

    return uniqueApiBaseUrls([
        ensureApiPath(urlWithProtocol),
        urlWithProtocol
    ]);
}

function resolveStoredApiBaseUrls() {
    const configuredBaseUrl = window.__API_BASE_URL__ || localStorage.getItem(API_BASE_URL_STORAGE_KEY);
    const lastWorkingBaseUrl = localStorage.getItem(LAST_WORKING_API_BASE_URL_STORAGE_KEY);

    return uniqueApiBaseUrls([
        ...expandApiBaseUrlCandidates(configuredBaseUrl),
        ...expandApiBaseUrlCandidates(lastWorkingBaseUrl)
    ]);
}

function resolveDynamicApiBaseUrls() {
    const { protocol, hostname, port, origin } = window.location;

    if (protocol === 'file:' || !hostname) {
        return LOCAL_API_FALLBACKS;
    }

    const sameOriginApi = `${origin}${API_PATH}`;
    const hostApi = `${protocol}//${hostname}:${API_DEFAULT_PORT}${API_PATH}`;

    if (port === API_DEFAULT_PORT) {
        return [sameOriginApi];
    }

    if (FRONTEND_DEV_PORTS.has(port)) {
        return uniqueApiBaseUrls([hostApi, sameOriginApi, ...LOCAL_API_FALLBACKS]);
    }

    return uniqueApiBaseUrls([sameOriginApi, hostApi, ...LOCAL_API_FALLBACKS]);
}

function resolveApiBaseUrlCandidates() {
    return uniqueApiBaseUrls([
        ...resolveStoredApiBaseUrls(),
        ...resolveDynamicApiBaseUrls()
    ]);
}

let API_BASE_URL_CANDIDATES = resolveApiBaseUrlCandidates();
let API_BASE_URL = API_BASE_URL_CANDIDATES[0];

function refreshApiBaseUrlCandidates(preferredBaseUrl = null) {
    API_BASE_URL_CANDIDATES = uniqueApiBaseUrls([
        preferredBaseUrl,
        ...resolveApiBaseUrlCandidates()
    ]);
    API_BASE_URL = API_BASE_URL_CANDIDATES[0];
}

function rememberWorkingApiBaseUrl(baseUrl) {
    const normalizedBaseUrl = normalizeApiBaseUrl(baseUrl);
    if (!normalizedBaseUrl) {
        return;
    }

    try {
        localStorage.setItem(LAST_WORKING_API_BASE_URL_STORAGE_KEY, normalizedBaseUrl);
    } catch (error) {
        // Ignore storage write failures and continue with in-memory state.
    }

    refreshApiBaseUrlCandidates(normalizedBaseUrl);
}

function getStoredApiBaseUrl() {
    return normalizeApiBaseUrl(localStorage.getItem(API_BASE_URL_STORAGE_KEY));
}

function setConfiguredApiBaseUrl(url) {
    const candidates = expandApiBaseUrlCandidates(url);
    const storedValue = normalizeApiBaseUrl(url);

    if (storedValue) {
        localStorage.setItem(API_BASE_URL_STORAGE_KEY, storedValue);
        refreshApiBaseUrlCandidates(candidates[0] || storedValue);
        return API_BASE_URL;
    }

    localStorage.removeItem(API_BASE_URL_STORAGE_KEY);
    refreshApiBaseUrlCandidates();
    return API_BASE_URL;
}

function isLikelyApiResponse(response, payload) {
    const contentType = response.headers.get('content-type') || '';
    if (contentType.includes('application/json')) {
        return true;
    }

    if (payload.body && typeof payload.body === 'object') {
        return true;
    }

    return Boolean(extractResponseText(payload.rawText));
}

window.apiConfig = {
    clearBaseUrl: () => setConfiguredApiBaseUrl(''),
    getCandidates: () => [...API_BASE_URL_CANDIDATES],
    getCurrentBaseUrl: () => API_BASE_URL,
    getStoredBaseUrl: getStoredApiBaseUrl,
    setBaseUrl: setConfiguredApiBaseUrl
};

// Authentication Management
function getToken() {
    return localStorage.getItem('token');
}

function getUser() {
    const userStr = localStorage.getItem('user');
    return userStr ? JSON.parse(userStr) : null;
}

function handleAuthError() {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    window.location.href = 'login.html';
}

function checkAuth() {
    if (!getToken() && !window.location.pathname.endsWith('login.html')) {
        window.location.href = 'login.html';
        return false;
    }
    return true;
}

// Check auth on load
if (!window.location.pathname.endsWith('login.html')) {
    checkAuth();
}

// Main API Wrapper
async function apiCall(endpoint, options = {}) {
    const token = getToken();

    const defaultHeaders = {
        'Content-Type': 'application/json',
    };

    if (token) {
        defaultHeaders.Authorization = `Bearer ${token}`;
    }

    const config = {
        ...options,
        headers: {
            ...defaultHeaders,
            ...options.headers
        }
    };

    try {
        const { response, baseUrl } = await fetchWithResolvedApi(endpoint, config);
        const payload = await readApiPayload(response);

        if (isLikelyApiResponse(response, payload)) {
            rememberWorkingApiBaseUrl(baseUrl);
        }

        if (response.status === 401 || response.status === 403) {
            handleAuthError();
            throw createApiError(
                getApiErrorMessageFromPayload(response, payload, 'Oturum suresi doldu veya yetkiniz yok.'),
                { status: response.status, isAuthError: true }
            );
        }

        if (!response.ok) {
            throw createApiError(
                getApiErrorMessageFromPayload(response, payload, 'API istegi basarisiz oldu.'),
                { status: response.status }
            );
        }

        if (!payload.body) {
            throw createApiError('Sunucudan gecersiz bir yanit alindi.', {
                status: response.status
            });
        }

        return payload.body;
    } catch (error) {
        if (error instanceof TypeError) {
            const networkError = createApiError(
                'Sunucuya baglanilamadi. API adresini ve sunucunun calistigini kontrol edin.',
                { isNetworkError: true, cause: error }
            );
            console.error('API Error:', networkError);
            throw networkError;
        }

        console.error('API Error:', error);
        throw error;
    }
}

async function fetchWithResolvedApi(endpoint, config) {
    let lastError = null;

    for (const baseUrl of API_BASE_URL_CANDIDATES) {
        try {
            const response = await fetch(`${baseUrl}${endpoint}`, config);
            return { response, baseUrl };
        } catch (error) {
            lastError = error;
        }
    }

    throw lastError || new TypeError('API request failed');
}

async function readApiPayload(response) {
    const rawText = await response.text();

    if (!rawText) {
        return { body: null, rawText: '' };
    }

    try {
        return {
            body: JSON.parse(rawText),
            rawText
        };
    } catch (error) {
        return {
            body: null,
            rawText
        };
    }
}

function getApiErrorMessageFromPayload(response, payload, fallbackMessage) {
    if (payload.body && payload.body.message) {
        return payload.body.message;
    }

    const rawMessage = extractResponseText(payload.rawText);
    if (rawMessage) {
        return rawMessage;
    }

    if (response.status >= 500) {
        return 'Sunucuda bir hata olustu. Lutfen tekrar deneyin.';
    }

    return fallbackMessage;
}

function extractResponseText(rawText) {
    const trimmedText = String(rawText || '').trim();
    if (!trimmedText) {
        return null;
    }

    if (trimmedText.startsWith('<!DOCTYPE') || trimmedText.startsWith('<html')) {
        return null;
    }

    return trimmedText;
}

function createApiError(message, properties = {}) {
    const error = new Error(message);
    Object.assign(error, properties);
    return error;
}

function getApiErrorMessage(error, fallbackMessage = 'Bir hata olustu.') {
    return error && error.message ? error.message : fallbackMessage;
}

// Generic Modal Management
class ModalManager {
    static open(modalId) {
        const modal = document.getElementById(modalId);
        if (modal) {
            modal.classList.add('active');
            document.body.style.overflow = 'hidden';
        }
    }

    static close(modalId) {
        const modal = document.getElementById(modalId);
        if (modal) {
            modal.classList.remove('active');
            document.body.style.overflow = '';
        }
    }

    static closeAll() {
        document.querySelectorAll('.modal-overlay').forEach(modal => {
            modal.classList.remove('active');
        });
        document.body.style.overflow = '';
    }
}

// Close modals when clicking outside or on close buttons
document.addEventListener('DOMContentLoaded', () => {
    const user = getUser();
    if (user && document.getElementById('sidebarUserName')) {
        document.getElementById('sidebarUserName').textContent = user.fullName;
        document.getElementById('sidebarUserRole').textContent =
            user.role === 'ADMIN' ? 'Sistem Yoneticisi' :
                user.role === 'MANAGER' ? 'Proje Muduru' : 'Kullanici';
        document.getElementById('userAvatarText').textContent = user.fullName.charAt(0).toUpperCase();

        if (user.role !== 'ADMIN') {
            document.querySelectorAll('.admin-only').forEach(el => el.classList.add('d-none'));
        }
    }

    const logoutBtn = document.getElementById('logoutBtn');
    if (logoutBtn) {
        logoutBtn.addEventListener('click', () => {
            localStorage.removeItem('token');
            localStorage.removeItem('user');
            window.location.href = 'login.html';
        });
    }

    document.querySelectorAll('.modal-close, [data-dismiss="modal"]').forEach(btn => {
        btn.addEventListener('click', event => {
            const modal = event.target.closest('.modal-overlay');
            if (modal) {
                ModalManager.close(modal.id);
            }
        });
    });

    document.querySelectorAll('.modal-overlay').forEach(overlay => {
        overlay.addEventListener('click', event => {
            if (event.target === overlay) {
                ModalManager.close(overlay.id);
            }
        });
    });
});

// XSS Protection - HTML escape helper
function escapeHtml(str) {
    if (str == null) return '';
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#039;');
}

// Format date helper
function formatDate(dateString) {
    if (!dateString) return '-';
    const date = new Date(dateString);
    return date.toLocaleDateString('tr-TR');
}

// Get Badge class for status
function getStatusBadgeClass(status) {
    switch (status) {
        case 'TODO':
        case 'PLANNING':
        case 'ON_HOLD':
            return 'status-todo';
        case 'IN_PROGRESS':
        case 'IN_REVIEW':
            return 'status-progress';
        case 'COMPLETED':
            return 'status-done';
        case 'CANCELLED':
            return 'status-cancelled';
        default:
            return 'status-todo';
    }
}

function getStatusLabel(status) {
    const labels = {
        PLANNING: 'Planlama',
        IN_PROGRESS: 'Devam Ediyor',
        ON_HOLD: 'Beklemede',
        COMPLETED: 'Tamamlandi',
        CANCELLED: 'Iptal',
        TODO: 'Yapilacak',
        IN_REVIEW: 'Planlandi'
    };
    return labels[status] || status;
}

function getPriorityLabel(priority) {
    const labels = {
        LOW: 'Dusuk',
        MEDIUM: 'Orta',
        HIGH: 'Yuksek',
        CRITICAL: 'Kritik'
    };
    const text = labels[priority] || priority;
    if (priority === 'CRITICAL') {
        return `<span style="color:#DE350B; font-weight:600;">${text}</span>`;
    }
    return text;
}
