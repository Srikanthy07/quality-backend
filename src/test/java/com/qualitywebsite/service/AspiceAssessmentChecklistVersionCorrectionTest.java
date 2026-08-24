package com.qualitywebsite.service;

import com.qualitywebsite.entity.DocumentEntity;
import com.qualitywebsite.entity.DocumentMaster;
import com.qualitywebsite.entity.DocumentVersion;
import com.qualitywebsite.repository.DocumentRepository;
import com.qualitywebsite.repository.DocumentMasterRepository;
import com.qualitywebsite.repository.DocumentVersionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
public class AspiceAssessmentChecklistVersionCorrectionTest {

    @Autowired
    private DataInitializationService dataInitializationService;

    @Autowired
    private DmsMigrationService dmsMigrationService;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private DocumentMasterRepository documentMasterRepository;

    @Autowired
    private DocumentVersionRepository documentVersionRepository;

    @Test
    @DisplayName("Review Point #4: ASPICE Assessment Checklist Generic Practices Checklist v4.0 (L2) version is corrected to 4.0")
    void testGenericPracticesChecklistVersionIsFour() {
        dataInitializationService.seedDocuments();
        dmsMigrationService.migrateToDatabaseStorage();

        Optional<DocumentEntity> docEntityOpt = documentRepository.findById("SUP1-CHK-007");
        if (docEntityOpt.isEmpty()) {
            docEntityOpt = documentRepository.findAll().stream()
                    .filter(d -> d.getDocumentName() != null && d.getDocumentName().toLowerCase().contains("v4.0"))
                    .findFirst();
        }
        assertThat(docEntityOpt).isPresent();
        DocumentEntity entity = docEntityOpt.get();
        assertThat(entity.getVersion()).isEqualTo("4.0");

        Optional<DocumentMaster> masterOpt = documentMasterRepository.findByDocumentCode("SUP1-CHK-007");
        if (masterOpt.isEmpty()) {
            masterOpt = documentMasterRepository.findAll().stream()
                    .filter(m -> m.getDocumentName() != null && m.getDocumentName().toLowerCase().contains("v4.0"))
                    .findFirst();
        }
        assertThat(masterOpt).isPresent();
        DocumentMaster master = masterOpt.get();
        assertThat(master.getCurrentVersion()).isEqualTo("4.0");

        Optional<DocumentVersion> versionOpt = documentVersionRepository.findByDocumentMasterIdAndIsLatestTrue(master.getId());
        assertThat(versionOpt).isPresent();
        DocumentVersion version = versionOpt.get();
        assertThat(version.getMajorVersion()).isEqualTo(4);
        assertThat(version.getMinorVersion()).isEqualTo(0);
        assertThat(version.getVersion()).isEqualTo("4.0");
    }
}
