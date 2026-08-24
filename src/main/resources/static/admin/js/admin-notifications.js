/**
 * Centralized Admin Notification System
 * Standardized across all Admin Portal pages.
 */
(function() {
  'use strict';

  window.adminNotify = function(options) {
    let title = '';
    let message = '';
    let type = 'success'; // 'success' | 'warning' | 'duplicate' | 'danger' | 'error' | 'info'
    let customDismiss = null;

    if (typeof options === 'string') {
      message = options;
    } else if (options && typeof options === 'object') {
      title = options.title || '';
      message = options.message || '';
      type = options.type || 'success';
      if (options.autoDismiss !== undefined) {
        customDismiss = options.autoDismiss;
      }
    }

    if (type === 'error') type = 'danger';

    // Standardized default auto-dismiss durations
    let autoDismiss = 3000; // Success default: ~3s
    if (type === 'warning' || type === 'duplicate') {
      autoDismiss = 4000; // Warning/Duplicate: ~4s
    } else if (type === 'danger') {
      autoDismiss = 5000; // Danger/Error: ~5s
    } else if (type === 'info') {
      autoDismiss = 3500; // Info: ~3.5s
    }
    if (customDismiss !== null) {
      autoDismiss = customDismiss;
    }

    // Choose icon based on notification type
    let iconClass = 'fas fa-check-circle';
    if (type === 'warning' || type === 'duplicate') {
      iconClass = 'fas fa-exclamation-triangle';
    } else if (type === 'danger') {
      iconClass = 'fas fa-times-circle';
    } else if (type === 'info') {
      iconClass = 'fas fa-info-circle';
    }

    const alertEl = document.createElement('div');
    alertEl.className = `admin-alert admin-alert--${type} alert alert-${type}`;

    let inner = `<i class="${iconClass} admin-alert__icon"></i><div class="admin-alert__content">`;
    if (title) {
      inner += `<div class="admin-alert__title">${escapeHtml(title)}</div>`;
    }
    if (message) {
      inner += `<div class="admin-alert__body">${escapeHtml(message)}</div>`;
    }
    inner += `</div><button type="button" class="admin-alert__close" aria-label="Dismiss">&times;</button>`;
    alertEl.innerHTML = inner;

    // Attach manual dismiss listener
    const closeBtn = alertEl.querySelector('.admin-alert__close');
    if (closeBtn) {
      closeBtn.addEventListener('click', function() {
        dismissAlert(alertEl);
      });
    }

    // Floating Toast rendering (Top-Right viewport overlay)
    let toastContainer = document.getElementById('admin-toast-container');
    if (!toastContainer) {
      toastContainer = document.createElement('div');
      toastContainer.id = 'admin-toast-container';
      document.body.appendChild(toastContainer);
    }
    toastContainer.appendChild(alertEl);

    if (autoDismiss > 0) {
      setTimeout(function() {
        dismissAlert(alertEl);
      }, autoDismiss);
    }
    return alertEl;
  };

  function dismissAlert(alertEl) {
    if (!alertEl || alertEl.classList.contains('admin-toast-dismissed')) return;
    alertEl.classList.add('admin-toast-dismissed');
    setTimeout(function() {
      if (alertEl.parentNode) {
        alertEl.parentNode.removeChild(alertEl);
      }
    }, 260);
  }

  window.clearAdminNotify = function(containerId) {
    if (containerId) {
      const targetContainer = document.getElementById(containerId);
      if (targetContainer) {
        targetContainer.innerHTML = '';
        targetContainer.style.display = 'none';
      }
    }
    const toastContainer = document.getElementById('admin-toast-container');
    if (toastContainer) {
      toastContainer.innerHTML = '';
    }
  };

  window.markFieldInvalid = function(fieldId, message) {
    const field = typeof fieldId === 'string' ? document.getElementById(fieldId) : fieldId;
    if (!field) return;
    field.classList.add('is-invalid');

    let parent = field.parentNode;
    let feedback = parent.querySelector('.invalid-feedback');
    if (!feedback) {
      feedback = document.createElement('div');
      feedback.className = 'invalid-feedback';
      parent.appendChild(feedback);
    }
    feedback.innerHTML = '<i class="fas fa-exclamation-circle"></i> <span>' + escapeHtml(message) + '</span>';
    feedback.style.display = 'flex';
  };

  window.clearFieldValidation = function(fieldId) {
    const field = typeof fieldId === 'string' ? document.getElementById(fieldId) : fieldId;
    if (!field) return;
    field.classList.remove('is-invalid');
    let parent = field.parentNode;
    let feedback = parent.querySelector('.invalid-feedback');
    if (feedback) {
      feedback.remove();
    }
  };

  function escapeHtml(str) {
    if (!str) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }
})();
