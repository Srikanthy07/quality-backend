package com.qualitywebsite.repository;

import com.qualitywebsite.entity.DocumentDownloadLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DocumentDownloadLogRepository extends JpaRepository<DocumentDownloadLog, Long> {

    @Query("SELECT COUNT(d) FROM DocumentDownloadLog d WHERE d.downloadTime >= :start AND d.downloadTime <= :end")
    long countDownloadsBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT d.documentId, d.documentName, d.category, COUNT(d), MAX(d.downloadTime) " +
           "FROM DocumentDownloadLog d WHERE d.downloadTime >= :start AND d.downloadTime <= :end " +
           "GROUP BY d.documentId, d.documentName, d.category ORDER BY COUNT(d) DESC")
    List<Object[]> findTopDownloadedDocumentsBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end, Pageable pageable);

    @Query("SELECT d.category, COUNT(d) FROM DocumentDownloadLog d WHERE d.downloadTime >= :start AND d.downloadTime <= :end GROUP BY d.category ORDER BY COUNT(d) DESC")
    List<Object[]> findDownloadsByCategoryBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
