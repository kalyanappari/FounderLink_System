package com.founderlink.startup.query;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.founderlink.startup.dto.response.StartupResponseDto;
import com.founderlink.startup.entity.Startup;
import com.founderlink.startup.entity.StartupStage;
import com.founderlink.startup.exception.InvalidSearchException;
import com.founderlink.startup.exception.StartupNotFoundException;
import com.founderlink.startup.mapper.StartupMapper;
import com.founderlink.startup.repository.StartupRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class StartupQueryService {

    private final StartupRepository startupRepository;
    private final StartupMapper startupMapper;

    /**
     * QUERY: Get active startup by ID.
     * Also used by FeignClient from other services.
     * Cache key = startupId.
     */
    @Cacheable(value = "startupById", key = "#id")
    public StartupResponseDto getStartupById(Long id) {
        log.info("QUERY - getStartupById: id={} (cache miss, hitting DB)", id);
        return startupRepository.findByIdAndIsDeletedFalse(id)
                .map(startupMapper::toResponseDto)
                .orElseThrow(() -> new StartupNotFoundException("Startup not found with id: " + id));
    }

    /**
     * QUERY: Get all active startups.
     * Cache key includes page and size.
     */
    @Cacheable(value = "allStartups", key = "'all_' + #pageable.pageNumber + '_' + #pageable.pageSize")
    public com.founderlink.startup.dto.response.PagedResponse<StartupResponseDto> getAllStartups(org.springframework.data.domain.Pageable pageable) {
        log.info("QUERY - getAllStartups (cache miss, hitting DB)");
        org.springframework.data.domain.Page<Startup> page = startupRepository.findByIsDeletedFalse(pageable);
        return new com.founderlink.startup.dto.response.PagedResponse<>(page.map(startupMapper::toResponseDto));
    }

    /**
     * QUERY: Get active startups by founder.
     * Cache key includes founderId, page, size.
     */
    @Cacheable(value = "startupsByFounder", key = "#founderId + '_' + #pageable.pageNumber + '_' + #pageable.pageSize")
    public com.founderlink.startup.dto.response.PagedResponse<StartupResponseDto> getStartupsByFounderId(Long founderId, org.springframework.data.domain.Pageable pageable) {
        log.info("QUERY - getStartupsByFounderId: founderId={} (cache miss, hitting DB)", founderId);
        org.springframework.data.domain.Page<Startup> page = startupRepository.findByFounderIdAndIsDeletedFalse(founderId, pageable);
        return new com.founderlink.startup.dto.response.PagedResponse<>(page.map(startupMapper::toResponseDto));
    }

    /**
     * QUERY: Search startups by filters and paginate.
     * Cache key = combination of all filter params.
     */
    @Cacheable(value = "searchStartups",
               key = "(#industry ?: 'null') + '_' + (#stage ?: 'null') + '_' + (#minFunding ?: 'null') + '_' + (#maxFunding ?: 'null') + '_' + #pageable.pageNumber + '_' + #pageable.pageSize")
    public com.founderlink.startup.dto.response.PagedResponse<StartupResponseDto> searchStartups(String industry, StartupStage stage,
                                                    BigDecimal minFunding, BigDecimal maxFunding,
                                                    org.springframework.data.domain.Pageable pageable) {
        log.info("QUERY - searchStartups: industry={}, stage={}, min={}, max={} (cache miss, hitting DB)",
                industry, stage, minFunding, maxFunding);

        if (minFunding != null && maxFunding != null) {
            if (minFunding.compareTo(BigDecimal.ZERO) < 0 || maxFunding.compareTo(BigDecimal.ZERO) < 0) {
                throw new InvalidSearchException("Funding values cannot be negative");
            }
            if (minFunding.compareTo(maxFunding) > 0) {
                throw new InvalidSearchException("Minimum funding cannot be greater than maximum funding");
            }
        }
        if (minFunding != null && minFunding.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidSearchException("Minimum funding cannot be negative");
        }
        if (maxFunding != null && maxFunding.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidSearchException("Maximum funding cannot be negative");
        }

        org.springframework.data.domain.Page<Startup> page = startupRepository.searchStartups(industry, stage, minFunding, maxFunding, pageable);
        return new com.founderlink.startup.dto.response.PagedResponse<>(page.map(startupMapper::toResponseDto));
    }
}
