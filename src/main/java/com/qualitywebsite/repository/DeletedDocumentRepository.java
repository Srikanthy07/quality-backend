package com.qualitywebsite.repository;

import com.qualitywebsite.entity.DeletedDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeletedDocumentRepository extends JpaRepository<DeletedDocument, Long> {

    Optional<DeletedDocument> findByOriginalMasterId(Long originalMasterId);

    Optional<DeletedDocument> findByDocumentNameIgnoreCase(String documentName);

    Optional<DeletedDocument> findByDocumentCode(String documentCode);

    List<DeletedDocument> findAllByOrderByDeletedDateDesc();

    List<DeletedDocument> findByCategoryIgnoreCaseOrderByDeletedDateDesc(String category);

    boolean existsByChecksum(String checksum);

    void deleteByOriginalMasterId(Long originalMasterId);
}
