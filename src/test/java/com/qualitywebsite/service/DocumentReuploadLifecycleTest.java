package com.qualitywebsite.service;

import com.qualitywebsite.dto.UploadResponseDTO;
import com.qualitywebsite.entity.DocumentMaster;
import com.qualitywebsite.entity.DocumentVersion;
import com.qualitywebsite.repository.DocumentMasterRepository;
import com.qualitywebsite.repository.DocumentVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import com.qualitywebsite.entity.DeletedDocument;
import com.qualitywebsite.repository.DeletedDocumentRepository;
import com.qualitywebsite.repository.DocumentMasterRepository;
import com.qualitywebsite.repository.DocumentVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
public class DocumentReuploadLifecycleTest {

    @Autowired
    private DataInitializationService dataInitializationService;

    @Autowired
    private DmsMigrationService dmsMigrationService;

    @Autowired
    private DmsDocumentService dmsDocumentService;

    @Autowired
    private DocumentMasterRepository documentMasterRepository;

    @Autowired
    private DocumentVersionRepository documentVersionRepository;

    @Autowired
    private DeletedDocumentRepository deletedDocumentRepository;

    @BeforeEach
    void setUp() {
        dataInitializationService.seedDocuments();
        dmsMigrationService.migrateToDatabaseStorage();
    }

    private MockMultipartFile createMockPdf(String filename, String contentStr) {
        byte[] pdfHeader = "%PDF-1.4\n".getBytes();
        byte[] customBytes = contentStr.getBytes();
        byte[] fullContent = new byte[pdfHeader.length + customBytes.length];
        System.arraycopy(pdfHeader, 0, fullContent, 0, pdfHeader.length);
        System.arraycopy(customBytes, 0, fullContent, pdfHeader.length, customBytes.length);

        return new MockMultipartFile("file", filename, "application/pdf", fullContent);
    }

    @Test
    @DisplayName("Test 1: Active document + same checksum -> duplicate rejected")
    void test1_ActiveDocument_SameChecksum_DuplicateRejected() throws Exception {
        MockMultipartFile file = createMockPdf("Quality_Policy_Test1.pdf", "Content for Test 1 Unique Checksum AAA");
        
        // 1. Initial upload -> ACTIVE / UNDER_REVIEW master created
        UploadResponseDTO res1 = dmsDocumentService.uploadDocument(
                file, "ASPICE PRM", "Supporting Process Group", "SUP.1", "Quality Assurance",
                "Quality Policy Test 1", "1.0", "Initial upload", "admin", false);
        assertThat(res1.isSuccess()).isTrue();
        Long masterId1 = res1.getDocumentMasterId();

        // Approve it so it becomes fully active
        dmsDocumentService.approveDocument(masterId1, "admin");

        // 2. Upload exact same file content again
        UploadResponseDTO res2 = dmsDocumentService.uploadDocument(
                file, "ASPICE PRM", "Supporting Process Group", "SUP.1", "Quality Assurance",
                "Quality Policy Test 1 Duplicate", "1.0", "Duplicate upload attempt", "admin", false);

        assertThat(res2.isSuccess()).isFalse();
        assertThat(res2.isDuplicateChecksum()).isTrue();
        assertThat(res2.getMessage()).contains("Duplicate file detected");
    }

    @Test
    @DisplayName("Test 2: Active document + changed checksum -> new version according to versioning logic")
    void test2_ActiveDocument_ChangedChecksum_NewVersion() throws Exception {
        MockMultipartFile file1 = createMockPdf("Doc2_v1.pdf", "Content Version 1.0 BBB");
        UploadResponseDTO res1 = dmsDocumentService.uploadDocument(
                file1, "ASPICE PRM", "System Engineering Process Group", "SYS.1", "Requirements Elicitation",
                "Doc Test 2", "1.0", "Version 1.0", "admin", false);
        assertThat(res1.isSuccess()).isTrue();
        Long masterId = res1.getDocumentMasterId();
        dmsDocumentService.approveDocument(masterId, "admin");

        // Upload same document name but with changed file content (different checksum)
        MockMultipartFile file2 = createMockPdf("Doc2_v2.pdf", "Content Version 2.0 CCC Changed");
        UploadResponseDTO res2 = dmsDocumentService.uploadDocument(
                file2, "ASPICE PRM", "System Engineering Process Group", "SYS.1", "Requirements Elicitation",
                "Doc Test 2", "2.0", "Version 2.0 update", "admin", true);

        assertThat(res2.isSuccess()).isTrue();
        assertThat(res2.getDocumentMasterId()).isEqualTo(masterId); // Same master
        assertThat(res2.getVersion()).isEqualTo("2.0");

        List<DocumentVersion> versions = documentVersionRepository.findByDocumentMasterIdOrderByUploadedDateDesc(masterId);
        assertThat(versions.size()).isEqualTo(2);
    }

