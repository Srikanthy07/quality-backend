/**
 * flow-scale.js  v3
 * Scales .im-process-wrapper proportionally to its container width.
 * Minimum scale: 0.55 (flow never shrinks below 55% — remains readable).
 * Below that threshold, flow scrolls horizontally instead.
 */
(function () {
  'use strict';

  // Must match .im-process-wrapper { width: Xpx } in responsive-flow.css
  var DESIGN_WIDTH = 1000;
  // Minimum scale before switching to horizontal scroll
  var MIN_SCALE = 0.55;

  function wrapAll() {
    document.querySelectorAll('.im-process-wrapper').forEach(function (wrapper) {
      if (!wrapper.parentElement.classList.contains('im-scale-outer')) {
        var outer = document.createElement('div');
        outer.className = 'im-scale-outer';
        wrapper.parentNode.insertBefore(outer, wrapper);
        outer.appendChild(wrapper);
      }
    });
  }

  function scaleAll() {
    document.querySelectorAll('.im-process-wrapper').forEach(function (wrapper) {
      var outer = wrapper.parentElement;

      // Reset so we can measure available space
      wrapper.style.transform  = '';
      wrapper.style.width      = DESIGN_WIDTH + 'px';
      wrapper.style.marginLeft = '';
      outer.style.height       = '';
      outer.style.overflowX    = '';

      // Available width = parent container (the .im-block or section container)
      var available = outer.offsetWidth || outer.parentElement.offsetWidth || window.innerWidth;
      var raw = available / DESIGN_WIDTH;
      var scale = Math.min(1, Math.max(MIN_SCALE, raw));

      if (raw < MIN_SCALE) {
        // Too small to scale — let it scroll instead
        wrapper.style.transform  = 'none';
        wrapper.style.width      = DESIGN_WIDTH + 'px';
        wrapper.style.marginLeft = '';
        outer.style.overflowX    = 'auto';
        outer.style.overflowY    = 'hidden';
        outer.style.height       = '';
        // Add thin scrollbar
        outer.style.scrollbarWidth = 'thin';
      } else {
        // Apply scale
        wrapper.style.transform       = 'scale(' + scale + ')';
        wrapper.style.transformOrigin = 'top left';
        outer.style.overflowX         = 'hidden';
        outer.style.overflowY         = 'hidden';

        // Shrink outer height to match scaled height
        var naturalH = wrapper.offsetHeight;
        outer.style.height = (naturalH * scale) + 'px';

        // Centre the scaled wrapper horizontally
        // scaledWidth = DESIGN_WIDTH * scale; leftover = available - scaledWidth
        var leftover = available - (DESIGN_WIDTH * scale);
        wrapper.style.marginLeft = (leftover > 0 ? leftover / 2 : 0) + 'px';
      }
    });
  }

  // Debounced resize
  var resizeTimer;
  window.addEventListener('resize', function () {
    clearTimeout(resizeTimer);
    resizeTimer = setTimeout(scaleAll, 80);
  });

  function init() {
    wrapAll();
    scaleAll();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }

  // Re-run after everything paints (fonts affect card height)
  window.addEventListener('load', function () {
    setTimeout(scaleAll, 150);
  });

})();