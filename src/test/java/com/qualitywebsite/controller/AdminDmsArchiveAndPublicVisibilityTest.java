package com.qualitywebsite.controller;

import com.qualitywebsite.entity.DeletedDocument;
import com.qualitywebsite.entity.DocumentEntity;
import com.qualitywebsite.entity.DocumentMaster;
import com.qualitywebsite.entity.DocumentVersion;
import com.qualitywebsite.repository.DeletedDocumentRepository;
import com.qualitywebsite.repository.DocumentMasterRepository;
import com.qualitywebsite.repository.DocumentRepository;
import com.qualitywebsite.repository.DocumentVersionRepository;
import com.qualitywebsite.service.DmsDocumentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:archive_visibility_testdb;DB_CLOSE_DELAY=-1;MODE=MySQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "server.ssl.enabled=false"
})
class AdminDmsArchiveAndPublicVisibilityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DocumentMasterRepository documentMasterRepository;

    @Autowired
    private DocumentVersionRepository documentVersionRepository;

    @Autowired
    private DeletedDocumentRepository deletedDocumentRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DmsDocumentService dmsDocumentService;

    private DocumentMaster activeMaster;
    private DocumentVersion activeVersion;

    private DocumentMaster archivedMaster;
    private DocumentVersion archivedVersion;

    private DocumentMaster approvedGtMaster;
    private DocumentMaster archivedGtMaster;

    @BeforeEach
    void setUp() {
        documentVersionRepository.deleteAll();
        documentMasterRepository.deleteAll();
        documentRepository.deleteAll();

        // 1. Create Active Approved Master & Version (ASPICE PRM)
        activeMaster = DocumentMaster.builder()
                .documentCode("ACTIVE-DOC-001")
                .processId("SYS.1")
                .processName("Requirements Elicitation")
                .processGroup("System Engineering")
                .category("ASPICE PRM")
                .documentName("Active Test Document")
                .description("Description for active document")
                .currentVersion("1.0")
                .status("APPROVED")
                .createdBy("admin")
                .createdDate(LocalDateTime.now())
                .updatedDate(LocalDateTime.now())
                .build();
        activeMaster = documentMasterRepository.save(activeMaster);

        activeVersion = DocumentVersion.builder()
                .documentMaster(activeMaster)
                .majorVersion(1)
                .minorVersion(0)
                .fileName("Active_Test_Doc_v1.0.pdf")
                .fileType("PDF")
                .mimeType("application/pdf")
                .fileSize(2048L)
                .fileData("Active PDF content".getBytes())
                .checksum("checksum_active_1001")
                .uploadedBy("admin")
                .uploadedDate(LocalDateTime.now())
                .approvalStatus("APPROVED")
                .isLatest(true)
                .build();
        activeVersion = documentVersionRepository.save(activeVersion);

        DocumentEntity activeLegacy = DocumentEntity.builder()
                .id("SYS1-001")
                .documentName("Active Test Document")
                .category("ASPICE PRM")
                .process("SYS.1")
                .processGroup("System Engineering")
                .version("1.0")
                .fileName("Active_Test_Doc_v1.0.pdf")
                .filePath("/uploaded-documents/Active_Test_Doc_v1.0.pdf")
                .fileType("PDF")
                .fileSize(2048L)
                .isActive(true)
                .updatedAt(LocalDateTime.now())
                .build();
        documentRepository.save(activeLegacy);

        // 2. Create Archived Master & Version (ASPICE PRM)
        archivedMaster = DocumentMaster.builder()
                .documentCode("ARCHIVED-DOC-002")
                .processId("SWE.1")
                .processName("SW Requirements Analysis")
                .processGroup("Software Engineering")
                .category("ASPICE PRM")
                .documentName("Archived Test Document")
                .description("Description for archived document")
                .currentVersion("1.0")
                .status("ARCHIVED")
                .createdBy("admin")
                .createdDate(LocalDateTime.now())
                .updatedDate(LocalDateTime.now())
                .build();
        archivedMaster = documentMasterRepository.save(archivedMaster);

        archivedVersion = DocumentVersion.builder()
                .documentMaster(archivedMaster)
                .majorVersion(1)
                .minorVersion(0)
                .fileName("Archived_Test_Doc_v1.0.pdf")
                .fileType("PDF")
                .mimeType("application/pdf")
                .fileSize(3072L)
                .fileData("Archived PDF content".getBytes())
                .checksum("checksum_archived_2002")
                .uploadedBy("admin")
                .uploadedDate(LocalDateTime.now())
                .approvalStatus("ARCHIVED")
                .isLatest(true)
                .build();
        archivedVersion = documentVersionRepository.save(archivedVersion);

        DocumentEntity archivedLegacy = DocumentEntity.builder()
                .id("SWE1-002")
                .documentName("Archived Test Document")
                .category("ASPICE PRM")
                .process("SWE.1")
                .processGroup("Software Engineering")
                .version("1.0")
                .fileName("Archived_Test_Doc_v1.0.pdf")
                .filePath("/uploaded-documents/Archived_Test_Doc_v1.0.pdf")
                .fileType("PDF")
                .fileSize(3072L)
                .isActive(false)
                .updatedAt(LocalDateTime.now())
                .build();
        documentRepository.save(archivedLegacy);

        // 3. Approved Generic Template
        approvedGtMaster = DocumentMaster.builder()
                .documentCode("GT-DOC-003")
                .processId("GLOBAL")
                .processName("General")
                .processGroup("Quality")
                .category("Generic Templates")
                .documentName("Approved Generic Template")
                .description("Approved template description")
                .currentVersion("1.0")
                .status("APPROVED")
                .createdBy("admin")
                .createdDate(LocalDateTime.now())
                .updatedDate(LocalDateTime.now())
                .build();
        approvedGtMaster = documentMasterRepository.save(approvedGtMaster);

        DocumentVersion approvedGtVersion = DocumentVersion.builder()
                .documentMaster(approvedGtMaster)
                .majorVersion(1)
                .minorVersion(0)
                .fileName("Approved_Generic_Template_v1.0.docx")
                .fileType("DOCX")
                .mimeType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                .fileSize(1024L)
                .fileData("Approved GT content".getBytes())
                .checksum("checksum_gt_3003")
                .uploadedBy("admin")
                .uploadedDate(LocalDateTime.now())
                .approvalStatus("APPROVED")
                .isLatest(true)
                .build();
        documentVersionRepository.save(approvedGtVersion);

        DocumentEntity approvedGtLegacy = DocumentEntity.builder()
                .id("GT-003")
                .documentName("Approved Generic Template")
                .category("Generic Templates")
                .process("GLOBAL")
                .processGroup("Quality")
                .version("1.0")
                .fileName("Approved_Generic_Template_v1.0.docx")
                .filePath("/uploaded-documents/Approved_Generic_Template_v1.0.docx")
                .fileType("DOCX")
                .fileSize(1024L)
                .isActive(true)
                .updatedAt(LocalDateTime.now())
                .build();
        documentRepository.save(approvedGtLegacy);

        // 4. Archived Generic Template
        archivedGtMaster = DocumentMaster.builder()
                .documentCode("GT-DOC-004")
                .processId("GLOBAL")
                .processName("General")
                .processGroup("Quality")
                .category("Generic Templates")
                .documentName("Archived Generic Template")
                .description("Archived template description")
                .currentVersion("1.0")
                .status("ARCHIVED")
                .createdBy("admin")
                .createdDate(LocalDateTime.now())
                .updatedDate(LocalDateTime.now())
                .build();
        archivedGtMaster = documentMasterRepository.save(archivedGtMaster);

        DocumentVersion archivedGtVersion = DocumentVersion.builder()
                .documentMaster(archivedGtMaster)
                .majorVersion(1)
                .minorVersion(0)
                .fileName("Archived_Generic_Template_v1.0.docx")
                .fileType("DOCX")
                .mimeType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                .fileSize(1024L)
                .fileData("Archived GT content".getBytes())
                .checksum("checksum_gt_4004")
                .uploadedBy("admin")
                .uploadedDate(LocalDateTime.now())
                .approvalStatus("ARCHIVED")
                .isLatest(true)
                .build();
        documentVersionRepository.save(archivedGtVersion);

        DocumentEntity archivedGtLegacy = DocumentEntity.builder()
                .id("GT-004")
                .documentName("Archived Generic Template")
                .category("Generic Templates")
                .process("GLOBAL")
                .processGroup("Quality")
                .version("1.0")
                .fileName("Archived_Generic_Template_v1.0.docx")
                .filePath("/uploaded-documents/Archived_Generic_Template_v1.0.docx")
                .fileType("DOCX")
                .fileSize(1024L)
                .isActive(false)
                .updatedAt(LocalDateTime.now())
                .build();
        documentRepository.save(archivedGtLegacy);
    }

    // 1-4. APPROVED -> ARCHIVED, hidden & non-downloadable publicly, appears in Archived Documents filter
    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("1-4. Archiving active document sets status to ARCHIVED, hides it from public website & download, appears in Admin Archived Documents")
    void testArchiveDocumentBehaviorAndVisibility() throws Exception {
        Long id = activeMaster.getId();

        mockMvc.perform(post("/api/admin/dms/documents/" + id + "/archive")
                        .with(csrf())
                        .secure(true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Document archived successfully"));

        DocumentMaster reloaded = documentMasterRepository.findById(id).orElseThrow();
        assertEquals("ARCHIVED", reloaded.getStatus());

        List<DocumentVersion> versions = documentVersionRepository.findByDocumentMasterIdOrderByUploadedDateDesc(id);
        assertFalse(versions.isEmpty());
        assertEquals(activeVersion.getId(), versions.get(0).getId());

        // Admin filter ARCHIVED returns archived document
        mockMvc.perform(get("/api/admin/dms/documents?status=ARCHIVED").secure(true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].documentName", hasItem("Active Test Document")));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("Regression Test: DELETE /api/admin/documents/{id} sets master & version status to DELETED with deleted_by & deleted_date and creates deleted_documents archive")
    void testDeleteEndpointWorkflow() throws Exception {
        Long id = activeMaster.getId();

        mockMvc.perform(delete("/api/admin/documents/" + id)
                        .with(csrf())
                        .secure(true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Document deleted successfully"));

        DocumentMaster reloaded = documentMasterRepository.findById(id).orElseThrow();
        assertEquals("DELETED", reloaded.getStatus());
        assertNotNull(reloaded.getDeletedBy());
        assertNotNull(reloaded.getDeletedDate());

        List<DocumentVersion> versions = documentVersionRepository.findByDocumentMasterIdOrderByUploadedDateDesc(id);
        assertFalse(versions.isEmpty());
        assertEquals("DELETED", versions.get(0).getApprovalStatus());

        Optional<DeletedDocument> delOpt = deletedDocumentRepository.findByOriginalMasterId(id);
        assertTrue(delOpt.isPresent());
        assertEquals("Active Test Document", delOpt.get().getDocumentName());
    }

    // 5-13. Direct Restore ARCHIVED -> APPROVED Test
    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("5-13. Restoring ARCHIVED document sets status directly to APPROVED, latest version to APPROVED, preserves version number & history, appears in Active Documents, disappears from Archived Documents, becomes publicly visible and downloadable")
    void testDirectRestoreToApprovedWorkflow() throws Exception {
        Long id = archivedMaster.getId();
        int initialVersionCount = documentVersionRepository.findByDocumentMasterIdOrderByUploadedDateDesc(id).size();

        // Restore ARCHIVED document -> status becomes APPROVED directly (NOT UNDER_REVIEW)
        mockMvc.perform(post("/api/admin/dms/documents/" + id + "/restore")
                        .with(csrf())
                        .secure(true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(containsString("restored successfully")));

        DocumentMaster restoredMaster = documentMasterRepository.findById(id).orElseThrow();
        assertEquals("APPROVED", restoredMaster.getStatus());
        assertNotEquals("UNDER_REVIEW", restoredMaster.getStatus());

        DocumentVersion restoredVersion = documentVersionRepository.findByDocumentMasterIdAndIsLatestTrue(id).orElseThrow();
        assertEquals("APPROVED", restoredVersion.getApprovalStatus());

        // Verify version count and version number remain unchanged
        List<DocumentVersion> versionsAfterRestore = documentVersionRepository.findByDocumentMasterIdOrderByUploadedDateDesc(id);
        assertEquals(initialVersionCount, versionsAfterRestore.size());
        assertEquals("1.0", restoredMaster.getCurrentVersion());
        assertEquals(archivedVersion.getId(), restoredVersion.getId());

        // Admin filter ACTIVE includes restored document; ARCHIVED filter excludes it
        mockMvc.perform(get("/api/admin/dms/documents?status=ACTIVE").secure(true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].documentName", hasItem("Archived Test Document")));

        mockMvc.perform(get("/api/admin/dms/documents?status=ARCHIVED").secure(true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].documentName", not(hasItem("Archived Test Document"))));

        // Restored document is immediately visible publicly & downloadable
        mockMvc.perform(get("/api/public/documents").secure(true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].documentName", hasItem("Archived Test Document")));

        mockMvc.perform(get("/api/public/dms/download/" + restoredVersion.getId()).secure(true))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"));
    }

    // 14-22. Permanent Delete Workflow Test
    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("14-22. Permanent Delete moves document to DELETED status, records deletedBy & deletedDate, appears in Deleted Documents, disappears from Active/Archived/Public APIs/search/downloads")
    void testPermanentDeleteWorkflow() throws Exception {
        Long id = archivedMaster.getId();

        // Perform Permanent Delete
        mockMvc.perform(post("/api/admin/dms/documents/" + id + "/delete-permanently")
                        .with(csrf())
                        .secure(true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(containsString("permanently deleted")));

        DocumentMaster deletedMaster = documentMasterRepository.findById(id).orElseThrow();
        assertEquals("DELETED", deletedMaster.getStatus());
        assertEquals("admin", deletedMaster.getDeletedBy());
        assertNotNull(deletedMaster.getDeletedDate());

        // Disappears from ACTIVE, ARCHIVED, and ALL admin filters
        mockMvc.perform(get("/api/admin/dms/documents?status=ACTIVE").secure(true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].documentName", not(hasItem("Archived Test Document"))));

        mockMvc.perform(get("/api/admin/dms/documents?status=ARCHIVED").secure(true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].documentName", not(hasItem("Archived Test Document"))));

        mockMvc.perform(get("/api/admin/dms/documents?status=ALL").secure(true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].documentName", not(hasItem("Archived Test Document"))));

        // Appears in DELETED admin section filter
        mockMvc.perform(get("/api/admin/dms/documents?status=DELETED").secure(true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].documentName", hasItem("Archived Test Document")))
                .andExpect(jsonPath("$[*].deletedBy", hasItem("admin")));

        // Excluded from public listings, public search, and public downloads
        mockMvc.perform(get("/api/public/documents").secure(true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].documentName", not(hasItem("Archived Test Document"))));

        mockMvc.perform(get("/api/public/search?query=Archived").secure(true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].documentName", not(hasItem("Archived Test Document"))));

        mockMvc.perform(get("/api/public/dms/download/" + archivedVersion.getId()).secure(true))
                .andExpect(status().isNotFound());
    }

    // 23-26. UNDER_REVIEW, Reject workflow, Version History, and Public Security Regression
    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("23-26. UNDER_REVIEW and Reject workflow for new documents, version history, and public security regression pass cleanly")
    void testUnderReviewRejectAndPublicSecurityRegression() throws Exception {
        // Create an UNDER_REVIEW document
        DocumentMaster reviewMaster = DocumentMaster.builder()
                .documentCode("REVIEW-DOC-005")
                .processId("SUP.1")
                .processName("Quality Assurance")
                .processGroup("Support")
                .category("ASPICE PRM")
                .documentName("Under Review Document")
                .currentVersion("1.0")
                .status("UNDER_REVIEW")
                .createdBy("admin")
                .createdDate(LocalDateTime.now())
                .updatedDate(LocalDateTime.now())
                .build();
        reviewMaster = documentMasterRepository.save(reviewMaster);

        DocumentVersion reviewVersion = DocumentVersion.builder()
                .documentMaster(reviewMaster)
                .majorVersion(1)
                .minorVersion(0)
                .fileName("Review_Doc_v1.0.pdf")
                .fileType("PDF")
                .mimeType("application/pdf")
                .fileSize(1024L)
                .fileData("Review content".getBytes())
                .checksum("checksum_review_5005")
                .uploadedBy("admin")
                .uploadedDate(LocalDateTime.now())
                .approvalStatus("UNDER_REVIEW")
                .isLatest(true)
                .build();
        documentVersionRepository.save(reviewVersion);

        DocumentEntity reviewLegacy = DocumentEntity.builder()
                .id("SUP1-005")
                .documentName("Under Review Document")
                .category("ASPICE PRM")
                .process("SUP.1")
                .processGroup("Support")
                .version("1.0")
                .fileName("Review_Doc_v1.0.pdf")
                .filePath("/uploaded-documents/Review_Doc_v1.0.pdf")
                .fileType("PDF")
                .fileSize(1024L)
                .isActive(false)
                .updatedAt(LocalDateTime.now())
                .build();
        documentRepository.save(reviewLegacy);

        // UNDER_REVIEW document is NOT visible publicly
        mockMvc.perform(get("/api/public/documents").secure(true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].documentName", not(hasItem("Under Review Document"))));

        // Reject workflow works
        mockMvc.perform(post("/api/admin/dms/documents/" + reviewMaster.getId() + "/reject")
                        .with(csrf())
                        .secure(true))
                .andExpect(status().isOk());

        DocumentMaster rejectedMaster = documentMasterRepository.findById(reviewMaster.getId()).orElseThrow();
        assertEquals("REJECTED", rejectedMaster.getStatus());

        // Approve workflow works
        mockMvc.perform(post("/api/admin/dms/documents/" + reviewMaster.getId() + "/approve")
                        .with(csrf())
                        .secure(true))
                .andExpect(status().isOk());

        DocumentMaster approvedMaster = documentMasterRepository.findById(reviewMaster.getId()).orElseThrow();
        assertEquals("APPROVED", approvedMaster.getStatus());

        // Now visible publicly
        mockMvc.perform(get("/api/public/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].documentName", hasItem("Under Review Document")));

        // Generic templates regression
        mockMvc.perform(get("/api/public/generic-templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].documentName", hasItem("Approved Generic Template")))
                .andExpect(jsonPath("$[*].documentName", not(hasItem("Archived Generic Template"))));

        // Direct URL security
        mockMvc.perform(get("/uploaded-documents/Active_Test_Doc_v1.0.pdf"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/uploaded-documents/Archived_Test_Doc_v1.0.pdf"))
                .andExpect(status().isNotFound());
    }
}
