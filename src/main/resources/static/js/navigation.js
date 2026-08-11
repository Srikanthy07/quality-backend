/**
 * IAST Quality Portal - navigation.js
 * Enterprise Navbar: active page highlighting, smooth dropdowns (desktop hover + mobile tap),
 * keyboard accessibility (ARIA & Arrow navigation), sticky scroll shadow, and search bar handling.
 */
(function () {
  'use strict';

  var MOBILE_BREAKPOINT = 1024;

  function isMobile() {
    return window.innerWidth <= MOBILE_BREAKPOINT;
  }

  function normalizePath(pathname) {
    if (!pathname || pathname === '/index.html') return '/';
    return pathname;
  }

  /* ── 1. Sticky Navbar Scroll Shadow ── */
  var navbar = document.querySelector('.navbar');
  if (navbar) {
    var handleScroll = function () {
      navbar.classList.toggle('scrolled', window.scrollY > 20);
    };
    window.addEventListener('scroll', handleScroll, { passive: true });
    handleScroll();
  }

  /* ── 2. Navigation Elements & State ── */
  var toggle = document.querySelector('.navbar__toggle');
  var nav = document.querySelector('.navbar__nav');
  var dropdowns = Array.from(document.querySelectorAll('.navbar__dropdown'));
  var allLinks = Array.from(document.querySelectorAll('.navbar__link, .navbar__dropdown-item'));

  function resetHamburger() {
    if (!toggle) return;
    toggle.setAttribute('aria-expanded', 'false');
    var spans = toggle.querySelectorAll('span');
    spans.forEach(function (span) {
      span.style.transform = '';
      span.style.opacity = '';
    });
  }

  function animateHamburger(open) {
    if (!toggle) return;
    var spans = toggle.querySelectorAll('span');
    toggle.setAttribute('aria-expanded', String(open));
    if (open && spans.length === 3) {
      spans[0].style.transform = 'translateY(7px) rotate(45deg)';
      spans[1].style.opacity = '0';
      spans[2].style.transform = 'translateY(-7px) rotate(-45deg)';
    } else {
      resetHamburger();
    }
  }

  function closeAllDropdowns() {
    dropdowns.forEach(function (dropdown) {
      var menu = dropdown.querySelector('.navbar__dropdown-menu');
      var btn = dropdown.querySelector('.navbar__dropdown-toggle');
      var icon = dropdown.querySelector('.navbar__dropdown-icon');
      if (menu) menu.classList.remove('open');
      if (btn) btn.setAttribute('aria-expanded', 'false');
      if (icon) icon.style.transform = '';
    });
  }

  function closeMobileNav() {
    if (!nav || !nav.classList.contains('open')) return;
    nav.classList.remove('open');
    animateHamburger(false);
    closeAllDropdowns();
  }

  /* ── 3. Dropdown Toggle & Hover Management ── */
  dropdowns.forEach(function (dropdown) {
    var btn = dropdown.querySelector('.navbar__dropdown-toggle');
    var menu = dropdown.querySelector('.navbar__dropdown-menu');
    var icon = dropdown.querySelector('.navbar__dropdown-icon');
    var leaveTimer = null;

    if (!btn || !menu) return;

    function openMenu() {
      clearTimeout(leaveTimer);
      // Close any other open dropdown
      dropdowns.forEach(function (other) {
        if (other !== dropdown) {
          var otherMenu = other.querySelector('.navbar__dropdown-menu');
          var otherBtn = other.querySelector('.navbar__dropdown-toggle');
          var otherIcon = other.querySelector('.navbar__dropdown-icon');
          if (otherMenu) otherMenu.classList.remove('open');
          if (otherBtn) otherBtn.setAttribute('aria-expanded', 'false');
          if (otherIcon) otherIcon.style.transform = '';
        }
      });

      menu.classList.add('open');
      btn.setAttribute('aria-expanded', 'true');
      if (icon) icon.style.transform = 'rotate(180deg)';
    }

    function closeMenuImmediate() {
      clearTimeout(leaveTimer);
      menu.classList.remove('open');
      btn.setAttribute('aria-expanded', 'false');
      if (icon) icon.style.transform = '';
    }

    function toggleMenu() {
      if (menu.classList.contains('open')) {
        closeMenuImmediate();
      } else {
        openMenu();
      }
    }

    // --- Desktop Hover Events with Buffer ---
    dropdown.addEventListener('mouseenter', function () {
      if (!isMobile()) {
        openMenu();
      }
    });

    dropdown.addEventListener('mouseleave', function () {
      if (!isMobile()) {
        leaveTimer = setTimeout(function () {
          closeMenuImmediate();
        }, 140); // 140ms hover buffer to prevent flicker
      }
    });

    // --- Click Event (Desktop & Mobile) ---
    btn.addEventListener('click', function (e) {
      e.preventDefault();
      e.stopPropagation();
      toggleMenu();
    });

    // --- Keyboard Navigation (Enter, Space, ArrowDown) ---
    btn.addEventListener('keydown', function (e) {
      if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        toggleMenu();
      } else if (e.key === 'ArrowDown') {
        e.preventDefault();
        openMenu();
        var firstItem = menu.querySelector('.navbar__dropdown-item');
        if (firstItem) firstItem.focus();
      }
    });

    // --- Submenu Item Arrow Key & Escape Navigation ---
    var items = Array.from(menu.querySelectorAll('.navbar__dropdown-item'));
    items.forEach(function (item, index) {
      item.addEventListener('keydown', function (e) {
        if (e.key === 'ArrowDown') {
          e.preventDefault();
          var next = items[index + 1] || items[0];
          if (next) next.focus();
        } else if (e.key === 'ArrowUp') {
          e.preventDefault();
          var prev = items[index - 1] || items[items.length - 1];
          if (prev) prev.focus();
        } else if (e.key === 'Escape') {
          closeMenuImmediate();
          btn.focus();
        }
      });
    });
  });

  // Click outside navbar to close all dropdowns and mobile menu
  document.addEventListener('click', function (e) {
    if (navbar && !navbar.contains(e.target)) {
      closeAllDropdowns();
    }
  });

  // Mobile Hamburger Toggle
  if (toggle && nav) {
    toggle.addEventListener('click', function (e) {
      e.stopPropagation();
      var open = nav.classList.toggle('open');
      animateHamburger(open);
      if (!open) closeAllDropdowns();
    });
  }

  // Close Mobile Menu or Dropdown on Link Selection
  allLinks.forEach(function (link) {
    link.addEventListener('click', function () {
      if (isMobile()) {
        closeMobileNav();
      } else {
        closeAllDropdowns();
      }
    });
  });

  // Global Escape Key Listener
  document.addEventListener('keydown', function (e) {
    if (e.key === 'Escape') {
      closeAllDropdowns();
      closeMobileNav();
    }
  });

  // Window Resize Listener
  window.addEventListener('resize', function () {
    if (!isMobile()) {
      closeMobileNav();
    }
  }, { passive: true });

  /* ── 4. Automatic Active Navigation Highlighting ── */
  function clearAllActive() {
    allLinks.forEach(function (l) { l.classList.remove('active'); });
    document.querySelectorAll('.navbar__dropdown-toggle.active').forEach(function (btn) {
      btn.classList.remove('active');
    });
  }

  function setParentDropdownActive(link) {
    var dropdown = link.closest('.navbar__dropdown');
    if (!dropdown) return;
    var parentToggle = dropdown.querySelector('.navbar__dropdown-toggle');
    if (parentToggle) parentToggle.classList.add('active');
  }

  function highlightLink(link) {
    if (!link) return;
    link.classList.add('active');
    setParentDropdownActive(link);
  }

  function updateActiveStates() {
    clearAllActive();

    var currentPath = normalizePath(window.location.pathname);
    var currentHash = window.location.hash;

    // Search Page: keep search input visible, do not highlight unrelated menus
    if (currentPath === '/search.html') {
      return;
    }

    var bestMatch = null;

    allLinks.forEach(function (link) {
      var href = link.getAttribute('href') || '';
      if (!href || href === 'javascript:void(0)') return;

      try {
        var url = new URL(href, window.location.origin);
        var linkPath = normalizePath(url.pathname);
        var linkHash = url.hash;

        // Page match for /generic-template.html, /lessons-learned.html, /master-list.html
        if (linkPath !== '/' && linkPath === currentPath) {
          bestMatch = link;
        }

        // Section details page
        if (currentPath.indexOf('section-details') !== -1 && linkPath.indexOf('section-details') !== -1) {
          bestMatch = link;
        }

        // Quality checks page
        if (currentPath.indexOf('quality-checks') !== -1 && linkPath.indexOf('quality-checks') !== -1) {
          bestMatch = link;
        }

        // Hash match on Home Page (e.g. /#vision, /#culture, /#gates, /#prm)
        if (currentPath === '/' && linkPath === '/' && linkHash && linkHash === currentHash) {
          bestMatch = link;
        }
      } catch (err) {}
    });

    // Default to Home link on Home page when no section hash is selected
    if (!bestMatch && currentPath === '/' && (!currentHash || currentHash === '#home')) {
      bestMatch = allLinks.find(function (l) {
        var h = l.getAttribute('href') || '';
        return h === '/' || h === '/index.html' || h === 'index.html';
      });
    }

    if (bestMatch) {
      highlightLink(bestMatch);
    }
  }

  updateActiveStates();

  window.addEventListener('hashchange', updateActiveStates);

  // Scroll-based section active state on Home page
  var sections = Array.from(document.querySelectorAll('section[id]'));
  if (sections.length && normalizePath(window.location.pathname) === '/') {
    var updateScrollActive = function () {
      if (normalizePath(window.location.pathname) !== '/') return;

      var navHeight = navbar ? navbar.offsetHeight : 68;
      var targetPos = window.scrollY + navHeight + 40;
      var activeSection = null;

      sections.forEach(function (sec) {
        if (sec.offsetTop <= targetPos) activeSection = sec;
      });

      if (activeSection && activeSection.id) {
        var targetHash = '#' + activeSection.id;
        var matchingLink = allLinks.find(function (l) {
          var h = l.getAttribute('href') || '';
          return h.indexOf(targetHash) !== -1;
        });

        if (matchingLink) {
          clearAllActive();
          highlightLink(matchingLink);
        } else if (window.scrollY < 100) {
          clearAllActive();
          var homeLink = allLinks.find(function (l) {
            var h = l.getAttribute('href') || '';
            return h === '/' || h === '/index.html';
          });
          if (homeLink) highlightLink(homeLink);
        }
      }
    };

    window.addEventListener('scroll', updateScrollActive, { passive: true });
  }

})();
