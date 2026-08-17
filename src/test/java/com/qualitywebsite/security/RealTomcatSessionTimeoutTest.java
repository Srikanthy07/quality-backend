package com.qualitywebsite.security;

import com.qualitywebsite.entity.AdminUser;
import com.qualitywebsite.repository.AdminUserRepository;
import jakarta.servlet.http.HttpSessionEvent;
import jakarta.servlet.http.HttpSessionListener;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(RealTomcatSessionTimeoutTest.TestTimeoutConfig.class)
@TestPropertySource(properties = {
    "http.port=0",
    "server.servlet.session.timeout=3s",
    "spring.datasource.url=jdbc:h2:mem:real_tomcat_testdb;DB_CLOSE_DELAY=-1;MODE=MySQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class RealTomcatSessionTimeoutTest {

    @TestConfiguration
    static class TestTimeoutConfig {
        @Bean
        public HttpSessionListener testSessionTimeoutListener() {
            return new HttpSessionListener() {
                @Override
                public void sessionCreated(HttpSessionEvent se) {
                    // Force 3-second HTTP session inactivity timeout for real Tomcat testing
                    se.getSession().setMaxInactiveInterval(3);
                }
            };
        }
    }

    @LocalServerPort
    private int port;

    @Autowired
    private Environment environment;

    @Autowired
    private AdminUserRepository adminUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private LoginRateLimiterService loginRateLimiterService;

    @Autowired
    private SessionRegistry sessionRegistry;

    private String baseUrl;

    private boolean isSslEnabled() {
        return Boolean.parseBoolean(environment.getProperty("server.ssl.enabled", "false"));
    }

    @BeforeEach
    void setUp() {
        String scheme = isSslEnabled() ? "https" : "http";
        baseUrl = scheme + "://localhost:" + port;
        loginRateLimiterService.loginSucceeded("admin");
        adminUserRepository.deleteAll();

        AdminUser admin = AdminUser.builder()
                .username("admin")
                .email("admin@iast.com")
                .password(passwordEncoder.encode("Admin#Pass2026!"))
                .enabled(true)
                .build();
        adminUserRepository.save(admin);
    }

    private HttpClient createTestHttpClient(CookieManager cookieManager) throws Exception {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .cookieHandler(cookieManager)
                .followRedirects(HttpClient.Redirect.NEVER);

        if (isSslEnabled()) {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new javax.net.ssl.TrustManager[]{ new javax.net.ssl.X509ExtendedTrustManager() {
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                public void checkClientTrusted(X509Certificate[] certs, String authType, java.net.Socket socket) {}
                public void checkServerTrusted(X509Certificate[] certs, String authType, java.net.Socket socket) {}
                public void checkClientTrusted(X509Certificate[] certs, String authType, javax.net.ssl.SSLEngine engine) {}
                public void checkServerTrusted(X509Certificate[] certs, String authType, javax.net.ssl.SSLEngine engine) {}
            }}, new java.security.SecureRandom());
            builder.sslContext(sslContext);

            javax.net.ssl.SSLParameters sslParams = new javax.net.ssl.SSLParameters();
            sslParams.setEndpointIdentificationAlgorithm(null);
            builder.sslParameters(sslParams);
        }

        return builder.build();
    }

    private String extractCsrfToken(String html) {
        if (html == null) return "";
        int idx = html.indexOf("name=\"_csrf\"");
        if (idx != -1) {
            int valStart = html.indexOf("value=\"", idx) + 7;
            int valEnd = html.indexOf("\"", valStart);
            return html.substring(valStart, valEnd);
        }
        return "";
    }

    private String getJsessionId(CookieManager cookieManager) {
        List<HttpCookie> cookies = cookieManager.getCookieStore().getCookies();
        for (HttpCookie c : cookies) {
            if ("JSESSIONID".equalsIgnoreCase(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }

    @Test
    void testRealTomcatSessionTimeoutAndReLogin() throws Exception {
        CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient client = createTestHttpClient(cookieManager);

        // 1. GET /admin/login to obtain CSRF token & initial JSESSIONID
        HttpRequest getLoginReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/admin/login"))
                .GET()
                .build();
        HttpResponse<String> getLoginResp = client.send(getLoginReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, getLoginResp.statusCode(), "TEST 5: Login page is accessible");
        String csrfToken = extractCsrfToken(getLoginResp.body());

        // 2. Perform POST /admin/login
        String formBody = "username=admin&password=" + java.net.URLEncoder.encode("Admin#Pass2026!", StandardCharsets.UTF_8)
                + "&_csrf=" + java.net.URLEncoder.encode(csrfToken, StandardCharsets.UTF_8);

        HttpRequest postLoginReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/admin/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formBody))
                .build();

        HttpResponse<String> postLoginResp = client.send(postLoginReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(302, postLoginResp.statusCode(), "TEST 1: Normal login succeeds with 302 redirect");
        assertTrue(postLoginResp.headers().firstValue("Location").orElse("").contains("/admin/dashboard"));

        String originalJsessionId = getJsessionId(cookieManager);
        assertNotNull(originalJsessionId, "JSESSIONID cookie must be created on login");

        // 3. GET /admin/dashboard -> 200 OK
        HttpRequest getDashReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/admin/dashboard"))
                .GET()
                .build();
        HttpResponse<String> getDashResp = client.send(getDashReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, getDashResp.statusCode(), "TEST 2: Protected dashboard works when session is active");

        // 4. Wait 5 seconds so Tomcat's 3-second session timeout expires the session
        System.out.println("Sleeping 5 seconds to trigger real Tomcat session timeout...");
        Thread.sleep(5000);

        // 5. GET /admin/dashboard -> Should be redirected to /admin/login?expired=true because session is expired
        HttpResponse<String> getDashAfterTimeoutResp = client.send(getDashReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(302, getDashAfterTimeoutResp.statusCode(), "TEST 3 & 4: Expired session cannot access /admin/dashboard and is redirected to login page");
        assertTrue(getDashAfterTimeoutResp.headers().firstValue("Location").orElse("").contains("/admin/login"), "Redirect location must be login page");

        // 6. GET /admin/login?expired=true to get fresh login page and new CSRF token
        HttpRequest getExpiredLoginReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/admin/login?expired=true"))
                .GET()
                .build();
        HttpResponse<String> getExpiredLoginResp = client.send(getExpiredLoginReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, getExpiredLoginResp.statusCode());
        String freshCsrfToken = extractCsrfToken(getExpiredLoginResp.body());

        // 7. Perform re-login (POST /admin/login)
        String reLoginFormBody = "username=admin&password=" + java.net.URLEncoder.encode("Admin#Pass2026!", StandardCharsets.UTF_8)
                + "&_csrf=" + java.net.URLEncoder.encode(freshCsrfToken, StandardCharsets.UTF_8);

        HttpRequest postReLoginReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/admin/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(reLoginFormBody))
                .build();

        HttpResponse<String> postReLoginResp = client.send(postReLoginReq, HttpResponse.BodyHandlers.ofString());

        assertEquals(302, postReLoginResp.statusCode(), "TEST 6: Fresh username/password login succeeds after real timeout");
        assertTrue(postReLoginResp.headers().firstValue("Location").orElse("").contains("/admin/dashboard"),
                "Re-login MUST redirect to /admin/dashboard");

        String newJsessionId = getJsessionId(cookieManager);
        assertNotNull(newJsessionId);
        assertNotEquals(originalJsessionId, newJsessionId, "TEST 7: New JSESSIONID is created on re-login");

        // 8. Verify the new session can access /admin/dashboard
        HttpResponse<String> getDashFinalResp = client.send(getDashReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, getDashFinalResp.statusCode(), "TEST 8: New session can access dashboard");
    }

    @Test
    void testRealTomcatTwoSessionsAndTimeoutBehavior() throws Exception {
        CookieManager cmA = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient clientA = createTestHttpClient(cmA);

        CookieManager cmB = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient clientB = createTestHttpClient(cmB);

        // 1. Browser A Login
        HttpRequest getLoginReqA = HttpRequest.newBuilder().uri(URI.create(baseUrl + "/admin/login")).GET().build();
        HttpResponse<String> getLoginRespA = clientA.send(getLoginReqA, HttpResponse.BodyHandlers.ofString());
        String csrfA = extractCsrfToken(getLoginRespA.body());

        String bodyA = "username=admin&password=" + java.net.URLEncoder.encode("Admin#Pass2026!", StandardCharsets.UTF_8)
                + "&_csrf=" + java.net.URLEncoder.encode(csrfA, StandardCharsets.UTF_8);

        HttpRequest postLoginReqA = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/admin/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(bodyA))
                .build();

        clientA.send(postLoginReqA, HttpResponse.BodyHandlers.ofString());

        // 2. Browser B Login
        HttpRequest getLoginReqB = HttpRequest.newBuilder().uri(URI.create(baseUrl + "/admin/login")).GET().build();
        HttpResponse<String> getLoginRespB = clientB.send(getLoginReqB, HttpResponse.BodyHandlers.ofString());
        String csrfB = extractCsrfToken(getLoginRespB.body());

        String bodyB = "username=admin&password=" + java.net.URLEncoder.encode("Admin#Pass2026!", StandardCharsets.UTF_8)
                + "&_csrf=" + java.net.URLEncoder.encode(csrfB, StandardCharsets.UTF_8);

        HttpRequest postLoginReqB = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/admin/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(bodyB))
                .build();

        clientB.send(postLoginReqB, HttpResponse.BodyHandlers.ofString());

        // Verify both Browser A & B can access dashboard (TEST 10: Two active sessions work)
        HttpRequest getDashA = HttpRequest.newBuilder().uri(URI.create(baseUrl + "/admin/dashboard")).GET().build();
        HttpRequest getDashB = HttpRequest.newBuilder().uri(URI.create(baseUrl + "/admin/dashboard")).GET().build();

        assertEquals(200, clientA.send(getDashA, HttpResponse.BodyHandlers.ofString()).statusCode());
        assertEquals(200, clientB.send(getDashB, HttpResponse.BodyHandlers.ofString()).statusCode());

        // 3. Allow Browser A session to expire naturally (5 seconds) while keeping Browser B active with a ping
        System.out.println("Sleeping 5 seconds to expire Browser A session while keeping Browser B active...");
        Thread.sleep(2000);
        clientB.send(getDashB, HttpResponse.BodyHandlers.ofString()); // Ping to keep Browser B active
        Thread.sleep(3000);

        // Browser A should now be redirected to login page on next request (TEST 11: One session expires)
        HttpResponse<String> respAAfterTimeout = clientA.send(getDashA, HttpResponse.BodyHandlers.ofString());
        assertEquals(302, respAAfterTimeout.statusCode(), "Browser A session should be expired");

        // 4. Re-login from Browser A
        HttpResponse<String> getExpiredLoginA = clientA.send(getLoginReqA, HttpResponse.BodyHandlers.ofString());
        String freshCsrfA = extractCsrfToken(getExpiredLoginA.body());

        String reBodyA = "username=admin&password=" + java.net.URLEncoder.encode("Admin#Pass2026!", StandardCharsets.UTF_8)
                + "&_csrf=" + java.net.URLEncoder.encode(freshCsrfA, StandardCharsets.UTF_8);

        HttpRequest postReLoginA = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/admin/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(reBodyA))
                .build();

        HttpResponse<String> postReLoginRespA = clientA.send(postReLoginA, HttpResponse.BodyHandlers.ofString());
        assertEquals(302, postReLoginRespA.statusCode(), "Re-login from Browser A should succeed");

        // Keep Browser B active first, then Browser A2 active, so Browser B is the oldest active session
        clientB.send(getDashB, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, clientA.send(getDashA, HttpResponse.BodyHandlers.ofString()).statusCode());

        // 5. Check 3rd Login Enforces Max 2 Limit (TEST 12: Maximum 2 sessions is still enforced)
        CookieManager cmC = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient clientC = createTestHttpClient(cmC);

        HttpRequest getLoginReqC = HttpRequest.newBuilder().uri(URI.create(baseUrl + "/admin/login")).GET().build();
        HttpResponse<String> getLoginRespC = clientC.send(getLoginReqC, HttpResponse.BodyHandlers.ofString());
        String csrfC = extractCsrfToken(getLoginRespC.body());

        String bodyC = "username=admin&password=" + java.net.URLEncoder.encode("Admin#Pass2026!", StandardCharsets.UTF_8)
                + "&_csrf=" + java.net.URLEncoder.encode(csrfC, StandardCharsets.UTF_8);

        HttpRequest postLoginReqC = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/admin/login"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(bodyC))
                .build();

        HttpResponse<String> respBEvicted = clientB.send(getDashB, HttpResponse.BodyHandlers.ofString());
        assertEquals(302, respBEvicted.statusCode(), "Browser B (oldest) should be expired by 3rd login");
        assertTrue(respBEvicted.headers().firstValue("Location").orElse("").contains("/admin/login"), "Browser B eviction redirect location must be login page");
    }
}
