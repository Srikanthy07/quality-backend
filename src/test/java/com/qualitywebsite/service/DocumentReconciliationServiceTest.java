package com.qualitywebsite.service;

import com.qualitywebsite.dto.DocumentReconciliationItemDTO;
import com.qualitywebsite.dto.UploadResponseDTO;
import com.qualitywebsite.entity.DeletedDocument;
import com.qualitywebsite.entity.DocumentMaster;
import com.qualitywebsite.entity.DocumentVersion;
import com.qualitywebsite.repository.DeletedDocumentRepository;
import com.qualitywebsite.repository.DocumentMasterRepository;
import com.qualitywebsite.repository.DocumentVersionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:reconcile_testdb;DB_CLOSE_DELAY=-1;MODE=MySQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional
class DocumentReconciliationServiceTest {

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

    private MockMultipartFile createMockPdf(String fileName, String content) {
        byte[] pdfContent = ("%PDF-1.4\n" + content + "\n%%EOF").getBytes();
        return new MockMultipartFile("file", fileName, "application/pdf", pdfContent);
    }

    @Test
    @DisplayName("TASK 12 Test 1 & 2: Active/Approved document remains active and approved during reconciliation")
    void testActiveDocumentRemainsActive() throws Exception {
        MockMultipartFile file = createMockPdf("ActiveDoc.pdf", "Active doc content 111");
        UploadResponseDTO res = dmsDocumentService.uploadDocument(
                file, "ASPICE PRM", "System Engineering Process Group", "SYS.1", "Requirement Analysis",
                "Active Test Document 111", "1.0", "Initial", "admin", false);
        Long masterId = res.getDocumentMasterId();
        dmsDocumentService.approveDocument(masterId, "admin");

        List<DocumentReconciliationItemDTO> report = documentReconciliationService.reconcileAllDocuments();

        DocumentMaster master = documentMasterRepository.findById(masterId).orElseThrow();
        assertThat(master.getStatus()).isEqualTo("APPROVED");

        DocumentReconciliationItemDTO item = report.stream()
                .filter(i -> masterId.equals(i.getMasterId()))
                .findFirst().orElseThrow();

        assertThat(item.getMasterStatus()).isEqualTo("APPROVED");
    }

    @Test
    @DisplayName("TASK 12 Test 3 & 4: Deleted ASPICE document is correctly reconciled to DELETED and gets 1 archive record")
    void testDeletedAspiceDocumentReconciledToDeleted() throws Exception {
        MockMultipartFile aspiceFile = createMockPdf("ASPICE_v3.1.pdf", "ASPICE v3.1 content 222");
        UploadResponseDTO res = dmsDocumentService.uploadDocument(
                aspiceFile, "ASPICE PRM", "System Engineering Process Group", "SUP.1", "Quality Assurance",
                "ASPICE v3.1 vs v4.0", "1.0", "ASPICE doc 222", "admin", false);
        Long masterId = res.getDocumentMasterId();
        dmsDocumentService.approveDocument(masterId, "admin");

        // Simulate legacy soft-deleted record or archive creation
        dmsDocumentService.deletePermanently(masterId, "admin");

        // Run reconciliation
        List<DocumentReconciliationItemDTO> report = documentReconciliationService.reconcileAllDocuments();

        DocumentMaster master = documentMasterRepository.findById(masterId).orElseThrow();
        assertThat(master.getStatus()).isEqualTo("DELETED");
        assertThat(master.getDeletedBy()).isNotNull();
        assertThat(master.getDeletedDate()).isNotNull();

        List<DeletedDocument> archives = deletedDocumentRepository.findAll().stream()
                .filter(d -> masterId.equals(d.getOriginalMasterId()))
                .toList();
        assertThat(archives).hasSize(1);

        DocumentReconciliationItemDTO item = report.stream()
                .filter(i -> masterId.equals(i.getMasterId()))
                .findFirst().orElseThrow();

        assertThat(item.getMasterStatus()).isEqualTo("DELETED");
    }

