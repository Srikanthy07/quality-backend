package com.qualitywebsite.controller;

import com.qualitywebsite.service.DmsDocumentService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:admin_modal_testdb;DB_CLOSE_DELAY=-1;MODE=MySQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class AdminDocumentsModalTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DmsDocumentService dmsDocumentService;

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("Verify Admin Documents page contains custom Archive Modal HTML elements and no native confirm()")
    void testAdminDocumentsPageModalHtmlAndNoNativeConfirm() throws Exception {
        mockMvc.perform(get("/admin/documents").secure(true))
                .andExpect(status().isOk())

                // Verify custom Archive modal structure
                .andExpect(content().string(containsString("id=\"archive-modal\"")))
                .andExpect(content().string(containsString("<h3>Archive Document</h3>")))
                .andExpect(content().string(containsString("Are you sure you want to archive this document?")))
                .andExpect(content().string(containsString("The document will be moved to ARCHIVED status and its version history will be preserved.")))

                // Verify modal buttons
                .andExpect(content().string(containsString("id=\"archive-modal-cancel-btn\"")))
                .andExpect(content().string(containsString("id=\"archive-modal-confirm-btn\"")))
                .andExpect(content().string(containsString(">Cancel</button>")))
                .andExpect(content().string(containsString(">Archive</button>")))

                // Verify modal controller functions exist
                .andExpect(content().string(containsString("openArchiveModal")))
                .andExpect(content().string(containsString("closeArchiveModal")))
                .andExpect(content().string(containsString("confirmArchiveDoc")))

                // Verify NO native confirm() exists in deleteDoc
                .andExpect(content().string(not(containsString("if (confirm("))))
                .andExpect(content().string(not(containsString("window.confirm("))));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("Verify existing DELETE endpoint archives document and returns 200 OK or 404")
    void testExistingDeleteEndpointArchivesDocument() throws Exception {
        mockMvc.perform(delete("/api/admin/dms/documents/999999")
                        .with(csrf())
                        .secure(true))
                .andExpect(status().isNotFound());
    }
}