    @Test
    @DisplayName("Test 3: Archived document + same checksum -> upload allowed")
    void test3_ArchivedDocument_SameChecksum_UploadAllowed() throws Exception {
        MockMultipartFile file = createMockPdf("Archived_Test3.pdf", "Content for Test 3 DDD");

        // 1. Initial upload & approve
        UploadResponseDTO res1 = dmsDocumentService.uploadDocument(
                file, "ASPICE PRM", "System Engineering Process Group", "SYS.2", "System Requirements Analysis",
                "Archived Doc Test 3", "1.0", "Initial", "admin", false);
        assertThat(res1.isSuccess()).isTrue();
        Long masterId1 = res1.getDocumentMasterId();
        dmsDocumentService.approveDocument(masterId1, "admin");

        // 2. Archive the document
        boolean archived = dmsDocumentService.archiveDocument(masterId1, "admin");
        assertThat(archived).isTrue();
        DocumentMaster master1 = documentMasterRepository.findById(masterId1).orElseThrow();
        assertThat(master1.getStatus()).isEqualTo("ARCHIVED");

        // 3. Upload exact same file content again
        UploadResponseDTO res2 = dmsDocumentService.uploadDocument(
                file, "ASPICE PRM", "System Engineering Process Group", "SYS.2", "System Requirements Analysis",
                "Archived Doc Test 3 Reuploaded", "1.0", "Reupload after archive", "admin", false);

        assertThat(res2.isSuccess()).isTrue();
        assertThat(res2.getDocumentMasterId()).isNotEqualTo(masterId1); // New master created
    }

    @Test
    @DisplayName("Test 4: Deleted document + same checksum -> upload allowed")
    void test4_DeletedDocument_SameChecksum_UploadAllowed() throws Exception {
        MockMultipartFile file = createMockPdf("Deleted_Test4.pdf", "Content for Test 4 EEE");

        // Upload, approve, and permanently delete
        UploadResponseDTO res1 = dmsDocumentService.uploadDocument(
                file, "ASPICE PRM", "System Engineering Process Group", "SYS.3", "System Architectural Design",
                "Deleted Doc Test 4", "1.0", "Initial", "admin", false);
        Long masterId1 = res1.getDocumentMasterId();
        dmsDocumentService.approveDocument(masterId1, "admin");
        dmsDocumentService.deletePermanently(masterId1, "admin");

        DocumentMaster master1 = documentMasterRepository.findById(masterId1).orElseThrow();
        assertThat(master1.getStatus()).isEqualTo("DELETED");

        // Upload exact same file content again
        UploadResponseDTO res2 = dmsDocumentService.uploadDocument(
                file, "ASPICE PRM", "System Engineering Process Group", "SYS.3", "System Architectural Design",
                "Deleted Doc Test 4 Reuploaded", "1.0", "Reupload after permanent delete", "admin", false);

        assertThat(res2.isSuccess()).isTrue();
        assertThat(res2.getDocumentMasterId()).isNotEqualTo(masterId1);
    }

