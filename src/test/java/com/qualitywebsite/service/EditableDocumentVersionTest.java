package com.qualitywebsite.service;

import com.qualitywebsite.dto.UploadResponseDTO;
import com.qualitywebsite.entity.DocumentMaster;
import com.qualitywebsite.repository.DocumentMasterRepository;
import com.qualitywebsite.repository.DocumentVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
public class EditableDocumentVersionTest {

    @Autowired
    private DmsDocumentService dmsDocumentService;

    @Autowired
    private DocumentMasterRepository documentMasterRepository;

    @Autowired
    private DocumentVersionRepository documentVersionRepository;

    private byte[] createPdfBytes(String content) {
        return ("%PDF-1.5\n%EOF\n" + content).getBytes(StandardCharsets.UTF_8);
    }

    @BeforeEach
    void setUp() {
        documentVersionRepository.deleteAll();
        documentMasterRepository.deleteAll();
    }

    @Test
    @DisplayName("Case 1 & 2: Custom version number (e.g. 2.0) is respected for new document")
    void testNewDocumentCustomVersion() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", createPdfBytes("Content A"));
        UploadResponseDTO response = dmsDocumentService.uploadDocument(
                file, "ASPICE PRM", "General", "TEST.1", "Test Process", "Test Doc 1", "2.0", "Remarks", "admin", false
        );

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getVersion()).isEqualTo("2.0");

        DocumentMaster master = documentMasterRepository.findById(response.getDocumentMasterId()).orElseThrow();
        assertThat(master.getCurrentVersion()).isEqualTo("2.0");
    }

    @Test
    @DisplayName("Case 3 & 4: Uploading new version with custom version number (e.g. 2.5)")
    void testNewVersionCustomVersion() throws Exception {
        MockMultipartFile file1 = new MockMultipartFile("file", "test.pdf", "application/pdf", createPdfBytes("Initial content"));
        UploadResponseDTO resp1 = dmsDocumentService.uploadDocument(
                file1, "ASPICE PRM", "General", "TEST.2", "Test Process", "Test Doc 2", "1.0", "Remarks", "admin", false
        );

        MockMultipartFile file2 = new MockMultipartFile("file", "test.pdf", "application/pdf", createPdfBytes("Updated content"));
        UploadResponseDTO resp2 = dmsDocumentService.uploadDocument(
                file2, "ASPICE PRM", "General", "TEST.2", "Test Process", "Test Doc 2", "2.5", "Remarks", "admin", true
        );

        assertThat(resp2.isSuccess()).isTrue();
        assertThat(resp2.getVersion()).isEqualTo("2.5");

        DocumentMaster master = documentMasterRepository.findById(resp2.getDocumentMasterId()).orElseThrow();
        assertThat(master.getCurrentVersion()).isEqualTo("2.5");
    }

    @Test
    @DisplayName("Case 5: Rejects duplicate version number for the same document")
    void testDuplicateVersionRejection() throws Exception {
        MockMultipartFile file1 = new MockMultipartFile("file", "test.pdf", "application/pdf", createPdfBytes("Content V1"));
        UploadResponseDTO resp1 = dmsDocumentService.uploadDocument(
                file1, "ASPICE PRM", "General", "TEST.3", "Test Process", "Test Doc 3", "1.0", "Remarks", "admin", false
        );

        MockMultipartFile file2 = new MockMultipartFile("file", "test.pdf", "application/pdf", createPdfBytes("Content V2"));
        assertThatThrownBy(() -> dmsDocumentService.uploadDocument(
                file2, "ASPICE PRM", "General", "TEST.3", "Test Process", "Test Doc 3", "1.0", "Remarks", "admin", true
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Document version 1.0 already exists");
    }

    @Test
    @DisplayName("Case 6: Identical file content is rejected as duplicate file even if different version is entered")
    void testSameFileContentDuplicateCheckFirst() throws Exception {
        byte[] content = createPdfBytes("Identical content");
        MockMultipartFile file1 = new MockMultipartFile("file", "test.pdf", "application/pdf", content);
        UploadResponseDTO resp1 = dmsDocumentService.uploadDocument(
                file1, "ASPICE PRM", "General", "TEST.4", "Test Process", "Test Doc 4", "1.0", "Remarks", "admin", false
        );

        MockMultipartFile file2 = new MockMultipartFile("file", "test.pdf", "application/pdf", content);
        UploadResponseDTO resp2 = dmsDocumentService.uploadDocument(
                file2, "ASPICE PRM", "General", "TEST.4", "Test Process", "Test Doc 4", "2.0", "Remarks", "admin", true
        );

        assertThat(resp2.isSuccess()).isFalse();
        assertThat(resp2.getMessage()).contains("Duplicate file detected");
    }

    @Test
    @DisplayName("Case 7 & 8: Invalid or blank version number is rejected")
    void testInvalidAndBlankVersionValidation() {
        MockMultipartFile file = new MockMultipartFile("file", "test.pdf", "application/pdf", createPdfBytes("Content"));

        assertThatThrownBy(() -> dmsDocumentService.uploadDocument(
                file, "ASPICE PRM", "General", "TEST.5", "Test Process", "Test Doc 5", "abc", "Remarks", "admin", false
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Please enter a valid document version number");

        assertThatThrownBy(() -> dmsDocumentService.uploadDocument(
                file, "ASPICE PRM", "General", "TEST.6", "Test Process", "Test Doc 6", "", "Remarks", "admin", false
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Document version number is required");
    }
}
