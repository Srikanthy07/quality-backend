package com.qualitywebsite.interceptor;

import com.qualitywebsite.service.WebsiteAnalyticsService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebAnalyticsInterceptor implements HandlerInterceptor {

    private final WebsiteAnalyticsService websiteAnalyticsService;
    private final Environment environment;

    private static final String COOKIE_VISITOR_ID = "vid";
    private static final String COOKIE_SESSION_ID = "sid";
    private static final int COOKIE_MAX_AGE_ONE_YEAR = 365 * 24 * 60 * 60;
    private static final int COOKIE_MAX_AGE_SESSION = 30 * 60; // 30 minutes inactivity timeout

    private static final Set<String> ALLOWED_HTML_PAGES = Set.of(
            "/",
            "/index.html",
            "/search.html",
            "/quality-checks.html",
            "/section-details.html",
            "/generic-template.html",
            "/lessons-learned.html",
            "/master-list.html"
    );

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        try {
            // 1. Exclude test traffic
            if (isTestEnvironmentOrRequest(request)) {
                return true;
            }

            // 2. Only track GET requests
            if (!"GET".equalsIgnoreCase(request.getMethod())) {
                return true;
            }

            String uri = request.getRequestURI();

            // 3. Exclude static assets, API calls, admin routes, and non-HTML endpoints
            if (shouldIgnoreUri(uri)) {
                return true;
            }

            // 4. Extract or generate Visitor ID (vid)
            String visitorId = getCookieValue(request, COOKIE_VISITOR_ID);
            boolean isNewVisitorCookie = false;
            if (visitorId == null || visitorId.isBlank()) {
                visitorId = UUID.randomUUID().toString();
                isNewVisitorCookie = true;
                setCookie(response, COOKIE_VISITOR_ID, visitorId, COOKIE_MAX_AGE_ONE_YEAR);
            }

            // 5. Extract or generate Session ID (sid)
            String sessionId = getCookieValue(request, COOKIE_SESSION_ID);
            boolean isNewSession = false;
            if (sessionId == null || sessionId.isBlank()) {
                sessionId = UUID.randomUUID().toString();
                isNewSession = true;
                setCookie(response, COOKIE_SESSION_ID, sessionId, COOKIE_MAX_AGE_SESSION);
            } else {
                // Refresh session cookie expiry (30 min inactivity timeout)
                setCookie(response, COOKIE_SESSION_ID, sessionId, COOKIE_MAX_AGE_SESSION);
            }

            // 6. Parse User-Agent & Headers
            String userAgent = request.getHeader("User-Agent");
            String referrer = request.getHeader("Referer");

            String browser = parseBrowser(userAgent);
            String os = parseOperatingSystem(userAgent);
            String deviceType = parseDeviceType(userAgent);

            String pageTitle = derivePageTitle(uri);

            // 7. Log visit asynchronously
            websiteAnalyticsService.logVisit(
                    visitorId, sessionId, uri, pageTitle, browser, os, deviceType, referrer, isNewSession, isNewVisitorCookie
            );

        } catch (Exception e) {
            log.error("[Analytics Interceptor] Error logging visit: {}", e.getMessage());
        }
        return true;
    }

    private boolean isTestEnvironmentOrRequest(HttpServletRequest request) {
        // Check active Spring profiles
        String[] profiles = environment.getActiveProfiles();
        for (String p : profiles) {
            if ("test".equalsIgnoreCase(p)) {
                return true;
            }
        }

        // Check system property flag
        if ("false".equalsIgnoreCase(System.getProperty("analytics.enabled"))) {
            return true;
        }

        // Check header markers or MockMvc / test User-Agents
        String ua = request.getHeader("User-Agent");
        if (ua == null || ua.contains("MockMvc") || ua.contains("JUnit") || ua.contains("TestClient")) {
            return true;
        }

        if (request.getHeader("X-Test-Execution") != null || request.getHeader("X-Test-Request") != null) {
            return true;
        }

        return false;
    }

    private boolean shouldIgnoreUri(String uri) {
        if (uri == null) return true;
        String lower = uri.toLowerCase();

        // Explicitly exclude non-public, admin, API, static assets, and downloads
        if (lower.startsWith("/admin") ||
            lower.startsWith("/api/") ||
            lower.startsWith("/css/") ||
            lower.startsWith("/js/") ||
            lower.startsWith("/images/") ||
            lower.startsWith("/documents/") ||
            lower.startsWith("/uploaded-documents/") ||
            lower.startsWith("/data/") ||
            lower.startsWith("/h2-console") ||
            lower.equals("/favicon.ico") ||
            lower.endsWith(".css") ||
            lower.endsWith(".js") ||
            lower.endsWith(".png") ||
            lower.endsWith(".jpg") ||
            lower.endsWith(".jpeg") ||
            lower.endsWith(".gif") ||
            lower.endsWith(".ico") ||
            lower.endsWith(".svg") ||
            lower.endsWith(".woff") ||
            lower.endsWith(".woff2") ||
            lower.endsWith(".json") ||
            lower.endsWith(".pdf") ||
            lower.endsWith(".xlsx") ||
            lower.endsWith(".docx")) {
            return true;
        }

        // Only allow recognized public HTML pages
        return !ALLOWED_HTML_PAGES.contains(lower);
    }

    private String getCookieValue(HttpServletRequest request, String cookieName) {
        if (request.getCookies() == null) return null;
        return Arrays.stream(request.getCookies())
                .filter(c -> cookieName.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    private void setCookie(HttpServletResponse response, String name, String value, int maxAge) {
        Cookie cookie = new Cookie(name, value);
        cookie.setPath("/");
        cookie.setMaxAge(maxAge);
        cookie.setHttpOnly(false);
        response.addCookie(cookie);
    }

    private String parseBrowser(String ua) {
        if (ua == null) return "Unknown";
        String lower = ua.toLowerCase();
        if (lower.contains("edg/") || lower.contains("edge/")) return "Microsoft Edge";
        if (lower.contains("chrome/") && !lower.contains("chromium/")) return "Chrome";
        if (lower.contains("firefox/")) return "Firefox";
        if (lower.contains("safari/") && !lower.contains("chrome/")) return "Safari";
        if (lower.contains("opr/") || lower.contains("opera/")) return "Opera";
        if (lower.contains("trident/") || lower.contains("msie ")) return "Internet Explorer";
        return "Browser";
    }

    private String parseOperatingSystem(String ua) {
        if (ua == null) return "Unknown";
        String lower = ua.toLowerCase();
        if (lower.contains("windows")) return "Windows";
        if (lower.contains("android")) return "Android";
        if (lower.contains("iphone") || lower.contains("ipad") || lower.contains("ipod")) return "iOS";
        if (lower.contains("mac os x") || lower.contains("macintosh")) return "macOS";
        if (lower.contains("linux")) return "Linux";
        return "OS";
    }

    private String parseDeviceType(String ua) {
        if (ua == null) return "Desktop";
        String lower = ua.toLowerCase();
        if (lower.contains("ipad") || lower.contains("tablet") || (lower.contains("android") && !lower.contains("mobile"))) return "Tablet";
        if (lower.contains("mobile") || lower.contains("iphone") || lower.contains("ipod") || lower.contains("android")) return "Mobile";
        return "Desktop";
    }

    private String derivePageTitle(String uri) {
        if (uri == null || uri.equals("/") || uri.equals("/index.html")) return "Home Page";
        if (uri.contains("master-list")) return "Master List";
        if (uri.contains("generic-template")) return "Generic Templates";
        if (uri.contains("lessons-learned")) return "Lessons Learned";
        if (uri.contains("quality-checks")) return "Quality Checklists";
        if (uri.contains("section-details")) return "Process Details";
        if (uri.contains("search")) return "Global Search";
        return uri;
    }
}
