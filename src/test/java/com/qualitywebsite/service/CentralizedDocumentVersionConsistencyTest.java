package com.qualitywebsite.service;

import com.qualitywebsite.dto.DocumentMasterDTO;
import com.qualitywebsite.dto.MasterListSearchResultDTO;
import com.qualitywebsite.entity.DocumentEntity;
import com.qualitywebsite.entity.DocumentMaster;
import com.qualitywebsite.entity.DocumentVersion;
import com.qualitywebsite.repository.DocumentMasterRepository;
import com.qualitywebsite.repository.DocumentRepository;
import com.qualitywebsite.repository.DocumentVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
public class CentralizedDocumentVersionConsistencyTest {

    @Autowired
    private DataInitializationService dataInitializationService;

    @Autowired
    private DmsMigrationService dmsMigrationService;

    @Autowired
    private DocumentService documentService;

    @Autowired
    private DmsDocumentService dmsDocumentService;

    @Autowired
    private MasterListService masterListService;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentMasterRepository documentMasterRepository;

    @Autowired
    private DocumentVersionRepository documentVersionRepository;

    @BeforeEach
    void setUp() {
        dataInitializationService.seedDocuments();
        dmsMigrationService.migrateToDatabaseStorage();
    }

    @Test
    @DisplayName("Review Point #4: Single Source of Truth — All views reflect DocumentMaster.currentVersion")
    void testSingleSourceOfTruth_AllViewsConsistent() {
        // 1. Verify ASPICE Assessment Checklist document
        Optional<DocumentMaster> masterOpt = documentMasterRepository.findAll().stream()
                .filter(m -> m.getDocumentName() != null && m.getDocumentName().equalsIgnoreCase("Generic Practices Checklist v4.0 (L2)"))
                .findFirst();
        assertThat(masterOpt).isPresent();
        DocumentMaster master = masterOpt.get();
        String currentVersion = master.getCurrentVersion();
        assertThat(currentVersion).isEqualTo("4.0");

        // 2. Verify DocumentEntity version (used by public endpoints & section-details.html)
        List<DocumentEntity> publicDocs = documentService.getAllDocuments();
        Optional<DocumentEntity> docEntityOpt = publicDocs.stream()
                .filter(d -> d.getDocumentName() != null && d.getDocumentName().equalsIgnoreCase("Generic Practices Checklist v4.0 (L2)"))
                .findFirst();
        assertThat(docEntityOpt).isPresent();
        assertThat(docEntityOpt.get().getVersion()).isEqualTo(currentVersion);

        // 3. Verify Search Results return the same version
        List<MasterListSearchResultDTO> searchResults = masterListService.searchMasterList("Generic Practices Checklist");
        assertThat(searchResults).isNotEmpty();
        Optional<MasterListSearchResultDTO> matchRes = searchResults.stream()
                .filter(r -> r.getDocumentName() != null && r.getDocumentName().equalsIgnoreCase("Generic Practices Checklist v4.0 (L2)"))
                .findFirst();
        if (matchRes.isPresent()) {
            assertThat(matchRes.get().getVersion()).isEqualTo(currentVersion);
        }

        // 4. Verify DmsDocumentService.searchAndFilter returns the same version for Admin Portal
        List<DocumentMasterDTO> adminDmsList = dmsDocumentService.searchAndFilter("Generic Practices", null, null);
        Optional<DocumentMasterDTO> adminDmsOpt = adminDmsList.stream()
                .filter(d -> d.getDocumentName() != null && d.getDocumentName().equalsIgnoreCase("Generic Practices Checklist v4.0 (L2)"))
                .findFirst();
        assertThat(adminDmsOpt).isPresent();
        assertThat(adminDmsOpt.get().getCurrentVersion()).isEqualTo(currentVersion);
    }

    @Test
    @DisplayName("Review Point #4: Lifecycle — New UNDER_REVIEW version does NOT alter public version until APPROVED")
    void testUploadNewVersion_PublicVersionRemainsUntilApproved() throws Exception {
        // Find an approved document
        DocumentMaster master = documentMasterRepository.findAll().stream()
                .filter(m -> "APPROVED".equalsIgnoreCase(m.getStatus()))
                .findFirst()
                .orElseThrow();

        String initialApprovedVersion = master.getCurrentVersion();
        Long masterId = master.getId();

        // Admin uploads a new version v5.0 (Status UNDER_REVIEW)
        byte[] fileContent = "%PDF-1.4 new version 5.0 bytes content".getBytes();
        dmsDocumentService.uploadNewVersion(masterId, fileContent, "Updated_Doc.pdf", "PDF", "application/pdf", "5.0", "New feature updates", "admin");

        // Reload master
        DocumentMaster reloadedMaster = documentMasterRepository.findById(masterId).orElseThrow();
        assertThat(reloadedMaster.getStatus()).isEqualTo("UNDER_REVIEW");

        // Public documents list MUST NOT contain the unapproved version
        List<DocumentEntity> publicDocs = documentService.getAllDocuments();
        boolean containsUnapproved = publicDocs.stream()
                .anyMatch(d -> d.getDocumentName().equalsIgnoreCase(reloadedMaster.getDocumentName()) && "5.0".equals(d.getVersion()));
        assertThat(containsUnapproved).isFalse();

        // Now Admin approves the document
        dmsDocumentService.approveDocument(masterId, "admin");

        // Reload master after approval
        DocumentMaster approvedMaster = documentMasterRepository.findById(masterId).orElseThrow();
        assertThat(approvedMaster.getStatus()).isEqualTo("APPROVED");
        assertThat(approvedMaster.getCurrentVersion()).isEqualTo("5.0");

        // Public documents list MUST NOW reflect the new approved version 5.0
        List<DocumentEntity> publicDocsAfterApprove = documentService.getAllDocuments();
        Optional<DocumentEntity> approvedDocOpt = publicDocsAfterApprove.stream()
                .filter(d -> d.getDocumentName().equalsIgnoreCase(approvedMaster.getDocumentName()))
                .findFirst();
        assertThat(approvedDocOpt).isPresent();
        assertThat(approvedDocOpt.get().getVersion()).isEqualTo("5.0");

        // Version history MUST retain both initial version and version 5.0
        List<DocumentVersion> history = documentVersionRepository.findByDocumentMasterIdOrderByUploadedDateDesc(masterId);
        assertThat(history.size()).isGreaterThanOrEqualTo(2);
        assertThat(history.stream().anyMatch(v -> "5.0".equals(v.getVersion()))).isTrue();
        assertThat(history.stream().anyMatch(v -> initialApprovedVersion.equals(v.getVersion()))).isTrue();
    }
}
