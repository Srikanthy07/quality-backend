/**
 * IAST Quality Portal - script.js v7
 * Handles: navbar scroll/mobile, fixed-navbar hash scrolling, scroll animations,
 * active section links, gate tooltips, counters, and ASPICE PRM modal.
 */

/* ═══════════════════════════════════════════
   Navbar handling is consolidated in navigation.js
═══════════════════════════════════════════ */


/* ═══════════════════════════════════════════
   Smooth section jumps with fixed navbar offset
═══════════════════════════════════════════ */
(function () {
  const getNavbarOffset = () => {
    const navbar = document.querySelector('.navbar');
    return navbar ? navbar.offsetHeight + 18 : 0;
  };

  const scrollToTarget = (hash, updateUrl) => {
    if (!hash || hash === '#') return false;

    const target = document.getElementById(decodeURIComponent(hash.slice(1)));
    if (!target) return false;

    const top = target.getBoundingClientRect().top + window.scrollY - getNavbarOffset();
    window.scrollTo({ top, behavior: 'smooth' });

    if (updateUrl) {
      history.pushState(null, '', hash);
    }

    return true;
  };

  document.querySelectorAll('a[href*="#"]').forEach(link => {
    link.addEventListener('click', event => {
      const url = new URL(link.getAttribute('href'), window.location.href);

      if (url.pathname !== window.location.pathname) return;

      if (scrollToTarget(url.hash, true)) {
        event.preventDefault();
      }
    });
  });

  if (window.location.hash) {
    window.setTimeout(() => scrollToTarget(window.location.hash, false), 80);
  }
})();

/* ═══════════════════════════════════════════
   Intersection Observer: fade-up animations
═══════════════════════════════════════════ */
(function () {
  const els = document.querySelectorAll('.fade-up');
  if (!els.length) return;

  const observer = new IntersectionObserver(entries => {
    entries.forEach(entry => {
      if (entry.isIntersecting) {
        entry.target.classList.add('visible');
        observer.unobserve(entry.target);
      }
    });
  }, { threshold: 0.12 });

  els.forEach(el => observer.observe(el));
})();

/* ═══════════════════════════════════════════
   Active nav link based on current section & page
═══════════════════════════════════════════ */
(function () {
  const path = window.location.pathname;
  const search = window.location.search;
  const links = Array.from(document.querySelectorAll('.navbar__link, .navbar__dropdown-item'));
  if (!links.length) return;

  const normalizePath = pathname => {
    if (!pathname || pathname === '' || pathname === '/') return '/';
    if (pathname.endsWith('/index.html')) return '/';
    return pathname;
  };

  /* Clear all section & link highlights */
  const clearActive = () => {
    links.forEach(link => {
      link.classList.remove('active');
      const dropdown = link.closest('.navbar__dropdown');
      if (dropdown) {
        const toggle = dropdown.querySelector('.navbar__dropdown-toggle');
        if (toggle) toggle.classList.remove('active');
      }
    });
  };

  const currentNormalized = normalizePath(path);

  // Initial highlight on page load for non-home pages or specific page matching
  clearActive();

  if (currentNormalized !== '/') {
    links.forEach(link => {
      const href = link.getAttribute('href') || '';
      if (!href || href === 'javascript:void(0)') return;
      
      const url = new URL(href, window.location.href);
      const linkNormalized = normalizePath(url.pathname);

      if (linkNormalized === currentNormalized && linkNormalized !== '/') {
        if (url.search) {
          if (url.search === search) {
            link.classList.add('active');
            const dropdown = link.closest('.navbar__dropdown');
            if (dropdown) {
              const toggle = dropdown.querySelector('.navbar__dropdown-toggle');
              if (toggle) toggle.classList.add('active');
            }
          }
        } else {
          link.classList.add('active');
          const dropdown = link.closest('.navbar__dropdown');
          if (dropdown) {
            const toggle = dropdown.querySelector('.navbar__dropdown-toggle');
            if (toggle) toggle.classList.add('active');
          }
        }
      }
    });
  }

  // Section tracking on home page (`/`)
  if (currentNormalized === '/') {
    const sections = Array.from(document.querySelectorAll('section'));
    const getNavbarOffset = () => {
      const navbar = document.querySelector('.navbar');
      return navbar ? navbar.offsetHeight + 18 : 0;
    };

    const getLinkForSection = (section) => {
      if (!section.id) return null;
      return links.find(link => {
        const href = link.getAttribute('href') || '';
        if (!href.includes('#')) return false;
        const url = new URL(href, window.location.href);
        return url.hash.slice(1) === section.id;
      });
    };

    let currentActiveLink = null;

    const updateActiveLink = () => {
      const scrollPos = window.scrollY;
      const navOffset = getNavbarOffset();

      if (scrollPos < 80) {
        if (currentActiveLink !== null) {
          clearActive();
          currentActiveLink = null;
        }
        return;
      }

      let activeSection = null;
      const targetPos = scrollPos + navOffset + 24;

      for (let i = 0; i < sections.length; i++) {
        const section = sections[i];
        if (section.offsetTop <= targetPos) {
          activeSection = section;
        }
      }

      const targetLink = activeSection ? getLinkForSection(activeSection) : null;

      if (targetLink !== currentActiveLink) {
        clearActive();
        currentActiveLink = targetLink;
        if (targetLink) {
          targetLink.classList.add('active');
          const dropdown = targetLink.closest('.navbar__dropdown');
          if (dropdown) {
            const toggle = dropdown.querySelector('.navbar__dropdown-toggle');
            if (toggle) toggle.classList.add('active');
          }
        }
      }
    };

    if (sections.length > 0) {
      window.addEventListener('scroll', updateActiveLink, { passive: true });
      window.addEventListener('DOMContentLoaded', updateActiveLink);
      updateActiveLink();
    }
  }
})();

