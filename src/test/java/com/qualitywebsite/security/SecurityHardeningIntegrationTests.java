package com.qualitywebsite.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=${TEST_DB_URL:${DB_URL:jdbc:mysql://localhost:3306/quality_website?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8&useUnicode=true}}",
    "spring.datasource.username=${TEST_DB_USERNAME:${DB_USERNAME:root}}",
    "spring.datasource.password=${TEST_DB_PASSWORD:${DB_PASSWORD:1234}}"
})
class SecurityHardeningIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testAnonymousAccessToAdminDashboardIsRedirected() throws Exception {
        mockMvc.perform(get("/admin/dashboard").secure(true))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void testPublicDmsDownloadInvalidVersionReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/public/dms/download/999999").secure(true))
                .andExpect(status().isNotFound());
    }

    @Test
    void testPublicDmsDownloadInvalidMasterReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/public/dms/document/999999/download").secure(true))
                .andExpect(status().isNotFound());
    }

    @Test
    void testUnregisteredPhysicalFileReturnsNotFound() throws Exception {
        mockMvc.perform(get("/documents/non_existent_unregistered_test_file.pdf").secure(true))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/uploaded-documents/non_existent_unregistered_test_file.pdf").secure(true))
                .andExpect(status().isNotFound());
    }

    @Test
    void testPathTraversalAttemptReturnsNotFoundOrBadRequest() throws Exception {
        mockMvc.perform(get("/uploaded-documents/../application.yml").secure(true))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    if (status != 404 && status != 400 && status != 403) {
                        throw new AssertionError("Expected 404, 400, or 403 for path traversal attempt but got: " + status);
                    }
                });

        mockMvc.perform(get("/documents/../application.yml").secure(true))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    if (status != 404 && status != 400 && status != 403) {
                        throw new AssertionError("Expected 404, 400, or 403 for path traversal attempt but got: " + status);
                    }
                });
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void testAuthenticatedAdminCanAccessAdminApi() throws Exception {
        mockMvc.perform(get("/api/admin/dms/documents").secure(true))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }
}
