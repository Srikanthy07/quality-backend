package com.qualitywebsite.repository;

import com.qualitywebsite.entity.WebsiteVisitor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface WebsiteVisitorRepository extends JpaRepository<WebsiteVisitor, Long> {

    Optional<WebsiteVisitor> findFirstByVisitorIdOrderByVisitTimeDesc(String visitorId);

    Optional<WebsiteVisitor> findFirstByVisitorIdAndSessionIdOrderByVisitTimeDesc(String visitorId, String sessionId);

    boolean existsByVisitorId(String visitorId);

    @Query("SELECT COUNT(DISTINCT v.sessionId) FROM WebsiteVisitor v WHERE v.visitorId = :visitorId AND v.sessionId <> :sessionId")
    long countOtherSessions(@Param("visitorId") String visitorId, @Param("sessionId") String sessionId);

    @Query("SELECT COUNT(v) FROM WebsiteVisitor v WHERE v.visitTime >= :start AND v.visitTime <= :end")
    long countVisitsBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COUNT(DISTINCT v.visitorId) FROM WebsiteVisitor v WHERE v.visitTime >= :start AND v.visitTime <= :end")
    long countUniqueVisitorsBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COUNT(DISTINCT v.sessionId) FROM WebsiteVisitor v WHERE v.visitTime >= :start AND v.visitTime <= :end")
    long countSessionsBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COALESCE(SUM(v.pageViews), 0) FROM WebsiteVisitor v WHERE v.visitTime >= :start AND v.visitTime <= :end")
    long sumPageViewsBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT COUNT(DISTINCT v.visitorId) FROM WebsiteVisitor v")
    long countOverallUniqueVisitors();

    @Query("SELECT COUNT(DISTINCT v.sessionId) FROM WebsiteVisitor v")
    long countOverallSessions();

    @Query("SELECT COUNT(DISTINCT v.visitorId) FROM WebsiteVisitor v WHERE v.visitorId IN " +
           "(SELECT v2.visitorId FROM WebsiteVisitor v2 GROUP BY v2.visitorId HAVING COUNT(DISTINCT v2.sessionId) > 1)")
    long countReturningVisitors();

    @Query("SELECT v.pageUrl, COUNT(v), MAX(v.visitTime) FROM WebsiteVisitor v WHERE v.visitTime >= :start AND v.visitTime <= :end GROUP BY v.pageUrl ORDER BY COUNT(v) DESC")
    List<Object[]> findTopPagesBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end, Pageable pageable);

    @Query("SELECT v.browser, COUNT(v) FROM WebsiteVisitor v WHERE v.visitTime >= :start AND v.visitTime <= :end GROUP BY v.browser ORDER BY COUNT(v) DESC")
    List<Object[]> findBrowserDistributionBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT v.deviceType, COUNT(v) FROM WebsiteVisitor v WHERE v.visitTime >= :start AND v.visitTime <= :end GROUP BY v.deviceType ORDER BY COUNT(v) DESC")
    List<Object[]> findDeviceDistributionBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query("SELECT v FROM WebsiteVisitor v WHERE v.visitTime >= :start AND v.visitTime <= :end ORDER BY v.visitTime DESC")
    List<WebsiteVisitor> findRecentActivityBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end, Pageable pageable);
}
