package com.qualitywebsite.repository;

import com.qualitywebsite.entity.AdminUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AdminUserRepository extends JpaRepository<AdminUser, Long> {
    Optional<AdminUser> findByUsername(String username);
    Optional<AdminUser> findByUsernameIgnoreCase(String username);
    Optional<AdminUser> findByEmail(String email);
    Optional<AdminUser> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByUsernameIgnoreCase(String username);
    long countByEnabledTrue();
}
