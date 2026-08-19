package com.qualitywebsite.config;

import com.qualitywebsite.entity.AdminUser;
import com.qualitywebsite.repository.AdminUserRepository;
import com.qualitywebsite.security.LoginRateLimiterService;
import com.qualitywebsite.service.AdminAuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.session.HttpSessionEventPublisher;

@Configuration
@EnableWebSecurity
@Slf4j
public class SecurityConfig {

    private final AdminUserRepository adminUserRepository;
    private final LoginRateLimiterService loginRateLimiterService;
    private final AdminAuthService adminAuthService;

    public SecurityConfig(AdminUserRepository adminUserRepository,
                          LoginRateLimiterService loginRateLimiterService,
                          @Lazy AdminAuthService adminAuthService) {
        this.adminUserRepository = adminUserRepository;
        this.loginRateLimiterService = loginRateLimiterService;
        this.adminAuthService = adminAuthService;
    }

    @Value("${server.ssl.enabled:${SSL_ENABLED:false}}")
    private boolean sslEnabled;

    @Value("${REMEMBER_ME_KEY:IASTQualityWebsiteRememberMeKey_DEFAULT}")
    private String rememberMeKey;

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    @jakarta.annotation.PostConstruct
    public void validateSecurityConfig() {
        if (("prod".equalsIgnoreCase(activeProfile) || "production".equalsIgnoreCase(activeProfile))
                && ("IASTQualityWebsiteRememberMeKey_DEFAULT".equals(rememberMeKey) || rememberMeKey == null || rememberMeKey.isBlank())) {
            throw new IllegalStateException("Production profile is active but REMEMBER_ME_KEY environment variable is not configured or uses default string. Set REMEMBER_ME_KEY in environment variables.");
        }
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return username -> {
            String cleanUsername = (username != null) ? username.trim() : "";

            if (loginRateLimiterService.isBlocked(cleanUsername)) {
                log.warn("[Security Alert] Login attempt blocked for locked user account: '{}'", cleanUsername);
                throw new LockedException("Account is temporarily locked due to multiple failed login attempts. Please try again in 15 minutes.");
            }

            AdminUser admin = adminUserRepository.findByUsername(cleanUsername)
                    .orElseGet(() -> adminUserRepository.findByUsernameIgnoreCase(cleanUsername)
                            .orElseThrow(() -> {
                                log.warn("[Security Audit] Authentication lookup failed: Username '{}' not found in database.", cleanUsername);
                                return new UsernameNotFoundException("User not found: " + cleanUsername);
                            }));

            return User.builder()
                    .username(admin.getUsername())
                    .password(admin.getPassword())
                    .disabled(!admin.isEnabled())
                    .roles("ADMIN")
                    .build();
        };
    }

    @Bean
    public AuthenticationSuccessHandler authenticationSuccessHandler() {
        return (request, response, authentication) -> {
            String username = authentication.getName();
            loginRateLimiterService.loginSucceeded(username);
            adminAuthService.updateLastLogin(username);
            log.info("[Security Audit] Administrator '{}' logged in successfully.", username);
            response.sendRedirect("/admin/dashboard");
        };
    }

    @Bean
    public AuthenticationFailureHandler authenticationFailureHandler() {
        return (request, response, exception) -> {
            String username = request.getParameter("username");
            if (username != null && !username.isBlank()) {
                loginRateLimiterService.loginFailed(username.trim());
                log.warn("[Security Audit] Failed login attempt for username: {}", username.trim());
            }
            response.sendRedirect("/admin/login?error=true");
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .ignoringRequestMatchers("/h2-console/**", "/api/feedback", "/data/**", "/api/public/**")
            )
            .cors(Customizer.withDefaults())

            .headers(headers -> headers
                .cacheControl(Customizer.withDefaults())
                .frameOptions(frame -> frame.sameOrigin())
                .contentTypeOptions(Customizer.withDefaults())
                .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                .httpStrictTransportSecurity(hsts -> {
                    if (sslEnabled) {
                        hsts.includeSubDomains(true).maxAgeInSeconds(31536000);
                    } else {
                        hsts.disable();
                    }
                })
            );

        if (sslEnabled) {
            http.requiresChannel(channel -> channel.anyRequest().requiresSecure());
        }

        http
            .sessionManagement(session -> session
                .sessionFixation(fixation -> fixation.changeSessionId())
                .maximumSessions(2)
                .maxSessionsPreventsLogin(false)
                .sessionRegistry(sessionRegistry())
                .expiredUrl("/admin/login?evicted=true")
            )

            .authorizeHttpRequests(auth -> auth
                .requestMatchers(
                    "/",
                    "/index.html",
                    "/search.html",
                    "/quality-checks.html",
                    "/section-details.html",
                    "/generic-template.html",
                    "/lessons-learned.html",
                    "/master-list.html",
                    "/favicon.ico",
                    "/css/**",
                    "/js/**",
                    "/images/**",
                    "/documents/**",
                    "/uploaded-documents/**",
                    "/data/**",
                    "/api/feedback",
                    "/api/public/**",
                    "/admin/login",
                    "/admin/forgot-password",
                    "/admin/reset-password",
                    "/admin/css/**",
                    "/admin/js/**",
                    "/h2-console/**"
                ).permitAll()
                .requestMatchers("/admin/**", "/api/admin/**").hasRole("ADMIN")
                .anyRequest().permitAll()
            )
            .formLogin(form -> form
                .loginPage("/admin/login")
                .loginProcessingUrl("/admin/login")
                .successHandler(authenticationSuccessHandler())
                .failureHandler(authenticationFailureHandler())
                .permitAll()
            )
            .rememberMe(remember -> remember
                .key(rememberMeKey)
                .rememberMeParameter("remember-me")
                .tokenValiditySeconds(7 * 24 * 60 * 60) // 7 days
                .userDetailsService(userDetailsService())
            )
            .logout(logout -> logout
                .logoutUrl("/admin/logout")
                .logoutSuccessUrl("/admin/login?logout=true")
                .invalidateHttpSession(true)
                .deleteCookies("JSESSIONID", "remember-me")
                .permitAll()
            );

        return http.build();
    }
}
