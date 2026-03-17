// Configuration
const API_BASE_URL = 'http://localhost:8080/api';

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
        defaultHeaders['Authorization'] = `Bearer ${token}`;
    }

    const config = {
        ...options,
        headers: {
            ...defaultHeaders,
            ...options.headers
        }
    };

    try {
        const response = await fetch(`${API_BASE_URL}${endpoint}`, config);

        // Handle unauthorized
        if (response.status === 401 || response.status === 403) {
            handleAuthError();
            throw new Error('Oturum süresi doldu veya yetkiniz yok.');
        }

        const data = await response.json();

        if (!response.ok) {
            throw new Error(data.message || 'API request failed');
        }

        return data;
    } catch (error) {
        console.error('API Error:', error);
        throw error;
    }
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
    // Populate user info in sidebar if exists
    const user = getUser();
    if (user && document.getElementById('sidebarUserName')) {
        document.getElementById('sidebarUserName').textContent = user.fullName;
        document.getElementById('sidebarUserRole').textContent =
            user.role === 'ADMIN' ? 'Sistem Yöneticisi' :
                user.role === 'MANAGER' ? 'Proje Müdürü' : 'Kullanıcı';
        document.getElementById('userAvatarText').textContent = user.fullName.charAt(0).toUpperCase();

        // Hide admin links for non-admins
        if (user.role !== 'ADMIN') {
            document.querySelectorAll('.admin-only').forEach(el => el.classList.add('d-none'));
        }
    }

    // Logout handling
    const logoutBtn = document.getElementById('logoutBtn');
    if (logoutBtn) {
        logoutBtn.addEventListener('click', () => {
            localStorage.removeItem('token');
            localStorage.removeItem('user');
            window.location.href = 'login.html';
        });
    }

    // Modal close events
    document.querySelectorAll('.modal-close, [data-dismiss="modal"]').forEach(btn => {
        btn.addEventListener('click', (e) => {
            const modal = e.target.closest('.modal-overlay');
            if (modal) ModalManager.close(modal.id);
        });
    });

    document.querySelectorAll('.modal-overlay').forEach(overlay => {
        overlay.addEventListener('click', (e) => {
            if (e.target === overlay) {
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
        case 'ON_HOLD': return 'status-todo';
        case 'IN_PROGRESS':
        case 'IN_REVIEW': return 'status-progress';
        case 'COMPLETED': return 'status-done';
        case 'CANCELLED': return 'status-cancelled';
        default: return 'status-todo';
    }
}

function getStatusLabel(status) {
    const labels = {
        'PLANNING': 'Planlama',
        'IN_PROGRESS': 'Devam Ediyor',
        'ON_HOLD': 'Beklemede',
        'COMPLETED': 'Tamamlandı',
        'CANCELLED': 'İptal',
        'TODO': 'Yapılacak',
        'IN_REVIEW': 'Planlandı'
    };
    return labels[status] || status;
}

function getPriorityLabel(priority) {
    const labels = {
        'LOW': 'Düşük',
        'MEDIUM': 'Orta',
        'HIGH': 'Yüksek',
        'CRITICAL': 'Kritik'
    };
    const text = labels[priority] || priority;
    if (priority === 'CRITICAL') {
        return `<span style="color:#DE350B; font-weight:600;">${text}</span>`;
    }
    return text;
}
