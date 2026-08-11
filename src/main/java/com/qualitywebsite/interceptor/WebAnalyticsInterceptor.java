package com.qualitywebsite.interceptor;

import com.qualitywebsite.service.WebsiteAnalyticsService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebAnalyticsInterceptor implements HandlerInterceptor {

    private final WebsiteAnalyticsService websiteAnalyticsService;

    private static final String COOKIE_VISITOR_ID = "vid";
    private static final String COOKIE_SESSION_ID = "sid";
    private static final int COOKIE_MAX_AGE_ONE_YEAR = 365 * 24 * 60 * 60;
    private static final int COOKIE_MAX_AGE_SESSION = 30 * 60; // 30 minutes

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        try {
            String uri = request.getRequestURI();

            // Exclude non-public pages, admin console, API calls, and static resources
            if (shouldIgnoreUri(uri)) {
                return true;
            }

            // Only track GET requests for web pages
            if (!"GET".equalsIgnoreCase(request.getMethod())) {
                return true;
            }

            // Extract or generate Visitor ID (vid)
            String visitorId = getCookieValue(request, COOKIE_VISITOR_ID);
            boolean isNewVisitorCookie = false;
            if (visitorId == null || visitorId.isBlank()) {
                visitorId = UUID.randomUUID().toString();
                isNewVisitorCookie = true;
                setCookie(response, COOKIE_VISITOR_ID, visitorId, COOKIE_MAX_AGE_ONE_YEAR);
            }

            // Extract or generate Session ID (sid)
            String sessionId = getCookieValue(request, COOKIE_SESSION_ID);
            boolean isNewSession = false;
            if (sessionId == null || sessionId.isBlank()) {
                sessionId = UUID.randomUUID().toString();
                isNewSession = true;
                setCookie(response, COOKIE_SESSION_ID, sessionId, COOKIE_MAX_AGE_SESSION);
            } else {
                // Refresh session cookie expiry (30 min from now)
                setCookie(response, COOKIE_SESSION_ID, sessionId, COOKIE_MAX_AGE_SESSION);
            }

            // Parse User-Agent header
            String userAgent = request.getHeader("User-Agent");
            String referrer = request.getHeader("Referer");

            String browser = parseBrowser(userAgent);
            String os = parseOperatingSystem(userAgent);
            String deviceType = parseDeviceType(userAgent);

            String pageTitle = derivePageTitle(uri);

            // Log visit asynchronously
            websiteAnalyticsService.logVisit(
                    visitorId, sessionId, uri, pageTitle, browser, os, deviceType, referrer, isNewSession, isNewVisitorCookie
            );

        } catch (Exception e) {
            log.error("[Analytics Interceptor] Error logging visit: {}", e.getMessage());
        }
        return true;
    }

    private boolean shouldIgnoreUri(String uri) {
        if (uri == null) return true;
        String lower = uri.toLowerCase();
        return lower.startsWith("/admin") ||
               lower.startsWith("/api/admin") ||
               lower.startsWith("/css/") ||
               lower.startsWith("/js/") ||
               lower.startsWith("/images/") ||
               lower.startsWith("/h2-console") ||
               lower.equals("/favicon.ico") ||
               lower.endsWith(".css") ||
               lower.endsWith(".js") ||
               lower.endsWith(".png") ||
               lower.endsWith(".jpg") ||
               lower.endsWith(".ico") ||
               lower.endsWith(".svg") ||
               lower.endsWith(".woff") ||
               lower.endsWith(".woff2");
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
        cookie.setHttpOnly(false); // Accessible to client if needed
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
