package com.qualitywebsite.repository;

import com.qualitywebsite.entity.DocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentRepository extends JpaRepository<DocumentEntity, String> {

    // Retrieve only active documents
    List<DocumentEntity> findAllByIsActiveTrue();

    List<DocumentEntity> findByCategoryIgnoreCase(String category);

    List<DocumentEntity> findByDocumentNameIgnoreCase(String documentName);

    List<DocumentEntity> findByCategoryIgnoreCaseAndIsActiveTrue(String category);

    List<DocumentEntity> findByProcessIgnoreCase(String process);

    List<DocumentEntity> findByProcessGroupIgnoreCase(String processGroup);

    @Query("SELECT d FROM DocumentEntity d WHERE " +
           "(:query IS NULL OR " +
           " LOWER(d.documentName)  LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           " LOWER(d.process)       LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           " LOWER(d.processGroup)  LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           " LOWER(d.category)      LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           " LOWER(d.description)   LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           " LOWER(d.fileName)      LIKE LOWER(CONCAT('%', :query, '%'))) AND " +
           "(:category IS NULL OR LOWER(d.category) = LOWER(:category)) AND d.isActive = true")
    List<DocumentEntity> searchAndFilter(@Param("query") String query, @Param("category") String category);

    @Query("SELECT d FROM DocumentEntity d WHERE " +
           "LOWER(d.documentName) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "AND d.isActive = true " +
           "ORDER BY LOWER(d.documentName)")
    List<DocumentEntity> findActiveByDocumentNameContainingIgnoreCase(@Param("query") String query);

    long countByCategoryIgnoreCase(String category);

    // ── Active-only counts for dashboard statistics (RC-5 fix) ───────────────
    // Spring Data generates: SELECT COUNT(*) FROM documents WHERE is_active = true
    long countByIsActiveTrue();

    // Spring Data generates: SELECT COUNT(*) FROM documents WHERE LOWER(category) = LOWER(?) AND is_active = true
    long countByCategoryIgnoreCaseAndIsActiveTrue(String category);

    // ── Efficient duplicate detection (RC-6 fix) ─────────────────────────────
    // Replaces the full documentRepository.findAll() + Java-level loop in DocumentService.isDuplicate().
    // Spring Data generates a single SELECT EXISTS(...) query using indexed column lookups.
    boolean existsByDocumentNameIgnoreCaseAndCategoryIgnoreCaseAndProcessIgnoreCase(
            String documentName, String category, String process);

    @Query("SELECT MAX(d.updatedAt) FROM DocumentEntity d")
    java.time.LocalDateTime findLatestUploadDate();
}
