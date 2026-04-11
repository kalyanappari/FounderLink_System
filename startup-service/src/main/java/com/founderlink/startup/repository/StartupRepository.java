package com.founderlink.startup.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.founderlink.startup.entity.Startup;
import com.founderlink.startup.entity.StartupStage;

@Repository
public interface StartupRepository
        extends JpaRepository<Startup, Long> {

    // ─────────────────────────────────────────
    // MANDATORY
    // Get active startup by ID
    // Used by FeignClient
    // ─────────────────────────────────────────
    Optional<Startup> findByIdAndIsDeletedFalse(Long id);

    // ─────────────────────────────────────────
    // MANDATORY
    // Get all active startups
    // Used by Investor discovery
    // ─────────────────────────────────────────
    org.springframework.data.domain.Page<Startup> findByIsDeletedFalse(org.springframework.data.domain.Pageable pageable);

    // ─────────────────────────────────────────
    // MANDATORY
    // Get active startups by founder
    // Founder sees their own startups
    // ─────────────────────────────────────────
    org.springframework.data.domain.Page<Startup> findByFounderIdAndIsDeletedFalse(
            Long founderId, org.springframework.data.domain.Pageable pageable);

    // ─────────────────────────────────────────
    // POWER SEARCH WITH OPTIONAL PARAMETERS
    // ─────────────────────────────────────────
    @org.springframework.data.jpa.repository.Query("SELECT s FROM Startup s WHERE s.isDeleted = false " +
        "AND (:industry IS NULL OR LOWER(CAST(s.industry AS string)) LIKE LOWER(CONCAT('%', CAST(:industry AS string), '%'))) " +
        "AND (:stage IS NULL OR s.stage = :stage) " +
        "AND (:minFunding IS NULL OR s.fundingGoal >= :minFunding) " +
        "AND (:maxFunding IS NULL OR s.fundingGoal <= :maxFunding)")
    org.springframework.data.domain.Page<Startup> searchStartups(
            @org.springframework.data.repository.query.Param("industry") String industry,
            @org.springframework.data.repository.query.Param("stage") StartupStage stage,
            @org.springframework.data.repository.query.Param("minFunding") java.math.BigDecimal minFunding,
            @org.springframework.data.repository.query.Param("maxFunding") java.math.BigDecimal maxFunding,
            org.springframework.data.domain.Pageable pageable);
}