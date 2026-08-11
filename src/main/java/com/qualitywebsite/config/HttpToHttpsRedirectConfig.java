package com.qualitywebsite.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.catalina.connector.Connector;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;

@Configuration
@Slf4j
public class HttpToHttpsRedirectConfig {

    @Value("${server.port:8093}")
    private int serverPort;

    @Value("${server.ssl.enabled:${SSL_ENABLED:false}}")
    private boolean sslEnabled;

    @Value("${http.port:8081}")
    private int httpPort;

    private final Environment environment;

    public HttpToHttpsRedirectConfig(Environment environment) {
        this.environment = environment;
    }

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> servletContainer() {
        return tomcat -> tomcat.addAdditionalTomcatConnectors(httpConnector());
    }

    private Connector httpConnector() {
        Connector connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
        connector.setScheme("http");
        connector.setPort(httpPort);
        connector.setSecure(false);
        connector.setRedirectPort(serverPort);
        return connector;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logServerConfiguration() {
        String[] activeProfiles = environment.getActiveProfiles();
        String profilesStr = activeProfiles.length > 0 ? String.join(", ", activeProfiles) : "default";

        log.info("================ SERVER CONFIGURATION ================");
        log.info("server.port          : {}", serverPort);
        log.info("server.ssl.enabled   : {}", sslEnabled);
        log.info("Active Profiles      : {}", profilesStr);
        log.info("Configured Connectors: {} ({}), {} (http)", 
                 serverPort, sslEnabled ? "https" : "http", httpPort);
        log.info("HTTP Redirect Port   : {} -> {}", httpPort, serverPort);
        log.info("======================================================");
    }
}