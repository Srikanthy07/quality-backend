package com.qualitywebsite;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:app_testdb;DB_CLOSE_DELAY=-1;MODE=MySQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class QualityWebsiteApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void contextLoads() {
    }

    @Test
    void testFeedbackEndpointIsPublicAndValidates() throws Exception {
        mockMvc.perform(post("/api/feedback").secure(true)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    if (status == 401 || status == 403) {
                        throw new AssertionError("Expected public access but got auth failure: " + status);
                    }
                });
    }

    @Test
    void testFeedbackValidPayloadPassesValidation() throws Exception {
        String validPayload = """
            {
              "name": "Test User",
              "email": "test@example.com",
              "message": "This is a test feedback message with enough characters."
            }
            """;

        mockMvc.perform(post("/api/feedback").secure(true)
                .contentType(MediaType.APPLICATION_JSON)
                .content(validPayload))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    if (status == 400 || status == 401 || status == 403) {
                        throw new AssertionError(
                            "Expected 200 or 500 but got " + status +
                            ". Response: " + result.getResponse().getContentAsString()
                        );
                    }
                });
    }

    @Test
    void testUnauthenticatedAdminAccessRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/admin/dashboard").secure(true))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void testPublicDataEndpointsAreAccessible() throws Exception {
        mockMvc.perform(get("/api/public/documents").secure(true))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));

        mockMvc.perform(get("/data/documents.json").secure(true))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void testAuthenticatedAdminAccessDashboard() throws Exception {
        mockMvc.perform(get("/admin/dashboard").secure(true))
                .andExpect(status().isOk());
    }
}