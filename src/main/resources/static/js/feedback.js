/**
 * feedback.js — Feedback Form Logic  v2.1
 *
 * CHANGES FROM v2.0
 * ─────────────────
 * • No logic changes — the v2.0 JS was already correct.
 * • The honeypot input[name="_hp"] referenced on line 229 was NOT present
 *   in the HTML. Added it to the form in index.html (see index.html fix).
 *   The JS gracefully skips the check if the element is absent, so this
 *   was not a crash bug, just a missing bot-trap.
 *
 * VALIDATION LAYERS
 * ─────────────────
 * 1. Real-time  — fires on every keystroke (input event): clears errors as
 *                 user corrects them; re-validates if field was already touched.
 * 2. On-blur    — fires when field loses focus: full field validation so
 *                 user gets immediate feedback after leaving each field.
 * 3. On-submit  — full pass before any network call; focuses first bad field.
 * 4. Server     — maps server-side field errors back to inline error spans.
 *
 * RULES
 * ─────
 * Name    : required · 2–100 chars · letters/spaces/hyphens/apostrophes only · no numbers
 * Email   : required · RFC-style format · no placeholder addresses
 * Message : required · 10–2000 chars · min 2 words · no spam patterns · no all-caps
 *
 * EXTRAS
 * ──────
 * • Honeypot field (_hp) — if filled, silently reject (bot trap)
 * • Character counter turns orange at 90 %, red at limit
 * • Duplicate-submit guard
 */
