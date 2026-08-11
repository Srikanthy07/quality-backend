package com.qualitywebsite.repository;

import com.qualitywebsite.entity.AdminPasswordResetToken;
import com.qualitywebsite.entity.AdminUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AdminPasswordResetTokenRepository extends JpaRepository<AdminPasswordResetToken, Long> {
    Optional<AdminPasswordResetToken> findByTokenHash(String tokenHash);
    void deleteByAdminUser(AdminUser adminUser);
}
