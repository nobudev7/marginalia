package com.nobudev.marginalia.repository;

import com.nobudev.marginalia.entity.WhitelistEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WhitelistRepository extends JpaRepository<WhitelistEntry, Long> {
    Optional<WhitelistEntry> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    List<WhitelistEntry> findAllByOrderByCreatedAtDesc();
    void deleteByEmailIgnoreCase(String email);
}
