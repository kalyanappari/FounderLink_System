package com.founderlink.startup.serviceImpl;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Service;

import com.founderlink.startup.command.StartupCommandService;
import com.founderlink.startup.dto.request.StartupRequestDto;
import com.founderlink.startup.dto.response.StartupResponseDto;
import com.founderlink.startup.entity.StartupStage;
import com.founderlink.startup.query.StartupQueryService;
import com.founderlink.startup.service.StartupService;

import lombok.RequiredArgsConstructor;

/**
 * Facade that satisfies the existing StartupService contract.
 * Delegates writes → StartupCommandService (CQRS Command side)
 * Delegates reads  → StartupQueryService   (CQRS Query side + Redis cache)
 */
@Service
@RequiredArgsConstructor
public class StartupServiceImpl implements StartupService {

    private final StartupCommandService commandService;
    private final StartupQueryService   queryService;

    @Override
    public StartupResponseDto createStartup(Long founderId, StartupRequestDto requestDto) {
        return commandService.createStartup(founderId, requestDto);
    }

    @Override
    public StartupResponseDto updateStartup(Long id, Long founderId, StartupRequestDto requestDto) {
        return commandService.updateStartup(id, founderId, requestDto);
    }

    @Override
    public void deleteStartup(Long id, Long founderId) {
        commandService.deleteStartup(id, founderId);
    }

    @Override
    public StartupResponseDto getStartupById(Long id) {
        return queryService.getStartupById(id);
    }

    @Override
    public com.founderlink.startup.dto.response.PagedResponse<StartupResponseDto> getAllStartups(int page, int size) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        return queryService.getAllStartups(pageable);
    }

    @Override
    public com.founderlink.startup.dto.response.PagedResponse<StartupResponseDto> getStartupsByFounderId(Long founderId, int page, int size) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        return queryService.getStartupsByFounderId(founderId, pageable);
    }

    @Override
    public com.founderlink.startup.dto.response.PagedResponse<StartupResponseDto> searchStartups(String industry, StartupStage stage,
                                                    BigDecimal minFunding, BigDecimal maxFunding, int page, int size) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        return queryService.searchStartups(industry, stage, minFunding, maxFunding, pageable);
    }
}
