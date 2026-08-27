package com.qualitywebsite.controller;

import com.qualitywebsite.entity.DocumentMaster;
import com.qualitywebsite.entity.DocumentVersion;
import com.qualitywebsite.repository.DocumentMasterRepository;
import com.qualitywebsite.repository.DocumentVersionRepository;
import com.qualitywebsite.service.DocumentReconciliationService;
import com.qualitywebsite.service.DocumentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import org.springframework.security.test.context.support.WithMockUser;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "analytics.enabled=true",
    "spring.datasource.url=jdbc:h2:mem:public_dms_dl_testdb;DB_CLOSE_DELAY=-1;MODE=MySQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class PublicDmsDownloadTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DocumentMasterRepository documentMasterRepository;

    @Autowired
    private DocumentVersionRepository documentVersionRepository;

    @Autowired
    private DocumentService documentService;

    @Autowired
    private DocumentReconciliationService documentReconciliationService;

    @BeforeEach
    void setUp() {
        documentVersionRepository.deleteAll();
        documentMasterRepository.deleteAll();
    }

    @Test
    @Transactional
    void test1_ApprovedXlsxDownloadsSuccessfully() throws Exception {
        DocumentMaster master = DocumentMaster.builder()
                .documentCode("SUP1-ASSE-ASPICE-98816")
                .processId("SUP.1")
                .processName("Quality Assurance")
                .processGroup("Support Processes")
                .category("Quality Manual")
                .documentName("ASPICE v3.1 vs v4.0")
                .status("APPROVED")
                .currentVersion("1.0")
                .build();
        master = documentMasterRepository.save(master);

        byte[] fakeXlsxBytes = new byte[]{0x50, 0x4B, 0x03, 0x04, 0x10, 0x20}; // PK ZIP header
        DocumentVersion version = DocumentVersion.builder()
                .documentMaster(master)
                .version("1.0")
                .fileName("ASPICE v3.1 vs v4.0.xlsx")
                .fileType("XLSX")
                .mimeType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .fileSize((long) fakeXlsxBytes.length)
                .fileData(fakeXlsxBytes)
                .checksum("sha256_" + UUID.randomUUID())
                .approvalStatus("APPROVED")
                .isLatest(true)
                .build();
        version = documentVersionRepository.save(version);

        mockMvc.perform(get("/api/public/dms/download/" + version.getId()).secure(true))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("ASPICE v3.1 vs v4.0.xlsx")))
                .andExpect(content().bytes(fakeXlsxBytes));
    }

    @Test
    @Transactional
    void test2_SameNamePdfAndXlsxRemainIsolated() throws Exception {
        // Master 1: PDF version
        DocumentMaster pdfMaster = DocumentMaster.builder()
                .documentCode("CODE-PDF-001")
                .processId("SUP.1")
                .category("Quality Manual")
                .documentName("ASPICE v3.1 vs v4.0")
                .status("APPROVED")
                .build();
        pdfMaster = documentMasterRepository.save(pdfMaster);

        byte[] pdfBytes = "%PDF-1.4 test".getBytes();
        DocumentVersion pdfVer = DocumentVersion.builder()
                .documentMaster(pdfMaster)
                .version("1.0")
                .fileName("ASPICE v3.1 vs v4.0.pdf")
                .fileType("PDF")
                .mimeType("application/pdf")
                .fileSize((long) pdfBytes.length)
                .fileData(pdfBytes)
                .checksum("sha256_pdf_" + UUID.randomUUID())
                .approvalStatus("APPROVED")
                .isLatest(true)
                .build();
        pdfVer = documentVersionRepository.save(pdfVer);

        // Master 2: XLSX version
        DocumentMaster xlsxMaster = DocumentMaster.builder()
                .documentCode("CODE-XLSX-001")
                .processId("SUP.1")
                .category("Quality Manual")
                .documentName("ASPICE v3.1 vs v4.0")
                .status("APPROVED")
                .build();
        xlsxMaster = documentMasterRepository.save(xlsxMaster);

        byte[] xlsxBytes = "XLSX payload".getBytes();
        DocumentVersion xlsxVer = DocumentVersion.builder()
                .documentMaster(xlsxMaster)
                .version("1.0")
                .fileName("ASPICE v3.1 vs v4.0.xlsx")
                .fileType("XLSX")
                .mimeType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .fileSize((long) xlsxBytes.length)
                .fileData(xlsxBytes)
                .checksum("sha256_xlsx_" + UUID.randomUUID())
                .approvalStatus("APPROVED")
                .isLatest(true)
                .build();
        xlsxVer = documentVersionRepository.save(xlsxVer);

        // Download XLSX version
        mockMvc.perform(get("/api/public/dms/download/" + xlsxVer.getId()).secure(true))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(content().bytes(xlsxBytes));

        // Download PDF version
        mockMvc.perform(get("/api/public/dms/download/" + pdfVer.getId()).secure(true))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"))
                .andExpect(content().bytes(pdfBytes));
    }

    @Test
    @Transactional
    void test3_DeletedVersionCannotBeDownloaded() throws Exception {
        DocumentMaster master = DocumentMaster.builder()
                .documentCode("CODE-DEL-001")
                .processId("SUP.1")
                .category("Guidelines")
                .documentName("Deleted Spec")
                .status("DELETED")
                .build();
        master = documentMasterRepository.save(master);

        DocumentVersion ver = DocumentVersion.builder()
                .documentMaster(master)
                .version("1.0")
                .fileName("Deleted Spec.pdf")
                .fileType("PDF")
                .mimeType("application/pdf")
                .fileSize(100L)
                .fileData("deleted data".getBytes())
                .checksum("sha256_del_" + UUID.randomUUID())
                .approvalStatus("DELETED")
                .isLatest(true)
                .build();
        ver = documentVersionRepository.save(ver);

        mockMvc.perform(get("/api/public/dms/download/" + ver.getId()).secure(true))
                .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void test4_UnapprovedVersionCannotBeDownloaded() throws Exception {
        DocumentMaster master = DocumentMaster.builder()
                .documentCode("CODE-PEND-001")
                .processId("SUP.1")
                .category("Guidelines")
                .documentName("Pending Spec")
                .status("UNDER_REVIEW")
                .build();
        master = documentMasterRepository.save(master);

        DocumentVersion ver = DocumentVersion.builder()
                .documentMaster(master)
                .version("1.0")
                .fileName("Pending Spec.pdf")
                .fileType("PDF")
                .mimeType("application/pdf")
                .fileSize(100L)
                .fileData("pending data".getBytes())
                .checksum("sha256_pend_" + UUID.randomUUID())
                .approvalStatus("UNDER_REVIEW")
                .isLatest(true)
                .build();
        ver = documentVersionRepository.save(ver);

        mockMvc.perform(get("/api/public/dms/download/" + ver.getId()).secure(true))
                .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void test5_CorrectFilenameAndMimeTypeHeadersReturned() throws Exception {
        DocumentMaster master = DocumentMaster.builder()
                .documentCode("CODE-HEADER-001")
                .processId("MAN.3")
                .category("Management")
                .documentName("Test Document")
                .status("APPROVED")
                .build();
        master = documentMasterRepository.save(master);

        byte[] data = "some doc content".getBytes();
        DocumentVersion ver = DocumentVersion.builder()
                .documentMaster(master)
                .version("1.0")
                .fileName("Test Document.docx")
                .fileType("DOCX")
                .mimeType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                .fileSize((long) data.length)
                .fileData(data)
                .checksum("sha256_hdr_" + UUID.randomUUID())
                .approvalStatus("APPROVED")
                .isLatest(true)
                .build();
        ver = documentVersionRepository.save(ver);

        mockMvc.perform(get("/api/public/dms/download/" + ver.getId()).secure(true))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("Test Document.docx")));
    }

    @Test
    @Transactional
    void test6_LegacyPathDoesNotReturnWrongFile() throws Exception {
        // Stale legacy request for a PDF when only XLSX exists in DMS
        mockMvc.perform(get("/documents/aspice-prm/sup/sup1/aspice_v3.1_vs_v4.0.pdf").secure(true))
                .andExpect(status().isNotFound());
    }

    @Test
    @Transactional
    void test7_PublicPageUsesDmsVersionId() {
        DocumentMaster master = DocumentMaster.builder()
                .documentCode("SUP1-ASSE-ASPICE-98816")
                .processId("SUP.1")
                .processName("Quality Assurance")
                .processGroup("Support Processes")
                .category("Quality Manual")
                .documentName("ASPICE v3.1 vs v4.0")
                .status("APPROVED")
                .currentVersion("1.0")
                .build();
        master = documentMasterRepository.save(master);

        byte[] fakeXlsxBytes = new byte[]{0x50, 0x4B, 0x03, 0x04};
        DocumentVersion version = DocumentVersion.builder()
                .documentMaster(master)
                .version("1.0")
                .fileName("ASPICE v3.1 vs v4.0.xlsx")
                .fileType("XLSX")
                .mimeType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .fileSize((long) fakeXlsxBytes.length)
                .fileData(fakeXlsxBytes)
                .checksum("sha256_pub_" + UUID.randomUUID())
                .approvalStatus("APPROVED")
                .isLatest(true)
                .build();
        version = documentVersionRepository.save(version);

        List<com.qualitywebsite.entity.DocumentEntity> docs = documentService.getAllDocuments();
        assertFalse(docs.isEmpty());

        com.qualitywebsite.entity.DocumentEntity match = docs.stream()
                .filter(d -> "ASPICE v3.1 vs v4.0".equalsIgnoreCase(d.getDocumentName()))
                .findFirst()
                .orElseThrow();

        assertEquals("/api/public/dms/download/" + version.getId(), match.getFilePath(),
                "Public document filePath MUST point to the DMS Version endpoint");
        assertEquals("XLSX", match.getFileType(), "Public document fileType MUST match the approved version fileType");
    }

    @Test
    @Transactional
    void test8_ApplicationRestartPreservesApprovedDownload() throws Exception {
        DocumentMaster master = DocumentMaster.builder()
                .documentCode("CODE-RESTART-001")
                .processId("SWE.1")
                .category("Processes")
                .documentName("Software Spec")
                .status("APPROVED")
                .build();
        master = documentMasterRepository.save(master);

        byte[] content = "Restart Test Payload".getBytes();
        DocumentVersion ver = DocumentVersion.builder()
                .documentMaster(master)
                .version("1.0")
                .fileName("Software Spec.pdf")
                .fileType("PDF")
                .mimeType("application/pdf")
                .fileSize((long) content.length)
                .fileData(content)
                .checksum("sha256_rst_" + UUID.randomUUID())
                .approvalStatus("APPROVED")
                .isLatest(true)
                .build();
        ver = documentVersionRepository.save(ver);

        // Simulate application restart
        documentReconciliationService.runOneTimeReconciliation();

        mockMvc.perform(get("/api/public/dms/download/" + ver.getId()).secure(true))
                .andExpect(status().isOk())
                .andExpect(content().bytes(content));
    }

    @Test
    @Transactional
    void test9_PendingNewVersionDoesNotHideApprovedPreviousVersion() {
        DocumentMaster master = DocumentMaster.builder()
                .documentCode("CODE-VERSIONS-001")
                .processId("SUP.1")
                .category("Quality Manual")
                .documentName("Multi-Version Document")
                .status("APPROVED")
                .currentVersion("2.0")
                .build();
        master = documentMasterRepository.save(master);

        // Version 1.0 (APPROVED, is_latest=false)
        DocumentVersion v1 = DocumentVersion.builder()
                .documentMaster(master)
                .version("1.0")
                .fileName("Multi_v1.pdf")
                .fileType("PDF")
                .mimeType("application/pdf")
                .fileSize(100L)
                .fileData("v1 data".getBytes())
                .checksum("sha256_v1_" + UUID.randomUUID())
                .approvalStatus("APPROVED")
                .isLatest(false)
                .build();
        v1 = documentVersionRepository.save(v1);

        // Version 2.0 (PENDING / UNDER_REVIEW, is_latest=true)
        DocumentVersion v2 = DocumentVersion.builder()
                .documentMaster(master)
                .version("2.0")
                .fileName("Multi_v2.pdf")
                .fileType("PDF")
                .mimeType("application/pdf")
                .fileSize(200L)
                .fileData("v2 data".getBytes())
                .checksum("sha256_v2_" + UUID.randomUUID())
                .approvalStatus("UNDER_REVIEW")
                .isLatest(true)
                .build();
        documentVersionRepository.save(v2);

        // Public documents API should resolve to approved Version 1.0, NOT pending Version 2.0
        List<com.qualitywebsite.entity.DocumentEntity> docs = documentService.getAllDocuments();
        com.qualitywebsite.entity.DocumentEntity match = docs.stream()
                .filter(d -> "Multi-Version Document".equalsIgnoreCase(d.getDocumentName()))
                .findFirst()
                .orElseThrow();

        assertEquals("/api/public/dms/download/" + v1.getId(), match.getFilePath(),
                "Public document MUST resolve to the latest APPROVED version (v1.0), not unapproved v2.0");
        assertEquals("1.0", match.getVersion());
    }

    @Test
    @Transactional
    void test10_HeadRequestSupportedForPublicDownload() throws Exception {
        DocumentMaster master = DocumentMaster.builder()
                .documentCode("CODE-HEAD-TEST-001")
                .processId("SUP.1")
                .category("Quality Manual")
                .documentName("Head Test Doc")
                .status("APPROVED")
                .build();
        master = documentMasterRepository.save(master);

        byte[] payload = "HEAD test bytes".getBytes();
        DocumentVersion version = DocumentVersion.builder()
                .documentMaster(master)
                .version("1.0")
                .fileName("Head_Test.pdf")
                .fileType("PDF")
                .mimeType("application/pdf")
                .fileSize((long) payload.length)
                .fileData(payload)
                .checksum("sha256_head_" + UUID.randomUUID())
                .approvalStatus("APPROVED")
                .isLatest(true)
                .build();
        version = documentVersionRepository.save(version);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head("/api/public/dms/download/" + version.getId()).secure(true))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"));
    }

    @Test
    @Transactional
    void test11_FileFormatMetadataAndSameNameIsolation() throws Exception {
        // Document 1: 0813_ProjectName_QMP.docx
        DocumentMaster masterDocx = DocumentMaster.builder()
                .documentCode("CODE-DOCX-0813")
                .processId("MAN.3")
                .category("Quality Manual")
                .documentName("0813_ProjectName_QMP")
                .status("APPROVED")
                .build();
        masterDocx = documentMasterRepository.save(masterDocx);

        DocumentVersion vDocx = DocumentVersion.builder()
                .documentMaster(masterDocx)
                .version("1.0")
                .fileName("0813_ProjectName_QMP.docx")
                .fileType("DOCX")
                .mimeType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                .fileSize(500L)
                .fileData("docx data".getBytes())
                .checksum("sha256_docx_" + UUID.randomUUID())
                .approvalStatus("APPROVED")
                .isLatest(true)
                .build();
        vDocx = documentVersionRepository.save(vDocx);

        // Document 2: 1307_ProjectName_Problem_Record.xlsx
        DocumentMaster masterXlsx = DocumentMaster.builder()
                .documentCode("CODE-XLSX-1307")
                .processId("SUP.9")
                .category("Problem Management")
                .documentName("1307_ProjectName_Problem_Record")
                .status("APPROVED")
                .build();
        masterXlsx = documentMasterRepository.save(masterXlsx);

        DocumentVersion vXlsx = DocumentVersion.builder()
                .documentMaster(masterXlsx)
                .version("1.0")
                .fileName("1307_ProjectName_Problem_Record.xlsx")
                .fileType("XLSX")
                .mimeType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .fileSize(600L)
                .fileData("xlsx data".getBytes())
                .checksum("sha256_xlsx_" + UUID.randomUUID())
                .approvalStatus("APPROVED")
                .isLatest(true)
                .build();
        vXlsx = documentVersionRepository.save(vXlsx);

        // Document 3: Uppercase extension ASPICE_v3.1_vs_v4.0.PDF
        DocumentMaster masterPdf = DocumentMaster.builder()
                .documentCode("CODE-PDF-UPPER")
                .processId("SUP.1")
                .category("Quality Manual")
                .documentName("ASPICE_v3.1_vs_v4.0_Upper")
                .status("APPROVED")
                .build();
        masterPdf = documentMasterRepository.save(masterPdf);

        DocumentVersion vPdf = DocumentVersion.builder()
                .documentMaster(masterPdf)
                .version("1.0")
                .fileName("ASPICE_v3.1_vs_v4.0.PDF")
                .fileType("PDF")
                .mimeType("application/pdf")
                .fileSize(700L)
                .fileData("pdf data".getBytes())
                .checksum("sha256_pdf_" + UUID.randomUUID())
                .approvalStatus("APPROVED")
                .isLatest(true)
                .build();
        vPdf = documentVersionRepository.save(vPdf);

        // Verify public API returns exact fileName & fileType metadata
        List<com.qualitywebsite.entity.DocumentEntity> publicDocs = documentService.getAllDocuments();

        com.qualitywebsite.entity.DocumentEntity docxMatch = publicDocs.stream()
                .filter(d -> "0813_ProjectName_QMP".equals(d.getDocumentName()))
                .findFirst().orElseThrow();
        assertEquals("0813_ProjectName_QMP.docx", docxMatch.getFileName());
        assertEquals("DOCX", docxMatch.getFileType());
        assertEquals("/api/public/dms/download/" + vDocx.getId(), docxMatch.getFilePath());

        com.qualitywebsite.entity.DocumentEntity xlsxMatch = publicDocs.stream()
                .filter(d -> "1307_ProjectName_Problem_Record".equals(d.getDocumentName()))
                .findFirst().orElseThrow();
        assertEquals("1307_ProjectName_Problem_Record.xlsx", xlsxMatch.getFileName());
        assertEquals("XLSX", xlsxMatch.getFileType());
        assertEquals("/api/public/dms/download/" + vXlsx.getId(), xlsxMatch.getFilePath());

        com.qualitywebsite.entity.DocumentEntity pdfMatch = publicDocs.stream()
                .filter(d -> "ASPICE_v3.1_vs_v4.0_Upper".equals(d.getDocumentName()))
                .findFirst().orElseThrow();
        assertEquals("ASPICE_v3.1_vs_v4.0.PDF", pdfMatch.getFileName());
        assertEquals("PDF", pdfMatch.getFileType());
        assertEquals("/api/public/dms/download/" + vPdf.getId(), pdfMatch.getFilePath());
    }

    @Test
    @Transactional
    void test12_ComprehensiveHeadAndGetVerificationAllFormatsAndErrors() throws Exception {
        // 1. Invalid version ID -> 404
        mockMvc.perform(get("/api/public/dms/download/999999").secure(true))
                .andExpect(status().isNotFound());

        // 2. DOCX Approved GET & HEAD -> HTTP 200
        DocumentMaster masterDocx = DocumentMaster.builder()
                .documentCode("CODE-ALL-DOCX")
                .processId("MAN.3")
                .category("Quality Manual")
                .documentName("QMP Standard")
                .status("APPROVED")
                .build();
        masterDocx = documentMasterRepository.save(masterDocx);

        byte[] docxBytes = "0813_ProjectName_QMP bytes".getBytes();
        DocumentVersion vDocx = DocumentVersion.builder()
                .documentMaster(masterDocx)
                .version("1.0")
                .fileName("0813_ProjectName_QMP.docx")
                .fileType("DOCX")
                .mimeType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                .fileSize((long) docxBytes.length)
                .fileData(docxBytes)
                .checksum("sha256_all_docx_" + UUID.randomUUID())
                .approvalStatus("APPROVED")
                .isLatest(true)
                .build();
        vDocx = documentVersionRepository.save(vDocx);

        // HEAD request for DOCX
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head("/api/public/dms/download/" + vDocx.getId()).secure(true))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("0813_ProjectName_QMP.docx")))
                .andExpect(header().string(HttpHeaders.CONTENT_LENGTH, String.valueOf(docxBytes.length)));

        // GET request for DOCX
        mockMvc.perform(get("/api/public/dms/download/" + vDocx.getId()).secure(true))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("0813_ProjectName_QMP.docx")))
                .andExpect(header().string(HttpHeaders.CONTENT_LENGTH, String.valueOf(docxBytes.length)))
                .andExpect(content().bytes(docxBytes));

        // 3. HEAD request for XLSX
        DocumentMaster masterXlsx = DocumentMaster.builder()
                .documentCode("CODE-ALL-XLSX")
                .processId("SUP.9")
                .category("Quality Manual")
                .documentName("Assessment Questionnaires")
                .status("APPROVED")
                .build();
        masterXlsx = documentMasterRepository.save(masterXlsx);

        byte[] xlsxBytes = "Assessment_Questionnaires.xlsx bytes".getBytes();
        DocumentVersion vXlsx = DocumentVersion.builder()
                .documentMaster(masterXlsx)
                .version("1.0")
                .fileName("Assessment_Questionnaires.xlsx")
                .fileType("XLSX")
                .mimeType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .fileSize((long) xlsxBytes.length)
                .fileData(xlsxBytes)
                .checksum("sha256_all_xlsx_" + UUID.randomUUID())
                .approvalStatus("APPROVED")
                .isLatest(true)
                .build();
        vXlsx = documentVersionRepository.save(vXlsx);

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head("/api/public/dms/download/" + vXlsx.getId()).secure(true))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("Assessment_Questionnaires.xlsx")))
                .andExpect(header().string(HttpHeaders.CONTENT_LENGTH, String.valueOf(xlsxBytes.length)));
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    void test13_AdminArchiveAndPermanentDeleteWorkflowStandardization() throws Exception {
        // Create an active document for Generic Templates
        DocumentMaster master = DocumentMaster.builder()
                .documentCode("CODE-GT-ARCHIVE")
                .processId("GLOBAL")
                .category("Generic Templates")
                .documentName("Archivable Generic Template")
                .status("APPROVED")
                .build();
        master = documentMasterRepository.save(master);

        DocumentVersion version = DocumentVersion.builder()
                .documentMaster(master)
                .version("1.0")
                .fileName("Archivable_Generic_Template.docx")
                .fileType("DOCX")
                .mimeType("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                .fileSize(100L)
                .fileData("gt bytes".getBytes())
                .checksum("sha256_gt_arch_" + UUID.randomUUID())
                .approvalStatus("APPROVED")
                .isLatest(true)
                .build();
        documentVersionRepository.save(version);

        // 1. Initial status -> ACTIVE / APPROVED, returned in public endpoints
        mockMvc.perform(get("/api/public/generic-templates").secure(true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].documentName", hasItem("Archivable Generic Template")));

        // 2. Perform Archive request (First Delete Action)
        mockMvc.perform(post("/api/admin/dms/documents/" + master.getId() + "/archive")
                .with(csrf())
                .secure(true))
                .andExpect(status().isOk());

        // Verify document status changed to ARCHIVED and is excluded from active public listing
        DocumentMaster archivedMaster = documentMasterRepository.findById(master.getId()).orElseThrow();
        assertEquals("ARCHIVED", archivedMaster.getStatus());

        mockMvc.perform(get("/api/public/generic-templates").secure(true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].documentName", not(hasItem("Archivable Generic Template"))));

        // 3. Perform Permanent Delete request (Explicit Second Action)
        mockMvc.perform(post("/api/admin/dms/documents/" + master.getId() + "/delete-permanently")
                .with(csrf())
                .secure(true))
                .andExpect(status().isOk());

        DocumentMaster permDeletedMaster = documentMasterRepository.findById(master.getId()).orElseThrow();
        assertEquals("DELETED", permDeletedMaster.getStatus());
    }
}
