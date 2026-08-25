package com.qualitywebsite.repository;

import com.qualitywebsite.entity.DocumentVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, Long> {

    Optional<DocumentVersion> findByDocumentMasterIdAndIsLatestTrue(Long documentMasterId);

    List<DocumentVersion> findByDocumentMasterIdOrderByUploadedDateDesc(Long documentMasterId);

    /**
     * Lookup a version by major + minor integers.
     * Replaces the old findByDocumentMasterIdAndVersion which referenced a non-existent persistent 'version' column.
     */
    @Query("SELECT v FROM DocumentVersion v WHERE v.documentMaster.id = :masterId AND v.majorVersion = :major AND v.minorVersion = :minor")
    Optional<DocumentVersion> findByMasterIdAndMajorMinor(
            @Param("masterId") Long masterId,
            @Param("major") Integer major,
            @Param("minor") Integer minor);

    long countByDocumentMasterId(Long documentMasterId);

    boolean existsByChecksum(String checksum);

    Optional<DocumentVersion> findFirstByChecksum(String checksum);

    List<DocumentVersion> findByChecksum(String checksum);

    @Query("SELECT COUNT(v) > 0 FROM DocumentVersion v WHERE v.checksum = :checksum AND v.documentMaster IS NOT NULL AND UPPER(v.documentMaster.status) NOT IN ('ARCHIVED', 'DELETED') AND UPPER(v.approvalStatus) NOT IN ('ARCHIVED', 'DELETED')")
    boolean existsActiveByChecksum(@Param("checksum") String checksum);

    @Query("SELECT v FROM DocumentVersion v WHERE v.checksum = :checksum AND v.documentMaster IS NOT NULL AND UPPER(v.documentMaster.status) NOT IN ('ARCHIVED', 'DELETED') AND UPPER(v.approvalStatus) NOT IN ('ARCHIVED', 'DELETED')")
    List<DocumentVersion> findActiveByChecksum(@Param("checksum") String checksum);

    @Query("SELECT v FROM DocumentVersion v WHERE v.checksum = :checksum AND v.documentMaster IS NOT NULL AND UPPER(v.documentMaster.status) NOT IN ('ARCHIVED', 'DELETED') AND UPPER(v.approvalStatus) NOT IN ('ARCHIVED', 'DELETED')")
    Optional<DocumentVersion> findFirstActiveByChecksum(@Param("checksum") String checksum);

    Optional<DocumentVersion> findFirstByFileNameIgnoreCase(String fileName);

    boolean existsByDocumentMasterIdAndChecksum(Long masterId, String checksum);

    Optional<DocumentVersion> findFirstByDocumentMasterIdAndChecksum(Long masterId, String checksum);
}
