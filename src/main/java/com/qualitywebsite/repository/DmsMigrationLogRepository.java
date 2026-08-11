package com.qualitywebsite.repository;

import com.qualitywebsite.entity.DmsMigrationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DmsMigrationLogRepository extends JpaRepository<DmsMigrationLog, Long> {
    List<DmsMigrationLog> findByDocumentMasterIdOrderByPerformedDateDesc(Long documentMasterId);
    List<DmsMigrationLog> findTop20ByOrderByPerformedDateDesc();
}