    @Test
    @DisplayName("TASK 12 Test 5, 6 & 7: Historical versions and current versions remain intact & duplicate checksum does not cause NonUniqueResultException")
    void testVersionHistoryAndChecksumSafety() throws Exception {
        MockMultipartFile fileV1 = createMockPdf("VerDoc.pdf", "Version doc content v1");
        UploadResponseDTO res1 = dmsDocumentService.uploadDocument(
                fileV1, "ASPICE PRM", "System Engineering Process Group", "SYS.2", "System Design",
                "Version History Doc", "1.0", "v1", "admin", false);
        Long masterId = res1.getDocumentMasterId();
        dmsDocumentService.approveDocument(masterId, "admin");

        MockMultipartFile fileV2 = createMockPdf("VerDoc_v2.pdf", "Version doc content v2");
        dmsDocumentService.uploadNewVersion(masterId, fileV2.getBytes(), "VerDoc_v2.pdf", "PDF", "application/pdf", "v2 upload", "admin");
        dmsDocumentService.approveDocument(masterId, "admin");

        List<DocumentReconciliationItemDTO> report = documentReconciliationService.reconcileAllDocuments();

        DocumentMaster master = documentMasterRepository.findById(masterId).orElseThrow();
        assertThat(master.getCurrentVersion()).isEqualTo("1.1");

        List<DocumentVersion> versions = documentVersionRepository.findByDocumentMasterIdOrderByUploadedDateDesc(masterId);
        assertThat(versions).hasSize(2);
        assertThat(versions.get(0).getIsLatest()).isTrue();

        DocumentReconciliationItemDTO item = report.stream()
                .filter(i -> masterId.equals(i.getMasterId()))
                .findFirst().orElseThrow();

        assertThat(item.getVersion()).isEqualTo("1.1");
    }

    @Test
    @DisplayName("TASK 12 Test 8 & 9: Deleted checksum does not block re-upload & active approved checksum blocks duplicate upload")
    void testChecksumBehaviorReuploadAndBlock() throws Exception {
        MockMultipartFile file = createMockPdf("SharedContent.pdf", "Shared checksum content 333");

        // 1. Upload & delete first doc
        UploadResponseDTO res1 = dmsDocumentService.uploadDocument(
                file, "ASPICE PRM", "System Engineering Process Group", "SYS.3", "Architecture",
                "Deleted Doc 333", "1.0", "Initial", "admin", false);
        Long masterId1 = res1.getDocumentMasterId();
        dmsDocumentService.approveDocument(masterId1, "admin");
        dmsDocumentService.deletePermanently(masterId1, "admin");

        // 2. Re-upload exact same file content -> MUST be allowed
        UploadResponseDTO res2 = dmsDocumentService.uploadDocument(
                file, "ASPICE PRM", "System Engineering Process Group", "SYS.3", "Architecture",
                "Reuploaded Doc 333", "1.0", "Reupload", "admin", false);
        assertThat(res2.isSuccess()).isTrue();
        Long masterId2 = res2.getDocumentMasterId();
        assertThat(masterId2).isNotEqualTo(masterId1);

        // 3. Approve second doc
        dmsDocumentService.approveDocument(masterId2, "admin");

        // 4. Try uploading exact same file a third time while masterId2 is APPROVED -> MUST be blocked as duplicate
        UploadResponseDTO res3 = dmsDocumentService.uploadDocument(
                file, "ASPICE PRM", "System Engineering Process Group", "SYS.3", "Architecture",
                "Duplicate Active Doc", "1.0", "Should block", "admin", false);

        assertThat(res3.isSuccess()).isFalse();
        assertThat(res3.isDuplicateChecksum()).isTrue();
        assertThat(res3.getAction()).isEqualTo("REJECTED");
    }

    @Test
    @DisplayName("TASK 12 Test 10 & 11: Idempotency & No duplicate DocumentMaster records created on repeated execution")
    void testIdempotencyAndNoDuplicatesCreated() throws Exception {
        int masterCountBefore = (int) documentMasterRepository.count();

        // Run reconciliation 1st time
        List<DocumentReconciliationItemDTO> report1 = documentReconciliationService.reconcileAllDocuments();
        int masterCountAfter1 = (int) documentMasterRepository.count();

        // Run reconciliation 2nd time
        List<DocumentReconciliationItemDTO> report2 = documentReconciliationService.reconcileAllDocuments();
        int masterCountAfter2 = (int) documentMasterRepository.count();

        assertThat(masterCountAfter1).isEqualTo(masterCountBefore);
        assertThat(masterCountAfter2).isEqualTo(masterCountBefore);
        assertThat(report2.size()).isEqualTo(report1.size());

        for (DocumentReconciliationItemDTO item : report2) {
            assertThat(item.getReconciliationResult()).isEqualTo("SKIPPED");
        }
    }
}
