package com.qualitywebsite.config;

import org.springframework.context.annotation.Configuration;

@Configuration
public class MailConfig {
    // Spring Boot auto-configures JavaMailSender from the spring.mail.*
    // properties in application.properties.
    // No manual bean definition is required.
    // The only thing needed is a valid spring.mail.password in application.properties.
}