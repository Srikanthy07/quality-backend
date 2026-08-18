package com.qualitywebsite.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qualitywebsite.dto.DocumentMasterDTO;
import com.qualitywebsite.entity.DocumentMaster;
import com.qualitywebsite.entity.DocumentVersion;
import com.qualitywebsite.repository.DocumentMasterRepository;
import com.qualitywebsite.repository.DocumentVersionRepository;
import com.qualitywebsite.service.DmsDocumentService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:desc_edit_testdb;DB_CLOSE_DELAY=-1;MODE=MySQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class DocumentDescriptionEditTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DocumentMasterRepository documentMasterRepository;

    @Autowired
    private DocumentVersionRepository documentVersionRepository;

    @Autowired
    private DmsDocumentService dmsDocumentService;

    @Autowired
    private ObjectMapper objectMapper;

    private DocumentMaster docWithDescription;
    private DocumentMaster docWithoutDescription;

    @BeforeEach
    void setUp() {
        documentVersionRepository.deleteAll();
        documentMasterRepository.deleteAll();

        // 1. Document with Description
        DocumentMaster master1 = DocumentMaster.builder()
                .documentCode("PROC-WITH-DESC-001")
                .processId("SYS.2")
                .processName("System Requirements")
                .processGroup("System Engineering")
                .category("ASPICE PRM")
                .documentName("System Requirements Specification")
                .description("Initial description for system requirements specification guidelines.")
                .currentVersion("1.0")
                .status("APPROVED")
                .createdBy("admin")
                .createdDate(LocalDateTime.now())
                .updatedDate(LocalDateTime.now())
                .build();
        docWithDescription = documentMasterRepository.save(master1);

        DocumentVersion v1 = DocumentVersion.builder()
                .documentMaster(docWithDescription)
                .majorVersion(1)
                .minorVersion(0)
                .fileName("SYS2_SRS_v1.0.docx")
                .fileType("DOCX")
                .mimeType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                .fileSize(1024L)
                .fileData("test data".getBytes())
                .checksum("checksum1")
                .uploadedBy("admin")
                .uploadedDate(LocalDateTime.now())
                .approvalStatus("APPROVED")
                .isLatest(true)
                .build();
        documentVersionRepository.save(v1);

        // 2. Document without Description
        DocumentMaster master2 = DocumentMaster.builder()
                .documentCode("PROC-NO-DESC-002")
                .processId("SWE.1")
                .processName("Software Requirements")
                .processGroup("Software Engineering")
                .category("Generic Templates")
                .documentName("Software Test Plan Checklist")
                .description(null)
                .currentVersion("1.0")
                .status("APPROVED")
                .createdBy("admin")
                .createdDate(LocalDateTime.now())
                .updatedDate(LocalDateTime.now())
                .build();
        docWithoutDescription = documentMasterRepository.save(master2);

        DocumentVersion v2 = DocumentVersion.builder()
                .documentMaster(docWithoutDescription)
                .majorVersion(1)
                .minorVersion(0)
                .fileName("SWE1_Checklist_v1.0.docx")
                .fileType("DOCX")
                .mimeType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                .fileSize(2048L)
                .fileData("test data 2".getBytes())
                .checksum("checksum2")
                .uploadedBy("admin")
                .uploadedDate(LocalDateTime.now())
                .approvalStatus("APPROVED")
                .isLatest(true)
                .build();
        documentVersionRepository.save(v2);
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("Verify HTML template contains Description group and hasDescription dynamic check in Edit Details modal")
    void testHtmlContainsDescriptionGroup() throws Exception {
        mockMvc.perform(get("/admin/documents").secure(true))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"edit-description-group\"")))
                .andExpect(content().string(containsString("id=\"edit-description\"")))
                .andExpect(content().string(containsString("hasDescription")));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("Document with description -> Description is returned in DTO and can be updated without version increment")
    void testUpdateDocumentWithDescription() throws Exception {
        Long id = docWithDescription.getId();

        // 1. Verify GET returns existing description
        mockMvc.perform(get("/api/admin/dms/documents/" + id).secure(true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Initial description for system requirements specification guidelines."));

        // 2. Update description via PUT
        DocumentMasterDTO updateData = DocumentMasterDTO.builder()
                .entityVersion(docWithDescription.getEntityVersion())
                .documentName("Updated SRS Title")
                .category("ASPICE PRM")
                .processId("SYS.2")
                .processGroup("System Engineering")
                .description("Updated description for system requirements specification guidelines.")
                .build();

        mockMvc.perform(put("/api/admin/dms/documents/" + id)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateData))
                        .secure(true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Updated description for system requirements specification guidelines."))
                .andExpect(jsonPath("$.documentName").value("Updated SRS Title"));

        // 3. Verify in Database
        DocumentMaster updatedMaster = documentMasterRepository.findById(id).orElseThrow();
        assertEquals("Updated description for system requirements specification guidelines.", updatedMaster.getDescription());
        assertEquals("1.0", updatedMaster.getCurrentVersion(), "Document version must NOT change when updating description");

        // 4. Verify version history is unchanged
        var versions = dmsDocumentService.getVersionHistory(id);
        assertEquals(1, versions.size(), "Version history count must remain unchanged");
        assertEquals("1.0", versions.get(0).getVersion());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("Document without description -> Description remains null when not supplied and no description is created")
    void testDocumentWithoutDescriptionRemainsNull() throws Exception {
        Long id = docWithoutDescription.getId();

        // 1. Verify GET returns null description
        mockMvc.perform(get("/api/admin/dms/documents/" + id).secure(true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value(nullValue()));

        // 2. Perform metadata update WITHOUT description
        DocumentMasterDTO updateData = DocumentMasterDTO.builder()
                .entityVersion(docWithoutDescription.getEntityVersion())
                .documentName("Renamed Checklist")
                .category("Generic Templates")
                .processId("SWE.1")
                .processGroup("Software Engineering")
                .description(null) // Field not supplied
                .build();

        mockMvc.perform(put("/api/admin/dms/documents/" + id)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateData))
                        .secure(true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value(nullValue()))
                .andExpect(jsonPath("$.documentName").value("Renamed Checklist"));

        // 3. Verify Database description remains NULL
        DocumentMaster masterInDb = documentMasterRepository.findById(id).orElseThrow();
        assertNull(masterInDb.getDescription(), "No description should be created for documents without a description");
        assertEquals("1.0", masterInDb.getCurrentVersion());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("Existing Edit Details functionality for category, processId, and processGroup continues to work normally")
    void testExistingEditDetailsFunctionality() throws Exception {
        Long id = docWithDescription.getId();

        DocumentMasterDTO updateData = DocumentMasterDTO.builder()
                .entityVersion(docWithDescription.getEntityVersion())
                .documentName("System Requirements Specification v2")
                .category("Generic Templates")
                .processId("SYS.3")
                .processGroup("System Architecture")
                .build();

        mockMvc.perform(put("/api/admin/dms/documents/" + id)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateData))
                        .secure(true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentName").value("System Requirements Specification v2"))
                .andExpect(jsonPath("$.category").value("Generic Templates"))
                .andExpect(jsonPath("$.processId").value("SYS.3"))
                .andExpect(jsonPath("$.processGroup").value("System Architecture"));
    }
}
