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

    Optional<DocumentVersion> findByChecksum(String checksum);

    Optional<DocumentVersion> findFirstByFileNameIgnoreCase(String fileName);

    @Query("SELECT v FROM DocumentVersion v WHERE v.documentMaster.id = :masterId AND v.checksum = :checksum")
    Optional<DocumentVersion> findByMasterIdAndChecksum(@Param("masterId") Long masterId, @Param("checksum") String checksum);
}
