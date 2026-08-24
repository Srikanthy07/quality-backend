package com.qualitywebsite.controller;

import com.qualitywebsite.entity.DocumentMaster;
import com.qualitywebsite.entity.DocumentVersion;
import com.qualitywebsite.repository.DocumentMasterRepository;
import com.qualitywebsite.repository.DocumentVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:upload_error_testdb;DB_CLOSE_DELAY=-1;MODE=MySQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class AdminDocumentUploadErrorHandlingTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DocumentMasterRepository documentMasterRepository;

    @Autowired
    private DocumentVersionRepository documentVersionRepository;

    @BeforeEach
    void setUp() {
        documentVersionRepository.deleteAll();
        documentMasterRepository.deleteAll();
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("1. Valid Document Upload — succeeds with 200 OK")
    void testValidUpload_Succeeds() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "Test_Specification.pdf", "application/pdf", "%PDF-1.4 test pdf content".getBytes());

        mockMvc.perform(multipart("/api/admin/dms/upload")
                .file(file)
                .param("category", "ASPICE PRM")
                .param("processGroup", "Software Engineering")
                .param("processId", "SWE.1")
                .param("documentName", "SWE.1 Software Requirements Spec")
                .param("version", "1.0")
                .param("remarks", "Initial test upload")
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.action").value("CREATED"))
                .andExpect(jsonPath("$.version").value("1.0"));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("1b. Uploading a valid DOCX with version '2.0' succeeds and stores version '2.0' in document_version table")
    void testValidDocxUploadWithVersion2_0_StoresVersionInDatabase() throws Exception {
        byte[] docxBytes = new byte[]{0x50, 0x4B, 0x03, 0x04, 0x14, 0x00, 0x06, 0x00}; // ZIP magic bytes for DOCX
        MockMultipartFile docxFile = new MockMultipartFile(
                "file", "System_Architecture_Template.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", docxBytes);

        mockMvc.perform(multipart("/api/admin/dms/upload")
                .file(docxFile)
                .param("category", "ASPICE PRM")
                .param("processGroup", "System Engineering")
                .param("processId", "SYS.3")
                .param("documentName", "SYS.3 System Architecture Template")
                .param("version", "2.0")
                .param("remarks", "Version 2.0 Upload Test")
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.action").value("CREATED"))
                .andExpect(jsonPath("$.version").value("2.0"));

        // Verify database persistence
        DocumentMaster master = documentMasterRepository
                .findByProcessIdIgnoreCaseAndCategoryIgnoreCaseAndDocumentNameIgnoreCase("SYS.3", "ASPICE PRM", "SYS.3 System Architecture Template")
                .orElseThrow();
        assertEquals("2.0", master.getCurrentVersion());

        DocumentVersion version = documentVersionRepository
                .findByDocumentMasterIdAndIsLatestTrue(master.getId())
                .orElseThrow();
        assertEquals("2.0", version.getVersion());
        assertEquals(2, version.getMajorVersion());
        assertEquals(0, version.getMinorVersion());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("2. Missing Document Name — returns specific required-field error message")
    void testMissingDocumentName_ReturnsSpecificError() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "Test_Specification.pdf", "application/pdf", "%PDF-1.4 test pdf content".getBytes());

        mockMvc.perform(multipart("/api/admin/dms/upload")
                .file(file)
                .param("category", "ASPICE PRM")
                .param("processId", "SWE.1")
                .param("documentName", "   ")
                .param("version", "1.0")
                .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Document name is required."));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("3. Missing Process ID — returns specific required-field error message")
    void testMissingProcessId_ReturnsSpecificError() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "Test_Specification.pdf", "application/pdf", "%PDF-1.4 test pdf content".getBytes());

        mockMvc.perform(multipart("/api/admin/dms/upload")
                .file(file)
                .param("category", "ASPICE PRM")
                .param("processId", "")
                .param("documentName", "SWE.1 Software Requirements Spec")
                .param("version", "1.0")
                .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Process ID is required."));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("4. Missing Category — returns specific required-field error message")
    void testMissingCategory_ReturnsSpecificError() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "Test_Specification.pdf", "application/pdf", "%PDF-1.4 test pdf content".getBytes());

        mockMvc.perform(multipart("/api/admin/dms/upload")
                .file(file)
                .param("category", "")
                .param("processId", "SWE.1")
                .param("documentName", "SWE.1 Software Requirements Spec")
                .param("version", "1.0")
                .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Please select a category."));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("5. Multiple Missing Fields — returns generic fill all required fields message")
    void testMultipleMissingFields_ReturnsFillAllFieldsError() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "Test_Specification.pdf", "application/pdf", "%PDF-1.4 test pdf content".getBytes());

        mockMvc.perform(multipart("/api/admin/dms/upload")
                .file(file)
                .param("category", "")
                .param("processId", "")
                .param("documentName", "")
                .param("version", "1.0")
                .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Please fill in all required fields."));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("6. No File Selected — returns specific file-required error message")
    void testNoFileSelected_ReturnsFileRequiredError() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "", "application/pdf", new byte[0]);

        mockMvc.perform(multipart("/api/admin/dms/upload")
                .file(emptyFile)
                .param("category", "ASPICE PRM")
                .param("processId", "SWE.1")
                .param("documentName", "SWE.1 Software Requirements Spec")
                .param("version", "1.0")
                .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Please select a document to upload."));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("7. Unsupported File Type — returns specific invalid file format error message")
    void testUnsupportedFileType_ReturnsInvalidFileFormatError() throws Exception {
        MockMultipartFile unsupportedFile = new MockMultipartFile(
                "file", "malicious_script.exe", "application/x-msdownload", "MZ executable bytes".getBytes());

        mockMvc.perform(multipart("/api/admin/dms/upload")
                .file(unsupportedFile)
                .param("category", "ASPICE PRM")
                .param("processId", "SWE.1")
                .param("documentName", "Executable Test File")
                .param("version", "1.0")
                .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid file format. Please upload a supported document type."));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("8. Identical Duplicate File Content — returns specific duplicate file error message")
    void testDuplicateFileContent_ReturnsDuplicateFileError() throws Exception {
        byte[] fileBytes = "%PDF-1.4 duplicate test content".getBytes();
        MockMultipartFile file1 = new MockMultipartFile("file", "Doc.pdf", "application/pdf", fileBytes);

        // Upload first time
        mockMvc.perform(multipart("/api/admin/dms/upload")
                .file(file1)
                .param("category", "ASPICE PRM")
                .param("processId", "SWE.1")
                .param("documentName", "Duplicate Document Test")
                .param("version", "1.0")
                .with(csrf()))
                .andExpect(status().isOk());

        // Upload exact same file second time
        MockMultipartFile file2 = new MockMultipartFile("file", "Doc.pdf", "application/pdf", fileBytes);
        mockMvc.perform(multipart("/api/admin/dms/upload")
                .file(file2)
                .param("category", "ASPICE PRM")
                .param("processId", "SWE.1")
                .param("documentName", "Duplicate Document Test")
                .param("version", "1.0")
                .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.action").value("REJECTED"))
                .andExpect(jsonPath("$.duplicateChecksum").value(true))
                .andExpect(jsonPath("$.message").value("Duplicate file detected. This document already exists with the same file content."));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("8b. Renamed file with identical content returns 409 duplicate and does NOT create a new document_version row")
    void testRenamedFileIdenticalContent_Returns409DuplicateAndNoNewVersionRow() throws Exception {
        byte[] sharedBytes = "%PDF-1.4 shared document bytes for duplicate testing".getBytes();
        MockMultipartFile originalFile = new MockMultipartFile("file", "Original_Document.pdf", "application/pdf", sharedBytes);

        // First upload succeeds
        mockMvc.perform(multipart("/api/admin/dms/upload")
                .file(originalFile)
                .param("category", "ASPICE PRM")
                .param("processGroup", "Software Engineering")
                .param("processId", "SWE.2")
                .param("documentName", "Original Software Design")
                .param("version", "1.0")
                .with(csrf()))
                .andExpect(status().isOk());

        long countBefore = documentVersionRepository.count();

        // Second upload with DIFFERENT filename and DIFFERENT documentName, but IDENTICAL content
        MockMultipartFile renamedFile = new MockMultipartFile("file", "Renamed_Copied_Document.pdf", "application/pdf", sharedBytes);
        mockMvc.perform(multipart("/api/admin/dms/upload")
                .file(renamedFile)
                .param("category", "ASPICE PRM")
                .param("processGroup", "System Engineering")
                .param("processId", "SYS.2")
                .param("documentName", "Renamed System Design")
                .param("version", "1.0")
                .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Duplicate file detected. This document already exists with the same file content."));

        // Verify NO new DocumentVersion row was inserted
        assertEquals(countBefore, documentVersionRepository.count());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("8c. Same filename with DIFFERENT content is NOT rejected as duplicate by checksum")
    void testSameFilenameDifferentContent_NotDuplicateByChecksum() throws Exception {
        byte[] bytesV1 = "%PDF-1.4 version 1 content bytes".getBytes();
        MockMultipartFile fileV1 = new MockMultipartFile("file", "Common_Name.pdf", "application/pdf", bytesV1);

        mockMvc.perform(multipart("/api/admin/dms/upload")
                .file(fileV1)
                .param("category", "ASPICE PRM")
                .param("processGroup", "Software Engineering")
                .param("processId", "SWE.3")
                .param("documentName", "Common Name Spec")
                .param("version", "1.0")
                .with(csrf()))
                .andExpect(status().isOk());

        byte[] bytesV2 = "%PDF-1.4 version 2 completely different content bytes".getBytes();
        MockMultipartFile fileV2 = new MockMultipartFile("file", "Common_Name.pdf", "application/pdf", bytesV2);

        // Uploading same filename with DIFFERENT content should NOT return 409 duplicate
        mockMvc.perform(multipart("/api/admin/dms/upload")
                .file(fileV2)
                .param("category", "ASPICE PRM")
                .param("processGroup", "Software Engineering")
                .param("processId", "SWE.3")
                .param("documentName", "Common Name Spec")
                .param("version", "2.0")
                .param("confirmNewVersion", "true")
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("8d. Multiple database records with same checksum — returns 409 duplicate without NonUniqueResultException")
    void testMultipleDatabaseRecordsWithSameChecksum_HandledWithoutNonUniqueResultException() throws Exception {
        byte[] sharedBytes = "%PDF-1.4 shared content across multiple versions".getBytes();
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(sharedBytes);
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) hexString.append('0');
            hexString.append(hex);
        }
        String sharedChecksum = hexString.toString();

        // Pre-create master
        DocumentMaster master = documentMasterRepository.save(DocumentMaster.builder()
                .documentCode("MULTI-001")
                .category("ASPICE PRM")
                .processGroup("Software")
                .processId("SWE.1")
                .processName("SWE.1 Process")
                .documentName("Multi Version Test Doc")
                .currentVersion("3.0")
                .status("APPROVED")
                .createdBy("admin")
                .createdDate(LocalDateTime.now())
                .updatedDate(LocalDateTime.now())
                .build());

        // Pre-create 3 DocumentVersion records in the database with the EXACT SAME checksum
        for (int i = 1; i <= 3; i++) {
            documentVersionRepository.save(DocumentVersion.builder()
                    .documentMaster(master)
                    .version(i + ".0")
                    .majorVersion(i)
                    .minorVersion(0)
                    .fileName("Doc_v" + i + ".pdf")
                    .fileType("PDF")
                    .mimeType("application/pdf")
                    .fileSize(100L)
                    .fileData(sharedBytes)
                    .checksum(sharedChecksum)
                    .uploadedBy("admin")
                    .uploadedDate(LocalDateTime.now())
                    .approvalStatus("APPROVED")
                    .isLatest(i == 3)
                    .build());
        }

        assertEquals(3, documentVersionRepository.findByChecksum(sharedChecksum).size());

        // Now attempt to upload a file with the same content (same checksum)
        MockMultipartFile fileToUpload = new MockMultipartFile("file", "New_Upload.pdf", "application/pdf", sharedBytes);

        mockMvc.perform(multipart("/api/admin/dms/upload")
                .file(fileToUpload)
                .param("category", "ASPICE PRM")
                .param("processGroup", "Software Engineering")
                .param("processId", "SWE.1")
                .param("documentName", "Multi Version Test Doc")
                .param("version", "4.0")
                .with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Duplicate file detected. This document already exists with the same file content."));

        // Verify count of versions remains 3 (no 4th version saved)
        assertEquals(3, documentVersionRepository.findByChecksum(sharedChecksum).size());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("9. Duplicate Document Version — returns specific duplicate version error message")
    void testDuplicateVersion_ReturnsDuplicateVersionError() throws Exception {
        // Create initial document master & v1.0 version
        DocumentMaster master = DocumentMaster.builder()
                .documentCode("SWE1-DOC-001")
                .category("ASPICE PRM")
                .processGroup("Software")
                .processId("SWE.1")
                .processName("SWE.1 Process")
                .documentName("Version Test Doc")
                .currentVersion("1.0")
                .status("APPROVED")
                .createdBy("admin")
                .createdDate(LocalDateTime.now())
                .updatedDate(LocalDateTime.now())
                .build();
        master = documentMasterRepository.save(master);

        DocumentVersion v1 = DocumentVersion.builder()
                .documentMaster(master)
                .majorVersion(1)
                .minorVersion(0)
                .fileName("Doc_v1.pdf")
                .fileType("PDF")
                .mimeType("application/pdf")
                .fileSize(100L)
                .fileData("%PDF-1.4 content v1".getBytes())
                .checksum("checksum-v1")
                .uploadedBy("admin")
                .uploadedDate(LocalDateTime.now())
                .approvalStatus("APPROVED")
                .isLatest(true)
                .build();
        documentVersionRepository.save(v1);

        // Upload new version explicitly requesting v1.0
        MockMultipartFile newFile = new MockMultipartFile("file", "Doc_v1_new.pdf", "application/pdf", "%PDF-1.4 different content v2".getBytes());
        mockMvc.perform(multipart("/api/admin/dms/documents/" + master.getId() + "/version")
                .file(newFile)
                .param("version", "1.0")
                .param("remarks", "Trying duplicate version")
                .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(containsString("Document version")))
                .andExpect(jsonPath("$.message").value(containsString("already exists. Please enter a different version number.")));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("10. Invalid Version Format — returns specific version validation error message")
    void testInvalidVersionFormat_ReturnsInvalidVersionError() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "Test_Specification.pdf", "application/pdf", "%PDF-1.4 test pdf content".getBytes());

        mockMvc.perform(multipart("/api/admin/dms/upload")
                .file(file)
                .param("category", "ASPICE PRM")
                .param("processId", "SWE.1")
                .param("documentName", "SWE.1 Spec")
                .param("version", "invalid-version-1.a")
                .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Please enter a valid document version number, such as 1.0 or 2.1."));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("10a. Blank Version — returns Document version number is required.")
    void testBlankVersion_ReturnsRequiredMessage() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "Test_Blank.pdf", "application/pdf", "%PDF-1.4 blank version pdf content".getBytes());

        mockMvc.perform(multipart("/api/admin/dms/upload")
                .file(file)
                .param("category", "ASPICE PRM")
                .param("processId", "SWE.1")
                .param("documentName", "SWE.1 Blank Version Test")
                .param("version", "")
                .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Document version number is required."));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("10b. Version abc or 1.x — returns Please enter a valid document version number")
    void testVersionAbcAnd1x_ReturnsInvalidMessage() throws Exception {
        MockMultipartFile file1 = new MockMultipartFile("file", "Test_Abc.pdf", "application/pdf", "%PDF-1.4 content abc".getBytes());
        mockMvc.perform(multipart("/api/admin/dms/upload")
                .file(file1)
                .param("category", "ASPICE PRM")
                .param("processId", "SWE.1")
                .param("documentName", "SWE.1 Abc Test")
                .param("version", "abc")
                .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Please enter a valid document version number, such as 1.0 or 2.1."));

        MockMultipartFile file2 = new MockMultipartFile("file", "Test_1x.pdf", "application/pdf", "%PDF-1.4 content 1x".getBytes());
        mockMvc.perform(multipart("/api/admin/dms/upload")
                .file(file2)
                .param("category", "ASPICE PRM")
                .param("processId", "SWE.1")
                .param("documentName", "SWE.1 1x Test")
                .param("version", "1.x")
                .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Please enter a valid document version number, such as 1.0 or 2.1."));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("10c. Custom Version 10.5 — accepted and persisted correctly in MySQL")
    void testVersion10_5_AcceptedAndPersisted() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "Test_10_5.pdf", "application/pdf", "%PDF-1.4 content 10.5".getBytes());
        mockMvc.perform(multipart("/api/admin/dms/upload")
                .file(file)
                .param("category", "ASPICE PRM")
                .param("processGroup", "Software Engineering")
                .param("processId", "SWE.4")
                .param("documentName", "SWE.4 Unit Test Spec")
                .param("version", "10.5")
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.version").value("10.5"));

        List<DocumentVersion> list = documentVersionRepository.findAll();
        DocumentVersion v10_5 = list.stream().filter(v -> "10.5".equals(v.getVersion())).findFirst().orElseThrow();
        assertEquals(10, v10_5.getMajorVersion());
        assertEquals(5, v10_5.getMinorVersion());
        DocumentMaster master105 = documentMasterRepository.findById(v10_5.getDocumentMaster().getId()).orElseThrow();
        assertEquals("10.5", master105.getCurrentVersion());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("10d. Existing document version 1.0 -> Upload new version 2.5 preserves version history")
    void testExistingDocUploadNewVersion2_5_PreservesHistory() throws Exception {
        MockMultipartFile fileV1 = new MockMultipartFile("file", "Doc_v1_0.pdf", "application/pdf", "%PDF-1.4 version 1.0 content".getBytes());
        mockMvc.perform(multipart("/api/admin/dms/upload")
                .file(fileV1)
                .param("category", "ASPICE PRM")
                .param("processGroup", "Software Engineering")
                .param("processId", "SWE.5")
                .param("documentName", "SWE.5 Integration Spec")
                .param("version", "1.0")
                .with(csrf()))
                .andExpect(status().isOk());

        DocumentMaster master = documentMasterRepository.findByProcessIdIgnoreCaseAndCategoryIgnoreCaseAndDocumentNameIgnoreCase("SWE.5", "ASPICE PRM", "SWE.5 Integration Spec").orElseThrow();

        MockMultipartFile fileV25 = new MockMultipartFile("file", "Doc_v2_5.pdf", "application/pdf", "%PDF-1.4 version 2.5 content".getBytes());
        mockMvc.perform(multipart("/api/admin/dms/documents/" + master.getId() + "/version")
                .file(fileV25)
                .param("version", "2.5")
                .param("remarks", "Major revision 2.5")
                .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.version").value("2.5"));

        List<DocumentVersion> history = documentVersionRepository.findByDocumentMasterIdOrderByUploadedDateDesc(master.getId());
        assertEquals(2, history.size());
        assertTrue(history.stream().anyMatch(v -> "1.0".equals(v.getVersion())));
        assertTrue(history.stream().anyMatch(v -> "2.5".equals(v.getVersion())));
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("11. Security Check — Error responses contain no SQL, stack trace, or internal details")
    void testSecurity_NoErrorDetailsLeaked() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "Test_Specification.pdf", "application/pdf", "%PDF-1.4 test pdf content".getBytes());

        mockMvc.perform(multipart("/api/admin/dms/upload")
                .file(file)
                .param("category", "")
                .param("processId", "")
                .param("documentName", "")
                .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.stackTrace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.sql").doesNotExist())
                .andExpect(content().string(not(containsString("SQLException"))))
                .andExpect(content().string(not(containsString("org.h2"))))
                .andExpect(content().string(not(containsString("com.qualitywebsite"))));
    }
}
