package com.founderlink.startup.query;

import com.founderlink.startup.dto.response.PagedResponse;
import com.founderlink.startup.dto.response.StartupResponseDto;
import com.founderlink.startup.entity.Startup;
import com.founderlink.startup.entity.StartupStage;
import com.founderlink.startup.exception.InvalidSearchException;
import com.founderlink.startup.exception.StartupNotFoundException;
import com.founderlink.startup.mapper.StartupMapper;
import com.founderlink.startup.repository.StartupRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StartupQueryServiceTest {

    @Mock
    private StartupRepository startupRepository;

    @Mock
    private StartupMapper startupMapper;

    @InjectMocks
    private StartupQueryService queryService;

    @Test
    void getStartupById_shouldReturnDto_whenExists() {
        // Arrange
        Long id = 1L;
        Startup startup = new Startup();
        startup.setId(id);
        when(startupRepository.findByIdAndIsDeletedFalse(id)).thenReturn(Optional.of(startup));
        when(startupMapper.toResponseDto(startup)).thenReturn(new StartupResponseDto());

        // Act
        StartupResponseDto result = queryService.getStartupById(id);

        // Assert
        assertThat(result).isNotNull();
        verify(startupRepository).findByIdAndIsDeletedFalse(id);
    }

    @Test
    void getStartupById_shouldThrowNotFound_whenMissing() {
        // Arrange
        when(startupRepository.findByIdAndIsDeletedFalse(anyLong())).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> queryService.getStartupById(1L))
                .isInstanceOf(StartupNotFoundException.class);
    }

    @Test
    void getAllStartups_shouldReturnPagedResponse() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        Page<Startup> page = new PageImpl<>(List.of(new Startup()));
        when(startupRepository.findByIsDeletedFalse(pageable)).thenReturn(page);
        when(startupMapper.toResponseDto(any())).thenReturn(new StartupResponseDto());

        // Act
        PagedResponse<StartupResponseDto> result = queryService.getAllStartups(pageable);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
    }

    @Test
    void searchStartups_shouldFail_whenMinGreaterThanMax() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        BigDecimal min = new BigDecimal("1000");
        BigDecimal max = new BigDecimal("500");

        // Act & Assert
        assertThatThrownBy(() -> queryService.searchStartups(null, null, min, max, pageable))
                .isInstanceOf(InvalidSearchException.class)
                .hasMessage("Minimum funding cannot be greater than maximum funding");
    }

    @Test
    void searchStartups_shouldFail_whenBothFundingNegative() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        BigDecimal min = new BigDecimal("-1");
        BigDecimal max = new BigDecimal("-1");

        // Act & Assert
        assertThatThrownBy(() -> queryService.searchStartups(null, null, min, max, pageable))
                .isInstanceOf(InvalidSearchException.class)
                .hasMessage("Funding values cannot be negative");
    }

    @Test
    void searchStartups_shouldFail_whenMaxFundingNegativeOnlyInsideBlock() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        BigDecimal min = new BigDecimal("100");
        BigDecimal max = new BigDecimal("-1");

        // Act & Assert
        assertThatThrownBy(() -> queryService.searchStartups(null, null, min, max, pageable))
                .isInstanceOf(InvalidSearchException.class)
                .hasMessage("Funding values cannot be negative");
    }

    @Test
    void searchStartups_shouldFail_whenFundingNegative() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        BigDecimal min = new BigDecimal("-1");

        // Act & Assert
        assertThatThrownBy(() -> queryService.searchStartups(null, null, min, null, pageable))
                .isInstanceOf(InvalidSearchException.class)
                .hasMessage("Minimum funding cannot be negative");
    }

    @Test
    void searchStartups_shouldFail_whenMaxFundingNegative() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        BigDecimal max = new BigDecimal("-1");

        // Act & Assert
        assertThatThrownBy(() -> queryService.searchStartups(null, null, null, max, pageable))
                .isInstanceOf(InvalidSearchException.class)
                .hasMessage("Maximum funding cannot be negative");
    }

    @Test
    void searchStartups_shouldSucceed_whenValidRange() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        Page<Startup> page = new PageImpl<>(List.of(new Startup()));
        when(startupRepository.searchStartups(any(), any(), any(), any(), eq(pageable))).thenReturn(page);
        when(startupMapper.toResponseDto(any())).thenReturn(new StartupResponseDto());

        // Act
        PagedResponse<StartupResponseDto> result = queryService.searchStartups("Tech", StartupStage.IDEA, null, null, pageable);

        // Assert
        assertThat(result).isNotNull();
        verify(startupRepository).searchStartups(eq("Tech"), eq(StartupStage.IDEA), any(), any(), eq(pageable));
    }
}
