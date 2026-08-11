package com.qualitywebsite.repository;

import com.qualitywebsite.entity.MasterListItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MasterListItemRepository extends JpaRepository<MasterListItem, Long> {

    @Query("SELECT m FROM MasterListItem m ORDER BY m.sNo ASC, m.id ASC")
    List<MasterListItem> findAllByOrderBySNoAscIdAsc();
}
