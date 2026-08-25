package com.qualitywebsite.repository;

import com.qualitywebsite.entity.DocumentMaster;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentMasterRepository extends JpaRepository<DocumentMaster, Long> {

    Optional<DocumentMaster> findByDocumentCode(String documentCode);

    Optional<DocumentMaster> findFirstByProcessIdIgnoreCaseAndCategoryIgnoreCaseAndDocumentNameIgnoreCaseOrderByIdDesc(
            String processId, String category, String documentName);

    List<DocumentMaster> findAllByProcessIdIgnoreCaseAndCategoryIgnoreCaseAndDocumentNameIgnoreCaseOrderByIdDesc(
            String processId, String category, String documentName);

    default Optional<DocumentMaster> findByProcessIdIgnoreCaseAndCategoryIgnoreCaseAndDocumentNameIgnoreCase(
            String processId, String category, String documentName) {
        List<DocumentMaster> list = findAllByProcessIdIgnoreCaseAndCategoryIgnoreCaseAndDocumentNameIgnoreCaseOrderByIdDesc(processId, category, documentName);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    Optional<DocumentMaster> findFirstByProcessIdIgnoreCaseAndCategoryIgnoreCaseAndDocumentNameIgnoreCase(
            String processId, String category, String documentName);

    @Query("SELECT m FROM DocumentMaster m WHERE LOWER(m.processId) = LOWER(:processId) AND LOWER(m.category) = LOWER(:category) AND LOWER(m.documentName) = LOWER(:documentName) AND UPPER(m.status) NOT IN ('ARCHIVED', 'DELETED') ORDER BY m.id DESC")
    List<DocumentMaster> findAllActiveByProcessIdAndCategoryAndDocumentName(
            @Param("processId") String processId,
            @Param("category") String category,
            @Param("documentName") String documentName);

    default Optional<DocumentMaster> findActiveByProcessIdAndCategoryAndDocumentName(
            String processId, String category, String documentName) {
        List<DocumentMaster> list = findAllActiveByProcessIdAndCategoryAndDocumentName(processId, category, documentName);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    boolean existsByProcessIdIgnoreCaseAndCategoryIgnoreCaseAndDocumentNameIgnoreCase(
            String processId, String category, String documentName);

    List<DocumentMaster> findByStatus(String status);

    List<DocumentMaster> findByCategoryIgnoreCaseAndStatus(String category, String status);

    List<DocumentMaster> findByStatusNot(String status);

    List<DocumentMaster> findAllByOrderByUpdatedDateDesc();

    long countByStatus(String status);

    long countByCategoryIgnoreCaseAndStatus(String category, String status);

    @Query("SELECT m FROM DocumentMaster m WHERE " +
           "(:query IS NULL OR :query = '' OR " +
           " LOWER(m.documentName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           " LOWER(m.processId) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           " LOWER(m.processName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           " LOWER(m.processGroup) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           " LOWER(m.category) LIKE LOWER(CONCAT('%', :query, '%'))) AND " +
           "(:category IS NULL OR :category = '' OR LOWER(m.category) = LOWER(:category)) AND " +
           "((:statusFilter IS NULL OR :statusFilter = '' OR :statusFilter = 'ACTIVE') AND m.status NOT IN ('ARCHIVED', 'DELETED') OR " +
           " :statusFilter = 'ARCHIVED' AND m.status = 'ARCHIVED' OR " +
           " :statusFilter = 'DELETED' AND m.status = 'DELETED' OR " +
           " :statusFilter = 'ALL' AND m.status <> 'DELETED' OR " +
           " (:statusFilter NOT IN ('ACTIVE', 'ARCHIVED', 'DELETED', 'ALL') AND LOWER(m.status) = LOWER(:statusFilter)))")
    List<DocumentMaster> searchAndFilter(
            @Param("query") String query,
            @Param("category") String category,
            @Param("statusFilter") String statusFilter);

    @Query("SELECT m FROM DocumentMaster m WHERE " +
           "(:query IS NULL OR :query = '' OR " +
           " LOWER(m.documentName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           " LOWER(m.processId) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           " LOWER(m.processName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           " LOWER(m.processGroup) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           " LOWER(m.category) LIKE LOWER(CONCAT('%', :query, '%'))) AND " +
           "(:category IS NULL OR :category = '' OR LOWER(m.category) = LOWER(:category)) AND " +
           "((:statusFilter IS NULL OR :statusFilter = '' OR :statusFilter = 'ACTIVE') AND m.status NOT IN ('ARCHIVED', 'DELETED') OR " +
           " :statusFilter = 'ARCHIVED' AND m.status = 'ARCHIVED' OR " +
           " :statusFilter = 'DELETED' AND m.status = 'DELETED' OR " +
           " :statusFilter = 'ALL' AND m.status <> 'DELETED' OR " +
           " (:statusFilter NOT IN ('ACTIVE', 'ARCHIVED', 'DELETED', 'ALL') AND LOWER(m.status) = LOWER(:statusFilter)))")
    org.springframework.data.domain.Page<DocumentMaster> searchAndFilterPaged(
            @Param("query") String query,
            @Param("category") String category,
            @Param("statusFilter") String statusFilter,
            org.springframework.data.domain.Pageable pageable);

    @Query("SELECT m FROM DocumentMaster m WHERE " +
           "(:query IS NULL OR " +
           " LOWER(m.documentName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           " LOWER(m.processId) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           " LOWER(m.processName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           " LOWER(m.processGroup) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           " LOWER(m.category) LIKE LOWER(CONCAT('%', :query, '%'))) AND " +
           "m.status = 'APPROVED'")
    List<DocumentMaster> searchPublicApproved(@Param("query") String query);

    @Query("SELECT MAX(m.updatedDate) FROM DocumentMaster m")
    LocalDateTime findLatestUploadDate();
}
