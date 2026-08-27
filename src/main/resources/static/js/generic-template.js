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

  function getDocFormatExtension(doc) {
    if (!doc) return '';
    var fname = (doc.fileName || doc.filename || '').trim();
    if (fname && fname.indexOf('.') !== -1) {
      var ext = fname.split('.').pop().trim().toUpperCase();
      if (ext && ext.indexOf('/') === -1 && ext.indexOf('\\') === -1) {
        return ext;
      }
    }
    var ftype = (doc.fileType || doc.type || '').trim().toUpperCase();
    if (ftype) {
      if (ftype.startsWith('.')) ftype = ftype.substring(1);
      return ftype;
    }
    return '';
  }

  function typeLabel(typeOrDoc) {
    var ext = (typeof typeOrDoc === 'object' && typeOrDoc !== null)
      ? getDocFormatExtension(typeOrDoc)
      : String(typeOrDoc || '').trim().toUpperCase();
    if (ext.startsWith('.')) ext = ext.substring(1);
    if (ext === 'PDF') return 'PDF';
    if (ext === 'XLSX' || ext === 'XLS') return 'XLS';
    if (ext === 'DOCX' || ext === 'DOC') return 'DOC';
    if (ext === 'PPTX' || ext === 'PPT') return 'PPT';
    if (ext === 'HTML' || ext === 'HTM') return 'HTML';
    return 'DOC';
  }

  function getBadgeClass(typeOrDoc) {
    var label = typeLabel(typeOrDoc);
    if (label === 'XLS') return 'sd-doc-badge--xls';
    if (label === 'PPT') return 'sd-doc-badge--ppt';
    if (label === 'DOC') return 'sd-doc-badge--doc';
    if (label === 'HTML') return 'sd-doc-badge--html';
    return 'sd-doc-badge--pdf';
  }

  function filenameFromUrl(url) {
    return decodeURIComponent((url || '').split('/').pop()) || 'document';
  }

  function isPdfDoc(doc, filePath) {
    if (!doc) return false;
    var ext = getDocFormatExtension(doc);
    return ext === 'PDF';
  }

  function buildDocCard(doc) {
    var card = document.createElement('div');
    card.className = 'sd-doc-card';
    if (doc.id) {
      card.id = 'doc-' + doc.id;
      card.setAttribute('data-doc-id', doc.id);
    }

    var badge = document.createElement('div');
    badge.className = 'sd-doc-badge ' + getBadgeClass(doc);
    badge.textContent = typeLabel(doc);

    var info = document.createElement('div');
    info.className = 'sd-doc-info';

    var filePath = (doc.filePath && typeof doc.filePath === 'string') ? doc.filePath.trim() : '';
    var filename = doc.fileName || (filePath && !filePath.startsWith('/api/') ? filenameFromUrl(filePath) : '');

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

    if (filename) {
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