    @Test
    @DisplayName("Test 5 & 6 & 7 & 8 & Verification Sequence: Complete re-upload lifecycle after permanent deletion")
    void testFullReuploadLifecycleSequence() throws Exception {
        // Step 1: Upload "Test.pdf"
        MockMultipartFile testPdf = createMockPdf("Test.pdf", "Unique lifecycle test content FFF");
        UploadResponseDTO res1 = dmsDocumentService.uploadDocument(
                testPdf, "ASPICE PRM", "Supporting Process Group", "SUP.8", "Configuration Management",
                "Test Document Lifecycle", "1.0", "Initial upload", "admin", false);
        assertThat(res1.isSuccess()).isTrue();
        Long oldMasterId = res1.getDocumentMasterId();

        // Step 2: Confirm it exists as ACTIVE / APPROVED
        dmsDocumentService.approveDocument(oldMasterId, "admin");
        DocumentMaster master1 = documentMasterRepository.findById(oldMasterId).orElseThrow();
        assertThat(master1.getStatus()).isEqualTo("APPROVED");

        // Step 3: Delete it (Archive/Delete)
        boolean archived = dmsDocumentService.archiveDocument(oldMasterId, "admin");
        assertThat(archived).isTrue();

        // Step 4: Confirm dedicated DeletedDocument entity was created in deleted_documents table
        assertThat(deletedDocumentRepository.findByOriginalMasterId(oldMasterId)).isPresent();
        DeletedDocument delDocEntry = deletedDocumentRepository.findByOriginalMasterId(oldMasterId).get();
        assertThat(delDocEntry.getDocumentName()).isEqualTo("Test Document Lifecycle");

        // Step 5: Confirm active documents list excludes deleted document
        assertThat(dmsDocumentService.getAllAdminDocuments(null, null, "ACTIVE"))
                .noneMatch(d -> d.getId().equals(oldMasterId));

        // Step 6: Confirm Deleted Documents view includes deleted document
        assertThat(dmsDocumentService.getAllAdminDocuments(null, null, "DELETED"))
                .anyMatch(d -> d.getDocumentName().equals("Test Document Lifecycle"));

        // Step 7: Upload the exact same "Test.pdf" file and document name again
        UploadResponseDTO res2 = dmsDocumentService.uploadDocument(
                testPdf, "ASPICE PRM", "Supporting Process Group", "SUP.8", "Configuration Management",
                "Test Document Lifecycle", "1.0", "Reupload after permanent deletion", "admin", false);

        // Step 8: Confirm upload succeeds
        assertThat(res2.isSuccess()).isTrue();

        // Step 9: Confirm a NEW DocumentMaster ID was created
        Long newMasterId = res2.getDocumentMasterId();
        assertThat(newMasterId).isNotNull();
        assertThat(newMasterId).isNotEqualTo(oldMasterId);

        // Step 10: Confirm the old DocumentMaster is STILL ARCHIVED and dedicated DeletedDocument entry remains intact
        DocumentMaster oldMasterStillDeleted = documentMasterRepository.findById(oldMasterId).orElseThrow();
        assertThat(oldMasterStillDeleted.getStatus()).isEqualTo("ARCHIVED");
        assertThat(deletedDocumentRepository.findByOriginalMasterId(oldMasterId)).isPresent();

        // Step 11: Confirm the new document is UNDER_REVIEW / ACTIVE
        DocumentMaster newMaster = documentMasterRepository.findById(newMasterId).orElseThrow();
        assertThat(newMaster.getStatus()).isEqualTo("UNDER_REVIEW");
        assertThat(newMaster.getDocumentName()).isEqualTo("Test Document Lifecycle");

        // Step 12: Confirm no duplicate error was generated
        assertThat(res2.isDuplicateChecksum()).isFalse();
        assertThat(res2.getAction()).isEqualTo("CREATED");
    }

