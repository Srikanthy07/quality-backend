package com.qualitywebsite.repository;

import com.qualitywebsite.entity.SearchLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface SearchLogRepository extends JpaRepository<SearchLog, Long> {

    @Query("SELECT s.searchKeyword, COUNT(s), AVG(s.resultsCount), MAX(s.searchTime) " +
           "FROM SearchLog s WHERE s.searchTime >= :start AND s.searchTime <= :end " +
           "GROUP BY s.searchKeyword ORDER BY COUNT(s) DESC")
    List<Object[]> findTopKeywordsBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end, Pageable pageable);

    @Query("SELECT s.searchKeyword, COUNT(s) FROM SearchLog s WHERE s.searchTime >= :start AND s.searchTime <= :end AND s.resultsCount = 0 GROUP BY s.searchKeyword ORDER BY COUNT(s) DESC")
    List<Object[]> findZeroResultSearchesBetween(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end, Pageable pageable);
}
