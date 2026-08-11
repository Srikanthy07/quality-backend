/**
 * IAST Quality Portal - global-search.js
 * Enhanced Global Search & Live Autocomplete
 * Features:
 * - LocalStorage Recent Searches (Max 5, client-only)
 * - Live Autocomplete Dropdown with Category Grouping:
 *   1) Processes
 *   2) Documents
 *   3) Generic Templates
 *   4) Lessons Learned
 * - Keyboard Navigation (Up, Down, Enter, Escape)
 * - Matched Keyword Highlighting (<mark>)
 * - Result count header ("XX Results Found")
 * - Friendly Empty & Error States
 */
(function () {
  'use strict';

  const RECENT_KEY = 'quality_recent_searches';

  /* ── 1. LocalStorage Recent Searches Helpers ── */
  function getRecentSearches() {
    try {
      const raw = localStorage.getItem(RECENT_KEY);
      return raw ? JSON.parse(raw) : [];
    } catch (e) {
      return [];
    }
  }

  function saveRecentSearch(query) {
    if (!query || !query.trim()) return;
    const q = query.trim();
    let list = getRecentSearches();
    list = list.filter(item => item.toLowerCase() !== q.toLowerCase());
    list.unshift(q);
    if (list.length > 5) list = list.slice(0, 5);
    try {
      localStorage.setItem(RECENT_KEY, JSON.stringify(list));
    } catch (e) {}
  }

  function clearRecentSearches() {
    try {
      localStorage.removeItem(RECENT_KEY);
    } catch (e) {}
  }

  /* ── 2. General Helpers ── */
  function getQueryParam(name) {
    const params = new URLSearchParams(window.location.search);
    return params.get(name) || '';
  }

  function escapeRegExp(string) {
    return string.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  }

  function escapeHtml(str) {
    return String(str || '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;');
  }

  function highlightText(text, tokens) {
    if (!text) return '';
    if (!tokens || !tokens.length) return escapeHtml(text);
    const pattern = tokens.filter(Boolean).map(escapeRegExp).join('|');
    if (!pattern) return escapeHtml(text);
    const regex = new RegExp(`(${pattern})`, 'gi');
    return escapeHtml(text).replace(regex, '<mark class="search-highlight">$1</mark>');
  }

  async function fetchSearchResults(query) {
    if (!query || !query.trim()) return [];
    const response = await fetch(`/api/public/search?query=${encodeURIComponent(query.trim())}`);
    if (!response.ok) throw new Error(`HTTP ${response.status}`);
    return await response.json();
  }

  /* ── 3. Live Autocomplete Component ── */
  function initNavbarAutocomplete() {
    const searchInputs = document.querySelectorAll('.navbar__search-input');
    searchInputs.forEach(input => {
      const form = input.closest('form');
      const box = input.closest('.navbar__search-box') || form || input.parentElement;

      // Create popup container
      let popup = box.querySelector('.search-autocomplete-popup');
      if (!popup) {
        popup = document.createElement('div');
        popup.className = 'search-autocomplete-popup';
        popup.hidden = true;
        box.appendChild(popup);
      }

      let debounceTimer = null;
      let activeIndex = -1;

      function closePopup() {
        popup.hidden = true;
        popup.innerHTML = '';
        activeIndex = -1;
      }

      function updateActiveItem(items) {
        items.forEach((item, idx) => {
          if (idx === activeIndex) {
            item.classList.add('active');
            item.scrollIntoView({ block: 'nearest' });
          } else {
            item.classList.remove('active');
          }
        });
      }

      function renderRecentPopup() {
        const recent = getRecentSearches();
        if (!recent.length) {
          closePopup();
          return;
        }

        let html = `
          <div class="autocomplete-recent-header">
            <span>Recent Searches</span>
            <span class="autocomplete-recent-clear" id="btn-clear-recent"><i class="fas fa-trash-can"></i> Clear</span>
          </div>
        `;

        recent.forEach((q, idx) => {
          html += `
            <div class="autocomplete-item recent-item" data-query="${escapeHtml(q)}" tabindex="-1">
              <span class="item-title"><i class="fas fa-history" style="color:#94a3b8; margin-right:8px;"></i>${escapeHtml(q)}</span>
              <span class="item-badge">Recent</span>
            </div>
          `;
        });

        popup.innerHTML = html;
        popup.hidden = false;
        activeIndex = -1;

        const clearBtn = popup.querySelector('#btn-clear-recent');
        if (clearBtn) {
          clearBtn.addEventListener('click', (e) => {
            e.stopPropagation();
            clearRecentSearches();
            closePopup();
          });
        }

        popup.querySelectorAll('.recent-item').forEach(item => {
          item.addEventListener('click', () => {
            const q = item.getAttribute('data-query');
            input.value = q;
            saveRecentSearch(q);
            if (form) form.submit();
            closePopup();
          });
        });
      }

      function renderSuggestionsPopup(results, query) {
        if (!results || !results.length) {
          popup.innerHTML = `<div style="padding:14px; text-align:center; color:#64748b; font-size:0.85rem;"><i class="fas fa-magnifying-glass" style="margin-right:6px;"></i> No matching suggestions found</div>`;
          popup.hidden = false;
          activeIndex = -1;
          return;
        }

        const tokens = query.trim().toLowerCase().split(/\s+/).filter(Boolean);

        // Group into 4 categories
        const groups = {
          'Processes': [],
          'Documents': [],
          'Generic Templates': [],
          'Lessons Learned': []
        };

        results.forEach(item => {
          const cat = (item.category || '').trim();
          if (cat === 'Generic Templates') {
            groups['Generic Templates'].push(item);
          } else if (cat === 'Lessons Learned') {
            groups['Lessons Learned'].push(item);
          } else if (item.processId) {
            groups['Processes'].push(item);
          } else {
            groups['Documents'].push(item);
          }
        });

        let html = '';
        const order = [
          { name: 'Processes', icon: 'fa-diagram-project' },
          { name: 'Documents', icon: 'fa-file-lines' },
          { name: 'Generic Templates', icon: 'fa-layer-group' },
          { name: 'Lessons Learned', icon: 'fa-lightbulb' }
        ];

        let totalRendered = 0;

        order.forEach(g => {
          const items = groups[g.name];
          if (items && items.length > 0) {
            html += `<div class="autocomplete-group-header"><i class="fas ${g.icon}"></i> ${g.name} (${items.length})</div>`;
            items.slice(0, 4).forEach(item => {
              totalRendered++;
              const title = item.processId ? `${item.processId} ${item.documentName || item.processName}` : (item.documentName || 'Document');
              const highlightedTitle = highlightText(title, tokens);
              const badge = item.category || item.processGroup || 'ASPICE';
              const targetUrl = item.pageUrl || `/search.html?search=${encodeURIComponent(query)}`;

              html += `
                <a href="${targetUrl}" class="autocomplete-item suggestion-item" data-query="${escapeHtml(query)}" tabindex="-1">
                  <span class="item-title">${highlightedTitle}</span>
                  <span class="item-badge">${escapeHtml(badge)}</span>
                </a>
              `;
            });
          }
        });

        popup.innerHTML = html;
        popup.hidden = false;
        activeIndex = -1;

        popup.querySelectorAll('.suggestion-item').forEach(item => {
          item.addEventListener('click', () => {
            saveRecentSearch(query);
            closePopup();
          });
        });
      }

      // Input Event Handlers
      input.addEventListener('focus', () => {
        if (!input.value.trim()) {
          renderRecentPopup();
        }
      });

      input.addEventListener('input', () => {
        clearTimeout(debounceTimer);
        const val = input.value.trim();

        if (!val) {
          renderRecentPopup();
          return;
        }

        popup.innerHTML = `<div style="padding:14px; text-align:center; color:#64748b; font-size:0.85rem;"><i class="fas fa-spinner fa-spin" style="margin-right:6px;"></i> Searching...</div>`;
        popup.hidden = false;

        debounceTimer = setTimeout(() => {
          fetchSearchResults(val)
            .then(res => renderSuggestionsPopup(res, val))
            .catch(() => closePopup());
        }, 220);
      });

      // Keyboard Navigation (Up, Down, Enter, Escape)
      input.addEventListener('keydown', (e) => {
        const items = Array.from(popup.querySelectorAll('.autocomplete-item'));
        if (popup.hidden || !items.length) {
          if (e.key === 'Enter') {
            saveRecentSearch(input.value);
          }
          return;
        }

        if (e.key === 'ArrowDown') {
          e.preventDefault();
          activeIndex = (activeIndex + 1) % items.length;
          updateActiveItem(items);
        } else if (e.key === 'ArrowUp') {
          e.preventDefault();
          activeIndex = (activeIndex - 1 + items.length) % items.length;
          updateActiveItem(items);
        } else if (e.key === 'Enter') {
          if (activeIndex >= 0 && items[activeIndex]) {
            e.preventDefault();
            items[activeIndex].click();
          } else {
            saveRecentSearch(input.value);
            closePopup();
          }
        } else if (e.key === 'Escape') {
          closePopup();
        }
      });

      // Form submission saves recent search
      if (form) {
        form.addEventListener('submit', () => {
          saveRecentSearch(input.value);
          closePopup();
        });
      }

      // Click outside closes popup
      document.addEventListener('click', (e) => {
        if (!box.contains(e.target)) {
          closePopup();
        }
      });
    });
  }

  /* ── 4. Global Search Results Page Renderer (`search.html`) ── */
  function initSearchResultsPage() {
    const resultsPage = document.querySelector('.search-page');
    if (!resultsPage) return;

    const query = getQueryParam('search');

    function showState(idsToShow) {
      ['search-idle', 'search-skeleton', 'search-results', 'empty-state', 'search-error', 'search-filters']
        .forEach(id => {
          const node = document.getElementById(id);
          if (!node) return;
          node.hidden = !idsToShow.includes(id);
        });
    }

    if (!query) {
      showState(['search-idle']);
      return;
    }

    showState(['search-skeleton']);

    const queryLine = document.getElementById('search-query-line');
    const queryTerm = document.getElementById('search-query-term');
    const summary = document.getElementById('search-summary');

    if (queryLine && queryTerm) {
      queryTerm.textContent = `"${query}"`;
      queryLine.hidden = false;
    }

    fetchSearchResults(query)
      .then(results => {
        if (!results || !results.length) {
          if (summary) summary.innerHTML = '';
          showState(['empty-state']);
          return;
        }

        const totalCount = results.length;
        if (summary) {
          summary.innerHTML = `<i class="fas fa-check-circle" style="color:#00aabb;"></i> <strong>${totalCount}</strong> Result${totalCount === 1 ? '' : 's'} Found`;
        }

        renderResultsList(results, query);
      })
      .catch(err => {
        console.error('Failed to load search results:', err);
        showState(['search-error']);
      });
  }

  function renderResultsList(results, query) {
    const container = document.getElementById('search-results');
    if (!container) return;
    container.innerHTML = '';

    const tokens = query.trim().toLowerCase().split(/\s+/).filter(Boolean);

    // Group results by Process Group or Category
    const groups = new Map();
    results.forEach(r => {
      const grp = r.processGroup || r.category || 'General';
      if (!groups.has(grp)) groups.set(grp, []);
      groups.get(grp).push(r);
    });

    const frag = document.createDocumentFragment();

    groups.forEach((items, groupName) => {
      const groupSection = document.createElement('div');
      groupSection.className = 'search-group';

      const title = document.createElement('h3');
      title.className = 'search-group-title';
      title.textContent = `${groupName} (${items.length})`;
      groupSection.appendChild(title);

      const grid = document.createElement('div');
      grid.className = 'process-grid' + (items.length === 1 ? ' process-grid--single' : '');

      items.forEach(item => {
        const card = document.createElement('div');
        card.className = 'process-card';

        const processIdHtml = item.processId ? highlightText(item.processId, tokens) : '';
        const processNameHtml = highlightText(item.processName || item.documentName, tokens);
        const categoryHtml = highlightText(item.category || item.processGroup, tokens);
        const docNameHtml = highlightText(item.documentName || 'Document', tokens);
        const versionHtml = item.version ? `v${escapeHtml(item.version)}` : 'v1.0';
        const pageUrl = item.pageUrl || `/section-details.html?section=${encodeURIComponent(item.processId || '')}`;

        card.innerHTML = `
          <div class="process-card__header">
            ${item.processId ? `<span class="process-card__id">${processIdHtml}</span>` : `<span class="process-card__id" style="background:#e0f2fe; color:#0284c7;">${escapeHtml(item.category || 'DOC')}</span>`}
            <span class="process-card__group">${categoryHtml}</span>
          </div>
          <h4 class="process-card__name">${processNameHtml}</h4>
          <div class="process-card__docs-label">Document Details</div>
          <ul class="process-card__docs">
            <li class="process-card__doc">
              <span class="process-card__doc-name">${docNameHtml} <small style="color:#64748b; font-weight:600; margin-left:6px;">(${versionHtml})</small></span>
              <a href="${pageUrl}" class="process-card__doc-view" aria-label="View Document">
                <i class="fas fa-arrow-up-right-from-square"></i>
              </a>
            </li>
          </ul>
          <div class="process-card__footer">
            <a href="${pageUrl}" class="process-card__open-btn">
              View / Open <i class="fas fa-arrow-right"></i>
            </a>
          </div>
        `;
        grid.appendChild(card);
      });

      groupSection.appendChild(grid);
      frag.appendChild(groupSection);
    });

    container.appendChild(frag);
    showState(['search-results']);
  }

  function showState(idsToShow) {
    ['search-idle', 'search-skeleton', 'search-results', 'empty-state', 'search-error', 'search-filters']
      .forEach(id => {
        const node = document.getElementById(id);
        if (!node) return;
        node.hidden = !idsToShow.includes(id);
      });
  }

  /* ── 5. Initialize ── */
  document.addEventListener('DOMContentLoaded', () => {
    initNavbarAutocomplete();
    initSearchResultsPage();
  });

})();