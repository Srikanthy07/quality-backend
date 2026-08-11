(function () {
  'use strict';

  var container = document.getElementById('generic-templates-container');

  function getBadgeClass(type) {
    var t = String(type || '').toUpperCase();
    if (t === 'XLS' || t === 'XLSX') return 'sd-doc-badge--xls';
    if (t === 'PPT' || t === 'PPTX') return 'sd-doc-badge--ppt';
    if (t === 'DOC' || t === 'DOCX') return 'sd-doc-badge--doc';
    if (t === 'HTML' || t === 'HTM') return 'sd-doc-badge--html';
    return 'sd-doc-badge--pdf';
  }

  function typeLabel(type) {
    var t = String(type || '').toUpperCase();
    if (t === 'DOCX') return 'DOC';
    if (t === 'PPTX') return 'PPT';
    if (t === 'XLSX') return 'XLS';
    return t.substring(0, 4);
  }

  function filenameFromUrl(url) {
    return decodeURIComponent((url || '').split('/').pop()) || 'document';
  }

  function isPdfDoc(doc, filePath) {
    if (!doc) return false;
    var type = String(doc.type || '').toUpperCase();
    var path = String(filePath || '').toLowerCase();
    return type === 'PDF' || path.slice(-4) === '.pdf';
  }

  function buildDocCard(doc) {
    var card = document.createElement('div');
    card.className = 'sd-doc-card';
    if (doc.id) {
      card.id = 'doc-' + doc.id;
      card.setAttribute('data-doc-id', doc.id);
    }

    var type = doc.type || (doc.filePath || '').split('.').pop() || 'DOC';
    type = type.toUpperCase();

    var badge = document.createElement('div');
    badge.className = 'sd-doc-badge ' + getBadgeClass(type);
    badge.textContent = typeLabel(type);

    var info = document.createElement('div');
    info.className = 'sd-doc-info';

    var filePath = (doc.filePath && typeof doc.filePath === 'string') ? doc.filePath.trim() : '';
    var filename = filePath ? filenameFromUrl(filePath) : '';

    var label = document.createElement('div');
    label.className = 'sd-doc-label';
    label.textContent = doc.documentName || filename || 'document';

    var version = document.createElement('div');
    version.className = 'sd-doc-version';
    version.textContent = 'Version: ' + (doc.version || '1.0');

    info.appendChild(label);
    info.appendChild(version);

    if (doc.description) {
      var desc = document.createElement('div');
      desc.className = 'sd-doc-desc';
      desc.textContent = doc.description;
      info.appendChild(desc);
    }

    if (filePath) {
      var fname = document.createElement('span');
      fname.className = 'sd-doc-filename';
      fname.textContent = filename;
      info.appendChild(fname);
    }

    var actions = document.createElement('div');
    actions.className = 'sd-doc-actions';

    if (filePath) {
      if (isPdfDoc(doc, filePath)) {
        var viewBtn = document.createElement('a');
        viewBtn.className = 'sd-doc-btn sd-doc-btn--view';
        viewBtn.href = filePath;
        viewBtn.target = '_blank';
        viewBtn.rel = 'noopener noreferrer';
        viewBtn.textContent = 'View Document';
        actions.appendChild(viewBtn);
      }

      var dlBtn = document.createElement('a');
      dlBtn.className = 'sd-doc-btn sd-doc-btn--dl';
      dlBtn.href = filePath;
      dlBtn.download = filename;
      dlBtn.textContent = 'Download';
      actions.appendChild(dlBtn);
    } else {
      var na = document.createElement('span');
      na.className = 'sd-doc-btn';
      na.style = 'background:#f1f5f9;color:#94a3b8;cursor:default;border:1.5px dashed #cbd5e1;box-shadow:none;';
      na.textContent = 'Document unavailable';
      actions.appendChild(na);
    }

    info.appendChild(actions);
    card.appendChild(badge);
    card.appendChild(info);

    return card;
  }

  function renderPlaceholder() {
    if (!container) return;
    container.innerHTML = '';
    var card = document.createElement('div');
    card.className = 'sd-card';
    var body = document.createElement('div');
    body.className = 'sd-card__body';

    var empty = document.createElement('div');
    empty.className = 'sd-docs-empty';

    var icon = document.createElement('i');
    icon.className = 'fas fa-folder-open';

    var p = document.createElement('p');
    p.textContent = 'Generic Templates will be available here once they are published.';
    p.style.fontSize = '1rem';
    p.style.fontWeight = '500';
    p.style.color = '#64748b';
    p.style.marginTop = '10px';

    empty.appendChild(icon);
    empty.appendChild(p);
    body.appendChild(empty);
    card.appendChild(body);
    container.appendChild(card);
  }

  function loadTemplates() {
    if (!container) return;

    // Show loading state
    container.innerHTML = '';
    var loadingCard = document.createElement('div');
    loadingCard.className = 'sd-card';
    var loadingBody = document.createElement('div');
    loadingBody.className = 'sd-card__body';
    
    var loadingWrap = document.createElement('div');
    loadingWrap.className = 'sd-docs-empty';
    var loadingIcon = document.createElement('i');
    loadingIcon.className = 'fas fa-spinner fa-spin';
    var loadingText = document.createElement('p');
    loadingText.textContent = 'Loading templates...';
    loadingWrap.appendChild(loadingIcon);
    loadingWrap.appendChild(loadingText);
    loadingBody.appendChild(loadingWrap);
    loadingCard.appendChild(loadingBody);
    container.appendChild(loadingCard);

    fetch('/api/public/generic-templates')
      .then(function (res) {
        if (!res.ok) return fetch('data/generic-templates.json').then(function(r){ return r.json(); });
        return res.json();
      })
      .then(function (docs) {
        if (!Array.isArray(docs) || docs.length === 0) {
          renderPlaceholder();
          return;
        }

        container.innerHTML = '';
        var card = document.createElement('div');
        card.className = 'sd-card';
        var body = document.createElement('div');
        body.className = 'sd-card__body';

        var grid = document.createElement('div');
        grid.className = 'sd-docs-grid';

        docs.forEach(function (doc) {
          grid.appendChild(buildDocCard(doc));
        });

        body.appendChild(grid);
        card.appendChild(body);
        container.appendChild(card);
      })
      .catch(function () {
        renderPlaceholder();
      });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', loadTemplates);
  } else {
    loadTemplates();
  }

})();
