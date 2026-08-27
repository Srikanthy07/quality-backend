package com.qualitywebsite.service;

import com.qualitywebsite.entity.AdminUser;
import com.qualitywebsite.entity.DeletedDocument;
import com.qualitywebsite.entity.DocumentMaster;
import com.qualitywebsite.entity.DocumentVersion;
import com.qualitywebsite.repository.AdminUserRepository;
import com.qualitywebsite.repository.DeletedDocumentRepository;
import com.qualitywebsite.repository.DocumentMasterRepository;
import com.qualitywebsite.repository.DocumentVersionRepository;
import com.qualitywebsite.security.LoginRateLimiterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest
@TestPropertySource(properties = {
    "analytics.enabled=true",
    "spring.datasource.url=jdbc:h2:mem:recon_fix_testdb;DB_CLOSE_DELAY=-1;MODE=MySQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class DocumentReconciliationFixTest {

    @Autowired
    private DocumentReconciliationService documentReconciliationService;

    @Autowired
    private DmsDocumentService dmsDocumentService;

    @Autowired
    private DocumentMasterRepository documentMasterRepository;

    @Autowired
    private DocumentVersionRepository documentVersionRepository;

    @Autowired
    private DeletedDocumentRepository deletedDocumentRepository;

    @Autowired
    private AdminUserRepository adminUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private LoginRateLimiterService loginRateLimiterService;

    @BeforeEach
    void setUp() {
        loginRateLimiterService.loginSucceeded("admin");

        deletedDocumentRepository.deleteAll();
        documentVersionRepository.deleteAll();
        documentMasterRepository.deleteAll();

        adminUserRepository.deleteAll();
        AdminUser admin = AdminUser.builder()
                .username("admin")
                .email("admin@iast.com")
                .password(passwordEncoder.encode("Admin#Pass2026!"))
                .enabled(true)
                .build();
        adminUserRepository.save(admin);
    }

    @Test
    @Transactional
    void test1_SameNameDifferentMasterId_RemainIsolatedAfterReconciliation() {
        // Create Old Master (ID 42 equivalent) marked DELETED
        DocumentMaster oldMaster = DocumentMaster.builder()
                .documentCode("SUP1-ASPICEV31VSV")
                .processId("SUP.1")
                .processName("Quality Assurance")
                .processGroup("Support Processes")
                .category("Quality Manual")
                .documentName("ASPICE v3.1 vs v4.0")
                .status("DELETED")
                .deletedBy("admin")
                .deletedDate(LocalDateTime.now().minusDays(10))
                .currentVersion("1.0")
                .build();
        oldMaster = documentMasterRepository.save(oldMaster);

        DeletedDocument delDoc = DeletedDocument.builder()
                .originalMasterId(oldMaster.getId())
                .documentCode(oldMaster.getDocumentCode())
                .processId(oldMaster.getProcessId())
                .category(oldMaster.getCategory())
                .documentName(oldMaster.getDocumentName())
                .deletedBy("admin")
                .deletedDate(LocalDateTime.now().minusDays(10))
                .build();
        deletedDocumentRepository.save(delDoc);

        // Create New Master (ID 106 equivalent) marked APPROVED
        DocumentMaster newMaster = DocumentMaster.builder()
                .documentCode("SUP1-ASSE-ASPICE-98816")
                .processId("SUP.1")
                .processName("Quality Assurance")
                .processGroup("Support Processes")
                .category("Quality Manual")
                .documentName("ASPICE v3.1 vs v4.0")
                .status("APPROVED")
                .currentVersion("1.0")
                .build();
        newMaster = documentMasterRepository.save(newMaster);

        // Run reconciliation
        documentReconciliationService.reconcileAllDocuments();

        // Assert old master remains DELETED, new master remains APPROVED
        DocumentMaster recheckedOld = documentMasterRepository.findById(oldMaster.getId()).orElseThrow();
        DocumentMaster recheckedNew = documentMasterRepository.findById(newMaster.getId()).orElseThrow();

        assertEquals("DELETED", recheckedOld.getStatus(), "Old master must remain DELETED");
        assertEquals("APPROVED", recheckedNew.getStatus(), "New master with same display name MUST remain APPROVED");
    }

    @Test
    @Transactional
    void test2_SameNameDifferentDocumentCode_RemainIsolated() {
        DocumentMaster oldMaster = DocumentMaster.builder()
                .documentCode("SUP1-OLD-001")
                .processId("SUP.1")
                .category("Guidelines")
                .documentName("ASPICE v3.1 vs v4.0")
                .status("DELETED")
                .build();
        oldMaster = documentMasterRepository.save(oldMaster);

        deletedDocumentRepository.save(DeletedDocument.builder()
                .originalMasterId(oldMaster.getId())
                .documentCode("SUP1-OLD-001")
                .category("Guidelines")
                .documentName("ASPICE v3.1 vs v4.0")
                .build());

        DocumentMaster newMaster = DocumentMaster.builder()
                .documentCode("SUP1-NEW-001")
                .processId("SUP.1")
                .category("Guidelines")
                .documentName("ASPICE v3.1 vs v4.0")
                .status("APPROVED")
                .build();
        newMaster = documentMasterRepository.save(newMaster);

        documentReconciliationService.reconcileAllDocuments();

        assertEquals("APPROVED", documentMasterRepository.findById(newMaster.getId()).get().getStatus(),
                "Document with distinct code must remain APPROVED");
    }

    @Test
    @Transactional
    void test3_NewUploadAfterOldDeletion_LeavesNewUploadApproved() {
        DocumentMaster oldMaster = DocumentMaster.builder()
                .documentCode("DOC-OLD-" + UUID.randomUUID())
                .processId("MAN.3")
                .category("Templates")
                .documentName("Project Plan Template")
                .status("APPROVED")
                .build();
        oldMaster = documentMasterRepository.save(oldMaster);

        // Admin deletes old master
        boolean deleted = dmsDocumentService.deletePermanently(oldMaster.getId(), "admin");
        assertTrue(deleted, "Old master should be successfully deleted");

        // Now upload new document with same display name
        DocumentMaster newMaster = DocumentMaster.builder()
                .documentCode("DOC-NEW-" + UUID.randomUUID())
                .processId("MAN.3")
                .category("Templates")
                .documentName("Project Plan Template")
                .status("APPROVED")
                .build();
        newMaster = documentMasterRepository.save(newMaster);

        // Run reconciliation
        documentReconciliationService.reconcileAllDocuments();

        assertEquals("DELETED", documentMasterRepository.findById(oldMaster.getId()).get().getStatus());
        assertEquals("APPROVED", documentMasterRepository.findById(newMaster.getId()).get().getStatus(),
                "New document uploaded after old deletion must remain APPROVED");
    }

    @Test
    @Transactional
    void test4_ReconciliationIsIdempotent_RepeatedRunsDoNotDeleteValidDocument() {
        DocumentMaster master = DocumentMaster.builder()
                .documentCode("DOC-IDEM-" + UUID.randomUUID())
                .processId("SWE.1")
                .category("Processes")
                .documentName("Software Requirements Spec")
                .status("APPROVED")
                .build();
        master = documentMasterRepository.save(master);

        // Run reconciliation 3 times
        documentReconciliationService.reconcileAllDocuments();
        documentReconciliationService.reconcileAllDocuments();
        documentReconciliationService.reconcileAllDocuments();

        assertEquals("APPROVED", documentMasterRepository.findById(master.getId()).get().getStatus(),
                "Repeated reconciliation runs MUST NOT flip valid document status to DELETED");
    }

    @Test
    @Transactional
    void test5_GenuineAdminDeletion_CorrectlyArchivesAndPreservesDeletedStatus() {
        DocumentMaster master = DocumentMaster.builder()
                .documentCode("DOC-GENUINE-200")
                .processId("SYS.2")
                .category("Architecture")
                .documentName("System Architecture Spec")
                .status("APPROVED")
                .build();
        master = documentMasterRepository.save(master);

        DocumentVersion ver = DocumentVersion.builder()
                .documentMaster(master)
                .version("1.0")
                .fileName("Architecture_Spec.pdf")
                .fileType("pdf")
                .mimeType("application/pdf")
                .fileSize(1024L)
                .fileData(new byte[]{1, 2, 3})
                .checksum("checksum_test_5_" + UUID.randomUUID())
                .approvalStatus("APPROVED")
                .isLatest(true)
                .build();
        documentVersionRepository.save(ver);

        // Perform explicit admin deletion
        boolean success = dmsDocumentService.deletePermanently(master.getId(), "admin");
        assertTrue(success, "Permanent delete should return true");

        // Verify status in DB
        DocumentMaster deletedMaster = documentMasterRepository.findById(master.getId()).orElseThrow();
        assertEquals("DELETED", deletedMaster.getStatus());

        Optional<DeletedDocument> delArchiveOpt = deletedDocumentRepository.findByOriginalMasterId(master.getId());
        assertTrue(delArchiveOpt.isPresent(), "DeletedDocument archive record must exist for genuine deletion");
        assertEquals(master.getId(), delArchiveOpt.get().getOriginalMasterId());

        // Re-run reconciliation to confirm genuine deletion is preserved
        documentReconciliationService.reconcileAllDocuments();
        assertEquals("DELETED", documentMasterRepository.findById(master.getId()).get().getStatus());
    }

    @Test
    @Transactional
    void test6_MasterListIsolation_DeletedDocumentHidden_ApprovedSameNameVisible() {
        // Old deleted document
        DocumentMaster oldMaster = DocumentMaster.builder()
                .documentCode("CODE-OLD")
                .processId("SUP.1")
                .category("Guidelines")
                .documentName("ASPICE v3.1 vs v4.0")
                .status("DELETED")
                .build();
        oldMaster = documentMasterRepository.save(oldMaster);

        deletedDocumentRepository.save(DeletedDocument.builder()
                .originalMasterId(oldMaster.getId())
                .documentCode("CODE-OLD")
                .category("Guidelines")
                .documentName("ASPICE v3.1 vs v4.0")
                .build());

        // New approved document with same name
        DocumentMaster newMaster = DocumentMaster.builder()
                .documentCode("CODE-NEW")
                .processId("SUP.1")
                .category("Guidelines")
                .documentName("ASPICE v3.1 vs v4.0")
                .status("APPROVED")
                .build();
        newMaster = documentMasterRepository.save(newMaster);

        final Long oldMasterId = oldMaster.getId();
        final Long newMasterId = newMaster.getId();

        List<DocumentMaster> activeMasters = documentMasterRepository.findByStatusIgnoreCase("APPROVED");

        assertTrue(activeMasters.stream().anyMatch(m -> m.getId().equals(newMasterId)),
                "New approved same-name document must be present in active query");
        assertFalse(activeMasters.stream().anyMatch(m -> m.getId().equals(oldMasterId)),
                "Old deleted document must NOT be present in active query");
    }

    @Test
    @Transactional
    void test7_ApplicationStartupReconciliation_PreservesActiveApprovedDocuments() {
        DocumentMaster master = DocumentMaster.builder()
                .documentCode("CODE-STARTUP-1")
                .processId("MAN.3")
                .category("Management")
                .documentName("Quality Plan")
                .status("APPROVED")
                .build();
        master = documentMasterRepository.save(master);

        // Simulate application startup
        documentReconciliationService.runOneTimeReconciliation();

        assertEquals("APPROVED", documentMasterRepository.findById(master.getId()).get().getStatus(),
                "Application startup reconciliation must preserve APPROVED status");
    }

    @Test
    @Transactional
    void test8_RecoverySafety_RestoresOnlyIncorrectlyAutoDeletedDocuments() {
        // 1. Genuine deletion with DeletedDocument archive -> Should NOT be recovered
        DocumentMaster genuineDeleted = DocumentMaster.builder()
                .documentCode("CODE-GENUINE-DEL")
                .processId("SUP.1")
                .category("Safety")
                .documentName("Safety Manual")
                .status("DELETED")
                .build();
        genuineDeleted = documentMasterRepository.save(genuineDeleted);

        deletedDocumentRepository.save(DeletedDocument.builder()
                .originalMasterId(genuineDeleted.getId())
                .documentCode("CODE-GENUINE-DEL")
                .category("Safety")
                .documentName("Safety Manual")
                .build());

        // 2. Old deleted document (ID 42 equivalent)
        DocumentMaster oldDeleted = DocumentMaster.builder()
                .documentCode("CODE-HISTORICAL-42")
                .processId("SUP.1")
                .category("Quality Manual")
                .documentName("ASPICE v3.1 vs v4.0")
                .status("DELETED")
                .build();
        oldDeleted = documentMasterRepository.save(oldDeleted);

        deletedDocumentRepository.save(DeletedDocument.builder()
                .originalMasterId(oldDeleted.getId())
                .documentCode("CODE-HISTORICAL-42")
                .category("Quality Manual")
                .documentName("ASPICE v3.1 vs v4.0")
                .build());

        // 3. Incorrectly auto-deleted document (ID 106 equivalent: status DELETED, NO DeletedDocument for this masterId, but old DeletedDocument exists for same name)
        DocumentMaster incorrectlyDeleted = DocumentMaster.builder()
                .documentCode("CODE-AUTO-106")
                .processId("SUP.1")
                .category("Quality Manual")
                .documentName("ASPICE v3.1 vs v4.0")
                .status("DELETED")
                .build();
        incorrectlyDeleted = documentMasterRepository.save(incorrectlyDeleted);

        DocumentVersion version = DocumentVersion.builder()
                .documentMaster(incorrectlyDeleted)
                .version("1.0")
                .fileName("ASPICE_Comparison.pdf")
                .fileType("pdf")
                .mimeType("application/pdf")
                .fileSize(1024L)
                .fileData(new byte[]{1, 2, 3})
                .checksum("checksum_test_8_" + UUID.randomUUID())
                .approvalStatus("DELETED")
                .isLatest(true)
                .build();
        documentVersionRepository.save(version);

        // Run recovery
        List<DocumentMaster> recovered = documentReconciliationService.recoverIncorrectlyDeletedDocuments();

        // Assert: Only the incorrectly auto-deleted document (ID 106 equivalent) was recovered
        assertEquals(1, recovered.size(), "Only 1 document should be recovered");
        assertEquals(incorrectlyDeleted.getId(), recovered.get(0).getId());
        assertEquals("APPROVED", documentMasterRepository.findById(incorrectlyDeleted.getId()).get().getStatus());

        // Genuine deletions remain DELETED
        assertEquals("DELETED", documentMasterRepository.findById(genuineDeleted.getId()).get().getStatus());
        assertEquals("DELETED", documentMasterRepository.findById(oldDeleted.getId()).get().getStatus());
    }
}