/* ═══════════════════════════════════════════
   Gate node tooltip on hover
═══════════════════════════════════════════ */
(function () {
  const nodes = document.querySelectorAll('.gate-node__diamond');
  nodes.forEach(node => {
    node.addEventListener('mouseenter', () => {
      node.style.zIndex = '10';
    });
    node.addEventListener('mouseleave', () => {
      node.style.zIndex = '';
    });
  });
})();

/* ═══════════════════════════════════════════
   Smooth counter animation for hero stats
═══════════════════════════════════════════ */
(function () {
  const counters = document.querySelectorAll('[data-count]');
  if (!counters.length) return;

  const animateCounter = el => {
    const target = parseInt(el.getAttribute('data-count'), 10);
    const suffix = el.getAttribute('data-suffix') || '';
    const duration = 1800;
    const step = 16;
    const increment = target / (duration / step);
    let current = 0;

    const timer = setInterval(() => {
      current += increment;
      if (current >= target) {
        current = target;
        clearInterval(timer);
      }
      el.textContent = Math.floor(current) + suffix;
    }, step);
  };

  const observer = new IntersectionObserver(entries => {
    entries.forEach(entry => {
      if (entry.isIntersecting) {
        animateCounter(entry.target);
        observer.unobserve(entry.target);
      }
    });
  }, { threshold: 0.5 });

  counters.forEach(el => observer.observe(el));
})();


/* ═══════════════════════════════════════════
   Scroll Progress Bar
═══════════════════════════════════════════ */
(function () {
  const bar = document.getElementById('scroll-progress-bar');
  if (!bar) return;

  window.addEventListener('scroll', () => {
    const scrollTop = window.scrollY;
    const docHeight = document.documentElement.scrollHeight - window.innerHeight;
    const scrolled  = docHeight > 0 ? (scrollTop / docHeight) * 100 : 0;
    bar.style.width = scrolled + '%';
  });
})();


/* ═══════════════════════════════════════════
   Hero Particles Background
═══════════════════════════════════════════ */
(async function () {
  const container = document.getElementById('particles');
  if (!container || !window.tsParticles) return;

  try {
    await window.tsParticles.load('particles', {
      fpsLimit: 60,
      particles: {
        number: { value: 45, density: { enable: true, value_area: 800 } },
        color: { value: '#00aabb' },
        shape: { type: 'circle' },
        opacity: {
          value: 0.4,
          animation: { enable: false }
        },
        size: {
          value: { min: 1.5, max: 3.5 },
          animation: { enable: true, speed: 2, minimumValue: 1.5 }
        },
        move: {
          enable: true,
          speed: { min: 0.3, max: 1.2 },
          direction: 'none',
          random: true,
          straight: false,
          outMode: 'out'
        },
        links: {
          enable: true,
          distance: 120,
          color: '#00aabb',
          opacity: 0.25,
          width: 1.2,
          triangles: { enable: false, frequency: 5 }
        }
      },
      interactivity: {
        events: {
          onHover: { enable: true, mode: 'grab' },
          onClick: { enable: false }
        },
        modes: {
          grab: {
            distance: 180,
            line_linked: { opacity: 0.5 },
            particle:    { opacity: 0.8 }
          }
        }
      },
      background: { color: 'transparent' },
      detectRetina: true
    });
  } catch (error) {
    // Gracefully skip particles if not available
  }
})();