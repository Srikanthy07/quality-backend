package com.qualitywebsite.security;

import com.qualitywebsite.entity.AdminUser;
import com.qualitywebsite.repository.AdminUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import javax.net.ssl.SSLContext;
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

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
    "http.port=0",
    "spring.datasource.url=jdbc:h2:mem:real_tomcat_testdb;DB_CLOSE_DELAY=-1;MODE=MySQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class RealTomcatSessionTimeoutTest {

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
    @DisplayName("Real Tomcat Authentication & Dashboard Access")
    void testRealTomcatAuthenticationAndDashboardAccess() throws Exception {
        CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient client = createTestHttpClient(cookieManager);

        // 1. GET /admin/login to obtain CSRF token & initial JSESSIONID
        HttpRequest getLoginReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/admin/login"))
                .GET()
                .build();
        HttpResponse<String> getLoginResp = client.send(getLoginReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, getLoginResp.statusCode(), "Login page is accessible");
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
        assertEquals(302, postLoginResp.statusCode(), "Normal login succeeds with 302 redirect");
        assertTrue(postLoginResp.headers().firstValue("Location").orElse("").contains("/admin/dashboard"));

        String jsessionId = getJsessionId(cookieManager);
        assertNotNull(jsessionId, "JSESSIONID cookie must be created on login");

        // 3. GET /admin/dashboard -> 200 OK
        HttpRequest getDashReq = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/admin/dashboard"))
                .GET()
                .build();
        HttpResponse<String> getDashResp = client.send(getDashReq, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, getDashResp.statusCode(), "Protected dashboard works when session is active");
    }

    @Test
    @DisplayName("Real Tomcat Multi-Session & Eviction Behavior")
    void testRealTomcatTwoSessionsAndEvictionBehavior() throws Exception {
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

        // Verify both Browser A & B can access dashboard (Two active sessions work)
        HttpRequest getDashA = HttpRequest.newBuilder().uri(URI.create(baseUrl + "/admin/dashboard")).GET().build();
        HttpRequest getDashB = HttpRequest.newBuilder().uri(URI.create(baseUrl + "/admin/dashboard")).GET().build();

        assertEquals(200, clientA.send(getDashA, HttpResponse.BodyHandlers.ofString()).statusCode());
        assertEquals(200, clientB.send(getDashB, HttpResponse.BodyHandlers.ofString()).statusCode());

        // 3. Browser C Login (3rd session -> evicts Browser A)
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

        clientC.send(postLoginReqC, HttpResponse.BodyHandlers.ofString());

        // Browser A (oldest) should now be evicted
        HttpResponse<String> respAEvicted = clientA.send(getDashA, HttpResponse.BodyHandlers.ofString());
        assertEquals(302, respAEvicted.statusCode(), "Browser A (oldest) should be evicted by 3rd login");
        assertTrue(respAEvicted.headers().firstValue("Location").orElse("").contains("/admin/login"), "Eviction redirect location must be login page");
    }
}
