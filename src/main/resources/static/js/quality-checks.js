/**
 * quality-checks.js
 * IAST Quality Portal — Quality Checks page
 *
 * Reads ?type=project|system|software from the URL, shows the matching
 * .qc-section (#section-project / #section-system / #section-software),
 * hides the rest, and updates the hero badge/title/tabs to match.
 *
 * Drop this file at:  /js/quality-checks.js
 * Pair with:           /quality-checks.html
 */

(function () {
  'use strict';

  var GATES = {
    project: {
      section: 'section-project',
      tab:     'tab-project',
      tabActiveClass: 'active-proj',
      badgeClass: 'qc-hero__badge--proj',
      badgeText:  'Project',
      title:      'Project Quality Gates',
      sub:        'Project-level quality gates from initiation through closure.'
    },
    system: {
      section: 'section-system',
      tab:     'tab-system',
      tabActiveClass: 'active-sys',
      badgeClass: 'qc-hero__badge--sys',
      badgeText:  'System',
      title:      'System Quality Gate',
      sub:        'System engineering quality gates covering requirements, architecture, integration & release.'
    },
    software: {
      section: 'section-software',
      tab:     'tab-software',
      tabActiveClass: 'active-sw',
      badgeClass: 'qc-hero__badge--sw',
      badgeText:  'Software',
      title:      'Software Quality Gate',
      sub:        'Software development quality gates covering design, coding, testing & release readiness.'
    }
  };

  function getTypeParam() {
    var params = new URLSearchParams(window.location.search);
    var type = (params.get('type') || '').toLowerCase().trim();
    return type || 'project';
  }

  function hideAllSections() {
    var sections = document.querySelectorAll('.qc-section');
    for (var i = 0; i < sections.length; i++) {
      sections[i].classList.remove('visible');
    }
  }

  function clearTabState() {
    var tabs = document.querySelectorAll('.qc-tab');
    for (var i = 0; i < tabs.length; i++) {
      tabs[i].classList.remove('active-proj', 'active-sys', 'active-sw');
    }
  }

  function clearBadgeState(badgeEl) {
    badgeEl.classList.remove(
      'qc-hero__badge--proj',
      'qc-hero__badge--sys',
      'qc-hero__badge--sw'
    );
  }

  function showInvalid() {
    hideAllSections();
    var invalid = document.getElementById('section-invalid');
    if (invalid) invalid.classList.add('visible');

    var titleEl = document.getElementById('qc-title');
    var subEl   = document.getElementById('qc-sub');
    var badgeEl = document.getElementById('qc-badge');
    var pageTitleEl = document.getElementById('page-title');

    if (titleEl) titleEl.textContent = 'Quality Gate Checks';
    if (subEl)   subEl.textContent   = 'Select a gate type below to view the detailed quality checks.';
    if (badgeEl) { clearBadgeState(badgeEl); badgeEl.textContent = 'Gates'; }
    if (pageTitleEl) pageTitleEl.textContent = 'IAST Quality Gates | Quality Portal';

    clearTabState();
  }

  function renderGate(type) {
    var config = GATES[type];

    if (!config) {
      showInvalid();
      return;
    }

    hideAllSections();
    var sectionEl = document.getElementById(config.section);
    if (sectionEl) sectionEl.classList.add('visible');

    // Hero text
    var titleEl = document.getElementById('qc-title');
    var subEl   = document.getElementById('qc-sub');
    var badgeEl = document.getElementById('qc-badge');
    var pageTitleEl = document.getElementById('page-title');

    if (titleEl) titleEl.textContent = config.title;
    if (subEl)   subEl.textContent   = config.sub;
    if (badgeEl) {
      clearBadgeState(badgeEl);
      badgeEl.classList.add(config.badgeClass);
      badgeEl.textContent = config.badgeText;
    }
    if (pageTitleEl) pageTitleEl.textContent = config.title + ' | IAST Quality Portal';

    // Tabs
    clearTabState();
    var tabEl = document.getElementById(config.tab);
    if (tabEl) tabEl.classList.add(config.tabActiveClass);
  }

  function init() {
    var type = getTypeParam();
    renderGate(type);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();