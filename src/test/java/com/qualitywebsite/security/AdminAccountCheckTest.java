package com.qualitywebsite.security;

import com.qualitywebsite.entity.AdminUser;
import com.qualitywebsite.repository.AdminUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=${TEST_DB_URL:${DB_URL:jdbc:mysql://localhost:3306/quality_website?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8&useUnicode=true}}",
    "spring.datasource.username=${TEST_DB_USERNAME:${DB_USERNAME:root}}",
    "spring.datasource.password=${TEST_DB_PASSWORD:${DB_PASSWORD:1234}}"
})
class AdminAccountCheckTest {

    @Autowired
    private AdminUserRepository adminUserRepository;

    @Test
    void testAdminRepositoryExists() {
        assertNotNull(adminUserRepository);
    }
}
