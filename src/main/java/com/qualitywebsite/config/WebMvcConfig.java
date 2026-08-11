package com.qualitywebsite.config;

import com.qualitywebsite.interceptor.WebAnalyticsInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final WebAnalyticsInterceptor webAnalyticsInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(webAnalyticsInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/admin/**",
                        "/api/admin/**",
                        "/css/**",
                        "/js/**",
                        "/images/**",
                        "/favicon.ico",
                        "/h2-console/**"
                );
    }
}