(function () {
  'use strict';

  /* ── DOM refs ── */
  const form        = document.getElementById('feedbackForm');
  if (!form) return;

  const nameInput   = document.getElementById('name');
  const emailInput  = document.getElementById('email');
  const msgInput    = document.getElementById('message');
  const charCountEl = document.getElementById('charCount');
  const submitBtn   = document.getElementById('submitBtn');

  const successAlert = document.getElementById('successAlert');
  const errorAlert   = document.getElementById('errorAlert');
  const successMsg   = document.getElementById('successMessage');
  const errorMsg     = document.getElementById('errorMessage');

  /* ── Constants ── */
  const NAME_MIN      = 2;
  const NAME_MAX      = 100;
  const MSG_MIN       = 10;
  const MSG_MAX       = 2000;
  const MSG_MIN_WORDS = 2;
  const CHAR_WARN_PCT = 0.90;
  const EMAIL_RE      = /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/;
  const NAME_RE       = /^[A-Za-zÀ-ÖØ-öø-ÿ\s'\-\.]+$/;

  /* ── State ── */
  let isSubmitting = false;
  const touched    = { name: false, email: false, message: false };

  /* ── Field error helpers ── */
  function getErrEl(el) {
    return document.getElementById(el.id + 'Error');
  }
  function clearFieldError(el) {
    el.classList.remove('invalid', 'valid');
    const e = getErrEl(el); if (e) e.textContent = '';
  }
  function markInvalid(el, msg) {
    el.classList.add('invalid'); el.classList.remove('valid');
    const e = getErrEl(el); if (e) e.textContent = msg;
  }
  function markValid(el) {
    el.classList.remove('invalid'); el.classList.add('valid');
    const e = getErrEl(el); if (e) e.textContent = '';
  }

  /* ── Alert helpers ── */
  function hideAlerts() {
    successAlert.classList.remove('show');
    errorAlert.classList.remove('show');
  }
  function showSuccess(message) {
    hideAlerts();
    successMsg.textContent = message || 'Thank you! Your feedback has been submitted successfully.';
    successAlert.classList.add('show');
    successAlert.scrollIntoView({ behavior: 'smooth', block: 'center' });
  }
  function showError(message) {
    hideAlerts();
    errorMsg.textContent = message || 'Something went wrong. Please try again.';
    errorAlert.classList.add('show');
    errorAlert.scrollIntoView({ behavior: 'smooth', block: 'center' });
  }

  /* ── Loading state ── */
  function setLoading(on) {
    isSubmitting = on;
    submitBtn.disabled = on;
    submitBtn.classList.toggle('loading', on);
  }

  /* ── String utils ── */
  function wordCount(s) {
    return s.trim().split(/\s+/).filter(w => w.length > 0).length;
  }
  function visibleLen(s) {
    return s.replace(/[\x00-\x08\x0B\x0C\x0E-\x1F\x7F]/g, '').length;
  }

  /* ── Per-field validators ── */
  function validateName(v) {
    const s = v.trim();
    if (!s)                      return 'Name is required.';
    if (s.length < NAME_MIN)     return `Name must be at least ${NAME_MIN} characters.`;
    if (s.length > NAME_MAX)     return `Name must not exceed ${NAME_MAX} characters.`;
    if (!NAME_RE.test(s))        return 'Name may only contain letters, spaces, hyphens, and apostrophes.';
    if (/\d/.test(s))            return 'Name should not contain numbers.';
    if (/(.)\\1{4,}/.test(s))    return 'Name appears invalid (too many repeated characters).';
    return '';
  }

  function validateEmail(v) {
    const s = v.trim();
    if (!s)                      return 'Email address is required.';
    if (!EMAIL_RE.test(s))       return 'Please enter a valid email address (e.g. name@example.com).';
    if (s.length > 150)          return 'Email address is too long.';
    const local = s.split('@')[0].toLowerCase();
    if (['test','noreply','no-reply','admin','null','undefined'].includes(local))
                                 return 'Please use a real email address.';
    return '';
  }

  function validateMessage(v) {
    const s   = v.trim();
    const len = visibleLen(s);
    if (!s)                           return 'Message is required.';
    if (len < MSG_MIN)                return `Message must be at least ${MSG_MIN} characters (currently ${len}).`;
    if (len > MSG_MAX)                return `Message must not exceed ${MSG_MAX} characters (currently ${len}).`;
    if (wordCount(s) < MSG_MIN_WORDS) return `Please write at least ${MSG_MIN_WORDS} words in your message.`;
    if (/(.)\\1{9,}/.test(s))         return 'Message appears to contain spam characters.';
    if (wordCount(s) > 5 && s === s.toUpperCase() && /[A-Z]/.test(s))
                                      return 'Please avoid writing in all capital letters.';
    return '';
  }

  function checkField(el, fn) {
    const err = fn(el.value);
    if (err) { markInvalid(el, err); return false; }
    markValid(el); return true;
  }

  function validateAll() {
    const ok1 = checkField(nameInput,  validateName);
    const ok2 = checkField(emailInput, validateEmail);
    const ok3 = checkField(msgInput,   validateMessage);
    if (!ok1) { nameInput.focus();  return false; }
    if (!ok2) { emailInput.focus(); return false; }
    if (!ok3) { msgInput.focus();   return false; }
    return true;
  }

  function applyServerFieldErrors(errors) {
    if (!errors || typeof errors !== 'object') return;
    const map = { name: nameInput, email: emailInput, message: msgInput };
    Object.keys(errors).forEach(f => { if (map[f]) markInvalid(map[f], errors[f]); });
  }

  /* ── Character counter ── */
  function updateCharCount() {
    const len   = visibleLen(msgInput.value);
    const ratio = len / MSG_MAX;
    charCountEl.textContent = len;
    charCountEl.style.color =
      ratio >= 1             ? 'var(--error,   #dc2626)' :
      ratio >= CHAR_WARN_PCT ? 'var(--warning, #d97706)' : '';
  }

  /* ── Real-time listeners ── */
  nameInput.addEventListener('input', () => {
    clearFieldError(nameInput);
    if (touched.name) checkField(nameInput, validateName);
  });
  emailInput.addEventListener('input', () => {
    clearFieldError(emailInput);
    if (touched.email) checkField(emailInput, validateEmail);
  });
  msgInput.addEventListener('input', () => {
    updateCharCount();
    if (touched.message) checkField(msgInput, validateMessage);
  });

  /* ── Blur listeners ── */
  nameInput.addEventListener('blur', () => {
    touched.name = true;
    checkField(nameInput, validateName);
  });
  emailInput.addEventListener('blur', () => {
    touched.email = true;
    checkField(emailInput, validateEmail);
  });
  msgInput.addEventListener('blur', () => {
    touched.message = true;
    checkField(msgInput, validateMessage);
  });

  /* ── Submit handler ── */
  form.addEventListener('submit', async (e) => {
    e.preventDefault();
    hideAlerts();
    if (isSubmitting) return;

    // Honeypot — silently reject bots
    const hp = form.querySelector('input[name="_hp"]');
    if (hp && hp.value.trim() !== '') {
      showSuccess();
      form.reset();
      if (charCountEl) charCountEl.textContent = '0';
      return;
    }

    touched.name = touched.email = touched.message = true;
    if (!validateAll()) return;

    const payload = {
      name:    nameInput.value.trim(),
      email:   emailInput.value.trim(),
      message: msgInput.value.trim(),
    };

    setLoading(true);

    try {
      const response = await fetch('/api/feedback', {
        method:  'POST',
        headers: { 'Content-Type': 'application/json', 'Accept': 'application/json' },
        body:    JSON.stringify(payload),
      });

      let data = null;
      try { data = await response.json(); } catch (_) { /* non-JSON body */ }

      if (response.ok && data && data.success) {
        showSuccess(data.message);
        form.reset();
        if (charCountEl) {
          charCountEl.textContent = '0';
          charCountEl.style.color = '';
        }
        [nameInput, emailInput, msgInput].forEach(el => el.classList.remove('invalid', 'valid'));
        Object.keys(touched).forEach(k => { touched[k] = false; });

      } else if (response.status === 400 && data) {
        applyServerFieldErrors(data.errors);
        showError(data.message || 'Please correct the highlighted fields and try again.');

      } else if (data && data.message) {
        showError(data.message);

      } else {
        showError('Unable to submit feedback right now. Please try again later.');
      }

    } catch (err) {
      showError('Network error. Please check your connection and try again.');

    } finally {
      setLoading(false);
    }
  });

  /* ── Init ── */
  updateCharCount();

})();