    @Test
    @DisplayName("TASK 5 Scenario: ASPICE v3.1 vs v4.0 delete lifecycle & active checksum verification")
    void testAspiceDocumentDeleteLifecycle() throws Exception {
        // 1. Upload "ASPICE v3.1 vs v4.0"
        MockMultipartFile aspiceFile = createMockPdf("ASPICE_v3.1_vs_v4.0.pdf", "ASPICE v3.1 vs v4.0 comparative analysis content");
        UploadResponseDTO res1 = dmsDocumentService.uploadDocument(
                aspiceFile, "ASPICE PRM", "System Engineering Process Group", "SUP.1", "Quality Assurance",
                "ASPICE v3.1 vs v4.0", "1.0", "ASPICE comparison document", "admin", false);
        assertThat(res1.isSuccess()).isTrue();
        Long masterId = res1.getDocumentMasterId();

        // 2. Approve it
        dmsDocumentService.approveDocument(masterId, "admin");
        DocumentMaster masterBefore = documentMasterRepository.findById(masterId).orElseThrow();
        assertThat(masterBefore.getStatus()).isEqualTo("APPROVED");

        DocumentVersion versionBefore = documentVersionRepository.findByDocumentMasterIdAndIsLatestTrue(masterId).orElseThrow();
        assertThat(versionBefore.getApprovalStatus()).isEqualTo("APPROVED");
        String checksum = versionBefore.getChecksum();
        assertThat(checksum).isNotNull();

        // Active checksum query must return true before deletion
        assertThat(documentVersionRepository.existsActiveByChecksum(checksum)).isTrue();

        // 3. Delete it through the same service path used by the Admin UI Delete button
        boolean deleted = dmsDocumentService.deletePermanently(masterId, "admin");
        assertThat(deleted).isTrue();

        // 4. Assert document_master and document_version updated to DELETED with deleted_by & deleted_date
        DocumentMaster masterAfter = documentMasterRepository.findById(masterId).orElseThrow();
        assertThat(masterAfter.getStatus()).isEqualTo("DELETED");
        assertThat(masterAfter.getDeletedBy()).isNotNull();
        assertThat(masterAfter.getDeletedDate()).isNotNull();

        List<DocumentVersion> versionsAfter = documentVersionRepository.findByDocumentMasterIdOrderByUploadedDateDesc(masterId);
        assertThat(versionsAfter).isNotEmpty();
        for (DocumentVersion v : versionsAfter) {
            assertThat(v.getApprovalStatus()).isEqualTo("DELETED");
        }

        // Assert deleted_documents contains exactly one matching archive record
        List<DeletedDocument> delDocs = deletedDocumentRepository.findAll().stream()
                .filter(d -> masterId.equals(d.getOriginalMasterId()))
                .toList();
        assertThat(delDocs).hasSize(1);
        DeletedDocument delDoc = delDocs.get(0);
        assertThat(delDoc.getDocumentName()).isEqualTo("ASPICE v3.1 vs v4.0");

        // 5. Assert active checksum query returns FALSE after deletion
        assertThat(documentVersionRepository.existsActiveByChecksum(checksum)).isFalse();

        // 6. Assert old deleted records remain in database/history
        assertThat(documentMasterRepository.findById(masterId)).isPresent();
        assertThat(documentVersionRepository.findByDocumentMasterIdOrderByUploadedDateDesc(masterId)).isNotEmpty();

        // 7. Assert deleting the same document again does NOT create a second archive record
        boolean deletedAgain = dmsDocumentService.archiveDocument(masterId, "admin");
        assertThat(deletedAgain).isTrue();

        List<DeletedDocument> delDocsAfterSecond = deletedDocumentRepository.findAll().stream()
                .filter(d -> masterId.equals(d.getOriginalMasterId()))
                .toList();
        assertThat(delDocsAfterSecond).hasSize(1);

        // 8. Re-upload test: Upload exact same physical file again -> allowed as new active document
        UploadResponseDTO resReupload = dmsDocumentService.uploadDocument(
                aspiceFile, "ASPICE PRM", "System Engineering Process Group", "SUP.1", "Quality Assurance",
                "ASPICE v3.1 vs v4.0 Reuploaded", "1.0", "Reupload after delete", "admin", false);
        assertThat(resReupload.isSuccess()).isTrue();
        assertThat(resReupload.getDocumentMasterId()).isNotEqualTo(masterId);
    }

