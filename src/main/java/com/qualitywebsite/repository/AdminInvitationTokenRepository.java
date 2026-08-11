package com.qualitywebsite.repository;

import com.qualitywebsite.entity.AdminInvitationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AdminInvitationTokenRepository extends JpaRepository<AdminInvitationToken, Long> {
    Optional<AdminInvitationToken> findByTokenHash(String tokenHash);
    Optional<AdminInvitationToken> findByEmailIgnoreCaseAndUsedFalse(String email);
    boolean existsByEmailIgnoreCaseAndUsedFalse(String email);
}
