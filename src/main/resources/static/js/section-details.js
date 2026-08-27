(function () {
  'use strict';

  /* ═══════════════════════════════════════════════════════════
     1. DATA — identical source-of-truth as script.js PRM_DATA
  ═══════════════════════════════════════════════════════════ */
  var PRM_DATA = {
    'SUP.1':  { title:'Quality Assurance', group:'Supporting Process Group', color:'#2e7d32',
      purpose:'Ensure work products and processes comply with predefined provisions; resolve and prevent non-compliances.',
      outcomes:['QA strategy is developed and maintained','Process and product compliance is verified','Non-conformities are identified and tracked','Quality records are maintained','Process improvements are recommended'] },
    'SUP.8':  { title:'Configuration Management', group:'Supporting Process Group', color:'#2e7d32',
      purpose:'Establish and maintain the integrity of all work products and make them available to concerned parties.',
      outcomes:['Configuration items are identified and baselined','Changes are controlled and tracked','Configuration status is recorded and reported','Configuration audits are performed','Releases are managed systematically'] },
    'SUP.9':  { title:'Problem Resolution Management', group:'Supporting Process Group', color:'#2e7d32',
      purpose:'Ensure that problems are identified, analyzed, managed and controlled to resolution.',
      outcomes:['Problems are recorded and classified','Root cause analysis is performed','Corrective actions are defined and tracked','Problem trends are analyzed','Lessons learned are documented'] },
    'SUP.10': { title:'Change Request Management', group:'Supporting Process Group', color:'#2e7d32',
      purpose:'Ensure that change requests are managed, tracked, implemented, and verified throughout the lifecycle.',
      outcomes:['Change requests are recorded and classified','Impact analysis is performed','CRs are approved or rejected with rationale','Implementation is tracked and verified','Change history is maintained'] },
    'SUP.11': { title:'ML Data Management', group:'Supporting Process Group', color:'#2e7d32',
      purpose:'Ensure ML data assets are identified, collected, validated, and maintained throughout the ML lifecycle.',
      outcomes:['Data requirements are defined','Collection and labelling processes are established','Data quality and integrity is verified','Data versioning and lineage is maintained','Data governance policies are applied'] },
    'SYS.1':  { title:'Requirements Elicitation', group:'System Engineering Process Group', color:'#1565c0',
      purpose:'Gather, process, and track evolving stakeholder needs and requirements throughout the product lifecycle.',
      outcomes:['Stakeholder requirements are identified','Requirements are documented and agreed','Traceability to stakeholder needs is established','Requirements changes are managed','Agreement with stakeholders is confirmed'] },
    'SYS.2':  { title:'System Requirements Analysis', group:'System Engineering Process Group', color:'#1565c0',
      purpose:'Transform stakeholder requirements into a set of system technical requirements describing what the system needs to do.',
      outcomes:['System requirements are defined and analyzed','Consistency and feasibility are verified','Traceability to stakeholder requirements is established','System requirements are baselined','Requirements changes are tracked'] },
    'SYS.3':  { title:'System Architectural Design', group:'System Engineering Process Group', color:'#1565c0',
      purpose:'Establish a system architectural design and identify which requirements are allocated to which elements.',
      outcomes:['System architecture is defined','Architectural alternatives are evaluated','Requirements are allocated to architecture elements','Architecture is documented and baselined','Interface definitions are established'] },
    'SYS.4':  { title:'System Integration & Verification', group:'System Engineering Process Group', color:'#1565c0',
      purpose:'Integrate system elements and verify that the integrated system satisfies architectural requirements.',
      outcomes:['Integration strategy is defined','System elements are integrated incrementally','Integration tests are executed and recorded','Defects are tracked and resolved','Integration is verified against architecture'] },
    'SYS.5':  { title:'System Verification', group:'System Engineering Process Group', color:'#1565c0',
      purpose:'Confirm by examination and objective evidence that the system satisfies specified system requirements.',
      outcomes:['Verification strategy is defined','System verification is planned and performed','Verification results are recorded and evaluated','Non-conformances are identified and corrected','System verification report is produced'] },
    'VAL.1':  { title:'Validation', group:'Validation Process Group', color:'#4527a0',
      purpose:'Confirm that the system can accomplish its intended use in the target operational environment.',
      outcomes:['Validation strategy is established','Validation environment is prepared','Validation activities are performed','Validation results are evaluated','Validation report is produced and approved'] },
    'MAN.3':  { title:'Project Management', group:'Management Process Group', color:'#4527a0',
      purpose:'Establish and maintain plans that define, execute, and control project activities in accordance with defined objectives.',
      outcomes:['Project scope is defined','Project plans are created and maintained','Resources are allocated and tracked','Project status is monitored and reported','Issues and risks are managed'] },
    'MAN.5':  { title:'Risk Management', group:'Management Process Group', color:'#4527a0',
      purpose:'Identify, analyze, treat, and monitor project risks continuously throughout the project lifecycle.',
      outcomes:['Risk management strategy is defined','Risks are identified and analyzed','Risk treatments are planned and implemented','Risk status is monitored and reported','Risk register is maintained up to date'] },
    'MAN.6':  { title:'Measurement', group:'Management Process Group', color:'#4527a0',
      purpose:'Collect, analyze, and report data relating to process and product performance to support decision making.',
      outcomes:['Measurement needs are identified','Measurement plan is established','Data collection and storage is implemented','Data is analyzed and interpreted','Results are communicated to stakeholders'] },
    'PIM.3':  { title:'Process Improvement', group:'Process Improvement Group', color:'#4527a0',
      purpose:'Continually improve the effectiveness and efficiency of processes used by the organization.',
      outcomes:['Improvement needs are identified','Improvement goals are established','Process changes are defined and piloted','Improvements are deployed organization-wide','Process performance is re-evaluated'] },
    'REU.2':  { title:'Management of Products for Reuse', group:'Reuse Process Group', color:'#4527a0',
      purpose:'Manage reusable products so they can be effectively used by projects within the organization.',
      outcomes:['Reuse strategy is defined','Reusable products are identified and catalogued','Reuse feasibility is evaluated','Reusable assets are maintained and released','Reuse metrics are tracked'] },
    'SWE.1':  { title:'SW Requirements Analysis', group:'Software Engineering Process Group', color:'#e65100',
      purpose:'Establish software requirements from system requirements allocated to software and define interface requirements.',
      outcomes:['SW requirements are derived from system requirements','SW requirements are documented and agreed','Interface requirements are defined','Requirements consistency is verified','SW requirements are baselined'] },
    'SWE.2':  { title:'SW Architectural Design', group:'Software Engineering Process Group', color:'#e65100',
      purpose:'Establish an architectural design for software that identifies software components and their interfaces.',
      outcomes:['SW architecture is defined','Architectural alternatives are evaluated','SW requirements are allocated to components','Interfaces between components are defined','Architecture is documented and reviewed'] },
    'SWE.3':  { title:'SW Detailed Design & Unit Construction', group:'Software Engineering Process Group', color:'#e65100',
      purpose:'Provide an evaluated detailed design for software components and produce software units.',
      outcomes:['Detailed design is created for each component','Design is reviewed and approved','Software units are constructed from design','Unit construction guidelines are followed','Code review is performed on each unit'] },
    'SWE.4':  { title:'Software Unit Verification', group:'Software Engineering Process Group', color:'#e65100',
      purpose:'Verify software units to ensure that they satisfy their requirements and are free of defects.',
      outcomes:['Unit verification strategy is defined','Unit tests are designed and executed','Structural coverage is measured','Defects are identified and corrected','Unit verification results are documented'] },
    'SWE.5':  { title:'SW Component Verification & Integration', group:'Software Engineering Process Group', color:'#e65100',
      purpose:'Integrate software components and verify that the integrated software satisfies architectural requirements.',
      outcomes:['Integration order is planned','Components are integrated incrementally','Integration tests are designed and executed','Interface compliance is verified','Integration results are documented'] },
    'SWE.6':  { title:'Software Verification', group:'Software Engineering Process Group', color:'#e65100',
      purpose:'Confirm that the integrated software satisfies the specified software requirements.',
      outcomes:['SW verification strategy is defined','Verification criteria are established','SW verification tests are executed','Results are evaluated against criteria','SW verification report is produced'] },
    'HWE.1':  { title:'HW Requirements Analysis', group:'Hardware Engineering Process Group', color:'#b71c1c',
      purpose:'Establish hardware requirements from system requirements allocated to hardware.',
      outcomes:['HW requirements are derived','HW interface requirements are defined','Requirements consistency is verified','HW requirements are baselined','Traceability to system requirements is established'] },
    'HWE.2':  { title:'HW Design', group:'Hardware Engineering Process Group', color:'#b71c1c',
      purpose:'Develop a hardware design that satisfies hardware requirements.',
      outcomes:['HW design is developed','Design alternatives are evaluated','Requirements are allocated to design elements','Design is reviewed and approved','Design documentation is maintained'] },
    'HWE.3':  { title:'Verification against HW Design', group:'Hardware Engineering Process Group', color:'#b71c1c',
      purpose:'Confirm by examination and objective evidence that hardware satisfies its design.',
      outcomes:['Verification strategy is defined','Test cases are derived from design','Verification is performed and results recorded','Non-conformances are identified and resolved','Verification report is produced'] },
    'HWE.4':  { title:'Verification against HW Requirements', group:'Hardware Engineering Process Group', color:'#b71c1c',
      purpose:'Confirm by examination and objective evidence that hardware satisfies specified requirements.',
      outcomes:['Test cases are derived from HW requirements','Verification tests are executed','Structural coverage is evaluated','Results are recorded and analyzed','Verification report is approved'] },
    'MLE.1':  { title:'ML Requirements Analysis', group:'ML Engineering Process Group', color:'#e65100',
      purpose:'Establish ML system requirements, define data requirements, and specify model performance criteria.',
      outcomes:['ML requirements are defined','Data requirements are specified','Model performance criteria are established','ML-specific risks are identified','Requirements are agreed with stakeholders'] },
    'MLE.2':  { title:'ML Architecture', group:'ML Engineering Process Group', color:'#e65100',
      purpose:'Design the ML system architecture, define model types, and specify data pipelines and training infrastructure.',
      outcomes:['ML architecture is designed','Model type and approach is selected','Data pipeline architecture is defined','Architecture is reviewed and documented','Training infrastructure is planned'] },
    'MLE.3':  { title:'ML Training', group:'ML Engineering Process Group', color:'#e65100',
      purpose:'Train and evaluate ML models to satisfy defined ML requirements and performance criteria.',
      outcomes:['Training strategy is defined','Models are trained with versioned data','Training experiments are tracked','Model performance is evaluated','Trained model is documented and stored'] },
    'MLE.4':  { title:'ML Model Testing', group:'ML Engineering Process Group', color:'#e65100',
      purpose:'Verify that the trained ML model satisfies ML requirements in the intended operational environment.',
      outcomes:['Model test strategy is defined','Test data is prepared and validated','Model is tested against requirements','Edge cases and failure modes are evaluated','Model test report is produced'] },
    'ACQ.4':  { title:'Supplier Monitoring', group:'Acquisition Process Group', color:'#4527a0',
      purpose:'Track and assess the performance of suppliers against agreed requirements and plans.',
      outcomes:['Supplier performance criteria are defined','Monitoring plan is established','Supplier deliverables are reviewed','Supplier performance is measured and reported','Corrective actions are requested when needed'] },
    'SPL.2':  { title:'Product Release', group:'Supply Process Group', color:'#4527a0',
      purpose:'Control the release of a product to the customer, ensuring all release criteria are satisfied.',
      outcomes:['Release criteria are defined and agreed','Release package is assembled and verified','Release notes are prepared','Customer acceptance is obtained','Product release is documented and archived'] }
  };

  /* ═══════════════════════════════════════════════════════════
     2. DYNAMIC DOCUMENT REGISTRY
  ═══════════════════════════════════════════════════════════ */
  var loadedDocuments = [];

  /* ═══════════════════════════════════════════════════════════
     3. UTILITY FUNCTIONS
  ═══════════════════════════════════════════════════════════ */

  /** File types that cannot render inline — must be force-downloaded */
  var FORCE_DOWNLOAD_TYPES = ['DOCX','DOC','XLSX','XLS','PPTX','PPT','ZIP','RAR','7Z'];

  function mustForceDownload(type) {
    return FORCE_DOWNLOAD_TYPES.indexOf(String(type || '').toUpperCase()) !== -1;
  }

  /**
   * Extract actual file extension case-insensitively.
   * Primary Source of Truth: doc.fileName
   * Secondary Fallback: doc.fileType / doc.type
   */
  function getDocFormatExtension(doc) {
    if (!doc) return '';

    // 1. Primary Source of Truth: doc.fileName / doc.filename
    var fname = (doc.fileName || doc.filename || '').trim();
    if (fname && fname.indexOf('.') !== -1) {
      var ext = fname.split('.').pop().trim().toUpperCase();
      if (ext && ext.indexOf('/') === -1 && ext.indexOf('\\') === -1) {
        return ext;
      }
    }

    // 2. Secondary Fallback: doc.fileType / doc.type
    var ftype = (doc.fileType || doc.type || '').trim().toUpperCase();
    if (ftype) {
      if (ftype.startsWith('.')) ftype = ftype.substring(1);
      return ftype;
    }

    return '';
  }

  /**
   * Format mapping:
   * PDF / .pdf   -> PDF
   * XLSX / .xlsx -> XLS
   * XLS / .xls   -> XLS
   * DOCX / .docx -> DOC
   * DOC / .doc   -> DOC
   * PPTX / .pptx -> PPT
   * PPT / .ppt   -> PPT
   */
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

  function resolveUrl(doc) {
    var path = doc.filePath || doc.url || doc.file || '';
    if (!path) return '';
    if (/^(https?:)?\/\//.test(path) || path.charAt(0) === '/') return path;
    return path;
  }

  /** Read ?section= from current URL */
  function getSectionId() {
    var params = new URLSearchParams(window.location.search);
    return (params.get('section') || '').trim().toUpperCase();
  }

  /** Read ?doc= from current URL */
  function getDocParam() {
    var params = new URLSearchParams(window.location.search);
    return (params.get('doc') || '').trim();
  }

  /* ═══════════════════════════════════════════════════════════
     4. BUILD DOM HELPERS
  ═══════════════════════════════════════════════════════════ */

  function el(tag, attrs, children) {
    var node = document.createElement(tag);
    if (attrs) {
      Object.keys(attrs).forEach(function(k) {
        if (k === 'className') { node.className = attrs[k]; }
        else if (k === 'textContent') { node.textContent = attrs[k]; }
        else if (k === 'innerHTML') { node.innerHTML = attrs[k]; }
        else { node.setAttribute(k, attrs[k]); }
      });
    }
    (children || []).forEach(function(c) { if (c) node.appendChild(c); });
    return node;
  }

  function buildSectionCard(iconClass, iconColor, label, bodyFn) {
    var icon  = el('div', { className: 'sd-card__hdr-icon ' + iconColor }, [
      el('i', { className: iconClass })
    ]);
    var lbl   = el('div', { className: 'sd-card__label', textContent: label });
    var hdr   = el('div', { className: 'sd-card__hdr' }, [icon, lbl]);
    var body  = el('div', { className: 'sd-card__body' });
    bodyFn(body);
    return el('div', { className: 'sd-card' }, [hdr, body]);
  }

  function buildPurposeCard(purpose) {
    return buildSectionCard('fas fa-bullseye', 'sd-card__hdr-icon--teal', 'Process Purpose', function(body) {
      body.appendChild(el('p', { className: 'sd-purpose', textContent: purpose }));
    });
  }

  function buildOutcomesCard(outcomes) {
    return buildSectionCard('fas fa-list-check', 'sd-card__hdr-icon--navy', 'Key Outcomes', function(body) {
      var ul = el('ul', { className: 'sd-outcomes' });
      (outcomes || []).forEach(function(o) {
        ul.appendChild(el('li', { textContent: o }));
      });
      body.appendChild(ul);
    });
  }

  function isPdfDoc(doc, filePath) {
    if (!doc) return false;
    var ext = getDocFormatExtension(doc);
    return ext === 'PDF';
  }

  function buildDocCard(doc) {
    if (!doc) return el('div', { textContent: 'Invalid Document Data' });

    var filePath = (doc.filePath && typeof doc.filePath === 'string') ? doc.filePath.trim() : '';
    var filename = doc.fileName || (filePath && !filePath.startsWith('/api/') ? filenameFromUrl(filePath) : '');

    var cardAttrs = { className: 'sd-doc-card' };
    if (doc.id) {
      cardAttrs.id = 'doc-' + doc.id;
      cardAttrs['data-doc-id'] = doc.id;
    }

    var badge = el('div', { className: 'sd-doc-badge ' + getBadgeClass(doc), textContent: typeLabel(doc) });

    var label = el('div', { className: 'sd-doc-label', textContent: doc.documentName || filename || 'document' });
    
    // Version display
    var versionVal = doc.version || '1.0';
    var version = el('div', { className: 'sd-doc-version', textContent: 'Version: ' + versionVal });

    var desc  = doc.description
      ? el('div', { className: 'sd-doc-desc', textContent: doc.description })
      : null;
      
    var fname = filename 
      ? el('span', { className: 'sd-doc-filename', textContent: filename })
      : null;

    var actions = el('div', { className: 'sd-doc-actions' });

    if (filePath) {
      // 1. View Document Button (PDF only)
      if (isPdfDoc(doc, filePath)) {
        var viewBtn = el('a', {
          className: 'sd-doc-btn sd-doc-btn--view',
          href: filePath,
          target: '_blank',
          rel: 'noopener noreferrer',
          textContent: 'View Document'
        });
        actions.appendChild(viewBtn);
      }
      
      // 2. Download Button
      var dlBtn = el('a', {
        className: 'sd-doc-btn sd-doc-btn--dl',
        href: filePath,
        download: filename,
        textContent: 'Download'
      });
      
      actions.appendChild(dlBtn);
    } else {
      // Document unavailable state
      actions.appendChild(el('span', {
        className: 'sd-doc-btn',
        style: 'background:#f1f5f9;color:#94a3b8;cursor:default;border:1.5px dashed #cbd5e1;box-shadow:none;',
        textContent: 'Document unavailable'
      }));
    }

    var info = el('div', { className: 'sd-doc-info' }, [label, version, desc, fname, actions]);
    return el('div', cardAttrs, [badge, info]);
  }

  function buildDocumentsCard(sectionId) {
    return buildSectionCard('fas fa-folder-open', 'sd-card__hdr-icon--green', 'Available Documents', function(body) {
      var docs = loadedDocuments.filter(function(doc) {
        if (!doc) return false;
        // Exclude non-PRM categories — they are shown on their own dedicated pages.
        // Every DocumentEntity now has a category field set, so we cannot use
        // "if (doc.category) return false" (that would reject everything from the API).
        var cat = String(doc.category || '').toLowerCase().trim();
        if (cat === 'assessment checklist' || cat === 'generic templates' || cat === 'lessons learned') return false;
        return (String(doc.process).toUpperCase() === String(sectionId).toUpperCase() ||
                String(doc.process).toLowerCase() === 'global');
      });

      if (docs.length === 0) {
        var empty = el('div', { className: 'sd-docs-empty' });
        empty.appendChild(el('i', { className: 'fas fa-folder-open' }));
        empty.appendChild(el('p', { textContent: 'TBD - To Be Discussed.' }));
        body.appendChild(empty);
        return;
      }

      var grid = el('div', { className: 'sd-docs-grid' });
      docs.forEach(function(doc) { grid.appendChild(buildDocCard(doc)); });
      body.appendChild(grid);
    });
  }

  function buildAssessmentChecklistCard(sectionId) {
    var docs = loadedDocuments.filter(function(doc) {
      if (!doc) return false;
      // DataInitializationService.determineCategory() stores "Assessment Checklist" in the DB.
      // The old static JSON had "aspice_assessment_checklist" / "supporting" — those never matched.
      var cat = String(doc.category || '').toLowerCase().trim();
      var isChecklist = cat === 'assessment checklist';
      return isChecklist && String(doc.process).toUpperCase() === String(sectionId).toUpperCase();
    });

    if (docs.length === 0) {
      return null;
    }

    var card = buildSectionCard('fas fa-clipboard-check', 'sd-card__hdr-icon--checklist', 'ASPICE Assessment Checklist', function(body) {
      var list = el('div', { className: 'sd-chk-list' });

      // Table Header Row
      var hdrSno  = el('div', { className: 'sd-chk-sno', textContent: 'S.No' });
      var hdrName = el('div', { className: 'sd-chk-name', textContent: 'Document Name' });
      var hdrVer  = el('div', { className: 'sd-chk-version', textContent: 'Version' });
      var hdrAct  = el('div', { className: 'sd-chk-action', textContent: 'Action' });
      list.appendChild(el('div', { className: 'sd-chk-hdr-row' }, [hdrSno, hdrName, hdrVer, hdrAct]));

      docs.forEach(function(doc, idx) {
        var filePath = (doc.filePath && typeof doc.filePath === 'string') ? doc.filePath.trim() : '';
        var filename = filePath ? filenameFromUrl(filePath) : '';
        var rawVer = String(doc.version || '1.0').trim();
        var versionVal = rawVer.toLowerCase().startsWith('v') ? rawVer : 'v' + rawVer;

        var snoEl = el('div', { className: 'sd-chk-sno', textContent: String(idx + 1) });
        var nameEl = el('div', { className: 'sd-chk-name', textContent: doc.documentName || 'Document' });
        var verEl = el('div', { className: 'sd-chk-version' }, [
          el('span', { className: 'ml-version-badge', textContent: versionVal })
        ]);
        var actionEl = el('div', { className: 'sd-chk-action' });

        if (filePath) {
          var dlBtn = el('a', {
            className: 'sd-doc-btn sd-doc-btn--dl',
            href: filePath,
            download: filename,
            textContent: 'Download'
          });
          actionEl.appendChild(dlBtn);
        } else {
          actionEl.appendChild(el('span', {
            className: 'sd-doc-btn',
            style: 'background:#f1f5f9;color:#94a3b8;cursor:default;border:1.5px dashed #cbd5e1;box-shadow:none;',
            textContent: 'Unavailable'
          }));
        }

        var row = el('div', { className: 'sd-chk-row' }, [snoEl, nameEl, verEl, actionEl]);
        list.appendChild(row);
      });

      body.appendChild(list);
    });

    card.classList.add('sd-card--checklist');
    return card;
  }

  /* ═══════════════════════════════════════════════════════════
     5. PAGE RENDER
  ═══════════════════════════════════════════════════════════ */

  function renderNotFound(id) {
    document.getElementById('sd-hero-title').textContent = 'Section Not Found';
    document.getElementById('sd-hero-badge').textContent = id || '?';
    document.getElementById('sd-hero-group').textContent = '';
    document.title = 'ASPICE PRM – Not Found | IAST Quality Portal';

    var content = document.getElementById('sd-content');
    var wrap    = el('div', { className: 'sd-not-found' });
    wrap.appendChild(el('h2', { textContent: 'Section "' + (id || '') + '" not found' }));
    wrap.appendChild(el('p',  { textContent: 'The ASPICE PRM section you requested does not exist or has not been configured yet.' }));
    var back = el('a', {
      href      : '/#prm',
      className : 'btn-primary',
      textContent: '\u2190 Back to ASPICE PRM'
    });
    wrap.appendChild(back);
    content.appendChild(el('div', { className: 'sd-card' }, [wrap]));
  }

  function renderSection(id, data) {
    /* ── Hero ── */
    var badge = document.getElementById('sd-hero-badge');
    badge.textContent   = id;
    badge.style.background = data.color || '#00aabb';

    document.getElementById('sd-hero-title').textContent = data.title || id;
    document.getElementById('sd-hero-group').textContent = data.group || '';
    document.title = id + ' – ' + (data.title || '') + ' | IAST Quality Portal';

    /* ── Content cards ── */
    var content = document.getElementById('sd-content');
    content.innerHTML = '';
    content.appendChild(buildPurposeCard(data.purpose));
    content.appendChild(buildOutcomesCard(data.outcomes));
    content.appendChild(buildDocumentsCard(id));

    var chkCard = buildAssessmentChecklistCard(id);
    if (chkCard) {
      content.appendChild(chkCard);
    }

    /* ── Highlight requested document card if ?doc=... parameter exists ── */
    var targetDocId = getDocParam();
    if (targetDocId) {
      setTimeout(function() {
        var docCard = document.getElementById('doc-' + targetDocId) ||
                      document.querySelector('[data-doc-id="' + targetDocId + '"]');
        if (!docCard) {
          var allCards = document.querySelectorAll('.sd-doc-card[data-doc-id]');
          for (var i = 0; i < allCards.length; i++) {
            var cid = allCards[i].getAttribute('data-doc-id');
            if (cid && cid.toUpperCase() === targetDocId.toUpperCase()) {
              docCard = allCards[i];
              break;
            }
          }
        }
        if (docCard) {
          docCard.scrollIntoView({ behavior: 'smooth', block: 'center' });
          docCard.classList.add('doc-card--highlighted');
          setTimeout(function() {
            docCard.classList.remove('doc-card--highlighted');
          }, 3500);
        }
      }, 150);
    }
  }

  function renderError(id, data) {
    /* ── Hero ── */
    var badge = document.getElementById('sd-hero-badge');
    badge.textContent   = id;
    badge.style.background = data.color || '#00aabb';

    document.getElementById('sd-hero-title').textContent = data.title || id;
    document.getElementById('sd-hero-group').textContent = data.group || '';
    document.title = id + ' – ' + (data.title || '') + ' | IAST Quality Portal';

    /* ── Content cards ── */
    var content = document.getElementById('sd-content');
    content.innerHTML = '';
    content.appendChild(buildPurposeCard(data.purpose));
    content.appendChild(buildOutcomesCard(data.outcomes));

    var errorCard = buildSectionCard('fas fa-exclamation-triangle', 'sd-card__hdr-icon--teal', 'Available Documents', function(body) {
      var errWrap = el('div', { className: 'sd-docs-empty' });
      errWrap.appendChild(el('i', { className: 'fas fa-exclamation-triangle', style: 'color: #dc2626;' }));
      errWrap.appendChild(el('p', { textContent: 'Failed to load document templates. Please try again later.' }));
      body.appendChild(errWrap);
    });
    content.appendChild(errorCard);
  }

  /* ═══════════════════════════════════════════════════════════
     6. INIT
  ═══════════════════════════════════════════════════════════ */

  function init() {
    var id   = getSectionId();
    var data = id ? PRM_DATA[id] : null;

    if (!id || !data) {
      renderNotFound(id);
      return;
    }

    // Render static structure with loading status first
    var content = document.getElementById('sd-content');
    content.innerHTML = '';
    content.appendChild(buildPurposeCard(data.purpose));
    content.appendChild(buildOutcomesCard(data.outcomes));

    var loadingCard = buildSectionCard('fas fa-folder-open', 'sd-card__hdr-icon--green', 'Available Documents', function(body) {
      var loadWrap = el('div', { className: 'sd-docs-empty' });
      loadWrap.appendChild(el('i', { className: 'fas fa-spinner fa-spin' }));
      loadWrap.appendChild(el('p', { textContent: 'Loading templates...' }));
      body.appendChild(loadWrap);
    });
    content.appendChild(loadingCard);

    // Fetch documents registry
    fetch('/api/public/documents')
      .then(function(res) {
        if (!res.ok) {
          return fetch('data/documents.json').then(function(r) { return r.json(); });
        }
        return res.json();
      })
      .then(function(json) {
        loadedDocuments = json || [];
        renderSection(id, data);
      })
      .catch(function(err) {

        renderError(id, data);
      });
  }

  /* Run after DOM is ready */
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }

})();