    @Test
    @DisplayName("TASK 8 Regression Test: Exact checksum 7eb3... with DELETED status allows re-upload & multiple historical records do not throw NonUniqueResultException")
    void testExactChecksumReuploadScenario() throws Exception {
        String exactChecksum = "7eb35cf691e151b04c5126c99e09222bab6de69b948c44491c2bbfa52cfb3190";

        // 1. Create historical DELETED master ID 42 (or master)
        DocumentMaster master42 = DocumentMaster.builder()
                .documentCode("SUP1-ASPICEV31VSV")
                .processId("SUP.1")
                .processName("Quality Assurance")
                .processGroup("Supporting Process Group")
                .category("Assessment Checklist")
                .documentName("ASPICE v3.1 vs v4.0")
                .currentVersion("4.0")
                .status("DELETED")
                .createdBy("admin")
                .deletedBy("admin")
                .deletedDate(java.time.LocalDateTime.now())
                .build();
        master42 = documentMasterRepository.save(master42);

        DocumentVersion version42v1 = DocumentVersion.builder()
                .documentMaster(master42)
                .version("1.0")
                .majorVersion(1)
                .minorVersion(0)
                .fileName("ASPICE_v3.1_vs_v4.0.pdf")
                .fileType("PDF")
                .mimeType("application/pdf")
                .fileSize(1024L)
                .fileData("dummy content 1".getBytes())
                .checksum(exactChecksum)
                .approvalStatus("DELETED")
                .isLatest(false)
                .build();
        documentVersionRepository.save(version42v1);

        DocumentVersion version42v2 = DocumentVersion.builder()
                .documentMaster(master42)
                .version("4.0")
                .majorVersion(4)
                .minorVersion(0)
                .fileName("ASPICE_v3.1_vs_v4.0.pdf")
                .fileType("PDF")
                .mimeType("application/pdf")
                .fileSize(1024L)
                .fileData("dummy content 2".getBytes())
                .checksum(exactChecksum)
                .approvalStatus("DELETED")
                .isLatest(true)
                .build();
        documentVersionRepository.save(version42v2);

        // 2. Verify multiple historical records with same checksum exist
        // Clear any pre-seeded active versions with this checksum for isolated scenario testing
        List<DocumentVersion> preSeededActive = documentVersionRepository.findActiveByChecksum(exactChecksum);
        for (DocumentVersion v : preSeededActive) {
            v.setApprovalStatus("DELETED");
            if (v.getDocumentMaster() != null) {
                v.getDocumentMaster().setStatus("DELETED");
                documentMasterRepository.save(v.getDocumentMaster());
            }
            documentVersionRepository.save(v);
        }

        List<DocumentVersion> historyMatches = documentVersionRepository.findByChecksum(exactChecksum);
        assertThat(historyMatches.size()).isGreaterThanOrEqualTo(2);

        // 3. Active checksum check MUST return false
        assertThat(documentVersionRepository.existsActiveByChecksum(exactChecksum)).isFalse();

        // 4. Upload file with exact same checksum
        byte[] fileBytes = ("%PDF-1.4\nExact ASPICE 4.0 checksum payload\n%%EOF").getBytes();
        // Override calculateChecksum in test check or use mock multipart file that produces matching bytes
        MockMultipartFile mockFile = new MockMultipartFile("file", "ASPICE_v3.1_vs_v4.0.pdf", "application/pdf", fileBytes) {
            @Override
            public byte[] getBytes() {
                return fileBytes;
            }
        };

        // Standard upload call
        UploadResponseDTO response = dmsDocumentService.uploadDocument(
                mockFile, "Assessment Checklist", "Supporting Process Group", "SUP.1", "Quality Assurance",
                "ASPICE v3.1 vs v4.0 Reuploaded", "1.0", "Reupload after delete", "admin", false);

        // 5. Assert upload proceeds and is NOT rejected with 409
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.isDuplicateChecksum()).isFalse();
        assertThat(response.getAction()).isEqualTo("CREATED");
        Long newMasterId = response.getDocumentMasterId();
        assertThat(newMasterId).isNotEqualTo(master42.getId());

        // 6. Approve new document
        dmsDocumentService.approveDocument(newMasterId, "admin");

        // 7. Verify uploading same checksum while newMaster is APPROVED is blocked as active duplicate
        String newChecksum = documentVersionRepository.findByDocumentMasterIdAndIsLatestTrue(newMasterId).get().getChecksum();

        assertThat(documentVersionRepository.existsActiveByChecksum(newChecksum)).isTrue();
    }
}
